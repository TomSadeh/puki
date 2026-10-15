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
  POST /speak    -> JSON {"text", "lang", "speed"?}: just say this text (questions, story lines)
  POST /look?lang=he -> body: PNG of a drawing; the parrot reacts to it (Gemma vision)
  POST /story    -> JSON {"played", "icons": {name: label}, "lang"} -> JSON {"sentences": [{"text", "icon"}]}
  POST /listen?lang=he[&context=...] -> body: WAV recording of the child; Whisper transcribes it and the
                   parrot answers. Same response, plus X-Heard (what Whisper heard).

Every request must carry the pairing code in an X-Puki-Code header. The code is
made on first start, kept in pairing_code.txt next to this file, and printed at
startup; the parent types it into the app once. `python doorbell.py --new-code`
makes a new one (every tablet then needs the new code). Wrong codes are slowed
down, and an address that keeps guessing is locked out for a while.

A dashboard for the parent runs next to it on this PC only (dashboard.py,
http://127.0.0.1:8793 by default): status, the pairing code as a QR code, and a
live log of what Puki was asked and said.

Standard library only, so it runs with any Python 3.
"""

import hmac
import json
import os
import secrets
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import dashboard

HERE = os.path.dirname(os.path.abspath(__file__))


def load_config():
    with open(os.path.join(HERE, "config.example.json"), encoding="utf-8") as f:
        cfg = json.load(f)
    local = os.path.join(HERE, "config.json")
    if os.path.exists(local):
        with open(local, encoding="utf-8") as f:
            cfg.update(json.load(f))
    # "~" in paths means the user's home folder
    for key in ("llama_dir", "model", "mmproj", "brain_python", "whisper_model", "bluetts_dir"):
        cfg[key] = os.path.expanduser(cfg[key])
    return cfg


CFG = load_config()
LLAMA_URL = f"http://127.0.0.1:{CFG['llama_port']}"
BRAIN_URL = f"http://127.0.0.1:{CFG['brain_port']}"


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)


# ---------- Pairing code ----------

CODE_FILE = os.path.join(HERE, "pairing_code.txt")
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # no 0/O or 1/I to mix up
MAX_BODY = 25 * 1024 * 1024  # a recording or a drawing is far smaller


def normalize_code(code):
    """Case, spaces and dashes don't matter when typing the code."""
    return "".join(c for c in code.upper() if c.isalnum())


def load_pairing_code(new=False):
    if not new and os.path.exists(CODE_FILE):
        with open(CODE_FILE, encoding="utf-8") as f:
            code = normalize_code(f.read())
        if len(code) >= 8:
            return code
    code = "".join(secrets.choice(CODE_ALPHABET) for _ in range(8))  # 40 bits
    with open(CODE_FILE, "w", encoding="utf-8") as f:
        f.write(pretty_code(code) + "\n")
    return code


def pretty_code(code):
    return f"{code[:4]}-{code[4:]}"


class Guard:
    """Slows down and locks out addresses that keep sending wrong codes."""

    MAX_FAILS = 10        # wrong codes allowed...
    WINDOW_S = 10 * 60    # ...within this many seconds,
    LOCKOUT_S = 15 * 60   # before the address is shut out for this long

    def __init__(self):
        self.lock = threading.Lock()
        self.fails = {}    # ip -> times of recent wrong codes
        self.blocked = {}  # ip -> locked out until

    def is_blocked(self, ip):
        with self.lock:
            until = self.blocked.get(ip, 0)
            if until and time.time() >= until:
                del self.blocked[ip]
                return False
            return bool(until)

    def failed(self, ip):
        now = time.time()
        with self.lock:
            recent = [t for t in self.fails.get(ip, []) if now - t < self.WINDOW_S] + [now]
            self.fails[ip] = recent
            if len(recent) >= self.MAX_FAILS:
                self.blocked[ip] = now + self.LOCKOUT_S
                self.fails.pop(ip, None)
                log(f"{ip} sent too many wrong pairing codes, locked out for {self.LOCKOUT_S // 60} minutes")
                EVENTS.add("lockout", ip=ip, text=f"too many wrong pairing codes, locked out for {self.LOCKOUT_S // 60} minutes")

    def succeeded(self, ip):
        with self.lock:
            self.fails.pop(ip, None)


PAIRING_CODE = load_pairing_code(new="--new-code" in sys.argv)
GUARD = Guard()
EVENTS = dashboard.Events()  # what the dashboard's log shows; memory only


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
        self.device = None       # "GPU" or "CPU" while awake
        self.awake_since = None

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
        self.device = "GPU" if gpu_layers else "CPU"
        log(f"waking brain on {self.device}")
        EVENTS.add("brain", text=f"waking up on the {self.device}")
        flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
        llama = subprocess.Popen(
            [os.path.join(CFG["llama_dir"], "llama-server"), "-m", CFG["model"],
             "--host", "127.0.0.1", "--port", str(CFG["llama_port"]),
             "-ngl", str(gpu_layers), "-c", str(CFG["context"]), "--no-webui"]
            + (["--mmproj", CFG["mmproj"]] if CFG.get("mmproj") and os.path.exists(CFG["mmproj"]) else []),
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
                self.awake_since = time.time()
                log("brain ready")
                EVENTS.add("brain", text="awake")
                return
            time.sleep(0.5)
        log("brain failed to start")
        self.stop("failed to start")

    def stop(self, reason=None):
        for p in self.procs:
            if p.poll() is None:
                p.kill()
        self.procs = []
        with self.lock:
            self.state = "cold"
        self.device = None
        self.awake_since = None
        if reason:
            EVENTS.add("brain", text=f"asleep ({reason})")

    def idle_watch(self):
        while True:
            time.sleep(20)
            if self.state == "ready" and time.time() - self.last_used > CFG["idle_minutes"] * 60:
                log("idle, putting brain to sleep")
                self.stop(f"idle for {CFG['idle_minutes']} minutes")


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

    def _authorized(self):
        """Checks the pairing code; answers the request itself (401/429) when it's wrong."""
        ip = self.client_address[0]
        if GUARD.is_blocked(ip):
            self._json(429, {"error": "too many wrong pairing codes, try again later"})
            return False
        sent = normalize_code(self.headers.get("X-Puki-Code", ""))
        if hmac.compare_digest(sent.encode(), PAIRING_CODE.encode()):
            GUARD.succeeded(ip)
            return True
        time.sleep(1)  # makes guessing slow
        EVENTS.add("denied", ip=ip, text="wrong or missing pairing code")
        GUARD.failed(ip)
        self._json(401, {"error": "wrong pairing code"})
        return False

    def do_GET(self):
        if not self._authorized():
            return
        if self.path == "/health":
            self._json(200, {"brain": BRAIN.state})
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        if not self._authorized():
            return
        length = int(self.headers.get("Content-Length", 0) or 0)
        if length < 0 or length > MAX_BODY:
            self._json(413, {"error": "too large"})
            return
        body = self.rfile.read(length) if length else b"{}"
        path = self.path.split("?")[0]  # the app adds ?name=...&gender=... to every request
        if path == "/wake":
            BRAIN.wake()
            BRAIN.touch()
            self._json(200, {"brain": BRAIN.state})
        elif path in ("/say", "/listen", "/speak", "/look", "/story"):
            BRAIN.wake()
            BRAIN.touch()
            deadline = time.time() + CFG["startup_timeout_s"]
            while BRAIN.state == "warming" and time.time() < deadline:
                time.sleep(0.2)
            if BRAIN.state != "ready":
                self.send_response(503)
                self.end_headers()
                return
            started = time.time()
            event = describe_request(path, self.path, body)
            event["ip"] = self.client_address[0]
            try:
                ctype = self.headers.get("Content-Type", "application/json")
                req = urllib.request.Request(f"{BRAIN_URL}{self.path}", data=body, headers={"Content-Type": ctype})
                with urllib.request.urlopen(req, timeout=120) as r:
                    data = r.read()
                    self.send_response(r.status)
                    for h in ("Content-Type", "X-Text", "X-Heard"):
                        if r.headers.get(h):
                            self.send_header(h, r.headers[h])
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
                    event["status"] = r.status
                    event["reply"] = describe_reply(path, r.headers, data)
                    if r.headers.get("X-Heard"):
                        event["heard"] = urllib.parse.unquote(r.headers["X-Heard"])
            except urllib.error.HTTPError as e:
                self.send_response(e.code)
                self.end_headers()
                event["status"] = e.code
            except Exception as e:
                log(f"say failed: {e}")
                self.send_response(502)
                self.end_headers()
                event["status"] = 502
            event["ms"] = int((time.time() - started) * 1000)
            EVENTS.add(path.strip("/"), **event)
            BRAIN.touch()
        else:
            self._json(404, {"error": "not found"})


# ---------- For the dashboard ----------

def describe_request(path, full_path, body):
    """What the tablet asked, in a line for the dashboard's log."""
    query = urllib.parse.parse_qs(urllib.parse.urlsplit(full_path).query)
    try:
        req = json.loads(body) if path in ("/say", "/speak", "/story") else {}
    except ValueError:
        req = {}
    if path == "/say":
        return {"text": req.get("situation", ""), "lang": req.get("lang", "he")}
    if path == "/speak":
        return {"text": req.get("text", ""), "lang": req.get("lang", "he")}
    if path == "/story":
        return {"text": "bedtime story: " + str(req.get("played", "")), "lang": req.get("lang", "he")}
    if path == "/look":
        return {"text": f"looked at a drawing ({len(body) // 1024} KB)", "lang": query.get("lang", ["he"])[0]}
    if path == "/listen":
        context = query.get("context", [""])[0]
        return {"text": "listened" + (f" ({context})" if context else ""), "lang": query.get("lang", ["he"])[0]}
    return {"text": path}


def describe_reply(path, headers, data):
    if headers.get("X-Text"):
        return urllib.parse.unquote(headers["X-Text"])
    if path == "/story":
        try:
            return " ".join(s.get("text", "") for s in json.loads(data).get("sentences", []))
        except ValueError:
            pass
    return ""


def lan_address():
    """This PC's address on the home network (no packet is sent)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def save_local_config(key, value):
    """Remembers a setting changed from the dashboard in config.json."""
    path = os.path.join(HERE, "config.json")
    local = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            local = json.load(f)
    local[key] = value
    with open(path, "w", encoding="utf-8") as f:
        json.dump(local, f, ensure_ascii=False, indent=2)
        f.write("\n")


class DashboardApp:
    events = EVENTS

    def __init__(self):
        self._vram = (0.0, 0)

    def pair_url(self):
        # What the app's QR scanner reads: where the PC is, and the code
        return f"puki://pair?host={lan_address()}:{CFG['port']}&code={PAIRING_CODE}"

    def free_vram(self):
        if time.time() - self._vram[0] > 10:  # nvidia-smi is slowish; the page polls every 2 s
            self._vram = (time.time(), free_vram_mb())
        return self._vram[1]

    def status(self):
        now = time.time()
        sleeps_in = None
        if BRAIN.state == "ready":
            sleeps_in = max(0, int(CFG["idle_minutes"] * 60 - (now - BRAIN.last_used)))
        return {
            "brain": BRAIN.state,
            "device": BRAIN.device,
            "awake_since": BRAIN.awake_since,
            "sleeps_in_s": sleeps_in,
            "idle_minutes": CFG["idle_minutes"],
            "free_vram_mb": self.free_vram(),
            "min_free_vram_mb": CFG["min_free_vram_mb"],
            "code": pretty_code(PAIRING_CODE),
            "address": f"{lan_address()}:{CFG['port']}",
            "pair_url": self.pair_url(),
            "now": now,
        }

    def sleep(self):
        if BRAIN.state != "cold":
            log("put to sleep from the dashboard")
            BRAIN.stop("put to sleep from the dashboard")

    def new_code(self):
        global PAIRING_CODE
        PAIRING_CODE = load_pairing_code(new=True)
        log(f"new pairing code: {pretty_code(PAIRING_CODE)}")
        EVENTS.add("pairing", text="made a new pairing code; tablets need the new one")

    def set_idle(self, minutes):
        CFG["idle_minutes"] = minutes
        save_local_config("idle_minutes", minutes)
        EVENTS.add("settings", text=f"sleeps after {minutes} idle minutes")


def main():
    threading.Thread(target=BRAIN.idle_watch, daemon=True).start()
    server = ThreadingHTTPServer((CFG["bind"], CFG["port"]), Handler)
    log(f"doorbell listening on {CFG['bind']}:{CFG['port']}")
    log(f"pairing code: {pretty_code(PAIRING_CODE)}  (type it in the app's parent settings)")
    dashboard.serve(DashboardApp(), CFG["dashboard_port"])
    log(f"dashboard: http://127.0.0.1:{CFG['dashboard_port']}  (this PC only)")
    try:
        server.serve_forever()
    finally:
        BRAIN.stop()


if __name__ == "__main__":
    sys.exit(main())
