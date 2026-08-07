package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.ma.MusicData
import com.mattschoe.smarthome.data.model.ArtistDetail
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.HomeState
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.QueueMode
import com.mattschoe.smarthome.data.model.RecurrenceRange
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

/**
 * The single [HomeAdapter] the UI sees when the home has both a Home Assistant and a Music Assistant
 * connection. It keeps [ha] as the source of truth for devices (lights, transport, volume, climate,
 * calendar) and overlays [ma]'s rich music data — the YouTube-Music browse shelves and the full
 * per-room play queue that HA's proxy can't provide.
 *
 * [subscribe] merges `ha.subscribe()` with `ma.music`: the three browse shelves replace the (blank)
 * HA ones and each room's [com.mattschoe.smarthome.data.model.AudioState.queue] is filled from the
 * matching MA queue. Every device setter delegates straight to [ha]; only [play] routes to [ma].
 */
class CompositeHomeAdapter(
    private val ha: HomeAdapter,
    private val ma: MusicAssistantAdapter,
) : HomeAdapter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val merged: StateFlow<HomeState> =
        combine(ha.subscribe(), ma.music) { home, music -> home.withMusic(music) }
            .stateIn(scope, SharingStarted.Eagerly, ha.subscribe().value.withMusic(ma.music.value))

    override fun subscribe(): StateFlow<HomeState> = merged

    // --- Device intents: delegated verbatim to the HA adapter ---
    override fun setBrightness(room: Room, value: Int) = ha.setBrightness(room, value)
    override fun setWarmth(room: Room, warmth: Warmth) = ha.setWarmth(room, warmth)
    override fun setVolume(room: Room, value: Int) = ha.setVolume(room, value)
    override fun toggleLight(room: Room) = ha.toggleLight(room)

    // Transport routes to HA's media_player — MA owns only browse + play-media.
    override fun togglePlay(room: Room) = ha.togglePlay(room)
    override fun next(room: Room) = ha.next(room)
    override fun previous(room: Room) = ha.previous(room)
    override fun seek(room: Room, positionSec: Int) = ha.seek(room, positionSec)
    override fun setShuffle(room: Room, shuffle: Boolean) = ha.setShuffle(room, shuffle)
    override fun setRepeat(room: Room, mode: RepeatMode) = ha.setRepeat(room, mode)

    // Grouping is a media_player service like the transport ones, and HA reports it back on the
    // players' `group_members` — MA has no part in it.
    override fun joinAudio(leader: Room, follower: Room) = ha.joinAudio(leader, follower)
    override fun unjoinAudio(room: Room) = ha.unjoinAudio(room)

    // Starting a specific item is the one intent MA owns (it has the browse/play-media source), as is
    // everything addressing the queue — HA's media_player has no queue to speak of.
    override suspend fun play(room: Room, uri: String, radio: Boolean) = ma.play(room, uri, radio)
    override suspend fun playAll(room: Room, uris: List<String>) = ma.playAll(room, uris)
    override suspend fun enqueue(room: Room, uri: String, mode: QueueMode) = ma.enqueue(room, uri, mode)
    override suspend fun artistDetail(uri: String): ArtistDetail = ma.artistDetail(uri)
    override suspend fun playQueueItem(room: Room, queueItemId: String) = ma.playQueueItem(room, queueItemId)
    override fun moveQueueItem(room: Room, queueItemId: String, posShift: Int) =
        ma.moveQueueItem(room, queueItemId, posShift)
    override suspend fun search(query: String): List<BrowseItem> = ma.search(query)

    override fun addTodo(due: LocalDate, label: String) = ha.addTodo(due, label)
    override fun toggleTodo(id: String) = ha.toggleTodo(id)
    override fun editTodo(id: String, label: String) = ha.editTodo(id, label)

    // The calendar is entirely HA's — Music Assistant has no part in it, and [withMusic] leaves
    // `calendar` untouched, so the merged state carries HA's events and todos through unchanged.
    override fun refreshCalendar() = ha.refreshCalendar()
    override suspend fun createEvent(sourceId: String, draft: CalendarEventDraft) =
        ha.createEvent(sourceId, draft)
    override suspend fun updateEvent(
        sourceId: String,
        uid: String,
        draft: CalendarEventDraft,
        recurrenceId: String?,
        range: RecurrenceRange,
    ) = ha.updateEvent(sourceId, uid, draft, recurrenceId, range)
    override suspend fun deleteEvent(
        sourceId: String,
        uid: String,
        recurrenceId: String?,
        range: RecurrenceRange,
    ) = ha.deleteEvent(sourceId, uid, recurrenceId, range)
}

/**
 * Overlay [MusicData] onto a device-truth [HomeState]: replace the browse shelves, fill each speaker
 * room's queue from its matching MA queue, and enrich its now-playing track ([enrichNowPlaying]).
 * When [MusicData] is empty (MA not yet connected) this is a no-op — the shelves stay blank and the
 * rooms untouched, exactly as HA-only.
 */
internal fun HomeState.withMusic(music: MusicData): HomeState = copy(
    playlists = music.playlists.ifEmpty { playlists },
    quickPicks = music.quickPicks.ifEmpty { quickPicks },
    mixedForYou = music.mixedForYou.ifEmpty { mixedForYou },
    spotifyPlaylists = music.spotifyPlaylists.ifEmpty { spotifyPlaylists },
    spotifyRecentlyPlayed = music.spotifyRecentlyPlayed.ifEmpty { spotifyRecentlyPlayed },
    rooms = rooms.mapValues { (room, roomState) ->
        val audio = roomState.audio ?: return@mapValues roomState
        val queue = music.queuesByRoom[room] ?: audio.queue
        val nowPlaying = enrichNowPlaying(audio.nowPlaying, music.nowPlayingByRoom[room])
        roomState.copy(audio = audio.copy(queue = queue, nowPlaying = nowPlaying))
    },
)

/**
 * Keep HA authoritative for the playing track but take the fields it reports worse: its cover art is
 * a 512-px re-encode from the Music Assistant image proxy, where MA hands out the original
 * (Quick-Picks-quality) source URL, and it knows neither the MA uri nor the queue handle.
 *
 * The two sources tick independently, so a stale MA `current_item` could describe a different song;
 * the title match is the guard — when the titles disagree, HA's track is kept whole rather than
 * painted with the wrong cover.
 */
private fun enrichNowPlaying(ha: MediaTrack?, ma: MediaTrack?): MediaTrack? {
    if (ha == null || ma == null) return ha
    if (ha.title.normalizedTitle() != ma.title.normalizedTitle()) return ha
    return ha.copy(
        artworkUrl = ma.artworkUrl ?: ha.artworkUrl,
        uri = ma.uri ?: ha.uri,
        queueItemId = ma.queueItemId ?: ha.queueItemId,
    )
}

private fun String.normalizedTitle(): String = trim().lowercase()
