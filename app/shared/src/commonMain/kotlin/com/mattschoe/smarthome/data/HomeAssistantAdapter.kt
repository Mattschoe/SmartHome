package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.ha.HaAreaDto
import com.mattschoe.smarthome.data.ha.HaCalendarEventDto
import com.mattschoe.smarthome.data.ha.HaDeviceDto
import com.mattschoe.smarthome.data.ha.HaEntityRegistryDto
import com.mattschoe.smarthome.data.ha.HaStateDto
import com.mattschoe.smarthome.data.ha.HaTodoItemsDto
import com.mattschoe.smarthome.data.ha.MEDIA_PLAYER_BY_ROOM
import com.mattschoe.smarthome.data.ha.RoomEntities
import com.mattschoe.smarthome.data.ha.SWITCH_LIGHTS_BY_ROOM
import com.mattschoe.smarthome.data.ha.discoverCalendarSources
import com.mattschoe.smarthome.data.ha.discoverRoomEntities
import com.mattschoe.smarthome.data.ha.discoverTodoEntity
import com.mattschoe.smarthome.data.ha.discoverWeatherEntity
import com.mattschoe.smarthome.data.ha.feelsLikeC
import com.mattschoe.smarthome.data.ha.mapCalendarEvents
import com.mattschoe.smarthome.data.ha.mapTodoItems
import com.mattschoe.smarthome.data.ha.resolveSyncLeaders
import com.mattschoe.smarthome.data.ha.temperatureToC
import com.mattschoe.smarthome.data.ha.weatherConditionFrom
import com.mattschoe.smarthome.data.ha.windSpeedToMs
import com.mattschoe.smarthome.data.model.ArtistDetail
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.CalendarState
import com.mattschoe.smarthome.data.model.ClimateState
import com.mattschoe.smarthome.data.model.HomeState
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.RecurrenceRange
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.data.model.Warmth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
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
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
 * Calendars and todos come from the same instance but over their own channels: events are fetched
 * over REST for a rolling window (HA has no WebSocket command that lists a date range), while the
 * todo list arrives as a push subscription. Climate is backed by the home's single `weather.*` entity
 * — it fills the outdoor tile alone, the indoor/energy tiles have no sensors yet and stay "—" — and
 * the Media panel's queue is empty: HA's `media_player` exposes no standard play-queue.
 */
class HomeAssistantAdapter(
    private val config: HaConfig,
    private val cache: CalendarCache? = null,
) : HomeAdapter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient { install(WebSockets) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Calendar/todo data, kept beside the entity states as the other half of what `rebuild()` reads.
    // Flows rather than plain fields: todo writes apply optimistically from the caller's thread while
    // the connection coroutine writes the backend's echo.
    private val calendarSources = MutableStateFlow<List<CalendarSource>>(emptyList())
    private val calendarEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    private val todoItems = MutableStateFlow<List<TodoItem>>(emptyList())
    // True until a live fetch lands — what the panel labels as showing cached, possibly outdated data.
    private val calendarStale = MutableStateFlow(true)
    @Volatile private var todoEntityId: String? = null
    @Volatile private var weatherEntityId: String? = null
    // The list the live subscription follows; null before one is taken out and after every drop,
    // since a socket takes its subscriptions with it.
    @Volatile private var subscribedTodoEntity: String? = null

    // Coalesces calendar refetch requests (panel opened, month navigated, after a write, the slow
    // poll) into one fetch at a time. Conflated, so a request made while disconnected fires on
    // reconnect instead of piling up.
    private val calendarRefreshTrigger = Channel<Unit>(Channel.CONFLATED)

    // Same shape for registry changes: HA reports them one entity at a time, and the whole burst
    // should cost a single re-discovery.
    private val rediscoverTrigger = Channel<Unit>(Channel.CONFLATED)

    private val _state = MutableStateFlow(blankHome())

    // Short-lived optimistic overlay: fields the user just set that `rebuild()` must keep on top of the
    // HA-derived state until [RoomHold.deadline] passes, so HA's interim transition echoes can't revert
    // a value mid-drag. A thread-safe StateFlow (setter thread writes, connection thread reads) — no lock.
    private val holds = MutableStateFlow<Map<Room, RoomHold>>(emptyMap())

    // The discovered shape of the home, and the live states of the entities it maps. Written by both
    // the read loop (`state_changed`) and re-discovery (a registry change), so all three are published
    // rather than mutated: an immutable map swapped atomically needs no lock on either side.
    @Volatile private var roomEntities: Map<Room, RoomEntities> = emptyMap()
    @Volatile private var mappedEntityIds: Set<String> = emptySet()
    private val entityStates = MutableStateFlow<Map<String, HaStateDto>>(emptyMap())

    // Request/response correlation, plus the per-subscription event handlers. The single read loop
    // completes the deferreds and fans events to their handler; [request] awaits its own reply.
    private val pendingMutex = Mutex()
    private var nextId = 1
    private val pending = mutableMapOf<Int, CompletableDeferred<JsonObject>>()
    private val eventHandlers = mutableMapOf<Int, (JsonObject) -> Unit>()

    private var reconnectDelay = INITIAL_RECONNECT_MS
    @Volatile private var session: DefaultClientWebSocketSession? = null

    init {
        // Render the last-known calendar (marked stale) before the socket is even up: the household
        // has no other calendar client, so an unreachable box must not mean an empty calendar.
        cache?.read()?.let { cached ->
            calendarSources.value = cached.sources
            calendarEvents.value = cached.events
            todoItems.value = cached.todos
            _state.value = blankHome()
        }
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

    /**
     * Ask the backend for a fresh calendar window. Unlike lights and media, calendar events are not
     * pushed — the window is fetched — so opening the panel or navigating months is a hint that now
     * is a good moment to refetch (and to force a subscribed calendar's coordinator to re-poll).
     * Which panel is showing stays entirely the ViewModel's business; this only receives the nudge.
     */
    override fun refreshCalendar() {
        calendarRefreshTrigger.trySend(Unit)
    }

    // Todo intents. Each applies the matching pure transition optimistically (so the checkbox never
    // lags the tap) and then calls the HA service; the list's push subscription re-publishes the
    // whole list moments later, replacing the optimistic row — and its id — with HA's own.

    /**
     * Add a todo due on [due]. HA mints the real `uid`, so the optimistic row carries a temporary one
     * until the echo lands (rows are keyed by id, so it re-keys rather than rebuilds).
     */
    @OptIn(ExperimentalUuidApi::class)
    override fun addTodo(due: LocalDate, label: String) {
        val entityId = todoEntityId ?: return
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        mutateTodos { it.addTodo(Uuid.random().toString(), due, trimmed) }
        callService(
            "todo",
            "add_item",
            buildJsonObject { put("item", trimmed); put("due_date", due.toString()) },
            entityTarget(entityId),
        )
    }

    override fun toggleTodo(id: String) {
        val entityId = todoEntityId ?: return
        val wasDone = todoItems.value.firstOrNull { it.id == id }?.done ?: return
        mutateTodos { it.toggleTodo(id) }
        callService(
            "todo",
            "update_item",
            // Always address by uid: `update_item` matches on uid *or* summary, and two todos may
            // well share a summary.
            buildJsonObject { put("item", id); put("status", if (wasDone) "needs_action" else "completed") },
            entityTarget(entityId),
        )
    }

    /** Set a todo's label; committing a blank one removes it — the UI's delete escape hatch. */
    override fun editTodo(id: String, label: String) {
        val entityId = todoEntityId ?: return
        val trimmed = label.trim()
        mutateTodos { it.editTodo(id, trimmed) }
        if (trimmed.isEmpty()) {
            // `remove_item` takes a *list* of items, unlike the other two.
            callService(
                "todo",
                "remove_item",
                buildJsonObject { putJsonArray("item") { add(id) } },
                entityTarget(entityId),
            )
        } else {
            callService(
                "todo",
                "update_item",
                buildJsonObject { put("item", id); put("rename", trimmed) },
                entityTarget(entityId),
            )
        }
    }

    /**
     * Apply a pure todo transition to the published state *and* to the adapter's own todo field, so
     * the optimistic edit survives the next `rebuild()` (which reads the field, not the old state).
     */
    private fun mutateTodos(transform: (HomeState) -> HomeState) {
        val next = transform(_state.value)
        todoItems.value = next.calendar.todos
        _state.value = next
    }

    // Calendar writes. No optimistic apply: unlike a checkbox, a saved event is worth being sure
    // about, and the caller is already waiting on the reply — so the panel updates from the refetch
    // these trigger, which is also the only way an expanded recurring series comes back correct.

    override suspend fun createEvent(sourceId: String, draft: CalendarEventDraft) {
        writableSource(sourceId)
        request("calendar/event/create", buildJsonObject {
            put("entity_id", sourceId)
            put("event", draft.toEventJson())
        })
        refreshCalendar()
    }

    override suspend fun updateEvent(
        sourceId: String,
        uid: String,
        draft: CalendarEventDraft,
        recurrenceId: String?,
        range: RecurrenceRange,
    ) {
        writableSource(sourceId)
        request("calendar/event/update", buildJsonObject {
            put("entity_id", sourceId)
            put("uid", uid)
            putRecurrence(recurrenceId, range)
            put("event", draft.toEventJson())
        })
        refreshCalendar()
    }

    override suspend fun deleteEvent(
        sourceId: String,
        uid: String,
        recurrenceId: String?,
        range: RecurrenceRange,
    ) {
        writableSource(sourceId)
        request("calendar/event/delete", buildJsonObject {
            put("entity_id", sourceId)
            put("uid", uid)
            putRecurrence(recurrenceId, range)
        })
        refreshCalendar()
    }

    /** The calendar [sourceId] names, or a failure — a read-only calendar is never a write target. */
    private fun writableSource(sourceId: String): CalendarSource {
        val source = calendarSources.value.firstOrNull { it.id == sourceId }
            ?: throw IllegalArgumentException("unknown calendar '$sourceId'")
        require(source.canWrite) { "calendar '${source.displayName}' is read-only" }
        return source
    }

    /**
     * Scope a write to one occurrence of a recurring series. Both keys are omitted for a plain event:
     * a `recurrence_id` on a non-recurring event is meaningless, and an empty range is HA's own
     * "just this one".
     */
    private fun JsonObjectBuilder.putRecurrence(recurrenceId: String?, range: RecurrenceRange) {
        if (recurrenceId == null) return
        put("recurrence_id", recurrenceId)
        if (range == RecurrenceRange.ThisAndFuture) put("recurrence_range", "THISANDFUTURE")
    }

    /**
     * The `event` payload of a calendar write. All-day events carry plain dates, timed ones local
     * date-times — the same either/or Home Assistant sends back on the read side.
     */
    private fun CalendarEventDraft.toEventJson(): JsonObject = buildJsonObject {
        put("summary", summary)
        if (allDay) {
            put("dtstart", start.date.toString())
            put("dtend", end.date.toString())
        } else {
            put("dtstart", start.toString())
            put("dtend", end.toString())
        }
        description?.let { put("description", it) }
        location?.let { put("location", it) }
        rrule?.let { put("rrule", it) }
    }

    private fun areaId(room: Room): String? = roomEntities[room]?.areaId
    // Reads aggregate over both real lights and switch-backed lamps (a lit switch counts as 100%).
    private fun lightsOf(room: Room): List<HaStateDto> =
        (roomEntities[room]?.lightIds.orEmpty() + roomEntities[room]?.switchIds.orEmpty())
            .mapNotNull { entityStates.value[it] }
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
                        failPending()
                        // Subscriptions die with the socket; the next session takes them out again.
                        subscribedTodoEntity = null
                        // Whatever calendar is on screen is now last-known, not live — say so rather
                        // than let it read as current until the socket comes back.
                        calendarStale.value = true
                        rebuild()
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

    /**
     * Auth handshake → read loop → discovery → snapshot → subscriptions. Returns when the socket
     * closes. The handshake runs *before* the read loop starts: its frames carry no `id`, so the
     * dispatcher has nothing to correlate them by, and they are strictly sequential anyway.
     */
    private suspend fun DefaultClientWebSocketSession.runSession() = coroutineScope {
        receiveJson() // auth_required
        sendText(buildJsonObject { put("type", "auth"); put("access_token", config.token) }.toString())
        val auth = receiveJson()
        if (auth["type"]?.jsonPrimitive?.content != "auth_ok") {
            throw HaAuthException(auth["message"]?.jsonPrimitive?.content ?: "auth_invalid")
        }

        val reader = launch { readLoop() }

        discover()

        request("subscribe_events", buildJsonObject { put("event_type", "state_changed") }) {
            handleStateChanged(it)
        }
        // Registry edits — a calendar recolored or renamed, a lamp moved into a room, a todo list
        // created — are not `state_changed` events, so without these the home's shape would stay
        // frozen as it was at connect: on a wall tablet, until someone restarts the app.
        REGISTRY_EVENTS.forEach { eventType ->
            request("subscribe_events", buildJsonObject { put("event_type", eventType) }) {
                rediscoverTrigger.trySend(Unit)
            }
        }
        reconnectDelay = INITIAL_RECONNECT_MS // healthy connection — reset backoff
        onConnected()

        reader.join() // returns when `incoming` closes; lets the webSocket block end and reconnect
    }

    /**
     * Read the home's shape: which entities back each room, which weather entity backs the climate
     * glance, the calendars (with the colors set on them in Home Assistant) and which todo list can
     * carry due dates. Runs at connect and again on every registry change, so an edit made in HA lands
     * without a reconnect.
     */
    private suspend fun discover() {
        val areas = json.decodeFromJsonElement<List<HaAreaDto>>(request("config/area_registry/list"))
        val devices = json.decodeFromJsonElement<List<HaDeviceDto>>(request("config/device_registry/list"))
        val entities = json.decodeFromJsonElement<List<HaEntityRegistryDto>>(request("config/entity_registry/list"))
        roomEntities = discoverRoomEntities(areas, devices, entities, SWITCH_LIGHTS_BY_ROOM, MEDIA_PLAYER_BY_ROOM)

        val states = json.decodeFromJsonElement<List<HaStateDto>>(request("get_states"))
        // The weather entity is found in the snapshot but has to join the mapped set before the states
        // are filtered by it: `handleStateChanged` drops anything unmapped, so an id left out here
        // would take its initial value and then never update again.
        weatherEntityId = discoverWeatherEntity(states)
        mappedEntityIds = roomEntities.values
            .flatMap { it.lightIds + it.switchIds + listOfNotNull(it.mediaPlayerId) }
            .toSet() + listOfNotNull(weatherEntityId)
        entityStates.value = states.filter { it.entity_id in mappedEntityIds }.associateBy { it.entity_id }
        // Calendars and the todo list are discovered from the same snapshot — no second round-trip.
        // Their colors are a registry option rather than an entity attribute, hence both inputs.
        calendarSources.value = discoverCalendarSources(states, entities)
        todoEntityId = discoverTodoEntity(states)
        // A home that lost its todo list keeps no rows from it: the checklist says there is no list.
        if (todoEntityId == null) todoItems.value = emptyList()
        rebuild()
        // Follows the list across a change, and re-subscribes on a fresh socket (which drops them all).
        if (todoEntityId != subscribedTodoEntity) subscribeTodos()
    }

    /** Background work kicked off once the session is live: keeping the calendar window fetched. */
    private fun CoroutineScope.onConnected() {
        launch { calendarRefreshConsumer() }
        launch { calendarPollLoop() }
        launch { rediscoverConsumer() }
    }

    /**
     * Re-read the registries after Home Assistant reports a change. Debounced ahead of the work: HA
     * emits one event per entity, so adding an integration arrives as a burst that should cost a
     * single pass. A newly added or recolored calendar also wants its events now rather than at the
     * next slow poll.
     */
    private suspend fun rediscoverConsumer() {
        for (unit in rediscoverTrigger) {
            delay(REDISCOVER_DEBOUNCE_MS)
            runCatching { discover() }
                .onFailure { println("HomeAssistantAdapter: re-discovery failed: ${it.message}") }
            calendarRefreshTrigger.trySend(Unit)
        }
    }

    /**
     * The todo list pushes its **whole** contents on every change, so there is nothing to diff and
     * no polling — a change made on the other phone lands here as the next event. A home with no
     * due-date-capable list simply has no subscription (and inert todo intents).
     *
     * The handler checks that it still speaks for the current list: when the list changes under a
     * live session the old subscription keeps pushing (it is only dropped with the socket), and its
     * events must not overwrite the new list's.
     */
    private suspend fun subscribeTodos() {
        val entityId = todoEntityId ?: return
        runCatching {
            request("todo/item/subscribe", buildJsonObject { put("entity_id", entityId) }) { event ->
                if (todoEntityId == entityId) handleTodoItems(event)
            }
        }
            .onSuccess { subscribedTodoEntity = entityId }
            .onFailure { println("HomeAssistantAdapter: todo subscribe failed: ${it.message}") }
    }

    private fun handleTodoItems(event: JsonObject) {
        val dto = runCatching { json.decodeFromJsonElement<HaTodoItemsDto>(event) }.getOrNull() ?: return
        todoItems.value = mapTodoItems(dto.items, fallbackDue = today())
        persistCalendar()
        rebuild()
    }

    // --- Calendar reads (REST; HA has no WebSocket command that lists a date range) ---

    /** Serialize refetches: one at a time, with a short trailing debounce to coalesce bursts. */
    private suspend fun calendarRefreshConsumer() {
        for (unit in calendarRefreshTrigger) {
            runCatching { refreshCalendarNow() }
                .onFailure { println("HomeAssistantAdapter: calendar fetch failed: ${it.message}") }
            delay(CALENDAR_DEBOUNCE_MS)
        }
    }

    /**
     * Keeps the window fresh without anything having to ask. Also how the window follows the date:
     * it is recomputed from *today* on every fetch, so a wall tablet left running rolls over on its
     * own. The first tick fires the initial load immediately.
     */
    private suspend fun calendarPollLoop() {
        while (true) {
            calendarRefreshTrigger.trySend(Unit)
            delay(CALENDAR_POLL_MS)
        }
    }

    /**
     * Fetch every calendar's window and republish. Read-only sources are poked first: a subscribed
     * calendar is coordinator-backed and polled once a day by default, which is far too stale for a
     * work roster, and `homeassistant.update_entity` is what forces it to refresh now.
     */
    private suspend fun refreshCalendarNow() {
        val sources = calendarSources.value
        if (sources.isEmpty()) {
            // A home with no calendar entities has nothing to fetch — that is an empty calendar, not
            // an out-of-date one, so it must not be labelled offline.
            calendarStale.value = false
            rebuild()
            return
        }
        val readOnly = sources.filterNot { it.canWrite }.map { it.id }
        if (readOnly.isNotEmpty()) {
            runCatching {
                request("call_service", buildJsonObject {
                    put("domain", "homeassistant")
                    put("service", "update_entity")
                    putJsonObject("target") { putJsonArray("entity_id") { readOnly.forEach { add(it) } } }
                })
            }
        }
        val today = today()
        val start = today.plus(-CALENDAR_WINDOW_BACK_MONTHS, DateTimeUnit.MONTH)
        val end = today.plus(CALENDAR_WINDOW_FORWARD_MONTHS, DateTimeUnit.MONTH)
        val events = sources.flatMap { source ->
            mapCalendarEvents(source.id, fetchEvents(source.id, start, end), TimeZone.currentSystemDefault())
        }
        calendarEvents.value = sortCalendarEvents(events)
        calendarStale.value = false
        persistCalendar()
        rebuild()
    }

    /** One calendar's events between [start] and [end] (both days inclusive of their whole span). */
    private suspend fun fetchEvents(entityId: String, start: LocalDate, end: LocalDate): List<HaCalendarEventDto> {
        val response = client.get("${config.httpBase}/api/calendars/$entityId") {
            header(HttpHeaders.Authorization, "Bearer ${config.token}")
            parameter("start", LocalDateTime(start, LocalTime(0, 0)).toString())
            parameter("end", LocalDateTime(end, LocalTime(0, 0)).toString())
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("GET /api/calendars/$entityId -> ${response.status}")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** Write the current calendar window to the offline cache, if one is configured. */
    private fun persistCalendar() {
        cache?.write(
            CachedCalendar(
                sources = calendarSources.value,
                events = calendarEvents.value,
                todos = todoItems.value,
            )
        )
    }

    /**
     * The single consumer of `incoming`. Every frame carries the `id` of the request or subscription
     * it belongs to: a `result` completes that request's awaiter, an `event` goes to the handler
     * registered for that subscription — so several subscriptions can run side by side.
     */
    private suspend fun DefaultClientWebSocketSession.readLoop() {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val obj = runCatching { json.parseToJsonElement(frame.readText()).jsonObject }.getOrNull() ?: continue
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: continue
            when (obj["type"]?.jsonPrimitive?.content) {
                // A reply with no awaiter is one whose caller already timed out — drop it.
                "result" -> pendingMutex.withLock { pending.remove(id) }?.complete(obj)
                "event" -> {
                    val handler = pendingMutex.withLock { eventHandlers[id] }
                    val event = obj["event"]?.jsonObject
                    if (handler != null && event != null) handler(event)
                }
            }
        }
    }

    /** A `state_changed` event for a mapped entity patches its state and rebuilds; others are ignored. */
    private fun handleStateChanged(event: JsonObject) {
        val data = event["data"]?.jsonObject ?: return
        val entityId = data["entity_id"]?.jsonPrimitive?.content ?: return
        if (entityId !in mappedEntityIds) return

        val newState = data["new_state"]
        if (newState == null || newState is JsonNull) entityStates.update { it - entityId }
        else entityStates.update { it + (entityId to json.decodeFromJsonElement<HaStateDto>(newState)) }
        rebuild()
    }

    /**
     * Send a command with a fresh id and await its `result`, correlated by that id. Passing [onEvent]
     * makes it a **subscription**: the handler stays registered for the life of the session and
     * receives every `event` frame HA pushes under the same id. Throws [HaCommandException] on an
     * unsuccessful reply, on timeout, and when the socket is down.
     */
    private suspend fun request(
        type: String,
        extra: JsonObject? = null,
        onEvent: ((JsonObject) -> Unit)? = null,
    ): JsonElement {
        val deferred = CompletableDeferred<JsonObject>()
        val id = pendingMutex.withLock {
            val i = nextId++
            pending[i] = deferred
            if (onEvent != null) eventHandlers[i] = onEvent
            i
        }
        var failed = true
        try {
            sendText(buildJsonObject {
                put("id", id)
                put("type", type)
                extra?.forEach { (k, v) -> put(k, v) }
            }.toString())
            val reply = withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
            if (reply["success"]?.jsonPrimitive?.booleanOrNull == false) {
                val error = reply["error"]?.jsonObject
                throw HaCommandException(
                    type,
                    error?.get("code")?.jsonPrimitive?.contentOrNull,
                    error?.get("message")?.jsonPrimitive?.contentOrNull,
                )
            }
            failed = false
            return reply["result"] ?: JsonNull
        } finally {
            // A subscription that never got its acknowledgement is not subscribed — drop its handler
            // so a later id reuse (after a reconnect) can't inherit it.
            pendingMutex.withLock {
                pending.remove(id)
                if (failed) eventHandlers.remove(id)
            }
        }
    }

    /** Fail every awaiting request when the socket drops, so callers don't hang until timeout. */
    private suspend fun failPending() {
        pendingMutex.withLock {
            pending.values.forEach { it.completeExceptionally(HaCommandException("*", null, "connection closed")) }
            pending.clear()
            eventHandlers.clear()
        }
    }

    private suspend fun DefaultClientWebSocketSession.receiveJson(): JsonObject {
        while (true) {
            val frame = incoming.receive()
            if (frame is Frame.Text) return json.parseToJsonElement(frame.readText()).jsonObject
        }
    }

    /**
     * Fire a service call and forget it — the truth comes back as a `state_changed` echo, not as this
     * reply, so the reply is only read to log a rejection.
     */
    private fun callService(domain: String, service: String, data: JsonObject?, target: JsonObject) {
        scope.launch {
            runCatching {
                request("call_service", buildJsonObject {
                    put("domain", domain)
                    put("service", service)
                    if (data != null) put("service_data", data)
                    put("target", target)
                })
            }.onFailure { println("HomeAssistantAdapter: $domain.$service failed: ${it.message}") }
        }
    }

    private fun areaTarget(areaId: String): JsonObject = buildJsonObject { put("area_id", areaId) }
    private fun entityTarget(entityId: String): JsonObject = buildJsonObject { put("entity_id", entityId) }

    private suspend fun sendText(text: String) {
        val open = session ?: throw HaCommandException("send", null, "no connection")
        open.send(Frame.Text(text))
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
                .associateWith { room -> speaker(room)?.let { entityStates.value[it] }.attrStringList("group_members") },
            roomByEntityId = roomEntities.entries
                .mapNotNull { (room, entities) -> entities.mediaPlayerId?.let { it to room } }
                .toMap(),
        )
        _state.value = HomeState(
            rooms = Room.entries.associateWith { room ->
                val lights = lightsOf(room)
                val speaker = speaker(room)?.let { entityStates.value[it] }
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
            climate = climateState(),
            playlists = emptyList(),
            quickPicks = emptyList(),
            mixedForYou = emptyList(),
            calendar = calendarState(),
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
        climate = ClimateState(null, null, null, null, null),
        playlists = emptyList(),
        quickPicks = emptyList(),
        mixedForYou = emptyList(),
        calendar = calendarState(),
    )

    /**
     * The climate glance from the home's `weather.*` entity: the outdoor tile's apparent temperature
     * and its condition icon. The other three tiles have no sensors in this home and stay `null`.
     * Wind speed and temperature are converted from the units the entity itself reports, since those
     * are per-integration. Weather entities update roughly hourly, so the `state_changed` push that
     * already drives everything else is enough — nothing here polls.
     */
    private fun climateState(): ClimateState {
        val weather = weatherEntityId?.let { entityStates.value[it] }
        val tempUnit = weather.attrString("temperature_unit")
        val tempC = temperatureToC(weather.attrDouble("temperature"), tempUnit)
        return ClimateState(
            indoorTempC = null,
            humidityPct = null,
            energyKw = null,
            feelsLikeC = feelsLikeC(
                tempC = tempC,
                humidityPct = weather.attrInt("humidity")?.coerceIn(0, 100),
                dewPointC = temperatureToC(weather.attrDouble("dew_point"), tempUnit),
                windMs = windSpeedToMs(weather.attrDouble("wind_speed"), weather.attrString("wind_speed_unit")),
                uvIndex = weather.attrDouble("uv_index"),
            ),
            condition = weatherConditionFrom(weather?.state),
        )
    }

    /**
     * The calendar payload `rebuild()` (and the pre-connection blank state) publishes, assembled from
     * the adapter's own calendar fields — which may already hold the offline cache before the socket
     * is up, hence the [CalendarState.stale] flag rather than an empty calendar.
     */
    private fun calendarState(): CalendarState = CalendarState(
        events = calendarEvents.value,
        todos = todoItems.value,
        sources = calendarSources.value,
        stale = calendarStale.value,
        // Stale means nothing has been discovered yet (or the socket is down), and an unreached box is
        // not the same as a home without a list — only claim the absence once we have actually looked.
        hasTodoList = todoEntityId != null || calendarStale.value,
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
    private class HaCommandException(type: String, code: String?, message: String?) :
        Exception("HA command '$type' failed (code=$code): $message")

    private companion object {
        const val INITIAL_RECONNECT_MS = 1_000L
        const val MAX_RECONNECT_MS = 30_000L
        // Registry lists are the slowest of these and answer well inside a second on a healthy box.
        const val REQUEST_TIMEOUT_MS = 20_000L
        val HOLD = 3_000.milliseconds

        // A generous rolling window: a household calendar is tiny, and fetching a year ahead means
        // month navigation never has to reach the adapter (see the CORE RULE — the VM filters).
        const val CALENDAR_WINDOW_BACK_MONTHS = 1
        const val CALENDAR_WINDOW_FORWARD_MONTHS = 12
        // Only a safety net: the panel asks for a refetch when it opens and after every write.
        const val CALENDAR_POLL_MS = 15 * 60 * 1_000L
        const val CALENDAR_DEBOUNCE_MS = 750L

        // The registries whose changes redefine what the dashboard is looking at. Entity changes
        // carry the calendar colors and names; area/device changes move entities between rooms.
        val REGISTRY_EVENTS = listOf(
            "entity_registry_updated",
            "device_registry_updated",
            "area_registry_updated",
        )
        // Long enough to swallow the burst HA emits while an integration is being set up.
        const val REDISCOVER_DEBOUNCE_MS = 2_000L
    }
}
