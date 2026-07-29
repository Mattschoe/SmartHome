package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.HomeState
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Compose-free, adapter-free pure functions for the signature interactions and state transitions.
 */

/** Brightness (0–100) for a dial pointer [angleDeg] in [0,180]. `round((1 − deg/180) × 100)`. */
fun brightnessFromAngle(angleDeg: Float): Int =
    ((1f - angleDeg.coerceIn(0f, 180f) / 180f) * 100f).roundToInt()

/** Inverse of [brightnessFromAngle]: the dial angle in [0,180] for a given brightness, for drawing. */
fun angleFromBrightness(brightness: Int): Float =
    (1f - brightness.coerceIn(0, 100) / 100f) * 180f

/**
 * Pointer angle in degrees, clamped to the dial's top half [0,180], for a touch at ([px],[py])
 * against dial center ([cx],[cy]) in screen coordinates (y grows downward).
 */
fun angleFromPointer(cx: Float, cy: Float, px: Float, py: Float): Float {
    val degrees = atan2(cy - py, px - cx) * 180f / PI.toFloat()
    // Below the diameter line atan2 is negative; snap to the nearest end by x-side instead of
    // clamping to 0° (which would read as 100%). Right of center → 0° (100%), left → 180° (0%).
    return when {
        degrees in 0f..180f -> degrees
        px >= cx -> 0f
        else -> 180f
    }
}

/** Fraction 0–1 for a horizontal drag at [x] within a track starting at [left] of the given [width]. */
fun volumeFractionFromX(x: Float, left: Float, width: Float): Float {
    if (width <= 0f) return 0f
    return ((x - left) / width).coerceIn(0f, 1f)
}

/** Volume (0–100) from a 0–1 [fraction]. */
fun volumeFromFraction(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 100f).roundToInt()

private inline fun HomeState.updateRoom(room: Room, block: (RoomState) -> RoomState): HomeState =
    copy(rooms = rooms + (room to block(rooms.getValue(room))))

/** Set a room's brightness. Dragging the dial forces the light on (per the spec). */
fun HomeState.withBrightness(room: Room, value: Int): HomeState =
    updateRoom(room) { it.copy(brightnessPct = value.coerceIn(0, 100), isLightOn = true) }

/** Select a warmth swatch; this recolors the dial and turns the light on. */
fun HomeState.withWarmth(room: Room, warmth: Warmth): HomeState =
    updateRoom(room) { it.copy(lightWarmth = warmth, isLightOn = true) }

/** Toggle a room's light on/off (the center bulb tap). */
fun HomeState.toggleLight(room: Room): HomeState =
    updateRoom(room) { it.copy(isLightOn = !it.isLightOn) }

/**
 * Apply an audio transition to [room], leaving a speaker-less room (`audio == null`) untouched so
 * every transport mutation is a safe no-op there.
 */
private inline fun HomeState.updateAudio(room: Room, block: (AudioState) -> AudioState): HomeState =
    updateRoom(room) { rs -> rs.audio?.let { rs.copy(audio = block(it)) } ?: rs }

/** Set a room's audio volume. Does not change playback state. */
fun HomeState.withVolume(room: Room, value: Int): HomeState =
    updateAudio(room) { it.copy(volumePct = value.coerceIn(0, 100)) }

/** Toggle play/pause on a room's audio session. */
fun HomeState.togglePlay(room: Room): HomeState =
    updateAudio(room) { it.copy(isPlaying = !it.isPlaying) }

/** Set shuffle on/off. */
fun HomeState.setShuffle(room: Room, on: Boolean): HomeState =
    updateAudio(room) { it.copy(isShuffle = on) }

/** Set the repeat mode. */
fun HomeState.setRepeat(room: Room, mode: RepeatMode): HomeState =
    updateAudio(room) { it.copy(repeat = mode) }

/**
 * Put [follower] in [leader]'s sync group, so the two play as one. Both rooms record [leader] as
 * their [AudioState.syncLeader] — the leader points at itself — which is what [audioJoined] reads.
 * A speaker-less room on either side leaves that side untouched.
 */
fun HomeState.joinAudio(leader: Room, follower: Room): HomeState =
    updateAudio(leader) { it.copy(syncLeader = leader) }
        .updateAudio(follower) { it.copy(syncLeader = leader) }

/**
 * Take [room] out of its sync group. A follower leaving just drops itself; the **leader** leaving
 * dissolves the group, since its followers have nothing left to follow. Either way a group left with
 * a single member is no group at all, so its leader is cleared too.
 */
fun HomeState.unjoinAudio(room: Room): HomeState {
    val leader = rooms[room]?.audio?.syncLeader ?: return this
    val leaving =
        if (leader == room) rooms.filterValues { it.audio?.syncLeader == room }.keys else setOf(room)
    return leaving
        .fold(this) { home, member -> home.updateAudio(member) { it.copy(syncLeader = null) } }
        .dropSoloGroups()
}

/** Clear the leader of any room left alone in its group — one member is not a group. */
private fun HomeState.dropSoloGroups(): HomeState = copy(
    rooms = rooms.mapValues { (room, roomState) ->
        val leader = roomState.audio?.syncLeader ?: return@mapValues roomState
        val hasCompany = rooms.any { (other, state) -> other != room && state.audio?.syncLeader == leader }
        if (hasCompany) roomState else roomState.copy(audio = roomState.audio.copy(syncLeader = null))
    },
)

/** Whether [a] and [b] are playing as one — the same, non-null sync leader. */
fun Map<Room, RoomState>.audioJoined(a: Room, b: Room): Boolean {
    val leaderA = this[a]?.audio?.syncLeader ?: return false
    return leaderA == this[b]?.audio?.syncLeader
}

/** Seek within the current track, clamped to `[0, duration]`. */
fun HomeState.seek(room: Room, sec: Int): HomeState =
    updateAudio(room) { it.copy(positionSec = sec.coerceIn(0, it.nowPlaying?.durationSec ?: 0)) }

/**
 * Advance to the next track. Round-robin so the demo cycles forever: the queue head becomes
 * now-playing, the old current track is pushed onto the queue tail, and the position resets.
 */
fun HomeState.next(room: Room): HomeState = updateAudio(room) { a ->
    a.queue.firstOrNull()?.let {
        a.copy(nowPlaying = it, queue = a.queue.drop(1) + listOfNotNull(a.nowPlaying), positionSec = 0)
    } ?: a
}

/**
 * Go to the previous track. HA convention: restart the current track if more than 3s in (or the
 * queue is empty), otherwise rotate the queue tail back to now-playing.
 */
fun HomeState.previous(room: Room): HomeState = updateAudio(room) { a ->
    if (a.positionSec > 3 || a.queue.isEmpty()) a.copy(positionSec = 0)
    else a.copy(
        nowPlaying = a.queue.last(),
        queue = listOfNotNull(a.nowPlaying) + a.queue.dropLast(1),
        positionSec = 0,
    )
}

/**
 * How a queue entry is addressed in the mock store. The real backend hands out a
 * [MediaTrack.queueItemId]; the fixtures have none, so their title stands in as the handle.
 */
private fun MediaTrack.queueKey(): String = queueItemId ?: title

/**
 * Skip to a queue entry: it becomes now-playing, and everything it jumped over — plus the track that
 * was playing — rotates to the tail, the same round-robin [next] uses (skipping to the head *is*
 * [next]). Unknown handles are a no-op.
 */
fun HomeState.playQueueItem(room: Room, queueItemId: String): HomeState = updateAudio(room) { a ->
    val index = a.queue.indexOfFirst { it.queueKey() == queueItemId }
    if (index < 0) a
    else a.copy(
        nowPlaying = a.queue[index],
        queue = a.queue.drop(index + 1) + listOfNotNull(a.nowPlaying) + a.queue.take(index),
        positionSec = 0,
    )
}

/**
 * Start playing a browse tile on [room] — the mock's stand-in for Music Assistant's `play_media`.
 * The tile becomes now-playing (with a nominal duration, since a [BrowseItem] carries none) and the
 * queue is left as-is. Unknown/blank uris are a no-op.
 */
fun HomeState.playBrowseItem(room: Room, item: BrowseItem): HomeState = updateAudio(room) { a ->
    a.copy(
        nowPlaying = MediaTrack(
            title = item.name,
            artist = item.subtitle.orEmpty(),
            album = null,
            artworkUrl = item.artworkUrl,
            durationSec = MOCK_TRACK_DURATION_SEC,
            uri = item.uri,
        ),
        isPlaying = true,
        positionSec = 0,
        // A play *replaces* the queue (matching MA's behavior); the previous track's up-next rows
        // don't survive it. The real adapter's Don't-Stop-the-Music then appends a continuation —
        // the mock refills instantly from the other browse tiles so the flow works offline.
        queue = (quickPicks + mixedForYou)
            .filter { it.uri != null && it.uri != item.uri }
            .take(MOCK_CONTINUATION_SIZE)
            .mapIndexed { i, tile ->
                MediaTrack(
                    title = tile.name,
                    artist = tile.subtitle.orEmpty(),
                    album = null,
                    artworkUrl = tile.artworkUrl,
                    durationSec = MOCK_TRACK_DURATION_SEC,
                    uri = tile.uri,
                    // Keyed on the played item so each play yields a *distinct* queue — the loader
                    // waits for a queue that differs from the previous one.
                    queueItemId = "mock-continuation-${item.name}-$i",
                )
            },
    )
}

private const val MOCK_CONTINUATION_SIZE = 5

private const val MOCK_TRACK_DURATION_SEC = 180

/** Move a queue entry [posShift] positions (negative = earlier), clamped to the queue. */
fun HomeState.moveQueueItem(room: Room, queueItemId: String, posShift: Int): HomeState = updateAudio(room) { a ->
    val from = a.queue.indexOfFirst { it.queueKey() == queueItemId }
    if (from < 0) a
    else {
        val reordered = a.queue.toMutableList()
        reordered.add((from + posShift).coerceIn(0, a.queue.lastIndex), reordered.removeAt(from))
        a.copy(queue = reordered)
    }
}

/**
 * The play order for a tapped top hit: the tapped entry and everything after it, then the entries
 * above it at the tail — so the tap starts where the user pointed without discarding the rest of the
 * list. An out-of-range [index] leaves the order untouched.
 */
fun <T> rotateFrom(items: List<T>, index: Int): List<T> {
    if (index !in items.indices) return items
    return items.drop(index) + items.take(index)
}

/** Cycle repeat: Off → All → Off. */
fun RepeatMode.cycle(): RepeatMode = when (this) {
    RepeatMode.Off -> RepeatMode.All
    RepeatMode.All -> RepeatMode.Off
}

// --- Calendar / todos ---

/**
 * The 6×7 = 42 cells of a Monday-first month grid for ([year], [month]). Each cell is the day-of-month
 * number, or `null` for the leading/trailing blanks around the month. Leading blanks come from the
 * 1st's weekday (Mon=1 → 0 blanks, Sun=7 → 6 blanks); the length of the month is the day distance to
 * the next month's 1st. Pure so the grid math is unit-tested independently of the composable.
 */
fun calendarGrid(year: Int, month: Int): List<Int?> {
    val first = LocalDate(year, month, 1)
    val leading = first.dayOfWeek.isoDayNumber - 1 // Mon=0 … Sun=6
    val daysInMonth = first.daysUntil(first.plus(1, DateTimeUnit.MONTH))
    return List(42) { index ->
        val day = index - leading + 1
        if (day in 1..daysInMonth) day else null
    }
}

/**
 * Append a todo bound to [due]. [id] is supplied by the caller (the adapter mints a fresh one) so this
 * stays deterministic/testable. A blank [label] is a no-op — `todo.add_item` requires a summary, so
 * the ghost add-row never commits an empty item. New items append (stable order → rows never jump).
 */
fun HomeState.addTodo(id: String, due: LocalDate, label: String): HomeState {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return this
    return copy(calendar = calendar.copy(todos = calendar.todos + TodoItem(id, due, trimmed, done = false)))
}

/** Flip a todo's `done` (the tap gesture ↔ HA needs_action/completed). */
fun HomeState.toggleTodo(id: String): HomeState =
    copy(calendar = calendar.copy(
        todos = calendar.todos.map { if (it.id == id) it.copy(done = !it.done) else it },
    ))

/**
 * Set a todo's label. Editing to a blank label **removes** the item — the deliberate escape hatch in
 * place of an explicit delete.
 */
fun HomeState.editTodo(id: String, label: String): HomeState {
    val trimmed = label.trim()
    val todos =
        if (trimmed.isEmpty()) calendar.todos.filterNot { it.id == id }
        else calendar.todos.map { if (it.id == id) it.copy(label = trimmed) else it }
    return copy(calendar = calendar.copy(todos = todos))
}

// --- Optimistic-hold reconciliation (used by the real HA adapter) ---

/** ±slack (percentage points) within which a held brightness/volume target counts as reached by HA. */
private const val HoldMatchTolerance = 2

/**
 * A per-room optimistic overlay for the HA adapter. Each non-null field is a value the user just set
 * that [reconcileHold] keeps on top of HA-derived state until HA reports it (confirm) or [deadline]
 * passes (timeout). Lives here beside its pure reconciliation logic; the adapter arms and stores it.
 */
internal data class RoomHold(
    val brightnessPct: Int? = null,
    val isLightOn: Boolean? = null,
    val lightWarmth: Warmth? = null,
    val volumePct: Int? = null,
    val isPlaying: Boolean? = null,
    val seek: SeekHold? = null,
    // Default to an already-elapsed mark; the adapter always re-arms it via copy before use.
    val deadline: ComparableTimeMark = TimeSource.Monotonic.markNow(),
)

/**
 * A committed seek, anchored in time. Unlike the scalar holds, HA often **never** echoes a seek
 * while playback continues — Sonos only re-stamps `media_position` on a state transition (pause,
 * track change), so until one happens HA keeps projecting from the pre-seek stamp. The anchor
 * therefore projects the target forward itself, exempt from [RoomHold.deadline], and lets go only
 * when HA's position agrees, the track changes, or [SeekHoldMax] passes (see [resolveSeek]).
 */
internal data class SeekHold(
    val targetSec: Int,
    /** Title of the track the seek was aimed at — a track change invalidates the anchor. */
    val track: String?,
    val at: ComparableTimeMark,
)

/** ±seconds within which HA's reported position counts as agreeing with a seek anchor. */
private const val SeekMatchToleranceSec = 5

/** Backstop for a seek HA never acknowledges at all (e.g. it silently failed on the speaker). */
private val SeekHoldMax = 90.seconds

/**
 * Reconcile an optimistic [hold] against the freshly HA-derived [fromHa] at time [now]. Returns the
 * [RoomState] to display and the **reduced** hold — the same hold with every field that has *settled*
 * dropped, or `null` when nothing is still held.
 *
 * Per held field: once HA reports the target (exact for on/off/warmth, within [HoldMatchTolerance]
 * for brightness/volume) **or** [deadline] has passed, the field is released — HA's value shows and
 * the field is dropped. Dropping a *converged* field is deliberate: it lets a genuine external change
 * that arrives moments later through, instead of being masked back to the just-set value. Until then
 * the held (optimistic) value shows, so HA's interim transition echoes can't jitter the control.
 */
internal fun reconcileHold(
    hold: RoomHold,
    fromHa: RoomState,
    now: ComparableTimeMark,
): Pair<RoomState, RoomHold?> {
    val expired = now >= hold.deadline
    val audio = fromHa.audio

    val brightness = resolveNear(hold.brightnessPct, fromHa.brightnessPct, expired)
    val lightOn = resolveExact(hold.isLightOn, fromHa.isLightOn, expired)
    val warmth = resolveExact(hold.lightWarmth, fromHa.lightWarmth, expired)
    val volume = if (audio != null) resolveNear(hold.volumePct, audio.volumePct, expired) else null to null
    val playing = if (audio != null) resolveExact(hold.isPlaying, audio.isPlaying, expired) else null to null
    // The seek anchor deliberately ignores [expired] — its release rules live in [resolveSeek].
    val seek = if (audio != null) resolveSeek(hold.seek, audio, now) else null to null

    val display = fromHa.copy(
        brightnessPct = brightness.first,
        isLightOn = lightOn.first,
        lightWarmth = warmth.first,
        audio = audio?.copy(
            volumePct = volume.first ?: audio.volumePct,
            isPlaying = playing.first ?: audio.isPlaying,
            positionSec = seek.first ?: audio.positionSec,
        ),
    )
    val reduced = RoomHold(
        brightnessPct = brightness.second,
        isLightOn = lightOn.second,
        lightWarmth = warmth.second,
        volumePct = volume.second,
        isPlaying = playing.second,
        seek = seek.second,
        deadline = hold.deadline,
    )
    val stillHeld = listOf(
        brightness.second, lightOn.second, warmth.second, volume.second, playing.second, seek.second,
    ).any { it != null }
    return display to reduced.takeIf { stillHeld }
}

/**
 * Resolve a seek [anchor] → (position to display, anchor to keep). While held, the display is the
 * seek target projected forward in real time (frozen while paused), so playback reads continuously
 * from where the user dropped the knob. It releases when HA's own position agrees within
 * [SeekMatchToleranceSec] — which any truth-carrying transition (the seek echo, a pause) produces —
 * or goes stale: the track changed, or [SeekHoldMax] passed without HA ever agreeing.
 */
private fun resolveSeek(
    anchor: SeekHold?,
    audio: AudioState,
    now: ComparableTimeMark,
): Pair<Int?, SeekHold?> {
    if (anchor == null) return null to null
    val age = now - anchor.at
    if (audio.nowPlaying?.title != anchor.track || age > SeekHoldMax) return null to null
    val expected = anchor.targetSec +
        if (audio.isPlaying) age.inWholeSeconds.coerceAtLeast(0L).toInt() else 0
    return if (abs(audio.positionSec - expected) <= SeekMatchToleranceSec) null to null
    else expected to anchor
}

/** Resolve one numeric field → (value to display, target to keep). Releases on tolerance match or expiry. */
private fun resolveNear(target: Int?, ha: Int, expired: Boolean): Pair<Int, Int?> = when {
    target == null -> ha to null
    expired || abs(target - ha) <= HoldMatchTolerance -> ha to null
    else -> target to target
}

/** Resolve one exact-match field → (value to display, target to keep). Releases on equality or expiry. */
private fun <T> resolveExact(target: T?, ha: T, expired: Boolean): Pair<T, T?> = when {
    target == null -> ha to null
    expired || target == ha -> ha to null
    else -> target to target
}
