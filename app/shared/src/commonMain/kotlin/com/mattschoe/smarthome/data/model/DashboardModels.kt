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
    Bathroom("Badeværelse", hasSpeaker = false),
    Hall("Entré", hasSpeaker = false);

    companion object {
        /** The rooms the AUDIO selector offers — those with a speaker. Derived from [hasSpeaker]. */
        val audioRooms: List<Room> get() = entries.filter { it.hasSpeaker }
    }
}

/** The two mutually-exclusive right-card panels. */
enum class Panel { Media, Calendar }

/**
 * Which provider's listening the Media panel's **browse** shelves show — the two people sharing this
 * home each have their own account feeding one merged Music Assistant library. [badge] is the letter
 * on the toggle; [providerDomain] is the MA `provider_domain` / `provider` prefix that identifies a
 * row as this source's. Search is deliberately *not* scoped by this — it stays combined.
 */
enum class MusicSource(val badge: String, val providerDomain: String) {
    YtMusic("M", "ytmusic"),
    Spotify("C", "spotify"),
}

/** Repeat mode for a room's audio session, mirroring Home Assistant's `media_player` repeat states. */
enum class RepeatMode { Off, All }


/**
 * @param album HA media_album_name
 * @param artworkUrl HA media_image_url
 * @param uri Music Assistant item uri (e.g. `ytmusic--…://track/…`); play/queue source, `null` when unknown.
 * @param queueItemId Music Assistant queue-item handle — stable per queue entry, and what the
 *   skip-to/reorder intents address. `null` for tracks that come from anywhere but a queue.
 */
data class MediaTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String? = null,
    val durationSec: Int,
    val uri: String? = null,
    val queueItemId: String? = null,
)

/**
 * What a browse tile actually is. The search grid routes an [Artist] tile to the artist surface
 * instead of playing it; every other kind is a play target. Media types we don't model collapse to
 * [Other].
 */
enum class BrowseKind { Track, Album, Artist, Playlist, Other }

/**
 * One tile in a browse shelf (Quick Picks, Mixed For You, Playlists). Home-wide, not owned by any
 * room — any room can play it. [subtitle] is the secondary line (artist/owner; there is no reliable
 * track-count source from Music Assistant). [artworkUrl] is real cover art when available (else the
 * tile falls back to a colored glyph); [uri] is the Music Assistant uri tapped to play.
 */
data class BrowseItem(
    val name: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val uri: String? = null,
    val kind: BrowseKind = BrowseKind.Other,
)

/**
 * An artist's drill-in payload: [topTracks] is played as an ordered block (tap a hit and it plays
 * from there on), [albums] are individual play targets in a rail.
 */
data class ArtistDetail(
    val topTracks: List<BrowseItem>,
    val albums: List<BrowseItem>,
) {
    companion object {
        /** What an adapter without a Music Assistant connection answers with. */
        val EMPTY = ArtistDetail(emptyList(), emptyList())
    }
}

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
    /**
     * The room leading the sync group this room plays in: itself when it leads, another room when it
     * follows that room's playback, `null` when it plays alone. The additive grouping relation on top
     * of per-room ownership — rooms still own their own audio.
     */
    val syncLeader: Room? = null,
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

/**
 * Read-only climate glance shown in the left card's 2×2 tile grid. Never mutated by controls. A field
 * is `null` when no sensor backs it (the real HA adapter has no climate entities yet → all null → the
 * tiles render a "—" placeholder); the mock adapter populates every field.
 */
data class ClimateState(
    val indoorTempC: Double?,
    val humidityPct: Int?,
    val energyKw: Double?,
    val outdoorTempC: Double?,
) { init { require(humidityPct == null || humidityPct in 0..100) } }

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
 * @param quickPicks single songs/albums from Music Assistant's `music/recommendations` shelves
 * @param mixedForYou the supermixes from Music Assistant's "Mixed for you" shelf
 * @param spotifyPlaylists the Spotify side's playlists, the [MusicSource.Spotify] counterpart to [playlists]
 * @param spotifyRecentlyPlayed Spotify items from MA's "recently played" folder; Spotify serves no
 *   algorithmic feed of its own, so this and [spotifyPlaylists] are all its browse side has.
 */
data class HomeState(
    val rooms: Map<Room, RoomState>,
    val climate: ClimateState,
    val playlists: List<BrowseItem>,
    val quickPicks: List<BrowseItem>,
    val mixedForYou: List<BrowseItem>,
    val calendar: CalendarState,
    val spotifyPlaylists: List<BrowseItem> = emptyList(),
    val spotifyRecentlyPlayed: List<BrowseItem> = emptyList(),
)
