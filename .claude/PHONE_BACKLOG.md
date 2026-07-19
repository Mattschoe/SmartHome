# Phone Backlog — Smart Home Dashboard (mobile phone layout)

Phased roadmap for the **phone** layout — the compact re-flow of the tablet dashboard described in
`app/docs/mobile_phone_layout/layout_guide.md`. Each phase below is a **starting brief** for a
separate planning/build session: it states the phase's goal, points at the exact mockups and existing
code, and fixes the constraints — but deliberately leaves implementation detail open for that
session's agent to plan and build. Do phases **in order**; each builds on the last.

This is the phone-scoped sibling of `.claude/PROJECT_BACKLOG.md` (the tablet roadmap, Phases 1–9).
The tablet dashboard (`DashboardLayout.Expanded`) is built; the phone is the `DashboardLayout.Compact`
branch, currently a stub in `ui/pages/homepage/Homepage.kt` (`CompactDashboard`). **Nothing here
changes the tablet's appearance** — the tablet must keep matching its reference screenshots after
every phase.

## How to track progress (for agents)

- Each phase has a **status line** and a **checklist**. When you start a phase, set its status to
  `IN PROGRESS`. When it is finished **and** its verification gate passes, set the status to `DONE`,
  check every box, and fill in `Completed` (date + commit).
- Mark a box done by changing `- [ ]` to `- [x]`. A phase is only `DONE` when all its boxes are checked.
- Do **not** mark a phase `DONE` until its verification gate actually passes — report failures instead.
- Keep this file the single source of truth for phone status.

## Status legend

`TODO` · `IN PROGRESS` · `BLOCKED` · `DONE`

---

## Guardrails (apply to every phase)

The phone is a **re-flow, not a fork** — same domain, tokens, language, state model, and interactions
as the tablet, only rearranged (`layout_guide.md` → Common). Specifically:

- **Reuse, don't fork.** The dial, warmth control, volume slider, transport, scrubber, now-playing,
  media panel, and calendar are the *same* controls as the tablet. They currently live as `private`
  functions inside `CenterCard.kt` / `RightCard.kt`. Per the chosen **extract-as-you-go** strategy,
  each phase that needs a control **promotes it to a shared, stateless composable** (suggested home:
  `ui/controls/`) and has *both* tablet and phone consume it — never copy-paste a second version.
  A phase that touches the tablet card files must leave the tablet **pixel-identical** to its
  reference screenshots (pure refactor).
- **Orientation is the switch.** Portrait → 4 horizontally-paged single-card pages; landscape → 3
  vertically-paged two-card pages. The choice comes from the window **aspect ratio** inside the
  Compact branch (the desktop window is resizable — see CLAUDE.md).
- **State model is unchanged.** `activeLightRoom` / `activeAudioRoom` are two independent selections;
  the audio selector lists only speaker rooms (`Room.audioRooms`). Playlists are a shared library.
  No new state shapes, no new tokens — reuse `Color.kt` / `Type.kt` / `Dimensions.kt`. Danish
  `Room.displayName` (Stue/Køkken…), not the English placeholders in the mocks.
- **Paging is a pager, not routes.** Phone pages are a Compose pager; the nav host / route seam is
  **not** used for them. Taps operate controls only — there is no tap-to-navigate.
- **Nested swipes** (room-chip row, playlist rail, quick-picks grid) scroll on their own axis inside a
  page **without stealing the page-pager gesture**.
- **Deferred — grouping.** "Whole home" and "+ Create group" (vertical Page 3 mock) are the multi-room
  grouping feature deferred from v1 (CLAUDE.md CORE RULE). Render only real speaker rooms; leave the
  seam, don't wire multi-room sync.
- **Icons**: reuse the Material-Symbols glyphs already shipped for the tablet. Each phase **enumerates
  the icons it needs** and flags any genuinely new glyph (e.g. a warmth-row check mark) for the user
  to supply *before* the phase runs. Never silently substitute `Icons.*`.

### Verification gate (every phase)

There are **no phone reference screenshots** to diff against the tablet ones — the visual source of
truth is the mockup PNGs under `app/docs/mobile_phone_layout/` (`vertical/`, `horizontal/`). So each
phase verifies by:

1. **Phone build + on-device/emulator check** at a phone size (portrait ~360–412dp wide; landscape by
   rotating), compared against that phase's named mockup PNG(s) via `/android-verify`.
2. **Tablet regression** — the tablet (`Expanded`) still matches `app/docs/tablet_layout/*.png`
   (mandatory whenever a phase touched a shared/tablet file).
3. **iOS compile-only** — `cd app && ./gradlew :shared:compileKotlinIosSimulatorArm64`.
4. **Tests** where logic exists (e.g. aspect-ratio → arrangement, start-page index) — `./gradlew allTests`.

---

## Phase P1 — Compact seam + pager scaffold

**Status:** `TODO` · **Completed:** —

**Goal:** stand up the phone navigation skeleton with *no real content yet*, so paging is de-risked
before any control work. Extend the layout seam so `DashboardLayout.Compact` sub-branches into
**Portrait** and **Landscape** by aspect ratio, and build the two pagers with correct gesture axes,
page counts, indicators, and placeholder pages. Reference: `layout_guide.md` → Common + the page
tables for both orientations.

- [ ] Decide + implement the Compact sub-branch: portrait vs landscape from the window aspect ratio
      (extend `ui/layout/DashboardLayout.kt` or add a small pure, testable helper — keep the
      width→Compact/Expanded mapping intact).
- [ ] **Portrait**: a horizontal pager of **4** placeholder pages, page-dot row **bottom center**,
      **starts on page 2** (Light Control) on launch.
- [ ] **Landscape**: a vertical pager of **3** placeholder pages, indicator on the **right edge**,
      each page a two-card sage-surface frame (left + right card slots).
- [ ] Placeholders name their page (Apps / Light / Music / Media·Calendar, etc.) so paging is legible.
- [ ] Wire this into `CompactDashboard(...)` in `Homepage.kt`, replacing the "ikke designet" stub;
      pass the `Ready` state + `viewModel` through (pages fill in later phases).
- [ ] **Verify:** paging + indicators + start-page correct in both orientations; tablet unchanged;
      iOS compiles; helper unit-tested.

---

## Phase P2 — Portrait: Light Control + Apps (pages 1–2)

**Status:** `TODO` · **Completed:** —

**Goal:** build the two left-most portrait pages. Reference: `layout_guide.md` → Vertical Page 1
(`vertical/app_page.png`) and Page 2 (`vertical/homepage.png`). This is the first extract-as-you-go
phase: promote the **brightness dial** and **room-chip row** out of `CenterCard.kt` into shared
controls, and render **warmth as a full-width vertical list of rows** (phone presentation) driven by
the same `Warmth` state as the tablet's inline swatches.

- [ ] **Icons:** dial bulb / warmth dots reuse tablet glyphs; **new** — a warmth-row **check mark**
      for the selected row (request from user if not already shipped).
- [ ] **Page 2 — Light Control:** nested **horizontal** room-chip swipe (last chip cut off + fade),
      selecting a chip sets `activeLightRoom`; reused brightness dial (drag = brightness, tap bulb =
      toggle, warmth-colored); large `64%` + `Brightness · <room>` caption; **warmth as vertical rows**
      (dot + name, selected row = raised card + check), selecting recolors the dial + turns light on.
- [ ] **Page 1 — Apps:** section label `APPS`; **3-column** grid of tinted launcher tiles + captions;
      grid scrolls vertically inside the page. Tiles are illustrative (reuse the tablet's apps set).
- [ ] Nested room-chip swipe does not steal the page-pager gesture.
- [ ] Extracted dial / warmth / room-chip controls are shared; **tablet still matches its screenshot.**
- [ ] **Verify** against `vertical/homepage.png` + `vertical/app_page.png`; tablet unchanged; iOS compiles.

---

## Phase P3 — Portrait: Music / Now Playing (page 3)

**Status:** `TODO` · **Completed:** —

**Goal:** build the active-audio-room playback surface. Reference: `layout_guide.md` → Vertical Page 3
(`vertical/music_page.png`). Promote the **now-playing surface**, **transport row**, and **volume
slider** out of `RightCard.kt` / `CenterCard.kt` into shared controls; bind them to the
`activeAudioRoom`'s `RoomState` (per-room audio).

- [ ] **Icons:** shuffle / prev / play-pause / next / repeat + the level-reactive volume glyph — all
      already shipped for the tablet; confirm, don't re-request.
- [ ] Large **album-art** square (dark placeholder + waveform glyph when art absent); now-playing
      title + `artist · album`; **scrubber** with elapsed / total.
- [ ] **Transport row**: shuffle · prev · **play/pause (filled accent circle)** · next · repeat.
- [ ] **Volume slider** (speaker icon + trailing `%` readout) sets the active audio room's volume.
- [ ] `PLAYING IN` audio-room chips (speaker glyph), driving `activeAudioRoom`, **real speaker rooms
      only** — "Whole home" / "+ Create group" **deferred** (leave seam, don't wire grouping).
- [ ] Extracted controls are shared; **tablet still matches its screenshot.**
- [ ] **Verify** against `vertical/music_page.png`; tablet unchanged; iOS compiles.

---

## Phase P4 — Portrait: Media & Calendar panel (page 4)

**Status:** `TODO` · **Completed:** —

**Goal:** build the right-card mirror as a full page. Reference: `layout_guide.md` → Vertical Page 4
(`vertical/music_selection_page.png`) + the tablet Calendar (`Dashboard_with_calendar.png`) for the
Calendar tab (not shown in vertical mocks — carried over). Promote the **Media panel** and **Calendar
panel** out of `RightCard.kt` into shared controls behind the same `Panel` tab state.

- [ ] **Icons:** search, month-nav arrows, checklist/checkbox — reuse tablet glyphs; confirm.
- [ ] **Media / Calendar** segmented control (`Panel` state; Media default).
- [ ] **Media:** search field; `UP NEXT` queue rows (art + title + artist + duration); `QUICK PICKS`
      **2-column** grid of track cards; `YOUR PLAYLISTS` **horizontal rail** (nested swipe) of playlist
      cards (title + song count), reading the shared `playlists` library; page scrolls vertically.
- [ ] **Calendar** tab reuses the tablet month grid (today = accent cell) + agenda + to-do.
- [ ] Nested playlist rail / quick-picks grid don't steal the page-pager gesture.
- [ ] Extracted panels are shared; **tablet still matches its screenshots.**
- [ ] **Verify** against `vertical/music_selection_page.png` + calendar reference; tablet unchanged;
      iOS compiles.
- [ ] **Gate:** portrait phone is now fully usable end-to-end (all 4 pages) before starting landscape.

---

## Phase P5 — Landscape: three two-card pages + ROOMS overview

**Status:** `TODO` · **Completed:** —

**Goal:** compose the landscape arrangement by **reusing everything built in P2–P4**, laid out two
cards per vertically-paged screen, and build the one genuinely new surface. Reference:
`layout_guide.md` → Horizontal Pages 1–3 (`horizontal/home_page.png`, `middle_page.png`,
`bottom_page.png`).

- [ ] **Page 1 — Home:** left card = light control (room chips + dial + **warmth as a vertical list on
      the right side of the card**); right card = **`ROOMS` overview** — the new all-rooms light-summary
      composable (per-room bulb tint + name + `warmth · %` / `Off`, active row raised, tap selects the
      light room; off rooms muted, struck bulb, no %). Bind to `RoomState`.
- [ ] **Page 2 — Music:** left card = now playing (album art, title, **compact transport** — prev /
      play-pause / next only — and volume `%`); right card = media panel (search, `UP NEXT`, playlist
      rail), condensed to the card.
- [ ] **Page 3 — Utility:** left card = `APPS` grid in a **4-column** layout; right card = Calendar
      (month header + `<`/`>`, month grid with today accent, `TODAY` agenda).
- [ ] All controls come from the shared kit extracted in P2–P4 — **no new copies**; the ROOMS overview
      is the only net-new composable.
- [ ] **Verify** against all three `horizontal/*.png`; tablet unchanged; iOS compiles.

---

## Phase P6 — Polish, parity & DoD

**Status:** `TODO` · **Completed:** —

**Goal:** close the gap to a shippable phone experience across both orientations. Reference: whole of
`layout_guide.md` + the `Notes for implementers` section.

- [ ] **Orientation switching** mid-session is seamless (rotating / resizing swaps arrangements without
      losing selection state); Compact↔Expanded resize still works.
- [ ] **Nested-swipe correctness** audited on-device: room-chip row, playlist rail, quick-picks grid
      never fight the page pager.
- [ ] **Touch targets ≥ 44dp**, page indicators legible, scroll regions contained (page never
      scrolls in an unintended axis).
- [ ] **Deferred-grouping seam** left clean and documented (no dead multi-room code shipped).
- [ ] **Full DoD pass**: both orientations verified on-device against every mockup PNG; tablet still
      matches its reference screenshots; iOS compiles; `./gradlew allTests` green.

---

## Deferred (post-phone-v1)

- **Multi-room audio grouping** — "Whole home" chip + "+ Create group" (vertical Page 3). Reintroduce
  as an additive relation on top of per-room ownership (CLAUDE.md CORE RULE), shared with the tablet.
- **Phone-specific persistence** — remembering the last-viewed page per orientation, if desired
  (tablet room-state persistence is tracked separately in `PROJECT_BACKLOG.md` Phase 8).
</content>
</invoke>
