"""The doorbell's own certificate, so the tablet and the PC talk over HTTPS.

It's self-signed: no certificate authority knows a PC at home. The app trusts it because the
pairing QR code carries its fingerprint (and, when the address is typed in by hand instead, the
app remembers the certificate it first saw). Made once, on first start, next to this file.

Python can serve HTTPS out of the box but can't make a certificate, so that one step uses the
`cryptography` package: here if it's installed, else in the brain's own Python (it's in
requirements.txt).

    python tls.py <cert.pem> <key.pem>    # makes a new pair (what ensure() runs)
"""

import base64
import hashlib
import os
import ssl
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CERT_FILE = os.path.join(HERE, "puki_cert.pem")
KEY_FILE = os.path.join(HERE, "puki_key.pem")


def make(cert_path, key_path):
    """A new P-256 key and a self-signed certificate for it, valid for 20 years."""
    import datetime

    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.x509.oid import NameOID

    key = ec.generate_private_key(ec.SECP256R1())
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Puki brain")])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (x509.CertificateBuilder()
            .subject_name(name).issuer_name(name)
            .public_key(key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(now - datetime.timedelta(days=1))
            .not_valid_after(now + datetime.timedelta(days=365 * 20))
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .sign(key, hashes.SHA256()))
    key_pem = key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                serialization.NoEncryption())
    # The key is only for this user (on Windows the user's folders already are)
    fd = os.open(key_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "wb") as f:
        f.write(key_pem)
    with open(cert_path, "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.PEM))


def fingerprint(cert_path=None):
    """SHA-256 of the certificate (DER), as lowercase hex: what the app pins."""
    with open(cert_path or CERT_FILE, encoding="ascii") as f:
        der = ssl.PEM_cert_to_DER_cert(f.read())
    return hashlib.sha256(der).hexdigest()


def for_link(fp):
    """The fingerprint as the pairing link carries it: base64url, 43 characters (the hex is too
    long for the dashboard's small QR code)."""
    return base64.urlsafe_b64encode(bytes.fromhex(fp)).decode().rstrip("=")


def ensure(other_python=None):
    """Makes the certificate if there's none yet; returns its fingerprint."""
    if not (os.path.exists(CERT_FILE) and os.path.exists(KEY_FILE)):
        try:
            make(CERT_FILE, KEY_FILE)
        except ImportError:
            if not other_python:
                raise RuntimeError("making the HTTPS certificate needs the 'cryptography' package")
            subprocess.run([other_python, os.path.abspath(__file__), CERT_FILE, KEY_FILE], check=True,
                           capture_output=True, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    return fingerprint()


def server_context():
    """What the doorbell wraps each connection in: TLS 1.2 or newer, with our certificate."""
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.minimum_version = ssl.TLSVersion.TLSv1_2
    ctx.load_cert_chain(CERT_FILE, KEY_FILE)
    return ctx


if __name__ == "__main__":
    make(sys.argv[1], sys.argv[2])
