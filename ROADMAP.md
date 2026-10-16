# Roadmap

Where Puki is, what comes next, and the ideas after that. Order is a suggestion: real
play-testing with a toddler decides what comes first. Nothing here needs the cloud.

Sizes: **S** an hour or so, **M** a few hours, **L** a day or more.

## Where Puki is today

**The game**
- Islands with scene-based games: animals, colors, shapes, counting, letters, music, and
  "My Day" (bath, table, dressing, tidying, teeth, bedtime), plus drawing and a sticker album.
- Every game grows with the child: levels move up after easy sessions and back down after
  hard ones. A quiet moment repeats the question, and after two misses the answer glows.
  Taps work where dragging is hard, and double taps can't break anything.
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

## Next up

1. **Security, performance and code hygiene pass** (done in 0.5): HTTPS between the tablet
   and the PC, pinned to the PC's own certificate; releases signed with Puki's own key; every
   installer download pinned to a SHA-256; smoother drawing on the tablet; tests and CI.
   Left: the hub at a steady 60 fps on the tablet (it's about 45).
2. **Phone brain polish** (M each)
   - English on the phone: BlueTTS needs espeak there, or use Kokoro.
   - Faster start: Gemma takes about 18 s the first time.
   - Whisper on the phone's GPU (about 2 s a word now).
   - The hub's islands crowd together on a phone's wide screen.
3. **Rewards and the parent's view** (M each)
   - A "new sticker!" moment at the end of a session, and more stickers, spaced further apart.
   - A parent progress page: stars and level per game, words she said, reset and level override.
   - A fridge for her drawings, with Puki's comments.
4. **Games that feel better** (S–M each)
   - Counting by tapping each thing (one tap, one number).
   - Bedtime as a bedroom scene, like the other routines.
   - More variety in My Day: items drawn from a bigger pool each visit.
5. **Small new toys** (S–M each, from the research below)
   - **Peekaboo barn:** animals pop out from behind doors and bushes. Nothing to get wrong.
   - **Big and small:** "who gets the big apple?", with two choices.
   - **Sort it:** socks to the drawer, balls to the box.
   - **A calm goodbye** when the time limit ends: Puki yawns, the lights dim, he waves.
     No countdown (warnings make the end harder, not easier).
   - **Talk about it later:** after a session, the parent page suggests 2–3 things to ask her.
   - **Echo parrot:** she says anything, and Puki says it back in a silly parrot voice.

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
- A proper release key for the APKs (they're signed with the developer's local key now), a
  signed Puki.exe (no SmartScreen warning), and a macOS brain.
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
