#!/usr/bin/env python3
"""Sets up Puki's brain on this PC, step by step.

    python brain\\install.py                 # everything, asking before it starts
    python brain\\install.py --dry-run       # just show what it would do
    python brain\\install.py --dir D:\\puki-brain --yes

It downloads llama.cpp (the CUDA build when there's an NVIDIA GPU, otherwise the CPU
one), Gemma and its vision part, the ivrit.ai Hebrew Whisper, Parakeet for English, and
BlueTTS with its voice models (about 9 GB in all). Then it creates the Python environment,
writes brain/config.json, adds the home-network firewall rule (one Windows admin prompt),
starts Puki at login, and opens the dashboard with the pairing code.

Safe to run again: finished steps and complete downloads are skipped, and an interrupted
download resumes where it stopped. Standard library only, so any Python 3.10+ can run it.
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))

# The versions Puki was tested with
LLAMA_TAG = "b11669"
BLUETTS_COMMIT = "0e38dbf08ed53f85863d1eab092bd9572c53a503"
GEMMA_REPO = "unsloth/gemma-4-E4B-it-GGUF"
GEMMA_FILE = "gemma-4-E4B-it-Q4_0.gguf"
MMPROJ_FILE = "mmproj-F16.gguf"
WHISPER_REPO = "ivrit-ai/whisper-large-v3-turbo-ct2"
PARAKEET_REPO = "istupakov/parakeet-tdt-0.6b-v2-onnx"
PARAKEET_FILES = ["encoder-model.int8.onnx", "decoder_joint-model.int8.onnx", "nemo128.onnx", "vocab.txt", "config.json"]
BLUETTS_ONNX_REPO = "notmax123/BlueTTS2.5-onnx"

PORT = 8790
DASHBOARD = "http://127.0.0.1:8793"
FIREWALL_RULE = "Puki companion"
NEEDED_GB = 12
UA = {"User-Agent": "puki-installer"}


# ---------- Output ----------

def say(msg=""):
    print(msg, flush=True)


def step(n, title):
    say(f"\n[{n}] {title}")


def fail(msg):
    say(f"\n  ✗ {msg}")
    sys.exit(1)


# ---------- Downloads ----------

def http_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.load(r)


def download(url, dest, size=None, dry=False):
    """Downloads [url] to [dest] with a progress bar, resuming a partial .part file."""
    name = os.path.basename(dest)
    if os.path.exists(dest) and (size is None or os.path.getsize(dest) == size):
        say(f"    ✓ {name} (already there)")
        return
    if dry:
        say(f"    → would download {name}" + (f" ({size / 1e6:,.0f} MB)" if size else ""))
        return
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    part = dest + ".part"
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
                with open(part, mode) as f:
                    while True:
                        chunk = r.read(1 << 20)
                        if not chunk:
                            break
                        f.write(chunk)
                        done += len(chunk)
                        if total:
                            pct = done * 100 // total
                            speed = (done - have) / max(time.time() - start, 0.1) / 1e6
                            print(f"\r    {name}: {pct:3d}%  {done / 1e6:,.0f}/{total / 1e6:,.0f} MB  {speed:.1f} MB/s   ", end="", flush=True)
            print()
            break
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            print()
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
    os.replace(part, dest)


def hf_files(repo):
    """{path: size} of a Hugging Face model repo (top level)."""
    return {x["path"]: x.get("size") for x in http_json(f"https://huggingface.co/api/models/{repo}/tree/main")
            if x.get("type") == "file"}


def hf_url(repo, path):
    return f"https://huggingface.co/{repo}/resolve/main/{path}"


# ---------- Checks ----------

def nvidia():
    """(name, VRAM MB, driver's CUDA version) of the NVIDIA GPU, or None."""
    try:
        q = subprocess.run(["nvidia-smi", "--query-gpu=name,memory.total", "--format=csv,noheader,nounits"],
                           capture_output=True, text=True, timeout=15)
        head = subprocess.run(["nvidia-smi"], capture_output=True, text=True, timeout=15).stdout
        name, mem = [x.strip() for x in q.stdout.strip().splitlines()[0].split(",")]
        cuda = 0.0
        if "CUDA Version:" in head:
            cuda = float(head.split("CUDA Version:")[1].split()[0])
        return name, int(mem), cuda
    except Exception:
        return None


def long_paths_ok(root):
    """Python packages nest deep; past Windows' 260-character limit pip fails halfway."""
    if len(root) <= 60:
        return True
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"SYSTEM\CurrentControlSet\Control\FileSystem") as k:
            return winreg.QueryValueEx(k, "LongPathsEnabled")[0] == 1
    except OSError:
        return False


def powershell(cmd):
    return subprocess.run(["powershell", "-NoProfile", "-Command", cmd], capture_output=True, text=True)


def port_open(port):
    import socket
    with socket.socket() as s:
        s.settimeout(0.5)
        return s.connect_ex(("127.0.0.1", port)) == 0


# ---------- Steps ----------

def install_llama(root, gpu, dry):
    dest = os.path.join(root, "llama")
    if os.path.exists(os.path.join(dest, "llama-server.exe")):
        say("    ✓ llama.cpp (already there)")
        return
    flavor = "cpu-x64"
    if gpu:
        flavor = "cuda-13.4-x64" if gpu[2] >= 13 else "cuda-12.4-x64"
    assets = {a["name"]: (a["browser_download_url"], a["size"])
              for a in http_json(f"https://api.github.com/repos/ggml-org/llama.cpp/releases/tags/{LLAMA_TAG}")["assets"]}
    wanted = [f"llama-{LLAMA_TAG}-bin-win-{flavor}.zip"]
    if gpu:
        wanted.append(f"cudart-llama-bin-win-{flavor}.zip")
    for name in wanted:
        if name not in assets:
            fail(f"llama.cpp {LLAMA_TAG} has no {name}")
        url, size = assets[name]
        z = os.path.join(root, "downloads", name)
        download(url, z, size, dry)
        if not dry:
            with zipfile.ZipFile(z) as f:
                f.extractall(dest)
    if not dry:
        say(f"    ✓ llama.cpp {LLAMA_TAG} ({flavor})")


def install_models(root, dry):
    models = os.path.join(root, "models")
    gemma = hf_files(GEMMA_REPO)
    download(hf_url(GEMMA_REPO, GEMMA_FILE), os.path.join(models, GEMMA_FILE), gemma.get(GEMMA_FILE), dry)
    download(hf_url(GEMMA_REPO, MMPROJ_FILE), os.path.join(models, "mmproj-gemma-4-E4B-F16.gguf"), gemma.get(MMPROJ_FILE), dry)
    for path, size in hf_files(WHISPER_REPO).items():
        if not path.startswith("."):
            download(hf_url(WHISPER_REPO, path), os.path.join(models, "whisper-ivrit-turbo", path), size, dry)
    parakeet = hf_files(PARAKEET_REPO)
    for path in PARAKEET_FILES:
        download(hf_url(PARAKEET_REPO, path), os.path.join(models, "parakeet-tdt-0.6b-v2-onnx", path), parakeet.get(path), dry)


def install_bluetts(root, dry):
    dest = os.path.join(root, "BlueTTS")
    if os.path.exists(os.path.join(dest, "pyproject.toml")):
        say("    ✓ BlueTTS code (already there)")
    else:
        z = os.path.join(root, "downloads", f"BlueTTS-{BLUETTS_COMMIT[:7]}.zip")
        download(f"https://github.com/maxmelichov/BlueTTS/archive/{BLUETTS_COMMIT}.zip", z, None, dry)
        if not dry:
            tmp = os.path.join(root, "downloads", "bluetts-unzip")
            shutil.rmtree(tmp, ignore_errors=True)
            with zipfile.ZipFile(z) as f:
                f.extractall(tmp)
            shutil.move(os.path.join(tmp, os.listdir(tmp)[0]), dest)
            shutil.rmtree(tmp, ignore_errors=True)
            say("    ✓ BlueTTS code")
    for path, size in hf_files(BLUETTS_ONNX_REPO).items():
        if not path.startswith(".") and path != "README.md" and size:
            download(hf_url(BLUETTS_ONNX_REPO, path), os.path.join(dest, "onnx_models", path), size, dry)


def install_venv(root, gpu, dry):
    venv = os.path.join(root, ".venv")
    py = os.path.join(venv, "Scripts", "python.exe")
    if dry:
        say(f"    → would create {venv} and install the Python packages" + ("" if gpu else " (without the NVIDIA ones)"))
        return py
    if not os.path.exists(py):
        say("    creating the Python environment...")
        subprocess.run([sys.executable, "-m", "venv", venv], check=True)
    # Without an NVIDIA GPU, skip the CUDA libraries (about 1 GB)
    with open(os.path.join(HERE, "requirements.txt"), encoding="utf-8") as f:
        reqs = [l for l in f.read().splitlines() if gpu or not l.startswith("nvidia-")]
    req_file = os.path.join(root, "downloads", "requirements.txt")
    os.makedirs(os.path.dirname(req_file), exist_ok=True)
    with open(req_file, "w", encoding="utf-8") as f:
        f.write("\n".join(reqs) + "\n")
    say("    installing packages (a few minutes the first time)...")
    r = subprocess.run([py, "-m", "pip", "install", "--disable-pip-version-check", "-q",
                        os.path.join(root, "BlueTTS"), "-r", req_file])
    if r.returncode != 0:
        fail("pip couldn't install the packages (see above). Fix that and run the installer again.")
    say("    ✓ Python environment")
    return py


def write_config(root, py, dry):
    path = os.path.join(HERE, "config.json")
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
    })
    if dry:
        say(f"    → would write {path}")
        return
    with open(path, "w", encoding="utf-8") as f:
        json.dump(cfg, f, ensure_ascii=False, indent=2)
        f.write("\n")
    say(f"    ✓ {path}")


def port_rule():
    """Name of an enabled inbound rule that already opens the port, if any."""
    return powershell(
        "Get-NetFirewallRule -Direction Inbound -Action Allow -Enabled True | "
        f"Where-Object {{ ($_ | Get-NetFirewallPortFilter).LocalPort -contains '{PORT}' }} | "
        "Select-Object -First 1 -ExpandProperty DisplayName").stdout.strip()


def setup_firewall(dry):
    existing = port_rule()
    if existing:
        say(f"    ✓ firewall rule '{existing}' (already there)")
    elif dry:
        say(f"    → would add the firewall rule '{FIREWALL_RULE}' (TCP {PORT}, home networks only; asks for admin)")
    else:
        say("    Windows will ask for permission to add the firewall rule...")
        rule = (f"New-NetFirewallRule -DisplayName '{FIREWALL_RULE}' -Direction Inbound -Protocol TCP "
                f"-LocalPort {PORT} -Profile Private -Action Allow")
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


def setup_autostart(dry):
    startup = os.path.join(os.environ["APPDATA"], r"Microsoft\Windows\Start Menu\Programs\Startup")
    link = os.path.join(startup, "Puki brain.lnk")
    pythonw = os.path.join(os.path.dirname(sys.executable), "pythonw.exe")
    doorbell = os.path.join(HERE, "doorbell.py")
    # Any login shortcut that already runs the doorbell (e.g. one made by hand) counts
    existing = powershell(
        f"$sh = New-Object -ComObject WScript.Shell; Get-ChildItem '{startup}' -Filter *.lnk | "
        "Where-Object { $sh.CreateShortcut($_.FullName).Arguments -match 'doorbell[.]py' } | "
        "Select-Object -ExpandProperty Name").stdout.strip()
    if existing:
        say(f"    ✓ starts at login (already set up: {existing.splitlines()[0]})")
        return pythonw, doorbell
    if dry:
        say("    → would make Puki start at login")
        return pythonw, doorbell
    r = powershell(
        f"$s = (New-Object -ComObject WScript.Shell).CreateShortcut('{link}'); "
        f"$s.TargetPath = '{pythonw}'; $s.Arguments = '\"{doorbell}\"'; "
        f"$s.WorkingDirectory = '{HERE}'; $s.Save()")
    say("    ✓ starts at login" if os.path.exists(link) else f"    ! couldn't create the login shortcut: {r.stderr.strip()}")
    return pythonw, doorbell


def start_puki(pythonw, doorbell, dry):
    if dry:
        say("    → would start Puki and open the dashboard")
        return
    if port_open(PORT):
        say("    ✓ Puki is already running (restart the PC, or the doorbell, to pick up new settings)")
    else:
        subprocess.Popen([pythonw, doorbell], cwd=HERE, creationflags=0x00000008)  # DETACHED_PROCESS
        for _ in range(20):
            if port_open(PORT):
                break
            time.sleep(0.5)
        say("    ✓ Puki is running" if port_open(PORT) else "    ! Puki didn't start; run  python brain\\doorbell.py  to see why")
    code_file = os.path.join(HERE, "pairing_code.txt")
    if os.path.exists(code_file):
        with open(code_file, encoding="utf-8") as f:
            say(f"\n  Pairing code: {f.read().strip()}")
    import webbrowser
    webbrowser.open(DASHBOARD)
    say(f"  The dashboard ({DASHBOARD}) shows it too, with a QR code.")


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description="Set up Puki's brain on this PC.")
    ap.add_argument("--dir", default=os.path.join(os.path.expanduser("~"), "puki-brain"), help="where to put everything")
    ap.add_argument("--cpu", action="store_true", help="use the CPU build even if there's an NVIDIA GPU")
    ap.add_argument("--dry-run", action="store_true", help="only show what would happen")
    ap.add_argument("--yes", action="store_true", help="don't ask before starting")
    ap.add_argument("--no-firewall", action="store_true")
    ap.add_argument("--no-autostart", action="store_true")
    ap.add_argument("--no-start", action="store_true", help="don't start Puki at the end")
    ap.add_argument("--only", help="run just these steps, e.g. --only firewall,autostart "
                    "(llama, models, bluetts, venv, config, firewall, autostart, start)")
    args = ap.parse_args()
    root = os.path.abspath(os.path.expanduser(args.dir))
    dry = args.dry_run
    only = set(args.only.split(",")) if args.only else None
    run = lambda name: only is None or name in only  # noqa: E731

    say("Puki's brain: setup\n")
    if os.name != "nt":
        fail("this installer is for Windows. On Linux or macOS, follow brain/README.md by hand.")
    if sys.version_info < (3, 10):
        fail(f"Python 3.10 or newer is needed (this is {sys.version.split()[0]}).")
    gpu = None if args.cpu else nvidia()
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
    say("\n  Gemma is subject to Google's Gemma terms of use: https://ai.google.dev/gemma/terms")
    if not dry and not args.yes:
        if input("\n  Start? [Y/n] ").strip().lower() not in ("", "y", "yes"):
            return

    py = os.path.join(root, ".venv", "Scripts", "python.exe")
    pythonw, doorbell = os.path.join(os.path.dirname(sys.executable), "pythonw.exe"), os.path.join(HERE, "doorbell.py")
    step(1, "llama.cpp (runs Gemma)")
    install_llama(root, gpu, dry) if run("llama") else say("    skipped")
    step(2, "Models: Gemma, its vision part, Hebrew Whisper, Parakeet (English)")
    install_models(root, dry) if run("models") else say("    skipped")
    step(3, "BlueTTS (Puki's voice)")
    install_bluetts(root, dry) if run("bluetts") else say("    skipped")
    step(4, "Python environment")
    if run("venv"):
        py = install_venv(root, gpu, dry)
    else:
        say("    skipped")
    step(5, "Settings")
    write_config(root, py, dry) if run("config") else say("    skipped")
    step(6, "Firewall (home network only)")
    setup_firewall(dry) if run("firewall") and not args.no_firewall else say("    skipped")
    step(7, "Start at login")
    if run("autostart") and not args.no_autostart:
        pythonw, doorbell = setup_autostart(dry)
    else:
        say("    skipped")
    step(8, "Start Puki")
    start_puki(pythonw, doorbell, dry) if run("start") and not args.no_start else say("    skipped")
    say("\nDone. In the app, open parent settings (long-press the gear) and scan the QR code or")
    say("type the PC address and pairing code. The first reply takes about 30 seconds while Puki wakes up.")


if __name__ == "__main__":
    main()
