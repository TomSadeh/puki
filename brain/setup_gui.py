#!/usr/bin/env python3
"""The setup window: install.py's plan, questions, progress and result, in tkinter.

    python brain\\install.py --gui          # the installer, in a window
    Puki.exe                                # uses this for its first-run setup and uninstall

install.py does all the work on a background thread; it talks to this window only through
its UI object (TkUI here), and the window shows what it's told. Standard library only.
"""

import os
import queue
import sys
import threading
import time
import tkinter as tk
from tkinter import messagebox, ttk

import install

BG = "#f4f8fb"
INK = "#1d2a33"
MUTED = "#62727d"
ACCENT = "#2e9d55"


class TkUI(install.ConsoleUI):
    """Sends what the installer says and asks to the window (keeps the console UI's log file)."""

    def __init__(self, app):
        super().__init__()
        self.app = app
        self.last_progress = 0.0

    def say(self, msg=""):
        if self.quiet:
            return
        self.write_log(msg)
        self.app.post("say", msg)

    def step(self, n, total, title):
        self.say(f"\n[{n}/{total}] {title}")
        self.app.post("step", n, total, title)

    def progress(self, name, done, total, speed, overall):
        now = time.time()
        if now - self.last_progress >= 0.15 or done >= total:  # don't flood the window
            self.last_progress = now
            self.app.post("progress", name, done, total, speed, overall)

    def progress_done(self):
        self.app.post("progress_done")

    def ask(self, question, default=False):
        answer = self.app.call(lambda: messagebox.askyesno(
            "Puki", question.strip(), default=messagebox.YES if default else messagebox.NO, parent=self.app.root))
        self.write_log(f"{question.strip()} -> {'yes' if answer else 'no'}")
        return answer

    def choose(self, title, options, default):
        key = self.app.call(lambda: self.app.choose_dialog(title, options, default))
        self.write_log(f"{title} -> {key}")
        return key

    def finish(self, code, url):
        self.app.post("finish", code, url)

    def fail(self, msg):
        self.say(f"\n  ✗ {msg}")
        self.app.post("fail", msg)


class App:
    def __init__(self, title):
        self.events = queue.Queue()
        self.finished = False
        self.running = True
        self.root = tk.Tk()
        self.root.title(title)
        self.root.geometry("760x600")
        self.root.minsize(600, 480)
        self.root.configure(bg=BG)
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

        head = tk.Frame(self.root, bg=BG)
        head.pack(fill="x", padx=20, pady=(16, 6))
        tk.Label(head, text=f"🦜  {title}", font=("Segoe UI", 18, "bold"), bg=BG, fg=INK).pack(anchor="w")
        self.status = tk.Label(head, text="Getting ready...", font=("Segoe UI", 11), bg=BG, fg=MUTED)
        self.status.pack(anchor="w", pady=(2, 0))

        bars = tk.Frame(self.root, bg=BG)
        bars.pack(fill="x", padx=20, pady=6)
        self.file_label = tk.Label(bars, text="", font=("Segoe UI", 10), bg=BG, fg=INK, anchor="w")
        self.file_label.pack(fill="x")
        self.file_bar = ttk.Progressbar(bars, maximum=1000)
        self.file_bar.pack(fill="x", pady=(2, 8))
        self.all_label = tk.Label(bars, text="", font=("Segoe UI", 10), bg=BG, fg=INK, anchor="w")
        self.all_label.pack(fill="x")
        self.all_bar = ttk.Progressbar(bars, maximum=1000)
        self.all_bar.pack(fill="x", pady=(2, 0))

        self.body = tk.Frame(self.root, bg=BG)
        self.body.pack(fill="both", expand=True, padx=20, pady=8)
        self.log = tk.Text(self.body, font=("Consolas", 9), bg="white", fg=INK, relief="flat", wrap="word",
                           padx=8, pady=6, state="disabled")
        scroll = ttk.Scrollbar(self.body, command=self.log.yview)
        self.log.configure(yscrollcommand=scroll.set)
        scroll.pack(side="right", fill="y")
        self.log.pack(side="left", fill="both", expand=True)

        foot = tk.Frame(self.root, bg=BG)
        foot.pack(fill="x", padx=20, pady=(0, 14))
        self.close_button = ttk.Button(foot, text="Close", command=self.on_close, state="disabled")
        self.close_button.pack(side="right")
        self.root.after(80, self.pump)

    # ----- from the worker thread -----

    def post(self, kind, *args):
        self.events.put((kind, args))

    def call(self, fn):
        """Runs [fn] on the window's thread (dialogs must) and waits for its result."""
        box, done = [], threading.Event()
        self.post("call", fn, box, done)
        done.wait()
        return box[0]

    # ----- on the window's thread -----

    def pump(self):
        try:
            while True:
                kind, args = self.events.get_nowait()
                getattr(self, "on_" + kind)(*args)
        except queue.Empty:
            pass
        self.root.after(80, self.pump)

    def write(self, text):
        self.log.configure(state="normal")
        self.log.insert("end", text + "\n")
        self.log.see("end")
        self.log.configure(state="disabled")

    def on_say(self, msg):
        self.write(msg)

    def on_step(self, n, total, title):
        self.status.configure(text=f"Step {n} of {total}: {title}")
        self.file_label.configure(text="")
        self.file_bar["value"] = 0

    def on_progress(self, name, done, total, speed, overall):
        self.file_label.configure(text=f"{name}   {done / 1e6:,.0f} / {total / 1e6:,.0f} MB   {speed:.1f} MB/s")
        self.file_bar["value"] = 1000 * done / max(total, 1)
        if overall:
            self.all_label.configure(text=f"All downloads: {overall[0] / 1e9:.1f} / {overall[1] / 1e9:.1f} GB")
            self.all_bar["value"] = 1000 * overall[0] / max(overall[1], 1)

    def on_progress_done(self):
        self.file_bar["value"] = 1000

    def on_call(self, fn, box, done):
        try:
            box.append(fn())
        finally:
            done.set()

    def on_fail(self, msg):
        self.status.configure(text="Setup stopped. The messages below say why; running it again resumes.", fg="#c2412d")
        messagebox.showerror("Puki", msg, parent=self.root)

    def on_finish(self, code, url):
        self.finished = True
        self.status.configure(text="Puki is ready!", fg=ACCENT)
        for w in self.body.winfo_children():
            w.pack_forget()
        box = tk.Frame(self.body, bg=BG)
        box.pack(expand=True)
        if url:
            sys.path.insert(0, install.HERE)
            import qr
            m = qr.matrix(url)
            cell, quiet = 6, 4
            size = (len(m) + 2 * quiet) * cell
            canvas = tk.Canvas(box, width=size, height=size, bg="white", highlightthickness=0)
            for r, row in enumerate(m):
                for c, dark in enumerate(row):
                    if dark:
                        x, y = (c + quiet) * cell, (r + quiet) * cell
                        canvas.create_rectangle(x, y, x + cell, y + cell, fill="black", width=0)
            canvas.pack(side="left", padx=(0, 24))
        text = tk.Frame(box, bg=BG)
        text.pack(side="left")
        tk.Label(text, text="Pairing code", font=("Segoe UI", 10), bg=BG, fg=MUTED).pack(anchor="w")
        tk.Label(text, text=code or "–", font=("Consolas", 26, "bold"), bg=BG, fg=INK).pack(anchor="w")
        tk.Label(text, text=("On the tablet: parent settings (long-press the gear),\n"
                             "then scan this QR code, or type the code.\n\n"
                             "The first reply takes about 30 seconds\nwhile Puki wakes up."),
                 font=("Segoe UI", 11), bg=BG, fg=INK, justify="left").pack(anchor="w", pady=(10, 0))

    def on_done(self, code):
        self.running = False
        self.close_button.configure(state="normal")
        if code in (0, None) and not self.finished:
            self.status.configure(text="Done.", fg=ACCENT)

    def choose_dialog(self, title, options, default):
        win = tk.Toplevel(self.root)
        win.title("Puki")
        win.configure(bg=BG)
        win.transient(self.root)
        win.grab_set()
        tk.Label(win, text=title, font=("Segoe UI", 13, "bold"), bg=BG, fg=INK).pack(anchor="w", padx=18, pady=(14, 8))
        choice = tk.StringVar(value=default)
        for key, desc in options:
            row = tk.Frame(win, bg=BG)
            row.pack(fill="x", padx=18, pady=3)
            tk.Radiobutton(row, text=key.upper() + ("   (recommended)" if key == default else ""), variable=choice,
                           value=key, font=("Segoe UI", 11, "bold"), bg=BG, fg=INK, anchor="w",
                           activebackground=BG).pack(anchor="w")
            tk.Label(row, text=desc, font=("Segoe UI", 10), bg=BG, fg=MUTED, anchor="w").pack(anchor="w", padx=(26, 0))
        ttk.Button(win, text="OK", command=win.destroy).pack(anchor="e", padx=18, pady=14)
        win.protocol("WM_DELETE_WINDOW", win.destroy)
        self.root.wait_window(win)
        return choice.get()

    def on_close(self):
        if self.running and not messagebox.askyesno(
                "Puki", "Stop the setup? Downloads resume where they stopped next time.", parent=self.root):
            return
        if self.running:
            os._exit(1)  # the worker may be mid-download; the next run resumes
        self.root.destroy()


def run(argv, title="Setting up Puki"):
    """Runs install.py with [argv] in the window; returns its exit code when the window closes."""
    app = App(title)
    install.UI = TkUI(app)
    result = {"code": None}

    def work():
        sys.argv = ["install"] + list(argv)
        try:
            install.main()
            result["code"] = 0
        except SystemExit as e:
            result["code"] = e.code
        except Exception as e:  # show it, don't vanish
            install.UI.fail(f"{type(e).__name__}: {e}")
            result["code"] = 1
        app.post("done", result["code"])

    threading.Thread(target=work, daemon=True).start()
    app.root.mainloop()
    return result["code"]


if __name__ == "__main__":
    sys.exit(run(sys.argv[1:]))
