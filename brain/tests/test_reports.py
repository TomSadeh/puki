"""מלשין לרנינג: reports are kept, one JSON line each, and trimmed to size."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import reports  # noqa: E402


def test_reports_are_kept_and_read_back(tmp_path, monkeypatch):
    monkeypatch.setattr(reports, "REPORTS_FILE", str(tmp_path / "reports.jsonl"))
    assert reports.load() == []
    reports.add("tablet", "יש! אש חמה!", heard="בוא נדליק אש", lang="he", game="animals")
    reports.add("dashboard", "x" * 1000, lang="fr")
    kept = reports.load()
    assert [r["source"] for r in kept] == ["tablet", "dashboard"]
    assert kept[0]["said"] == "יש! אש חמה!" and kept[0]["heard"] == "בוא נדליק אש" and kept[0]["game"] == "animals"
    assert len(kept[1]["said"]) == 400       # trimmed
    assert kept[1]["lang"] == "he"           # only he or en
