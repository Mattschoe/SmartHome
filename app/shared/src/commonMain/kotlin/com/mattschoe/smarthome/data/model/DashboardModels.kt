package com.mattschoe.smarthome.data.model

import kotlinx.datetime.LocalDate

/**
 * The device-data models. [HomeState] is the single object a `HomeAdapter` exposes; the UI-selection
 * state (`activeRoom`/`panel`) lives on `HomeScreenState` in the ViewModel, not here.
 */

/** Color-temperature presets for a room's light, coldest -> warmest ordering */
enum class Warmth { Candle, Warm, Soft, Neutral, Cool }

/**
 * The fixed set of rooms for this home. [displayName] is UI-facing, so it is Danish. [hasSpeaker]
 * marks which rooms have audio: the light selector lists every room, the AUDIO selector lists only
 * speaker rooms ([audioRooms]). To move or add a speaker, flip the boolean on that one line.
 */
enum class Room(val displayName: String, val hasSpeaker: Boolean) {
    LivingRoom("Stue", hasSpeaker = true),
    Kitchen("Køkken", hasSpeaker = false),
    Bedroom("Soveværelse", hasSpeaker = true),
    Bathroom("Badeværelse", hasSpeaker = true),
    Hall("Entré", hasSpeaker = false);

    companion object {
        /** The rooms the AUDIO selector offers — those with a speaker. Derived from [hasSpeaker]. */
        val audioRooms: List<Room> get() = entries.filter { it.hasSpeaker }
    }
}

/** The two mutually-exclusive right-card panels. */
enum class Panel { Media, Calendar }

/** Repeat mode for a room's audio session, mirroring Home Assistant's `media_player` repeat states. */
enum class RepeatMode { Off, All }


/**
 * @param album HA media_album_name
 * @param artworkUrl HA media_image_url
 */
data class MediaTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String? = null,
    val durationSec: Int,
)

/** A shared, home-wide playlist. The library is not owned by any room; any room can play from it. */
data class Playlist(
    val name: String,
    val trackCount: Int,
)

/**
 * Per-room audio session. It is `null` on a [RoomState] for rooms without a speaker (no HA
 * `media_player` entity). Transport/volume transitions on a speaker-less room are no-ops.
 */
data class AudioState(
    val volumePct: Int,
    val isPlaying: Boolean,
    val nowPlaying: MediaTrack?,
    val positionSec: Int,
    val queue: List<MediaTrack>,
    val isShuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
) { init { require(volumePct in 0..100 && positionSec >= 0) } }

/**
 * Per-room, mutable device state driven by the center card's controls. A room owns everything that
 * happens inside it, both its lights and its own audio playback ([audio], `null` when the room has
 * no speaker).
 */
data class RoomState(
    val brightnessPct: Int,
    val isLightOn: Boolean,
    val lightWarmth: Warmth,
    val audio: AudioState?,
) { init { require(brightnessPct in 0..100) } }

/** Read-only climate glance shown in the left card's 2×2 tile grid. Never mutated by controls. */
data class ClimateState(
    val indoorTempC: Double,
    val humidityPct: Int,
    val energyKw: Double,
    val outdoorTempC: Double,
) { init { require(humidityPct in 0..100) } }

/**
 * A read-only calendar event bound to a [date]. Maps onto a Home Assistant `calendar` entity event
 * later (Phase 9); [time] is a pre-formatted display string for now.
 */
data class CalendarEvent(
    val date: LocalDate,
    val title: String,
    val time: String,
)

/**
 * A to-do item, shaped to map onto a Home Assistant `todo.*` list item: [id] ↔ HA `uid` (client-
 * stable so backend echoes re-key existing rows instead of tearing them down), [label] ↔ `summary`,
 * [done] ↔ `status` (needs_action/completed), [due] the day it is bound to. "Todos for a day" is a
 * client-side filter on [due] over one shared list — the per-day bucket is a UI idea, not backend
 * structure.
 */
data class TodoItem(
    val id: String,
    val due: LocalDate,
    val label: String,
    val done: Boolean,
)

/**
 * The calendar payload the adapter exposes: a flat list of [events] and [todos]. The current day and
 * the displayed month are UI selection (they come from the system clock / the ViewModel), not device
 * data, so they are not on here.
 */
data class CalendarState(
    val events: List<CalendarEvent>,
    val todos: List<TodoItem>,
)

/**
 * The device-truth state for the whole home, exposed by a `HomeAdapter`
 * Each room owns its own lights *and* audio in [rooms];
 * @param playlists is the shared library any room can play from;
 * @param climate is read-only.
 * @param quickPicks HA browse_media "featured/recommended"
 * @param keepListening HA browse_media "recently played"
 */
data class HomeState(
    val rooms: Map<Room, RoomState>,
    val climate: ClimateState,
    val playlists: List<Playlist>,
    val quickPicks: List<Playlist>,
    val keepListening: List<Playlist>,
    val calendar: CalendarState,
)
