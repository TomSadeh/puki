#!/usr/bin/env python3
"""Builds Puki.exe: the tray app with the brain's files inside (see tray.py).

    python brain\\build_exe.py        ->  brain\\dist\\Puki.exe

Uses its own build environment (brain\\.build, git-ignored) with PyInstaller, pystray and
Pillow. Unsigned executables can trigger Windows SmartScreen and the occasional antivirus
false positive; publish the build's SHA-256 with it.
"""

import hashlib
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(HERE, ".build")
PY = os.path.join(BUILD, "venv", "Scripts", "python.exe")
# cryptography: Puki.exe makes the HTTPS certificate itself (an updated exe may meet a brain
# environment from before it was in requirements.txt)
PACKAGES = ["pyinstaller==6.11.1", "pystray==0.19.5", "pillow", "cryptography"]


def run(*cmd):
    print("  $", " ".join(os.path.basename(c) if i == 0 else c for i, c in enumerate(cmd)), flush=True)
    subprocess.run(cmd, check=True)


def main():
    if not os.path.exists(PY):
        run(sys.executable, "-m", "venv", os.path.join(BUILD, "venv"))
    run(PY, "-m", "pip", "install", "-q", "--disable-pip-version-check", *PACKAGES)

    # The icon: the tray's parrot, in the sizes Windows asks for
    ico = os.path.join(BUILD, "puki.ico")
    run(PY, "-c", "import sys; sys.path.insert(0, r'%s'); import tray; "
                  "tray.icon_image('ready').save(r'%s', sizes=[(16, 16), (32, 32), (48, 48), (64, 64)])" % (HERE, ico))

    # A build stamp: Puki.exe re-unpacks its files when this changes
    try:
        rev = subprocess.run(["git", "-C", HERE, "rev-parse", "--short", "HEAD"], capture_output=True, text=True).stdout.strip()
    except OSError:
        rev = ""
    stamp = os.path.join(BUILD, "build.txt")
    with open(stamp, "w", encoding="utf-8") as f:
        f.write(f"{rev or 'dev'} {time.strftime('%Y-%m-%d %H:%M')}\n")

    sys.path.insert(0, HERE)
    import tray
    data = [f"{os.path.join(HERE, f)};." for f in tray.BRAIN_FILES] + [f"{stamp};."]
    cmd = [PY, "-m", "PyInstaller", "--noconfirm", "--onefile", "--windowed", "--name", "Puki", "--icon", ico,
           "--distpath", os.path.join(HERE, "dist"), "--workpath", os.path.join(BUILD, "work"),
           "--specpath", BUILD, "--paths", HERE,
           "--hidden-import", "doorbell", "--hidden-import", "dashboard", "--hidden-import", "qr", "--hidden-import", "tls", "--collect-submodules", "cryptography",
           "--hidden-import", "install", "--hidden-import", "setup_gui", "--hidden-import", "pystray._win32"]
    for d in data:
        cmd += ["--add-data", d]
    run(*cmd, os.path.join(HERE, "tray.py"))

    exe = os.path.join(HERE, "dist", "Puki.exe")
    with open(exe, "rb") as f:
        sha = hashlib.sha256(f.read()).hexdigest()
    print(f"\n  {exe}  ({os.path.getsize(exe) / 1e6:.1f} MB)\n  SHA-256: {sha}")


if __name__ == "__main__":
    main()
