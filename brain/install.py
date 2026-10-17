#!/usr/bin/env python3
"""Sets up Puki's brain on this PC, step by step.

    python brain\\install.py                 # everything, asking before it starts
    python brain\\install.py --dry-run       # just show what it would do
    python brain\\install.py --dir D:\\puki-brain --yes

It downloads llama.cpp (the CUDA build when there's an NVIDIA GPU, otherwise the CPU
one), Gemma and its vision part (E4B, or the smarter 12B on a GPU with 12 GB or more; see
--model), the ivrit.ai Hebrew Whisper, Parakeet for English, and
BlueTTS with its voice models and Hebrew pronunciation (about 10 GB in all). Then it creates the Python environment,
writes brain/config.json, adds the home-network firewall rule (one Windows admin prompt),
starts Puki at login, and opens the dashboard with the pairing code.

Safe to run again: finished steps and complete downloads are skipped, and an interrupted
download resumes where it stopped. Everything it downloads is pinned in pins.json (Hugging
Face files at a fixed commit, and the SHA-256 of each file; make_pins.py writes it) and
checked against it, so a file changed upstream never gets installed. Before starting it shows how much it will download and about how long that
takes; everything it prints also goes to install.log in the folder. At the end it shows the
pairing code and a QR code for the tablet. --gui shows all this in a window instead (Puki.exe
always does). Standard library only, so any Python 3.10+ can run it.

    python brain\\install.py --update      # the newest signed Puki release, then only what changed
    python brain\\install.py --uninstall   # remove it all (asks before each part)
"""

import argparse
import filecmp
import hashlib
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.request
import zipfile

import bundle
from common import SIGN_NAMESPACE, lan_address, pair_url, pairing_code, port_open, ssh_keygen, write_private
from common import load_config as _load_config

# Puki.exe unpacks the brain's files elsewhere and says where (tray.py)
HERE = os.environ.get("PUKI_BRAIN_DIR") or os.path.dirname(os.path.abspath(__file__))

# The versions Puki was tested with
FROZEN = getattr(sys, "frozen", False)  # running inside Puki.exe
WINDOWS = os.name == "nt"
# Inside Puki.exe there's no console, so each command the setup runs would open (and flash) a
# console window of its own: these keep them hidden. From a terminal they share its console
NO_WINDOW = {"creationflags": 0x08000000} if WINDOWS and (FROZEN or sys.stdout is None) else {}  # CREATE_NO_WINDOW
VENV_PY = os.path.join(".venv", "Scripts", "python.exe") if WINDOWS else os.path.join(".venv", "bin", "python")
LLAMA_SERVER = "llama-server.exe" if WINDOWS else "llama-server"
SERVICE = os.path.expanduser("~/.config/systemd/user/puki.service")  # Linux: starts Puki at login
LLAMA_TAG = "b11669"
UV_VERSION = "0.12.21"  # makes the Python environment when there's no Python (Puki.exe)
BLUETTS_COMMIT = "0e38dbf08ed53f85863d1eab092bd9572c53a503"
GEMMA_REPO = "unsloth/gemma-4-E4B-it-GGUF"
GEMMA_FILE = "gemma-4-E4B-it-Q4_0.gguf"
MMPROJ_FILE = "mmproj-F16.gguf"
# The bigger, smarter one: needs about 9 GB of free GPU memory
GEMMA12_REPO = "unsloth/gemma-4-12B-it-qat-GGUF"
GEMMA12_FILE = "gemma-4-12B-it-qat-UD-Q4_K_XL.gguf"
MMPROJ12_NAME = "mmproj-gemma-4-12B-F16.gguf"
WHISPER_REPO = "ivrit-ai/whisper-large-v3-turbo-ct2"
PARAKEET_REPO = "istupakov/parakeet-tdt-0.6b-v2-onnx"
PARAKEET_FILES = ["encoder-model.int8.onnx", "decoder_joint-model.int8.onnx", "nemo128.onnx", "vocab.txt", "config.json"]
BLUETTS_ONNX_REPO = "notmax123/BlueTTS2.5-onnx"
# BlueTTS's Hebrew pronunciation. Left to itself, renikud-plus fetches it from Hugging Face the
# first time Puki speaks Hebrew; the installer brings it, so the brain never goes online
RENIKUD_REPO = "notmax123/RenikudPlus"
RENIKUD_FILE = "model.onnx"

REPO = "TomSadeh/puki"  # where --update gets the latest Puki (signed releases)
PORT = 8790
DASHBOARD = "http://127.0.0.1:8793"
FIREWALL_RULE = "Puki companion"
NEEDED_GB = 14  # with E4B (13.7 GB measured on Linux); 15 with 12B, 21 with both
UA = {"User-Agent": "puki-installer"}


# ---------- Output: the console, or the setup window (setup_gui.py) ----------

class ConsoleUI:
    """Everything the installer shows or asks goes through here, so a window can replace it."""

    def __init__(self):
        self.log = None
        self.quiet = False  # while planning: work out what's needed without printing it

    def open_log(self, path):
        try:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            self.log = open(path, "a", encoding="utf-8")
            self.log.write(f"\n===== {time.strftime('%Y-%m-%d %H:%M:%S')}  {' '.join(sys.argv[1:])}\n")
        except OSError:
            self.log = None

    def write_log(self, msg):
        if self.log:
            self.log.write(msg + "\n")
            self.log.flush()

    def say(self, msg=""):
        if self.quiet:
            return
        print(msg, flush=True)
        self.write_log(msg)

    def step(self, n, total, title):
        self.say(f"\n[{n}/{total}] {title}")

    def progress(self, name, done, total, speed, overall):
        # One line, redrawn with \r: a line wider than the terminal wraps, and \r only returns to
        # its last row, so every update would leave a row behind. So: the fullest line that fits
        # (the last column stays empty, as some terminals wrap on writing it), padded to clear
        # what a longer line left.
        width = shutil.get_terminal_size().columns - 1
        head = f"    {name}: {done * 100 // max(total, 1):3d}%"
        mb, rate = f"  {done / 1e6:,.0f}/{total / 1e6:,.0f} MB", f"  {speed:.1f} MB/s"
        lines = [head + mb + rate]
        if overall:
            sizes = f"{overall[0] / 1e9:.1f}/{overall[1] / 1e9:.1f} GB"
            lines = [head + mb + rate + f"   (all downloads: {sizes})", head + rate + f"  (all: {sizes})"] + lines
        line = next((l for l in lines if len(l) <= width), lines[-1][:width])
        print("\r" + line.ljust(width), end="", flush=True)

    def progress_done(self):
        print()

    def ask(self, question, default=False):
        hint = "[Y/n]" if default else "[y/N]"
        answer = input(f"{question} {hint} ").strip().lower()
        self.write_log(f"{question} {hint} {answer}")
        return default if not answer else answer in ("y", "yes")

    def choose(self, title, options, default):
        """[options]: [(key, description)]; returns a key."""
        self.say(f"\n  {title}")
        for key, text in options:
            self.say(f"    {key:5} {text}")
        while True:
            answer = input(f"  [{default}] ").strip().lower() or default
            if answer in dict(options):
                self.write_log(f"  -> {answer}")
                return answer
            print(f"  Please type one of: {', '.join(k for k, _ in options)}")

    def finish(self, code, url):
        if code:
            print(f"\n  Pairing code: {code}")  # on screen only: the install log doesn't keep it
        if url:
            qr_lines = qr_text(url)
            if qr_lines:
                print("\n  Scan this with the tablet (parent settings → scan the QR code):\n")
                for line in qr_lines:
                    print("  " + line)

    def fail(self, msg):
        self.say(f"\n  ✗ {msg}")


UI = ConsoleUI()


def say(msg=""):
    UI.say(msg)


STEPS = 9


def step(n, title, total=None):
    UI.step(n, total or STEPS, title)


def fail(msg):
    UI.quiet = False  # a failure during the silent planning pass still has to be seen
    UI.fail(msg)
    sys.exit(1)


def qr_text(text):
    """A QR code in block characters, dark on light (reads right on a dark console)."""
    try:
        sys.path.insert(0, HERE)
        import qr
        m = qr.matrix(text)
    except Exception:
        return None
    pad = 2
    n = len(m) + 2 * pad
    light = lambda r, c: not (pad <= r < len(m) + pad and pad <= c < len(m) + pad and m[r - pad][c - pad])  # noqa: E731
    lines = []
    for r in range(0, n, 2):
        row = ""
        for c in range(n):
            top, bottom = light(r, c), (light(r + 1, c) if r + 1 < n else True)
            row += "█" if top and bottom else "▀" if top else "▄" if bottom else " "
        lines.append(row)
    return lines


# ---------- Downloads ----------

def pins():
    """pins.json: the SHA-256 of every file this installer downloads (make_pins.py writes it).
    Only pinned files are downloaded, from pinned Hugging Face commits, and each must match."""
    if "data" not in PINS:
        try:
            with open(os.path.join(HERE, "pins.json"), encoding="utf-8") as f:
                PINS["data"] = json.load(f)
        except (OSError, ValueError) as e:
            fail(f"pins.json is missing or broken ({e}); get Puki again.")
    return PINS["data"]


PINS = {}


def pinned(section, key):
    entry = pins()[section].get(key)
    if entry is None:
        fail(f"pins.json has nothing for {key}: run brain/make_pins.py after changing a version.")
    return entry


# The plan: what still has to be downloaded (bytes), and how much of it is done
PLAN = {"total": 0, "done": 0, "files": []}
VERIFIED = {"path": None, "data": {}}  # files already checked against their SHA-256


def load_verified(root):
    VERIFIED["path"] = os.path.join(root, "downloads", "verified.json")
    try:
        with open(VERIFIED["path"], encoding="utf-8") as f:
            VERIFIED["data"] = json.load(f)
    except (OSError, ValueError):
        VERIFIED["data"] = {}


def mark_verified(dest, sha256):
    if not VERIFIED["path"]:
        return
    VERIFIED["data"][os.path.abspath(dest)] = sha256
    os.makedirs(os.path.dirname(VERIFIED["path"]), exist_ok=True)
    with open(VERIFIED["path"], "w", encoding="utf-8") as f:
        json.dump(VERIFIED["data"], f, indent=1)


def file_sha256(path, label):
    h = hashlib.sha256()
    total = os.path.getsize(path)
    done, start = 0, time.time()
    with open(path, "rb") as f:
        while chunk := f.read(1 << 22):
            h.update(chunk)
            done += len(chunk)
            if total > 50_000_000:
                UI.progress(f"checking {label}", done, total, done / max(time.time() - start, 0.1) / 1e6, None)
    if total > 50_000_000:
        UI.progress_done()
    return h.hexdigest()


def download(url, dest, size=None, dry=False, sha256=None, unpinned=False):
    """Downloads [url] to [dest] with a progress bar, resuming a partial .part file, and checks
    it against [sha256] (from pins.json). Only Puki's own release (--update) comes [unpinned]: its signature vouches for it."""
    if not sha256 and not unpinned:
        fail(f"no checksum pinned for {os.path.basename(dest)}; refusing to download it.")
    name = os.path.basename(dest)
    if os.path.exists(dest) and (size is None or os.path.getsize(dest) == size):
        if sha256 and not dry and VERIFIED["data"].get(os.path.abspath(dest)) != sha256:
            # Downloaded before checksums were checked (or by hand): check it once
            if file_sha256(dest, name) != sha256:
                say(f"    ! {name} doesn't match its pinned checksum; downloading it again")
                os.remove(dest)
                return download(url, dest, size, dry, sha256)
            mark_verified(dest, sha256)
            say(f"    ✓ {name} (already there, checked)")
            return
        say(f"    ✓ {name} (already there)")
        return
    part = dest + ".part"
    if dry:
        have = os.path.getsize(part) if os.path.exists(part) else 0
        if size:
            PLAN["total"] += max(size - have, 0)
            PLAN["files"].append((url, size - have))
        say(f"    → would download {name}" + (f" ({size / 1e6:,.0f} MB)" if size else ""))
        return
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    have = os.path.getsize(part) if os.path.exists(part) else 0
    headers = dict(UA)
    if have:
        headers["Range"] = f"bytes={have}-"
    for attempt in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=60) as r:
                if have and r.status != 206:  # server ignored the range: start over
                    have = 0
                total = have + int(r.headers.get("Content-Length", 0) or 0)
                mode = "ab" if have else "wb"
                start = time.time()
                done = have
                started_at = done
                with open(part, mode) as f:
                    while True:
                        chunk = r.read(1 << 20)
                        if not chunk:
                            break
                        f.write(chunk)
                        done += len(chunk)
                        PLAN["done"] += len(chunk)
                        if total:
                            speed = (done - started_at) / max(time.time() - start, 0.1) / 1e6
                            overall = (PLAN["done"], PLAN["total"]) if PLAN["total"] else None
                            UI.progress(name, done, total, speed, overall)
            UI.progress_done()
            break
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            UI.progress_done()
            say(f"    connection problem ({e}); retrying...")
            time.sleep(3 * (attempt + 1))
            have = os.path.getsize(part) if os.path.exists(part) else 0
            headers = dict(UA)
            if have:
                headers["Range"] = f"bytes={have}-"
    else:
        fail(f"couldn't download {name}. Check the internet connection and run the installer again; it resumes.")
    if size is not None and os.path.getsize(part) != size:
        fail(f"{name} came out the wrong size. Run the installer again to retry.")
    if sha256:
        if file_sha256(part, name) != sha256:
            os.remove(part)
            fail(f"{name} doesn't match its pinned checksum (a broken or tampered download), so it "
                 "was deleted. Run the installer again to download it again.")
    os.replace(part, dest)
    if sha256:
        mark_verified(dest, sha256)


def hf_files(repo):
    """{path: (size, sha256)} of the files pinned for a Hugging Face model repo."""
    return {path: tuple(v) for path, v in pinned("huggingface", repo)["files"].items()}


def hf_download(repo, files, path, dest, dry):
    if path not in files:
        fail(f"{repo}/{path} isn't in pins.json: run brain/make_pins.py.")
    size, sha = files[path]
    download(hf_url(repo, path), dest, size, dry, sha)


def github_assets(repo, tag):
    """{name: (url, size, sha256)} of the assets pinned for a GitHub release."""
    return {name: (f"https://github.com/{repo}/releases/download/{tag}/{name}", size, sha)
            for name, (size, sha) in pinned("github", f"{repo}@{tag}").items()}


def hf_url(repo, path):
    # The pinned commit, not main: what's downloaded is exactly what was pinned
    return f"https://huggingface.co/{repo}/resolve/{pinned('huggingface', repo)['revision']}/{path}"


def bluetts_archive_url():
    return f"https://github.com/maxmelichov/BlueTTS/archive/{BLUETTS_COMMIT}.zip"


# ---------- Checks ----------

def nvidia(interactive=False):
    """(name, VRAM MB, driver's CUDA version) of the NVIDIA GPU, or None when there's no NVIDIA driver.

    A driver that's there but not working (most often: updated, and the PC not restarted since)
    stops the setup, so it doesn't quietly pick the CPU build on a PC that has a GPU. When someone
    is at the keyboard ([interactive]), they can choose to go on without the GPU instead."""
    if shutil.which("nvidia-smi") is None:
        return None
    try:
        return _query_nvidia()
    except DriverProblem as problem:
        retry = "Restart the PC (after a driver update, that's usually it) and run the setup again" + \
                ("." if FROZEN else ", or add --cpu to skip the GPU.")
        if not interactive:
            fail(f"{problem}\n    {retry}")
        say(f"\n  ! {problem}")
        say(f"    {retry}")
        if UI.ask("    Continue without the GPU instead (everything on the CPU, slower)?", default=False):
            return None
        sys.exit(1)


class DriverProblem(Exception):
    """The NVIDIA driver is there, but nvidia-smi doesn't give a usable answer."""


def _query_nvidia():
    def smi(*args):
        try:
            r = subprocess.run(["nvidia-smi", *args], capture_output=True, text=True, timeout=15, **NO_WINDOW)
        except subprocess.TimeoutExpired:
            raise DriverProblem("nvidia-smi didn't answer within 15 seconds, so the NVIDIA driver seems stuck.")
        if r.returncode != 0:
            why = (r.stdout + r.stderr).strip().splitlines() or [f"exit code {r.returncode}"]
            raise DriverProblem(f"there's an NVIDIA driver, but nvidia-smi fails: {why[0]}")
        return r.stdout

    rows = smi("--query-gpu=name,memory.total", "--format=csv,noheader,nounits").strip().splitlines()
    head = smi()
    # The CUDA version picks llama.cpp's build, so it can't be guessed. Newer drivers print it as
    # "CUDA UMD Version: 13.4" (next to a separate KMD version) instead of "CUDA Version: 13.4".
    cuda = re.search(r"CUDA (?:UMD )?Version:\s*([\d.]+)", head)
    if not rows or not cuda:
        raise DriverProblem(f"nvidia-smi answered without the GPU or its CUDA version:\n{head.strip()}")
    try:
        name, mem = [x.strip() for x in rows[0].split(",")]
        return name, int(mem), float(cuda.group(1))
    except ValueError:  # e.g. memory reported as [N/A]
        raise DriverProblem(f"nvidia-smi gave an answer Puki can't read: {rows[0]!r}")


def long_paths_ok(root):
    """Python packages nest deep; past Windows' 260-character limit pip fails halfway."""
    if not WINDOWS or len(root) <= 60:
        return True
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"SYSTEM\CurrentControlSet\Control\FileSystem") as k:
            return winreg.QueryValueEx(k, "LongPathsEnabled")[0] == 1
    except OSError:
        return False


def powershell(cmd):
    # No console window: Puki.exe's tray calls this too (e.g. "Start with Windows"), and each call
    # would flash one on the screen
    return subprocess.run(["powershell", "-NoProfile", "-Command", cmd], capture_output=True, text=True,
                          creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))


# ---------- Steps ----------

def read_marker(folder):
    """The version this installer put in [folder], or None (not installed by it, or older)."""
    try:
        with open(os.path.join(folder, "PUKI_VERSION.txt"), encoding="utf-8") as f:
            return f.read().strip()
    except OSError:
        return None


def write_marker(folder, version):
    with open(os.path.join(folder, "PUKI_VERSION.txt"), "w", encoding="utf-8") as f:
        f.write(version + "\n")


def install_llama(root, gpu, dry):
    dest = os.path.join(root, "llama")
    if WINDOWS:
        flavor = ("win-cuda-13.4-x64" if gpu[2] >= 13 else "win-cuda-12.4-x64") if gpu else "win-cpu-x64"
        wanted = [f"llama-{LLAMA_TAG}-bin-{flavor}.zip"] + ([f"cudart-llama-bin-{flavor}.zip"] if gpu else [])
    else:
        flavor = ("ubuntu-cuda-13.4-x64" if gpu[2] >= 13 else "ubuntu-cuda-12.8-x64") if gpu else "ubuntu-x64"
        wanted = [f"llama-{LLAMA_TAG}-bin-{flavor}.tar.gz"] + ([f"cudart-llama-{LLAMA_TAG}-bin-{flavor}.tar.gz"] if gpu else [])
    version = f"{LLAMA_TAG} {flavor}"
    if os.path.exists(os.path.join(dest, LLAMA_SERVER)):
        have = read_marker(dest)
        if have is None or have == version:  # no marker: set up by hand, leave it alone
            say(f"    ✓ llama.cpp (already there{'' if have is None else ', ' + LLAMA_TAG})")
            return
        say(f"    llama.cpp {have} → {version}")
        if dry:
            say("    → would replace it")
        else:
            shutil.rmtree(dest)
    assets = github_assets("ggml-org/llama.cpp", LLAMA_TAG)
    for name in wanted:
        if name not in assets:
            fail(f"llama.cpp {LLAMA_TAG} has no {name}")
        url, size, sha = assets[name]
        z = os.path.join(root, "downloads", name)
        download(url, z, size, dry, sha)
        if not dry:
            extract(z, dest)
    if not dry:
        write_marker(dest, version)
        say(f"    ✓ llama.cpp {LLAMA_TAG} ({flavor})")


def extract(archive, dest):
    """Unpacks a .zip or .tar.gz into [dest]; a single top folder in the archive is flattened."""
    tmp = dest + ".unpack"
    shutil.rmtree(tmp, ignore_errors=True)
    if archive.endswith(".zip"):
        with zipfile.ZipFile(archive) as f:
            f.extractall(tmp)
    else:
        with tarfile.open(archive) as f:
            f.extractall(tmp, **({"filter": "data"} if hasattr(tarfile, "data_filter") else {}))
    entries = os.listdir(tmp)
    top = os.path.join(tmp, entries[0]) if len(entries) == 1 and os.path.isdir(os.path.join(tmp, entries[0])) else tmp
    os.makedirs(dest, exist_ok=True)
    for name in os.listdir(top):
        target = os.path.join(dest, name)
        if os.path.isdir(target) and not os.path.islink(target):
            shutil.rmtree(target)
        elif os.path.lexists(target):
            os.remove(target)
        shutil.move(os.path.join(top, name), target)
    shutil.rmtree(tmp, ignore_errors=True)


def install_models(root, model, dry):
    models = os.path.join(root, "models")
    if model in ("e4b", "both"):
        gemma = hf_files(GEMMA_REPO)
        hf_download(GEMMA_REPO, gemma, GEMMA_FILE, os.path.join(models, GEMMA_FILE), dry)
        hf_download(GEMMA_REPO, gemma, MMPROJ_FILE, os.path.join(models, "mmproj-gemma-4-E4B-F16.gguf"), dry)
    if model in ("12b", "both"):
        gemma12 = hf_files(GEMMA12_REPO)
        hf_download(GEMMA12_REPO, gemma12, GEMMA12_FILE, os.path.join(models, GEMMA12_FILE), dry)
        hf_download(GEMMA12_REPO, gemma12, MMPROJ_FILE, os.path.join(models, MMPROJ12_NAME), dry)
    whisper = hf_files(WHISPER_REPO)
    for path in whisper:
        if not path.startswith("."):
            hf_download(WHISPER_REPO, whisper, path, os.path.join(models, "whisper-ivrit-turbo", path), dry)
    parakeet = hf_files(PARAKEET_REPO)
    for path in PARAKEET_FILES:
        hf_download(PARAKEET_REPO, parakeet, path, os.path.join(models, "parakeet-tdt-0.6b-v2-onnx", path), dry)


def install_bluetts(root, dry):
    dest = os.path.join(root, "BlueTTS")
    have = read_marker(dest)
    if os.path.exists(os.path.join(dest, "pyproject.toml")) and have not in (None, BLUETTS_COMMIT):
        say(f"    BlueTTS {have[:7]} → {BLUETTS_COMMIT[:7]}")
        if not dry:
            # Keep the downloaded voice models; only the code is replaced
            keep = os.path.join(root, "downloads", "onnx_models-keep")
            shutil.rmtree(keep, ignore_errors=True)
            if os.path.isdir(os.path.join(dest, "onnx_models")):
                shutil.move(os.path.join(dest, "onnx_models"), keep)
            shutil.rmtree(dest)
            install_bluetts(root, dry=False)  # fresh code
            if os.path.isdir(keep):
                shutil.rmtree(os.path.join(dest, "onnx_models"), ignore_errors=True)
                shutil.move(keep, os.path.join(dest, "onnx_models"))
            return
    if os.path.exists(os.path.join(dest, "pyproject.toml")):
        say("    ✓ BlueTTS code (already there)")
    else:
        z = os.path.join(root, "downloads", f"BlueTTS-{BLUETTS_COMMIT[:7]}.zip")
        download(bluetts_archive_url(), z, None, dry, pinned("archives", bluetts_archive_url()))
        if not dry:
            tmp = os.path.join(root, "downloads", "bluetts-unzip")
            shutil.rmtree(tmp, ignore_errors=True)
            with zipfile.ZipFile(z) as f:
                f.extractall(tmp)
            shutil.move(os.path.join(tmp, os.listdir(tmp)[0]), dest)
            shutil.rmtree(tmp, ignore_errors=True)
            write_marker(dest, BLUETTS_COMMIT)
            say("    ✓ BlueTTS code")
    onnx = hf_files(BLUETTS_ONNX_REPO)
    for path, (size, _) in onnx.items():
        if not path.startswith(".") and path != "README.md" and size:
            hf_download(BLUETTS_ONNX_REPO, onnx, path, os.path.join(dest, "onnx_models", path), dry)
    hf_download(RENIKUD_REPO, hf_files(RENIKUD_REPO), RENIKUD_FILE,
                os.path.join(root, "models", "renikud-plus", RENIKUD_FILE), dry)


def get_uv(root, dry):
    """uv: one small program that downloads Python and installs packages (used by Puki.exe)."""
    uv = os.path.join(root, "uv", "uv.exe")
    if os.path.exists(uv):
        return uv
    z = os.path.join(root, "downloads", f"uv-{UV_VERSION}.zip")
    url, size, sha = github_assets("astral-sh/uv", UV_VERSION)["uv-x86_64-pc-windows-msvc.zip"]
    download(url, z, size, dry, sha)
    if not dry:
        with zipfile.ZipFile(z) as f:
            f.extractall(os.path.dirname(uv))
    return uv


def install_venv(root, gpu, dry):
    venv = os.path.join(root, ".venv")
    py = os.path.join(root, VENV_PY)
    if dry:
        say(f"    → would create {venv} and install the Python packages" + ("" if gpu else " (without the NVIDIA ones)"))
        return py
    uv = get_uv(root, dry) if FROZEN else None
    # uv keeps its Python and cache in Puki's folder: nothing shared with other projects, gone on uninstall
    uv_env = dict(os.environ, UV_PYTHON_INSTALL_DIR=os.path.join(root, "uv", "python"),
                  UV_CACHE_DIR=os.path.join(root, "uv", "cache"))
    if not os.path.exists(py):
        say("    creating the Python environment...")
        if uv:  # Puki.exe: no Python on the PC necessarily, so uv brings one
            subprocess.run([uv, "venv", "--python", "3.12", "--python-preference", "only-managed", venv], check=True, env=uv_env, **NO_WINDOW)
        else:
            subprocess.run([sys.executable, "-m", "venv", venv], check=True, **NO_WINDOW)
    # Without an NVIDIA GPU, skip the CUDA libraries (about 1 GB)
    with open(os.path.join(HERE, "requirements.txt"), encoding="utf-8") as f:
        reqs = [l for l in f.read().splitlines() if gpu or not l.startswith("nvidia-")]
    req_file = os.path.join(root, "downloads", "requirements.txt")
    os.makedirs(os.path.dirname(req_file), exist_ok=True)
    with open(req_file, "w", encoding="utf-8") as f:
        f.write("\n".join(reqs) + "\n")
    say("    installing packages (a few minutes the first time)...")
    if uv:
        r = subprocess.run([uv, "pip", "install", "--python", py, "-q", os.path.join(root, "BlueTTS"), "-r", req_file], env=uv_env, **NO_WINDOW)
    else:
        r = subprocess.run([py, "-m", "pip", "install", "--disable-pip-version-check", "-q",
                            os.path.join(root, "BlueTTS"), "-r", req_file], **NO_WINDOW)
    if r.returncode != 0:
        fail("pip couldn't install the packages (see above). Fix that and run the installer again.")
    say("    ✓ Python environment")
    return py


def brain_dir(root):
    """Where Puki runs from: the brain's files, with its settings, pairing code and certificate
    beside them. Puki.exe unpacks its files there itself."""
    return os.path.join(root, "brain")


def install_brain_files(root, dry):
    """Copies the brain's files from here into the install folder, where Puki runs them: pulling
    or editing this folder doesn't change the running Puki until the installer runs again."""
    dest = brain_dir(root)
    if os.path.isdir(dest) and os.path.samefile(HERE, dest):
        say(f"    ✓ {dest} (this installer runs from there)")
        return
    changed = [f for f in bundle.FILES
               if not (os.path.exists(os.path.join(dest, f)) and filecmp.cmp(os.path.join(HERE, f), os.path.join(dest, f), shallow=False))]
    if not changed:
        say(f"    ✓ {dest} (up to date)")
    elif dry:
        say(f"    → would copy {len(changed)} of the brain's {len(bundle.FILES)} files to {dest}")
    else:
        os.makedirs(dest, exist_ok=True)
        for f in changed:
            shutil.copy2(os.path.join(HERE, f), os.path.join(dest, f))
        say(f"    ✓ {dest} ({len(changed)} of {len(bundle.FILES)} files new or changed)")


def write_config(root, py, model, dry):
    path = os.path.join(brain_dir(root), "config.json")
    cfg = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            cfg = json.load(f)
    m = lambda *p: os.path.join(root, *p).replace("\\", "/")  # noqa: E731
    cfg.update({
        "llama_dir": m("llama"),
        "model": m("models", GEMMA_FILE),
        "mmproj": m("models", "mmproj-gemma-4-E4B-F16.gguf"),
        "brain_python": py.replace("\\", "/"),
        "whisper_model": m("models", "whisper-ivrit-turbo"),
        "parakeet_model": m("models", "parakeet-tdt-0.6b-v2-onnx"),
        "bluetts_dir": m("BlueTTS"),
        "renikud_model": m("models", "renikud-plus", RENIKUD_FILE),
        "model_12b": m("models", GEMMA12_FILE),
        "mmproj_12b": m("models", MMPROJ12_NAME),
    })
    # Which Gemma to use (changeable later on the dashboard); keep the parent's choice if any
    if model == "12b" or (model == "both" and "model_choice" not in cfg):
        cfg["model_choice"] = "12b"
    elif model == "e4b":
        cfg["model_choice"] = "e4b"
    if dry:
        say(f"    → would write {path}")
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    write_private(path, json.dumps(cfg, ensure_ascii=False, indent=2) + "\n")  # may hold the child's name
    say(f"    ✓ {path}")


def port_rule():
    """Name of an enabled inbound rule that already opens the port, if any."""
    return powershell(
        "Get-NetFirewallRule -Direction Inbound -Action Allow -Enabled True | "
        f"Where-Object {{ ($_ | Get-NetFirewallPortFilter).LocalPort -contains '{PORT}' }} | "
        "Select-Object -First 1 -ExpandProperty DisplayName").stdout.strip()


def rule_is_local(name):
    """True if the rule only lets in this PC's own network (not any network marked Private)."""
    out = powershell(f"(Get-NetFirewallRule -DisplayName '{name}' | Get-NetFirewallAddressFilter).RemoteAddress").stdout
    return "LocalSubnet" in out


def lan_subnet():
    """The home network, e.g. 192.168.1.0/24, for a firewall rule that only lets it in."""
    ip = lan_address()
    return ".".join(ip.split(".")[:3]) + ".0/24" if ip else None


def ufw_active():
    return shutil.which("ufw") is not None and subprocess.run(
        ["systemctl", "is-active", "--quiet", "ufw"], capture_output=True).returncode == 0


def setup_firewall_linux(dry):
    subnet = lan_subnet()
    if not ufw_active():
        say("    ✓ no active ufw firewall, so the tablet can reach this PC (if you use another firewall,")
        say(f"      open TCP {PORT} to your home network only)")
        return
    cmd = ["sudo", "ufw", "allow", "from", subnet, "to", "any", "port", str(PORT), "proto", "tcp", "comment", FIREWALL_RULE]
    if dry:
        say(f"    → would ask to run: {shlex.join(cmd)}")
        return
    say(f"    ufw is on. Puki needs TCP {PORT} open to your home network ({subnet}) only:")
    say(f"      {shlex.join(cmd)}")
    if UI.ask("    Run it now (asks for your password)?", default=True):
        r = subprocess.run(cmd)
        say("    ✓ firewall rule added" if r.returncode == 0 else "    ! that didn't work; run the command above yourself")


def setup_firewall(dry):
    if not WINDOWS:
        return setup_firewall_linux(dry)
    existing = port_rule()
    # Older installs (and rules made by hand, e.g. "Olam Katan companion") opened the port to any
    # network marked Private; narrow whichever rule opens it to this one
    wide = bool(existing) and "'" not in existing and not rule_is_local(existing)
    if wide and dry:
        say(f"    → would limit the firewall rule '{existing}' to your home network (asks for admin)")
    elif wide:
        say("    Windows will ask for permission to limit the firewall rule to your home network...")
        powershell(f"Start-Process powershell -Verb RunAs -Wait -WindowStyle Hidden -ArgumentList "
                   f"\"-NoProfile -Command Set-NetFirewallRule -DisplayName '{existing}' -RemoteAddress LocalSubnet\"")
        say(f"    ✓ firewall rule '{existing}' (your home network only)" if rule_is_local(existing)
            else f"    ! the rule '{existing}' still lets in any private network (the prompt was declined?)")
    elif existing:
        say(f"    ✓ firewall rule '{existing}' (already there)")
    elif dry:
        say(f"    → would add the firewall rule '{FIREWALL_RULE}' (TCP {PORT}, home networks only; asks for admin)")
    else:
        say("    Windows will ask for permission to add the firewall rule...")
        rule = (f"New-NetFirewallRule -DisplayName '{FIREWALL_RULE}' -Direction Inbound -Protocol TCP "
                f"-LocalPort {PORT} -Profile Private -RemoteAddress LocalSubnet -Action Allow")
        powershell(f"Start-Process powershell -Verb RunAs -Wait -WindowStyle Hidden -ArgumentList \"-NoProfile -Command {rule}\"")
        if port_rule():
            say(f"    ✓ firewall rule added (TCP {PORT}, home networks only)")
        else:
            say("    ! the rule wasn't added (the permission prompt was declined?). Run the installer again,")
            say("      or see brain/README.md for the one-line command.")
    public = powershell("Get-NetConnectionProfile | Where-Object NetworkCategory -eq 'Public' | "
                        "Select-Object -ExpandProperty Name").stdout.strip()
    if public:
        say(f"    ! Your network '{public.splitlines()[0]}' is set to Public, so the tablet can't reach this PC.")
        say("      If it's your home Wi-Fi: Settings → Network & internet → Wi-Fi → (your network) →")
        say("      Network profile type → Private.")


def setup_autostart_linux(root, py, dry):
    """A systemd user service: the doorbell starts at login and restarts if it stops. It runs in
    the brain's own Python (not whichever one ran this installer), from the brain's folder."""
    brain = brain_dir(root)
    unit = f"""[Unit]
Description=Puki's brain (the doorbell for the tablet)
After=network-online.target

[Service]
ExecStart={py} {os.path.join(brain, "doorbell.py")}
WorkingDirectory={brain}
Environment=PYTHONUTF8=1
Restart=on-failure

[Install]
WantedBy=default.target
"""
    if os.path.exists(SERVICE):
        with open(SERVICE, encoding="utf-8") as f:
            if f.read() == unit:
                say("    ✓ starts at login (systemd user service 'puki', already set up)")
                return
    if dry:
        say(f"    → would {'update' if os.path.exists(SERVICE) else 'add'} the systemd user service 'puki' ({SERVICE})")
        return
    os.makedirs(os.path.dirname(SERVICE), exist_ok=True)
    with open(SERVICE, "w", encoding="utf-8") as f:
        f.write(unit)
    r = subprocess.run(["systemctl", "--user", "daemon-reload"], capture_output=True, text=True)
    r = subprocess.run(["systemctl", "--user", "enable", "puki"], capture_output=True, text=True)
    say("    ✓ starts at login (systemd user service 'puki')" if r.returncode == 0
        else f"    ! couldn't enable the service: {r.stderr.strip()}")
    say("      To have it run even before anyone logs in:  sudo loginctl enable-linger $USER")


def setup_autostart(root, py, dry):
    if not WINDOWS:
        return setup_autostart_linux(root, py, dry)
    if FROZEN:
        say("    ✓ Puki.exe will start at login (it sets that up when it opens)")
        return
    startup = startup_folder()
    link = os.path.join(startup, "Puki brain.lnk")
    # Another login shortcut that runs a doorbell (made by hand, or by an older installer from
    # the checkout) would start a second one, from somewhere without Puki's settings
    others = powershell(
        f"$sh = New-Object -ComObject WScript.Shell; Get-ChildItem '{startup}' -Filter *.lnk | "
        "Where-Object { $_.Name -ne 'Puki brain.lnk' -and $sh.CreateShortcut($_.FullName).Arguments -match 'doorbell[.]py' } | "
        "Select-Object -ExpandProperty Name").stdout.split("\n")
    for name in filter(None, map(str.strip, others)):
        say(f"    ! the login shortcut '{name}' also starts a doorbell; delete it from {startup}")
    if dry:
        say(f"    → would make Puki start at login ({link})")
        return
    brain = brain_dir(root)
    r = powershell(
        f"$s = (New-Object -ComObject WScript.Shell).CreateShortcut('{link}'); "
        f"$s.TargetPath = '{venv_pythonw(py)}'; $s.Arguments = '\"{os.path.join(brain, 'doorbell.py')}\"'; "
        f"$s.WorkingDirectory = '{brain}'; $s.Save()")
    say("    ✓ starts at login" if os.path.exists(link) else f"    ! couldn't create the login shortcut: {r.stderr.strip()}")


def venv_pythonw(py):
    """The brain's Python without a console window (Windows)."""
    return os.path.join(os.path.dirname(py), "pythonw.exe")


def start_puki(root, py, dry):
    if dry:
        say("    → would start Puki, open the dashboard, and ask Puki for a line in Hebrew and in English")
        return
    brain = brain_dir(root)
    doorbell = os.path.join(brain, "doorbell.py")
    cfg = doorbell_config(root)
    port = cfg["port"]
    if port_open(port):
        say("    ✓ Puki is already running (restart the PC, or the doorbell, to pick up new settings)")
    else:
        if not WINDOWS and os.path.exists(SERVICE):
            r = subprocess.run(["systemctl", "--user", "start", "puki"], capture_output=True, text=True)
            if r.returncode != 0:
                fail("systemd couldn't start Puki:\n" + (r.stderr or r.stdout).strip())
        elif not WINDOWS:
            subprocess.Popen([py, doorbell], cwd=brain, start_new_session=True,
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        else:
            subprocess.Popen([venv_pythonw(py), doorbell], cwd=brain, creationflags=0x00000008)  # DETACHED_PROCESS
        deadline = time.time() + cfg["startup_timeout_s"]
        while not port_open(port) and time.time() < deadline:
            time.sleep(0.5)
        if not port_open(port):
            fail(f"Puki didn't start (nothing answers on port {port}). {where_errors_are(root)}")
        say("    ✓ Puki is running")
    import webbrowser
    webbrowser.open(DASHBOARD)
    say(f"  The dashboard ({DASHBOARD}) shows the pairing code too.")
    check_puki_talks(root, port, cfg["startup_timeout_s"])


# What the installer asks Puki for at the end, to hear that everything works
HELLO = "Puki was just set up on this computer and says hello to the family"


def check_puki_talks(root, port, startup_timeout_s):
    """Wakes the brain and asks for a line in Hebrew and in English, the way the tablet does
    (HTTPS, pinned to this PC's certificate, with the pairing code): a finished install is a
    Puki who talks, not a service that started."""
    import http.client
    import ssl
    import urllib.parse
    import tls
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ctx.check_hostname, ctx.verify_mode = False, ssl.CERT_NONE  # self-signed: pinned below instead
    code = pairing(root)[0]
    cert = os.path.join(brain_dir(root), os.path.basename(tls.CERT_FILE))
    # The doorbell waits up to startup_timeout_s for the brain to wake, then 120 s for its answer
    timeout = startup_timeout_s + 120
    say("    asking Puki for a line in each language (waking the brain takes up to a minute)...")
    for lang, language in (("he", "Hebrew"), ("en", "English")):
        start = time.time()
        conn = http.client.HTTPSConnection("127.0.0.1", port, timeout=timeout, context=ctx)
        try:
            conn.connect()  # the certificate first: the pairing code goes only to this PC's Puki
            if hashlib.sha256(conn.sock.getpeercert(binary_form=True)).hexdigest() != tls.fingerprint(cert):
                fail(f"something other than Puki answered on port {port} (its certificate isn't this PC's Puki's)")
            conn.request("POST", "/say", body=json.dumps({"situation": HELLO, "lang": lang}).encode(),
                         headers={"X-Puki-Code": code, "Content-Type": "application/json"})
            r = conn.getresponse()
            audio = r.read()
        except OSError as e:
            fail(f"Puki didn't answer in {language} ({type(e).__name__}: {e}). {where_errors_are(root)}")
        finally:
            conn.close()
        if r.status != 200 or not audio:
            fail(f"Puki didn't talk in {language} (HTTP {r.status} {r.reason}). {where_errors_are(root)}")
        say(f"    ✓ {language}, {time.time() - start:.0f} s: {urllib.parse.unquote(r.getheader('X-Text', ''))}")


def where_errors_are(root):
    if WINDOWS:
        return (f"The brain's errors are in {os.path.join(brain_dir(root), 'brain_errors.log')}; running"
                "  python brain\\doorbell.py  in a console shows the doorbell's.")
    if os.path.exists(SERVICE):
        return "Its log:  journalctl --user -u puki -e"
    return f"Run  {os.path.join(root, VENV_PY)} {os.path.join(brain_dir(root), 'doorbell.py')}  to see why."


def doorbell_config(root):
    """The doorbell's settings as doorbell.py reads them: config.example.json, then config.json."""
    return _load_config(brain_dir(root))


def pairing(root):
    """The pairing code (made now if the doorbell hasn't run yet) and the link the app scans,
    with the certificate's fingerprint so the tablet pins it before it sends anything (made now
    too, if the doorbell hasn't made it yet). No link when there's no network or no certificate."""
    from common import pretty_code
    brain = brain_dir(root)
    code = pairing_code(os.path.join(brain, "pairing_code.txt"))
    ip = lan_address()
    fp = certificate_fingerprint(root)
    url = pair_url(ip, PORT, code, tls_link(fp)) if ip and fp else None
    return pretty_code(code), url


def certificate_fingerprint(root):
    """The doorbell's certificate fingerprint, making the certificate first if there's none
    (in the brain's own Python when this one lacks the cryptography package); None if that fails."""
    import tls
    brain = brain_dir(root)
    cert, key = os.path.join(brain, "puki_cert.pem"), os.path.join(brain, "puki_key.pem")
    if not (os.path.exists(cert) and os.path.exists(key)):
        try:
            tls.make(cert, key)
        except ImportError:
            py = os.path.join(root, VENV_PY)
            if not os.path.exists(py) or subprocess.run([py, os.path.join(HERE, "tls.py"), cert, key], **NO_WINDOW,
                                                        capture_output=True).returncode != 0:
                return None
    return tls.fingerprint(cert)


def tls_link(fp):
    import tls
    return tls.for_link(fp)


def startup_folder():
    return os.path.join(os.environ["APPDATA"], r"Microsoft\Windows\Start Menu\Programs\Startup")


def puki_shortcuts():
    """Login shortcuts that start Puki (made by this installer, by Puki.exe, or by hand)."""
    if not WINDOWS:
        return [SERVICE] if os.path.exists(SERVICE) else []
    out = powershell(
        f"$sh = New-Object -ComObject WScript.Shell; Get-ChildItem '{startup_folder()}' -Filter *.lnk | "
        "Where-Object { $l = $sh.CreateShortcut($_.FullName); ($l.Arguments + ' ' + $l.TargetPath) -match "
        "'doorbell[.]py|tray[.]py|Puki[.]exe' } | Select-Object -ExpandProperty FullName").stdout
    return [l.strip() for l in out.splitlines() if l.strip()]


def stop_puki(root, dry):
    """Stops the doorbell (or tray), the brain and llama-server."""
    if dry:
        say("    → would stop Puki if it's running")
        return
    if not WINDOWS:
        if os.path.exists(SERVICE):
            subprocess.run(["systemctl", "--user", "stop", "puki"], capture_output=True)
        me = {str(os.getpid()), str(os.getppid())}
        places = sorted({HERE, brain_dir(root)})
        for pattern in [os.path.join(d, f) for d in places for f in ("doorbell.py", "brain.py")] + [os.path.join(root, "llama", "llama-server")]:
            pids = subprocess.run(["pgrep", "-f", pattern], capture_output=True, text=True).stdout.split()
            for pid in set(pids) - me:
                subprocess.run(["kill", pid], capture_output=True)
        say("    ✓ Puki stopped")
        return
    powershell(
        "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'doorbell[.]py|brain[.]py|tray[.]py|Puki[.]exe' "
        f"-and ($_.CommandLine -like '*{HERE}*' -or $_.CommandLine -like '*{brain_dir(root)}*' -or $_.Name -eq 'Puki.exe') "
        f"-and $_.ProcessId -notin @({os.getpid()}, {os.getppid()}) }} | "
        "ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }; "
        f"Get-Process llama-server -ErrorAction SilentlyContinue | Where-Object {{ $_.Path -like '{root}*' }} | "
        "Stop-Process -Force -ErrorAction SilentlyContinue")
    for _ in range(20):
        if not port_open(PORT):
            break
        time.sleep(0.5)
    say("    ✓ Puki stopped")


def installed_model(root):
    """e4b / 12b / both, from what's in the models folder."""
    e4b = os.path.exists(os.path.join(root, "models", GEMMA_FILE))
    big = os.path.exists(os.path.join(root, "models", GEMMA12_FILE))
    return "both" if e4b and big else "12b" if big else "e4b"


# PC updates are signed, like the tablet's: Android installs a new APK only if Puki's release key
# signed it, and --update takes a new brain only if Puki's update key signed it (ssh-keygen's
# signatures, so nothing extra is needed: OpenSSH comes with Windows 10 and up, and with Linux).
# The key's public half is update_signers; its private half stays with Puki's makers.
SIGNERS_FILE = os.path.join(HERE, "update_signers")
RELEASE_FILE = "release.txt"  # in the signed zip: its version, so an old signed release can't pass as new


def signature_ok(path, sig, signers=SIGNERS_FILE):
    """True only if [sig] is a signature of [path] by a key in [signers], made for Puki's updates."""
    exe = ssh_keygen()
    if not exe:
        fail("ssh-keygen (OpenSSH) isn't installed, so the update's signature can't be checked; "
             "install OpenSSH and run the update again.")
    with open(path, "rb") as f:
        r = subprocess.run([exe, "-Y", "verify", "-f", signers, "-I", "puki", "-n", SIGN_NAMESPACE, "-s", sig], **NO_WINDOW,
                           stdin=f, capture_output=True)
    return r.returncode == 0


def version_tuple(v):
    return tuple(int(x) if x.isdigit() else 0 for x in v.strip().lstrip("v").split("."))


def latest_release():
    """(version, zip url, zip size, signature url) of the newest release that has a signed brain."""
    req = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases?per_page=10",
                                 headers={"Accept": "application/vnd.github+json", "User-Agent": "puki-installer"})
    with urllib.request.urlopen(req, timeout=20) as r:
        releases = json.load(r)
    best = None
    for rel in releases:
        if rel.get("draft"):
            continue
        version = rel["tag_name"].lstrip("v")
        assets = {a["name"]: a for a in rel.get("assets", [])}
        name = f"puki-brain-{version}.zip"
        if name in assets and name + ".sig" in assets and (best is None or version_tuple(version) > version_tuple(best[0])):
            best = (version, assets[name]["browser_download_url"], assets[name]["size"],
                    assets[name + ".sig"]["browser_download_url"])
    return best


def installed_release(root):
    """The version of the signed release this Puki was last updated to, or None."""
    try:
        with open(os.path.join(brain_dir(root), RELEASE_FILE), encoding="utf-8") as f:
            return f.read().strip()
    except OSError:
        return None


def unpack_release(z, dest, version):
    """Unpacks the verified zip into [dest] and checks the version it carries inside."""
    if os.path.isdir(dest):
        shutil.rmtree(dest)
    with zipfile.ZipFile(z) as f:
        top = f.namelist()[0].split("/")[0] + "/"
        for member in f.infolist():
            rel = member.filename[len(top):]
            target = os.path.normpath(os.path.join(dest, rel))
            if not rel or member.is_dir():
                continue
            if not target.startswith(os.path.normpath(dest) + os.sep):
                fail(f"the update has a file outside its folder ({member.filename}); not using it")
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with f.open(member) as src, open(target, "wb") as dst:
                shutil.copyfileobj(src, dst)
    try:
        with open(os.path.join(dest, "brain", RELEASE_FILE), encoding="utf-8") as f:
            inside = f.read().strip()
    except OSError:
        inside = None
    if inside != version:
        fail(f"the update says it's {inside}, but its release is {version}; not using it")


def update(args, root):
    """Fetches the newest signed release, checks its signature, then runs its installer, which
    only does what changed. A folder you cloned or downloaded is left as it is: the release is
    unpacked into the install folder (<folder>/update) and installed from there."""
    say("Puki: update\n")
    step(1, "The newest Puki", 3)
    if args.dry_run:
        say(f"    → would download the newest signed release from github.com/{REPO}, check its signature,"
            " stop Puki and run its installer")
        return
    try:
        latest = latest_release()
    except OSError as e:
        fail(f"couldn't reach GitHub ({e})")
    if not latest:
        fail(f"no signed release on github.com/{REPO} yet")
    version, url, size, sig_url = latest
    have = installed_release(root)
    if have and version_tuple(version) <= version_tuple(have):
        say(f"    ✓ up to date (Puki {have})")
        return
    say(f"    Puki {version}" + (f" (this one is {have})" if have else ""))
    downloads = os.path.join(root, "downloads")
    z = os.path.join(downloads, f"puki-brain-{version}.zip")
    for f in (z, z + ".sig"):
        if os.path.exists(f):
            os.remove(f)
    # Not pinned (it's newer than any pins.json): the signature below is what vouches for it
    download(url, z, size=size, unpinned=True)
    download(sig_url, z + ".sig", unpinned=True)
    step(2, "Its signature", 3)
    if not signature_ok(z, z + ".sig"):
        os.remove(z)
        fail("the update isn't signed with Puki's key, so it isn't used (it may be damaged, or not from Puki's makers).")
    say("    ✓ signed with Puki's update key")
    source = os.path.join(root, "update", f"puki-{version}")
    unpack_release(z, source, version)
    step(3, "Stop Puki", 3)
    stop_puki(root, False)
    say("\n  Running the new installer...\n")
    cmd = [sys.executable, os.path.join(source, "brain", "install.py"), "--dir", root, "--yes",
           "--model", args.model or installed_model(root)]
    cmd += ["--cpu"] if args.cpu else []
    code = subprocess.run(cmd).returncode
    if code == 0:
        with open(os.path.join(brain_dir(root), RELEASE_FILE), "w", encoding="utf-8") as f:
            f.write(version + "\n")
    sys.exit(code)


def uninstall_windows_firewall(dry, ask):
    mine = powershell(f"Get-NetFirewallRule -DisplayName '{FIREWALL_RULE}' -ErrorAction SilentlyContinue").stdout.strip()
    other = port_rule()
    if mine:
        if dry:
            say(f"    → would remove '{FIREWALL_RULE}' (asks for admin)")
        elif ask(f"Remove the firewall rule '{FIREWALL_RULE}'? (Windows asks for permission)"):
            powershell(f"Start-Process powershell -Verb RunAs -Wait -WindowStyle Hidden -ArgumentList "
                       f"\"-NoProfile -Command Remove-NetFirewallRule -DisplayName '{FIREWALL_RULE}'\"")
            gone = not powershell(f"Get-NetFirewallRule -DisplayName '{FIREWALL_RULE}' -ErrorAction SilentlyContinue").stdout.strip()
            say("    ✓ removed" if gone else "    ! still there (the permission prompt was declined?)")
    elif other:
        say(f"    The rule '{other}' opens port {PORT}; it wasn't made by this installer, so it stays. To remove it,")
        say(f"    in an administrator PowerShell:  Remove-NetFirewallRule -DisplayName '{other}'")
    else:
        say("    nothing to remove")


def uninstall(args, root):
    dry = args.dry_run
    ask = lambda q: dry or args.yes or UI.ask(f"  {q}", default=False)  # noqa: E731
    say("Puki: uninstall\n")
    step(1, "Stop Puki")
    stop_puki(root, dry)
    step(2, "Start at login")
    links = puki_shortcuts()
    if not links:
        say("    nothing to remove")
    for link in links:
        if dry:
            say(f"    → would remove {os.path.basename(link)}")
        else:
            if link == SERVICE:
                subprocess.run(["systemctl", "--user", "disable", "puki"], capture_output=True)
            os.remove(link)
            if link == SERVICE:
                subprocess.run(["systemctl", "--user", "daemon-reload"], capture_output=True)
            say(f"    ✓ removed {os.path.basename(link)}")
    step(3, "Firewall rule")
    if not WINDOWS:
        if ufw_active():
            cmd = f"sudo ufw delete allow from {lan_subnet()} to any port {PORT} proto tcp"
            if dry:
                say(f"    → would ask to run: {cmd}")
            elif ask(f"Remove the ufw rule for port {PORT}? (runs: {cmd})"):
                subprocess.run(cmd.split())
        else:
            say("    nothing to remove (no active ufw)")
    else:
        uninstall_windows_firewall(dry, ask)
    brain = brain_dir(root)
    step(4, f"Models, llama.cpp, BlueTTS and the Python environment ({root})")
    parts = [os.path.join(root, n) for n in (os.listdir(root) if os.path.isdir(root) else [])
             if os.path.join(root, n) != brain]
    if parts:
        size = sum(os.path.getsize(os.path.join(d, f)) for part in parts for d, _, fs in os.walk(part) for f in fs) / 1e9
        if dry:
            say(f"    → would delete them ({size:,.1f} GB)")
        elif ask(f"Delete them ({size:,.1f} GB)?"):
            for part in parts:
                shutil.rmtree(part, ignore_errors=True) if os.path.isdir(part) else os.remove(part)
            left = [p for p in parts if os.path.exists(p)]
            say("    ✓ deleted" if not left else f"    ! couldn't delete {', '.join(left)} (in use?)")
    else:
        say("    nothing there")
    step(5, f"Puki's settings, pairing code and certificate ({brain})")
    if not os.path.isdir(brain):
        say("    nothing there")
    elif dry:
        say("    → would ask, then delete them")
    elif ask("Delete your settings, the pairing code and the certificate too?"):
        shutil.rmtree(brain, ignore_errors=True)
        say("    ✓ deleted" if not os.path.exists(brain) else "    ! some files couldn't be deleted (in use?)")
        if os.path.isdir(root) and not os.listdir(root):
            os.rmdir(root)
    say("\nDone. The Puki folder you installed from (the code) is still here; delete it if you like.")


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description="Set up Puki's brain on this PC.")
    default_dir = (os.path.join(os.environ.get("LOCALAPPDATA", os.path.expanduser("~")), "Puki") if FROZEN
                   else os.path.join(os.path.expanduser("~"), "puki-brain"))
    ap.add_argument("--dir", default=default_dir, help="where to put everything")
    ap.add_argument("--cpu", action="store_true", help="use the CPU build even if there's an NVIDIA GPU")
    ap.add_argument("--dry-run", action="store_true", help="only show what would happen")
    ap.add_argument("--yes", action="store_true", help="don't ask before starting")
    ap.add_argument("--no-firewall", action="store_true")
    ap.add_argument("--no-autostart", action="store_true")
    ap.add_argument("--no-start", action="store_true", help="don't start Puki at the end")
    ap.add_argument("--model", choices=["e4b", "12b", "both"],
                    help="which Gemma: e4b (lighter, faster), 12b (smarter, needs ~9 GB free GPU memory) or both "
                         "(12B when the GPU has room, E4B when it's busy). Default: asks, recommending 12b on a 12 GB+ GPU")
    ap.add_argument("--update", action="store_true", help="get the newest signed Puki release and redo only what changed")
    ap.add_argument("--uninstall", action="store_true", help="stop Puki and remove everything it installed")
    ap.add_argument("--gui", action="store_true", help="show the setup in a window (Puki.exe always does)")
    ap.add_argument("--only", help="run just these steps, e.g. --only firewall,autostart "
                    "(llama, models, bluetts, venv, files, config, firewall, autostart, start)")
    args = ap.parse_args()
    if args.gui and UI.__class__ is ConsoleUI:
        import setup_gui  # runs this same main() in a window
        return setup_gui.run(sys.argv[1:])
    root = os.path.abspath(os.path.expanduser(args.dir))
    if not args.dry_run:  # a dry run changes nothing, not even a log file
        UI.open_log(os.path.join(root, "install.log"))
    dry = args.dry_run
    only = set(args.only.split(",")) if args.only else None
    run = lambda name: only is None or name in only  # noqa: E731
    load_verified(root)
    if args.uninstall:
        return uninstall(args, root)
    if args.update:
        return update(args, root)

    say("Puki's brain: setup\n")
    if sys.platform == "darwin":
        fail("macOS isn't supported by this installer yet; follow brain/README.md by hand.")
    if sys.version_info < (3, 10) and not FROZEN:
        fail(f"Python 3.10 or newer is needed (this is {sys.version.split()[0]}).")
    gpu = None if args.cpu else nvidia(interactive=not (args.yes or dry))
    say(f"  Folder:  {root}")
    if gpu:
        say(f"  GPU:     {gpu[0]}, {gpu[1] / 1024:.0f} GB (CUDA {gpu[2]:g})"
            + ("" if gpu[1] >= 6000 else "; under 6 GB, so Puki will often run on the CPU"))
    else:
        say("  GPU:     none found, so everything runs on the CPU (replies take a few seconds)")
    os.makedirs(root, exist_ok=True) if not dry else None
    free = shutil.disk_usage(root if os.path.exists(root) else os.path.dirname(root)).free / 1e9
    say(f"  Disk:    {free:,.0f} GB free; about {NEEDED_GB} GB needed")
    if not long_paths_ok(root):
        fail(f"the folder path is too long for Windows ({len(root)} characters). Pick a shorter one, e.g. --dir C:/puki-brain")
    if free < NEEDED_GB and not os.path.exists(os.path.join(root, "models")):
        fail("not enough disk space. Free some up, or pick another drive with --dir.")
    big_gpu = bool(gpu) and gpu[1] >= 12000
    model = args.model
    if model is None:
        default = "both" if big_gpu else "e4b"
        options = [("e4b", "lighter and faster; fine on any PC (5.8 GB download)"),
                   ("12b", "smarter, better Hebrew; needs about 9 GB of free GPU memory (6.9 GB download)"),
                   ("both", "12B when the GPU has room, E4B when something else is using it (12.7 GB)")]
        model = default if dry or args.yes else UI.choose("Which Gemma (Puki's mind)?", options, default)
        say(f"  Model:   {model}")
    if model in ("12b", "both") and not big_gpu:
        say("  ! Without a GPU with 12 GB or more, the 12B runs on the CPU and answers slowly.")
    say("\n  Gemma is subject to Google's Gemma terms of use: https://ai.google.dev/gemma/terms")

    # The plan: what's missing, how big, and about how long it takes
    if not dry:
        UI.quiet = True
        try:
            for name, fn in (("llama", lambda: install_llama(root, gpu, True)), ("models", lambda: install_models(root, model, True)),
                             ("bluetts", lambda: install_bluetts(root, True))):
                if run(name):
                    fn()
        finally:
            UI.quiet = False
        say(f"\n  To download: {PLAN['total'] / 1e9:.1f} GB" + (estimate() if PLAN["total"] > 100e6 else "")
            + ("" if os.path.exists(os.path.join(root, VENV_PY)) else ", plus Python packages (1 to 3 GB)"))
    if not dry and not args.yes:
        if not UI.ask("\n  Start?", default=True):
            return

    py = os.path.join(root, VENV_PY)
    if FROZEN and not os.path.samefile(HERE, brain_dir(root)):
        fail(f"Puki.exe's files are in {HERE}, but the install folder is {root}")
    step(1, "llama.cpp (runs Gemma)")
    install_llama(root, gpu, dry) if run("llama") else say("    skipped")
    step(2, "Models: Gemma, its vision part, Hebrew Whisper, Parakeet (English)")
    install_models(root, model, dry) if run("models") else say("    skipped")
    step(3, "BlueTTS (Puki's voice)")
    install_bluetts(root, dry) if run("bluetts") else say("    skipped")
    step(4, "Python environment")
    if run("venv"):
        py = install_venv(root, gpu, dry)
    else:
        say("    skipped")
    step(5, "Puki's files (what runs; changing this folder doesn't change them)")
    install_brain_files(root, dry) if run("files") else say("    skipped")
    step(6, "Settings")
    write_config(root, py, model, dry) if run("config") else say("    skipped")
    step(7, "Firewall (home network only)")
    setup_firewall(dry) if run("firewall") and not args.no_firewall else say("    skipped")
    step(8, "Start at login")
    setup_autostart(root, py, dry) if run("autostart") and not args.no_autostart else say("    skipped")
    step(9, "Start Puki and hear it talk")
    start_puki(root, py, dry) if run("start") and not args.no_start else say("    skipped")
    if dry:
        return
    say("\nDone. In the app, open parent settings (long-press the gear) and scan the QR code, or type")
    say(f"this PC's address ({lan_address()}:{PORT}) and the pairing code. The first reply takes about")
    say("30 seconds while Puki wakes up.")
    UI.finish(*pairing(root))


def estimate():
    """", about N minutes at X MB/s", from a short speed test on the biggest download."""
    if not PLAN["files"]:
        return ""
    url = max(PLAN["files"], key=lambda f: f[1])[0]
    try:
        req = urllib.request.Request(url, headers=dict(UA, Range="bytes=0-8388607"))
        start = time.time()
        with urllib.request.urlopen(req, timeout=20) as r:
            got = len(r.read())
        speed = got / max(time.time() - start, 0.05)
    except Exception:
        return ""
    minutes = PLAN["total"] / speed / 60
    when = f"about {minutes:.0f} minutes" if minutes >= 2 else "a minute or two"
    return f", {when} at {speed / 1e6:.0f} MB/s"


if __name__ == "__main__":
    main()
