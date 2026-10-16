"""Puki's line filter and word check (the phone's Kotlin port has the same tests: PersonaTest.kt)."""
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import persona  # noqa: E402


@pytest.fixture(autouse=True)
def plain_child():
    persona.configure({})  # no name, a girl, as in a fresh install
    yield


def test_keeps_a_warm_short_line():
    assert persona.clean("הפרה רעבה!", "he") == "הפרה רעבה!"  # "רע" inside a word is fine
    assert persona.clean("The cow is hungry!", "en") == "The cow is hungry!"


def test_strips_tags_quotes_and_extra_lines():
    assert persona.clean('"<b>שלום!</b>"', "he") == "שלום!"
    assert persona.clean("Hi there!\nAnd a second line", "en") == "Hi there!"


@pytest.mark.parametrize("line, lang", [
    ("זו טעות.", "he"),
    ("והטעות קטנה.", "he"),  # with a prefix
    ("לא נכון!", "he"),
    ("That's wrong!", "en"),
    ("Not right, try again.", "en"),
])
def test_rejects_harsh_words(line, lang):
    assert persona.clean(line, lang) is None


@pytest.mark.parametrize("line, lang", [
    (" ".join(["מילה"] * 15) + ".", "he"),
    ("x" * 91, "en"),
    ("Hello שלום", "en"),
    ("", "he"),
])
def test_rejects_long_lines_and_other_scripts(line, lang):
    assert persona.clean(line, lang) is None


def test_drops_compliments_about_the_child():
    assert persona.clean("את כל כך חכמה! הפרה אכלה.", "he") == "הפרה אכלה."
    assert persona.clean("You're so smart! The cow ate.", "en") == "The cow ate."
    assert persona.clean("You are here!", "en") == "You are here!"


def test_voice_swaps_misread_words():
    assert persona.fix_misreads("הידד!") == "יש!"
    assert persona.fix_misreads("והידד") == "ויש"
    assert persona.fix_misreads("הידדים") == "הידדים"  # whole words only


def test_child_name_loses_nikud_and_falls_back_to_the_config():
    persona.configure({"child_name": "", "child_gender": "boy"})
    persona.set_child("  שָׁלוֹם ", "girl")
    assert persona.NAME == "שלום" and persona.GIRL
    persona.set_child("", "")
    assert persona.child("en") == "the little boy"


def test_examples_use_the_name():
    persona.set_child("נועה", "girl")
    assert any("נועה" in reply for _, reply in persona.examples("he"))


@pytest.mark.parametrize("heard, target, alts, want", [
    ("הכלב", "כלב", (), "word"),
    ("the dog", "dog", (), "word"),
    ("כלבים", "כלב", (), "word"),
    ("פה", "פרה", (), "close"),         # the first sound
    ("הב הב", "כלב", ("הב",), "close"),  # its sound
    ("בננה", "כלב", (), "tried"),
    ("", "כלב", (), "tried"),
    ("ספרים", "ספרימ", (), "word"),     # final letters match their plain form
])
def test_word_match_is_generous(heard, target, alts, want):
    assert persona.word_match(heard, target, alts) == want


def test_hotwords_include_the_names():
    persona.set_child("נועה", "girl")
    words = persona.hotwords().split()
    assert "פוקי" in words and "נועה" in words
