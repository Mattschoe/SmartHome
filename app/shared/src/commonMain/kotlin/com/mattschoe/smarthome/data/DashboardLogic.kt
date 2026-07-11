package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.HomeState
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
import kotlin.math.atan2
import kotlin.math.roundToInt

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
