package com.mattschoe.smarthome.data.ma

import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.Room

/**
 * The rich music data the [com.mattschoe.smarthome.data.MusicAssistantAdapter] exposes, sourced from
 * the MA server's own WS API — the parts the Home Assistant proxy can't reach. The composite adapter
 * overlays these onto the HA-derived `HomeState`: the three browse shelves at the top level and
 * [queuesByRoom] into each room's `AudioState.queue`.
 *
 * Browse shelves are home-wide (YouTube Music's recommendation feed). Queues are per-room, keyed by
 * matching an MA queue's display name to a [Room].
 *
 * [nowPlayingByRoom] is each queue's `current_item`. HA already reports what is playing, so this is
 * not the source of truth for the track — it carries the enriching fields HA's `media_player` can't
 * (full-resolution cover art, the MA uri, the queue handle) for the composite adapter to overlay.
 */
data class MusicData(
    val playlists: List<BrowseItem>,
    val quickPicks: List<BrowseItem>,
    val mixedForYou: List<BrowseItem>,
    val queuesByRoom: Map<Room, List<MediaTrack>>,
    val nowPlayingByRoom: Map<Room, MediaTrack> = emptyMap(),
    /** The Spotify browse side; derived from the same two replies as the shelves above. */
    val spotifyPlaylists: List<BrowseItem> = emptyList(),
    val spotifyRecentlyPlayed: List<BrowseItem> = emptyList(),
) {
    companion object {
        val EMPTY = MusicData(
            playlists = emptyList(),
            quickPicks = emptyList(),
            mixedForYou = emptyList(),
            queuesByRoom = emptyMap(),
            nowPlayingByRoom = emptyMap(),
            spotifyPlaylists = emptyList(),
            spotifyRecentlyPlayed = emptyList(),
        )
    }
}
