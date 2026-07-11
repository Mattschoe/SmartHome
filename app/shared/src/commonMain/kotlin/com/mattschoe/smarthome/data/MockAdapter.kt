package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarState
import com.mattschoe.smarthome.data.model.ClimateState
import com.mattschoe.smarthome.data.model.HomeState
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.Playlist
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
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

    override fun togglePlay(room: Room) = _state.update { it.togglePlay(room) }
    override fun next(room: Room) = _state.update { it.next(room) }
    override fun previous(room: Room) = _state.update { it.previous(room) }
    override fun seek(room: Room, positionSec: Int) = _state.update { it.seek(room, positionSec) }
    override fun setShuffle(room: Room, shuffle: Boolean) = _state.update { it.setShuffle(room, shuffle) }
    override fun setRepeat(room: Room, mode: RepeatMode) = _state.update { it.setRepeat(room, mode) }

    // The adapter owns id minting (the real backend assigns HA `uid`s); logic transitions take the id.
    @OptIn(ExperimentalUuidApi::class)
    override fun addTodo(due: LocalDate, label: String) {
        val id = Uuid.random().toString()
        _state.update { it.addTodo(id, due, label) }
    }
    override fun toggleTodo(id: String) = _state.update { it.toggleTodo(id) }
    override fun editTodo(id: String, label: String) = _state.update { it.editTodo(id, label) }
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
        outdoorTempC = 24.0,
    ),
    playlists = listOf(
        Playlist("Fokus", trackCount = 24),
        Playlist("Aftenro", trackCount = 18),
        Playlist("Morgenkaffe", trackCount = 31),
    ),
    // Browse shelves (HA browse_media seam, Phase 9). quickPicks holds ≥ 9 so the idle 3×3 grid
    // fills a full page and spills into a second one for the page dots; keepListening feeds the rail.
    quickPicks = listOf(
        Playlist("Discover Weekly", trackCount = 30),
        Playlist("Chill Hits", trackCount = 50),
        Playlist("Dansk Pop", trackCount = 42),
        Playlist("Deep Focus", trackCount = 60),
        Playlist("Indie Nyt", trackCount = 35),
        Playlist("Jazz Vibes", trackCount = 28),
        Playlist("Rock Klassikere", trackCount = 45),
        Playlist("Elektronisk", trackCount = 38),
        Playlist("Akustisk", trackCount = 22),
        Playlist("Fest", trackCount = 55),
        Playlist("Sommerhits", trackCount = 40),
    ),
    keepListening = listOf(
        Playlist("Random Access Memories", trackCount = 13),
        Playlist("OutRun", trackCount = 15),
        Playlist("Awaken, My Love!", trackCount = 11),
        Playlist("Hurry Up, We're Dreaming", trackCount = 22),
    ),
    calendar = CalendarState(
        // Events/todos span today + the next couple of days so the month grid shows dots on several
        // cells and selecting a day actually re-scopes the agenda + todo list.
        events = listOf(
            CalendarEvent(today, "Morgenmøde", "09:00"),
            CalendarEvent(today, "Tandlæge", "13:30"),
            CalendarEvent(today.plus(2, DateTimeUnit.DAY), "Middag med Sam", "19:00"),
        ),
        todos = listOf(
            TodoItem("seed-vand", today, "Vand planterne", done = false),
            TodoItem("seed-udlejer", today, "Svar udlejeren", done = true),
            TodoItem("seed-fly", today.plus(1, DateTimeUnit.DAY), "Book flybilletter", done = false),
        ),
    ),
    )
}
