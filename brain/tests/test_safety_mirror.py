"""The phone brain's Safety.kt mirrors safety.py. The word lists are copied by
scripts/sync_safety_kt.py (CI checks with --check); these are the tables copied by hand."""
import json
import os
import re
import sys

BRAIN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BRAIN)
import safety  # noqa: E402

KT = open(os.path.join(BRAIN, "..", "app", "src", "main", "java", "io", "github", "tomsadeh", "puki", "brain", "Safety.kt"),
          encoding="utf-8").read()


def kt_block(name):
    """The text of `val NAME = ...` up to the next blank line."""
    i = KT.index(f"val {name} =")
    return KT[i:KT.index("\n\n", i)]


def test_banned_phrases_match():
    block = kt_block("BANNED_PHRASES")
    assert sorted(re.findall(r'"([^"]+)"', block)) == sorted(safety.BANNED_PHRASES)


def test_game_allowances_match():
    block = kt_block("ALLOWED_IN_GAME")
    for game, words in safety.ALLOWED_IN_GAME.items():
        assert json.dumps(game) in block
        for w in words:
            assert json.dumps(w, ensure_ascii=False) in block


def test_min_digit_mass_matches():
    assert f"MIN_DIGIT_MASS = {safety.MIN_DIGIT_MASS}" in KT


def test_english_replies_match():
    for category, line in safety.replies("en", True).items():
        assert f'"{category}" to {json.dumps(line, ensure_ascii=False)}' in KT, category


def test_hebrew_replies_have_every_category():
    he = KT[KT.index("fun replies("):KT.index("fun guard(")]
    for category in safety.replies("he", True):
        assert f'"{category}" to' in he, category
