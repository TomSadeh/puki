# Roadmap

Ideas for what פוקי can do next, building on the stack that's already running on the
home PC: **Gemma 4 E4B** (writes, and can also see images), **BlueTTS** (speaks Hebrew and
English) and **ivrit.ai Whisper** (hears Hebrew). Nothing here needs the cloud.

Order is a suggestion. Real play-testing with a toddler decides what comes first.

## 1. Puki sees her drawings 🎨

**Status: drawings done. Show-and-tell with the camera is still open.**

When the child finishes a drawing on the canvas, the parrot looks at it and reacts:
"וואו, שמש צהובה גדולה!", or for scribbles, "איזה כחול חזק! זה ים?"

- Gemma 4 E4B is multimodal. llama-server loads its vision projector with `--mmproj`
  (`mmproj-F16.gguf`, about 1 GB, from the same Unsloth repo).
- The app sends the canvas as a PNG to a new `/look` endpoint. The brain asks Gemma to
  describe it warmly in one short sentence, with the usual filter and BlueTTS.
- The prompt should praise effort and color, never judge ("that's not a cat").
- **Later, show-and-tell:** the child holds a toy up to the tablet camera and Puki names it.
  Same endpoint; the photo goes only to the PC.

**Why first:** cheapest delight per line of code. The canvas and the brain both exist already.

## 2. Bedtime story 📖

**Status: done, using what the child did this session. A story that remembers earlier days needs §4.**

At the end of the bedtime routine, Puki tells a 1–2 minute story starring the child, their
favorite animals, and something they did today ("היום האכלת את הפיל, ובלילה הפיל חלם…").

- Gemma writes the story in short sentences. BlueTTS reads it one sentence at a time, so
  the first sentence starts within a few seconds.
- For each sentence, Gemma picks one of the app's existing drawings (from a list of
  `ic_*` names), so the story is illustrated with no image generation.
- It needs a small "today" summary from memory (see §4); until then, it uses the games
  played this session.
- Calm prompt: slow pace, sleepy ending, the same closing line every night as a ritual.

## 3. Talking games 🗣️

**Status: questions and answers with hands-free listening are done in Animal Garden (every
other round). The rest is open.**

- **Say it with Puki:** a picture appears, Puki says the word, and the child repeats it.
  Reward attempts, not accuracy. Matching is lenient (first sound, syllable count, known
  toddler versions like "פה" for "פרה"). Grow from single words to two-word phrases.
- **Questions and answers:** "מה הפרה אומרת?" The child says "מו!" and the cow moos. This
  turns existing games into conversations.
- **English words:** "זה כלב. באנגלית: dog!" Invite the child to repeat it.
- **Hands-free listening:** holding a button is hard at two. After Puki asks a question,
  listen automatically for a few seconds and stop on silence (Whisper's VAD is already on).

## 4. Puki remembers 🧠

Puki currently forgets everything between replies. Memory makes it feel like a friend:
"אתמול האכלת את הפיל! רוצה לבקר אותו שוב?"

The design follows a few rules:
- **The log is the memory.** Every event, transcript and reply is appended verbatim to a
  local, append-only log on the PC. It's never edited and never garbage-collected, and
  everything above it is derived from it and can be re-derived.
- **Write-time work happens off the hot path.** When the brain is about to go to sleep
  after idling, it first *consolidates*: Gemma reads the session's log and writes a short
  **diary entry** (a leaf), then updates a few small **pages** (the child's profile, their
  favorite things, words they say, milestones). This costs nothing while the child is playing.
- **The hot path stays cheap.** Each reply's prompt gets the profile page, plus any diary
  entries that are relevant to the current moment.
- **Retrieval isn't the mouth's job.** Code and small scoring models decide what memory
  reaches the prompt. Gemma only receives it and writes the reply.

**The diary is written in English, while the log stays in Hebrew.**
- Small, fast, well-studied encoders exist mostly for English (MiniLM, DeBERTa-v3, Ettin,
  and the NLI/cross-encoder checkpoints built on them). That makes English entries easy to
  embed, retrieve and gate on CPU. Hebrew small encoders are scarce and not tuned for retrieval.
- Gemma writes the entries at consolidation time anyway, so translating costs nothing extra.
- **The child's own words stay verbatim Hebrew inside the English entry**
  (`said "פיל!" while feeding the elephant`). Translating toddler speech would destroy exactly
  what the parent wants to keep, and Whisper's transcript can already be shaky.
- Keep canonical Hebrew names for recurring things (animals, toys, people) in the pages, so
  the Hebrew reply doesn't back-translate oddly.
- The reply model reads English memory and answers in Hebrew. Gemma handles that cross-lingual
  step well.

**Start small.** A few sessions a day is a tiny corpus. For the first months, the profile page
plus the last few diary entries fit in the prompt, so retrieval isn't needed yet. Encoder
retrieval, a relevance gate deciding whether a memory is worth mentioning at all, and
measuring both against a hand-labeled set should come in when the diary outgrows the context,
not before.

**Parent page.** The brain serves a small local web page on the home network: the words the
child said and when, games played, drawings with Puki's comments, and the diary in plain
language. It's a private record of a child growing up, and it never leaves the house.
The brain's dashboard (`brain/dashboard.py`, on the PC only) is the start of it: it already
shows a live log of what the child said and what Puki answered.

## 5. More content, faster 🌱

New categories (vehicles, food, family, weather), each with words, prompts and voice lines:
- Gemma drafts the word list and prompts.
- BlueTTS voices them, with phonemes for anything ambiguous (see the note on nikud in the README).
- Illustrations are vector drawables in the existing style.
- `scripts/check_voices.py` confirms nothing is silent.

## 6. Making it easy for other families 🏡

Today, setting up Puki takes Android Studio and eight manual steps on the PC. The goal is
"install the app, run one program on the PC, type your child's name".

**First-run setup in the app (small). Status: done.**
- A short welcome on first launch: language, the child's name, girl/boy, and "set up Puki?".
  Everything stays editable in parent settings.
- The app sends the child's name and gender with each request, so the PC needs no
  `config.json` and one brain can serve siblings on different tablets.
- The first time the PC is reachable, the app asks the brain to record the lines with the
  child's name ("כל הכבוד, ___!") and saves them on the tablet. They then work offline too,
  with no generator script.

**The app finds the PC by itself (small to medium).**
- The brain announces itself on the home network (mDNS / DNS-SD as `_puki._tcp`), and the
  app lists what it finds. No IP addresses.
- Fallback: the PC shows a QR code with its address, and the parent scans it in settings.
  **Status:** the dashboard shows the QR code (`puki://pair?host=…&code=…`, address plus
  pairing code); scanning it in the app is still open.

**English as a first-class language (small to medium).**
- Speech recognition: load a standard English Whisper (for example `large-v3-turbo` or
  `distil-large-v3` in CTranslate2 format) when the app is in English. The ivrit.ai model
  stays for Hebrew.
- Game situations sent to the brain become bilingual (about 20 strings), instead of always
  Hebrew.
- Voice: compare BlueTTS's English with [Kokoro](https://huggingface.co/hexgrad/Kokoro-82M),
  a small and very natural English voice, and use whichever sounds better for English.

**One program for the PC instead of a setup guide (medium).**
- **A single brain binary:** doorbell, brain, a Python runtime, onnxruntime, CTranslate2
  and llama.cpp, bundled with Nuitka or PyInstaller into one Windows app with a tray icon.
  The icon shows whether Puki is asleep, awake or busy, and offers pause and settings.
- **Models download on first run** with a progress bar (Gemma, its vision part, Whisper,
  BlueTTS; about 8 GB), and resume if interrupted. Models aren't bundled, so the download
  stays small, each model keeps its own license, and models update independently.
- **GPU or CPU:** detect an NVIDIA GPU and fetch the CUDA build of llama.cpp and the cuBLAS/
  cuDNN libraries only when there is one. Everyone else gets the CPU build.
- **Firewall and autostart:** one admin prompt adds the home-network firewall rule, and an
  "open at login" option replaces the startup shortcut.
- **Trust:** unsigned executables trigger Windows SmartScreen warnings and the occasional
  antivirus false positive. A code-signing certificate fixes this; until then, publish
  checksums and the build script so people can build it themselves.
- **Later:** a macOS build (Apple Silicon makes a great brain: llama.cpp uses Metal, and
  Whisper and BlueTTS run on the CPU) and a Linux build or Docker image.
- **Testing:** test on a clean PC every time. Installers mostly fail on machines that
  aren't the developer's.

**Getting the app onto tablets (medium).**
- Signed APKs on GitHub Releases: enough for tech-savvy parents.
- Google Play: its "Designed for Families" rules for kids' apps, plus microphone and
  local-network permissions, mean real paperwork. Worth it once the rest is solid.

## Ongoing

- **Run the whole stack on a high-end tablet:** Gemma E2B/E4B through LiteRT-LM on device,
  BlueTTS through ONNX Runtime Mobile, and a smaller Hebrew speech model. This would make
  Puki work away from home.
- **Tuning the persona** from real logs: which lines delight, which fall flat.
