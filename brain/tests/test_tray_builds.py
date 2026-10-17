"""Puki.exe won't unpack an older build over a newer one (its files, update key and pins)."""
import os
import sys

BRAIN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BRAIN)
import tray  # noqa: E402


def stamp(folder, text):
    folder.mkdir(exist_ok=True)
    (folder / "build.txt").write_text(text + "\n", encoding="utf-8")
    return str(folder)


def test_build_time_from_the_stamp():
    assert tray.build_time("a1b2c3d 2026-10-17 09:30") == "2026-10-17 09:30"
    assert tray.build_time("dev") == ""


def test_an_older_exe_is_refused(tmp_path, monkeypatch):
    monkeypatch.setattr(tray, "BUNDLE", stamp(tmp_path / "exe", "aaa 2026-10-01 10:00"))
    assert tray.is_older_build(stamp(tmp_path / "brain", "bbb 2026-10-17 09:30"))


def test_a_newer_or_same_exe_is_fine(tmp_path, monkeypatch):
    brain = stamp(tmp_path / "brain", "bbb 2026-10-17 09:30")
    monkeypatch.setattr(tray, "BUNDLE", stamp(tmp_path / "exe", "ccc 2026-10-20 08:00"))
    assert not tray.is_older_build(brain)
    monkeypatch.setattr(tray, "BUNDLE", stamp(tmp_path / "exe2", "bbb 2026-10-17 09:30"))
    assert not tray.is_older_build(brain)


def test_a_first_install_is_fine(tmp_path, monkeypatch):
    monkeypatch.setattr(tray, "BUNDLE", stamp(tmp_path / "exe", "aaa 2026-10-01 10:00"))
    (tmp_path / "brain").mkdir()
    assert not tray.is_older_build(str(tmp_path / "brain"))
