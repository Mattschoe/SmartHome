# Project Backlog — Smart Home Dashboard

Phased implementation roadmap. Each phase is the starting brief for a separate planning/build
session — sequential, grounded in the design specs (`app/docs/` + `CLAUDE.md`), open on
implementation detail. Do phases **in order**; each builds on the last.

## How to track progress (for agents)

- Each phase has a **status line** and a **checklist**. When you start a phase, set its status to
  `IN PROGRESS`. When it is finished **and** its verification gate passes, set the status to `DONE`,
  check every box in that phase, and fill in `Completed` (date + commit).
- Mark a box done by changing `- [ ]` to `- [x]`. A phase is only `DONE` when all its boxes are checked.
- Do **not** mark a phase `DONE` until its verification gate (Android `/android-verify` +
  iOS compile check + tests where logic exists) actually passes — report failures instead.
- Keep this file the single source of truth for status; the full rationale lives in
  `/home/matt/.claude/plans/alright-claude-lets-setup-quirky-cascade.md`.

## Status legend

`TODO` · `IN PROGRESS` · `BLOCKED` · `DONE`

---

## Guardrails (apply to every phase)

**This is an opinionated, single-home dashboard — not a configurable product.** Fixed accent
(Forest), fixed clock format, fixed name, fixed room list. No user-facing configurability unless a
phase says so.

- **Icons**: Compose bundles no Material Symbols Rounded. Each phase **enumerates the exact icons it
  needs**; the user supplies assets *before* that phase executes. Never silently substitute `Icons.*`.
- **Verification gate (every phase)**: (1) Android build + `/android-verify` vs the reference
  screenshots (`app/docs/Dashboard_with_media.png`, `Dashboard_with_calendar.png`); (2) iOS
  compile-only — `cd app && ./gradlew :shared:compileKotlinIosSimulatorArm64` (Linux, no simulator);
  (3) `./gradlew allTests` where logic exists.
- **Common-first**; no hardcoded hex/type in composables; touch targets ≥ 44dp; fixed 1280×800,
  only card-internal regions scroll.

---

## Phase 1 — Theme & design-token foundation

**Status:** `DONE` · **Completed:** 2026-07-04 (commit `1acf560`)

Make the theme the single source of truth so every later phase pulls colors/type from it.

- [x] `ui/theme/Color.kt` populated with all tokens (surface, card, border, ink, ink soft, muted,
      sage green, teal, rose, warm amber, inset) + fixed Forest accent (`#0A3323`/`#F6EEC7`).
- [x] Warmth-temperature colors (Candle→Cool) defined as tokens.
- [x] Typography finished: Newsreader 400/500/600 (currently only 400/700).
- [x] Reusable section-label style (uppercase ~10–11px, weight 500, ls ~1.5px, muted `#A7A88C`).
- [x] Swatch preview renders correctly on Android; iOS compiles.

---

## Phase 2 — Foundational state + mock data layer

**Status:** `IN PROGRESS` · **Completed:** — (reopened to correct the audio model; see note)

Land the reactive state layer early so all UI binds to a real `StateFlow` from day one.

> **Correction (2026-07-04):** the first pass modeled audio as a **global session** (`speaker`,
> `audioSource`, a global `MediaState`, `joinMusic()`) — wrong. Audio is now **per-room**, folded
> into `RoomState`; `playlists` is a shared library; there is no `speaker`/`audioSource`. Multi-room
> sync is deferred (see `CLAUDE.md` State Model CORE RULE).
>
> **Correction (2026-07-04):** UI-selection state (`activeRoom`/`panel`) was moved **out of the
> adapter** into `HomepageViewModel` — device data vs screen state separation. The adapter now
> exposes device-only `HomeState` (no `activeRoom`/`panel`, no `setActiveRoom`/`setPanel`); the VM
> owns the selection as `MutableStateFlow`s and `combine`+`stateIn`s them with the adapter flow into a
> sealed `HomeScreenState` (`Loading`/`Ready`). Re-run the verification gate before re-marking this
> phase `DONE`.

- [x] **Confirm the real room list with the user** before seeding fixtures.
      → Living Room, Kitchen, Bedroom, Bathroom, Hall.
- [x] `Warmth`, `Room` enums, `RoomState`; device-only `HomeState` (rooms/climate/playlists/calendar)
      from the adapter, `HomeScreenState` (VM-owned `activeRoom`/`panel`) in the ViewModel.
      *(`RoomId`→`Room` per plan.)*
- [x] **Audio is per-room**: `RoomState` owns `volumePct`/`isPlaying`/`nowPlaying`/`positionSec`/`queue`;
      `HomeState` carries a shared `playlists` list. No `speaker`/`audioSource`/global `MediaState`.
- [x] `HomeAdapter` interface + `MockAdapter` seeded from fixtures (rooms/calendar/climate, one room
      playing), optimistic mutations, read-only climate.
- [x] `AppContainer` constructs `MockAdapter`; `HomepageViewModel` exposes `StateFlow`; `Homepage`
      collects via `collectAsStateWithLifecycle()`.
- [x] Signature math as testable pure functions (dial angle→value, volume fraction, room-swap).
- [ ] Unit tests cover state transitions/math; iOS compiles. *(re-verify after the audio-model fix)*

---

## Phase 3 — Reusable primitives (component kit)

**Status:** `TODO` · **Completed:** —

Idempotent, stateless building blocks reused across all three cards.

- [x] `CardContainer` (cream, 1px border, radius 22, no shadow).
- [x] `SectionLabel`, `PillChip`/toggle chip (active = accent, idle = white + sage border).
- [x] `InsetSurface`, bottom scroll-fade overlay, icon wrapper (once assets exist).

---

## Phase 4 — Left card (date/time · climate · apps
> **Correction (2026-07-04):** Apps support have moved beyond the initial project backlog
> they should be seen as "additions" to the dashboard, and not vital for v1.

**Status:** `TODO` · **Completed:** —

Assemble the fixed 288px left column.

- [x] **Enumerate + request icons** (climate stats: temp/humidity/energy/outdoor; app tiles).
- [x] Date/time header (clock ticks ~20s, fixed format).
- [x] 2×2 read-only climate stat tiles bound to mock climate.
- [x] Matches reference screenshots; iOS compiles.

---

## Phase 5 — Center card (signature interactions)

**Status:** `IN PROGRESS` · **Completed:** —

The hardest phase — brightness dial, warmth, audio (likely split into sub-steps).

- [ ] **Enumerate + request icons** (volume-down, speaker/room glyphs).
- [x] Room chips swap the entire center-card state (per-room), no animation.
- [x] Brightness dial: Canvas half-arc + `pointerInput` drag (`value = round((1 − deg/180) × 100)`),
      center-bulb toggle, drag forces on, warmth-colored arc/knob, off-state grey + "Off".
- [x] Warmth swatches recolor the dial + turn light on; selected = scale 1.08 + double-ring halo.
- [ ] Audio (**per-room** — bound to the active room): volume slider (`(x−left)/width`).
      *Deferred from v1: the "Whole home" speaker chip and dashed "Join the music in {source}" — that's
      the multi-room grouping feature, not part of the per-room model (see CLAUDE.md CORE RULE).*
- [ ] `role = slider` + arrow-key a11y on dial and slider.
- [ ] Touch drag works on-device; room-swap swaps all state; warmth↔dial linkage; iOS compiles.

---

## Phase 6 — Right card, Media panel

**Status:** `TODO` · **Completed:** —

Media/Calendar tab shell + Media content.

- [ ] **Enumerate + request icons** (search, transport, shuffle/repeat).
- [ ] Pill segmented control (Media | Calendar, one active).
- [ ] Media binds to the **active room's** audio (`RoomState.nowPlaying`/`queue`); the empty state is
      simply *that room has nothing playing*. Playlists rail reads the shared `DashboardState.playlists`.
- [ ] Media: search, now-playing + scrubber, transport row, vertical "Up next" queue,
      horizontal scroll-snap "Playlists" rail, 40px bottom fade.
- [ ] Matches `Dashboard_with_media.png`; tab switch + scroll work; iOS compiles.

---

## Phase 7 — Right card, Calendar panel

**Status:** `TODO` · **Completed:** —

Calendar content behind the same tab shell.

- [ ] **Enumerate + request icons** (month nav arrows, checklist/checkbox).
- [ ] Month grid (today = accent cell) + prev/next month arrows.
- [ ] "Today" agenda list with colored dots + "To-do" checklist.
- [ ] Matches `Dashboard_with_calendar.png`; month nav works; iOS compiles.

---

## Phase 8 — Polish, persistence, nav seams, a11y & DoD

**Status:** `TODO` · **Completed:** —

Close the gap to the Definition of Done (handoff spec §09) + deferred seams.

- [ ] Settings nav seam only: `Settings` route + stub screen reachable from the dashboard
      (no settings content in v1).
- [ ] Persist last room states (multiplatform settings / DataStore).
- [ ] Kiosk / layout lock: edge-to-edge, exact 1280×800, page never scrolls.
- [ ] Accessibility sweep: content descriptions on every icon-only control; slider semantics.
- [ ] Full DoD pass against both reference screenshots on-device; iOS compiles.

---

## Phase 9 — Real Home Assistant (HAOS) integration

**Status:** `TODO` · **Completed:** —

Replace the mock with real device I/O behind the `HomeAdapter` seam.

- [ ] **Discuss HA instance, auth, and entity IDs with the user** at the start of this phase.
- [ ] `HomeAssistantAdapter` (WebSocket/REST) mapping entities → lights, `media_player`, climate, calendar.
- [ ] Swap `MockAdapter → HomeAssistantAdapter` in `AppContainer` (or config toggle).
- [ ] Controls actuate real devices; state reflects live HA; verified against the live instance.
