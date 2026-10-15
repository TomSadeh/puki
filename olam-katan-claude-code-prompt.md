# Olam Katan (עולם קטן) — "Little World"

## Claude Code Prompt

Build an Android tablet game for toddlers (ages 2–4), bilingual in Hebrew and English. The app is called **"Olam Katan" (עולם קטן)** — "Little World."

---

## Tech Stack

- **Kotlin + Jetpack Compose** (native Android)
- **Target:** Android tablets (min SDK 26, target SDK 34)
- **Audio:** Android built-in TTS engine (supports both Hebrew and English — no pre-recorded audio files needed)
- **Graphics:** Vector/SVG illustrations or Compose Canvas drawings. Bright, chunky, cartoon style. No photorealism.
- **Architecture:** Single-activity, Compose Navigation. MVVM with ViewModels per mini-game.
- **Storage:** Local only (DataStore or Room for progress/stars/stickers). No backend, no internet required.
- **Orientation:** Landscape locked.

---

## Core Design Principles

1. **Huge tap targets** — minimum 100dp, ideally 120dp+. Fat-finger-proof.
2. **Instant feedback on every tap** — sound + animation. No "dead" areas. If she taps something, it reacts.
3. **No fail state** — wrong drags bounce back gently with a playful sound (boing, wobble). Never a buzzer, never a red X.
4. **Positive reinforcement** — stars, sparkles, confetti, cheerful sounds on success.
5. **Short rounds** — each mini-game round is ~30–60 seconds, then a celebration screen and a gentle prompt to continue or switch.
6. **Toddler-proof navigation** — no small buttons, no text-based menus. The hub world IS the menu. Parent settings require a long-press (2 seconds) on the gear icon.
7. **No ads, no in-app purchases, no internet.** Fully offline.

---

## Bilingual System

- **Language toggle** in parent settings (long-press gear icon in top corner).
- **Hebrew mode:** All labels in Hebrew (RTL layout), TTS speaks Hebrew.
- **English mode:** All labels in English (LTR layout), TTS speaks English.
- Clean switch — no app restart needed. Language is a global state observed by all screens.
- Use Android's `TextToSpeech` API with locale `Locale("he", "IL")` for Hebrew and `Locale.US` for English.

---

## Progression System

### Stars
- Each mini-game round awards 1–3 stars based on completion.
- Stars are the global currency that unlocks new content.
- Star count is always visible on the hub world (top of screen, large and shiny).

### Sticker Album
- Completing activities earns collectible stickers (a sparkly dog, a rainbow, a toothbrush, etc.).
- A sticker album screen (accessible from hub world) displays the collection in a grid.
- Tapping a sticker plays its sound/animation. The album itself is a toy.

### Unlocking
- Hub world starts with 4 islands unlocked: Animal Garden, Color Splash, Shape Playground, Counting Garden.
- At 20 stars: unlock Music Meadow.
- At 40 stars: unlock Word Garden.
- At 10 stars: unlock "My Day" house.
- Locked islands appear dimmed with a star requirement badge. Tapping them shows a bouncy lock animation and the star count needed.

### Difficulty Scaling (automatic, per-island)
Each island tracks a local difficulty level (stored in DataStore). Difficulty increases after X successful rounds, never decreases. Details per island below.

---

## Hub World

A colorful landscape scene in landscape orientation. Rolling green hills, blue sky, fluffy clouds. The 7 islands/buildings are spread across the scene as large tappable areas:

| Position | Island | Icon |
|----------|--------|------|
| Left hill | Animal Garden | Barn with animals peeking out |
| Center-left | Color Splash | Rainbow paint palette |
| Center | Shape Playground | Colorful geometric blocks |
| Center-right | Counting Garden | Garden with numbered flowers |
| Right hill | Music Meadow | Musical notes floating (locked initially) |
| Far right | Word Garden | Open book with letters (locked initially) |
| Bottom center | My Day House | Cute little house (locked initially) |
| Top-left corner | Sticker Album | Sparkly book icon |
| Top-right corner | ⚙️ Settings | Gear icon (long-press 2s to open) |
| Top-center | Star Counter | Current star count with star icon |
| Bottom-right | Free Canvas | Easel / art board |

Clouds drift slowly. Sun blinks occasionally. Background has subtle ambient nature sounds (birds, breeze). Everything is alive and inviting.

---

## Island 1: Animal Garden 🐄

### Core Mechanic
- Tap an animal → hear its name in current language + its sound (moo, woof, etc.)
- Drag food to the correct animal (banana → monkey, bone → dog, carrot → rabbit)

### Difficulty Levels
1. **Level 1:** 3 farm animals (cow, dog, chicken). Tap to hear name + sound. Drag 1 food item to the obvious animal.
2. **Level 2:** 6 animals (add cat, horse, pig). Drag food with 3 choices visible.
3. **Level 3:** Audio quiz — "Which animal says מו/moo?" — tap the correct one out of 3. Still no penalty for wrong tap (wrong animal shakes head, right one highlights).
4. **Level 4:** Zoo animals (lion, elephant, monkey, giraffe, penguin, snake). Same mechanics.
5. **Level 5:** Ocean creatures (fish, whale, dolphin, octopus, turtle, crab).
6. **Level 6:** Dinosaurs (T-rex, triceratops, brontosaurus, stegosaurus, pterodactyl). Fun roar sounds.

### Content Per Animal
- SVG/vector illustration (friendly, not scary)
- Name in Hebrew + English
- Animal sound (short audio clip or TTS onomatopoeia)
- Associated food item with illustration

---

## Island 2: Color Splash 🎨

### Core Mechanic
- Tap paint buckets to color objects on screen.
- Drag colored balls into matching-color baskets.

### Difficulty Levels
1. **Level 1:** 3 primary colors (red, blue, yellow). Tap bucket → car/house/flower changes to that color. Confetti on completion.
2. **Level 2:** 6 colors (add green, orange, purple). Drag balls into matching baskets.
3. **Level 3:** Color mixing! Drag two paint buckets onto a canvas: yellow + blue → green, red + yellow → orange, red + blue → purple. Magical swirl animation.
4. **Level 4:** "Color the picture" — a coloring-book-style outline (butterfly, fish, house) with numbered/shaped zones. Tap a zone, then tap a color.
5. **Level 5:** "Find all the [red] things!" — a scene with many objects, tap the ones matching the requested color.

---

## Island 3: Shape Playground 🔷

### Core Mechanic
- Tap shapes to hear names and see them bounce/spin.
- Drag shapes into matching holes (shape sorter).

### Difficulty Levels
1. **Level 1:** 3 shapes (circle, square, triangle). Classic shape sorter — drag into matching hole.
2. **Level 2:** 6 shapes (add star, heart, diamond). Holes are slightly rotated, shape must be tapped to rotate before fitting.
3. **Level 3:** "Build a house" — drag shapes into a template (square body + triangle roof + rectangle door + circle window). Simple tangram.
4. **Level 4:** More complex tangrams (car, boat, tree, cat silhouette from shapes).
5. **Level 5:** "Which shape has 3 sides?" — audio quiz with tap response.

---

## Island 4: Counting Garden 🌻

### Core Mechanic
- Tap to plant flowers and hear the count.
- Drag items into containers while voice counts along.

### Difficulty Levels
1. **Level 1:** Count 1–3. Tap soil → flower grows. TTS counts "one… two… three!" (or "אחת… שתיים… שלוש!").
2. **Level 2:** Count 1–5. "Give the bunny 3 carrots" — drag carrots, TTS counts along.
3. **Level 3:** Count 1–10.
4. **Level 4:** "Which group has more?" — two groups of items, tap the bigger group.
5. **Level 5:** Simple addition visualization — "2 apples and 1 more apple makes…?" with visual merge animation.

---

## Island 5: Music Meadow 🎵 (unlocks at 20 stars)

### Core Mechanic
- Tap instruments to hear them play.
- Simon-says pattern matching.

### Difficulty Levels
1. **Level 1:** 4 instruments (drum, xylophone, tambourine, maracas). Tap to play. Free exploration.
2. **Level 2:** Repeat a 2-note pattern. Instruments light up in sequence, child repeats.
3. **Level 3:** 3-note patterns.
4. **Level 4:** 4-note patterns. New instruments (piano, guitar, flute, trumpet).
5. **Level 5:** Free compose — drag notes onto a simple 4-beat staff, tap play to hear the melody.

### Audio
- Use short audio samples for instruments (bundle as .ogg in assets). TTS for instrument names.

---

## Island 6: Word Garden 🔤 (unlocks at 40 stars)

### Core Mechanic
- Match letters to pictures.
- Drag letters to spell words.

### Difficulty Levels
1. **Level 1:** Letter recognition — show א with אריה (lion) picture / A with Apple picture. Tap letter to hear it. 6 letters at a time.
2. **Level 2:** "Find the letter א" — 3 letters on screen, tap the right one.
3. **Level 3:** Spell 2-letter Hebrew words / 3-letter English words with heavy visual hints. Letter slots show the letter faintly, child drags the matching letter on top.
4. **Level 4:** Spell without visual hints, but with audio hint (TTS says each letter).
5. **Level 5:** First words — match written word to picture (4 choices).

### Hebrew Letters (introduce gradually)
א, ב, ג, ד, ה, ו, ז, ח, ט, י, כ, ל, מ, נ, ס, ע, פ, צ, ק, ר, ש, ת

### English Letters (introduce gradually)
A–Z, uppercase only at first.

---

## Island 7: My Day (היום שלי) 🏠 (unlocks at 10 stars)

A cute house with rooms. Tap a room to enter its mini-game. Each room teaches a daily routine.

### Room Layout
The house is shown in cross-section (like a dollhouse). Tappable rooms:

| Room | Mini-Game |
|------|-----------|
| Bathroom (left) | Bath Time + Brushy Brushy |
| Bedroom (right) | Tidy Up + Getting Dressed |
| Kitchen (bottom) | Table Time |
| Whole house | Bedtime Routine (unlocks at 60 stars) |

### 7A: Bath Time 🛁
- A cartoon kid stands looking muddy.
- Drag soap onto body parts (arms, legs, tummy, face) → bubbles appear, mud disappears.
- Tap showerhead → water rinses bubbles away.
- **Progression:** Add shampoo (drag to hair), choosing warm/cold water knob (visual feedback — steam vs shivers), drying with towel drag.

### 7B: Brushy Brushy 🪥
- Big smiling mouth with teeth on screen.
- Drag toothbrush across teeth — they sparkle as cleaned.
- Missed spots wiggle gently to attract attention.
- **Progression:** Squeeze toothpaste first (drag tube to brush), rinse with cup, real 2-minute timer mode with fun progress bar (syncs to actual brushing time!).

### 7C: Tidy Up 🧸
- A messy room: toys on floor, books scattered, clothes on bed.
- Drag each item to its home — teddy → shelf, books → bookcase, clothes → hamper.
- Items make silly sounds when picked up (squeak, boing, whoosh).
- **Progression:** More items. Categorization — "put all BLUE toys in the box." Making the bed (drag blanket up in a smooth gesture).

### 7D: Getting Dressed 👕
- Paper-doll style. Drag clothes onto character in correct order.
- Underwear → shirt → pants → socks → shoes.
- Wrong order → silly animation (sock on head, shoe on hand) + giggle sound + gentle bounce-back.
- **Progression:** Weather-based! Show a window with weather (rain/sun/snow). Pick appropriate clothes (raincoat, sunhat, winter coat). Wrong weather choice gets a playful reaction (character shivers in sun clothes during snow).

### 7E: Table Time 🍽️
- Place setting silhouettes on a placemat.
- Drag plate, cup, fork, spoon to correct positions.
- Then drag healthy foods onto the plate.
- **Progression:** Food groups — sort into fruits / vegetables / grains. "Build a balanced meal."

### 7F: Bedtime Routine 🌙 (unlocks at 60 stars)
- A sequence mini-game: drag pajamas on → tap to brush teeth (mini brushy-brushy) → drag teddy into bed → tap light switch → stars appear on ceiling → lullaby plays.
- Designed to be played as part of actual bedtime routine.
- Gentle, calming animations. Slow transitions. Soft colors.
- Lullaby: use a royalty-free gentle melody or generate a simple tone sequence.

### "My Day" Timeline
- At the top of the My Day house screen, a horizontal timeline with icons: 🌅 wake → 🪥 brush → 👕 dress → 🍽️ eat → 🧸 play → 🧹 tidy → 🛁 bath → 🌙 sleep.
- As mini-games are completed in a session, the corresponding icon fills with a star.
- Resets daily.

---

## Free-Play Canvas 🎨

- Open-ended drawing/stamping screen.
- Bottom toolbar: color picker (6 large color circles), brush size (2 sizes), stamp selector.
- Stamps: all unlocked animals, shapes, food items, stickers. Tap to place on canvas.
- Tap stamp on canvas to hear its name.
- Clear button (long-press to prevent accidents).
- No goals, no scoring. Just creative play.

---

## Parent Settings (long-press gear icon, 2 seconds)

Settings screen with large, simple toggles:

| Setting | Options |
|---------|---------|
| Language | עברית / English |
| Sound Effects | On / Off |
| TTS Voice | On / Off |
| Music | On / Off |
| Reset Progress | Confirm dialog with "Are you sure?" |
| Daily Time Limit | Off / 15 min / 30 min / 45 min / 60 min |

Time limit: when reached, show a friendly "Time to take a break!" screen with a waving character. Only parent can dismiss (long-press).

---

## Animation & Polish Guidelines

- Use Compose animations: `animateFloatAsState`, `AnimatedVisibility`, `Animatable` for bounces and wiggles.
- Success celebrations: scale-up + rotate + particle confetti (use a simple Canvas-based particle system).
- Drag feedback: item scales up slightly (1.1x) when grabbed, shadow appears underneath.
- Transition between screens: gentle slide or fade. Never jarring.
- Idle animations: animals breathe/blink, flowers sway, clouds drift. The world feels alive.
- Color palette: bright but not neon. Soft, warm tones. Think Bluey/Hey Duggee aesthetic.

---

## Sound Design

- **TTS:** Android TextToSpeech for all words, letters, numbers, animal names, instructions.
- **SFX (bundled .ogg files):** tap (pop), success (chime), drag-start (soft pickup), drag-wrong (gentle boing), confetti (sparkle cascade), star-earned (ding-ding-ding ascending).
- **Animal sounds:** Short clips bundled as assets (moo, woof, meow, etc.).
- **Music:** One gentle looping background track for hub world. Each island can have its own ambient loop. All toggleable.

---

## File/Folder Structure Suggestion

```
app/
├── src/main/
│   ├── java/com/olamkatan/
│   │   ├── MainActivity.kt
│   │   ├── navigation/
│   │   │   └── NavGraph.kt
│   │   ├── ui/
│   │   │   ├── hub/              # Hub world screen
│   │   │   ├── animals/          # Animal Garden
│   │   │   ├── colors/           # Color Splash
│   │   │   ├── shapes/           # Shape Playground
│   │   │   ├── counting/         # Counting Garden
│   │   │   ├── music/            # Music Meadow
│   │   │   ├── words/            # Word Garden
│   │   │   ├── myday/            # My Day house + all rooms
│   │   │   ├── canvas/           # Free-play canvas
│   │   │   ├── stickers/         # Sticker album
│   │   │   ├── settings/         # Parent settings
│   │   │   └── components/       # Shared composables (ConfettiOverlay, StarCounter, DragTarget, etc.)
│   │   ├── data/
│   │   │   ├── GameRepository.kt
│   │   │   ├── ProgressStore.kt  # DataStore for stars, stickers, difficulty levels
│   │   │   └── ContentProvider.kt # Animal/shape/color data definitions
│   │   ├── audio/
│   │   │   ├── TtsManager.kt     # TextToSpeech wrapper
│   │   │   └── SfxPlayer.kt      # Sound effects via SoundPool
│   │   └── theme/
│   │       ├── Theme.kt
│   │       ├── Color.kt
│   │       └── Type.kt
│   ├── res/
│   │   ├── raw/                  # .ogg sound effects + animal sounds
│   │   └── drawable/             # Vector drawables for all illustrations
│   └── AndroidManifest.xml
├── build.gradle.kts
└── gradle/
```

---

## Implementation Priority (suggested order)

1. **Project setup** — Compose project, navigation, theme, language toggle.
2. **Hub world** — static scene with tappable islands, star counter.
3. **Animal Garden Level 1** — this validates the full loop: tap interaction, TTS, drag-and-drop, success feedback, star award.
4. **Progression system** — stars, unlocking, difficulty tracking.
5. **Remaining islands Level 1** — Color Splash, Shape Playground, Counting Garden.
6. **My Day house** — Bath Time and Brushy Brushy first (most engaging for daily use).
7. **All islands higher difficulty levels.**
8. **Music Meadow + Word Garden.**
9. **Free Canvas + Sticker Album.**
10. **Polish** — animations, particles, idle animations, ambient sounds.
11. **Parent settings + time limit.**
12. **Bedtime Routine.**

---

## Key Considerations

- **Test on a real tablet** with a real toddler frequently. What seems intuitive to an adult often isn't for a 2-year-old.
- **Accessibility:** Large text, high contrast, no reliance on color alone (always pair with shape/icon).
- **Battery:** Minimize battery drain — avoid constant high-framerate animations when idle. Use `LaunchedEffect` with delays rather than continuous recomposition.
- **TTS initialization:** Android TTS takes a moment to initialize. Pre-initialize on app start and show a friendly loading animation.
- **Hebrew RTL:** Compose handles RTL via `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)`. Test thoroughly — drag directions, text alignment, and navigation all flip.
