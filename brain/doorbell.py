#!/usr/bin/env python3
"""Always-on, featherweight front door for the companion's "brain".

Runs from Windows login (see brain/README.md). It uses no GPU and almost no
memory: it only listens on the home network. When the tablet asks the
companion something, it starts llama-server (Gemma) and brain.py (prompting +
BlueTTS), forwards the request, and shuts both down again after the
configured idle time, which frees the VRAM.

Endpoints (port from config, default 8790):
  GET  /health  -> {"brain": "cold" | "warming" | "ready"}
  POST /wake    -> start warming up in the background; returns /health
  POST /say     -> JSON {"situation": str, "lang": "he"|"en"}
                   200 audio/ogg (X-Text header = the line, URL-encoded), or 204 if no line
  POST /listen?lang=he -> body: WAV recording of the child; Whisper transcribes it and the
                   parrot answers. Same response, plus X-Heard (what Whisper heard).

Standard library only, so it runs with any Python 3.
"""

import json
import os
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))


def load_config():
    with open(os.path.join(HERE, "config.example.json"), encoding="utf-8") as f:
        cfg = json.load(f)
    local = os.path.join(HERE, "config.json")
    if os.path.exists(local):
        with open(local, encoding="utf-8") as f:
            cfg.update(json.load(f))
    # "~" in paths means the user's home folder
    for key in ("llama_dir", "model", "brain_python", "whisper_model", "bluetts_dir"):
        cfg[key] = os.path.expanduser(cfg[key])
    return cfg


CFG = load_config()
LLAMA_URL = f"http://127.0.0.1:{CFG['llama_port']}"
BRAIN_URL = f"http://127.0.0.1:{CFG['brain_port']}"


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)


def free_vram_mb():
    """Free GPU memory in MiB, or 0 if there's no usable NVIDIA GPU."""
    try:
        out = subprocess.run(
            ["nvidia-smi", "--query-gpu=memory.free", "--format=csv,noheader,nounits"],
            capture_output=True, text=True, timeout=10,
        ).stdout
        return int(out.strip().splitlines()[0])
    except Exception:
        return 0


def http_ok(url, timeout=2):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            return r.status == 200
    except Exception:
        return False


class Brain:
    """Starts, watches and stops the two heavy processes."""

    def __init__(self):
        self.lock = threading.Lock()
        self.procs = []
        self.state = "cold"
        self.last_used = 0.0

    def touch(self):
        self.last_used = time.time()

    def wake(self):
        with self.lock:
            if self.state != "cold":
                return
            self.state = "warming"
        threading.Thread(target=self._start, daemon=True).start()

    def _start(self):
        # GPU if there's room (training runs may be using it), otherwise CPU
        gpu_layers = 99 if free_vram_mb() >= CFG["min_free_vram_mb"] else 0
        log(f"waking brain on {'GPU' if gpu_layers else 'CPU'}")
        flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
        llama = subprocess.Popen(
            [os.path.join(CFG["llama_dir"], "llama-server"), "-m", CFG["model"],
             "--host", "127.0.0.1", "--port", str(CFG["llama_port"]),
             "-ngl", str(gpu_layers), "-c", str(CFG["context"]), "--no-webui"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=flags,
        )
        brain = subprocess.Popen(
            [CFG["brain_python"], os.path.join(HERE, "brain.py")],
            cwd=HERE, creationflags=flags,
        )
        self.procs = [llama, brain]
        deadline = time.time() + CFG["startup_timeout_s"]
        while time.time() < deadline:
            if any(p.poll() is not None for p in self.procs):
                break
            if http_ok(f"{LLAMA_URL}/health") and http_ok(f"{BRAIN_URL}/health"):
                with self.lock:
                    self.state = "ready"
                self.touch()
                log("brain ready")
                return
            time.sleep(0.5)
        log("brain failed to start")
        self.stop()

    def stop(self):
        for p in self.procs:
            if p.poll() is None:
                p.kill()
        self.procs = []
        with self.lock:
            self.state = "cold"

    def idle_watch(self):
        while True:
            time.sleep(20)
            if self.state == "ready" and time.time() - self.last_used > CFG["idle_minutes"] * 60:
                log("idle, putting brain to sleep")
                self.stop()


BRAIN = Brain()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def _json(self, code, obj):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self._json(200, {"brain": BRAIN.state})
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length) if length else b"{}"
        if self.path == "/wake":
            BRAIN.wake()
            BRAIN.touch()
            self._json(200, {"brain": BRAIN.state})
        elif self.path == "/say" or self.path.startswith("/listen"):
            BRAIN.wake()
            BRAIN.touch()
            deadline = time.time() + CFG["startup_timeout_s"]
            while BRAIN.state == "warming" and time.time() < deadline:
                time.sleep(0.2)
            if BRAIN.state != "ready":
                self.send_response(503)
                self.end_headers()
                return
            try:
                ctype = self.headers.get("Content-Type", "application/json")
                req = urllib.request.Request(f"{BRAIN_URL}{self.path}", data=body, headers={"Content-Type": ctype})
                with urllib.request.urlopen(req, timeout=60) as r:
                    data = r.read()
                    self.send_response(r.status)
                    for h in ("Content-Type", "X-Text", "X-Heard"):
                        if r.headers.get(h):
                            self.send_header(h, r.headers[h])
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
            except urllib.error.HTTPError as e:
                self.send_response(e.code)
                self.end_headers()
            except Exception as e:
                log(f"say failed: {e}")
                self.send_response(502)
                self.end_headers()
            BRAIN.touch()
        else:
            self._json(404, {"error": "not found"})


def main():
    threading.Thread(target=BRAIN.idle_watch, daemon=True).start()
    server = ThreadingHTTPServer((CFG["bind"], CFG["port"]), Handler)
    log(f"doorbell listening on {CFG['bind']}:{CFG['port']}")
    try:
        server.serve_forever()
    finally:
        BRAIN.stop()


if __name__ == "__main__":
    sys.exit(main())
