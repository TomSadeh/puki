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
app/src/main/java/io/github/tomsadeh/puki/
├── MainActivity.kt              # Single activity, sets up Compose + audio players
├── PukiApp.kt              # Top-level composable, NavHost, the companion's brain choice
├── PukiApplication.kt      # App-wide objects: repositories, Companion, the device brain
├── navigation/NavGraph.kt       # All routes
├── ui/
│   ├── hub/                     # Hub world (islands on the sea)
│   ├── animals/ colors/ shapes/ counting/ music/ words/   # The islands
│   ├── say/                     # Say it with Puki (speech, needs the brain)
│   ├── myday/                   # My Day house + one screen per routine, bedtime story
│   ├── canvas/                  # Free painting (Puki looks at the drawing)
│   ├── stickers/  onboarding/  settings/
│   ├── common/                  # CompositionLocals, AppViewModel, GameSession (levels, stars)
│   └── components/              # Shared composables (see below)
├── data/
│   ├── models/  content/        # Data classes and the static game content
│   ├── ProgressRepository.kt    # Stars, stickers, levels (and the level rule, nextLevel)
│   └── SettingsRepository.kt    # Language, gender, sound, time limit, companion
├── audio/                       # VoicePlayer, SfxPlayer, MusicPlayer, MicRecorder, Companion (talks to a brain)
├── brain/                       # The brain on a high-end phone: LocalBrain, Persona, Mind (Gemma),
│                                #   Ears (whisper.cpp JNI), voice/ (BlueTTS port), DeviceModels
├── update/Updater.kt            # In-app updates from GitHub releases
└── theme/
brain/                           # The PC brain (Python): doorbell.py, brain.py, persona.py, installer, tray
scripts/                         # Voices (generate/check), release.py
native/whisper/                  # Builds libpuki_whisper.so (prebuilt copies are in jniLibs)
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
| `GameBackground`, `SceneBackdrop` | A game's backdrop: an illustrated scene (farm, stage, bathroom...) or bubbles |
| `GameTopBar` / `MyDayTopBar` | Back, round dots and stars / back and the routine's name |
| `RoundPrompt` | Starts a round: tells Puki the scene, then plays the spoken prompt |
| `DragTarget` / `DropZone` | Drag and drop, also tap-to-place (`tapToZone`); see Drag and Drop |
| `RoundCompleteSheet` | End-of-session card: stars landing, sun rays, confetti, continue/home |
| `ConfettiOverlay` | Two confetti cannons and a gentle shower (about 90 pieces) |
| `CompanionParrot` | Puki in the corner: tap to chat, hold to talk |
| `Help.kt` | Hints when she's stuck (`rememberHelp`, `hintGlow`) |
| `Juice.kt`, `Reactions.kt`, `Scatter.kt` | Squish on press, pop-ins, idle bobbing, reactions, scattered layouts |
| `LongPressGate` | Two-second hold for parent-only actions |
| `TimeLimitOverlay` | The calm end when the time limit is reached |
| `WigglingItem` | Gently wiggles to attract attention |

Drawing: build Paths once (`drawWithCache`), put still parts in their own layer (`graphicsLayer()`)
and redraw only what moves; the tablet (Galaxy Tab A7 Lite) is slow.

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

- `DragTarget` handles the drag; `DragDropState` finds the zone under the piece (its centre, or
  at least 30% overlap). A wrong drop springs back home.
- `tapToZone`: a plain tap flies the piece to that zone, as if dragged (toddlers tap more than drag).
- The game decides right or wrong in `onDragEnd`; a second tap after a win is ignored (`null`).

### Animations

- Prefer Compose animation APIs: `animateFloatAsState`, `AnimatedVisibility`, `Animatable`, `updateTransition`.
- Spring animations for bounces: `spring(dampingRatio = 0.4f, stiffness = 300f)`.
- Confetti: custom `Canvas` drawing with a list of `Particle` data classes updated via `LaunchedEffect` + `withFrameMillis`.
- Idle animations (breathing, blinking): `rememberInfiniteTransition`.
- Keep frame rate reasonable: animations run only while their thing is on screen, and particles stay under ~100.

### Audio

- All speech is recorded: add a line to `scripts/generate_voices.py`, generate it, then run `scripts/check_voices.py` to confirm nothing the app plays is missing. Play with `voicePlayer.play(key)` or `playSequence(keys)` for sequences (counting: "one… two… three…").
- Lines that address the child come as `_he_f` / `_he_m` files; `VoicePlayer` picks the one matching the gender setting.
- Personal lines with the child's name (`voice_*_name*`) are git-ignored and optional; use `voicePlayer.personalKey(...)` / `playPraise(...)`, which fall back to generic lines.
- `SfxPlayer` uses `SoundPool` with max 6 streams. Preload all SFX on app start. Keep the pool small (<20 sounds total).
- Background music via `MediaPlayer` with `isLooping = true`. Fade out on pause/stop.
- Respect settings: check `SettingsRepository` before playing any audio.

### Difficulty System

- Each island has a level, 1 to `ProgressRepository.MAX_LEVEL` (3), in DataStore (`"difficulty_animals"`).
- At the end of a session (`GameSession.finish`): two easy sessions in a row (at most one slip every
  other round) move it up; a hard one (two slips a round) moves it down. The rule is
  `ProgressRepository.nextLevel`, tested in `LevelRuleTest`.
- The ViewModel reads the level when it starts (`GameSession.level()`) and picks content for it.

### Testing Considerations

- JVM unit tests in `app/src/test` (`./gradlew testDebugUnitTest`): the phone brain's Persona and
  voice text, the level rule, pairing links, and `RegexPortabilityTest` (no Java-only regex flags:
  Android's ICU engine rejects `(?U)`, which crashed 0.3.0 at start).
- The PC brain's persona: `python -m pytest brain/tests`.
- `python scripts/check_voices.py`: every voice line the app plays has audio.
- CI (`.github/workflows/ci.yml`) runs all of these on every push.
- Before a release, run it on a real device: the debug build's `SelfTestReceiver`
  (`adb shell am broadcast -n io.github.tomsadeh.puki/.SelfTestReceiver`, then `adb logcat -s PukiSelfTest`).
- Keep composables "dumb" — they render state and emit events, nothing else.

---

## Things to Avoid

- **No XML layouts.** Everything is Compose.
- **No failing states.** Never show error messages, red indicators, sad sounds, or negative feedback to the child. Wrong actions get gentle, playful redirects.
- **No small tap targets.** If it's tappable, it's at least 100dp. 120dp preferred.
- **No text-only navigation.** The child cannot read. All navigation is visual (icons, pictures, animations).
- **No internet.** The only network use is the optional companion on a PC at home, over the home network (see `brain/`), and checking GitHub for app updates. Everything else works fully offline.
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
./gradlew testDebugUnitTest
python -m pytest brain/tests
```

Test on a real tablet or phone over adb; the x86 emulator is very heavy. Releases: `python scripts/release.py --publish`
(builds the shrunk release APK and uploads it as a GitHub pre-release; installed apps offer the update).
All builds are signed with the release key in `~/keys` (puki-release.jks + puki-signing.properties,
never in git); without it they fall back to the debug key, which release.py won't publish.

---

## Where things stand

The original build plan is done (all islands, My Day, canvas, stickers, bedtime, levels). What's
next is in ROADMAP.md.
