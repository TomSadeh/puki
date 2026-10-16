"""The installer downloads only pinned files, and refuses one that doesn't match its pin."""
import hashlib
import http.server
import os
import re
import sys
import threading

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import install  # noqa: E402

GOOD = b"exactly the pinned bytes\n"


@pytest.fixture(scope="module")
def server():
    """A local web server: /good serves GOOD, /tampered serves something else."""
    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            body = GOOD if self.path == "/good" else b"something else entirely\n"
            self.send_response(200)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *a):
            pass

    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    yield f"http://127.0.0.1:{srv.server_address[1]}"
    srv.shutdown()


SHA = hashlib.sha256(GOOD).hexdigest()


def test_a_matching_download_is_kept(server, tmp_path):
    dest = tmp_path / "file.bin"
    install.download(f"{server}/good", str(dest), len(GOOD), sha256=SHA)
    assert dest.read_bytes() == GOOD


def test_a_tampered_download_is_refused_and_deleted(server, tmp_path):
    dest = tmp_path / "file.bin"
    with pytest.raises(SystemExit):
        install.download(f"{server}/tampered", str(dest), sha256=SHA)
    assert not dest.exists() and not (tmp_path / "file.bin.part").exists()


def test_nothing_unpinned_is_downloaded(server, tmp_path):
    with pytest.raises(SystemExit):
        install.download(f"{server}/good", str(tmp_path / "file.bin"))


def test_everything_the_installer_picks_is_pinned():
    hf = install.pins()["huggingface"]
    for repo, path in [(install.GEMMA_REPO, install.GEMMA_FILE), (install.GEMMA_REPO, install.MMPROJ_FILE),
                       (install.GEMMA12_REPO, install.GEMMA12_FILE), (install.GEMMA12_REPO, install.MMPROJ_FILE)] + \
                      [(install.PARAKEET_REPO, p) for p in install.PARAKEET_FILES]:
        assert path in hf[repo]["files"], (repo, path)
    assert hf[install.WHISPER_REPO]["files"] and hf[install.BLUETTS_ONNX_REPO]["files"]
    # Every llama.cpp build install_llama() can choose
    llama = install.github_assets("ggml-org/llama.cpp", install.LLAMA_TAG)
    t = install.LLAMA_TAG
    for flavor in ("win-cuda-13.4-x64", "win-cuda-12.4-x64"):
        assert f"llama-{t}-bin-{flavor}.zip" in llama and f"cudart-llama-bin-{flavor}.zip" in llama
    assert f"llama-{t}-bin-win-cpu-x64.zip" in llama
    for flavor in ("ubuntu-cuda-13.4-x64", "ubuntu-cuda-12.8-x64"):
        assert f"llama-{t}-bin-{flavor}.tar.gz" in llama and f"cudart-llama-{t}-bin-{flavor}.tar.gz" in llama
    assert f"llama-{t}-bin-ubuntu-x64.tar.gz" in llama
    assert "uv-x86_64-pc-windows-msvc.zip" in install.github_assets("astral-sh/uv", install.UV_VERSION)
    assert install.bluetts_archive_url() in install.pins()["archives"]


def test_pins_are_sha256_and_models_come_from_a_fixed_commit():
    p = install.pins()
    shas = [sha for repo in p["huggingface"].values() for _, sha in repo["files"].values()]
    shas += [sha for rel in p["github"].values() for _, sha in rel.values()] + list(p["archives"].values())
    assert shas and all(re.fullmatch(r"[0-9a-f]{64}", s) for s in shas)
    url = install.hf_url(install.WHISPER_REPO, "config.json")
    assert "/resolve/main/" not in url and re.search(r"/resolve/[0-9a-f]{40}/", url)
