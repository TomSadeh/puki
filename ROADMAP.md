# Roadmap

Where Puki is, what comes next, and the ideas after that. Order is a suggestion: real
play-testing with a toddler decides what comes first. Nothing here needs the cloud.

Sizes: **S** an hour or so, **M** a few hours, **L** a day or more.

## Where Puki is today (0.6)

**The game**
- Ten islands with scene-based games: animals, colors, shapes, counting, letters, music,
  **Peekaboo barn** (find the hiding animal), **Echo Puki** (Puki repeats her in a silly parrot
  voice, no PC needed), "Say it with Puki", and "My Day" (bath, table, dressing, tidying, teeth,
  bedtime). Plus drawing and a sticker album.
- Every game grows with the child: levels move up after easy sessions and back down after
  hard ones. A quiet moment repeats the question, and after two misses the answer glows.
  Taps work where dragging is hard, and double taps can't break anything.
- A calm goodbye when the parent's time limit is up: evening falls, Puki yawns and waves. No
  countdown.
- Hebrew and English, girl/boy forms, no reading needed, all voices generated with BlueTTS.
- The game updates itself from GitHub releases (parent settings).

**Puki, the companion**
- Comments on what the child does, looks at her drawings, tells a bedtime story about her
  day, answers when she talks to him, and plays "Say it with Puki" (repeat a word, checked
  by speech recognition) and "what does the cow say?".
- His brain runs on a PC at home (Gemma 4, BlueTTS, ivrit.ai Whisper, Parakeet for English),
  set up by one program (Puki.exe on Windows, `install.py` on Linux) and paired by QR code.
- **Or on a high-end phone** (about 12 GB, tested on a Galaxy S25): the same brain on the
  device, in Hebrew, no PC needed.

**Safe by design**
- The tablet and the PC talk over HTTPS, pinned to the PC's own certificate (its fingerprint
  is in the pairing QR code). Releases are signed with Puki's own key. Every file the PC
  installer downloads is pinned to a SHA-256. The brain can't be left holding the GPU if
  the doorbell is closed abruptly.

## Next up

1. **Rewards and the parent's view** (0.7, M each)
   - A "new sticker!" moment at the end of a session, and more stickers, spaced further apart.
   - A parent progress page on the PC's dashboard: stars and level per game, words she said,
     games played, reset and level override.
   - **Talk about it later:** after a session, the parent page suggests 2–3 things to ask her.
   - A fridge for her drawings, with Puki's comments.
2. **Phone brain polish** (M each)
   - **English on the phone through Android's own speech engine.** Google's on-device
     voices speak English (and Hebrew) offline once downloaded. Puki would only ever use a
     voice marked offline, never one that sends text to Google, so nothing leaves the phone.
     BlueTTS stays Puki's Hebrew voice. (The alternative is Kokoro; BlueTTS's English needs
     espeak, which phones don't have.)
   - Faster start: Gemma takes about 18 s the first time.
   - Whisper on the phone's GPU (about 2 s a word now).
   - Check the phone layouts on the S25: the food now sits beside the animal and the hub's
     islands scale down on short screens (0.5.1), but neither has been seen on a phone yet.
3. **Games that feel better** (S–M each)
   - Counting by tapping each thing (one tap, one number).
   - Bedtime as a bedroom scene, like the other routines.
   - More variety in My Day: items drawn from a bigger pool each visit.
   - The hub at a steady 60 fps on the tablet (it's about 44; the waves were the big cost).
4. **Small new toys** (S–M each, from the research below)
   - **Big and small:** "who gets the big apple?", with two choices.
   - **Sort it:** socks to the drawer, balls to the box.

## Later

**Puki gets closer to her** (L)
- **Puki remembers:** a diary of past days, so he can say "yesterday you fed the elephant!"
  and the bedtime story can reach back. See the design notes below.
- **Family photos:** the parent adds a few photos with names, and Puki asks
  "מי זה? זאת סבתא!" and waits for her answer. Photos stay on the device and the PC.
- **Feelings:** Puki is sad because his balloon flew away, and she comforts him.
- **Show-and-tell:** she holds a toy up to the camera and Puki names it.

**More to play** (M each)
- **Baby care corner:** open pretend play: feed, rock, bathe and tuck in a baby animal.
- **Act out the word:** after Say it, the animals act the verb out ("קופץ!").
- **A daytime picture book** where Puki pauses for her to finish the line.
- **Say it, grown up:** two-word phrases, and English words ("זה כלב. באנגלית: dog!").
- **New word categories** (vehicles, food, family, weather): Gemma drafts the words and
  prompts, BlueTTS voices them, `scripts/check_voices.py` confirms nothing is silent.

**Easier for other families** (M–L)
- The app finds the PC by itself (mDNS, `_puki._tcp`), instead of an address or QR code.
- A signed Puki.exe (no SmartScreen warning), and a macOS brain.
- Google Play, once the rest is solid (its "Designed for Families" rules mean real paperwork).
- Compare BlueTTS's English with Kokoro on the PC too.

**Ongoing:** tune Puki's personality from real logs: which lines delight, which fall flat.

## Principles

From play-testing and a research pass on what helps at two:
- **Toy first, task second:** as few wrong moves as possible.
- **Talk less, wait more:** Puki asks, then leaves her a few seconds to answer.
- **Use the AI only where the reply depends on what she just did or said;** recorded lines
  everywhere else (they also hide the second of thinking).
- **Two choices, big targets, short bursts of play.**
- **Built-in endings;** no streaks, no autoplay.
- **Puki is a playmate, not a best friend:** he never guilts her for leaving, and points her
  back to her family.
- **The parent stays in the loop.**
- **Nothing leaves the home:** no accounts, ads, analytics or cloud.

## Design notes: Puki remembers

Puki forgets everything between replies today. The plan for memory:

- **The log is the memory.** Every event, transcript and reply is appended verbatim to a
  local, append-only log on the PC. It's never edited or pruned; everything else is derived
  from it and can be re-derived.
- **Work happens off the hot path.** When the brain is about to sleep after idling, Gemma
  reads the session's log and writes a short **diary entry**, then updates a few small
  **pages** (her profile, favorite things, words she says, milestones). This costs nothing
  while she plays.
- **Replies stay cheap.** Each reply's prompt gets the profile page plus any diary entries
  relevant to the moment. Code and small scoring models decide what memory reaches the
  prompt; Gemma only receives it.
- **The diary is in English, the log stays in Hebrew.** Small, fast encoders for retrieval
  exist mostly for English, and Gemma writes the entries anyway. Her own words stay
  verbatim in Hebrew inside the entry (`said "פיל!" while feeding the elephant`), and
  recurring names keep their Hebrew form, so the Hebrew reply doesn't back-translate oddly.
- **Start small.** For the first months, the profile page plus the last few diary entries fit
  in the prompt. Retrieval, a gate on whether a memory is worth mentioning, and measuring
  both against a hand-labeled set come in when the diary outgrows the context.
- **A parent page** on the home network: words she said and when, games played, drawings
  with Puki's comments, and the diary in plain language. A private record that never leaves
  the house. The PC dashboard (`brain/dashboard.py`) is its start: it already shows a live
  log of what she said and what Puki answered.

## Design notes: which model thinks

Puki's lines must be short, warm, grammatical Hebrew that gets the child's gender right. In a
bake-off on Puki's real prompts (October 2026), Gemma 4 (E4B and 12B) wrote fluent, sensible
Hebrew. Qwen 3.5 4B was the closest alternative but got about one line in four or five wrong
(an invented word, a wrong fact); Qwen 3.5 2B and PrismML's Bonsai 2 (1–2 bit, even at 27B)
invented words and got genders wrong. Puki stays on Gemma, on the PC and on the phone. Small
models pass Puki's line filter easily, so a bake-off has to read the lines, not just count them.
