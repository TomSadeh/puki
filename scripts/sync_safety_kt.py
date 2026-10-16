"""Copies brain/safety.py's word lists into the phone brain's Safety.kt, so the two never drift.

    python scripts/sync_safety_kt.py

Run it after changing RULES in safety.py (the Kotlin tests then check the phone agrees)."""
import json
import os
import sys

repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.path.insert(0, repo + "/brain")
import safety  # noqa: E402

p = repo + "/app/src/main/java/io/github/tomsadeh/puki/brain/Safety.kt"
s = open(p, encoding="utf-8").read()


def kt_list(items, indent):
    out, line = [], ""
    for it in items:
        piece = json.dumps(it, ensure_ascii=False) + ", "
        if len(line) + len(piece) > 100:
            out.append(line.rstrip())
            line = ""
        line += piece
    out.append(line.rstrip().rstrip(","))
    return "listOf(" + ("\n" + indent).join(out) + ")"


COMMENTS = {"parent": "        // Last, and only as phrases: mommy and daddy come up in ordinary play all the time\n"}
rules = [COMMENTS.get(cat, "") + f'        Rule("{cat}",\n            {kt_list(he, "                ")},\n            {kt_list(en, "                ")}),'
         for cat, he, en in safety.RULES]
start = s.index("    val RULES = listOf(\n")
end = s.index("    /** What a game says itself")
s = s[:start] + "    val RULES = listOf(\n" + "\n".join(rules) + "\n    )\n\n" + s[end:]
open(p, "w", encoding="utf-8", newline="\n").write(s)
print("Safety.kt: word lists copied from safety.py")
