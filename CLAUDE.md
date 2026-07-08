# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Smart Home Dashboard** — a wall-mounted / tablet home-control surface: lighting, audio, a climate glance, media and calendar. It is a single-screen, landscape, touch-first dashboard targeting a **Xiaomi Redmi Pad 2 (11″, 1280×800, 16:10)**, built as a **Kotlin Multiplatform / Compose Multiplatform** app (Android + iOS share one Compose UI).

The full design + UX spec lives in `app/docs/` and is the source of truth for what to build — see [Design Reference](#design-reference). The project is **early-stage**: the module scaffold, navigation host, and theme plumbing exist, but most of the dashboard UI is still stubbed (see [Current State](#current-state)).

> The design was handed off (in `app/docs/`) as a Vite + React + TypeScript brief. That stack recommendation is **superseded** — we build in Compose Multiplatform. Read the handoff spec for design intent and behavior; the geometry, tokens, and dial/slider math are transcribed into the [Design Reference](#design-reference) below. Ignore any React/Vite/CSS-Modules stack guidance and re-author as idiomatic Compose.

## Planning & Exploration

When planning a task, use the file map and architecture section in this CLAUDE.md to identify the specific files relevant to the task, then read those files directly to get their current state. Do not do broad codebase exploration (grep sweeps, find commands, reading many files) when the relevant files are already documented here. The codebase is small — go straight to the 2–3 files that matter.

## Build & Development Commands

**The Gradle project root is `app/`, not the repo root.** Run all Gradle commands from `app/` (e.g. `cd app && ./gradlew …`).

```bash
# Build the Android debug APK
./gradlew :androidApp:assembleDebug

# Install & run on a connected device/emulator
./gradlew :androidApp:installDebug

# Build the shared KMP module (all targets)
./gradlew :shared:build

# Run all tests across all KMP targets (common + android host + ios sim)
./gradlew allTests

# Run everything (build + tests + checks)
./gradlew check

# Clean
./gradlew clean
```

iOS is built from Xcode via `app/iosApp/iosApp.xcodeproj` (it consumes the `Shared.framework` produced by the `:shared` module).

## Architecture

Kotlin Multiplatform project with a shared Compose UI and thin per-platform entry points. Three Gradle modules under `app/`:

- **`:shared`** — All app code: UI, navigation, state, and (eventually) the device-data layer. This is where nearly all work happens. Namespace `com.mattschoe.smarthome.shared`. Source sets: `commonMain` (everything shared), `androidMain` / `iosMain` (platform actuals), `commonTest` / `androidHostTest` / `iosTest`.
- **`:androidApp`** — Android application module. `MainActivity` + `AppApplication` create the `AppContainer` and call `App(appContainer)`. Namespace / applicationId `com.mattschoe.smarthome`.
- **`iosApp`** — Xcode project. Entry point `MainViewController()` (in `shared/iosMain`) wraps `App(appContainer)` in a `ComposeUIViewController`.

**Key architectural decisions:**
- **Compose Multiplatform UI in `commonMain`** — a single Compose tree renders on both platforms. Avoid platform-specific APIs in `commonMain`; use `expect`/`actual` (see `Platform.kt`) when you genuinely need them.
- **Manual DI via `AppContainer`** (no Hilt/Koin) — constructed in each platform entry point and passed into `App()`, then down to ViewModels. Currently an empty stub.
- **Type-safe navigation** using `@Serializable` sealed classes in `PageNavigation.kt` with `navigation-compose`. The dashboard is effectively single-screen; the nav host exists so sub-screens (settings, per-app pages) can be added.
- **Reactive state** — the adapter exposes device data (`HomeState`); the ViewModel owns UI-selection state as private `MutableStateFlow`s and `combine()`s them with the adapter flow into one public `screenState: StateFlow<HomeScreenState>` via `stateIn(viewModelScope, WhileSubscribed(5000), Loading)`. The UI collects it with `collectAsStateWithLifecycle()`.
- **Compose resources** — fonts/images live in `shared/src/commonMain/composeResources/` and are accessed via the generated `smarthome.shared.generated.resources.Res` accessor (e.g. `Res.font.newsreader`).

## Current State

The app compiles as a scaffold but the dashboard is **not yet implemented**. What exists:

When implementing, build the [design](#design-reference) against a mock in-memory store first; leave a typed adapter seam for real device integration ([Data Boundary](#data--device-boundary)).

## File Map

All paths relative to `app/shared/src/commonMain/kotlin/com/mattschoe/smarthome/` unless noted.

**Entry points & DI:**
- `App.kt` — Root composable `App(appContainer)`; applies `SmartHomeTheme` and hosts navigation.
- `AppContainer.kt` — Manual DI container (currently empty). Holds shared dependencies (adapters, repositories) as they are added.
- `Platform.kt` / `Platform.android.kt` / `Platform.ios.kt` — `expect`/`actual` platform info.
- `../../androidApp/.../MainActivity.kt` — Android `ComponentActivity` + `AppApplication`; edge-to-edge, creates `AppContainer`.
- `iosMain/.../MainViewController.kt` — iOS Compose entry point.

**Navigation (`ui/navigation/`):**
- `PageNavigation.kt` — **Registration point**: `@Serializable` sealed class of routes (currently just `Home`).
- `ApplicationNavigationHost.kt` — `NavHost`; maps routes to page composables and creates their ViewModels.

**Pages (`ui/pages/`):**
- `homepage/Homepage.kt` — The dashboard screen (stub). `LandscapeLayout` will hold the 3-column layout.
- `homepage/HomepageViewModel.kt` — Dashboard state holder (stub). Owns the per-room state, active room, and panel tab. (Accent, clock format, name, and room list are fixed app constants, not state.)

**Components (`ui/components/`):**
- `PageShell.kt` — Thin `Scaffold` wrapper (top bar + content padding).

**Theme (`ui/theme/`):**
- `Color.kt` — Design token colors (empty — needs the palette below).
- `Theme.kt` — `SmartHomeTheme` wrapper.

**Resources (`shared/src/commonMain/composeResources/`)**

**Docs (`app/docs/`):**
- `Claude Code Handoff - Smart Home Dashboard.dc.html` — The design/UX spec (intent, tokens, component contracts, interactions, state model, DoD).
- `Dashboard_with_media.png` / `Dashboard_with_calendar.png` — Rendered reference screenshots of the target dashboard (1240×800), showing the Media and Calendar right-panel states. These are the visual source of truth; the exact geometry, dial/volume math, and tokens are transcribed into this file below. `Read` them directly for layout, spacing, and color intent.

## Design Reference

The design intent lives in two places in `app/docs/`: the **rendered screenshots** (`Dashboard_with_media.png`, `Dashboard_with_calendar.png`) show exactly what to build, and the **handoff spec** (`Claude Code Handoff - Smart Home Dashboard.dc.html`, a React-ish prototyping format) carries the contracts, interactions, and DoD. **Read the screenshots for layout/color, the spec for behavior, port to Compose.** Highlights:

**Layout** — One full-bleed screen at 1280×800, landscape. A sage surface fills the viewport; three cream cards float on it in a fixed row: **LEFT 288px fixed** (date/time + 2×2 climate stats + scrolling Apps grid), **CENTER flex 1, min 346px** (room chips, brightness dial, warmth swatches, audio), **RIGHT flex 1.12, min 392px** (Media / Calendar tab switch, scrolls). Only card-internal regions scroll; the page never does. Design for the fixed 1280×800 device in v1 — do not make it fluid/responsive.

**Signature interactions** (copy the math from the prototype):
- **Brightness dial** — SVG-style half-arc (260×160 viewBox, center (130,140), radius 116), drag the knob to set 0–100%. Value = `round((1 − deg/180) × 100)`. Tapping the center bulb toggles the light on/off; dragging forces it on. In Compose: draw with `Canvas`/`drawArc`, handle drag with `pointerInput { detectDragGestures }`, and reproduce the pointer-angle math. The arc/knob take the current **warmth** color.
- **Warmth swatches** — five color-temp circles (Candle→Warm→Soft→Neutral→Cool); selecting one recolors the dial and turns the light on.
- **Volume slider** — horizontal drag; fraction = `(x − left) / width` clamped 0–1. Sets the **active audio room's** volume.
- **Room chips** — pill toggles; active = filled accent, idle = white with sage border. **Light and audio have separate selectors** (as in the reference PNGs): the top chip row picks the **light room** (dial + warmth); a second chip row in the AUDIO section picks the **audio room** (volume slider, and later the Media panel). The two are independent — one does **not** drive the other. The audio row lists only **speaker rooms** (`Room.audioRooms`, gated by `Room.hasSpeaker`), each with a speaker glyph. *(The "Whole home" speaker chip and the dashed "Join the music in {source}" affordance are **deferred from v1** — they're the multi-room grouping feature; v1 audio is strictly per-room. See the State Model CORE RULE.)*
- **Media / Calendar tabs** — pill segmented control; Media (search, now-playing + scrubber, transport, queue, horizontal playlist rail) and Calendar (month grid, agenda, to-do). The Media panel binds to the **active audio room's** audio; its empty state is simply *that room has nothing playing*.

**Design tokens** (centralize in `Color.kt` / `Type.kt` — the prototype hardcodes hex; don't):
- Surface (sage) `#B2C488` · Card `#FAF8EA` · Card border `#A7BB7C` · Ink `#23301C` · Ink soft `#5C6650` · Muted `#A7A88C` · Sage green `#839958` · Teal `#105666` · Rose `#D3968C` · Warm amber `#E0A24E` · Inset fill `#ECE6CF`.
- **Accent** — a single fixed accent drives every selected/active state (room chips, tabs, today's calendar cell). It is **not** user-switchable: Forest `#0A3323` with on-accent text `#F6EEC7`.
- **Type** — Newsreader everywhere, weights 400/500/600. Section labels (APPS, WARMTH, AUDIO…): ~10–11px, weight 500, uppercase, letter-spacing ~1.5px, color `#A7A88C`. Icons: Material Symbols Rounded (FILL 1). Compose has no Material Symbols bundled — plan to ship the icon glyphs/font or map to the closest `Icons.*` equivalents.

### State Model

State is split into two layers: **device data** the adapter exposes (`HomeState`) and **screen state** the ViewModel assembles (`HomeScreenState`). The ViewModel owns the UI selection (`activeLightRoom`/`activeAudioRoom`/`panel`) as private `MutableStateFlow`s and `combine()`s them with the adapter's `HomeState` into `screenState`. The implemented shapes live in `data/model/DashboardModels.kt` (device) and `ui/pages/homepage/HomeScreenState.kt` (screen):

```kotlin
enum class Warmth { Candle, Warm, Soft, Neutral, Cool }
// hasSpeaker gates the audio selector; Room.audioRooms = entries.filter { it.hasSpeaker }. Flip one line to move a speaker.
enum class Room(val displayName: String, val hasSpeaker: Boolean) { LivingRoom("Stue", true), Kitchen("Køkken", false), … }

// A room owns everything inside it — both its lights AND its own audio playback.
data class RoomState(
    // lights
    val brightnessPct: Int, val isLightOn: Boolean, val lightWarmth: Warmth,
    // audio (per-room session; playlists are shared, see below)
    val volumePct: Int, val isPlaying: Boolean,
    val nowPlaying: MediaTrack?, val positionSec: Int, val queue: List<MediaTrack>,
)

// Device truth — what the HomeAdapter exposes (mock now, Home Assistant later). No UI selection.
data class HomeState(
    val rooms: Map<Room, RoomState>,
    val climate: ClimateState,          // read-only glance
    val playlists: List<Playlist>,      // shared library — any room can play from it
    val calendar: CalendarState,
)

// Screen state — HomeState + VM-owned UI selection, collected by the UI.
sealed interface HomeScreenState {
    data object Loading : HomeScreenState
    data class Ready(
        val activeLightRoom: Room,      // pure UI selection: whose lights am I viewing (dial/warmth)
        val activeAudioRoom: Room,      // pure UI selection: whose audio am I viewing (volume/media) — independent of light room
        val rooms: Map<Room, RoomState>,
        val panel: Panel,               // Media | Calendar (right card)
        val climate: ClimateState,
        val playlists: List<Playlist>,
        val calendar: CalendarState,
    ) : HomeScreenState
}
```

> **CORE RULE — do not re-break this.** Rooms own their own **lights AND audio**. There is **no**
> global audio session, `speaker`, or `audioSource`. **Light and audio are selected independently:**
> `activeLightRoom` (dial/warmth) and `activeAudioRoom` (volume/Media) are **two separate pure-UI
> selections** — the top chip row drives the first, the AUDIO chip row drives the second, and neither
> drives the other. `activeLightRoom`/`activeAudioRoom`/`panel` live in the **ViewModel**, never in
> the adapter or `HomeState`; the device-data layer must never define them. Selecting a light room
> shows *that room's* lights; selecting an audio room shows *that room's* audio. Playlists are a
> shared library, not per-room. The audio selector lists only speaker rooms (`Room.hasSpeaker`).
> Multi-room sync ("Whole home" chip / "Join the music in {source}") is a **deferred grouping
> feature**, not part of the v1 model — reintroduce it later as an additive relation (e.g. a
> `groupId`) *on top of* per-room ownership. (Earlier drafts put a global audio session, then a
> single `activeRoom`, into the shared state; both were wrong and were removed — do not collapse the
> two selectors back into one.)

This is an **opinionated, single-home dashboard** — not a configurable product. Accent (Forest), clock format, user name, and the room list are **fixed app constants**, not runtime state or user-facing settings. Settings, if ever needed, is a navigation seam to a separate screen, never an in-dashboard surface.

Switching the light room swaps the dial/warmth; switching the audio room swaps the volume slider (and the right-card Media panel) — independently. Clock ticks ~every 20s. Persist the last room states so a wall tablet survives reloads (multiplatform settings/DataStore — add when needed).

### Data & Device Boundary

Build UI-first against a **mock in-memory store**; define the seam now so real integration is a drop-in later.
- Ship a `HomeAdapter` interface (`setBrightness`, `setWarmth`, `setVolume`, `toggleLight`, `subscribe(): StateFlow<HomeState>`) — **device setters only**, with a `MockAdapter` seeded from fixtures. Controls mutate the store optimistically. Audio is per-room; the Media panel reads the active audio room's `RoomState` (no global session). UI selection (`activeLightRoom`/`activeAudioRoom`/`panel`) is not on the adapter — it lives in the ViewModel.
- Climate stats (temp/humidity/energy/outdoor) are read-only display for now.
- Leave a `MatterAdapter` / Home Assistant stub for later. Put adapters in `AppContainer`.

## Registration Points (Adding New Things)

**New navigation route / sub-screen:**
1. Add a `@Serializable` entry to the `PageNavigation` sealed class.
2. Add a `composable<PageNavigation.X> { … }` route in `ApplicationNavigationHost.kt`, creating its ViewModel there.
3. Create the page composable + ViewModel under `ui/pages/`.

**New shared dependency (adapter/repository):** construct it in `AppContainer` and pass it into the ViewModel(s) that need it via `ApplicationNavigationHost`.

**New design token:** add it to `Color.kt` — never hardcode hex in composables.

## Key Conventions

- **Common-first**: put UI and logic in `commonMain`. Only drop to `androidMain`/`iosMain` (via `expect`/`actual`) for genuinely platform-specific APIs.
- **Compose-first UI**: screens are `@Composable` functions in `ui/pages/`, paired with a `ViewModel` exposing a `StateFlow`, collected with `collectAsStateWithLifecycle()`.
- **Touch, not mouse**: all drags via `pointerInput` gesture detectors; ensure hit targets ≥ 44dp (chips, transport buttons, swatches already exceed this).
- **No decorative gradients** — the only gradients are the functional dial glow and the right-panel bottom scroll fade. Cards keep a 1px border but now carry a subtle soft drop shadow (as do pills and the warmth swatches); elevation tokens live in `Dimensions` (`cardElevation`/`pillElevation`/`swatchElevation`). Canvas-drawn shapes (dial bulb/knob) fake the same shadow by under-drawing a low-alpha offset circle.
- **Accessibility**: give every icon-only control a content description; the dial/slider should expose a slider semantics role + keyboard/arrow support (the prototype omits this — add it).
- **UI verification**: after a UI change that affects what the user sees, verify it on a connected Android device/emulator with `/android-verify` before reporting done.

## SDK & Toolchain

- **AGP** 9.2.1 · **Kotlin** 2.4.0 · **Compose Multiplatform** 1.11.1 · **Material3** 1.11.0-alpha07 · **navigation-compose** 2.9.2 · **lifecycle** 2.11.0-rc01 · **kotlinx-serialization-json** 1.9.0.
- **Android**: minSdk 24, target/compileSdk 37, JVM target 11.
- **iOS targets**: `iosArm64`, `iosSimulatorArm64` (static `Shared.framework`).
- Version catalog: `app/gradle/libs.versions.toml`. Config cache + build cache are enabled (`app/gradle.properties`).
</content>
</invoke>
