"""Puki's line packs: situations are counted, lines wait for a parent, and only approved lines
(and their audio) go to the tablet."""
import os
import sys

BRAIN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BRAIN)
import packs  # noqa: E402
import safety  # noqa: E402

COW = "במשחק החיות: הפרה מצאה את האוכל שלה."


def test_situations_are_counted_and_wanted_most_asked_first(tmp_path):
    p = packs.Pack(str(tmp_path))
    for _ in range(3):
        p.note_situation(COW, "he", "girl")
    p.note_situation("In the garden: the dog barked.", "en", "girl")
    wanted = p.wanted(per_situation=3, limit=10)
    assert [w[1]["situation"] for w in wanted] == [COW, "In the garden: the dog barked."]
    assert wanted[0][2] == 3  # three lines to write


def test_bad_situations_are_ignored(tmp_path):
    p = packs.Pack(str(tmp_path))
    p.note_situation("", "he", "girl")
    p.note_situation(COW, "fr", "girl")
    p.note_situation(COW, "he", "other")
    assert p.wanted() == []


def test_lines_wait_for_a_parent_and_only_approved_ones_reach_the_tablet(tmp_path):
    p = packs.Pack(str(tmp_path))
    p.note_situation(COW, "he", "girl")
    key = p.wanted()[0][0]
    a = p.add(key, "יש! הפרה אוכלת!", b"OggS-a")
    b = p.add(key, "מממ, איזה אוכל טעים!", b"OggS-b")
    assert p.manifest()["lines"] == []
    assert p.audio(a) is None and p.audio(a, approved_only=False) == b"OggS-a"
    assert p.decide(a, True) and p.decide(b, False)
    m = p.manifest()
    assert [l["text"] for l in m["lines"]] == ["יש! הפרה אוכלת!"]
    assert m["lines"][0]["situation"] == COW and m["lines"][0]["gender"] == "girl"
    assert p.audio(a) == b"OggS-a"
    assert p.audio(b, approved_only=False) is None  # a turned-down line keeps no audio
    assert "מממ, איזה אוכל טעים!" in p.known_texts(key)  # ...but its text, so it isn't written again
    assert p.counts() == {"pending": 0, "approved": 1, "rejected": 1}


def test_a_moment_with_enough_lines_isnt_wanted(tmp_path):
    p = packs.Pack(str(tmp_path))
    p.note_situation(COW, "he", "girl")
    key = p.wanted()[0][0]
    for i in range(3):
        p.add(key, f"שורה {i}", b"x")
    assert p.wanted(per_situation=3) == []
    assert p.wanted(per_situation=4)[0][2] == 1


def test_it_survives_a_restart(tmp_path):
    p = packs.Pack(str(tmp_path))
    p.note_situation(COW, "he", "boy")
    key = p.wanted()[0][0]
    line = p.add(key, "יש!", b"x")
    p.decide(line, True)
    again = packs.Pack(str(tmp_path))
    assert again.manifest() == p.manifest() and again.audio(line) == b"x"


def test_ids_are_checked(tmp_path):
    p = packs.Pack(str(tmp_path))
    assert p.audio("../pack.json") is None and p.audio("") is None and not p.decide("nope", True)


def test_the_line_check_fails_closed():
    assert safety.line_ok({"0": 0.97, "1": 0.02})
    assert not safety.line_ok({"0": 0.8, "1": 0.2})       # not clearly fine
    assert not safety.line_ok({"0": 0.2, "1": 0.1})       # mostly not a digit at all
    assert not safety.line_ok({})
    assert '"יש! הפרה!"' in safety.line_check_prompt("יש! הפרה!")
