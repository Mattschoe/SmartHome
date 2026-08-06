package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the platform's own media surfaces (Android's notification and lock screen) need to know about
 * the active audio room's playback. A flattened read-model of the room's [AudioState], deliberately
 * without the queue or the browse library: those belong to the in-app panel, not to a notification.
 */
data class NowPlayingSnapshot(
    val room: Room,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val positionSec: Int,
    val durationSec: Int,
    val volumePct: Int,
)

/** What those surfaces may do back — the transport a notification carries, and nothing more. */
interface MediaCommands {
    fun togglePlay()
    fun next()
    fun previous()
    fun seek(positionSec: Int)
    fun setVolume(pct: Int)
}

/**
 * The seam between the ViewModel and a platform media session. The ViewModel publishes what the
 * active audio room is playing and installs the [commands] that answer the session's transport; a
 * platform that has such a session (Android) reads both, and one that doesn't (desktop, iOS — see
 * `.claude/PHONE_BACKLOG.md` → Deferred) simply never looks.
 *
 * It is strictly a read-model: which room is active stays the ViewModel's own selection, so nothing
 * here can change it — the CORE RULE holds on the other side of this seam too.
 */
class NowPlayingBridge {
    private val _snapshot = MutableStateFlow<NowPlayingSnapshot?>(null)

    /** The active audio room's playback, or `null` when it is playing nothing. */
    val snapshot: StateFlow<NowPlayingSnapshot?> = _snapshot.asStateFlow()

    /** Set while a ViewModel is alive; the session drops its transport when it is not. */
    var commands: MediaCommands? = null

    fun publish(snapshot: NowPlayingSnapshot?) {
        _snapshot.value = snapshot
    }
}

/** Flattens [room]'s audio into a snapshot, or `null` when the room has no track (or no speaker). */
fun nowPlayingSnapshot(room: Room, audio: AudioState?): NowPlayingSnapshot? {
    val track = audio?.nowPlaying ?: return null
    return NowPlayingSnapshot(
        room = room,
        title = track.title,
        artist = track.artist,
        album = track.album,
        artworkUrl = track.artworkUrl,
        isPlaying = audio.isPlaying,
        positionSec = audio.positionSec,
        durationSec = track.durationSec,
        volumePct = audio.volumePct,
    )
}
