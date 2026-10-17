"""Puki remembers: what happened in play, kept on this PC, so Puki can say "yesterday you fed
the elephant!" and the bedtime story can reach back (see ROADMAP.md, "Puki remembers").

- **The log is the memory.** Every exchange (a game moment and Puki's line, what the child said
  and Puki's answer, a word she tried in Say it) is appended to memory/log.jsonl. It's never
  edited; everything else is derived from it and can be written again.
- **Work happens off the hot path.** When the brain is about to sleep, Gemma reads the session's
  log and writes a short diary entry (diary.json), then updates her profile page (profile.json).
- **Replies stay cheap.** No retrieval yet: the profile, the last week's diary and today's
  session so far fit in Gemma's context ([prompt_block], capped at a few thousand characters).

What the child said about being hurt, scared and the like (safety.py's careful answers) is never
logged: Puki shouldn't bring that up later. The files are private, next to this file; only
brain.py writes them, one request at a time.
"""

import json
import os
import threading
import time

from common import write_private

DIARY_DAYS = 7          # diary entries in the prompt: the last week
TODAY_EVENTS = 15       # today's session in the prompt: the last few exchanges
PROFILE_CHARS = 1200
ENTRY_CHARS = 500
FIELD_CHARS = 200


def pronouns(girl):
    """She/he, for what Gemma reads and writes about the child."""
    return {"She": "She", "she": "she", "her": "her", "obj": "her"} if girl else \
        {"She": "He", "she": "he", "her": "his", "obj": "him"}


# Words every game moment has, which say nothing about what it was about
_STOP = {"במשחק", "הילדה", "הילד", "בחרו", "נכון", "מחפשים", "מחכה", "שוב", "אבל", "חיפשנו", "הצבע", "צבע",
         "האכילה", "האכיל", "אכילה", "אכיל", "ציירה", "צייר", "מצאו", "מצאה", "מצא", "לאוכל", "אוכל",
         "the", "child", "game", "picked", "that", "right", "looking", "for", "again", "waiting", "and", "with",
         "fed", "drew", "found", "food"}
_PREFIX = "הבולש"


def _content_words(situation):
    """The words that say what a game moment was about ("פיל", "בננה"), without Hebrew prefixes."""
    out = set()
    for w in "".join(c if c.isalnum() else " " for c in situation.split(":", 1)[-1].lower()).split():
        if w in _STOP or len(w) < 3:
            continue
        while len(w) > 3 and w[0] in _PREFIX:
            w = w[1:]
        out.add(w)
    return out - _STOP


def day_of(t):
    return time.strftime("%Y-%m-%d", time.localtime(t))


class Memory:
    def __init__(self, folder):
        self.dir = os.path.join(folder, "memory")
        self.log_path = os.path.join(self.dir, "log.jsonl")
        self.diary_path = os.path.join(self.dir, "diary.json")
        self.profile_path = os.path.join(self.dir, "profile.json")
        self.lock = threading.Lock()

    # ---------- The log ----------

    def note(self, kind, lang, game="", situation="", heard="", said="", t=None):
        """Appends one exchange: [kind] is say, listen, look, story, repeat or hear."""
        entry = {"t": round(t or time.time(), 1), "kind": kind, "lang": lang}
        for k, v in (("game", game), ("situation", situation), ("heard", heard), ("said", said)):
            if v:
                entry[k] = str(v)[:FIELD_CHARS]
        line = json.dumps(entry, ensure_ascii=False) + "\n"
        with self.lock:
            os.makedirs(self.dir, exist_ok=True)
            new = not os.path.exists(self.log_path)
            if not new:
                with open(self.log_path, "rb") as f:
                    f.seek(0, os.SEEK_END)
                    if f.tell():
                        f.seek(-1, os.SEEK_END)
                        if f.read(1) != b"\n":
                            line = "\n" + line  # a line cut short by a crash stays apart
            with open(self.log_path, "a", encoding="utf-8") as f:
                f.write(line)
            if new:
                try:
                    os.chmod(self.log_path, 0o600)
                except OSError:
                    pass

    def entries(self, since=0.0):
        try:
            with open(self.log_path, encoding="utf-8") as f:
                out = []
                for line in f:
                    try:
                        e = json.loads(line)
                    except ValueError:
                        continue  # a line cut short by a crash
                    if e.get("t", 0) > since:
                        out.append(e)
                return out
        except OSError:
            return []

    # ---------- Diary and profile ----------

    def _load(self, path, default):
        try:
            with open(path, encoding="utf-8") as f:
                return json.load(f)
        except (OSError, ValueError):
            return default

    def diary(self):
        """[{day, until, text}], oldest first."""
        return self._load(self.diary_path, {"entries": []}).get("entries", [])

    def profile(self):
        return self._load(self.profile_path, {"text": ""}).get("text", "")

    def written_until(self):
        d = self.diary()
        return d[-1]["until"] if d else 0.0

    def unwritten(self):
        """The exchanges no diary entry covers yet."""
        return self.entries(self.written_until())

    def add_entry(self, text, until):
        text = " ".join(str(text).split())[:ENTRY_CHARS]
        with self.lock:
            entries = self.diary()
            entries.append({"day": day_of(until), "until": until, "text": text})
            os.makedirs(self.dir, exist_ok=True)
            write_private(self.diary_path, json.dumps({"entries": entries}, ensure_ascii=False, indent=1))

    def set_profile(self, text):
        text = str(text).strip()[:PROFILE_CHARS]
        with self.lock:
            os.makedirs(self.dir, exist_ok=True)
            write_private(self.profile_path, json.dumps({"text": text, "updated": time.time()}, ensure_ascii=False))

    # ---------- What Gemma is asked ----------

    @staticmethod
    def describe(e, girl=True):
        """One exchange, in a short line for Gemma (the child's words and Puki's, verbatim)."""
        p = pronouns(girl)
        where = f"[{e['game']}] " if e.get("game") else ""
        kind = e.get("kind")
        if kind == "say":
            text = f"{e.get('situation', '')} Puki said: \"{e.get('said', '')}\""
        elif kind == "listen":
            text = f"{p['She']} said: \"{e.get('heard', '')}\". Puki answered: \"{e.get('said', '')}\""
        elif kind == "look":
            text = f"{p['She']} showed Puki a drawing. Puki said: \"{e.get('said', '')}\""
        elif kind == "story":
            text = f"Bedtime story about: {e.get('situation', '')}"
        elif kind == "repeat":
            text = f"Say it: {p['she']} tried the word \"{e.get('situation', '')}\" and said \"{e.get('heard', '')}\""
        elif kind == "hear":
            text = f"Answering out loud ({e.get('situation', '')}), {p['she']} said \"{e.get('heard', '')}\""
        else:
            text = e.get("situation", "")
        return where + text

    def diary_prompt(self, entries, child, girl=True):
        p = pronouns(girl)
        log = "\n".join(time.strftime("%H:%M ", time.localtime(e["t"])) + self.describe(e, girl) for e in entries[-120:])
        return (
            f"You keep a private diary for the parents of {child}, a toddler who plays a tablet game "
            "with Puki, a parrot. Below is the log of one play session. Write the diary entry in "
            f"English: 2 to 4 short sentences about what {p['she']} played, what {p['she']} said (quote {p['her']} words "
            f"exactly, in the language {p['she']} said them), and what {p['she']} liked or found hard. Only facts "
            f"from the log; leave out Puki's own lines unless {p['she']} answered them. No advice, no headings.\n\n"
            f"Log:\n{log}"
        )

    def profile_prompt(self, entry, child, girl=True):
        p = pronouns(girl)
        old = self.profile() or "(empty: this is the first session)"
        return (
            f"This is a short profile page about {child}, a toddler, kept so Puki the parrot can "
            f"remember {p['obj']}. Update it with the newest diary entry. At most 8 short lines, each "
            "starting with '- ': favorite games, animals, colors and things; "
            f"words {p['she']} says (exactly, in {p['her']} language; leave out anything that isn't a real word, "
            f"speech recognition mishears toddlers); what {p['she']}'s learning. Keep what still holds, add what's new, drop "
            "nothing important. Only facts from the page and the entry. Answer with the page only.\n\n"
            f"Profile page:\n{old}\n\nNewest diary entry:\n{entry}"
        )

    def related(self, situation, now=None):
        """An earlier session's game moment about the same thing as [situation] (the elephant
        again), for a game line to reach back to: (when, that moment), or None. Chosen here, not
        by Gemma: told to link to any memory, it links to unrelated ones and mixes them up."""
        now = now or time.time()
        words = _content_words(situation)
        if not words:
            return None
        since = now - DIARY_DAYS * 86400
        for e in reversed(self.entries(since)):
            if e["t"] > self.written_until() or e.get("kind") != "say":
                continue  # this session is "just now" already
            if words & _content_words(e.get("situation", "")):
                age = (time.mktime(time.strptime(day_of(now), "%Y-%m-%d")) -
                       time.mktime(time.strptime(day_of(e["t"]), "%Y-%m-%d"))) / 86400
                when = ("earlier today", "קודם היום") if age < 1 else ("yesterday", "אתמול") if age < 2 else \
                    (f"{int(round(age))} days ago", f"לפני {int(round(age))} ימים")
                return when, e["situation"]
        return None

    def prompt_block(self, now=None, girl=True):
        """What Puki remembers, for the system prompt: profile, the last week's diary, and today
        so far. Empty while there's nothing to remember."""
        now = now or time.time()
        parts = []
        profile = self.profile()
        if profile:
            parts.append(f"About {pronouns(girl)['obj']}:\n" + profile)
        today = day_of(now)
        days = []
        for e in self.diary():
            age = (time.mktime(time.strptime(today, "%Y-%m-%d")) - time.mktime(time.strptime(e["day"], "%Y-%m-%d"))) / 86400
            if 0 <= age < DIARY_DAYS:
                when = "Earlier today" if age < 1 else "Yesterday" if age < 2 else f"{int(round(age))} days ago"
                days.append(f"- {when}: {e['text']}")
        if days:
            parts.append("Your diary:\n" + "\n".join(days))
        start = time.mktime(time.strptime(today, "%Y-%m-%d"))
        session = [e for e in self.entries(max(start, self.written_until()))][-TODAY_EVENTS:]
        if session:
            parts.append("Just now:\n" + "\n".join("- " + self.describe(e, girl) for e in session))
        if not parts:
            return ""
        return ("\n\nWhat you remember (from playing together; use a memory only now and then, when it "
                "fits what just happened, and never make one up):\n" + "\n\n".join(parts))
