"""Puki remembers: exchanges are logged, a diary entry covers them, and the prompt gets the
profile, the last week's diary and today's session so far."""
import os
import sys
import time

BRAIN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BRAIN)
import memory  # noqa: E402

DAY = 24 * 3600


def at(days_ago, hour=10):
    t = time.localtime(time.time() - days_ago * DAY)
    return time.mktime((t.tm_year, t.tm_mon, t.tm_mday, hour, 0, 0, 0, 0, -1))


def test_nothing_to_remember_yet(tmp_path):
    m = memory.Memory(str(tmp_path))
    assert m.prompt_block() == "" and m.unwritten() == [] and m.diary() == []


def test_the_session_so_far_is_in_the_prompt(tmp_path):
    m = memory.Memory(str(tmp_path))
    now = at(0, 11)
    m.note("say", "he", situation="במשחק הצבעים בחרו כחול. זה נכון!", said="יש! כחול!", t=now - 60)
    m.note("listen", "he", heard="פיל!", said="פיל גדול!", t=now - 30)
    block = m.prompt_block(now=now)
    assert "Just now:" in block and 'She said: "פיל!"' in block and "בחרו כחול" in block
    assert "never make one up" in block
    assert 'He said: "פיל!"' in m.prompt_block(now=now, girl=False)


def test_a_diary_entry_covers_the_session_and_is_remembered_by_day(tmp_path):
    m = memory.Memory(str(tmp_path))
    for i in range(4):
        m.note("say", "he", situation=f"רגע {i}", said="יש!", t=at(1) + i)
    assert len(m.unwritten()) == 4
    m.add_entry("She fed the elephant and said \"פיל!\" twice.", m.unwritten()[-1]["t"])
    assert m.unwritten() == []
    m.set_profile("- Loves elephants\n- Says \"פיל\"")
    block = m.prompt_block(now=at(0, 9))
    assert "Yesterday: She fed the elephant" in block and "About her:\n- Loves elephants" in block
    assert "Just now:" not in block  # yesterday's session is in the diary, not "just now"
    assert "רגע" not in block


def test_old_entries_leave_the_prompt(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.add_entry("A long time ago.", at(10))
    m.add_entry("Three days ago.", at(3))
    block = m.prompt_block(now=at(0))
    assert "3 days ago: Three days ago." in block and "A long time ago" not in block


def test_a_broken_line_doesnt_lose_the_log(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("say", "he", situation="א", said="ב")
    with open(m.log_path, "a", encoding="utf-8") as f:
        f.write('{"t": 1, "kind"')  # cut short by a crash
    m.note("say", "he", situation="ג", said="ד")
    assert [e["situation"] for e in m.entries()] == ["א", "ג"]


def test_the_prompts_quote_her_words_and_follow_gender(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("repeat", "he", situation="כלב", heard="כב", t=at(0))
    entries = m.entries()
    diary = m.diary_prompt(entries, "the little boy", girl=False)
    assert 'he tried the word "כלב" and said "כב"' in diary and "his words" in diary
    assert "Only facts from the log" in diary
    assert "(empty: this is the first session)" in m.profile_prompt("He played.", "the little boy", girl=False)


def test_fields_are_capped(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("listen", "he", heard="א" * 5000, said="ב")
    assert len(m.entries()[0]["heard"]) == memory.FIELD_CHARS
    m.add_entry("x" * 5000, 1.0)
    assert len(m.diary()[0]["text"]) == memory.ENTRY_CHARS


def test_a_game_line_reaches_back_only_to_the_same_thing(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("say", "he", situation="במשחק החיות: הילדה האכילה את הפיל בבננה.", said="יאמי!", t=at(1, 10))
    m.note("say", "he", situation="במשחק החיות: הילדה האכילה את הקוף.", said="מממ!", t=at(1, 11))
    m.add_entry("She fed the elephant and the monkey.", at(1, 11))
    when, then = m.related("במשחק החיות: הילדה האכילה את הפיל.", now=at(0))
    assert when == ("yesterday", "אתמול") and "הפיל" in then["situation"]  # not the monkey just because both were fed
    assert m.related("במשחק הצבעים: מחפשים את הצבע צהוב.", now=at(0)) is None
    assert m.related("במשחק החיות: הפרה מחכה לאוכל.", now=at(0)) is None


def test_this_session_isnt_reached_back_to(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("say", "he", situation="במשחק החיות: הילדה האכילה את הפיל.", said="יאמי!", t=at(0, 9))
    assert m.related("במשחק החיות: הילדה האכילה את הפיל שוב.", now=at(0, 10)) is None  # no diary yet: it's "just now"


def test_word_forms_meet():
    assert memory._forms("הפילים") & memory._forms("פיל")
    assert memory._forms("בננות") & memory._forms("בננה")
    assert memory._forms("כחולה") & memory._forms("כחול")
    assert memory._forms("וְהַפִּיל") & memory._forms("פיל")  # nikud and two prefixes
    assert memory._forms("dogs") & memory._forms("dog")
    assert not memory._forms("פרה") & memory._forms("פרח")


def test_her_own_words_and_say_it_tries_are_found(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("listen", "he", heard="פילים! פילים גדולים!", said="וואו!", t=at(2, 10))
    m.note("repeat", "he", situation="כלב", heard="כב", t=at(1, 10))
    m.add_entry("She talked about elephants and tried to say dog.", at(1, 10))
    when, moment = m.related("במשחק החיות: הילדה האכילה את הפיל.", now=at(0))
    assert when[0] == "2 days ago" and moment["kind"] == "listen"
    assert m.moment_text(moment, "he") == 'אמרה לך "פילים! פילים גדולים!"'
    assert m.moment_text(moment, "en", girl=False) == 'he said to you "פילים! פילים גדולים!"'
    when, moment = m.related("במשחק המחבואים: הכלב מתחבא ומחפשים אותו.", now=at(0))
    assert moment["kind"] == "repeat" and "להגיד \"כלב\"" in m.moment_text(moment, "he")


def test_a_diary_quote_is_found_when_the_log_has_nothing(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.add_entry('She drew a sun and said "שמש!" twice. She liked the yellow paint.', at(1, 10))
    when, moment = m.related("בציור: הילדה ציירה שמש צהובה.", now=at(0))
    assert when[0] == "yesterday" and moment == {"kind": "diary", "text": 'She drew a sun and said "שמש!" twice.'}


def test_names_dont_make_moments_alike(tmp_path):
    m = memory.Memory(str(tmp_path))
    m.note("listen", "he", heard="פוקי! פוקי!", said="שלום!", t=at(1, 10))
    m.add_entry("She called Puki.", at(1, 10))
    assert m.related("במשחק הצבעים: פוקי מחפש כחול.", now=at(0), names=("פוקי", "Puki")) is None
    assert m.related("במשחק הצבעים: פוקי מחפש כחול.", now=at(0)) is not None  # (without the names, it would)
