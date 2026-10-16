## What and why

<!-- What does this change, and why? Link an issue if there is one. -->

## How I tested it

<!-- Device or emulator, OS for brain/installer changes, what you tried. For visual changes,
before/after screenshots (Hebrew if layout is involved). For brain changes, a few sample
requests and replies in Hebrew and English. -->

## Checklist (see [CONTRIBUTING.md](../CONTRIBUTING.md))

- [ ] No failure states, nothing the child needs to read, tap targets ≥ 100 dp
- [ ] New text and voice lines in Hebrew and English, Hebrew in girl and boy forms
- [ ] Nothing leaves the home: no analytics, cloud calls or new runtime downloads
- [ ] No personal data (names, voices, photos) in code, screenshots or commits
- [ ] Builds (`./gradlew assembleGithubDebug`); `scripts/check_voices.py` passes if voice lines changed
- [ ] New dependencies or assets: explained, license compatible with MIT, versions pinned
