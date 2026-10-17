"""Puki's line packs: lines for the game's moments, written at night, approved by a parent.

The tablet asks Puki to react to moments ("in the animal game: the cow found its food"). The
doorbell notes each such situation (text the app writes, never the child's words). At night,
while the brain is idle, it writes a few candidate lines for the most common situations that
don't have enough yet; the ones that pass the filters are voiced and wait for the parent on the
dashboard. Only approved lines go to the tablet, which then plays them instantly, without
waking the brain, and even when the PC is off.

Everything is kept next to this file: pack.json (situations and lines, private: a line may say
the child's name) and pack/<id>.ogg (the voiced lines). Only the doorbell writes them.
"""

import hashlib
import json
import os
import re
import secrets
import threading
import time

from common import write_private

STATUSES = ("pending", "approved", "rejected")
ID = re.compile(r"^[0-9a-f]{12}$")


def situation_key(situation, lang, gender):
    return hashlib.sha256(f"{lang}|{gender}|{situation}".encode("utf-8")).hexdigest()[:12]


class Pack:
    def __init__(self, folder):
        self.file = os.path.join(folder, "pack.json")
        self.audio_dir = os.path.join(folder, "pack")
        self.lock = threading.Lock()
        self.situations = {}  # key -> {situation, lang, gender, count, last}
        self.lines = {}       # id -> {id, key, situation, lang, gender, text, status, made}
        try:
            with open(self.file, encoding="utf-8") as f:
                data = json.load(f)
            self.situations = data.get("situations", {})
            self.lines = data.get("lines", {})
        except (OSError, ValueError):
            pass

    def _save(self):
        write_private(self.file, json.dumps({"situations": self.situations, "lines": self.lines},
                                            ensure_ascii=False, indent=1))

    # ---- What the tablet asked about ----

    def note_situation(self, situation, lang, gender):
        """Counts one request for [situation] (the app's own words for a moment in a game)."""
        situation = (situation or "").strip()[:300]
        if not situation or lang not in ("he", "en") or gender not in ("girl", "boy"):
            return
        key = situation_key(situation, lang, gender)
        with self.lock:
            s = self.situations.setdefault(key, {"situation": situation, "lang": lang, "gender": gender, "count": 0})
            s["count"] += 1
            s["last"] = time.time()
            self._save()

    def wanted(self, per_situation=3, limit=20):
        """The most asked-about situations with fewer than [per_situation] approved or waiting
        lines: [(key, situation dict, how many lines to write)], at most [limit]."""
        with self.lock:
            have = {}
            for line in self.lines.values():
                if line["status"] in ("pending", "approved"):
                    have[line["key"]] = have.get(line["key"], 0) + 1
            out = [(k, dict(s), per_situation - have.get(k, 0)) for k, s in self.situations.items()
                   if have.get(k, 0) < per_situation]
        out.sort(key=lambda x: -x[1]["count"])
        return out[:limit]

    def known_texts(self, key):
        """Every line already written for [key] (approved, waiting or rejected), so new ones differ."""
        with self.lock:
            return [line["text"] for line in self.lines.values() if line["key"] == key]

    # ---- Lines ----

    def add(self, key, text, audio):
        """A new candidate line for situation [key], with its voiced [audio] (OGG); waits for a parent."""
        with self.lock:
            s = self.situations.get(key)
            if not s or not text or not audio:
                return None
            line_id = secrets.token_hex(6)
            os.makedirs(self.audio_dir, exist_ok=True)
            with open(os.path.join(self.audio_dir, line_id + ".ogg"), "wb") as f:
                f.write(audio)
            self.lines[line_id] = {"id": line_id, "key": key, "situation": s["situation"], "lang": s["lang"],
                                   "gender": s["gender"], "text": text, "status": "pending", "made": time.time()}
            self._save()
            return line_id

    def decide(self, line_id, approve):
        """A parent's ✓ or ✗. A rejected line keeps its text (so it isn't written again), not its audio."""
        with self.lock:
            line = self.lines.get(line_id)
            if not line:
                return False
            line["status"] = "approved" if approve else "rejected"
            if not approve:
                try:
                    os.remove(os.path.join(self.audio_dir, line_id + ".ogg"))
                except OSError:
                    pass
            self._save()
            return True

    def pending(self):
        with self.lock:
            return sorted((dict(l) for l in self.lines.values() if l["status"] == "pending"), key=lambda l: l["made"])

    def counts(self):
        with self.lock:
            out = {s: 0 for s in STATUSES}
            for line in self.lines.values():
                out[line["status"]] += 1
            return out

    def manifest(self):
        """What the tablet syncs: the approved lines (no audio; it fetches that by id)."""
        with self.lock:
            lines = [{k: l[k] for k in ("id", "situation", "lang", "gender", "text")}
                     for l in self.lines.values() if l["status"] == "approved"]
        lines.sort(key=lambda l: l["id"])
        version = hashlib.sha256("".join(l["id"] for l in lines).encode()).hexdigest()[:12]
        return {"version": version, "lines": lines}

    def audio(self, line_id, approved_only=True):
        """A line's audio, or None (an unknown or malformed id, or not approved when [approved_only])."""
        if not ID.match(line_id or ""):
            return None
        with self.lock:
            line = self.lines.get(line_id)
            if not line or (approved_only and line["status"] != "approved"):
                return None
        try:
            with open(os.path.join(self.audio_dir, line_id + ".ogg"), "rb") as f:
                return f.read()
        except OSError:
            return None
