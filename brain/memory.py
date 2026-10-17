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
import re
import threading
import time

from common import write_private

DIARY_DAYS = 7          # diary entries in the prompt: the last week
TODAY_EVENTS = 15       # today's session in the prompt: the last few exchanges
PROFILE_CHARS = 1200
ENTRY_CHARS = 500
FIELD_CHARS = 200
ASK_CHARS = 140


def pronouns(girl):
    """She/he, for what Gemma reads and writes about the child."""
    return {"She": "She", "she": "she", "her": "her", "obj": "her"} if girl else \
        {"She": "He", "she": "he", "her": "his", "obj": "him"}


# Words every game moment has, which say nothing about what it was about
_STOP = {"במשחק", "הילדה", "הילד", "בחרו", "נכון", "מחפשים", "מחכה", "שוב", "אבל", "חיפשנו", "הצבע", "צבע",
         "האכילה", "האכיל", "אכילה", "אכיל", "ציירה", "צייר", "מצאו", "מצאה", "מצא", "לאוכל", "אוכל",
         "the", "child", "game", "picked", "that", "right", "looking", "for", "again", "waiting", "and", "with",
         "fed", "drew", "found", "food",
         # and everyday words, when what she said is searched too
         "אתה", "הנה", "איזה", "הזה", "הזאת", "שלה", "שלו", "איפה", "כמה", "אני", "רוצה", "רוצים", "עוד", "היום",
         "you", "your", "this", "what", "said", "she", "her", "his", "want", "more", "look", "here", "there"}
_PREFIXES = "הובלשמכ"
_FINALS = str.maketrans("ךםןףץ", "כמנפצ")
_NIKUD = dict.fromkeys(range(0x591, 0x5C8))


def _forms(word):
    """The forms a word may come in, so they can meet: "הפילים" and "פיל", "בננות" and "בננה",
    "dogs" and "dog". Up to two Hebrew prefixes (ה, ו, ב, ל, ש, מ, כ), a plural or feminine
    ending, final letters as regular ones."""
    w = word.lower().translate(_NIKUD).translate(_FINALS)
    bases = {w}
    for n in (1, 2):
        if len(w) - n >= 2 and all(c in _PREFIXES for c in w[:n]):
            bases.add(w[n:])
    out = set()
    for b in bases:
        out.add(b)
        for end in ("ימ", "ות", "ה", "es", "s"):
            if b.endswith(end) and len(b) - len(end) >= 2:
                out.add(b[:-len(end)])
    return out


_STOP_FORMS = set().union(*(_forms(w) for w in _STOP))


def _content_words(text, ignore=frozenset()):
    """The forms of the words that say what something was about ("פיל", "בננה"), leaving out
    the words every game moment has (and the game's name, before the colon)."""
    out = set()
    for w in "".join(c if c.isalnum() else " " for c in text.split(":", 1)[-1]).split():
        if len(w) < 3:
            continue
        forms = _forms(w)
        if forms & (_STOP_FORMS | ignore):
            continue
        out |= forms
    return out


_QUOTE = re.compile(r'"([^"]{2,60})"')


def day_of(t):
    return time.strftime("%Y-%m-%d", time.localtime(t))


class Memory:
    def __init__(self, folder):
        self.dir = os.path.join(folder, "memory")
        self.log_path = os.path.join(self.dir, "log.jsonl")
        self.diary_path = os.path.join(self.dir, "diary.json")
        self.profile_path = os.path.join(self.dir, "profile.json")
        self.paused_path = os.path.join(self.dir, "paused")
        self.lock = threading.Lock()

    # ---------- The log ----------

    def note(self, kind, lang, game="", situation="", heard="", said="", t=None):
        """Appends one exchange: [kind] is say, listen, look, story, repeat or hear. Nothing while
        a parent has paused memory."""
        if self.paused():
            return
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

    def add_entry(self, text, until, ask=()):
        """A diary entry covering the play up to [until]; [ask]: questions for the parent to ask
        the child about it later (see [ask_prompt])."""
        text = " ".join(str(text).split())[:ENTRY_CHARS]
        entry = {"day": day_of(until), "until": until, "text": text}
        ask = [" ".join(str(q).split())[:ASK_CHARS] for q in ask if str(q).strip()][:3]
        if ask:
            entry["ask"] = ask
        with self.lock:
            entries = self.diary()
            entries.append(entry)
            self._write_diary(entries)

    def set_profile(self, text):
        text = str(text).strip()[:PROFILE_CHARS]
        with self.lock:
            os.makedirs(self.dir, exist_ok=True)
            write_private(self.profile_path, json.dumps({"text": text, "updated": time.time()}, ensure_ascii=False))

    # ---------- The parent's controls (the dashboard) ----------

    def paused(self):
        return os.path.exists(self.paused_path)

    def set_paused(self, on):
        with self.lock:
            if on:
                os.makedirs(self.dir, exist_ok=True)
                write_private(self.paused_path, "Puki keeps nothing new while this file is here.\n")
            elif os.path.exists(self.paused_path):
                os.remove(self.paused_path)

    def _rewrite_log(self, keep):
        """Keeps only the log entries [keep] says yes to (written to a new file, then swapped in)."""
        kept = [e for e in self.entries() if keep(e)]
        tmp = self.log_path + ".new"
        write_private(tmp, "".join(json.dumps(e, ensure_ascii=False) + "\n" for e in kept))
        os.replace(tmp, self.log_path)

    def _write_diary(self, entries):
        os.makedirs(self.dir, exist_ok=True)
        write_private(self.diary_path, json.dumps({"entries": entries}, ensure_ascii=False, indent=1))

    def forget(self, until):
        """Forgets one diary entry (by its [until]) and the play it was written from, so Puki can't
        reach back to it either. True if there was such an entry."""
        with self.lock:
            entries = self.diary()
            i = next((i for i, e in enumerate(entries) if e["until"] == until), None)
            if i is None:
                return False
            start = entries[i - 1]["until"] if i else 0.0
            if os.path.exists(self.log_path):
                self._rewrite_log(lambda e: not start < e.get("t", 0) <= until)
            del entries[i]
            self._write_diary(entries)
            return True

    def erase(self):
        """Forgets everything: the log, the diary and the profile (pausing stays as it was)."""
        with self.lock:
            for path in (self.log_path, self.diary_path, self.profile_path):
                if os.path.exists(path):
                    os.remove(path)

    def prune(self, keep_days, now=None):
        """Drops the log and diary older than [keep_days] (0 keeps everything)."""
        if not keep_days:
            return
        cutoff = (now or time.time()) - keep_days * 86400
        with self.lock:
            if os.path.exists(self.log_path) and any(e.get("t", 0) < cutoff for e in self.entries()):
                self._rewrite_log(lambda e: e.get("t", 0) >= cutoff)
            entries = self.diary()
            if any(e["until"] < cutoff for e in entries):
                self._write_diary([e for e in entries if e["until"] >= cutoff])

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
            text = (f"Say it: {p['she']} said the word \"{e['situation']}\"" if e.get("heard") else
                    f"Say it: {p['she']} tried to say the word \"{e.get('situation', '')}\"")
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

    @staticmethod
    def ask_prompt(entry, child, lang, girl=True):
        """Questions for the parent to ask the child about this play, later at home: they turn
        screen time into a conversation with the family (the parent reads them on the dashboard)."""
        p = pronouns(girl)
        language = "Hebrew" if lang == "he" else "English"
        gender = (f"{p['she']} is a {'girl' if girl else 'boy'}, so in Hebrew speak to {p['obj']} in the "
                  f"{'feminine' if girl else 'masculine'} form" if lang == "he" else "")
        return (
            f"Here is today's diary entry about {child}, a toddler who played a tablet game with Puki "
            f"the parrot:\n{entry}\n\nWrite 2 or 3 very short, warm questions a parent can ask {p['obj']} "
            f"later at home about what {p['she']} did, in simple {language} a 2-year-old understands "
            f"(for example about an animal {p['she']} fed or a color {p['she']} found). {gender}. "
            "Each question on its own line starting with '- '. Only about things in the entry; no "
            "quizzing, no praise, nothing about the tablet itself."
        )

    def profile_prompt(self, entry, child, girl=True):
        p = pronouns(girl)
        old = self.profile() or "(empty: this is the first session)"
        return (
            f"This is a short profile page about {child}, a toddler, kept so Puki the parrot can "
            f"remember {p['obj']}. Update it with the newest diary entry. At most 8 short lines, each "
            "starting with '- ': favorite games, animals, colors and things; "
            f"words {p['she']} says: at most 8 single words or short phrases, only ones the diary quotes "
            f"{p['obj']} saying, exactly, in {p['her']} language (never Puki's lines, and leave out anything that "
            f"isn't a real word: speech recognition mishears toddlers); what {p['she']}'s learning. Keep "
            "what still holds and add what's new, but keep it short: merge, and drop what doesn't fit "
            "these rules. Only facts from the page and the entry. Answer with the page only.\n\n"
            f"Profile page:\n{old}\n\nNewest diary entry:\n{entry}"
        )

    def related(self, situation, now=None, names=()):
        """Something from an earlier session about the same thing as [situation] (the elephant
        again), for a game line to reach back to: (when, moment), or None. A moment is a game
        moment, something the child said (to Puki, in Say it, answering a game) or, failing
        those, a diary sentence quoting such a word. Chosen here, not by Gemma: told to link to
        any memory, it links to unrelated ones and mixes them up. See [moment_text]."""
        now = now or time.time()
        # The child's and Puki's names are in half of everything: they don't make two moments alike
        ignore = frozenset().union(*(_forms(w) for n in names for w in str(n).split() if len(w) >= 2))
        words = _content_words(situation, ignore)
        if not words:
            return None
        since = now - DIARY_DAYS * 86400
        until = self.written_until()  # later than that is this session: "just now" already
        for e in reversed(self.entries(since)):
            if e["t"] > until:
                continue
            kind = e.get("kind")
            text = {"say": e.get("situation", ""), "listen": e.get("heard", ""), "hear": e.get("heard", ""),
                    "repeat": e.get("situation", "") + " " + e.get("heard", "")}.get(kind)
            if text and words & _content_words(text, ignore):
                return self._when(e["t"], now), e
        for d in reversed(self.diary()):
            if d["until"] < since:
                break
            for sentence in re.split(r"(?<=[.!?])\s+", d["text"]):
                if any(words & _content_words(q, ignore) for q in _QUOTE.findall(sentence)):
                    return self._when(d["until"], now), {"kind": "diary", "text": sentence}
        return None

    @staticmethod
    def _when(t, now):
        age = (time.mktime(time.strptime(day_of(now), "%Y-%m-%d")) -
               time.mktime(time.strptime(day_of(t), "%Y-%m-%d"))) / 86400
        if age < 1:
            return "earlier today", "קודם היום"
        if age < 2:
            return "yesterday", "אתמול"
        return f"{int(round(age))} days ago", f"לפני {int(round(age))} ימים"

    @staticmethod
    def moment_text(moment, lang, girl=True):
        """A moment from [related], as Puki's hint says it in [lang]."""
        kind = moment.get("kind")
        heard = moment.get("heard", "")
        if kind == "say":
            return moment.get("situation", "")
        if kind == "diary":
            return moment["text"]
        if lang == "he":
            said = "אמרה" if girl else "אמר"
            if kind == "listen":
                return f'{said} לך "{heard}"'
            if kind == "repeat":
                if heard:
                    return f'{"אמרה" if girl else "אמר"} את המילה "{moment.get("situation", "")}"'
                return f'{"ניסתה" if girl else "ניסה"} להגיד "{moment.get("situation", "")}"'
            return f'{"ענתה" if girl else "ענה"} "{heard}"'
        p = pronouns(girl)
        if kind == "listen":
            return f'{p["she"]} said to you "{heard}"'
        if kind == "repeat":
            if heard:
                return f'{p["she"]} said the word "{moment.get("situation", "")}"'
            return f'{p["she"]} tried to say "{moment.get("situation", "")}"'
        return f'{p["she"]} answered "{heard}"'

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
