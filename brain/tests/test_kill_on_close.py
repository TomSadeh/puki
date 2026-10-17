"""The brain and llama-server live as long as the doorbell: not shorter (the thread that started
them ends right after a wake), and not longer (a killed doorbell takes them along)."""
import os
import shutil
import subprocess
import sys
import threading
import time

import pytest

BRAIN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BRAIN)

linux_only = pytest.mark.skipif(not sys.platform.startswith("linux"), reason="PR_SET_PDEATHSIG is Linux's")


@pytest.fixture
def doorbell(tmp_path, monkeypatch):
    """doorbell imported with its folder in tmp_path (the import makes a pairing code there)."""
    shutil.copy(os.path.join(BRAIN, "config.example.json"), tmp_path)
    monkeypatch.setenv("PUKI_BRAIN_DIR", str(tmp_path))
    sys.modules.pop("doorbell", None)
    import doorbell
    yield doorbell
    sys.modules.pop("doorbell", None)


def dead(pid):
    """True once [pid] is gone or a zombie (killed, not yet reaped by whoever adopted it)."""
    try:
        with open(f"/proc/{pid}/stat") as f:
            return f.read().rsplit(")", 1)[1].split()[0] == "Z"
    except FileNotFoundError:
        return True


@linux_only
def test_child_outlives_the_thread_that_started_it(doorbell):
    started = []
    t = threading.Thread(target=lambda: started.append(doorbell.KILL_ON_CLOSE.popen(["sleep", "30"])))
    t.start()
    t.join()
    child = started[0]
    try:
        time.sleep(0.5)  # a SIGKILL sent when the thread ended would have landed by now
        assert child.poll() is None, f"killed with the thread that started it ({child.returncode})"
    finally:
        child.kill()
        child.wait()


@linux_only
def test_child_dies_with_the_doorbell(tmp_path):
    shutil.copy(os.path.join(BRAIN, "config.example.json"), tmp_path)
    script = (f"import sys, time; sys.path.insert(0, {BRAIN!r}); import doorbell; "
              "print(doorbell.KILL_ON_CLOSE.popen(['sleep', '30']).pid, flush=True); time.sleep(30)")
    parent = subprocess.Popen([sys.executable, "-c", script], stdout=subprocess.PIPE, text=True,
                              env=dict(os.environ, PUKI_BRAIN_DIR=str(tmp_path)))
    pid = int(parent.stdout.readline())
    assert not dead(pid)
    parent.kill()
    parent.wait()
    deadline = time.time() + 5
    while not dead(pid) and time.time() < deadline:
        time.sleep(0.1)
    assert dead(pid), "the child outlived the doorbell"


def test_a_failed_start_reaches_the_caller(doorbell):
    with pytest.raises(FileNotFoundError):
        doorbell.KILL_ON_CLOSE.popen([os.path.join(BRAIN, "no-such-llama-server")])
    child = doorbell.KILL_ON_CLOSE.popen([sys.executable, "-c", "pass"])  # and the next start still works
    assert child.wait() == 0
