"""מלשין לרנינג: lines of Puki's that a parent flagged as wrong.

Each report is one line of JSON in reports.jsonl next to the brain: what Puki said, what the
child said or what was happening, the language and the game. They hold the child's words (and
maybe her name), so they stay on this PC and out of git. They're there to learn from by hand:
each one becomes a case in the stress test, and the word lists, prompt or gate get fixed
against it. Nothing learns from them by itself, so a bad report can't teach Puki anything worse.
"""
import json
import os
import threading
import time

# Puki.exe unpacks the brain's files elsewhere and says where (tray.py)
HERE = os.environ.get("PUKI_BRAIN_DIR") or os.path.dirname(os.path.abspath(__file__))
REPORTS_FILE = os.path.join(HERE, "reports.jsonl")
_lock = threading.Lock()


def _text(value, limit):
    return str(value or "")[:limit].strip()


def add(source, said, heard="", situation="", lang="he", game="", note=""):
    """Keeps a report; [source] is "tablet" or "dashboard". Returns what was kept."""
    entry = {
        "t": time.strftime("%Y-%m-%d %H:%M:%S"),
        "source": source,
        "said": _text(said, 400),
        "heard": _text(heard, 200),
        "situation": _text(situation, 300),
        "lang": "en" if lang == "en" else "he",
        "game": _text(game, 40),
        "note": _text(note, 300),
    }
    with _lock, open(REPORTS_FILE, "a", encoding="utf-8") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    return entry


def load():
    """Every report so far, oldest first."""
    if not os.path.exists(REPORTS_FILE):
        return []
    with _lock, open(REPORTS_FILE, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]
