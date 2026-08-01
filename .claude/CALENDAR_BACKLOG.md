# Calendar Backlog — Smart Home Dashboard (calendar + todo backend)

Phased roadmap for making the Calendar panel **real**. The calendar/todo **UI is already built**
(PROJECT_BACKLOG Phase 7) and runs on `MockAdapter` fixtures; everything below is the **backend
half** plus the model changes the real backend forces.

This is the calendar-scoped sibling of `.claude/PROJECT_BACKLOG.md` (tablet roadmap) and
`.claude/PHONE_BACKLOG.md` (phone layout). Do phases **in order**; each builds on the last.

## Context — why this shape

This dashboard app is the household's **only** calendar client. It runs on both phones, events are
created in it, and there is no outside client (no native phone calendar app, no DAVx⁵). That removes
the interop requirement, which was the only thing a CalDAV/iCal server bought — so **there is no
separate calendar server**. The store is HA's built-in `local_calendar`, running on the HAOS box
itself, with data as plain `.ics` files swept up in HA backups.

Verified against HA source (`dev`, 2026-07-29):

- `local_calendar/calendar.py` declares `CREATE_EVENT | DELETE_EVENT | UPDATE_EVENT` and handles
  RRULE recurrence including `recurrence_id` and `Range.THIS_AND_FUTURE`. It is **strictly more
  capable than CalDAV through HA**, whose `caldav/calendar.py` declares only `CREATE_EVENT` — no
  edit, no delete. Dropping the separate server *gains* features.
- `local_todo/todo.py` declares full CRUD **plus** `SET_DUE_DATE_ON_ITEM` / `SET_DUE_DATETIME_ON_ITEM`
  / `SET_DESCRIPTION_ON_ITEM`. The HAOS-default `todo.shopping_list` has `supported_features: 15`
  (create/delete/update/move, **no due-date bit**) and structurally cannot back this panel, whose
  `TodoItem.due` is non-null. Use a Local To-do list.
- Reads: `GET /api/calendars` and `GET /api/calendars/{entity_id}?start=&end=` (both `start`/`end`
  required). There is **no WS command to list a range**; `calendar/event/subscribe` exists but pins
  its range at subscribe time, so it buys nothing over a refetch here.
- Writes: WS `calendar/event/create` / `update` / `delete`, and `todo/item/subscribe` +
  `todo.add_item` / `update_item` / `remove_item`.

**Participants/attendees are not in HA's calendar model at all** and are accepted as dropped — the
Shared/Mine/GF's calendar split already encodes whose event it is. `location` *is* a first-class
field, both directions.

---

## How to track progress (for agents)

- Each phase has a **status line** and a **checklist**. When you start a phase, set its status to
  `IN PROGRESS`. When it is finished **and** its verification gate passes, set the status to `DONE`,
  check every box, and fill in `Completed` (date + commit).
- Mark a box done by changing `- [ ]` to `- [x]`. A phase is only `DONE` when all its boxes are checked.
- Do **not** mark a phase `DONE` until its verification gate actually passes — report failures instead.
- Keep this file the single source of truth for calendar status.

## Status legend

`TODO` · `IN PROGRESS` · `BLOCKED` · `DONE`

---

## Phase C0 — HAOS setup (human, not an agent task)

**Status:** `TODO` · **Blocks:** everything below

Nothing in C1+ can be verified against live data until these exist. Matthias does these in the HA UI.

- [ ] Add one **Local Calendar** integration entry per calendar: Shared, Mine, GF's, Work
      (names are the decision to settle — they become `calendar.*` entity ids and the UI's source labels).
- [ ] Add one **Local To-do** list for the dashboard's dated todos. Leave `todo.shopping_list` alone.
- [ ] Add the girlfriend's Quinyx work roster via **Remote Calendar**, pasting the
      `webcal://app.quinyx.com/webcal/?id=…` URL **as-is** — `remote_calendar/config_flow.py` rewrites
      `webcal://` → `https://` itself. Do this **first**: if Quinyx emits ICS the integration rejects
      (`invalid_ics_file`), we want to know before building against it.
- [ ] Report back the resulting entity ids so discovery can be verified.

> That Quinyx URL is a bearer secret — anyone holding it reads her shifts. It lives in HA config;
> keep it out of git the same way `local.properties` tokens are.

---

## Guardrails (apply to every phase)

- **The CORE RULE still holds.** `displayedMonth` / `selectedDay` / `panel` are **VM-owned UI
  selection** and must never reach the adapter or `HomeState`. The adapter decides its own fetch
  window; the VM filters. `HomeScreenState.Ready` already computes `selectedDayEvents`,
  `selectedDayTodos`, and `daysWithItems` — do not move that logic into the data layer.
- **Reuse the pure logic that exists.** `DashboardLogic.kt` already has tested
  `HomeState.addTodo(id, due, label)` / `toggleTodo(id)` / `editTodo(id, label)` (blank label =
  remove) and `calendarGrid(year, month)`. The HA adapter applies these optimistically exactly as
  `MockAdapter` does — do not write a second copy.
- **Mock parity.** `MockAdapter` stays a working, fixture-seeded adapter (it is the iOS/preview path,
  selected when `ha.token` is blank). Every model change must be reflected in its fixtures.
- **Mappers are pure and unit-tested**, in the style of `HaMappingTest` / `HaDiscoveryTest`. Adapter
  I/O is not unit-tested; the mapping either side of it is.
- **Verification gate (every phase):** (1) `cd app && ./gradlew allTests`; (2) iOS compile-only —
  `./gradlew :shared:compileKotlinIosSimulatorArm64`; (3) where UI is touched, Android build +
  `/android-verify` against `app/docs/Dashboard_with_calendar.png`.

---

## Phase C1 — WebSocket dispatcher refactor

**Status:** `TODO` · **Completed:** —

**Goal:** make `HomeAssistantAdapter` able to interleave request/response and push subscriptions.
Pure refactor — no behavior change, no new features.

Today `request()` (`HomeAssistantAdapter.kt:335`) loops on `incoming` until it sees its own id, so it
only works **during setup**; once the read loop at `:315` owns the channel there is no way to issue a
command and await its reply. Both `todo/item/subscribe` and every calendar write need exactly that.

- [ ] Mirror the pattern already proven in `MusicAssistantAdapter.kt` (`:100–167`): a `pendingMutex` +
      `pending: MutableMap<Int, CompletableDeferred<JsonObject>>`, a `readLoop()` launched as its own
      coroutine inside `coroutineScope { }`, and `runSession()` ending on `reader.join()`.
- [ ] Route incoming frames by `id`: `type: "result"` completes a pending deferred; `type: "event"`
      goes to a **subscription handler registered per id** (today `handleEvent` assumes every event is
      the one `state_changed` stream and ignores `id` entirely — that breaks the moment a second
      subscription exists).
- [ ] Fold the existing setup calls (`config/*_registry/list`, `get_states`, `subscribe_events`) onto
      the new dispatcher; `failPending()` on disconnect as MA does.
- [ ] Fix the latent `nextId` race while here: `callService` (`:359`) increments it from a launched
      coroutine while the connection coroutine also does — guard id minting with the same mutex.
- [ ] **Verify:** lights/media still behave identically on device; reconnect still works; `allTests`
      green; iOS compiles.

---

## Phase C2 — Calendar reads (multi-source, mixed writability)

**Status:** `TODO` · **Completed:** —

**Goal:** real events on the month grid and agenda, from N calendars of differing writability.

**The load-bearing model change.** Calendars are **not uniformly writable**: four read/write Local
Calendars plus at least one read-only subscription (Quinyx). The add/edit UI must only ever target
writable ones, so writability has to be in the model rather than assumed.

- [ ] Add to `data/model/DashboardModels.kt`: a `CalendarSource(id, displayName, canWrite)` and
      `CalendarState.sources: List<CalendarSource>`; give `CalendarEvent` a **source id**. Discover
      `canWrite` from each entity's `supported_features` attribute (`CREATE_EVENT` bit) — do **not**
      hardcode a list of entity ids.
- [ ] Discover calendar entities from the `get_states` snapshot already taken in `runSession()`
      (`entity_id` prefix `calendar.`, `friendly_name` for the label), alongside the existing
      `discoverRoomEntities` step in `data/ha/HaDiscovery.kt`.
- [ ] Fetch events over **REST**, not WS: `GET /api/calendars/{entity_id}?start=&end=` with the
      existing `HaConfig.httpBase` and a bearer header. The `HttpClient` at `:80` currently installs
      only `WebSockets` — plain `get` needs no extra plugin, but confirm the engine handles it on all
      three targets.
- [ ] **Rolling window, not the displayed month** — fetch `today − 1 month` … `today + 12 months` and
      let the VM filter. A household calendar is tiny; a generous window sidesteps month-navigation
      entirely and keeps UI selection out of the adapter. Refetch on: connect, month rollover, after
      any write (C4), and on a slow periodic timer.
- [ ] New pure mapper (suggested `data/ha/HaCalendarMapping.kt` + `HaCalendarDtos.kt` following the
      `HaDtos.kt` snake_case/`ignoreUnknownKeys` conventions) handling the shapes the current model
      does not: **all-day events** arrive as `date` rather than `dateTime` (render as "Hele dagen" in
      the preformatted `CalendarEvent.time`), and **multi-day events** must be expanded to one
      `CalendarEvent` per day or they vanish from `daysWithItems`.
- [ ] Date/time formatting belongs in a `data/CalendarFormat.kt`, matching the existing
      `ClockFormat.kt` / `MediaFormat.kt` / `ClimateFormat.kt` pattern — pure and unit-tested.
- [ ] Weave the result into `rebuild()` (`:381`), replacing the hardcoded
      `CalendarState(events = emptyList(), todos = emptyList())` at `:420` **and** the one in
      `blankHome()` at `:501`. Calendar data lives in an adapter field that `rebuild()` reads, the
      same way `entityStates` does.
- [ ] Confirm `CompositeHomeAdapter` passes `calendar` through from `ha` unchanged (it already
      forwards the todo intents at `:72–74`).
- [ ] **Agenda dots by source, not by row.** `AgendaRow` currently colors with `browseCardColor(index)`
      (`RightCard.kt:650`) — key it off the event's calendar instead, so his/hers/work read apart.
- [ ] **Quinyx staleness:** `remote_calendar`'s coordinator is `SCAN_INTERVAL = timedelta(days=1)` —
      a once-daily poll, far too stale for a work roster. Call `homeassistant.update_entity` on
      read-only sources to force a coordinator refresh (it is coordinator-backed, so this triggers
      `async_request_refresh`), when the Calendar panel opens or on month navigation.
- [ ] Update `MockAdapter` fixtures for the new `sources` + per-event source, keeping several
      distinct sources so the dot colors are exercised in previews.
- [ ] **Verify:** real events on grid + agenda, dots on the right days, all-day and multi-day render
      sanely, per-source colors distinct; `allTests`; iOS compiles.

---

## Phase C3 — Todos (fill the three no-ops)

**Status:** `TODO` · **Completed:** —

**Goal:** the checklist writes to a real Local To-do list and reflects changes from the other phone.

`HomeAssistantAdapter.addTodo` / `toggleTodo` / `editTodo` (`:237–239`) are `{}` no-ops today.

- [ ] Subscribe to `todo/item/subscribe` (entity_id) over the C1 dispatcher; it pushes
      `{"items": [...]}` with the full list on every change — no polling, no diffing.
- [ ] Map HA items → `TodoItem`: `uid` → `id`, `summary` → `label`, `status` (`needs_action` /
      `completed`) → `done`, `due` → `due`. Items with **no due date** need a decision — the model's
      `due` is non-null; simplest is to bucket them onto today rather than drop them silently.
- [ ] Writes via `call_service`: `todo.add_item` (`item` + `due_date`), `todo.update_item`
      (`item` = uid, plus `rename` / `status`), `todo.remove_item` (`item` takes a **list**).
      Note `update_item` matches by uid **or** summary — always pass uid.
- [ ] Apply optimistically through the existing `DashboardLogic` functions before the service call,
      exactly as `MockAdapter` does (`MockAdapter.kt:98–103`), so the checkbox does not lag the tap.
      Blank-label-on-edit maps to `todo.remove_item` — the delete escape hatch the UI already has.
- [ ] The adapter mints the id on add (per the `HomeAdapter` contract at `:94–98`); HA's echoed `uid`
      then re-keys the row. `TodoSection` is already `key(todo.id)`'d (`RightCard.kt:677`) so this
      re-keys rather than rebuilds.
- [ ] **Verify:** add/toggle/edit/delete round-trip to HA; a change made on the other phone appears
      live; `allTests`; iOS compiles.

---

## Phase C4 — Event writes (create / update / delete)

**Status:** `TODO` · **Completed:** —

**Goal:** the plumbing that lets the tablet be where events are created. **UI is deliberately out of
scope here** — this phase lands the adapter intents and the model, and the create/edit surface is
designed separately.

- [ ] Extend `HomeAdapter` with calendar write intents (create / update / delete), taking a target
      **source id** — and rejecting non-writable sources. Follow the existing intent conventions:
      fire-and-forget unless the caller watches a spinner, in which case `suspend` + propagate.
- [ ] Implement over WS: `calendar/event/create` / `update` / `delete`. Update and delete take `uid`
      plus optional `recurrence_id` / `recurrence_range` — carry `uid` on `CalendarEvent` so a
      recurring instance can be addressed. Support `location` (first-class); skip attendees.
- [ ] Refetch that source's window after a successful write (the REST window is not push-updated).
- [ ] `MockAdapter` implements the same intents against its fixtures so previews and iOS stay usable.
- [ ] **Verify:** an event created from the app appears in HA and on the other phone; editing a single
      occurrence of a recurring event does not rewrite the series; `allTests`; iOS compiles.

---

## Phase C5 — Offline read cache (resilience)

**Status:** `TODO` · **Completed:** —

**Goal:** retire the single point of failure this architecture introduces.

App-only means neither phone holds a synced copy — unlike a native CalDAV client, an HAOS outage or
rebuild leaves **both people with no calendar at all**, not even read-only. Worth fixing before this
becomes the household's only calendar in practice, rather than discovering it during an outage.

- [ ] Persist the last-fetched event + todo window locally (multiplatform settings/DataStore — the
      same "persist so a wall tablet survives reloads" seam CLAUDE.md already anticipates).
- [ ] Render from cache when the adapter has no connection, with a clear stale/offline indication.
- [ ] Fix the related staleness bug in `HomepageViewModel.kt:72`: `today` is captured **once** at VM
      construction, so a wall-mounted tablet left running rolls past midnight still highlighting
      yesterday. Derive it from the same ticking clock the left card uses.
- [ ] **Verify:** kill the HA connection and confirm the calendar still renders, marked stale.

---

## Later — offsite backup to the VPS (not phase 1)

Matthias has a VPS that would make a good **offsite backup target for HA backups**. Deliberately
*not* sequenced into the phases above — it is infrastructure, not app work, and nothing here depends
on it. Its real value is that it retires the C5 risk from the other end: the calendar's `.ics` files
ride along inside HA's normal backups, so an offsite copy means the household calendar survives the
**box** dying, not just a bad config edit.

Design when we get there:

- **Put the VPS on the tailnet** rather than exposing anything publicly. Tailscale is **already set
  up** on the HAOS box, so HA can reach the VPS with no ports forwarded, no reverse proxy, and no
  certificate management.
- Confirm the mechanism at the time rather than guessing now — HA's native backup system supports
  configurable backup locations, and there are add-on routes (rclone / SSH / network storage). Which
  is cleanest depends on the HA version running then.
- Sequence it **after** C5. The offline cache protects the daily experience; the offsite backup
  protects the data. The cache is the one users feel.

---

## Related, out of scope

- **"Weekly calendar view"** — the one calendar item in `PROJECT_BACKLOG.md` Phase 9. A UI feature
  on top of this data layer; do it after C2 lands real events.
- **Event create/edit UI** — the surface for C4's intents, designed separately.
- **Phone layout** — `PHONE_BACKLOG.md` Phase P4 reuses the tablet Calendar panel as-is; these model
  changes reach it for free, but nothing here should fork the panel.
