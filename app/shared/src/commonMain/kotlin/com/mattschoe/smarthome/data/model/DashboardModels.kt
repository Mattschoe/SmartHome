package com.mattschoe.smarthome.data.model

/**
 * The device-data models. [HomeState] is the single object a `HomeAdapter` exposes; the UI-selection
 * state (`activeRoom`/`panel`) lives on `HomeScreenState` in the ViewModel, not here.
 */

/** Color-temperature presets for a room's light, coldest -> warmest ordering */
enum class Warmth { Candle, Warm, Soft, Neutral, Cool }

/** The fixed set of rooms for this home. [displayName] is UI-facing, so it is Danish. */
enum class Room(val displayName: String) {
    LivingRoom("Stue"),
    Kitchen("Køkken"),
    Bedroom("Soveværelse"),
    Bathroom("Badeværelse"),
    Hall("Entré"),
}

/** The two mutually-exclusive right-card panels. */
enum class Panel { Media, Calendar }

data class MediaTrack(
    val title: String,
    val artist: String,
    val durationSec: Int,
)

/** A shared, home-wide playlist. The library is not owned by any room; any room can play from it. */
data class Playlist(
    val name: String,
    val trackCount: Int,
)

/**
 * Per-room, mutable device state driven by the center card's controls. A room owns everything that
 * happens inside it, both its lights and its own audio playback.
 *
 */
data class RoomState(
    val brightnessPct: Int,
    val isLightOn: Boolean,
    val lightWarmth: Warmth,
    val volumePct: Int,
    val isPlaying: Boolean,
    val nowPlaying: MediaTrack?,
    val positionSec: Int,
    val queue: List<MediaTrack>,
) { init { require(brightnessPct in 0..100 && volumePct in 0..100 && positionSec >= 0) } }

/** Read-only climate glance shown in the left card's 2×2 tile grid. Never mutated by controls. */
data class ClimateState(
    val indoorTempC: Double,
    val humidityPct: Int,
    val energyKw: Double,
    val outdoorTempC: Double,
) { init { require(humidityPct in 0..100) } }

data class CalendarEvent(
    val title: String,
    val time: String,
)

data class TodoItem(
    val label: String,
    val done: Boolean,
)

data class CalendarState(
    val year: Int,
    val month: Int,
    val today: Int,
    val events: List<CalendarEvent>,
    val todos: List<TodoItem>,
) { init { require(month in 1..12 && today in 1..31) } }

/**
 * The device-truth state for the whole home, exposed by a `HomeAdapter`
 * Each room owns its own lights *and* audio in [rooms];
 * [playlists] is the shared library any room can play from;
 * [climate] is read-only.
 *
 */
data class HomeState(
    val rooms: Map<Room, RoomState>,
    val climate: ClimateState,
    val playlists: List<Playlist>,
    val calendar: CalendarState,
)
