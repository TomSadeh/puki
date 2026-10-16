"""The doorbell's certificate: made once, and its fingerprint is what a TLS client really sees."""
import base64
import hashlib
import os
import socket
import ssl
import sys
import threading

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import tls  # noqa: E402

pytest.importorskip("cryptography")


@pytest.fixture
def cert(tmp_path, monkeypatch):
    monkeypatch.setattr(tls, "CERT_FILE", str(tmp_path / "cert.pem"))
    monkeypatch.setattr(tls, "KEY_FILE", str(tmp_path / "key.pem"))
    return tls.ensure()


def test_made_once(cert):
    before = open(tls.CERT_FILE).read()
    assert tls.ensure() == cert and open(tls.CERT_FILE).read() == before


def test_link_form_is_the_same_hash(cert):
    link = tls.for_link(cert)
    assert len(link) == 43
    assert base64.urlsafe_b64decode(link + "=").hex() == cert


def test_a_client_sees_that_certificate(cert):
    ctx = tls.server_context()
    listener = socket.create_server(("127.0.0.1", 0))
    port = listener.getsockname()[1]

    def serve():
        conn, _ = listener.accept()
        with ctx.wrap_socket(conn, server_side=True) as s:
            s.recv(1)

    threading.Thread(target=serve, daemon=True).start()
    client = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    client.check_hostname = False
    client.verify_mode = ssl.CERT_NONE  # the app pins the fingerprint instead
    with client.wrap_socket(socket.create_connection(("127.0.0.1", port))) as s:
        der = s.getpeercert(binary_form=True)
        assert s.version() in ("TLSv1.2", "TLSv1.3")
        s.send(b"x")
    assert hashlib.sha256(der).hexdigest() == cert
    listener.close()


def test_old_tls_is_refused(cert):
    assert tls.server_context().minimum_version >= ssl.TLSVersion.TLSv1_2
