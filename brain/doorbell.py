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
  POST /repeat?lang=he&target=כלב[&alt=הב] -> body: WAV of the child saying a word (Say it with Puki)
                   -> JSON {"heard", "match": "word" | "close" | "tried"}

Everything goes over HTTPS with the PC's own certificate (tls.py, made on first start); the
app trusts it by its fingerprint, which the pairing QR code carries.

Every request must carry the pairing code in an X-Puki-Code header. The code is
made on first start, kept in pairing_code.txt next to this file, and printed at
startup; the parent types it into the app once. `python doorbell.py --new-code`
makes a new one (every tablet then needs the new code). Wrong codes are slowed
down, and an address that keeps guessing is locked out for a while.

A dashboard for the parent runs next to it on this PC only (dashboard.py,
http://127.0.0.1:8793 by default): status, the pairing code as a QR code, and a
live log of what Puki was asked and said.

Standard library only, so it runs with any Python 3 (making the certificate, once, borrows the
brain's Python, see tls.py).
"""

import hmac
import json
import os
import queue
import shutil
import ssl
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import dashboard
from common import load_config as _load_config, normalize_code, pairing_code, pair_url, port_open, pretty_code, write_private
from common import lan_address as _lan_address
import reports
import tls

# Puki.exe unpacks the brain's files elsewhere and says where (tray.py)
HERE = os.environ.get("PUKI_BRAIN_DIR") or os.path.dirname(os.path.abspath(__file__))


def load_config():
    cfg = _load_config(HERE)
    # "~" in paths means the user's home folder
    for key in ("llama_dir", "model", "mmproj", "brain_python", "whisper_model", "parakeet_model", "model_12b", "mmproj_12b", "bluetts_dir", "renikud_model"):
        cfg[key] = os.path.expanduser(cfg[key])
    return cfg


CFG = load_config()
LLAMA_URL = f"http://127.0.0.1:{CFG['llama_port']}"
BRAIN_URL = f"http://127.0.0.1:{CFG['brain_port']}"


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)


# ---------- Pairing code ----------

CODE_FILE = os.path.join(HERE, "pairing_code.txt")
MAX_BODY = 25 * 1024 * 1024  # a recording or a drawing is far smaller
ERRORS_FILE = os.path.join(HERE, "brain_errors.log")  # the brain's errors when run without a console


def load_pairing_code(new=False):
    return pairing_code(CODE_FILE, new)


def lan_address():
    return _lan_address() or "127.0.0.1"


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
CERT_FP = ""  # the HTTPS certificate's fingerprint, set by start()
GUARD = Guard()
EVENTS = dashboard.Events()  # what the dashboard's log shows; memory only


GPU = {"error": None, "lock": threading.Lock()}  # why nvidia-smi fails, while it does


def free_vram_mb():
    """Free GPU memory in MiB, or 0 if there's no usable NVIDIA GPU.

    No nvidia-smi means no NVIDIA driver. An nvidia-smi that fails (most often: the driver was
    updated and the PC not restarted) also gives 0, so the brain wakes on the CPU, but it goes to
    the log and the dashboard when it starts and when it clears."""
    free, error, found = 0, None, shutil.which("nvidia-smi")
    if found:
        try:
            r = subprocess.run(
                ["nvidia-smi", "--query-gpu=memory.free", "--format=csv,noheader,nounits"],
                capture_output=True, text=True, timeout=10,
                # Without a console (pythonw), each call would flash a terminal window
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            )
        except subprocess.TimeoutExpired:
            error = "no answer within 10 seconds"
        else:
            lines = r.stdout.strip().splitlines()
            if r.returncode != 0:
                error = ((r.stdout + r.stderr).strip().splitlines() or [f"exit code {r.returncode}"])[0]
            elif not lines or not lines[0].strip().isdigit():
                error = f"unexpected answer {r.stdout.strip()!r}"
            else:
                free = int(lines[0])
    with GPU["lock"]:
        if error != GPU["error"]:
            GPU["error"] = error
            text = (f"nvidia-smi fails ({error}), so the brain uses the CPU" if error
                    else "nvidia-smi works again" if found else "nvidia-smi is gone, so there's no NVIDIA GPU")
            log(text)
            EVENTS.add("gpu", text=text)
    return free


def http_ok(url, timeout=2):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            return r.status == 200
    except Exception:
        return False


def models():
    """The Gemmas this PC can run: (file, vision part, free VRAM needed on the GPU, name)."""
    return {
        "e4b": (CFG["model"], CFG.get("mmproj"), CFG["min_free_vram_mb"], "E4B"),
        "12b": (CFG.get("model_12b", ""), CFG.get("mmproj_12b"), CFG.get("min_free_vram_mb_12b", 9500), "12B"),
    }


def installed(key):
    return bool(models()[key][0]) and os.path.exists(models()[key][0])


def pick_model(free_mb):
    """(model, gpu_layers): the chosen Gemma if it fits the GPU, else one that does, else the
    lightest installed one on the CPU."""
    choice = CFG.get("model_choice", "e4b")
    order = [k for k in ([choice] + [k for k in ("e4b", "12b") if k != choice]) if installed(k)] or ["e4b"]
    # Only fall back to a smaller model, never up to a bigger one than chosen
    order = [k for k in order if models()[k][2] <= models()[order[0]][2]]
    for k in order:
        if free_mb >= models()[k][2]:
            return models()[k], 99
    return min((models()[k] for k in order), key=lambda m: m[2]), 0


class Brain:
    """Starts, watches and stops the two heavy processes."""

    def __init__(self):
        self.lock = threading.Lock()
        self.procs = []
        self.state = "cold"
        self.last_used = 0.0
        self.device = None       # "GPU" or "CPU" while awake
        self.model = None        # "E4B" or "12B" while awake
        self.awake_since = None

    def touch(self):
        self.last_used = time.time()

    def wake(self):
        self.check()
        with self.lock:
            if self.state != "cold":
                return
            self.state = "warming"
        threading.Thread(target=self._start, daemon=True).start()

    def _start(self):
        # The chosen Gemma on the GPU if there's room (training runs may be using it), else
        # a smaller one that fits, else the lighter one on the CPU
        (model, mmproj, _, name), gpu_layers = pick_model(free_vram_mb())
        self.device = "GPU" if gpu_layers else "CPU"
        self.model = name
        log(f"waking brain: Gemma {name} on {self.device}")
        EVENTS.add("brain", text=f"waking up: Gemma {name} on the {self.device}")
        flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
        # From the login shortcut (pythonw) there's no console: don't pop up console windows,
        # and keep the brain's errors in a file so a failed start can be explained
        no_console = sys.stdout is None
        if no_console and os.name == "nt":
            flags |= subprocess.CREATE_NO_WINDOW
        errors = open(ERRORS_FILE, "w", encoding="utf-8") if no_console else None
        # UTF-8 mode: otherwise BlueTTS reads its data files in the Windows code page and crashes
        # Offline: the installer brings every model, so anything that tries to fetch one fails
        # instead of quietly downloading
        env = dict(os.environ, PYTHONUTF8="1", PYTHONIOENCODING="utf-8", HF_HUB_OFFLINE="1")
        if os.name != "nt":
            # Linux finds shared libraries through LD_LIBRARY_PATH, set before the process starts:
            # llama.cpp's own (next to llama-server) and the CUDA ones pip put in the brain's venv
            import glob
            venv = os.path.dirname(os.path.dirname(CFG["brain_python"]))
            libs = [CFG["llama_dir"]] + glob.glob(os.path.join(venv, "lib", "python3*", "site-packages", "nvidia", "*", "lib"))
            env["LD_LIBRARY_PATH"] = os.pathsep.join(libs + [os.environ.get("LD_LIBRARY_PATH", "")]).rstrip(os.pathsep)
        llama = KILL_ON_CLOSE.popen(
            [os.path.join(CFG["llama_dir"], "llama-server"), "-m", model,
             "--host", "127.0.0.1", "--port", str(CFG["llama_port"]),
             "-ngl", str(gpu_layers), "-c", str(CFG["context"]), "--no-webui"]
            + (["--mmproj", mmproj] if mmproj and os.path.exists(mmproj) else []),
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=flags, env=env,
        )
        brain = KILL_ON_CLOSE.popen(
            [CFG["brain_python"], os.path.join(HERE, "brain.py")],
            cwd=HERE, creationflags=flags, env=env,
            stdout=subprocess.DEVNULL if no_console else None, stderr=errors,
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
        self.stop("failed to start; errors are in brain/brain_errors.log" if no_console else "failed to start")

    def check(self):
        """Notices a brain that died while awake, so it isn't reported ready (and every request
        answered 502) until it's put to sleep; the next request wakes it again."""
        if self.state != "ready":
            return
        dead = [f"{name} exited with {p.returncode}"
                for name, p in zip(("llama-server", "brain.py"), self.procs) if p.poll() is not None]
        if dead:
            log("brain stopped while awake: " + ", ".join(dead))
            self.stop("stopped while awake: " + ", ".join(dead))

    def stop(self, reason=None):
        for p in self.procs:
            if p.poll() is None:
                p.kill()
        for p in self.procs:
            p.wait()  # gone, GPU memory and all, before a new wake measures what's free
        self.procs = []
        with self.lock:
            self.state = "cold"
        self.device = None
        self.model = None
        self.awake_since = None
        if reason:
            EVENTS.add("brain", text=f"asleep ({reason})")

    def idle_watch(self):
        while True:
            time.sleep(20)
            self.check()
            if self.state == "ready" and time.time() - self.last_used > CFG["idle_minutes"] * 60:
                log("idle, putting brain to sleep")
                self.stop(f"idle for {CFG['idle_minutes']} minutes")


BRAIN = Brain()


# ---------- The brain never outlives the doorbell ----------
# If the doorbell is killed (Task Manager, a crash, an update), llama-server and brain.py would
# keep running and hold the GPU with no one left to put them to sleep.

class _KillOnClose:
    """Windows: a job object that kills everything in it when the doorbell exits, however it
    exits. Linux: each child asks the kernel for SIGKILL when its parent dies (PR_SET_PDEATHSIG).
    That signal comes when the *thread* that started the child ends, not the process, and the
    brain wakes on a short-lived thread: so on Linux every child is started by one thread that
    lives as long as the doorbell."""

    def __init__(self):
        self.job = None
        self.spawns = queue.Queue()
        if sys.platform.startswith("linux"):
            threading.Thread(target=self._spawner, daemon=True, name="spawner").start()
        if os.name == "nt":
            try:
                import ctypes
                from ctypes import wintypes
                k32 = ctypes.WinDLL("kernel32", use_last_error=True)
                k32.CreateJobObjectW.restype = wintypes.HANDLE

                class Basic(ctypes.Structure):
                    _fields_ = [("PerProcessUserTimeLimit", ctypes.c_int64), ("PerJobUserTimeLimit", ctypes.c_int64),
                                ("LimitFlags", wintypes.DWORD), ("MinimumWorkingSetSize", ctypes.c_size_t),
                                ("MaximumWorkingSetSize", ctypes.c_size_t), ("ActiveProcessLimit", wintypes.DWORD),
                                ("Affinity", ctypes.c_size_t), ("PriorityClass", wintypes.DWORD),
                                ("SchedulingClass", wintypes.DWORD)]

                class Io(ctypes.Structure):
                    _fields_ = [(n, ctypes.c_uint64) for n in ("ReadOps", "WriteOps", "OtherOps",
                                                               "ReadBytes", "WriteBytes", "OtherBytes")]

                class Extended(ctypes.Structure):
                    _fields_ = [("Basic", Basic), ("Io", Io), ("ProcessMemoryLimit", ctypes.c_size_t),
                                ("JobMemoryLimit", ctypes.c_size_t), ("PeakProcessMemoryUsed", ctypes.c_size_t),
                                ("PeakJobMemoryUsed", ctypes.c_size_t)]

                job = k32.CreateJobObjectW(None, None)
                info = Extended()
                info.Basic.LimitFlags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
                if job and k32.SetInformationJobObject(job, 9, ctypes.byref(info), ctypes.sizeof(info)):  # 9: extended limits
                    self.job, self.k32 = job, k32
            except Exception as e:
                log(f"couldn't tie the brain to the doorbell ({e}); it may outlive a crash")

    def popen(self, args, **kwargs):
        """subprocess.Popen, for a child that dies with the doorbell."""
        if not sys.platform.startswith("linux"):
            proc = subprocess.Popen(args, **kwargs)
            if self.job:  # (brain.py's own children join the job too)
                import ctypes
                self.k32.AssignProcessToJobObject(self.job, ctypes.c_void_p(int(proc._handle)))
            return proc
        result, done = [], threading.Event()
        self.spawns.put((args, kwargs, result, done))
        done.wait()
        if isinstance(result[0], Exception):
            raise result[0]
        return result[0]

    def _spawner(self):
        while True:
            args, kwargs, result, done = self.spawns.get()
            try:
                result.append(subprocess.Popen(args, preexec_fn=self._die_with_parent, **kwargs))
            except Exception as e:  # raised again in the caller's thread (this one must keep running)
                result.append(e)
            done.set()

    @staticmethod
    def _die_with_parent():
        """Runs in the child before exec: SIGKILL it when the spawner thread (the doorbell) ends."""
        import ctypes
        import signal
        ctypes.CDLL("libc.so.6", use_errno=True).prctl(1, signal.SIGKILL)  # PR_SET_PDEATHSIG


KILL_ON_CLOSE = _KillOnClose()


def clear_leftovers():
    """Stops a llama-server or brain.py left running by an earlier doorbell (one that was killed
    before this version), so the new one gets the ports and the GPU memory back."""
    ports = [CFG["llama_port"], CFG["brain_port"]]
    if not any(port_open(p) for p in ports):
        return
    log("an earlier brain is still running; stopping it")
    if os.name == "nt":
        ps = ("Get-NetTCPConnection -State Listen -LocalPort " + ",".join(map(str, ports)) + " -ErrorAction SilentlyContinue | "
              "ForEach-Object { Get-CimInstance Win32_Process -Filter \"ProcessId=$($_.OwningProcess)\" } | "
              "Where-Object { $_.Name -eq 'llama-server.exe' -or $_.CommandLine -match 'brain[.]py' } | "
              "ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue; "
              "Get-CimInstance Win32_Process -Filter \"ParentProcessId=$($_.ProcessId)\" | "
              "ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } }")
        subprocess.run(["powershell", "-NoProfile", "-Command", ps], capture_output=True,
                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    else:
        for pattern in (os.path.join(HERE, "brain.py"), os.path.join(CFG["llama_dir"], "llama-server")):
            subprocess.run(["pkill", "-f", pattern], capture_output=True)
    for _ in range(20):
        if not any(port_open(p) for p in ports):
            break
        time.sleep(0.5)


class Handler(BaseHTTPRequestHandler):
    # A connection that never finishes its request is dropped after this many seconds, so a
    # device on the network can't tie up the doorbell (the code check is never reached then)
    timeout = 10

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
        elif path == "/report":
            # מלשין לרנינג: a parent flagged a line of Puki's on the tablet (kept here, see reports.py)
            try:
                req = json.loads(body[:8000] or b"{}")
            except ValueError:
                self._json(400, {"error": "not JSON"})
                return
            entry = reports.add("tablet", req.get("said"), req.get("heard"), req.get("situation"),
                                req.get("lang"), req.get("game"), req.get("note"))
            EVENTS.add("report", text="reported from the tablet", reply=entry["said"], heard=entry["heard"])
            self._json(200, {"ok": True})
        elif path in ("/say", "/listen", "/speak", "/look", "/story", "/repeat"):
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
    if path == "/repeat":
        return {"text": "say it: " + query.get("target", [""])[0], "lang": query.get("lang", ["he"])[0]}
    if path == "/listen":
        context = query.get("context", [""])[0]
        return {"text": "listened" + (f" ({context})" if context else ""), "lang": query.get("lang", ["he"])[0],
                "game": query.get("game", [""])[0]}
    return {"text": path}


def describe_reply(path, headers, data):
    if headers.get("X-Text"):
        return urllib.parse.unquote(headers["X-Text"])
    if path == "/story":
        try:
            return " ".join(s.get("text", "") for s in json.loads(data).get("sentences", []))
        except ValueError:
            pass
    if path == "/repeat":
        try:
            r = json.loads(data)
            said = {"word": "said the word", "close": "close enough", "tried": "tried"}.get(r.get("match"), "?")
            return f"{said} (heard: {r.get('heard') or 'nothing clear'})"
        except ValueError:
            pass
    return ""


def save_local_config(key, value):
    """Remembers a setting changed from the dashboard in config.json."""
    path = os.path.join(HERE, "config.json")
    local = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            local = json.load(f)
    local[key] = value
    write_private(path, json.dumps(local, ensure_ascii=False, indent=2) + "\n")  # holds the child's name


class DashboardApp:
    events = EVENTS

    def __init__(self):
        self._vram = (0.0, 0)

    def pair_url(self):
        return pair_url(lan_address(), CFG["port"], PAIRING_CODE, tls.for_link(CERT_FP))

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
            "model": BRAIN.model,
            "model_choice": CFG.get("model_choice", "e4b"),
            "models_installed": {k: installed(k) for k in ("e4b", "12b")},
            "min_free_vram_mb_12b": CFG.get("min_free_vram_mb_12b", 9500),
            "awake_since": BRAIN.awake_since,
            "sleeps_in_s": sleeps_in,
            "idle_minutes": CFG["idle_minutes"],
            "free_vram_mb": self.free_vram(),
            "gpu_error": GPU["error"],
            "min_free_vram_mb": CFG["min_free_vram_mb"],
            "code": pretty_code(PAIRING_CODE),
            "address": f"{lan_address()}:{CFG['port']}",
            "fingerprint": " ".join(CERT_FP[i:i + 4] for i in range(0, 16, 4)),
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
        log("made a new pairing code (it's on the dashboard)")
        EVENTS.add("pairing", text="made a new pairing code; tablets need the new one")

    def set_model(self, choice):
        CFG["model_choice"] = choice
        save_local_config("model_choice", choice)
        EVENTS.add("settings", text=f"Gemma {choice.upper()} from the next wake-up")

    def report(self, event_id):
        """מלשין לרנינג: a parent flagged a line in the dashboard's log. False if it's gone."""
        event = next((e for e in list(EVENTS.items) if e["id"] == event_id), None)
        if not event or not event.get("reply"):
            return False
        reports.add("dashboard", event["reply"], event.get("heard", ""), event.get("text", ""),
                    event.get("lang", "he"), event.get("game", ""))
        EVENTS.add("report", text="reported from the dashboard", reply=event["reply"], heard=event.get("heard", ""))
        return True

    def set_idle(self, minutes):
        CFG["idle_minutes"] = minutes
        save_local_config("idle_minutes", minutes)
        EVENTS.add("settings", text=f"sleeps after {minutes} idle minutes")


class TLSServer(ThreadingHTTPServer):
    """HTTPS only. Each connection does its handshake in its own thread, under the handler's
    timeout, so a slow or silent device can't hold up the others."""

    def __init__(self, address, handler, context):
        super().__init__(address, handler)
        self.context = context

    def get_request(self):
        sock, address = super().get_request()
        return self.context.wrap_socket(sock, server_side=True, do_handshake_on_connect=False), address

    def handle_error(self, request, client_address):
        err = sys.exc_info()[1]
        if isinstance(err, (ssl.SSLError, ConnectionError, TimeoutError, OSError)):
            # Most often an older app still speaking plain HTTP, or a device that went away
            EVENTS.add("denied", ip=client_address[0], text=f"connection dropped ({type(err).__name__}); "
                                                            "an app older than 0.5 needs updating")
            return
        super().handle_error(request, client_address)


def start():
    """Starts everything but the main loop (the tray runs that in a thread); returns the server."""
    global CERT_FP
    clear_leftovers()
    threading.Thread(target=BRAIN.idle_watch, daemon=True).start()
    CERT_FP = tls.ensure(CFG.get("brain_python"))
    server = TLSServer((CFG["bind"], CFG["port"]), Handler, tls.server_context())
    log(f"doorbell listening on {CFG['bind']}:{CFG['port']} (HTTPS, certificate {CERT_FP[:16]}...)")
    # Someone ran it in a terminal; a service's log shouldn't keep the code (and under pythonw or
    # Puki.exe there's no stdout at all)
    if sys.stdout is not None and sys.stdout.isatty():
        log(f"pairing code: {pretty_code(PAIRING_CODE)}  (type it in the app's parent settings)")
    else:
        log("pairing code: on the dashboard and in pairing_code.txt")
    dashboard.serve(DashboardApp(), CFG["dashboard_port"])
    log(f"dashboard: http://127.0.0.1:{CFG['dashboard_port']}  (this PC only)")
    return server


def main():
    server = start()
    try:
        server.serve_forever()
    finally:
        BRAIN.stop()


if __name__ == "__main__":
    sys.exit(main())
