"""Small helpers the doorbell, the installer, the tray and the release script share: the pairing
code and link, private files, the home-network address, and the update signature's tools.
Standard library only (the installer runs it before there's a Python environment)."""

import json
import os
import secrets
import shutil
import socket

CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # no 0/O or 1/I to mix up
SIGN_NAMESPACE = "puki-update"  # an update's signature made for anything else doesn't count


def write_private(path, text):
    """Writes a file only this user may read (the pairing code, the child's name). On Windows
    the user's folders already keep other users out; this covers Linux and shared machines."""
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(text)
    try:
        os.chmod(path, 0o600)  # also when the file was already there
    except OSError:
        pass


def normalize_code(code):
    """Case, spaces and dashes don't matter when typing the code."""
    return "".join(c for c in code.upper() if c.isalnum())


def pretty_code(code):
    return f"{code[:4]}-{code[4:]}"


def pairing_code(path, new=False):
    """The pairing code kept in [path] (normalized), made there first if there's none yet or [new]."""
    if not new and os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            code = normalize_code(f.read())
        if len(code) >= 8:
            return code
    code = "".join(secrets.choice(CODE_ALPHABET) for _ in range(8))  # 40 bits
    write_private(path, pretty_code(code) + "\n")
    return code


def pair_url(host, port, code, fp_link):
    """What the app's QR scanner reads: where the PC is, the code, and the certificate's
    fingerprint, so the app pins it before it sends anything."""
    return f"puki://pair?host={host}:{port}&code={normalize_code(code)}&fp={fp_link}"


def lan_address():
    """This PC's address on the home network (no packet is sent), or None when there's none."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return None
    finally:
        s.close()


def port_open(port, timeout=0.5):
    """True if something on this PC answers on [port]."""
    with socket.socket() as s:
        s.settimeout(timeout)
        return s.connect_ex(("127.0.0.1", port)) == 0


def load_config(folder):
    """The doorbell's settings: config.example.json, then config.json over it."""
    with open(os.path.join(folder, "config.example.json"), encoding="utf-8") as f:
        cfg = json.load(f)
    local = os.path.join(folder, "config.json")
    if os.path.exists(local):
        with open(local, encoding="utf-8") as f:
            cfg.update(json.load(f))
    return cfg


def ssh_keygen():
    """OpenSSH's ssh-keygen, which signs and checks updates (Windows 10 and up, and Linux, have it)."""
    found = shutil.which("ssh-keygen")
    if not found and os.name == "nt":
        found = os.path.join(os.environ.get("SystemRoot", r"C:\Windows"), "System32", "OpenSSH", "ssh-keygen.exe")
    return found if found and os.path.exists(found) else None
