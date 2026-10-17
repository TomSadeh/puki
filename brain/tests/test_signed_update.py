"""PC updates are signed, like the tablet's: install.py --update takes a release only if Puki's
update key signed it (for Puki's updates, not anything else), and only the version it says inside."""
import os
import subprocess
import sys
import zipfile

import pytest

BRAIN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BRAIN)
import install  # noqa: E402

needs_ssh_keygen = pytest.mark.skipif(install.ssh_keygen() is None, reason="needs ssh-keygen (OpenSSH)")


def make_key(folder, name):
    key = os.path.join(folder, name)
    subprocess.run([install.ssh_keygen(), "-q", "-t", "ed25519", "-N", "", "-C", name, "-f", key], check=True)
    signers = os.path.join(folder, name + ".signers")
    with open(key + ".pub", encoding="utf-8") as f:
        kind, blob = f.read().split()[:2]
    with open(signers, "w", encoding="utf-8") as f:
        f.write(f'puki namespaces="puki-update" {kind} {blob}\n')
    return key, signers


def sign(key, path, namespace="puki-update"):
    if os.path.exists(path + ".sig"):
        os.remove(path + ".sig")
    subprocess.run([install.ssh_keygen(), "-Y", "sign", "-q", "-f", key, "-n", namespace, path], check=True,
                   capture_output=True)
    return path + ".sig"


@pytest.fixture
def release(tmp_path):
    z = tmp_path / "puki-brain-1.2.3.zip"
    z.write_bytes(b"the brain of Puki 1.2.3")
    return str(z)


@needs_ssh_keygen
def test_signed_by_pukis_key_passes(tmp_path, release):
    key, signers = make_key(str(tmp_path), "puki")
    assert install.signature_ok(release, sign(key, release), signers)


@needs_ssh_keygen
def test_a_changed_file_fails(tmp_path, release):
    key, signers = make_key(str(tmp_path), "puki")
    sig = sign(key, release)
    with open(release, "ab") as f:
        f.write(b" and something else")
    assert not install.signature_ok(release, sig, signers)


@needs_ssh_keygen
def test_another_key_fails(tmp_path, release):
    _, signers = make_key(str(tmp_path), "puki")
    other, _ = make_key(str(tmp_path), "someone-else")
    assert not install.signature_ok(release, sign(other, release), signers)


@needs_ssh_keygen
def test_a_signature_made_for_something_else_fails(tmp_path, release):
    key, signers = make_key(str(tmp_path), "puki")
    assert not install.signature_ok(release, sign(key, release, namespace="git"), signers)


@needs_ssh_keygen
def test_no_signature_fails(tmp_path, release):
    _, signers = make_key(str(tmp_path), "puki")
    sig = release + ".sig"
    open(sig, "w").close()
    assert not install.signature_ok(release, sig, signers)


def test_the_bundled_key_is_pukis_update_key():
    with open(install.SIGNERS_FILE, encoding="utf-8") as f:
        lines = [l for l in f.read().splitlines() if l.strip() and not l.startswith("#")]
    assert len(lines) == 1 and lines[0].startswith('puki namespaces="puki-update" ssh-ed25519 ')


def make_zip(path, files):
    with zipfile.ZipFile(path, "w") as z:
        for name, data in files.items():
            z.writestr(name, data)


def test_unpacks_the_version_it_says_inside(tmp_path):
    z = str(tmp_path / "r.zip")
    make_zip(z, {"puki-1.2.3/brain/release.txt": "1.2.3\n", "puki-1.2.3/brain/install.py": "# new"})
    dest = str(tmp_path / "update")
    install.unpack_release(z, dest, "1.2.3")
    assert open(os.path.join(dest, "brain", "install.py")).read() == "# new"


def test_an_old_release_passing_as_new_is_refused(tmp_path):
    z = str(tmp_path / "r.zip")
    make_zip(z, {"puki-1.2.3/brain/release.txt": "1.0.0\n"})
    with pytest.raises(SystemExit):
        install.unpack_release(z, str(tmp_path / "update"), "1.2.3")


def test_a_file_outside_the_folder_is_refused(tmp_path):
    z = str(tmp_path / "r.zip")
    make_zip(z, {"puki-1.2.3/brain/release.txt": "1.2.3\n", "puki-1.2.3/../../evil.txt": "x"})
    with pytest.raises(SystemExit):
        install.unpack_release(z, str(tmp_path / "update"), "1.2.3")
    assert not (tmp_path.parent / "evil.txt").exists()


def test_versions_compare_as_numbers():
    assert install.version_tuple("0.10.0") > install.version_tuple("0.9.9")
    assert install.version_tuple("v1.2.3") == (1, 2, 3)
