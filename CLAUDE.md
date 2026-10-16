# CLAUDE.md — Puki (פוקי)

## What This Project Is

An Android tablet game for toddlers (ages 2–4), bilingual in Hebrew and English. A hub world with mini-game islands covering animals, colors, shapes, counting, music, letters, and daily routines (brushing teeth, tidying up, getting dressed, etc.). No ads, no tracking, no cloud. An optional talking parrot companion runs on a PC at home (`brain/`).

See `olam-katan-claude-code-prompt.md` for the full game design document.

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (no XML layouts, no Views)
- **Min SDK:** 26 | **Target SDK:** 34
- **Architecture:** Single-activity, MVVM. One `ViewModel` per mini-game screen.
- **Navigation:** Compose Navigation (`NavHost` / `NavController`)
- **Persistence:** Jetpack DataStore (Preferences) for progress, stars, stickers, difficulty levels, settings. No Room unless complexity demands it later.
- **Audio:** Pre-recorded voice lines (OGG in `res/raw`, generated offline with BlueTTS by `scripts/generate_voices.py`) played by `VoicePlayer`. `SoundPool` for short SFX. `MediaPlayer` for background music loops.
- **Graphics:** Vector drawables + Compose `Canvas` for custom drawings and particle effects. No Lottie, no third-party animation libraries.
- **Orientation:** Landscape only (locked in manifest).

---

## Project Structure

```
app/src/main/java/com/olamkatan/
├── MainActivity.kt              # Single activity, sets up Compose + audio players
├── OlamKatanApp.kt              # Top-level composable, NavHost lives here
├── navigation/
│   └── NavGraph.kt              # All routes defined here
├── ui/
│   ├── hub/                     # Hub world screen
│   ├── animals/                 # Animal Garden island
│   ├── colors/                  # Color Splash island
│   ├── shapes/                  # Shape Playground island
│   ├── counting/                # Counting Garden island
│   ├── music/                   # Music Meadow island
│   ├── words/                   # Word Garden island
│   ├── myday/                   # My Day house + sub-screens per room
│   ├── canvas/                  # Free-play canvas
│   ├── stickers/                # Sticker album
│   ├── settings/                # Parent settings
│   └── components/              # Shared composables (see below)
├── data/
│   ├── models/                  # Data classes: Animal, Shape, Color, Sticker, etc.
│   ├── content/                 # Static content definitions (animal lists, shape lists, etc.)
│   ├── ProgressRepository.kt   # Stars, stickers, difficulty levels
│   └── SettingsRepository.kt   # Language, sound, time limit
├── audio/
│   ├── VoicePlayer.kt          # Plays recorded voice lines (gendered Hebrew variants, praise, personal lines)
│   └── SfxPlayer.kt            # SoundPool wrapper for tap/success/error sounds
└── theme/
    ├── Theme.kt
    ├── Color.kt
    └── Type.kt
```

---

## Conventions

### Kotlin / Compose

- Use Kotlin idioms. Prefer `data class`, `sealed class`, `when`, extension functions.
- Composable functions are PascalCase. Non-composable functions are camelCase.
- Every screen has a `*Screen.kt` composable and a `*ViewModel.kt`. The Screen observes state from the ViewModel via `collectAsStateWithLifecycle()`.
- State flows down, events flow up. No mutable state in composables — all state lives in ViewModels as `StateFlow`.
- Use `remember` and `derivedStateOf` for local UI-only state (animation values, drag positions).
- No `mutableStateOf` in ViewModels — use `MutableStateFlow` / `StateFlow`.

### Shared Components (`ui/components/`)

Build and reuse these across all islands:

| Component | Purpose |
|-----------|---------|
| `DragTarget` | Generic drag-and-drop composable with snap-back animation |
| `DropZone` | Target area that highlights when a draggable hovers over it |
| `ConfettiOverlay` | Canvas-based particle confetti triggered on success |
| `StarBurst` | Star-earned celebration animation |
| `BigTapButton` | Oversized tappable area with scale animation on press |
| `RoundCompleteSheet` | "Great job!" overlay with star award and continue/home buttons |
| `LongPressGate` | Wrapper requiring 2-second long-press (for parent-only actions) |
| `WigglingItem` | Item that gently wiggles to attract attention |

### Naming

- Route names: `"hub"`, `"animals"`, `"colors"`, `"shapes"`, `"counting"`, `"music"`, `"words"`, `"myday"`, `"myday/bath"`, `"myday/brush"`, `"myday/tidy"`, `"myday/dress"`, `"myday/table"`, `"myday/bedtime"`, `"canvas"`, `"stickers"`, `"settings"`.
- Resource files: `ic_animal_cow.xml`, `ic_shape_circle.xml`, `snd_tap.ogg`, `snd_success.ogg`. Prefix with `ic_` for icons, `snd_` for sounds, `bg_` for backgrounds.
- Data classes: `Animal`, `ShapeItem`, `ColorItem`, `Sticker`, `GameProgress`, `AppSettings`.
- ViewModels: `AnimalGardenViewModel`, `ColorSplashViewModel`, etc.

### Language / Bilingual

- All user-facing strings go through a bilingual content system, NOT Android string resources (since we switch language at runtime without recreating the activity).
- Create a `BilingualText` data class: `data class BilingualText(val he: String, val en: String)`. Access via a `currentLanguage` state.
- Layout direction switches with language: wrap content in `CompositionLocalProvider(LocalLayoutDirection provides if (isHebrew) LayoutDirection.Rtl else LayoutDirection.Ltr)`.

### Drag and Drop

- Use `Modifier.pointerInput` with `detectDragGestures` for drag.
- Track drag offset in composable state. On release, check proximity to drop zones.
- If match: animate to snap position, trigger success.
- If no match: animate back to origin with a spring animation + play boing sound.
- Drag threshold: item must overlap drop zone by ≥40% to register as a drop.

### Animations

- Prefer Compose animation APIs: `animateFloatAsState`, `AnimatedVisibility`, `Animatable`, `updateTransition`.
- Spring animations for bounces: `spring(dampingRatio = 0.4f, stiffness = 300f)`.
- Confetti: custom `Canvas` drawing with a list of `Particle` data classes updated via `LaunchedEffect` + `withFrameMillis`.
- Idle animations (breathing, blinking): `rememberInfiniteTransition`.
- Keep frame rate reasonable — don't animate 200 particles at 60fps continuously. Cap particles at 30–50, remove when off-screen.

### Audio

- All speech is recorded: add a line to `scripts/generate_voices.py`, generate it, then run `scripts/check_voices.py` to confirm nothing the app plays is missing. Play with `voicePlayer.play(key)` or `playSequence(keys)` for sequences (counting: "one… two… three…").
- Lines that address the child come as `_he_f` / `_he_m` files; `VoicePlayer` picks the one matching the gender setting.
- Personal lines with the child's name (`voice_*_name*`) are git-ignored and optional; use `voicePlayer.personalKey(...)` / `playPraise(...)`, which fall back to generic lines.
- `SfxPlayer` uses `SoundPool` with max 6 streams. Preload all SFX on app start. Keep the pool small (<20 sounds total).
- Background music via `MediaPlayer` with `isLooping = true`. Fade out on pause/stop.
- Respect settings: check `SettingsRepository` before playing any audio.

### Difficulty System

- Each island has an `Int` difficulty level stored in DataStore, keyed like `"difficulty_animals"`.
- After 3 consecutive successful rounds at a level, increment difficulty.
- Never decrement. A "successful round" means completing the activity (since there's no fail state, track whether the child completed without excessive wrong attempts — but err toward advancing).
- ViewModel reads difficulty on init and selects content accordingly.

### Testing Considerations

- Every ViewModel should be testable with fake repositories.
- Keep composables "dumb" — they render state and emit events, nothing else.
- Content definitions (animal lists, etc.) should be pure data, easy to verify.

---

## Things to Avoid

- **No XML layouts.** Everything is Compose.
- **No failing states.** Never show error messages, red indicators, sad sounds, or negative feedback to the child. Wrong actions get gentle, playful redirects.
- **No small tap targets.** If it's tappable, it's at least 100dp. 120dp preferred.
- **No text-only navigation.** The child cannot read. All navigation is visual (icons, pictures, animations).
- **No internet.** The only network use is the optional companion on a PC at home, over the home network (see `brain/`). Everything else works fully offline.
- **No third-party analytics, ads, or tracking.**
- **No `Thread.sleep` or blocking calls on main thread.** Use coroutines.
- **No hardcoded strings in composables.** All text goes through `BilingualText`.

---

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Run tests
./gradlew test
```

Target a tablet emulator: Pixel C or Nexus 10 profile, API 34, landscape orientation.

---

## Implementation Order

Follow this order. Each step should result in a working, testable build:

1. Project scaffolding: Compose activity, theme, navigation shell, empty screens.
2. Settings + language system: DataStore, `BilingualText`, language toggle, RTL switching.
3. Audio system: `TtsManager` + `SfxPlayer` initialization and basic playback.
4. Hub world: static landscape, tappable islands, star counter, lock overlays.
5. Shared components: `DragTarget`, `DropZone`, `ConfettiOverlay`, `RoundCompleteSheet`.
6. Animal Garden (Level 1 only): validates full gameplay loop.
7. Progression system: star awarding, difficulty tracking, unlock logic.
8. Remaining islands Level 1: Color Splash, Shape Playground, Counting Garden.
9. My Day house: Bath Time, Brushy Brushy, Tidy Up, Getting Dressed, Table Time.
10. Higher difficulty levels for all islands.
11. Music Meadow + Word Garden.
12. Free Canvas + Sticker Album.
13. Bedtime Routine.
14. Polish: idle animations, particles, ambient sounds, time limit.
