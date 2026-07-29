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
import com.mattschoe.smarthome.data.ma.toMediaTrack
import com.mattschoe.smarthome.data.ma.upNextOffset
import com.mattschoe.smarthome.data.ma.withoutQueueItem
import com.mattschoe.smarthome.data.model.ArtistDetail
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.MusicSource
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
import kotlinx.coroutines.SupervisorJob
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
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Any other drop is retried on the backoff below.
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
        onConnected()
        reader.join() // returns when `incoming` closes; lets the webSocket block end and reconnect
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
        queueIdByRoom = idByRoom
        // Always-on auto-suggestions: keep "Don't Stop the Music" enabled on every room speaker queue so
        // the queue self-refills with YouTube-Music continuations. Idempotent — only touch queues that
        // are currently off, and re-enable if something turned it off (the user wants this as a default).
        val roomQueueIds = idByRoom.values.toSet()
        queues.filter { it.queue_id in roomQueueIds && !it.dont_stop_the_music_enabled }
            .forEach { enableDontStopTheMusic(it.queue_id) }
        val queueById = queues.associateBy { it.queue_id }
        val queuesByRoom = idByRoom.mapValues { (room, queueId) ->
            val queue = queueById[queueId]
            val offset = upNextOffset(queue?.current_index)
            fetchQueueItems(queueId, offset)
                .withoutQueueItem(queue?.current_item?.queue_item_id)
                .also { tracks ->
                }
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
        val shelves = mapRecommendations(folders, rotation)
        val playlists = mapPlaylists(playlistRows)
        // The Spotify side is derived from these same two replies — it needs no calls of its own.
        val spotifyPlaylists = mapSpotifyPlaylists(playlistRows)
        val spotifyRecentlyPlayed = mapRecentlyPlayed(folders, MusicSource.Spotify.providerDomain)
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
    suspend fun play(room: Room, uri: String, radio: Boolean) {
        val queueId = queueIdByRoom[room]
            ?: throw MaCommandException("player_queues/play_media", null,
                "no MA queue matched for $room (known: ${queueIdByRoom.keys.joinToString().ifBlank { "none" }})")
        command("player_queues/play_media", buildJsonObject {
            put("queue_id", queueId)
            put("media", uri)
            put("option", "replace")
            put("radio_mode", radio)
        })
        queueRefreshTrigger.trySend(Unit)
    }

    /**
     * Start [uris] on [room]'s speaker **in order** — `player_queues/play_media` takes `media` as a
     * JSON array and replaces the queue with exactly those items, first one playing. What comes after
     * the last is the queue's always-on Don't Stop the Music, which survives the `replace` (both
     * verified live against MA 2.9.9 / schema 31). Suspends until the reply and throws on failure,
     * like [play] — building a multi-item queue resolves streams, so it gets its own longer
     * [PLAY_ALL_TIMEOUT_MS].
     */
    suspend fun playAll(room: Room, uris: List<String>) {
        if (uris.isEmpty()) {
            return
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
    suspend fun artistDetail(uri: String): ArtistDetail {
        if (session == null) {
            return ArtistDetail.EMPTY
        }
        val ref = parseMaUri(uri)
        if (ref == null) {
            return ArtistDetail.EMPTY
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
        val detail = ArtistDetail(mapArtistTracks(tracks), mapArtistAlbums(albums))
        return detail
    }

    /**
     * Skip playback straight to [queueItemId] in [room]'s queue. MA's `player_queues/play_index`
     * takes either a positional index or a queue-item id for `index`; the id is what we hold, and it
     * survives the queue shifting under us between refreshes. Suspends/throws like [play] — the reply
     * arrives when the new track's stream is resolved.
     */
    suspend fun playQueueItem(room: Room, queueItemId: String) {
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
        }
    }

    /**
     * Search the configured music providers for [query]. Unlike the fire-and-forget playback intents
     * this one is `suspend` and lets failure through — the caller is a user waiting on a spinner, so a
     * short [SEARCH_TIMEOUT_MS] fails fast rather than hanging on the default 20 s, and a query issued
     * before the socket is up returns empty instead of burning that timeout.
     */
    suspend fun search(query: String): List<BrowseItem> {
        if (session == null) {
            return emptyList()
        }
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
        return mapSearchResults(results)
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
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw e
        } finally {
            pendingMutex.withLock { pending.remove(id) }
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

    private suspend fun sendText(text: String) {
        session?.send(Frame.Text(text))
    }

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
        // Sits well clear of the library so the rail never truncates as playlists are added.
        val PLAYLIST_LIMIT = 100
        // Per media type — four types × 24 is far more than the grid ever scrolls to.
        val SEARCH_LIMIT = 24
        // Measured ~1.4s cold / ~10ms cached; a user is watching the spinner, so don't wait 20s.
        val SEARCH_TIMEOUT_MS = 8_000L
        // `top_tracks` is one provider "songs" fetch, cached for 7 days — measured 1.9s cold. The
        // headroom is for a cold provider round-trip, not for the catalogue walk this used to do.
        val ARTIST_TIMEOUT_MS = 15_000L
        // Building a multi-item queue is slower than a single play_media (~7s measured for 3 tracks).
        val PLAY_ALL_TIMEOUT_MS = 30_000L
        // The playable types the providers actually serve; radio/podcasts/audiobooks aren't configured.
        val SEARCH_MEDIA_TYPES = listOf("track", "album", "artist", "playlist")
    }
}
