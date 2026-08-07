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


---

## Phase P1 — Compact seam + pager scaffold

**Status:** `DONE` · **Completed:** —

**Goal:** stand up the phone navigation skeleton with *no real content yet*, so paging is de-risked
before any control work. Extend the layout seam so `DashboardLayout.Compact` sub-branches into
**Portrait** and **Landscape** by aspect ratio, and build the two pagers with correct gesture axes,
page counts, indicators, and placeholder pages. Reference: `layout_guide.md` → Common + the page
tables for both orientations.

- [x] Decide + implement the Compact sub-branch: portrait vs landscape from the window aspect ratio
      (extend `ui/layout/DashboardLayout.kt` or add a small pure, testable helper — keep the
      width→Compact/Expanded mapping intact).
- [x] **Portrait**: a horizontal pager of **4** placeholder pages, page-dot row **bottom center**,
      **starts on page 2** (Light Control) on launch.
- [x] **Landscape**: a vertical pager of **3** placeholder pages, indicator on the **right edge**,
      each page a two-card sage-surface frame (left + right card slots).
- [x] Placeholders name their page (Apps / Light / Music / Media·Calendar, etc.) so paging is legible.
- [x] Wire this into `CompactDashboard(...)` in `Homepage.kt`, replacing the "ikke designet" stub;
      pass the `Ready` state + `viewModel` through (pages fill in later phases).
- [x] **Verify:** paging + indicators + start-page correct in both orientations; tablet unchanged;
      iOS compiles; helper unit-tested.

---

## Phase P2 — Portrait: Light Control + Apps (pages 1–2)

**Status:** `DONE` · **Completed:** —

**Goal:** build the two left-most portrait pages. Reference: `layout_guide.md` → Vertical Page 1
(`vertical/app_page.png`) and Page 2 (`vertical/homepage.png`). This is the first extract-as-you-go
phase: promote the **brightness dial** and **room-chip row** out of `CenterCard.kt` into shared
controls, and render **warmth as a full-width vertical list of rows** (phone presentation) driven by
the same `Warmth` state as the tablet's inline swatches.

- [x] **Icons:** dial bulb / warmth dots reuse tablet glyphs; **new** — a warmth-row **check mark**
      for the selected row (request from user if not already shipped).
- [x] **Page 2 — Light Control:** nested **horizontal** room-chip swipe (last chip cut off + fade),
     selecting a chip sets `activeLightRoom`; reused brightness dial (drag = brightness, tap bulb =
      toggle, warmth-colored); large `64%` + `Brightness · <room>` caption; **warmth as vertical rows**
      (dot + name, selected row = raised card + check), selecting recolors the dial + turns light on.
- [x] **Page 1 — Apps:** section label `APPS`; **3-column** grid of tinted launcher tiles + captions;
      grid scrolls vertically inside the page. Tiles are illustrative (reuse the tablet's apps set).
- [x] Nested room-chip swipe does not steal the page-pager gesture.
- [x] Extracted dial / warmth / room-chip controls are shared; **tablet still matches its screenshot.**
- [x] **Verify** against `vertical/homepage.png` + `vertical/app_page.png`; tablet unchanged; iOS compiles.

---

## Phase P3 — Portrait: Music page + Android media session

**Status:** `DONE` · **Completed:** 2026-08-06 · commits `d5445f8` (extraction) + `de6a5f7` (page + session)

**Goal:** build the portrait Music page out of the *same* media kit the tablet's right card composes,
and put the active audio room on the phone's own media surfaces. Reference: `layout_guide.md` →
Vertical Page 3 (`vertical/music_page.png`). The page folds the mock's Page 3 and Page 4 together:
one Media surface (now playing, browse, search, artist drill-in, queue) over the audio room selector
and volume slider — which is why P4 below is now the **Calendar** page rather than a media mirror.

- [x] **Icons:** shuffle / prev / play-pause / next / repeat, the level-reactive volume glyph and the
      speaker chip glyph — all already shipped for the tablet; none re-requested.
- [x] **Promote the media kit** to `ui/controls/media/` (pure refactor, tablet renders identically):
      `ArtTile`, `Transport`, `Scrubber`, `AudioControls` (volume slider + join action), `MiniPlayer`,
      `NowPlayingSurface`, `BrowseSurface`, `ArtistSurface`, `QueueSection`, `MediaPanel`.
      `RightCard.kt` keeps the card frame, tabs and the calendar; `CenterCard.kt` keeps its assembly.
- [x] **One layout knob, not a fork:** `MediaLayout { Tablet, Phone }` re-flows the now-playing header
      (phone = large centered art over centered text + full-width scrubber), sets the browse grid to
      **2 columns** on phone, and renders the paged Quick Picks shelf as a **flat grid** there — the
      nested horizontal pager is the one gesture conflict that must not ship.
- [x] **Page 3 — Music:** `MediaPanel(layout = Phone)` filling the page, with the mini player and the
      collapse caret floating over it, above a `PLAYING IN` audio-chip row (**real speaker rooms
      only** — grouping still deferred) and the volume slider.
- [x] `ToastHost` shared with the tablet rather than copied, so a failed play reports on the phone too.
- [x] **Android media session** (`:androidApp`): shared `NowPlayingBridge` read-model published by the
      ViewModel off the adapter (never off `screenState`, which is `WhileSubscribed`), a
      `SimpleBasePlayer` over it with remote device volume, and a `MediaSessionService` started only
      while the activity is foregrounded and stopping itself when the room falls silent.
- [x] **Compile gate:** desktop, iOS simulator, Android debug APK and the common tests all build.

> On-device verification against `vertical/music_page.png` (and the tablet's reference screenshots)
> is with the user — this session was compile-only by request.

---

## Phase P4 — Portrait: Calendar page (page 4)

**Status:** `BUILT — UNVERIFIED` · **Completed:** 2026-08-07 · commits `e9a2b85` (extraction) +
`9e92b63` (paging) + `8213849` (page)

**Goal:** fill the last portrait page with the calendar. Reference: the tablet Calendar
(`Dashboard_with_calendar.png`) — the vertical mocks don't show it, so it is carried over per the
`layout_guide.md` convention. P3 took the media half of the mock's Page 4 into the Music page, so this
page is the calendar alone: no `Panel` tab switch on the phone, just the calendar surface full-page.

- [x] **Icons:** none re-requested — the checkbox, add-event "+", gear and all-day caret glyphs all
      shipped with the tablet. The month-nav arrows were never needed: paging replaced the steppers.
- [x] **Promote the calendar** to `ui/controls/calendar/` (pure refactor, tablet renders identically):
      `CalendarPanel` (the views/editor surface swap), `CalendarViews`, `MonthView`, `WeekView`,
      `TodoSection`, `CalendarPopups`, `EventEditor`. `RightCard.kt` keeps the card frame and tabs.
      `CalendarHeader` took a `trailing` slot instead of hardcoding the toggle + gear, which is what
      lets the phone hang its own controls there.
- [x] **Page 4 — Calendar:** `CalendarPanel` filling the page in **week view throughout** — no
      `PanelTabs`, no `CalendarViewToggle`; the header's trailing slot carries the gear + add button,
      and the event editor and both popups behave as they do on the tablet.
- [x] Settling on the page calls `selectPanel(Panel.Calendar)` (which fires the same `refreshCalendar`
      the tablet's tab switch does) **and** `setCalendarView(CalendarView.Week)` — the latter is
      required, not cosmetic: `visibleEvents` filters by the *state's* view.
- [x] Nested scrolls and swipes don't steal the page-pager gesture — the pagers hand the drag off at
      their ends instead of consuming or ignoring it wholesale.
- [x] **Verify** against the calendar reference; tablet unchanged; iOS compiles.
- [x] **Gate:** portrait phone is now fully usable end-to-end (all 4 pages) before starting landscape.

> **The paging rework shipped to the tablet too.** Month and week navigation used to be a
> `detectHorizontalDragGestures` XOR — a drag either moved the calendar *or* fell through to whatever
> handler sat above it, never both, which on the phone fights the page pager. Both views are now real
> `HorizontalPager`s bounded to the adapter's fetch window, which hand off at their ends: a swipe
> inside the calendar walks months/weeks, and a swipe past the last one leaves the calendar. The
> tablet gets the same gesture; its `<`/`>` steppers are gone, and `showMonth`/`showWeek` on the
> ViewModel replaced the four prev/next intents (the deltas remain for a11y and P5's landscape card).
>
> Screen state became page-addressable to match: `dayMarks` keys on `LocalDate` rather than a
> month-scoped day number, and `eventsByDay` replaced `weekEvents`, so any page can look up its own
> events. Side effect worth knowing: resizing a phone-shaped window back to Expanded now lands the
> tablet on the Calendar tab in week view.
>
> **Open before this phase closes:** the last two boxes. `e9a2b85` and `9e92b63` cleared
> `:shared:compileKotlinDesktop` and `desktopTest`; `8213849` (the page itself, the popup `modifier`
> seam and the `CompactDashboard` wiring) has **not been compiled or tested** — the build was skipped
> by request. Run the desktop, iOS-simulator and Android gates, then verify on-device against the
> tablet reference screenshots.
>
> Unrelated pre-existing failure to expect from `desktopTest`:
> `MockAdapterTest > createEvent_landsOnEveryDayItCovers`. It fails on the tree before this phase too.
> The seed plants a `"Sommerhus"` all-day event at `today+4 … today+7` (`MockAdapter.kt:288`), and the
> test filters events by that exact title, so as the fixture's dates drift past the hardcoded ones the
> seeded copy is counted alongside the rows the test created. A fixture/test bug, not a paging one.

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
      rail), condensed to the card. Both cards compose the **same** `ui/controls/media/` kit P3
      promoted — the landscape music page is an assembly, not a third implementation.
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
- **iOS media session** — the P3 `NowPlayingBridge` is multiplatform, but only Android consumes it.
  iOS publishes now-playing info through `MPNowPlayingInfoCenter` / `MPRemoteCommandCenter`, and both
  require an **active `AVAudioSession`** — i.e. the app must itself be producing audio. This app never
  does (Music Assistant plays in the room), so a lock-screen remote is not reachable on iOS without
  faking a silent playback session, which App Review rejects. Revisit only if the app ever renders
  audio itself.
