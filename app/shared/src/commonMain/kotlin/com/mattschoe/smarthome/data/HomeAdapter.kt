package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.ArtistDetail
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.HomeState
import com.mattschoe.smarthome.data.model.QueueMode
import com.mattschoe.smarthome.data.model.RecurrenceRange
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/**
 * The device-data boundary. UI observes [subscribe] and issues device intents through the setters;
 * concrete adapters own the state store and mutate it. Climate is read-only (no setter), it is
 * display-only. UI-selection state (active room, panel) is not device data and lives in the ViewModel.
 */
// TODO(Phase 9): add a HomeAssistantAdapter (WebSocket/REST) implementing this same interface and
//  swap it in AppContainer.
interface HomeAdapter {
    fun subscribe(): StateFlow<HomeState>

    fun setBrightness(room: Room, value: Int)
    fun setWarmth(room: Room, warmth: Warmth)
    fun setVolume(room: Room, value: Int)
    fun toggleLight(room: Room)

    /**
     * Start playing a Music Assistant item ([uri], e.g. a track/album/playlist) on [room]'s speaker.
     * [radio] `true` (the default) plays it as an endless radio/"don't stop the music" session so the
     * queue keeps auto-filling with YouTube-Music-suggested continuations.
     *
     * Like [search], this is `suspend` and propagates failure: starting a YouTube-Music item takes MA
     * several seconds of stream resolution, and the caller is showing a pending/loading state that it
     * drops when this returns (or turns into a "couldn't play" notice when it throws). Adapters
     * without a Music Assistant connection (HA-only) throw.
     */
    suspend fun play(room: Room, uri: String, radio: Boolean = true)

    /**
     * Queue a Music Assistant item ([uri]) behind what [room] is already playing, without interrupting
     * it. [mode] places it at the top ([QueueMode.Next]) or the bottom ([QueueMode.Last]) of the block
     * of user-added entries — above the auto-appended continuations either way. A container uri
     * (playlist/album) is expanded by Music Assistant into its tracks, in order.
     *
     * `suspend` and failure-propagating like [play], and for the same reason: resolving a container
     * takes seconds, and the caller confirms the outcome with a notice. Adapters without a Music
     * Assistant connection throw.
     */
    suspend fun enqueue(room: Room, uri: String, mode: QueueMode)

    /**
     * Skip playback to a specific entry of [room]'s queue (Music Assistant `player_queues/play_index`),
     * addressed by the [com.mattschoe.smarthome.data.model.MediaTrack.queueItemId] the queue handed out.
     * `suspend` + failure-propagating for the same reason as [play] — switching tracks re-resolves the
     * stream, which takes seconds the caller spends showing a spinner on the tapped row.
     */
    suspend fun playQueueItem(room: Room, queueItemId: String)

    /**
     * Search the music library/providers for [query], returning playable browse tiles in relevance
     * order. Unlike the fire-and-forget intents this is `suspend` and propagates failure — the caller
     * is showing a spinner. Adapters without a Music Assistant connection return an empty list.
     */
    suspend fun search(query: String): List<BrowseItem>

    /**
     * Fetch the top tracks + albums of the artist at [uri], for the artist drill-in surface. Like
     * [search] this is `suspend` and propagates failure — the caller is showing a spinner. Adapters
     * without a Music Assistant connection return [ArtistDetail.EMPTY].
     */
    suspend fun artistDetail(uri: String): ArtistDetail

    /**
     * Replace [room]'s queue with [uris] **in the given order** and start the first. What follows the
     * last one is the queue's always-on "Don't Stop the Music", exactly as with [play]. `suspend` +
     * failure-propagating for the same reason as [play] — building the queue re-resolves streams,
     * which takes seconds the caller spends showing a pending surface. A no-op on speaker-less rooms
     * and on adapters without a Music Assistant connection.
     */
    suspend fun playAll(room: Room, uris: List<String>)

    /**
     * Move a queue entry [posShift] positions within [room]'s queue — negative moves it earlier,
     * positive later (Music Assistant `player_queues/move_item`, whose shift is relative).
     */
    fun moveQueueItem(room: Room, queueItemId: String, posShift: Int)

    // Transport intents — 1:1 with Home Assistant media_player services. No-op on speaker-less rooms.
    fun togglePlay(room: Room)
    fun next(room: Room)
    fun previous(room: Room)
    fun seek(room: Room, positionSec: Int)
    fun setShuffle(room: Room, shuffle: Boolean)
    fun setRepeat(room: Room, mode: RepeatMode)

    /**
     * Make [follower] play along with [leader] — the additive grouping relation on top of per-room
     * ownership (both rooms then report [leader] as their
     * [com.mattschoe.smarthome.data.model.AudioState.syncLeader]).
     */
    fun joinAudio(leader: Room, follower: Room)

    /** Take [room] out of its sync group; done to the group's leader, this dissolves the group. */
    fun unjoinAudio(room: Room)

    // Todo intents — 1:1 with Home Assistant todo services. The adapter mints the id on add (HA `uid`);
    // editing a todo to a blank label removes it (todo.remove_item), the escape hatch for delete.
    fun addTodo(due: LocalDate, label: String)
    fun toggleTodo(id: String)
    fun editTodo(id: String, label: String)

    /**
     * Ask for a fresh calendar window. Unlike lights and media, calendar events are fetched rather
     * than pushed, so the UI hints when a refresh is worth doing (the panel opened, months were
     * navigated). Which panel is showing remains ViewModel-owned selection — this only takes the
     * nudge. Fire-and-forget: nobody waits on it, the new window simply arrives on the state flow.
     */
    fun refreshCalendar()

    /**
     * Add [draft] to the calendar [sourceId]. `suspend` and failure-propagating like [play]: the
     * caller is a save button showing a spinner, and a silently dropped event is worse than an error.
     * Throws when the source is unknown or not writable
     * ([com.mattschoe.smarthome.data.model.CalendarSource.canWrite]).
     */
    suspend fun createEvent(sourceId: String, draft: CalendarEventDraft)

    /**
     * Replace the event [uid] on [sourceId] with [draft]. [recurrenceId] addresses one occurrence of
     * a recurring series (from [com.mattschoe.smarthome.data.model.CalendarEvent.recurrenceId]), and
     * [range] decides whether the edit stops at that occurrence or carries forward.
     */
    suspend fun updateEvent(
        sourceId: String,
        uid: String,
        draft: CalendarEventDraft,
        recurrenceId: String? = null,
        range: RecurrenceRange = RecurrenceRange.ThisEvent,
    )

    /** Delete the event [uid] on [sourceId]; [recurrenceId]/[range] scope it as in [updateEvent]. */
    suspend fun deleteEvent(
        sourceId: String,
        uid: String,
        recurrenceId: String? = null,
        range: RecurrenceRange = RecurrenceRange.ThisEvent,
    )
}
