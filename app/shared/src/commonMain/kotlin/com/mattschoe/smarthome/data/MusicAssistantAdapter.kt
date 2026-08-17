package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.ma.MaMediaItem
import com.mattschoe.smarthome.data.ma.MaQueue
import com.mattschoe.smarthome.data.ma.MaQueueItem
import com.mattschoe.smarthome.data.ma.MaRecommendationFolder
import com.mattschoe.smarthome.data.ma.MaSearchResults
import com.mattschoe.smarthome.data.ma.MusicData
import com.mattschoe.smarthome.data.ma.mapArtistAlbums
import com.mattschoe.smarthome.data.ma.mapArtistTracks
import com.mattschoe.smarthome.data.ma.mapPlaylists
import com.mattschoe.smarthome.data.ma.mapQueueItems
import com.mattschoe.smarthome.data.ma.mapRecentlyPlayed
import com.mattschoe.smarthome.data.ma.mapRecommendations
import com.mattschoe.smarthome.data.ma.mapSearchResults
import com.mattschoe.smarthome.data.ma.mapSpotifyPlaylists
import com.mattschoe.smarthome.data.ma.matchQueuesToRooms
import com.mattschoe.smarthome.data.ma.parseMaUri
import com.mattschoe.smarthome.data.ma.planEnqueue
import com.mattschoe.smarthome.data.ma.toMediaTrack
import com.mattschoe.smarthome.data.ma.upNextOffset
import com.mattschoe.smarthome.data.ma.withoutQueueItem
import com.mattschoe.smarthome.data.model.ArtistDetail
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.data.model.QueueMode
import com.mattschoe.smarthome.data.model.Room
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * A client for the home's **Music Assistant** server over its own WebSocket API (`:8095/ws`). It
 * exposes the rich music data the Home Assistant `music_assistant.*` proxy cannot reach — YouTube
 * Music recommendations, the full play queue, and radio continuation — as a single [music] flow the
 * [CompositeHomeAdapter] overlays onto `HomeState`.
 *
 * Unlike [HomeAssistantAdapter] (whose setup issues strictly sequential request/response calls), MA
 * interleaves push events with command replies, so this uses a **pending-request dispatcher**: a
 * single read loop routes `message_id` replies to their awaiting [CompletableDeferred] and pushes
 * `event` frames to [handleEvent]. Auth is required (schema ≥ 28): after the server hello we send an
 * `auth` command with the MA long-lived token before any other command.
 */
class MusicAssistantAdapter(private val config: MaConfig) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient { install(WebSockets) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _music = MutableStateFlow(MusicData.EMPTY)
    val music: StateFlow<MusicData> = _music.asStateFlow()

    // Drives which slice of the Quick Picks pool is on screen. Marked once per adapter rather than
    // per session: the browse loop restarts on every reconnect, so anything counting refetches would
    // spin the rotation on a flapping connection.
    private val sinceStart = TimeSource.Monotonic.markNow()

    // The server's own base URL (from the hello frame), used to resolve image-proxy paths in Phase 5.
    @Volatile private var baseUrl: String = ""

    // Room -> MA queue_id (also the player target). Rebuilt on each queue refresh; read by play()/DSTM.
    @Volatile private var queueIdByRoom: Map<Room, String> = emptyMap()

    // Coalesces queue-refresh requests from push events and the poll fallback into one refetch at a time.
    private val queueRefreshTrigger = Channel<Unit>(Channel.CONFLATED)

    // The bottom of each room's block of user-queued entries, as one queue-item id — the whole of what
    // [enqueue] remembers, since MA records no per-item provenance. See [planEnqueue]: a marker no
    // longer in the queue reads as "the block is empty", which is how this self-heals across track
    // advances, replaces and edits made in MA's own web UI, with no pruning on the refresh path.
    @Volatile private var userBlockTailByRoom: Map<Room, String> = emptyMap()

    // Serializes enqueues: two long-presses in a row must not interleave their before/after diffs.
    private val enqueueMutex = Mutex()

    // Pending request/response correlation. The single read loop completes these; [command] awaits them.
    private val pendingMutex = Mutex()
    private var nextId = 1
    private val pending = mutableMapOf<Int, CompletableDeferred<JsonObject>>()

    @Volatile private var session: DefaultClientWebSocketSession? = null
    private var reconnectDelay = INITIAL_RECONNECT_MS

    init {
        scope.launch { connectionLoop() }
    }

    // --- Connection lifecycle ---

    private suspend fun connectionLoop() {
        while (scope.isActive) {
            try {
                client.webSocket(config.webSocketUrl) {
                    session = this
                    try {
                        runSession()
                    } finally {
                        session = null
                        failPending()
                    }
                }
            } catch (e: MaAuthException) {
                // A bad token will never succeed on retry — stop reconnecting.
                log("authentication failed: ${e.message}")
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Any other drop is retried on the backoff below.
                log("connection lost (${e.message}); reconnecting in ${reconnectDelay}ms")
            }
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_MS)
        }
    }

    /** Hello → start read loop → auth → background work → stay until the socket closes. */
    private suspend fun DefaultClientWebSocketSession.runSession() = coroutineScope {
        // The first frame is the unsolicited server-info hello (no message_id). Read it before the
        // loop starts consuming `incoming`.
        val hello = receiveJson()
        baseUrl = hello["base_url"]?.jsonPrimitive?.contentOrNull ?: deriveBaseUrl()

        val reader = launch { readLoop() }
        authenticate()
        reconnectDelay = INITIAL_RECONNECT_MS // healthy connection — reset backoff
        log("connected to $baseUrl (schema ${hello["schema_version"]?.jsonPrimitive?.contentOrNull})")
        onConnected()
        reader.join() // returns when `incoming` closes; lets the webSocket block end and reconnect
        // [onConnected]'s loops run until cancelled, and this `coroutineScope` waits for every child:
        // without this the session would never end, so a dropped socket would never be reconnected
        // and every later command would be sent into a dead session.
        log("read loop ended; closing session")
        coroutineContext.cancelChildren()
    }

    private suspend fun DefaultClientWebSocketSession.readLoop() {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val text = frame.readText()
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: continue
            val mid = obj["message_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            if (mid != null) {
                // A reply with no awaiter is one whose caller already timed out — drop it.
                pendingMutex.withLock { pending.remove(mid) }?.complete(obj)
            } else if (obj["event"] != null) {
                handleEvent(obj)
            }
        }
    }

    /**
     * Background work kicked off once authenticated: the recommendation/playlist refresh (below),
     * plus queue fetch + live updates (Phase 6) and always-on auto-suggestions (Phase 7).
     */
    private fun CoroutineScope.onConnected() {
        launch { refreshBrowseLoop() }
        launch { queueRefreshConsumer() }
        launch { queuePollLoop() }
    }

    /** Serialize queue refetches: one at a time, with a short trailing debounce to coalesce bursts. */
    private suspend fun queueRefreshConsumer() {
        for (unit in queueRefreshTrigger) {
            runCatching { refreshQueues() }
                .onFailure { log("queue refresh failed: ${it.message}") }
            delay(QUEUE_DEBOUNCE_MS)
        }
    }

    /** Fallback so queues (incl. auto-appended radio tracks) stay fresh even if an event is missed. */
    private suspend fun queuePollLoop() {
        while (true) {
            queueRefreshTrigger.trySend(Unit) // first tick fires the initial load immediately
            delay(QUEUE_POLL_MS)
        }
    }

    private suspend fun refreshQueues() {
        val queues: List<MaQueue> = json.decodeFromJsonElement(command("player_queues/all"))
        val idByRoom = matchQueuesToRooms(queues)
        if (idByRoom.keys != queueIdByRoom.keys) {
            log("matched queues: ${idByRoom.entries.joinToString { "${it.key}=${it.value}" }.ifBlank { "none" }} " +
                "(of ${queues.size} MA queues: ${queues.joinToString { it.display_name.orEmpty() }})")
        }
        queueIdByRoom = idByRoom
        // Always-on auto-suggestions: keep "Don't Stop the Music" enabled on every room speaker queue so
        // the queue self-refills with YouTube-Music continuations. Idempotent — only touch queues that
        // are currently off, and re-enable if something turned it off (the user wants this as a default).
        val roomQueueIds = idByRoom.values.toSet()
        queues.filter { it.queue_id in roomQueueIds && !it.dont_stop_the_music_enabled }
            .forEach { enableDontStopTheMusic(it.queue_id) }
        val queueById = queues.associateBy { it.queue_id }
        // Per room, so one room's unreadable page can't cost every *other* room its queue: the rooms
        // are fetched in one pass and published together, and a single throw partway through would
        // abandon the whole update. A room that fails keeps the rows it already had.
        val previous = _music.value.queuesByRoom
        val queuesByRoom = idByRoom.mapValues { (room, queueId) ->
            val queue = queueById[queueId]
            val offset = upNextOffset(queue?.current_index)
            runCatching {
                fetchQueueItems(queueId, offset).withoutQueueItem(queue?.current_item?.queue_item_id)
            }
                .onSuccess { tracks ->
                    // The one line that answers "why is there no up next here?": how many entries MA
                    // says the queue holds, where playback sits in it, whether the continuation
                    // feature is on, and how many rows the UI is therefore given.
                    log(
                        "queue $room: items=${queue?.items} index=${queue?.current_index} " +
                            "dstm=${queue?.dont_stop_the_music_enabled} playing=${queue?.current_item?.name} " +
                            "-> upNext=${tracks.size}"
                    )
                }
                .onFailure { log("queue $room: could not read items at offset $offset: ${it.message}") }
                .getOrElse { previous[room].orEmpty() }
        }
        // The playing entry, kept beside the queue: it carries MA's own art/uri, which the composite
        // adapter overlays onto HA's (lower-resolution) now-playing.
        val nowPlayingByRoom = buildMap {
            for ((room, queueId) in idByRoom) {
                queueById[queueId]?.current_item?.toMediaTrack()?.let { put(room, it) }
            }
        }
        _music.update { it.copy(queuesByRoom = queuesByRoom, nowPlayingByRoom = nowPlayingByRoom) }
    }

    /** Turn on MA's per-queue "Don't Stop the Music" so it keeps appending suggested tracks. */
    private suspend fun enableDontStopTheMusic(queueId: String) {
        runCatching {
            command("player_queues/dont_stop_the_music", buildJsonObject {
                put("queue_id", queueId)
                put("dont_stop_the_music_enabled", true)
            })
        }
            .onSuccess { log("enabled Don't Stop the Music on $queueId") }
            // Silently losing this is how a queue ends up with no continuations at all, so it is
            // reported rather than swallowed.
            .onFailure { log("could not enable Don't Stop the Music on $queueId: ${it.message}") }
    }

    /**
     * One page of a queue starting at [offset] — the first unplayed entry, so the result *is* "up
     * next". `player_queues/items` is a plain slice, and the real queues run to hundreds of items
     * (each carrying its full media object), so the page is deliberately short: the UI never shows
     * more than a screenful, and the whole queue would cost ~1.3 MB per room per refresh.
     */
    private suspend fun fetchQueueItems(queueId: String, offset: Int): List<com.mattschoe.smarthome.data.model.MediaTrack> {
        val items: List<MaQueueItem> = json.decodeFromJsonElement(
            command("player_queues/items", buildJsonObject {
                put("queue_id", queueId); put("limit", UP_NEXT_LIMIT); put("offset", offset)
            })
        )
        return mapQueueItems(items)
    }

    /** Periodically refetch the YouTube-Music browse shelves so recommendations stay current. */
    private suspend fun refreshBrowseLoop() {
        while (true) {
            runCatching { refreshBrowse() }
                .onFailure { log("browse refresh failed: ${it.message}") }
            delay(BROWSE_REFRESH_MS)
        }
    }

    private suspend fun refreshBrowse() {
        val folders: List<MaRecommendationFolder> =
            json.decodeFromJsonElement(command("music/recommendations"))
        val playlistRows: List<MaMediaItem> = json.decodeFromJsonElement(
            command("music/playlists/library_items", buildJsonObject { put("limit", PLAYLIST_LIMIT); put("offset", 0) })
        )
        // Read on the browse tick rather than from a timer of its own, so the window advances on the
        // first refresh after each ROTATION_MS boundary — at most one BROWSE_REFRESH_MS late.
        val rotation = (sinceStart.elapsedNow().inWholeMilliseconds / ROTATION_MS).toInt()
        // Scoped to ytmusic: `music/recommendations` is library-wide, so an unscoped mapping would put
        // the Spotify account's songs on the YouTube-Music shelves (and vice versa).
        val shelves = mapRecommendations(folders, rotation, MusicSource.YtMusic.providerDomain)
        val playlists = mapPlaylists(playlistRows)
        // The Spotify side is derived from these same two replies — it needs no calls of its own.
        val spotifyPlaylists = mapSpotifyPlaylists(playlistRows)
        val spotifyRecentlyPlayed = mapRecentlyPlayed(folders, MusicSource.Spotify.providerDomain)
        log(
            "browse: ${folders.size} folders -> quickPicks=${shelves.quickPicks.size} " +
                "mixed=${shelves.mixedForYou.size} playlists=${playlists.size} " +
                "spotify=${spotifyPlaylists.size}/${spotifyRecentlyPlayed.size}"
        )
        _music.update {
            it.copy(
                quickPicks = shelves.quickPicks,
                mixedForYou = shelves.mixedForYou,
                playlists = playlists,
                spotifyPlaylists = spotifyPlaylists,
                spotifyRecentlyPlayed = spotifyRecentlyPlayed,
            )
        }
    }

    /**
     * Dispatch an MA push `event` frame. Any queue-shape change (items added, next track, radio
     * append) requests a queue refetch; the high-frequency `queue_time_updated` position ticks are
     * ignored so playback progress doesn't spam refetches.
     */
    private fun handleEvent(event: JsonObject) {
        val name = event["event"]?.jsonPrimitive?.contentOrNull ?: return
        // `queue_time_updated` fires ~1/sec while playing — logging it would drown everything else.
        if (name == "queue_time_updated") return
        val triggers = name.startsWith("queue")
        if (triggers) queueRefreshTrigger.trySend(Unit)
    }

    /**
     * Start playing an MA item [uri] on [room]'s speaker — `replace` the queue and play now, matching
     * MA's own "Play" button. Continuation is handled by the always-on per-queue **Don't Stop the
     * Music** (enabled in [refreshQueues]), *not* by [radio]: `radio_mode=true` makes `play_media`
     * synchronously build a provider radio before it replies, which stalls the command past its
     * timeout, so [radio] is left off by default.
     *
     * Suspends until MA's reply, which for a YouTube-Music item lands only once the stream is
     * resolved (measured 6–9 s) — so returning here means "the music is starting now", the signal the
     * caller's pending-play UI waits on. Throws when the room has no matched MA queue or MA rejects
     * the command. The queue view refreshes right after.
     */
    suspend fun play(room: Room, uri: String, radio: Boolean): Unit = intent("play $uri on $room") {
        val queueId = queueIdByRoom[room]
            ?: throw MaCommandException("player_queues/play_media", null,
                "no MA queue matched for $room (known: ${queueIdByRoom.keys.joinToString().ifBlank { "none" }})")
        command("player_queues/play_media", buildJsonObject {
            put("queue_id", queueId)
            put("media", uri)
            put("option", "replace")
            put("radio_mode", radio)
        })
        // A replace provably empties the user block, so the marker goes with it.
        userBlockTailByRoom = userBlockTailByRoom - room
        queueRefreshTrigger.trySend(Unit)
    }

    /**
     * Queue [uri] behind what [room] is already playing. The insert itself is `play_media` with
     * `option = "next"`, which MA drops in just past its playback buffer without interrupting the
     * current track (and expands a playlist/album uri into its tracks itself, so this stays
     * single-uri). `add` would append *below* the auto-appended continuations and `replace_next` would
     * wipe the user block, so neither can serve either mode.
     *
     * Where the run lands relative to the existing user block is then [planEnqueue]'s job, over an
     * up-next snapshot taken either side of the insert. The snapshots take their own un-debounced
     * fetch — [refreshQueues] sits behind a conflated debounce and re-enables Don't Stop the Music as
     * a side effect — and read a deeper window than the UI's, so a queued album can't push the marker
     * out of sight.
     *
     * Everything the reorder touches sits below the buffer watermark MA inserted after, so the moves
     * are structurally safe; a track ending mid-sequence is the residual risk. A refused move leaves
     * the item queued but higher than intended (at worst reading as "play next"), which is not worth
     * failing the whole gesture over — so it stops the sequence quietly and keeps the old marker
     * rather than recording a bottom the queue doesn't have.
     */
    suspend fun enqueue(room: Room, uri: String, mode: QueueMode): Unit = intent("enqueue $mode $uri on $room") {
        val queueId = queueIdByRoom[room]
            ?: throw MaCommandException("player_queues/play_media", null,
                "no MA queue matched for $room (known: ${queueIdByRoom.keys.joinToString().ifBlank { "none" }})")
        enqueueMutex.withLock {
            val offset = upNextOffset(currentIndexOf(queueId))
            val before = upNextIds(queueId, offset)
            command("player_queues/play_media", buildJsonObject {
                put("queue_id", queueId)
                put("media", uri)
                put("option", "next")
                put("radio_mode", false)
            }, timeoutMs = PLAY_ALL_TIMEOUT_MS)
            val plan = planEnqueue(before, upNextIds(queueId, offset), userBlockTailByRoom[room], mode)
            val landed = plan.moves.all { move ->
                runCatching {
                    command("player_queues/move_item", buildJsonObject {
                        put("queue_id", queueId)
                        put("queue_item_id", move.queueItemId)
                        put("pos_shift", move.posShift)
                    })
                }
                    .onFailure { log("enqueue reorder stopped at ${move.queueItemId}: ${it.message}") }
                    .isSuccess
            }
            if (landed) plan.tailId?.let { userBlockTailByRoom = userBlockTailByRoom + (room to it) }
            queueRefreshTrigger.trySend(Unit)
        }
    }

    /** [queueId]'s `current_index` off a fresh `player_queues/all` — where "up next" starts. */
    private suspend fun currentIndexOf(queueId: String): Int? {
        val queues: List<MaQueue> = json.decodeFromJsonElement(command("player_queues/all"))
        return queues.firstOrNull { it.queue_id == queueId }?.current_index
    }

    /** The queue-item ids of one [ENQUEUE_WINDOW]-deep up-next page — [enqueue]'s before/after snapshot. */
    private suspend fun upNextIds(queueId: String, offset: Int): List<String> {
        val items: List<MaQueueItem> = json.decodeFromJsonElement(
            command("player_queues/items", buildJsonObject {
                put("queue_id", queueId); put("limit", ENQUEUE_WINDOW); put("offset", offset)
            })
        )
        return items.map { it.queue_item_id }
    }

    /**
     * Start [uris] on [room]'s speaker **in order** — `player_queues/play_media` takes `media` as a
     * JSON array and replaces the queue with exactly those items, first one playing. What comes after
     * the last is the queue's always-on Don't Stop the Music, which survives the `replace` (both
     * verified live against MA 2.9.9 / schema 31). Suspends until the reply and throws on failure,
     * like [play] — building a multi-item queue resolves streams, so it gets its own longer
     * [PLAY_ALL_TIMEOUT_MS].
     */
    suspend fun playAll(room: Room, uris: List<String>): Unit = intent("playAll ${uris.size} items on $room") {
        if (uris.isEmpty()) {
            log("playAll on $room: nothing playable in the block")
            return@intent
        }
        val queueId = queueIdByRoom[room]
            ?: throw MaCommandException("player_queues/play_media", null,
                "no MA queue matched for $room (known: ${queueIdByRoom.keys.joinToString().ifBlank { "none" }})")
        command("player_queues/play_media", buildJsonObject {
            put("queue_id", queueId)
            putJsonArray("media") { uris.forEach { add(it) } }
            put("option", "replace")
            put("radio_mode", false)
        }, timeoutMs = PLAY_ALL_TIMEOUT_MS)
        // Like [play]: the replace empties the user block, so its marker goes too.
        userBlockTailByRoom = userBlockTailByRoom - room
        queueRefreshTrigger.trySend(Unit)
    }

    /**
     * The artist behind [uri]: their tracks and albums, for the drill-in surface. MA addresses artist
     * items by `(item_id, provider_instance_id_or_domain)` rather than by uri, hence [parseMaUri].
     *
     * Commands verified live against MA 2.9.9 / schema 31: `music/artists/top_tracks` and
     * `music/artists/artist_albums`. The api_command is `top_tracks` — `artist_toptracks` is a
     * controller *method* name and replies `error_code: 12`. `artist_tracks` exists but returns
     * discography order, not top hits: ytmusic declares `ARTIST_TOPTRACKS`/`ARTIST_ALBUMS` but not
     * `ARTIST_TRACKS`, so MA falls back to enumerating every album's tracklist. Both commands here
     * answer from cache in ms (`top_tracks` measured 1.9 s cold). Like [search], failure propagates —
     * a user is watching a spinner.
     */
    suspend fun artistDetail(uri: String): ArtistDetail = intent("artistDetail $uri") {
        val ref = parseMaUri(uri)
        if (ref == null) {
            log("artistDetail: '$uri' is not an MA uri")
            return@intent ArtistDetail.EMPTY
        }
        val args = buildJsonObject {
            put("item_id", ref.itemId)
            put("provider_instance_id_or_domain", ref.provider)
        }
        val tracks: List<MaMediaItem> = json.decodeFromJsonElement(
            command("music/artists/top_tracks", args, timeoutMs = ARTIST_TIMEOUT_MS)
        )
        val albums: List<MaMediaItem> = json.decodeFromJsonElement(
            command("music/artists/artist_albums", args, timeoutMs = ARTIST_TIMEOUT_MS)
        )
        ArtistDetail(mapArtistTracks(tracks), mapArtistAlbums(albums))
    }

    /**
     * Skip playback straight to [queueItemId] in [room]'s queue. MA's `player_queues/play_index`
     * takes either a positional index or a queue-item id for `index`; the id is what we hold, and it
     * survives the queue shifting under us between refreshes. Suspends/throws like [play] — the reply
     * arrives when the new track's stream is resolved.
     */
    suspend fun playQueueItem(room: Room, queueItemId: String): Unit = intent("skip to $queueItemId on $room") {
        val queueId = queueIdByRoom[room]
            ?: throw MaCommandException("player_queues/play_index", null,
                "no MA queue matched for $room (known: ${queueIdByRoom.keys.joinToString().ifBlank { "none" }})")
        command("player_queues/play_index", buildJsonObject {
            put("queue_id", queueId)
            put("index", queueItemId)
        })
        queueRefreshTrigger.trySend(Unit)
    }

    /**
     * Move [queueItemId] [posShift] positions within [room]'s queue — MA's `move_item` shift is
     * **relative**: positive moves it later, negative earlier, `0` makes it play next. The server
     * refuses to move an entry it has already played or buffered; that rejection is left to the next
     * refresh, which simply restores the server's order.
     */
    fun moveQueueItem(room: Room, queueItemId: String, posShift: Int) {
        val queueId = queueIdByRoom[room] ?: run {
            log("move on $room ignored: no MA queue matched")
            return
        }
        scope.launch {
            runCatching {
                command("player_queues/move_item", buildJsonObject {
                    put("queue_id", queueId)
                    put("queue_item_id", queueItemId)
                    put("pos_shift", posShift)
                })
                queueRefreshTrigger.trySend(Unit)
            }
                .onFailure { log("move $queueItemId by $posShift on $room failed: ${it.message}") }
        }
    }

    /**
     * Search the configured music providers for [query]. Unlike the fire-and-forget playback intents
     * this one is `suspend` and lets failure through — the caller is a user waiting on a spinner, so
     * "the search failed" is the honest answer to a dead socket or a timeout, where an empty list
     * would read on screen as "this music does not exist".
     */
    suspend fun search(query: String): List<BrowseItem> = intent("search '$query'") {
        val results: MaSearchResults = json.decodeFromJsonElement(
            command(
                "music/search",
                buildJsonObject {
                    put("search_query", query)
                    // Applied per media type, so the flat grid fills from a mix of all four.
                    put("limit", SEARCH_LIMIT)
                    putJsonArray("media_types") { SEARCH_MEDIA_TYPES.forEach { add(it) } }
                },
                timeoutMs = SEARCH_TIMEOUT_MS,
            )
        )
        mapSearchResults(results).also {
            log("search '$query' -> ${results.tracks.size}t/${results.albums.size}al/" +
                "${results.artists.size}ar/${results.playlists.size}p -> ${it.size} tiles")
        }
    }

    // --- Auth + request/response ---

    private suspend fun authenticate() {
        val result = try {
            command("auth", buildJsonObject { put("token", config.token) })
        } catch (e: MaCommandException) {
            throw MaAuthException(e.message ?: "auth rejected")
        }
        val authed = (result as? JsonObject)?.get("authenticated")?.jsonPrimitive?.booleanOrNull ?: false
        if (!authed) throw MaAuthException("server did not authenticate the token")
    }

    /**
     * Send an MA command and await its reply, correlated by `message_id`. Returns the reply's
     * `result` element (or [JsonNull]); throws [MaCommandException] on an `error_code` reply and on
     * timeout. Safe to call concurrently — the read loop fans replies back to the right awaiter.
     *
     * A timeout is deliberately **re-thrown as an ordinary exception**: [withTimeout] reports one as a
     * [TimeoutCancellationException], and every caller here is inside a flow or a `viewModelScope`
     * job that treats a `CancellationException` as "this work was superseded" — so a slow server would
     * silently tear the collector down (taking the search pipeline, and with it the screen state, with
     * it) instead of surfacing as the failure it is.
     */
    private suspend fun command(
        command: String,
        args: JsonObject? = null,
        timeoutMs: Long = REQUEST_TIMEOUT_MS,
    ): JsonElement {
        val deferred = CompletableDeferred<JsonObject>()
        val id = pendingMutex.withLock { val i = nextId++; pending[i] = deferred; i }
        try {
            sendText(buildJsonObject {
                put("message_id", id.toString())
                put("command", command)
                if (args != null) put("args", args)
            }.toString())
            val reply = withTimeout(timeoutMs) { deferred.await() }
            reply["error_code"]?.let {
                val code = it.jsonPrimitive.intOrNull
                val details = reply["details"]?.jsonPrimitive?.contentOrNull
                throw MaCommandException(command, code, details)
            }
            return reply["result"] ?: JsonNull
        } catch (e: TimeoutCancellationException) {
            throw MaCommandException(command, null, "no reply within ${timeoutMs}ms")
        } finally {
            // NonCancellable so the entry is still dropped when the caller is cancelled mid-flight
            // (a superseded search), rather than left in `pending` for the life of the session.
            withContext(NonCancellable) { pendingMutex.withLock { pending.remove(id) } }
        }
    }

    /** Fail every awaiting command when the socket drops, so `command` callers don't hang until timeout. */
    private suspend fun failPending() {
        pendingMutex.withLock {
            pending.values.forEach { it.completeExceptionally(MaCommandException("*", null, "connection closed")) }
            pending.clear()
        }
    }

    private suspend fun receiveJson(): JsonObject {
        while (true) {
            val frame = session?.incoming?.receive() ?: throw MaCommandException("receive", null, "no session")
            if (frame is Frame.Text) return json.parseToJsonElement(frame.readText()).jsonObject
        }
    }

    /**
     * Fails rather than dropping the frame when there is no session: a silently unsent command has
     * nobody to reply to it, so the caller would sit through its whole timeout to learn what is
     * already known here.
     */
    private suspend fun sendText(text: String) {
        val live = session ?: throw MaCommandException("send", null, "not connected to Music Assistant")
        live.send(Frame.Text(text))
    }

    /**
     * Run one user-facing intent with a log line either side. These are the calls a user watches a
     * spinner for, so when one fails the toast they get is backed by a logged reason — [command]
     * reports the server's own error code and details in it.
     */
    private suspend fun <T> intent(what: String, block: suspend () -> T): T {
        val start = TimeSource.Monotonic.markNow()
        try {
            return block().also { log("$what: ok in ${start.elapsedNow().inWholeMilliseconds}ms") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("$what: FAILED after ${start.elapsedNow().inWholeMilliseconds}ms: ${e.message}")
            throw e
        }
    }

    private fun log(message: String) = println("MusicAssistantAdapter: $message")

    /** Fallback MA base URL if the hello omits `base_url`: swap the ws scheme/path for http. */
    private fun deriveBaseUrl(): String =
        config.webSocketUrl
            .replaceFirst("wss://", "https://")
            .replaceFirst("ws://", "http://")
            .removeSuffix("/ws")

    private class MaAuthException(message: String) : Exception(message)
    private class MaCommandException(command: String, code: Int?, details: String?) :
        Exception("MA command '$command' failed (code=$code): $details")

    private companion object {
        val INITIAL_RECONNECT_MS = 1_000L
        val MAX_RECONNECT_MS = 30_000L
        val REQUEST_TIMEOUT_MS = 20_000L
        val BROWSE_REFRESH_MS = 5 * 60 * 1_000L
        // How long one slice of the Quick Picks pool stays on screen.
        val ROTATION_MS = 30 * 60 * 1_000L
        // Only a safety net — `queue*` push events drive the refreshes that matter.
        val QUEUE_POLL_MS = 30_000L
        val QUEUE_DEBOUNCE_MS = 750L
        val UP_NEXT_LIMIT = 20
        // [enqueue]'s own window, deeper than the UI's: a queued 50-track album must not push the
        // user block's bottom marker out of the slice the next enqueue diffs against.
        val ENQUEUE_WINDOW = 100
        // Sits well clear of the library so the rail never truncates as playlists are added.
        val PLAYLIST_LIMIT = 100
        // Per media type — four types × 24 is far more than the grid ever scrolls to.
        val SEARCH_LIMIT = 24
        // Measured ~1.4s cold / ~10ms cached against ytmusic alone; a search now fans out to Spotify
        // too, and each provider is a live API call, so the headroom is for the slowest of them
        // answering — a dead socket doesn't wait it out (see [sendText]).
        val SEARCH_TIMEOUT_MS = 15_000L
        // `top_tracks` is one provider "songs" fetch, cached for 7 days — measured 1.9s cold. The
        // headroom is for a cold provider round-trip, not for the catalogue walk this used to do.
        val ARTIST_TIMEOUT_MS = 15_000L
        // Building a multi-item queue is slower than a single play_media (~7s measured for 3 tracks).
        val PLAY_ALL_TIMEOUT_MS = 30_000L
        // The playable types the providers actually serve; radio/podcasts/audiobooks aren't configured.
        val SEARCH_MEDIA_TYPES = listOf("track", "album", "artist", "playlist")
    }
}
