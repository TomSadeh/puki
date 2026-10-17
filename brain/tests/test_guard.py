"""The doorbell's lockout: every try with a code counts until one is right, so guessing (even with
many connections at once) gets at most MAX_FAILS tries per address, then a lockout."""
import os
import shutil
import sys

import pytest

BRAIN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BRAIN)


@pytest.fixture
def doorbell(tmp_path, monkeypatch):
    """doorbell imported with its folder in tmp_path (the import makes a pairing code there)."""
    shutil.copy(os.path.join(BRAIN, "config.example.json"), tmp_path)
    monkeypatch.setenv("PUKI_BRAIN_DIR", str(tmp_path))
    sys.modules.pop("doorbell", None)
    import doorbell
    yield doorbell
    sys.modules.pop("doorbell", None)


def test_tries_are_capped_then_locked_out(doorbell):
    g = doorbell.Guard()
    assert all(g.attempt("10.0.0.5") for _ in range(g.MAX_FAILS))
    assert not g.attempt("10.0.0.5")
    assert not g.attempt("10.0.0.5")  # still locked out
    assert g.attempt("10.0.0.6")      # another address isn't


def test_the_right_code_clears_the_count(doorbell):
    g = doorbell.Guard()
    for _ in range(g.MAX_FAILS - 1):
        g.attempt("10.0.0.5")
    g.succeeded("10.0.0.5")
    assert all(g.attempt("10.0.0.5") for _ in range(g.MAX_FAILS))


def test_the_lockout_ends(doorbell, monkeypatch):
    g = doorbell.Guard()
    for _ in range(g.MAX_FAILS + 1):
        g.attempt("10.0.0.5")
    now = doorbell.time.time()
    monkeypatch.setattr(doorbell.time, "time", lambda: now + g.LOCKOUT_S + 1)
    assert g.attempt("10.0.0.5")


def test_the_doorbell_link_carries_the_fingerprint(doorbell):
    doorbell.CERT_FP = "ab" * 32
    url = doorbell.DashboardApp().pair_url()
    assert f"&fp={doorbell.tls.for_link(doorbell.CERT_FP)}" in url and f"code={doorbell.PAIRING_CODE}" in url
