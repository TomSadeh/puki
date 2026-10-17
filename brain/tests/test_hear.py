"""Answering a game out loud (POST /hear): the dashboard shows what was asked and heard, and the
doorbell forwards it to the brain like the other listening routes."""
import json
import os
import shutil
import sys

import pytest

BRAIN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BRAIN)


@pytest.fixture
def doorbell(tmp_path, monkeypatch):
    shutil.copy(os.path.join(BRAIN, "config.example.json"), tmp_path)
    monkeypatch.setenv("PUKI_BRAIN_DIR", str(tmp_path))
    sys.modules.pop("doorbell", None)
    import doorbell
    yield doorbell
    sys.modules.pop("doorbell", None)


def test_the_dashboard_shows_the_answers_and_what_was_heard(doorbell):
    full = "/hear?lang=he&hint=%D7%90%D7%93%D7%95%D7%9D&hint=%D7%9B%D7%97%D7%95%D7%9C&name=&gender=girl"
    req = doorbell.describe_request("/hear", full, b"RIFF")
    assert req == {"text": "answer out loud: אדום / כחול", "lang": "he"}
    reply = json.dumps({"heard": "כחול"}, ensure_ascii=False).encode()
    assert doorbell.describe_reply("/hear", {}, reply) == "heard: כחול"
    assert doorbell.describe_reply("/hear", {}, b'{"heard": ""}') == "heard: nothing clear"


def test_hear_is_forwarded_to_the_brain():
    with open(os.path.join(BRAIN, "doorbell.py"), encoding="utf-8") as f:
        src = f.read()
    assert '"/repeat", "/hear")' in src
    with open(os.path.join(BRAIN, "brain.py"), encoding="utf-8") as f:
        assert 'url.path == "/hear"' in f.read()
