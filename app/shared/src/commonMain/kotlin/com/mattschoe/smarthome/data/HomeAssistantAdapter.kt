package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.ha.HaAreaDto
import com.mattschoe.smarthome.data.ha.HaDeviceDto
import com.mattschoe.smarthome.data.ha.HaEntityRegistryDto
import com.mattschoe.smarthome.data.ha.HaStateDto
import com.mattschoe.smarthome.data.ha.MEDIA_PLAYER_BY_ROOM
import com.mattschoe.smarthome.data.ha.RoomEntities
import com.mattschoe.smarthome.data.ha.SWITCH_LIGHTS_BY_ROOM
import com.mattschoe.smarthome.data.ha.discoverRoomEntities
import com.mattschoe.smarthome.data.ha.resolveSyncLeaders
import com.mattschoe.smarthome.data.model.ArtistDetail
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.CalendarState
import com.mattschoe.smarthome.data.model.ClimateState
import com.mattschoe.smarthome.data.model.HomeState
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.Warmth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A [HomeAdapter] backed by a live Home Assistant instance over its WebSocket API. It authenticates,
 * discovers which entities back each [Room] from the area/device/entity registries, takes an initial
 * snapshot, then subscribes to `state_changed` events and keeps [subscribe]'s [HomeState] live.
 *
 * Setters apply an **optimistic** local transition (reusing the pure `DashboardLogic` functions) for
 * snappy dial/slider feel, then fire the matching `call_service`; HA's echoed event reconciles truth.
 * Only entities we mapped are tracked — sensor churn never triggers a rebuild.
 *
 * Climate, calendar and todos have no entities in this home yet, so they are emitted **blank** (the
 * left card's climate tiles render "—", the calendar/todo lists are empty). The Media panel's queue
 * is likewise empty: HA's `media_player` exposes no standard play-queue.
 */
class HomeAssistantAdapter(private val config: HaConfig) : HomeAdapter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient { install(WebSockets) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(blankHome())

    // Short-lived optimistic overlay: fields the user just set that `rebuild()` must keep on top of the
    // HA-derived state until [RoomHold.deadline] passes, so HA's interim transition echoes can't revert
    // a value mid-drag. A thread-safe StateFlow (setter thread writes, connection thread reads) — no lock.
    private val holds = MutableStateFlow<Map<Room, RoomHold>>(emptyMap())

    // All touched only from the single connection coroutine (setup + read loop), so no locking needed.
    private var roomEntities: Map<Room, RoomEntities> = emptyMap()
    private var mappedEntityIds: Set<String> = emptySet()
    private val entityStates = mutableMapOf<String, HaStateDto>()

    private var nextId = 1
    private var reconnectDelay = INITIAL_RECONNECT_MS
    @Volatile private var session: DefaultClientWebSocketSession? = null

    init {
        scope.launch { connectionLoop() }
    }

    override fun subscribe(): StateFlow<HomeState> = _state.asStateFlow()

    // --- Device intents: optimistic local apply, then the matching HA service call ---

    // Light writes target the whole room (area), so every lamp in it responds. A switch-backed lamp is
    // driven separately via the `switch` domain: on at any brightness > 0, off at 0.
    override fun setBrightness(room: Room, value: Int) {
        hold(room) { it.copy(brightnessPct = value, isLightOn = value > 0) }
        _state.update { it.withBrightness(room, value) }
        areaId(room)?.let {
            // transition:0 → HA jumps to the target instead of ramping (no "ticks up 1%" echoes).
            callService("light", "turn_on", buildJsonObject { put("brightness_pct", value.coerceIn(0, 100)); put("transition", 0) }, areaTarget(it))
        }
        setSwitches(room, value > 0)
    }

    override fun setWarmth(room: Room, warmth: Warmth) {
        hold(room) { it.copy(lightWarmth = warmth, isLightOn = true) }
        _state.update { it.withWarmth(room, warmth) }
        areaId(room)?.let {
            callService("light", "turn_on", buildJsonObject { put("color_temp_kelvin", warmth.toKelvin()); put("transition", 0) }, areaTarget(it))
        }
        // Selecting a warmth turns the light on; a switch-backed lamp has no color temp, so just turn it on.
        setSwitches(room, true)
    }

    override fun toggleLight(room: Room) {
        // Decide from the pre-toggle aggregate so a mixed room resolves to a single on/off, not per-lamp flips.
        val wasOn = _state.value.rooms[room]?.isLightOn ?: false
        hold(room) { it.copy(isLightOn = !wasOn) }
        _state.update { it.toggleLight(room) }
        areaId(room)?.let {
            callService("light", if (wasOn) "turn_off" else "turn_on", null, areaTarget(it))
        }
        setSwitches(room, !wasOn)
    }

    override fun setVolume(room: Room, value: Int) {
        hold(room) { it.copy(volumePct = value) }
        _state.update { it.withVolume(room, value) }
        speaker(room)?.let {
            callService("media_player", "volume_set", buildJsonObject { put("volume_level", volumeLevelFromPct(value)) }, entityTarget(it))
        }
    }

    // Starting a specific item requires the Music Assistant connection; the HA adapter alone has no
    // browse/play-media source. Failing (rather than silently ignoring) lets the caller's pending-play
    // UI resolve into its "couldn't play" notice. The composite routes play() to the MA adapter.
    override suspend fun play(room: Room, uri: String, radio: Boolean) {
        throw IllegalStateException("play($room, $uri): no Music Assistant connection")
    }

    // HA's media_player exposes no play-queue at all, so there is nothing to skip into or reorder —
    // both queue intents belong to Music Assistant and the composite routes them there.
    override suspend fun playQueueItem(room: Room, queueItemId: String) {
        throw IllegalStateException("playQueueItem($room, $queueItemId): no Music Assistant connection")
    }

    // The queue is Music Assistant's — HA's media_player has none to reorder or replace.
    override fun moveQueueItem(room: Room, queueItemId: String, posShift: Int) = Unit

    override suspend fun playAll(room: Room, uris: List<String>) = Unit

    // Searching needs the music providers, which only Music Assistant reaches — no hits without it.
    override suspend fun search(query: String): List<BrowseItem> = emptyList()

    // Likewise the artist catalogue: it comes from the music providers behind Music Assistant.
    override suspend fun artistDetail(uri: String): ArtistDetail = ArtistDetail.EMPTY

    override fun togglePlay(room: Room) {
        val wasPlaying = _state.value.rooms[room]?.audio?.isPlaying ?: false
        hold(room) { it.copy(isPlaying = !wasPlaying) }
        _state.update { it.togglePlay(room) }
        speaker(room)?.let { callService("media_player", "media_play_pause", null, entityTarget(it)) }
    }

    override fun next(room: Room) {
        _state.update { it.next(room) }
        speaker(room)?.let { callService("media_player", "media_next_track", null, entityTarget(it)) }
    }

    override fun previous(room: Room) {
        _state.update { it.previous(room) }
        speaker(room)?.let { callService("media_player", "media_previous_track", null, entityTarget(it)) }
    }

    override fun seek(room: Room, positionSec: Int) {
        // A seek needs a projecting anchor, not just the optimistic write below: Sonos/HA won't
        // re-stamp `media_position` until the next state transition, so every rebuild until then
        // would revert the position to a projection from the *pre-seek* stamp.
        val track = _state.value.rooms[room]?.audio?.nowPlaying?.title
        hold(room) { it.copy(seek = SeekHold(positionSec, track, TimeSource.Monotonic.markNow())) }
        _state.update { it.seek(room, positionSec) }
        speaker(room)?.let {
            callService("media_player", "media_seek", buildJsonObject { put("seek_position", positionSec) }, entityTarget(it))
        }
    }

    override fun setShuffle(room: Room, shuffle: Boolean) {
        _state.update { it.setShuffle(room, shuffle) }
        speaker(room)?.let {
            callService("media_player", "shuffle_set", buildJsonObject { put("shuffle", shuffle) }, entityTarget(it))
        }
    }

    override fun setRepeat(room: Room, mode: RepeatMode) {
        _state.update { it.setRepeat(room, mode) }
        speaker(room)?.let {
            callService("media_player", "repeat_set", buildJsonObject { put("repeat", mode.toHaRepeat()) }, entityTarget(it))
        }
    }

    // Grouping is the one pair of intents with no optimistic apply. A [RoomHold] encodes "not held"
    // as null, so a *leave* (which sets the leader to null) can't be expressed there — and an
    // un-held local write would be reverted by the very next rebuild, flickering the label. Instead
    // the group state flips when HA echoes the players' new `group_members`, one normal
    // `state_changed` away; the ViewModel guards re-taps until then.
    override fun joinAudio(leader: Room, follower: Room) {
        val leaderSpeaker = speaker(leader)
        val followerSpeaker = speaker(follower)
        if (leaderSpeaker == null || followerSpeaker == null) return
        callService(
            "media_player",
            "join",
            buildJsonObject { putJsonArray("group_members") { add(followerSpeaker) } },
            entityTarget(leaderSpeaker),
        )
    }

    override fun unjoinAudio(room: Room) {
        speaker(room)?.let { callService("media_player", "unjoin", null, entityTarget(it)) }
    }

    // No todo list is configured in this home yet, so the todo intents are safe no-ops for now.
    override fun addTodo(due: LocalDate, label: String) {}
    override fun toggleTodo(id: String) {}
    override fun editTodo(id: String, label: String) {}

    private fun areaId(room: Room): String? = roomEntities[room]?.areaId
    // Reads aggregate over both real lights and switch-backed lamps (a lit switch counts as 100%).
    private fun lightsOf(room: Room): List<HaStateDto> =
        (roomEntities[room]?.lightIds.orEmpty() + roomEntities[room]?.switchIds.orEmpty()).mapNotNull { entityStates[it] }
    private fun switchesOf(room: Room): List<String> = roomEntities[room]?.switchIds.orEmpty()
    private fun speaker(room: Room): String? = roomEntities[room]?.mediaPlayerId

    /** Drive the room's switch-backed lamps via the `switch` domain; a no-op for rooms without any. */
    private fun setSwitches(room: Room, on: Boolean) {
        switchesOf(room).forEach {
            callService("switch", if (on) "turn_on" else "turn_off", null, entityTarget(it))
        }
    }

    /** Merge [edit] into [room]'s optimistic hold and (re)arm its deadline. Also drops any expired holds. */
    private fun hold(room: Room, edit: (RoomHold) -> RoomHold) {
        holds.update { current ->
            // A live seek anchor outlives the scalar deadline (it has its own release rules in
            // resolveSeek), so an expired hold that still carries one must not be dropped here.
            val kept = current.filterValues { !it.deadline.hasPassedNow() || it.seek != null }
            kept + (room to edit(kept[room] ?: RoomHold()).copy(deadline = TimeSource.Monotonic.markNow() + HOLD))
        }
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
                    }
                }
            } catch (e: HaAuthException) {
                // A bad token will never succeed on retry — stop reconnecting.
                println("HomeAssistantAdapter: authentication failed: ${e.message}")
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("HomeAssistantAdapter: connection lost (${e.message}); reconnecting in ${reconnectDelay}ms")
            }
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_MS)
        }
    }

    /** Auth handshake → discovery → snapshot → subscribe → read loop. Returns when the socket closes. */
    private suspend fun DefaultClientWebSocketSession.runSession() {
        receiveJson() // auth_required
        send(buildJsonObject { put("type", "auth"); put("access_token", config.token) }.toString())
        val auth = receiveJson()
        if (auth["type"]?.jsonPrimitive?.content != "auth_ok") {
            throw HaAuthException(auth["message"]?.jsonPrimitive?.content ?: "auth_invalid")
        }

        val areas = json.decodeFromJsonElement<List<HaAreaDto>>(request("config/area_registry/list"))
        val devices = json.decodeFromJsonElement<List<HaDeviceDto>>(request("config/device_registry/list"))
        val entities = json.decodeFromJsonElement<List<HaEntityRegistryDto>>(request("config/entity_registry/list"))
        roomEntities = discoverRoomEntities(areas, devices, entities, SWITCH_LIGHTS_BY_ROOM, MEDIA_PLAYER_BY_ROOM)
        mappedEntityIds = roomEntities.values.flatMap { it.lightIds + it.switchIds + listOfNotNull(it.mediaPlayerId) }.toSet()

        val states = json.decodeFromJsonElement<List<HaStateDto>>(request("get_states"))
        entityStates.clear()
        states.forEach { if (it.entity_id in mappedEntityIds) entityStates[it.entity_id] = it }
        rebuild()

        request("subscribe_events", buildJsonObject { put("event_type", "state_changed") })
        reconnectDelay = INITIAL_RECONNECT_MS // healthy connection — reset backoff

        for (frame in incoming) {
            if (frame is Frame.Text) handleEvent(frame.readText())
        }
    }

    /** A `state_changed` event for a mapped entity patches its state and rebuilds; others are ignored. */
    private fun handleEvent(text: String) {
        val obj = json.parseToJsonElement(text).jsonObject
        if (obj["type"]?.jsonPrimitive?.content != "event") return
        val data = obj["event"]?.jsonObject?.get("data")?.jsonObject ?: return
        val entityId = data["entity_id"]?.jsonPrimitive?.content ?: return
        if (entityId !in mappedEntityIds) return

        val newState = data["new_state"]
        if (newState == null || newState is JsonNull) entityStates.remove(entityId)
        else entityStates[entityId] = json.decodeFromJsonElement<HaStateDto>(newState)
        rebuild()
    }

    /** Send a request with a fresh id and wait for its `result`, skipping unrelated frames. */
    private suspend fun DefaultClientWebSocketSession.request(type: String, extra: JsonObject? = null): JsonElement {
        val id = nextId++
        send(buildJsonObject {
            put("id", id)
            put("type", type)
            extra?.forEach { (k, v) -> put(k, v) }
        }.toString())
        while (true) {
            val msg = receiveJson()
            if (msg["type"]?.jsonPrimitive?.content == "result" &&
                msg["id"]?.jsonPrimitive?.intOrNull == id
            ) {
                return msg["result"] ?: JsonNull
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.receiveJson(): JsonObject {
        while (true) {
            val frame = incoming.receive()
            if (frame is Frame.Text) return json.parseToJsonElement(frame.readText()).jsonObject
        }
    }

    private fun callService(domain: String, service: String, data: JsonObject?, target: JsonObject) {
        scope.launch {
            send(buildJsonObject {
                put("id", nextId++)
                put("type", "call_service")
                put("domain", domain)
                put("service", service)
                if (data != null) put("service_data", data)
                put("target", target)
            }.toString())
        }
    }

    private fun areaTarget(areaId: String): JsonObject = buildJsonObject { put("area_id", areaId) }
    private fun entityTarget(entityId: String): JsonObject = buildJsonObject { put("entity_id", entityId) }

    private suspend fun send(text: String) {
        session?.send(Frame.Text(text))
    }

    // --- HA entity states -> domain HomeState ---

    private fun rebuild() {
        val activeHolds = holds.value
        val previous = _state.value
        val now = TimeSource.Monotonic.markNow()
        // Holds that survive this pass (a field still held, un-settled). Rooms whose hold fully settled
        // are dropped below so a later external change is reflected instead of masked.
        val survivors = mutableMapOf<Room, RoomHold>()
        // Whose playback each speaker room is following, read off the players' `group_members`.
        val syncLeaders = resolveSyncLeaders(
            groupMembersByRoom = Room.entries.filter { it.hasSpeaker }
                .associateWith { room -> speaker(room)?.let { entityStates[it] }.attrStringList("group_members") },
            roomByEntityId = roomEntities.entries
                .mapNotNull { (room, entities) -> entities.mediaPlayerId?.let { it to room } }
                .toMap(),
        )
        _state.value = HomeState(
            rooms = Room.entries.associateWith { room ->
                val lights = lightsOf(room)
                val speaker = speaker(room)?.let { entityStates[it] }
                val onLights = lights.filter { it.state == "on" }
                val fromHa = RoomState(
                    // When the room's lamps are all off HA drops the brightness attribute; carry the
                    // last-known level forward instead of collapsing to 0 so the dial stays put and
                    // only mutes to grey (the off-state contract in CenterCard's BrightnessDial).
                    brightnessPct = aggregateBrightnessPct(onLights, fallback = previous.rooms[room]?.brightnessPct ?: 0),
                    isLightOn = onLights.isNotEmpty(),
                    // Take the warmth of the first lit lamp reporting a color temperature.
                    lightWarmth = warmthFromKelvin(onLights.firstNotNullOfOrNull { it.attrInt("color_temp_kelvin") }),
                    audio = if (room.hasSpeaker) buildAudio(speaker, syncLeaders[room]) else null,
                )
                val hold = activeHolds[room] ?: return@associateWith fromHa
                val (display, reduced) = reconcileHold(hold, fromHa, now)
                reduced?.let { survivors[room] = it }
                display
            },
            climate = ClimateState(null, null, null, null),
            playlists = emptyList(),
            quickPicks = emptyList(),
            mixedForYou = emptyList(),
            calendar = CalendarState(events = emptyList(), todos = emptyList()),
        )
        pruneSettledHolds(activeHolds, survivors)
    }

    /**
     * Write back the reconciled holds, dropping any that fully settled. Reference-identity guards the
     * setter thread: a hold that a concurrent setter re-armed (a fresh [RoomHold] instance) since our
     * [snapshot] is left untouched, so we never clobber a just-armed optimistic value.
     */
    private fun pruneSettledHolds(snapshot: Map<Room, RoomHold>, survivors: Map<Room, RoomHold>) {
        holds.update { current ->
            buildMap {
                for ((room, hold) in current) {
                    if (hold === snapshot[room]) survivors[room]?.let { put(room, it) }
                    else put(room, hold) // added or re-armed concurrently — keep the newer hold
                }
            }
        }
    }

    /** Mean brightness across the room's lit lamps; a lit non-dimmable lamp counts as 100%. [fallback] if all off. */
    private fun aggregateBrightnessPct(onLights: List<HaStateDto>, fallback: Int): Int {
        if (onLights.isEmpty()) return fallback
        val pcts = onLights.map { light ->
            light.attrInt("brightness")?.let { brightnessPctFrom255(it) } ?: 100
        }
        return pcts.average().roundToInt()
    }

    private fun buildAudio(mp: HaStateDto?, syncLeader: Room?): AudioState {
        if (mp == null) return AudioState(volumePct = 0, isPlaying = false, nowPlaying = null, positionSec = 0, queue = emptyList())
        val nowPlaying = mp.attrString("media_title")?.let { title ->
            MediaTrack(
                title = title,
                artist = mp.attrString("media_artist") ?: "",
                album = mp.attrString("media_album_name"),
                artworkUrl = artworkUrl(mp.attrString("entity_picture")),
                durationSec = mp.attrInt("media_duration") ?: 0,
            )
        }
        val audio = AudioState(
            volumePct = volumePctFromLevel(mp.attrDouble("volume_level")),
            isPlaying = mp.state == "playing",
            nowPlaying = nowPlaying,
            // HA freezes `media_position` at `media_position_updated_at`; project it to now while
            // playing, or the scrubber only moves when some other state change happens to come in.
            positionSec = livePositionSec(
                positionSec = mp.attrInt("media_position"),
                updatedAtIso = mp.attrString("media_position_updated_at"),
                isPlaying = mp.state == "playing",
                now = Clock.System.now(),
            ),
            queue = emptyList(), // HA media_player exposes no standard play-queue
            isShuffle = mp.attrBool("shuffle") ?: false,
            repeat = repeatModeFromHa(mp.attrString("repeat")),
            syncLeader = syncLeader,
        )
        return audio
    }

    /** Resolve HA's `entity_picture` (often a relative `/api/...` path) to an absolute URL. */
    private fun artworkUrl(path: String?): String? = when {
        path == null -> null
        path.startsWith("http") -> path.atFullProxySize()
        else -> (config.httpBase + path).atFullProxySize()
    }

    private fun blankHome(): HomeState = HomeState(
        rooms = Room.entries.associateWith { room ->
            RoomState(
                brightnessPct = 0,
                isLightOn = false,
                lightWarmth = Warmth.Neutral,
                audio = if (room.hasSpeaker) AudioState(0, false, null, 0, emptyList()) else null,
            )
        },
        climate = ClimateState(null, null, null, null),
        playlists = emptyList(),
        quickPicks = emptyList(),
        mixedForYou = emptyList(),
        calendar = CalendarState(events = emptyList(), todos = emptyList()),
    )

    // --- Safe attribute readers (an absent or null attribute yields null, never throws) ---

    private fun HaStateDto?.attrInt(key: String): Int? = this?.prim(key)?.intOrNull
    private fun HaStateDto?.attrDouble(key: String): Double? = this?.prim(key)?.doubleOrNull
    private fun HaStateDto?.attrBool(key: String): Boolean? = this?.prim(key)?.booleanOrNull
    private fun HaStateDto?.attrString(key: String): String? =
        this?.prim(key)?.let { if (it.isString) it.content else null }

    /** A list-valued attribute (e.g. `group_members`); empty when absent, non-string entries dropped. */
    private fun HaStateDto?.attrStringList(key: String): List<String> =
        (this?.attributes?.get(key) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .orEmpty()

    private fun HaStateDto.prim(key: String): JsonPrimitive? =
        (attributes[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

    private class HaAuthException(message: String) : Exception(message)

    private companion object {
        const val INITIAL_RECONNECT_MS = 1_000L
        const val MAX_RECONNECT_MS = 30_000L
        val HOLD = 3_000.milliseconds
    }
}
