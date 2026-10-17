"""The pairing code and link, shared by the doorbell and the installer: the link always carries
the certificate's fingerprint, so a tablet pins it before it sends the code or anything else."""
import os
import stat
import sys
import urllib.parse

import pytest

BRAIN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BRAIN)
import common  # noqa: E402


def test_code_is_made_once_and_kept(tmp_path):
    path = str(tmp_path / "pairing_code.txt")
    code = common.pairing_code(path)
    assert len(code) == 8 and all(c in common.CODE_ALPHABET for c in code)
    assert common.pairing_code(path) == code
    assert open(path).read().strip() == common.pretty_code(code)
    assert common.pairing_code(path, new=True) != code


@pytest.mark.skipif(os.name == "nt", reason="Windows keeps other users out through the user's folders")
def test_code_file_is_private(tmp_path):
    path = str(tmp_path / "pairing_code.txt")
    common.pairing_code(path)
    assert stat.S_IMODE(os.stat(path).st_mode) == 0o600


def test_typing_the_code_any_way_works():
    assert common.normalize_code("k7m2-qx9p ") == "K7M2QX9P"


def test_pair_url_carries_the_fingerprint():
    url = common.pair_url("192.168.1.5", 8790, "K7M2-QX9P", "abc_DEF-123")
    q = urllib.parse.parse_qs(urllib.parse.urlparse(url).query)
    assert q == {"host": ["192.168.1.5:8790"], "code": ["K7M2QX9P"], "fp": ["abc_DEF-123"]}


def test_installer_link_carries_the_fingerprint(tmp_path, monkeypatch):
    pytest.importorskip("cryptography")
    import install
    import tls
    monkeypatch.setattr(install, "lan_address", lambda: "192.168.1.5")
    (tmp_path / "brain").mkdir()
    code, url = install.pairing(str(tmp_path))
    fp = tls.fingerprint(str(tmp_path / "brain" / "puki_cert.pem"))
    assert url == common.pair_url("192.168.1.5", install.PORT, code, tls.for_link(fp))


def test_no_link_without_a_network(tmp_path, monkeypatch):
    import install
    monkeypatch.setattr(install, "lan_address", lambda: None)
    (tmp_path / "brain").mkdir()
    assert install.pairing(str(tmp_path))[1] is None
