"""The safety nets for what the child says to Puki (safety.py)."""
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import safety  # noqa: E402


@pytest.mark.parametrize("heard,lang,cat", [
    ("כואב לי", "he", "hurt"),
    ("וכואב לי בבטן", "he", "hurt"),  # with a prefix letter
    ("יש דם", "he", "hurt"),
    ("אני רוצה אמא", "he", "parent"),
    ("בוא נשחק עם סכין", "he", "danger"),
    ("בוא נדליק אש", "he", "danger"),
    ("אני רוצה לאכול את התרופה", "he", "danger"),
    ("הכלב מת?", "he", "big_question"),
    ("בוא נסתיר מאמא", "he", "secrets"),
    ("די פוקי! שקט!", "he", "quiet"),
    ("אני לא רוצה ללכת לישון", "he", "bedtime"),
    ("אתה טיפש", "he", "unkind"),
    ("אבא כועס עליי", "he", "feelings"),
    ("I'm sad", "en", "feelings"),
    ("אתה אוהב אותי?", "he", "love"),
    ("I fell down", "en", "hurt"),
    ("let's play with a knife", "en", "danger"),
    ("is the doggy dead?", "en", "big_question"),
    ("I want mommy", "en", "parent"),
    ("buy me a game", "en", "buying"),
])
def test_catches(heard, lang, cat):
    assert safety.guard(heard, lang, girl=True)[0] == cat


@pytest.mark.parametrize("heard,lang", [
    ("אני רוצה מתוק", "he"),        # "מת" (dead) is a whole word only
    ("מתנה לי", "he"),
    ("שמש גדולה", "he"),
    ("ראש", "he"),
    ("אני אוהבת פרה", "he"),
    ("פרה!", "he"),
    ("a warm hug", "en"),            # not "war"
    ("I love the pillow", "en"),     # not "pill"
    ("let us match colors", "en"),   # not "matches"
    ("the doggy says woof", "en"),
    # From a review: words in Puki's own games, and Hebrew prefixes of other words
    ("בוא נספור מספרים", "he"),      # not "מספריים" (scissors)
    ("פוקי נפלא", "he"),             # not "נפל"
    ("אני רוצה סודה", "he"),          # not "סוד"
    ("שמים את העוגה בסיר", "he"),     # a pot in the kitchen
    ("החתול נעלם", "he"),            # peekaboo
    ("אמא שלי אוהבת תפוזים", "he"),  # mommy mentioned, not called for
    ("הכלב הלך", "he"),
    ("the tower fell", "en"),
    ("fire truck", "en"),
    ("play store", "en"),
    ("out the window, a bird", "en"),
    ("all gone!", "en"),
])
def test_ordinary_play_passes(heard, lang):
    assert safety.guard(heard, lang, girl=True) is None


@pytest.mark.parametrize("heard", ["מהתרופות", "והסכין", "בהשקע", "וכשנפלתי"])
def test_stacked_hebrew_prefixes(heard):
    assert safety.guard(heard, "he", girl=True) is not None


def test_a_game_may_use_its_own_words():
    knife = "בוא נחתוך את העוגה עם סכין"
    assert safety.guard(knife, "he", girl=True, game="myday/kitchen") is None
    assert safety.guard(knife, "he", girl=True)[0] == "danger"


def test_replies_follow_the_childs_gender():
    assert safety.guard("כואב לי", "he", girl=True)[1].count("בואי") == 1
    assert "בוא " in safety.guard("כואב לי", "he", girl=False)[1]
    assert "בואי" not in safety.guard("כואב לי", "he", girl=False)[1]


def test_every_category_has_a_reply_in_both_languages():
    for lang in ("he", "en"):
        for girl in (True, False):
            r = safety.replies(lang, girl)
            assert {cat for cat, _, _ in safety.RULES} <= set(r)
            assert set(safety.GATE_CATEGORY.values()) <= set(r)


def test_banned_phrases():
    assert not safety.reply_ok("Hooray! I love you so much!")
    assert not safety.reply_ok("אמא בדרך!")
    assert safety.reply_ok("בדרך כלל הפרה אוכלת עשב")  # "usually", not "on her way"
    assert not safety.reply_ok("אני החבר הכי טוב שלך")
    assert safety.reply_ok("יש! ירוק כמו העשב!")


def test_gate_threshold():
    ordinary = {"0": 0.97, "1": 0.02, "2": 0.01}
    assert safety.gate_category(ordinary, 0.95) is None
    assert safety.gate_category(ordinary, 0.99) == "hurt"   # a stricter threshold catches more
    assert safety.gate_category({"0": 0.2, "2": 0.7, "1": 0.1}, 0.9) == "danger"
    # Fails closed: when the first token wasn't mostly a digit, it's "unclear", not ordinary
    assert safety.gate_category({}, 0.9) == "unclear"
    assert safety.gate_category({"0": 0.3, "1": 0.05}, 0.9) == "unclear"
