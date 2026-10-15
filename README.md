# Puki 🦜 · עולם קטן

A Hebrew/English Android game for toddlers (ages 2–4), with **פוקי**, a talking parrot
whose brain runs on your own PC. No cloud, no ads, no tracking, and nothing ever leaves
your home.

![The hub: islands on a sea, one per game](docs/screenshots/hub.png)

## What's inside

**The game (Olam Katan, "a small world")**, an island world with toddler-sized games:

- **Animal Garden:** feed each animal the right food. The animal munches and does a happy hop.
- **Colors, Shapes, Counting, Letters:** tap, drag and count with spoken Hebrew prompts.
- **Music:** play instruments, then repeat a little melody.
- **My Day:** a cutaway house with bath time, brushing teeth, getting dressed, setting the
  table, tidying up and bedtime.
- **Drawing**, with Puki looking at the picture, and a **sticker album** that fills up with stars.

Toddler-first design: no reading needed, big tap targets, no failure states (wrong answers
get a gentle shake and "let's try again"), and every line spoken in a warm voice. Hebrew
instructions follow the child's gender (תלחצי / תלחץ), set in parent settings. All voice lines
are generated offline with [BlueTTS](https://github.com/maxmelichov/BlueTTS).

**פוקי, the companion (optional).** A parrot sits in the corner of the games. When it
can reach a PC at home running the [brain server](brain/README.md):

- it comments on what the child just did, written live by a local **Gemma 4 E4B** and spoken
  by BlueTTS (about a second per reply);
- holding the parrot lets the child talk to it, and a Hebrew-tuned **Whisper** from
  [ivrit.ai](https://huggingface.co/ivrit-ai) transcribes the recording on the PC;
- it **looks at the child's drawings** and reacts to them, using Gemma's vision;
- at the end of the bedtime routine it tells a short, calm **bedtime story** about the
  child's day, one sentence at a time, each with a matching picture;
- in Animal Garden it asks "**מה החתול אומר?**" and listens hands-free; any answer makes the
  animal reply with its sound;
- if the PC is off or out of reach, the parrot just doesn't show up and the game uses its
  recorded voices.

| | |
|---|---|
| ![Animal Garden](docs/screenshots/animal-garden.png) | ![פוקי in a game](docs/screenshots/companion.png) |
| ![My Day](docs/screenshots/my-day.png) | ![Sticker album](docs/screenshots/stickers.png) |

## Why the brain is on a PC

Toddler tablets tend to be cheap and weak (this was built for a Galaxy Tab A7 Lite with 3 GB
of RAM). A language model, Hebrew speech recognition and Hebrew TTS together need far more,
so the tablet stays the screen and a PC on the home network does the thinking. On a high-end
tablet the whole stack could run on the device; contributions welcome.

## Getting started

**The app** (Android Studio, or the command line):

```
./gradlew installDebug
```

- Minimum Android 8 (API 26). Built for tablets in landscape.
- On first launch, a short welcome screen asks for the language, the child's name, girl/boy,
  and the companion's PC address (optional).
- Parent settings: long-press the gear on the hub. There you can change all of the above,
  plus sounds, music and a time limit.
- Music: "Puki main theme" plays in the hub and games, and "Puki nights" plays during the
  bedtime routine, dipping under the bedtime story. To use your own, replace
  `app/src/main/res/raw/music_hub.mp3` and `music_night.mp3`.

**The companion:** see [brain/README.md](brain/README.md). You'll need a PC with Python, and
ideally an NVIDIA GPU with about 6 GB of free VRAM (it falls back to the CPU otherwise).

**Personal voice lines:** the game can say the child's name ("כל הכבוד, ___!"). The first time
the companion is reachable, Puki records these lines onto the tablet, so they also play when
the PC is off. The name only ever lives on the tablet and your PC. To bake them into the app
instead (they're git-ignored, so the name never ends up in a repo):

```
python scripts/generate_voices.py --bluetts-dir ~/puki-brain/BlueTTS --child-name "<name>" --child-gender girl
```

## Project layout

```
app/                       Android app (Kotlin, Jetpack Compose)
brain/                     companion server for the PC (doorbell.py, brain.py)
scripts/generate_voices.py all voice lines, generated with BlueTTS
scripts/check_voices.py    checks every voice line the app plays has audio
scripts/draw_companion.py  draws the parrot
CLAUDE.md                  conventions for working on the code (also read by Claude Code)
```

## Notes for developers

- **BlueTTS and nikud:** BlueTTS's Hebrew pronunciation model garbles text that already has
  nikud, so all text is fed to it plain. Words plain spelling can't disambiguate (letter
  names, animal sounds) are given as phonemes in `generate_voices.py`.
- **Adding a voice line:** add it to `voice_lines` in `scripts/generate_voices.py`, generate it,
  then run `scripts/check_voices.py`.

## Credits

- Music ("Puki main theme", "Puki nights") by the project's author: piano, produced with Suno
- [BlueTTS](https://github.com/maxmelichov/BlueTTS) by Max Melichov: Hebrew/English text-to-speech
- [ivrit.ai](https://huggingface.co/ivrit-ai): Hebrew fine-tuned Whisper
- [Gemma](https://ai.google.dev/gemma) by Google, subject to the
  [Gemma terms of use](https://ai.google.dev/gemma/terms); GGUF builds by [Unsloth](https://huggingface.co/unsloth)
- [llama.cpp](https://github.com/ggml-org/llama.cpp) and
  [faster-whisper](https://github.com/SYSTRAN/faster-whisper)

Models are downloaded separately and are not part of this repository.

## What's next

Puki seeing the child's drawings, bedtime stories, talking games, and a memory that
stays in your home. See [ROADMAP.md](ROADMAP.md).

## License

[MIT](LICENSE). Built with lots of help from [Claude Code](https://claude.com/claude-code).
