# Porting Puki's brain

Puki's brain is a handful of small ONNX models and some plain logic around them, so it can move to
another platform (iOS, desktop, the web) without retraining anything. This page is for someone doing
that: what the models are, what goes in and out of each, and how to check a port is right without a
toddler at hand. The Android code is the reference; every class named here is in
`app/src/main/java/io/github/tomsadeh/puki/brain/`.

The rules in [CONTRIBUTING.md](CONTRIBUTING.md) hold on every platform: nothing about the child
leaves the device, the child's words aren't logged or stored, and the network is used only for the
model download a parent asks for.

## The models

All of them run on ONNX Runtime's CPU execution provider, which has official builds for iOS
(CocoaPods and Swift Package Manager), macOS, Linux, Windows and the web. The int8 models use
dynamic-quantization ops that CoreML mostly won't take, so stay on the CPU; a phone from the last few
years is far faster than the 3 GB tablet Puki is tuned on.

Download each file from Hugging Face (`https://huggingface.co/<repo>/resolve/main/<file>`) and check
it against the size and SHA-256 pinned in `DeviceModels.kt` before using it.

| Model | What it does | Hugging Face | Size |
|---|---|---|---|
| Brains (Hebrew) | Writes Puki's lines; answers the safety gate | `dtsj89/puki-brains-he` | 62 MB |
| Brains (English) | The same, in English | `dtsj89/puki-brains-en` | 62 MB |
| Beak | Hebrew text (no vowel marks) to IPA | `dtsj89/puki-beak-he` | 2.5 MB |
| English Beak | English text to IPA | `dtsj89/puki-beak-en` | 3.6 MB |
| Syrinx | The voice: IPA to audio (BlueTTS with the Noa voice) | `dtsj89/puki-syrinx`, plus `noa.json` from BlueTTS's GitHub | 99 MB |
| Ears | The child's speech to text (kol-stream, Hebrew and English) | `dtsj89/puki-ears` | 364 MB |

Each language needs its Brains and its reader; the voice and the ears are shared. With everything
loaded the Android process sits at about 1.2 GB, and only one Brains is in memory at a time
(`LocalBrain.brainsFor`).

### Brains (`Brains.kt`, `Bpe.kt`)

A 44M-parameter llama-style model, one per language. `brains.json` gives its shape (`layers`,
`kv_heads`, `head_dim`, `eot`, `vocab`, `context`): read it from there, don't hard-code it.

- **Tokenizer:** `tokenizer.json` is a Hugging Face byte-level BPE (GPT-2's scheme): split by GPT-2's
  pattern, map each piece's UTF-8 bytes to printable characters, merge by rank. `Bpe.kt` is about 80
  lines; port it, or use any byte-level BPE library that reads `tokenizer.json`.
- **Inputs:** `ids` (int64, `[1, n]`), `past_k` and `past_v` (float32, `[layers, kv_heads, past, head_dim]`;
  `past` is 0 on the first step).
- **Outputs:** `logits` (float32, the next token's), `next_k` and `next_v` (the cache for the next step).
- **The loop:** run the whole prompt once with an empty cache, then one token at a time, feeding the
  returned cache back in. Stop at `eot` or after 48 new tokens. If the prompt is longer than
  `context` minus the new tokens, keep its end.
- **Sampling:** temperature 0.5, top-p 0.9 (0.5 beat 0.7 in a blind A/B). Decode the tokens and trim.
- **The prompt**, exactly (the same as `train/fmt.py`):

  ```
  lang: he
  child: girl
  task: silly
  input: <the game's input>
  earlier: <only for reach_back>
  puki:
  ```

  `lang` is `he` or `en`, `child` is `girl` or `boy`. The tasks are `tap`, `moment`, `talk`, `gate`
  and the game tasks: `silly`, `silly_fix`, `look`, `recap`, `reach_back`, `care` and `story_next`.
  Each game builds its input with the Kotlin in `data/content/` (`SillyContent`, `LookContent`,
  `RecapContent`, `ReachBack`, `CareContent`, `StoryContent`), worded exactly as the model was
  trained.
- **The child's name:** the model writes `@name` where the name goes. Put the name in, or, with no
  name, remove it with the commas around it (`LocalBrain.withName`).
- **The gate:** before Puki answers anything the child said, run the `gate` task for one step. Take
  the softmax of the logits at the tokens for `" 0"`, `" 1"`, `" 2"` and `" 3"` (each is a single
  token in both tokenizers) and pass them to `Safety.gateCategory`. It fails closed: if the digits
  don't hold at least half the probability, the answer is "unclear" and Puki gives a neutral line.

### Beak, the Hebrew reader (`voice/Beak.kt`)

- **Input:** `ids` (int64, `[1, n + 1]`): first a girl or boy token, then one id per character of the
  cleaned text. The alphabet and the special ids are in `Beak.kt`'s companion object; the cleaning is
  `Beak.clean` (one kind of geresh, NFD, single spaces).
- **Outputs:** `consonant` and `vowel` (`[1, n, classes]`) and `stress` (`[1, n]`).
- **Reading them:** each letter takes the best consonant among those `vocab.json`'s `allowed` lists
  for it, and its best vowel. In each word, the vowel-bearing letter with the highest stress score
  gets `ˈ`. A final ח with the vowel a is written `aχ`. The rest is in `Beak.phonemize`, about 50
  lines.

### English Beak, the English reader (`voice/EnglishBeak.kt`)

- **The table first:** `words.tsv` (word, then IPA) covers every word Puki says and about 30,000
  more. Only words missing from it go to the model.
- **The model:** `ids` (int64, `[1, letters + 2]`; 1 marks both edges, a letter is its index in
  `symbols.json`'s `letters` plus 2) to `logp` (`[1, slots, symbols]`, three slots per letter). Take the
  best symbol in each slot, drop repeats and blanks (0): a standard CTC greedy decode.
- **Around it:** strip accents (NFD), spell numbers up to 20 as words, keep the punctuation the voice
  uses (`.,!?;:-`), and hand Hebrew letters (a name written in Hebrew) to Beak.

### Syrinx, the voice (`voice/BlueVoice.kt`, `voice/VoiceText.kt`)

A port of BlueTTS's `blue_onnx`, which is the easiest reference to read next to the Kotlin. Four
graphs: `duration_predictor_style`, `text_encoder`, `vector_estimator` and `vocoder`. `tts.json`,
`vocab.json`, `uncond.npz`, `stats.npz` and the voice style `noa.json` supply the rest.

1. Hebrew text goes through `VoiceText.prepareTextForSynthesis`, then Beak. English goes straight to
   English Beak.
2. Split the IPA into chunks (`VoiceText.chunkText`) and encode each chunk to ids
   (`VoiceText.encode`: NFKD, the replacements, a final period if there's none).
3. The duration predictor gives the length in seconds; divide it by the speed (0.92 by default).
4. The text encoder gives the text embedding.
5. Flow matching: start from Gaussian noise, `latent_dim * chunk_compress_factor` channels by as
   many frames as the duration needs. Run 5 steps of the vector estimator; the first 3 also run
   with `uncond.npz`'s "no text" and "no speaker" and combine as `uncond + 4 * (cond - uncond)`. The
   last 2 steps are unguided.
6. De-normalize with `stats.npz`, reshape for the vocoder, run it, and trim one frame from each end.
7. If the peak is over 0.95, scale everything down to 0.95. The output is mono float audio at
   `tts.json`'s sample rate.

The `.npz` files are zips of float32 `.npy` arrays (`BlueVoice.readNpz` reads them in 20 lines).

### Ears (`KolEars.kt`)

- **Audio in:** 16 kHz mono. If the input is quiet, raise it toward the level it was trained on
  (`KolEars.louder`: at most 10x, never lower). Then cut frames as Gemma's feature extractor does:
  160 zeros in front, 320 samples every 160.
- **`encoder.onnx`:** `frames` (`[1, frames, 320]`) to `[1, T, 512]`, a row every 80 ms.
- **`step.onnx`:** `e` (`[N, 512]`, rows of that output) and `ctx` (int64, `[1, 2]`, the last two
  pieces) to piece scores (`[N, 2048]`). Greedy transducer decoding: score 16 rows at once, jump to
  the first one that writes a piece (0 means nothing), at most 3 pieces per row (`KolEars.greedy`).
- **Pieces to text:** `tokens.txt` holds sentencepiece pieces. `▁` starts a word and `<0xNN>` is a
  raw byte.
- Both graphs come int8 and already optimized: load them with optimization off, which saves
  seconds and memory on a slow device.

## The logic around the models

These are plain code, with no platform APIs, and they're what makes Puki Puki:

- `Persona.kt`: the line filter every line passes (`clean`: one line of at most 90 characters and 14
  words, only the language's own letters, no banned words, compliments about the child dropped), the
  Hebrew words the voice misreads (`forVoice`) and the word match for Say it with Puki.
- `Safety.kt`: the word lists that answer careful things with a fixed kind line before the model sees
  them (`guard`), the gate (`gateCategory`) and the check on what Puki may say back (`replyOk`).
- `LocalBrain.kt`: how a request becomes a line. For each game task it shows the check its line must
  pass (Silly Puki says the truth or a silly mistake, a drawing names only what's in it, the recap
  names two of the day's things, and so on). A line that fails is written again: 3 tries, or 4 for
  game tasks, whose lines also may not ask a question. Lines with imitated sounds ("squawk") are
  written again too (`SOUND_WORDS`).
- `data/content/`: the inputs and checks for each game.

## Checking a port

The JVM unit tests pin the Kotlin to the Python trainer, so a port that passes the same fixtures is
right without anyone playing. The fixtures are JSON in `app/src/test/resources/`:

| Fixture | Test | What it pins |
|---|---|---|
| `brains_bpe_cases.json` with `brains_tokenizer.json` | `BpeTest` | Tokenizer encode and decode (on v1's tokenizer; v2 uses the same scheme) |
| `silly.json` | `SillyContentTest` | Silly Puki's inputs and the truth check |
| `look.json` | `LookContentTest` | A drawing's description and the present/absent check |
| `recap.json` | `RecapContentTest` | The bedtime recap's input and `eventsNamed` |
| `reach.json` | `ReachBackTest` | Reaching back to an earlier moment |
| `care_story.json` | `CareStoryContentTest` | Helping friends and the story |

`SafetyTest`, `PersonaTest`, `KolEarsTest`, `VoiceTextTest`, `DeviceChecksTest` and
`RegexPortabilityTest` cover the rest in code. Read the Kotlin and assert the same cases.

Some things to watch:
- **Regex:** `RegexPortabilityTest` exists because a Java-only flag (`(?U)`) once slipped in. Word
  characters are spelled out as `[\p{L}\p{N}_']`. Make sure your platform's regex reads `\p{L}` the
  same way.
- **Unicode:** Beak cleans with NFD, the voice encoder uses NFKD and English Beak strips accents after
  NFD. Normalize the same way or Hebrew and accented names will read differently.
- **The models:** run each one once on the PC with Python's ONNX Runtime and once in the port, on the
  same input, and compare the outputs. For Brains, compare the logits for a fixed prompt. For the
  voice, set the noise to the same values (`BlueVoice.noise`) and the audio should match closely.

## Questions

Open an issue on [GitHub](https://github.com/TomSadeh/puki/issues). If you start a port, say so there
so others can find it and help.
