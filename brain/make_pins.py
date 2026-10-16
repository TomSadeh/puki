#!/usr/bin/env python3
"""Maintainer tool: writes pins.json, the SHA-256 of everything install.py downloads.

    python brain/make_pins.py

install.py downloads only what's pinned here, and refuses a file that doesn't match, so a
file changed later on Hugging Face or GitHub (by accident or not) never reaches a PC. Run this
after changing LLAMA_TAG, UV_VERSION, BLUETTS_COMMIT or a model in install.py, check the diff,
and commit pins.json with it.

  - Hugging Face: each repo is pinned to its current commit; big files' SHA-256 is their Git
    LFS id, small ones are downloaded and hashed.
  - GitHub releases (llama.cpp, uv): the SHA-256 GitHub lists for each asset.
  - The BlueTTS source archive: downloaded and hashed.
"""

import hashlib
import json
import os
import re
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import install as I  # noqa: E402  (its constants: repos, files, versions)

UA = {"User-Agent": "puki-pins"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        return r.read()


def hf_repo(repo, wanted):
    """{"revision", "files": {path: [size, sha256]}} for the files [wanted] picks."""
    revision = json.loads(get(f"https://huggingface.co/api/models/{repo}"))["sha"]
    tree = json.loads(get(f"https://huggingface.co/api/models/{repo}/tree/{revision}"))
    files = {}
    for x in tree:
        if x.get("type") != "file" or not wanted(x["path"], x.get("size") or 0):
            continue
        sha = (x.get("lfs") or {}).get("oid")
        if not sha:  # a small file kept in git itself: hash it
            sha = hashlib.sha256(get(f"https://huggingface.co/{repo}/resolve/{revision}/{x['path']}")).hexdigest()
        files[x["path"]] = [x.get("size"), sha]
    print(f"  {repo} @ {revision[:10]}: {len(files)} files")
    return {"revision": revision, "files": files}


def github_release(repo, tag, wanted):
    assets = json.loads(get(f"https://api.github.com/repos/{repo}/releases/tags/{tag}"))["assets"]
    out = {}
    for a in assets:
        if wanted(a["name"]):
            digest = a.get("digest") or ""
            if not digest.startswith("sha256:"):
                sys.exit(f"{repo} {tag}: GitHub lists no SHA-256 for {a['name']}")
            out[a["name"]] = [a["size"], digest[7:]]
    print(f"  {repo} {tag}: {len(out)} assets")
    return out


def main():
    print("Pinning what install.py downloads:")
    pins = {"huggingface": {}, "github": {}, "archives": {}}
    hf = pins["huggingface"]
    hf[I.GEMMA_REPO] = hf_repo(I.GEMMA_REPO, lambda p, s: p in (I.GEMMA_FILE, I.MMPROJ_FILE))
    hf[I.GEMMA12_REPO] = hf_repo(I.GEMMA12_REPO, lambda p, s: p in (I.GEMMA12_FILE, I.MMPROJ_FILE))
    hf[I.WHISPER_REPO] = hf_repo(I.WHISPER_REPO, lambda p, s: "/" not in p and not p.startswith("."))
    hf[I.PARAKEET_REPO] = hf_repo(I.PARAKEET_REPO, lambda p, s: p in I.PARAKEET_FILES)
    hf[I.BLUETTS_ONNX_REPO] = hf_repo(I.BLUETTS_ONNX_REPO,
                                      lambda p, s: "/" not in p and not p.startswith(".") and p != "README.md" and s > 0)

    # Every build install_llama() may pick (Windows and Linux, CPU and CUDA), and uv for Windows
    tag = re.escape(I.LLAMA_TAG)
    llama = re.compile(rf"^(cudart-)?llama-({tag}-)?bin-(win-(cpu|cuda-[\d.]+)-x64\.zip|ubuntu-(x64|cuda-[\d.]+-x64)\.tar\.gz)$")
    pins["github"][f"ggml-org/llama.cpp@{I.LLAMA_TAG}"] = github_release("ggml-org/llama.cpp", I.LLAMA_TAG, llama.match)
    pins["github"][f"astral-sh/uv@{I.UV_VERSION}"] = github_release(
        "astral-sh/uv", I.UV_VERSION, lambda n: n == "uv-x86_64-pc-windows-msvc.zip")

    url = I.bluetts_archive_url()
    pins["archives"][url] = hashlib.sha256(get(url)).hexdigest()
    print(f"  BlueTTS {I.BLUETTS_COMMIT[:7]} archive")

    path = os.path.join(HERE, "pins.json")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(pins, f, indent=1, sort_keys=True)
        f.write("\n")
    print(f"Wrote {path}")


if __name__ == "__main__":
    main()
