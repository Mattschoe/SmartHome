This is a guide that explains the UX experience of the `mobile_phone_layout`.
For UI see the images (with semantic naming) in this folder (`vertical/` and `horizontal/`).

The phone layout is a **re-flow of the same tablet dashboard** (see the tablet reference
screenshots in `app/docs/`) onto a phone. The domain, tokens, language, and interactions are
identical to the tablet — only the *arrangement* changes. The tablet's three floating cards
(LEFT light, CENTER audio, RIGHT media/calendar + the LEFT card's apps/climate) are split across
multiple swipeable pages.

# Common
- Both orientations are navigated by **swipes**, like a normal iOS app. There is **no** navigation to
  new pages via taps in phone view — taps only operate controls (chips, swatches, transport, dial,
  sliders). The nav host / route seam is not used for these pages; paging is a pager, not routes.
- A **page indicator** shows position and page count. Its placement tells you the swipe axis:
  vertical layout = a horizontal dot row at the **bottom center**; horizontal layout = an indicator
  on the **right edge**.
- **Conventions carry over from the tablet.** Anything the tablet spec establishes but a mockup
  doesn't happen to show still applies here: Danish room names (Stue/Køkken…) shown in mockups as
  English are placeholders — use the tablet's `Room.displayName`; the fixed Forest accent, warmth
  palette, section-label styling, Newsreader type, card/pill shadows, and the dial/slider math are
  all reused verbatim. Do not invent new tokens or behaviors for phone.
- **State model is unchanged.** Light room and audio room are still two independent selections
  (`activeLightRoom` / `activeAudioRoom`); the audio room selector lists only speaker rooms.
  Playlists are a shared library. See CLAUDE.md → State Model (CORE RULE).

---

# Vertical (portrait)

Four pages, swiped **horizontally** (left/right). Page-dot row sits at the **bottom center** (4 dots).
The app **starts on the Light Control page** (dot 2 is active on launch), so the pager opens in the
middle: swipe left → Apps, swipe right → Music → Media/Calendar.

Page order (left → right):

| # | Page | File |
|---|------|------|
| 1 | Apps grid | `vertical/app_page.png` |
| 2 | **Light Control (start page)** | `vertical/homepage.png` |
| 3 | Music / Now Playing | `vertical/music_page.png` |
| 4 | Media & Calendar panel | `vertical/music_selection_page.png` |

## Page 1 — Apps (`app_page.png`)
- Section label `APPS` at top, under the status bar.
- **3-column** grid of app tiles; each tile is a rounded-square tinted icon with a caption beneath
  (Budget, Alarms, Timers, To-do, Cameras, Climate, Locks, Energy, Scenes, Vacuum, Weather, Shopping,
  Notes, Wi-Fi, Plants, Guests, Blinds, Doorbell).
- The grid **scrolls vertically** inside the page if it overflows. Tiles are illustrative launchers.

## Page 2 — Light Control (`homepage.png`) — start page
- **Room chip row** across the top (Living room active, Kitchen, Bedroom, Bathroom…). This row is a
  **nested horizontal swipe** inside the page. The last visible chip is cut off at the right edge with
  a small fade/shadow to signal there is more to scroll. Selecting a chip sets `activeLightRoom`.
- **Brightness dial** — the same half-arc dial as the tablet (drag knob = brightness, tap center bulb
  = toggle). Arc/knob/bulb take the current warmth color. Below it: large `64%` and
  `Brightness · <room>` caption.
- `WARMTH` section — on phone the five warmth options are a **full-width vertical list of rows**
  (not the tablet's inline swatch circles): each row = a color-temp dot + name (Candle, Warm, Soft,
  Neutral, Cool). The selected row is drawn as a raised white/highlighted card with a **check mark**
  on the right (Warm is selected in the mock). Selecting a row recolors the dial and turns the light
  on. The list scrolls with the page.

## Page 3 — Music / Now Playing (`music_page.png`)
This is the **active audio room's** playback surface (per-room audio).
- Large **album-art** square (dark placeholder with a waveform glyph when art is absent).
- **Now-playing** title + `artist · album` subtitle (Midnight City / M83 · Hurry Up, We're Dreaming).
- **Scrubber** with elapsed / total times (1:52 / 4:03).
- **Transport row**: shuffle · previous · **play/pause** (large filled accent circle) · next · repeat.
- **Volume slider** with a speaker icon and a trailing `35%` readout — sets the active audio room's
  volume.
- `PLAYING IN` section — audio-room chips, each with a **speaker glyph**: Living room (active),
  Kitchen, Bedroom, **Whole home**, plus a **`+ Create group`** pill.
  > ⚠️ **Grouping is deferred in v1.** "Whole home" and "Create group" are the multi-room grouping
  > feature that CLAUDE.md defers from v1 (audio is strictly per-room). These appear in the mock as a
  > *future* target. For v1, render only the real speaker rooms (`Room.audioRooms`); treat Whole
  > home / Create group as not-yet-built. Do not let them collapse the two independent selectors.

## Page 4 — Media & Calendar panel (`music_selection_page.png`)
Mirrors the tablet's RIGHT card.
- **Media / Calendar** pill segmented control at top (Media active in the mock).
- **Search** field ("Search songs, artists, podcasts").
- `UP NEXT` — queue list, each row = art thumb + title + artist + duration (Instant Crush / Daft Punk
  5:37, Redbone / Childish Gambino 5:26, Nightcall / Kavinsky 4:18).
- `QUICK PICKS` — **2-column** grid of track cards (art thumb + title + artist): Midnight City,
  Holocene, Ivy, The Less I Know, Alaska, Weird Fishes.
- `YOUR PLAYLISTS` — **horizontal rail** of large playlist cards with a title + song count
  (Focus Flow · 42 songs, Evening Jazz · 28 songs, Indie Mix · 63 songs). The rail is a nested
  horizontal swipe; the page itself scrolls vertically.
- The **Calendar** tab (not shown in vertical mocks) reuses the tablet calendar — month grid + agenda,
  per the carried-over convention.

---

# Horizontal (landscape)

Three pages, swiped **vertically** (up/down). The page indicator sits on the **right edge**. Each page
shows **two cards side by side** (a left card + a right card), echoing the tablet's floating-card look
on the sage surface.

Page order (top → bottom):

| # | Page | Left card | Right card | File |
|---|------|-----------|-----------|------|
| 1 | Home | Light control | Rooms overview | `horizontal/home_page.png` |
| 2 | Music | Now playing | Media panel | `horizontal/middle_page.png` |
| 3 | Utility | Apps grid | Calendar | `horizontal/bottom_page.png` |

## Page 1 — Home (`home_page.png`)
- **Left card — Light control**: room chip row along the top (Living room active, Kitchen, Bedroom,
  Bathroom); brightness dial + `64%`; `WARMTH` options as a **vertical list on the right side of the
  card** (Candle, Warm selected as a pill, Soft, Neutral, Cool). Same dial/warmth semantics as
  vertical Page 2.
- **Right card — `ROOMS` overview** *(new to phone; not present on tablet or vertical)*: a list of all
  rooms with their **light** state at a glance. Each row = a warmth-tinted bulb icon + room name +
  a secondary line (Living room · Warm light · 64%, Kitchen · Soft light · 85%, Bedroom · Candle
  light · 22%, Bathroom · Light off · Off). The active/selected room (Living room) is drawn as a
  raised white row. Rows reflect `RoomState` (brightness, on/off, warmth); tapping a row selects that
  light room. "Off" rooms are muted with a struck-through/off bulb glyph and no percentage.

## Page 2 — Music (`middle_page.png`)
- **Left card — Now playing**: album art, title + `artist · album`, **transport** (previous ·
  play/pause · next — note the compact set omits shuffle/repeat vs. the vertical page), and the
  **volume slider** with `35%`. This is the active audio room's session.
- **Right card — Media panel**: search field, `UP NEXT` queue (Instant Crush, Redbone, Nightcall),
  and a `YOUR PLAYLISTS` horizontal rail of playlist cards. Same content as vertical Page 4's Media
  tab, condensed to fit the card.

## Page 3 — Utility (`bottom_page.png`)
- **Left card — `APPS` grid**: same launcher tiles as vertical Page 1, in a **4-column** grid to suit
  the wider card (Budget, Alarms, Timers, To-do, Cameras, Climate, Locks, Energy, Scenes, Vacuum,
  Weather, Shopping, Notes, Wi-Fi, Plants, Guests, Blinds, Doorbell). Scrolls if it overflows.
- **Right card — Calendar**: month header with `<` / `>` steppers (July 2026), a full month grid
  (S M T W T F S), **today** highlighted as a filled Forest-accent cell (the 17th), and a `TODAY`
  agenda beneath it (colored dot + time + title: 8:00 AM Team standup, 12:30 PM Lunch with Sam).

---

# Notes for implementers
- **Reuse, don't fork.** These pages recompose the *same* components and state as the tablet. Build
  the shared controls (dial, warmth list/swatches, volume slider, transport, media/calendar,
  room-overview row) once and lay them out per orientation.
- **Orientation is the switch.** Portrait → 4 horizontally-paged single-card pages; landscape → 3
  vertically-paged two-card pages. Choose the arrangement from the window aspect ratio (the desktop
  window is resizable too — see CLAUDE.md).
- **Nested swipes** (room-chip row, playlist rail, quick-picks grid) live inside a page and scroll on
  their own axis without stealing the page-pager gesture.
- **New surface:** the horizontal `ROOMS` overview (Page 1 right card) is the only element with no
  tablet/vertical equivalent — it needs a small all-rooms light-summary composable.
- **Deferred:** "Whole home" / "Create group" grouping (vertical Page 3) stays out of v1 per the
  CORE RULE; leave the seam but don't wire multi-room sync.
