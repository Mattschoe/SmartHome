package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.ArtistDetail
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.CalendarState
import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.data.model.ReminderRules
import com.mattschoe.smarthome.data.model.ClimateState
import com.mattschoe.smarthome.data.model.HomeState
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.QueueMode
import com.mattschoe.smarthome.data.model.RecurrenceRange
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.data.model.Warmth
import com.mattschoe.smarthome.data.model.WeatherCondition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * In-memory [HomeAdapter] seeded from [seedHome]. Controls mutate the store optimistically by
 * applying the matching pure transition from `DashboardLogic`.
 */
class MockAdapter(
    initial: HomeState = seedHome(),
) :  HomeAdapter {
    private val _state = MutableStateFlow(initial)

    override fun subscribe(): StateFlow<HomeState> = _state.asStateFlow()

    override fun setBrightness(room: Room, value: Int) = _state.update { it.withBrightness(room, value) }
    override fun setWarmth(room: Room, warmth: Warmth) = _state.update { it.withWarmth(room, warmth) }
    override fun setVolume(room: Room, value: Int) = _state.update { it.withVolume(room, value) }
    override fun toggleLight(room: Room) = _state.update { it.toggleLight(room) }

    // No real music backend — playing a browse tile promotes it to now-playing in the store, so the
    // pending-play flow (tap → loading surface → playing) is exercisable end to end without a server.
    override suspend fun play(room: Room, uri: String, radio: Boolean) {
        _state.update { home ->
            val item = home.allShelfItems().firstOrNull { it.uri == uri }
            if (item == null) home else home.playBrowseItem(room, item)
        }
    }

    // Queueing resolves the uri to a seeded tile the same way [play] does, then hands off to the pure
    // transition — so the two long-press actions are exercisable end to end without a server.
    override suspend fun enqueue(room: Room, uri: String, mode: QueueMode) {
        _state.update { home ->
            val item = home.allShelfItems().firstOrNull { it.uri == uri }
            if (item == null) home else home.enqueueBrowseItem(room, item, mode)
        }
    }

    // There is no queue to build either, so a multi-uri play just starts the head — enough for the
    // artist surface's "play from here" to land on a now-playing surface without a server.
    override suspend fun playAll(room: Room, uris: List<String>) {
        uris.firstOrNull()?.let { play(room, it, radio = false) }
    }

    // No provider to drill into either, so the artist surface is furnished from the seeded shelves —
    // enough to render and exercise it on the desktop/iOS preview path without a server.
    override suspend fun artistDetail(uri: String): ArtistDetail {
        val home = _state.value
        return ArtistDetail(topTracks = home.quickPicks, albums = home.playlists)
    }

    // Queue intents do work against the in-memory queue (fixtures have no MA handle, so the title
    // stands in), which keeps tap-to-skip and drag-to-reorder exercisable without a server.
    override suspend fun playQueueItem(room: Room, queueItemId: String) =
        _state.update { it.playQueueItem(room, queueItemId) }
    override fun moveQueueItem(room: Room, queueItemId: String, posShift: Int) =
        _state.update { it.moveQueueItem(room, queueItemId, posShift) }

    // No provider to query, so search filters the seeded shelves by name — enough to exercise the
    // search UI end to end on the desktop target without a Music Assistant server. Both sources are
    // searched: the toggle scopes browsing only, never search.
    override suspend fun search(query: String): List<BrowseItem> {
        val home = _state.value
        return home.allShelfItems()
            .filter { it.name.contains(query, ignoreCase = true) }
            .distinctBy { it.uri }
    }

    override fun togglePlay(room: Room) = _state.update { it.togglePlay(room) }
    override fun next(room: Room) = _state.update { it.next(room) }
    override fun previous(room: Room) = _state.update { it.previous(room) }
    override fun seek(room: Room, positionSec: Int) = _state.update { it.seek(room, positionSec) }
    override fun setShuffle(room: Room, shuffle: Boolean) = _state.update { it.setShuffle(room, shuffle) }
    override fun setRepeat(room: Room, mode: RepeatMode) = _state.update { it.setRepeat(room, mode) }

    // Grouping mutates the in-memory store like every other control, so the join/leave action is
    // exercisable end to end on the desktop preview path without a server.
    override fun joinAudio(leader: Room, follower: Room) = _state.update { it.joinAudio(leader, follower) }
    override fun unjoinAudio(room: Room) = _state.update { it.unjoinAudio(room) }

    // The adapter owns id minting (the real backend assigns HA `uid`s); logic transitions take the id.
    @OptIn(ExperimentalUuidApi::class)
    override fun addTodo(due: LocalDate, label: String) {
        val id = Uuid.random().toString()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        _state.update { it.addTodo(id, due, label, createdOn = today) }
    }
    override fun toggleTodo(id: String) =
        _state.update { it.toggleTodo(id, Clock.System.todayIn(TimeZone.currentSystemDefault())) }
    override fun editTodo(id: String, label: String) = _state.update { it.editTodo(id, label) }

    // The fixtures are already "fetched" — there is no window to re-request.
    override fun refreshCalendar() = Unit

    // Calendar writes mutate the in-memory store like every other control, so an event-editing
    // surface is exercisable end to end on the desktop/iOS preview path without a server. The
    // writability check is the real one: it is what such a surface has to respect.
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createEvent(sourceId: String, draft: CalendarEventDraft) {
        requireWritable(sourceId)
        val uid = Uuid.random().toString()
        _state.update { it.withEvents(it.calendar.events + draft.expand(sourceId, uid)) }
    }

    override suspend fun updateEvent(
        sourceId: String,
        uid: String,
        draft: CalendarEventDraft,
        recurrenceId: String?,
        range: RecurrenceRange,
    ) {
        requireWritable(sourceId)
        _state.update { home ->
            home.withEvents(home.calendar.events.filterNot { it.uid == uid } + draft.expand(sourceId, uid))
        }
    }

    override suspend fun deleteEvent(
        sourceId: String,
        uid: String,
        recurrenceId: String?,
        range: RecurrenceRange,
    ) {
        requireWritable(sourceId)
        _state.update { home -> home.withEvents(home.calendar.events.filterNot { it.uid == uid }) }
    }

    // Reminder rules live in the same in-memory store, so the picker, the detail popup and the gear's
    // per-calendar default are all exercisable on the desktop preview path. There is nothing to arm
    // there — the alarm scheduler is null off Android — so this is the UI's half alone.
    override suspend fun setEventReminder(
        sourceId: String,
        uid: String,
        recurrenceId: String?,
        rule: ReminderRule?,
    ) {
        val key = reminderKey(sourceId, uid, recurrenceId)
        _state.update { home ->
            home.withReminders { rules ->
                if (rule == null) rules.copy(byEvent = rules.byEvent - key)
                else rules.copy(byEvent = rules.byEvent + (key to rule))
            }
        }
    }

    override suspend fun setCalendarReminderDefault(sourceId: String, offsetMin: Int?) {
        _state.update { home ->
            home.withReminders { rules ->
                if (offsetMin == null) rules.copy(byCalendar = rules.byCalendar - sourceId)
                else rules.copy(byCalendar = rules.byCalendar + (sourceId to offsetMin))
            }
        }
    }

    private fun HomeState.withReminders(transform: (ReminderRules) -> ReminderRules): HomeState =
        copy(calendar = calendar.copy(reminders = transform(calendar.reminders)))

    private fun requireWritable(sourceId: String) {
        val source = _state.value.calendar.sources.firstOrNull { it.id == sourceId }
            ?: throw IllegalArgumentException("unknown calendar '$sourceId'")
        require(source.canWrite) { "calendar '${source.displayName}' is read-only" }
    }

    private fun CalendarEventDraft.expand(sourceId: String, uid: String): List<CalendarEvent> =
        expandCalendarEvent(
            sourceId = sourceId,
            title = summary,
            start = start,
            end = end,
            allDay = allDay,
            uid = uid,
            location = location,
        )

    private fun HomeState.withEvents(events: List<CalendarEvent>): HomeState =
        copy(calendar = calendar.copy(events = sortCalendarEvents(events)))
}

//TODO Delete
/**
 * Seed data for [MockAdapter]. Values are chosen to resemble the reference screenshots; the
 * media/calendar/climate fixtures are refined in Phases 4/6/7 as those cards are built.
 */
internal fun seedHome(): HomeState {
    // Date the seed off *today* (like LeftCard's clock) so events/todos land on the visible month.
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return HomeState(
    rooms = mapOf(
        // The Living Room is playing (matches Dashboard_with_media.png). Bedroom and Bathroom are
        // speaker rooms but idle (nowPlaying = null) so selecting one exercises the browse surface
        // (Dashboard_no_music_playing.png). Kitchen and Hall have no speaker → audio = null.
        Room.LivingRoom to RoomState(
            brightnessPct = 72, isLightOn = true, lightWarmth = Warmth.Soft,
            audio = AudioState(
                volumePct = 40, isPlaying = true,
                nowPlaying = MediaTrack(
                    "Midnight City", "M83", album = "Hurry Up, We're Dreaming", durationSec = 243,
                ),
                positionSec = 112,
                queue = listOf(
                    MediaTrack("Instant Crush", "Daft Punk", album = "Random Access Memories", durationSec = 337),
                    MediaTrack("Redbone", "Childish Gambino", album = "Awaken, My Love!", durationSec = 326),
                    MediaTrack("Nightcall", "Kavinsky", album = "OutRun", durationSec = 258),
                ),
                isShuffle = false, repeat = RepeatMode.Off,
            ),
        ),
        Room.Kitchen to RoomState(
            brightnessPct = 100, isLightOn = true, lightWarmth = Warmth.Neutral, audio = null,
        ),
        Room.Bedroom to RoomState(
            brightnessPct = 30, isLightOn = false, lightWarmth = Warmth.Warm,
            audio = AudioState(
                volumePct = 15, isPlaying = false, nowPlaying = null, positionSec = 0, queue = emptyList(),
            ),
        ),
        Room.Bathroom to RoomState(
            brightnessPct = 60, isLightOn = false, lightWarmth = Warmth.Cool,
            audio = AudioState(
                volumePct = 0, isPlaying = false, nowPlaying = null, positionSec = 0, queue = emptyList(),
            ),
        ),
        Room.Hall to RoomState(
            brightnessPct = 45, isLightOn = true, lightWarmth = Warmth.Candle, audio = null,
        ),
    ),
    climate = ClimateState(
        indoorTempC = 21.5,
        humidityPct = 44,
        energyKw = 1.2,
        feelsLikeC = 24.0,
        condition = WeatherCondition.PartlyCloudy,
    ),
    playlists = mockShelf(
        "playlist",
        "Fokus" to "Playlist", "Aftenro" to "Playlist", "Morgenkaffe" to "Playlist",
        "Løbetur" to "Playlist", "Fredagsbar" to "Playlist", "Søndagsbrunch" to "Playlist",
    ),
    // Browse shelves (real data comes from Music Assistant recommendations). quickPicks holds 27 —
    // three full 3×3 grid pages, so the mock exercises the page dots; mixedForYou feeds a rail.
    // Subtitles stand in for the artist/owner line the real MA data supplies.
    quickPicks = mockShelf(
        "track",
        "Sunlight" to "Selma Higgins", "Nightdrive" to "Kavinsky", "Blomsterhaven" to "Ude af Takt",
        "Static Bloom" to "Des Rocs", "Rolling Hills" to "Low Roar", "Vinterlys" to "Agnes Obel",
        "Paper Planes" to "M.I.A.", "Slow River" to "Bonobo", "Nordlys" to "Efterklang",
        "Golden Hour" to "Kacey Musgraves", "Midnatssol" to "Trentemøller", "Cascade" to "Floating Points",
        "Havets Sang" to "Mø", "Ember" to "Novo Amor", "Kobber" to "Blaue Blume",
        "Solstice" to "Khruangbin", "Regnvejr" to "The Minds of 99", "Halo Drift" to "Men I Trust",
        "Skyggen" to "Iceage", "Lantern" to "Hiatus Kaiyote", "Fjeldet" to "Sekuoia",
        "Neon Coast" to "The Midnight", "Stille Nu" to "Rasmus Walter", "Driftwood" to "Sylvan Esso",
        "Tågebanke" to "Vinnie Who", "Amber Room" to "Jungle", "Nordvest" to "Kesi",
    ),
    mixedForYou = mockShelf(
        "playlist",
        "Daft Punk-mix" to "Supermix", "Kavinsky-mix" to "Supermix",
        "Childish Gambino-mix" to "Supermix", "M83-mix" to "Supermix",
        "Synthwave-mix" to "Supermix", "Dansk indie-mix" to "Supermix",
    ),
    // The Spotify side of the source toggle. Deliberately shorter than the YT Music shelves — that
    // provider serves no recommendation feed, so its browse side really is this thin.
    spotifyPlaylists = mockShelf(
        "spotify-playlist",
        "Taylor Swift 💫" to "Playlist", "Dance 💃🏼" to "Playlist", "Hot Girl summer" to "Playlist",
        "Fourth Wing 🤍" to "Playlist", "70's - 90's 🎇" to "Playlist",
    ),
    spotifyRecentlyPlayed = mockShelf(
        "spotify-recent",
        "Cruel Summer" to "Taylor Swift", "Espresso" to "Sabrina Carpenter",
        "Good Luck, Babe!" to "Chappell Roan", "Birds of a Feather" to "Billie Eilish",
    ),
    calendar = CalendarState(
        // Four calendars of mixed writability, mirroring the real home: three local ones plus the
        // subscribed work roster, which no editing surface may write to. Enough distinct sources
        // that the agenda's per-calendar dot colors are visible in previews.
        // Colors are Home Assistant's own names, as the registry hands them over — the mock carries a
        // set so previews exercise the mapping onto the dashboard palette, not just the index fallback.
        sources = listOf(
            CalendarSource("calendar.papkassehuset", "Papkassehuset", canWrite = true, color = "green"),
            CalendarSource("calendar.matt", "Matt", canWrite = true, color = "primary"),
            CalendarSource("calendar.cecilie", "Cecilie", canWrite = true, color = "pink"),
            CalendarSource("calendar.c_arbejde", "C - Arbejde", canWrite = false, color = "amber"),
        ),
        // Events/todos span today + the next couple of days so the month grid shows dots on several
        // cells and selecting a day actually re-scopes the agenda + todo list. The multi-day and
        // all-day fixtures exercise the shapes the real backend sends.
        events = sortCalendarEvents(
            // Built through [expandCalendarEvent], exactly as the real adapters build what Home
            // Assistant sends, so the fixtures carry the whole-event start/end an editing surface
            // reads back — a row assembled by hand would open prefilled with the wrong time.
            seedEvent("calendar.matt", "Morgenmøde", today, LocalTime(9, 0), LocalTime(10, 0), "seed-1") +
                seedEvent("calendar.cecilie", "Tandlæge", today, LocalTime(13, 30), LocalTime(14, 15), "seed-2") +
                seedEvent("calendar.c_arbejde", "Aftenvagt", today, LocalTime(16, 0), LocalTime(23, 0), "seed-3") +
                seedEvent(
                    "calendar.papkassehuset", "Middag med Sam", today.plus(2, DateTimeUnit.DAY),
                    LocalTime(19, 0), LocalTime(22, 0), "seed-4",
                ) + expandCalendarEvent(
                sourceId = "calendar.papkassehuset",
                title = "Sommerhus",
                start = LocalDateTime(today.plus(4, DateTimeUnit.DAY), LocalTime(0, 0)),
                end = LocalDateTime(today.plus(7, DateTimeUnit.DAY), LocalTime(0, 0)),
                allDay = true,
                uid = "seed-5",
            )
        ),
        // One standing rule, on the calendar that most needs it: the read-only work roster, where a
        // reminder can only ever come from beside the event.
        reminders = ReminderRules(byCalendar = mapOf("calendar.c_arbejde" to 30)),
        todos = listOf(
            TodoItem("seed-vand", today, "Vand planterne", done = false),
            // Closed today, and one of them had been hanging over since yesterday — the pair the
            // Opgaver page's UDFØRT half exists to tell apart.
            TodoItem("seed-udlejer", today, "Svar udlejeren", done = true, completedOn = today),
            TodoItem(
                "seed-tandlaege", today.plus(-1, DateTimeUnit.DAY), "Ring til tandlægen",
                done = true, completedOn = today,
            ),
            // Left unticked from a passed day: carried onto every page from its own day forward.
            TodoItem("seed-daek", today.plus(-2, DateTimeUnit.DAY), "Skift dæk", done = false),
            TodoItem("seed-fly", today.plus(1, DateTimeUnit.DAY), "Book flybilletter", done = false),
        ),
    ),
    )
}

/** One timed seed event, expanded the same way the Home Assistant mapper expands a fetched one. */
private fun seedEvent(
    sourceId: String,
    title: String,
    day: LocalDate,
    from: LocalTime,
    to: LocalTime,
    uid: String,
): List<CalendarEvent> = expandCalendarEvent(
    sourceId = sourceId,
    title = title,
    start = LocalDateTime(day, from),
    end = LocalDateTime(day, to),
    uid = uid,
)

/**
 * Build a browse shelf from `name to subtitle` pairs, minting a mock uri per tile so the mock tiles
 * are tappable (a [BrowseItem] without a uri renders inert). No artwork — the tiles fall back to
 * their colored glyph, since the mock has no server to serve cover art.
 */
private fun mockShelf(kind: String, vararg items: Pair<String, String>): List<BrowseItem> =
    items.map { (name, subtitle) ->
        BrowseItem(name, subtitle = subtitle, uri = "mock://$kind/${name.lowercase().filter { it.isLetterOrDigit() }}")
    }

/** Every seeded tile, both sources — what the mock's play/search lookups resolve a uri against. */
private fun HomeState.allShelfItems(): List<BrowseItem> =
    quickPicks + playlists + mixedForYou + spotifyPlaylists + spotifyRecentlyPlayed
