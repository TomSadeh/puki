"""A request that comes while the brain is going to sleep waits for it, then wakes one brain:
not two (whose different tokens would get every reply refused)."""
import os
import shutil
import sys
import threading
import time

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


class SlowProc:
    """A process that takes a moment to die after it's killed."""
    def __init__(self):
        self.code = None

    def poll(self):
        return self.code

    def kill(self):
        pass

    def wait(self):
        time.sleep(0.4)
        self.code = -9


def test_a_wake_during_a_stop_starts_one_brain(doorbell, monkeypatch):
    b = doorbell.Brain()
    starts = []
    monkeypatch.setattr(b, "_start", lambda: starts.append(time.time()))
    b.state, b.procs = "ready", [SlowProc(), SlowProc()]
    stopping = threading.Thread(target=b.stop, args=("idle",))
    stopping.start()
    time.sleep(0.1)
    assert b.state == "stopping" and b.shown_state == "cold"
    wakes = [threading.Thread(target=b.wake) for _ in range(3)]
    for w in wakes:
        w.start()
    stopping.join()
    for w in wakes:
        w.join()
    time.sleep(0.1)
    assert len(starts) == 1 and b.state == "warming"


def test_a_second_stop_while_stopping_does_nothing(doorbell):
    b = doorbell.Brain()
    b.state, b.procs = "ready", [SlowProc()]
    first = threading.Thread(target=b.stop)
    first.start()
    time.sleep(0.1)
    b.stop()  # returns at once
    assert b.state == "stopping"
    first.join()
    assert b.state == "cold"
