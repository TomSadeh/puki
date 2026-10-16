#!/usr/bin/env python3
"""Puki in the system tray: runs the doorbell, and shows and controls the brain.

    pythonw brain\\tray.py          # from the repo (needs pystray and pillow)
    Puki.exe                       # the same, as one program (see build_exe.py)

The icon is grey while Puki sleeps, yellow while it wakes up, and green when it's awake.
Its menu opens the dashboard, shows the pairing code, puts the brain to sleep, turns
"start at login" on or off, uninstalls Puki, and quits.

As Puki.exe, the first run unpacks the brain's files into %LOCALAPPDATA%\\Puki and runs the
installer in a window (Python comes along via uv, so the PC needs none). After that it's
just the tray icon.
"""

import ctypes
import os
import shutil
import socket
import sys
import threading
import time
import webbrowser

FROZEN = getattr(sys, "frozen", False)
BUNDLE = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
# The files the brain needs on disk (brain.py runs in its own Python environment)
BRAIN_FILES = ["doorbell.py", "brain.py", "persona.py", "tls.py", "pins.json", "dashboard.py", "dashboard.html", "qr.py", "parakeet.py",
               "install.py", "setup_gui.py", "config.example.json", "requirements.txt"]
PORT = 8790
DASHBOARD = "http://127.0.0.1:8793"


def message(text, error=False):
    ctypes.windll.user32.MessageBoxW(0, text, "Puki", 0x10 if error else 0x40)


def port_open(port):
    with socket.socket() as s:
        s.settimeout(0.5)
        return s.connect_ex(("127.0.0.1", port)) == 0


def home():
    return os.environ.get("PUKI_HOME") or os.path.join(os.environ["LOCALAPPDATA"], "Puki")


def prepare():
    """Where the brain's files are; as Puki.exe, unpacks them there first (again after an update)."""
    if os.environ.get("PUKI_BRAIN_DIR"):
        return os.environ["PUKI_BRAIN_DIR"]
    if not FROZEN:
        brain_dir = os.path.dirname(os.path.abspath(__file__))
    else:
        brain_dir = os.path.join(home(), "brain")
        os.makedirs(brain_dir, exist_ok=True)
        stamp = read(os.path.join(BUNDLE, "build.txt"))
        if read(os.path.join(brain_dir, "build.txt")) != stamp:
            for f in BRAIN_FILES:
                shutil.copy2(os.path.join(BUNDLE, f), os.path.join(brain_dir, f))
            with open(os.path.join(brain_dir, "build.txt"), "w", encoding="utf-8") as fh:
                fh.write(stamp)
    os.environ["PUKI_BRAIN_DIR"] = brain_dir
    return brain_dir


def read(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read().strip()
    except OSError:
        return ""


def install_root(brain_dir):
    """The folder the installer put everything in: where the brain's Python environment is."""
    py = brain_python(brain_dir)
    if py and os.path.basename(os.path.dirname(os.path.dirname(py))) == ".venv":
        return os.path.dirname(os.path.dirname(os.path.dirname(py)))
    return home() if FROZEN else None


def brain_python(brain_dir):
    import json
    cfg = {}
    # Puki.exe: only what its own installer wrote counts (the example's paths are someone else's)
    for name in (("config.json",) if FROZEN else ("config.example.json", "config.json")):
        try:
            with open(os.path.join(brain_dir, name), encoding="utf-8") as f:
                cfg.update(json.load(f))
        except (OSError, ValueError):
            pass
    return os.path.expanduser(cfg.get("brain_python", ""))


def is_set_up(brain_dir):
    """True once the installer has written the paths and the Python environment exists."""
    py = brain_python(brain_dir)
    return bool(py) and os.path.exists(py)


def run_setup():
    """Runs the installer in the setup window (or a console window if there's no tkinter)."""
    with_window("Setting up Puki", ["--no-start"] + (["--dir", home()] if FROZEN else []))


def with_window(title, args):
    try:
        import setup_gui
    except Exception:
        return in_console(title, args)
    setup_gui.run(args, title)


def in_console(title, args):
    """Runs install.py with [args] in a console window of its own, then closes it."""
    ctypes.windll.kernel32.AllocConsole()
    out = open("CONOUT$", "w", encoding="utf-8", buffering=1)
    sys.stdout = sys.stderr = out
    sys.stdin = open("CONIN$", "r", encoding="utf-8")
    ctypes.windll.kernel32.SetConsoleTitleW(title)
    import install
    sys.argv = ["install"] + args
    try:
        install.main()
    except SystemExit:
        pass
    except Exception as e:  # show it, don't vanish
        print(f"\n  Setup stopped: {e}")
    input("\n  Press Enter to close this window. ")
    sys.stdout = sys.stderr = None
    ctypes.windll.kernel32.FreeConsole()


# ---------- The icon ----------

COLORS = {"cold": (150, 160, 170), "warming": (255, 193, 7), "ready": (76, 175, 80)}


def icon_image(state):
    """A little parrot with a status dot (grey asleep, yellow waking, green awake)."""
    from PIL import Image, ImageDraw
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((14, 22, 46, 60), fill=(67, 160, 71))       # body
    d.ellipse((20, 6, 46, 32), fill=(76, 175, 80))        # head
    d.ellipse((22, 34, 38, 56), fill=(197, 225, 165))     # tummy
    d.polygon([(20, 16), (8, 22), (20, 25)], fill=(255, 167, 38))  # beak
    d.ellipse((27, 12, 37, 22), fill="white")
    d.ellipse((30, 15, 35, 20), fill=(62, 39, 35))
    d.polygon([(30, 4), (34, 0), (38, 6)], fill=(229, 57, 53))   # crest
    dot = COLORS.get(state, COLORS["cold"])
    d.ellipse((40, 40, 62, 62), fill="white")
    d.ellipse((43, 43, 59, 59), fill=dot)
    return img


def main():
    if os.name != "nt":
        print("On Linux, Puki runs as a service (set up by  python3 brain/install.py); there's no tray icon.")
        print(f"The dashboard is at {DASHBOARD}, and  systemctl --user status puki  shows how it's doing.")
        return
    brain_dir = prepare()
    if port_open(PORT):
        message("Puki is already running. Opening the dashboard.")
        webbrowser.open(DASHBOARD)
        return
    if not is_set_up(brain_dir):
        run_setup()
        if not is_set_up(brain_dir):
            message("Puki isn't set up yet. Run Puki again to finish the setup.", error=True)
            return

    import doorbell
    import install
    import pystray

    server = doorbell.start()
    threading.Thread(target=server.serve_forever, daemon=True).start()

    names = {"cold": "asleep", "warming": "waking up", "ready": "awake"}

    def status(_=None):
        b = doorbell.BRAIN
        text = f"Puki is {names.get(b.state, b.state)}"
        if b.state == "ready" and b.model:
            text += f" (Gemma {b.model} on the {b.device})"
        return text

    def code(_=None):
        return f"Pairing code: {doorbell.pretty_code(doorbell.PAIRING_CODE)}"

    def autostart_on(_=None):
        return bool(install.puki_shortcuts())

    def toggle_autostart(icon, _):
        links = install.puki_shortcuts()
        if links:
            for link in links:
                os.remove(link)
        else:
            target, args = (sys.executable, "") if FROZEN else (sys.executable.replace("python.exe", "pythonw.exe"),
                                                                f'"{os.path.abspath(__file__)}"')
            link = os.path.join(install.startup_folder(), "Puki.lnk")
            install.powershell(f"$s = (New-Object -ComObject WScript.Shell).CreateShortcut('{link}'); "
                               f"$s.TargetPath = '{target}'; $s.Arguments = '{args}'; "
                               f"$s.WorkingDirectory = '{brain_dir}'; $s.Save()")
        icon.update_menu()

    def sleep(icon, _):
        doorbell.BRAIN.stop("put to sleep from the tray")

    def quit_(icon, _):
        doorbell.BRAIN.stop("Puki quit")
        server.shutdown()
        icon.stop()

    def uninstall(icon, _):
        root = install_root(brain_dir)
        if ctypes.windll.user32.MessageBoxW(
                0, "Remove Puki from this PC? A window will ask before each part: start at login, the "
                   "firewall rule, the downloaded models (several GB) and the settings.", "Puki", 0x34) != 6:
            return
        quit_(icon, None)
        with_window("Uninstall Puki", ["--uninstall"] + (["--dir", root] if root else []))
        if FROZEN:
            message("Puki is uninstalled. To finish, delete Puki.exe.")
        os._exit(0)

    menu = pystray.Menu(
        pystray.MenuItem(status, None, enabled=False),
        pystray.MenuItem("Open the dashboard", lambda: webbrowser.open(DASHBOARD), default=True),
        pystray.MenuItem(code, lambda: webbrowser.open(DASHBOARD)),
        pystray.MenuItem("Put to sleep now", sleep, enabled=lambda _: doorbell.BRAIN.state != "cold"),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Start at login", toggle_autostart, checked=autostart_on),
        pystray.MenuItem("Uninstall Puki...", uninstall),
        pystray.MenuItem("Quit Puki", quit_),
    )
    icon = pystray.Icon("Puki", icon_image("cold"), "Puki is asleep", menu)

    def watch():
        last = None
        while True:
            state = doorbell.BRAIN.state
            if state != last:
                icon.icon = icon_image(state)
                icon.title = status()
                icon.update_menu()
                last = state
            time.sleep(1)

    def first_run(icon):
        icon.visible = True
        threading.Thread(target=watch, daemon=True).start()
        if FROZEN and not autostart_on():
            toggle_autostart(icon, None)  # Puki.exe starts at login by default

    icon.run(setup=first_run)


if __name__ == "__main__":
    main()
