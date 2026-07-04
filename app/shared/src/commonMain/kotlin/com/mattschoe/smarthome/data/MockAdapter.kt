package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarState
import com.mattschoe.smarthome.data.model.ClimateState
import com.mattschoe.smarthome.data.model.HomeState
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.Playlist
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [HomeAdapter] seeded from [seedHome]. Controls mutate the store optimistically by
 * applying the matching pure transition from `DashboardLogic`.
 */
class MockAdapter(
    initial: HomeState = seedHome(),
) : HomeAdapter {
    private val _state = MutableStateFlow(initial)

    override fun subscribe(): StateFlow<HomeState> = _state.asStateFlow()

    override fun setBrightness(room: Room, value: Int) = _state.update { it.withBrightness(room, value) }
    override fun setWarmth(room: Room, warmth: Warmth) = _state.update { it.withWarmth(room, warmth) }
    override fun setVolume(room: Room, value: Int) = _state.update { it.withVolume(room, value) }
    override fun toggleLight(room: Room) = _state.update { it.toggleLight(room) }
}

//TODO Delete
/**
 * Seed data for [MockAdapter]. Values are chosen to resemble the reference screenshots; the
 * media/calendar/climate fixtures are refined in Phases 4/6/7 as those cards are built.
 */
internal fun seedHome(): HomeState = HomeState(
    rooms = mapOf(
        // Only the Living Room is playing (matches the reference screenshot); every other room owns
        // its own idle audio state — nothing playing, empty queue.
        Room.LivingRoom to RoomState(
            brightnessPct = 72, isLightOn = true, lightWarmth = Warmth.Soft,
            volumePct = 40, isPlaying = true,
            nowPlaying = MediaTrack("Midnight City", "M83", durationSec = 243),
            positionSec = 112,
            queue = listOf(
                MediaTrack("Instant Crush", "Daft Punk", durationSec = 337),
                MediaTrack("Redbone", "Childish Gambino", durationSec = 326),
                MediaTrack("Nightcall", "Kavinsky", durationSec = 258),
            ),
        ),
        Room.Kitchen to RoomState(
            brightnessPct = 100, isLightOn = true, lightWarmth = Warmth.Neutral,
            volumePct = 25, isPlaying = false, nowPlaying = null, positionSec = 0, queue = emptyList(),
        ),
        Room.Bedroom to RoomState(
            brightnessPct = 30, isLightOn = false, lightWarmth = Warmth.Warm,
            volumePct = 15, isPlaying = false, nowPlaying = null, positionSec = 0, queue = emptyList(),
        ),
        Room.Bathroom to RoomState(
            brightnessPct = 60, isLightOn = false, lightWarmth = Warmth.Cool,
            volumePct = 0, isPlaying = false, nowPlaying = null, positionSec = 0, queue = emptyList(),
        ),
        Room.Hall to RoomState(
            brightnessPct = 45, isLightOn = true, lightWarmth = Warmth.Candle,
            volumePct = 10, isPlaying = false, nowPlaying = null, positionSec = 0, queue = emptyList(),
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
    calendar = CalendarState(
        year = 2026,
        month = 7,
        today = 4,
        events = listOf(
            CalendarEvent("Morgenmøde", "09:00"),
            CalendarEvent("Tandlæge", "13:30"),
            CalendarEvent("Middag med Sam", "19:00"),
        ),
        todos = listOf(
            TodoItem("Vand planterne", done = false),
            TodoItem("Svar udlejeren", done = true),
            TodoItem("Book flybilletter", done = false),
        ),
    ),
)
