# Contributing to Puki

Thanks for wanting to help! Puki is a game for two-to-four-year-olds and a companion that
lives in the family's home. Both shape what gets accepted. Please read this before opening a
pull request; small, focused PRs that follow it get merged quickly.

**Not sure an idea fits?** Open an issue first and describe it. That saves you building
something that can't be merged. Bug fixes and small polish can go straight to a PR.

## The hard rules

A PR that breaks any of these won't be merged, however good it is otherwise.

**For the child**
- **No failure states.** No "wrong", no red X, no sad sounds, no lost stars or lives. A wrong
  try gets a gentle, playful redirect ("let's try again", the right answer modeled).
- **Nothing to read.** The child can't read: every action is reachable through pictures,
  sound and motion. Text on screen is only for the grown-up.
- **Big targets.** Anything tappable or draggable is at least 100 dp (120 dp preferred).
- **Calm, not compulsive.** No timers that pressure, no streaks, no "come back tomorrow"
  hooks, no flashing for its own sake. The parent's time limit must keep working.
- **Free, forever.** No payments, in-app purchases, subscriptions, ads, "premium" content or
  anything else that stops play to ask for money, now or later. Puki exists because toddler
  games do that.
- **Both languages.** Every new line or prompt exists in Hebrew and English, and Hebrew lines
  that address the child have both a girl and a boy form (`heGirl`, `_he_f` / `_he_m`).

**For the family's privacy**
- **Nothing leaves the home.** No analytics, crash reporting, ads, accounts, cloud APIs or
  remote downloads at runtime. The only network traffic is the tablet talking to the
  family's own PC over the home network. (The installer downloads models once, from
  Hugging Face and GitHub, at the parent's request.)
- **No personal data in the repo.** No children's names, voices, photos or recordings in
  code, tests, screenshots, commit messages or fixtures. Personal files stay git-ignored
  (`brain/config.json`, `brain/pairing_code.txt`, `voice_*_name*`).
- **The child's words stay private.** Don't log or store transcripts or recordings to disk
  without an explicit, off-by-default parent setting. The dashboard stays local-only.
- **Keep the brain locked down.** Every doorbell endpoint requires the pairing code; the
  dashboard binds to 127.0.0.1 and keeps its host and header checks. Don't weaken these.

**For what Puki says**
- **The safety rails stay on.** Changes to prompts or the reply filter (`brain/brain.py`) must
  keep replies short, warm, age-appropriate, in the child's grammatical gender, with no
  compliments tacked on. Show sample outputs in the PR.

**Legal**
- Contributions are under the repo's [MIT license](LICENSE). Only add art, sound and music
  that you made or that has a license compatible with MIT (say which in the PR). No assets
  from other games or apps, and no AI-generated voices of real people.

## What a PR needs to show

| If you change... | Show that... |
|---|---|
| Anything in the app | `./gradlew assembleGithubDebug` builds, and you played the changed screen on a tablet or emulator (landscape). For visual changes, **before and after screenshots**, in Hebrew (RTL) if layout is involved. |
| Voice lines | They're added to `scripts/generate_voices.py` and generated with BlueTTS (Noa); `python scripts/check_voices.py` reports nothing missing. Plain text without nikud; ambiguous words go in `PHONEMES`. |
| The brain (`brain/`) | It runs, and you tried the endpoints you touched, in Hebrew and English (a few sample requests and replies in the PR). `doorbell.py`, `dashboard.py`, `qr.py` and `install.py` stay standard-library only. |
| The installer | A `--dry-run` on a fresh folder, and a real run of the steps you changed. Pin versions you download (not "latest"), check sizes, and keep it safe to run twice. Say which OS you tested (Windows, Linux). |
| Dependencies | Why it's needed and its license. Prefer none: no new Android libraries for things Compose can do (animations, drawing), no new Python packages in the doorbell. Pin exact versions, at least two weeks old. |

## Style

- Match the code around you: naming, comment density, idioms. [`CLAUDE.md`](CLAUDE.md) has the
  app's conventions (MVVM, `StateFlow` in view models, `BilingualText`, shared components in
  `ui/components/`).
- Reuse what's there before adding new: `GameBackground(scene = ...)`, `SquishIndication`,
  `idleBob`, `popIn`, `PromptBubble`, `ConfettiOverlay`, `RoundCompleteSheet`.
- Graphics are vector drawables or Compose `Canvas`, drawn in the existing friendly style. No
  bitmap-heavy art, no third-party animation libraries.
- One topic per PR, with a commit message that says what changed and why.

## Using AI tools

Welcome. Much of Puki was built with [Claude Code](https://claude.com/claude-code). The same
bar applies: you're responsible for every line, you've run it, and the PR describes what you
tested yourself. Unreviewed bulk changes will be closed.

## Review

Reviews may take a few days. Expect questions about how a change feels for a two-year-old;
if you can, describe how a child reacted to it.
