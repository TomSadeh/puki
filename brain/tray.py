#!/usr/bin/env python3
"""Puki in the system tray: runs the doorbell, and shows and controls the brain.

    pythonw brain\\tray.py          # from the repo (needs pystray and pillow)
    Puki.exe                       # the same, as one program (see build_exe.py)

The icon is grey while Puki sleeps, yellow while it wakes up, and green when it's awake.
Its menu opens the dashboard, shows the pairing code, puts the brain to sleep, turns
"start at login" on or off, uninstalls Puki, and quits.

As Puki.exe, the first run unpacks the brain's files into %LOCALAPPDATA%\\Puki and runs the
installer in a window (Python comes along via uv, so the PC needs none). After that it's
just the tray icon. A newer Puki.exe unpacks its files again and re-runs the installer in a
window, which only does what changed (like install.py --update), before Puki starts.
"""

import ctypes
import os
import shutil
import sys
import threading
import time
import webbrowser

from common import port_open
from bundle import FILES as BRAIN_FILES  # the files the brain needs on disk (brain.py runs in its own Python)

FROZEN = getattr(sys, "frozen", False)
BUNDLE = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
PORT = 8790
DASHBOARD = "http://127.0.0.1:8793"


def message(text, error=False):
    ctypes.windll.user32.MessageBoxW(0, text, "Puki", 0x10 if error else 0x40)


def home():
    return os.environ.get("PUKI_HOME") or os.path.join(os.environ["LOCALAPPDATA"], "Puki")


def prepare():
    """Where the brain's files are, and the build still to be set up there (else None). As
    Puki.exe, unpacks its files there first when they're from another build (the first run, an
    update); the build counts as set up only once its installer has run (set_up_done)."""
    if os.environ.get("PUKI_BRAIN_DIR"):
        return os.environ["PUKI_BRAIN_DIR"], None
    pending = None
    if not FROZEN:
        brain_dir = os.path.dirname(os.path.abspath(__file__))
    else:
        brain_dir = os.path.join(home(), "brain")
        os.makedirs(brain_dir, exist_ok=True)
        if not build_is_set_up(brain_dir):
            for f in BRAIN_FILES:
                shutil.copy2(os.path.join(BUNDLE, f), os.path.join(brain_dir, f))
            pending = read(os.path.join(BUNDLE, "build.txt"))
    os.environ["PUKI_BRAIN_DIR"] = brain_dir
    return brain_dir, pending


def build_is_set_up(brain_dir):
    """True if this Puki.exe's own build is the one unpacked and set up in [brain_dir]."""
    return read(os.path.join(brain_dir, "build.txt")) == read(os.path.join(BUNDLE, "build.txt"))


def set_up_done(brain_dir, build):
    """Records that [build]'s files are unpacked and its installer has run; an update that
    stopped halfway runs again the next time Puki.exe opens."""
    with open(os.path.join(brain_dir, "build.txt"), "w", encoding="utf-8") as fh:
        fh.write(build)


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


def run_setup(brain_dir):
    """Runs the installer in the setup window (or a console window if there's no tkinter);
    returns its exit code. When Puki is already set up (a new Puki.exe), it runs as an update:
    no questions, the same Gemma, and only what changed."""
    if not is_set_up(brain_dir):
        return with_window("Setting up Puki", ["--no-start"] + (["--dir", home()] if FROZEN else []))
    import install
    root = install_root(brain_dir)
    return with_window("Updating Puki", ["--no-start", "--yes", "--dir", root, "--model", install.installed_model(root)])


def with_window(title, args):
    try:
        import setup_gui
    except Exception:
        return in_console(title, args)
    return setup_gui.run(args, title)


def in_console(title, args):
    """Runs install.py with [args] in a console window of its own, then closes it."""
    ctypes.windll.kernel32.AllocConsole()
    out = open("CONOUT$", "w", encoding="utf-8", buffering=1)
    sys.stdout = sys.stderr = out
    sys.stdin = open("CONIN$", "r", encoding="utf-8")
    ctypes.windll.kernel32.SetConsoleTitleW(title)
    import install
    sys.argv = ["install"] + args
    code = 0
    try:
        install.main()
    except SystemExit as e:
        code = e.code
    except Exception as e:  # show it, don't vanish
        print(f"\n  Setup stopped: {e}")
        code = 1
    input("\n  Press Enter to close this window. ")
    sys.stdout = sys.stderr = None
    ctypes.windll.kernel32.FreeConsole()
    return code


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
    if port_open(PORT):  # before unpacking: a running Puki's brain.py must not change under it
        if FROZEN and not build_is_set_up(os.path.join(home(), "brain")):
            message("Another version of Puki is running. Quit it (right-click its tray icon, Quit Puki), "
                    "then open this one again to update.")
            return
        message("Puki is already running. Opening the dashboard.")
        webbrowser.open(DASHBOARD)
        return
    brain_dir, new_build = prepare()
    if new_build or not is_set_up(brain_dir):
        updating = is_set_up(brain_dir)
        if run_setup(brain_dir) not in (0, None) or not is_set_up(brain_dir):
            message("Puki's update didn't finish. Open Puki again to retry." if updating
                    else "Puki isn't set up yet. Run Puki again to finish the setup.", error=True)
            return
        if new_build:
            set_up_done(brain_dir, new_build)

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
