package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.HomeState
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.QueueMode
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
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

/**
 * The room whose audio session [room] actually plays — itself when it plays alone or leads, the
 * group's leader when it follows one. **The** address of a group's playback: every intent about
 * *content* (play, enqueue, skip, transport) goes to this room, and the panel reads it, so the two
 * members of a group behave as one and neither reads as the source. The speaker itself is still
 * [room] — volume is never redirected.
 */
fun Map<Room, RoomState>.audioSessionRoom(room: Room): Room = this[room]?.audio?.syncLeader ?: room

/**
 * What [room] is playing: its session's audio ([audioSessionRoom]) carrying [room]'s **own** speaker
 * volume, since volume is per-speaker even inside a group. `null` on a speaker-less room.
 *
 * Identity is preserved for an ungrouped room (it is its own session, and the copy is skipped), so a
 * consumer comparing snapshots doesn't see a fresh instance on every read.
 */
fun Map<Room, RoomState>.audioSessionOf(room: Room): AudioState? {
    val own = this[room]?.audio ?: return null
    val session = this[audioSessionRoom(room)]?.audio ?: return own
    return if (session === own) own else session.copy(volumePct = own.volumePct)
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

/**
 * How a user-queued row is recognised in the mock store. The real adapter tracks the block's bottom as
 * a queue-item id it remembers ([com.mattschoe.smarthome.data.ma.planEnqueue]); here the rows mint
 * their own marked id, so the block is simply the leading run of them — and [playBrowseItem]'s queue
 * replacement empties it for free, since the rows it mints carry a different prefix.
 */
private const val MOCK_USER_QUEUE_PREFIX = "mock-user-"

/**
 * Queue a browse tile on [room] — the mock's stand-in for an `option = "next"` `play_media` plus the
 * reorder behind it. [QueueMode.Next] puts the row at the top of the user block, [QueueMode.Last] at
 * its bottom; both stay above the continuation rows. Now-playing is untouched, which is the whole
 * point of the intent.
 */
fun HomeState.enqueueBrowseItem(room: Room, item: BrowseItem, mode: QueueMode): HomeState =
    updateAudio(room) { a ->
        val blockSize = a.queue.takeWhile { it.queueItemId?.startsWith(MOCK_USER_QUEUE_PREFIX) == true }.size
        val at = if (mode == QueueMode.Next) 0 else blockSize
        val row = MediaTrack(
            title = item.name,
            artist = item.subtitle.orEmpty(),
            album = null,
            artworkUrl = item.artworkUrl,
            durationSec = MOCK_TRACK_DURATION_SEC,
            uri = item.uri,
            // Distinct per row, so queueing the same tile twice gives two addressable entries.
            queueItemId = "$MOCK_USER_QUEUE_PREFIX${a.queue.size}-${item.name}",
        )
        a.copy(queue = a.queue.take(at) + row + a.queue.drop(at))
    }

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
 * The Monday of the week [date] falls in — the anchor the week view's seven columns are laid out
 * from. The single place week boundaries are computed; nothing else may re-derive them.
 */
fun weekStart(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

/**
 * How many days the week view shows — Monday through Sunday.
 */
const val DaysPerWeek = 7

/**
 * The ISO 8601 week number [date] falls in — the "uge 36" a Danish household counts by, printed in
 * the week view's gutter corner.
 *
 * Counted off the week's **Thursday**: an ISO week belongs to the year holding its Thursday, so that
 * Thursday's day-of-year divides straight into the week number, and the weeks straddling New Year —
 * along with the 53-week years — need no case of their own.
 */
fun isoWeekNumber(date: LocalDate): Int =
    (weekStart(date).plus(3, DateTimeUnit.DAY).dayOfYear - 1) / DaysPerWeek + 1

/**
 * How far back and forward from today the adapter fetches calendar events — and keeps them. A
 * generous rolling window: a household calendar is tiny, and fetching a year ahead means month
 * navigation never has to reach the adapter.
 *
 * The **one** definition of the calendar's span, because the fetch window and the range the month and
 * week pagers scroll over have to be the same thing: inside it a neighbouring page always has data
 * behind it, and at its edges the pager stops consuming the drag so whatever is nesting it (the
 * phone's page pager) gets it instead — which is right, there being nothing beyond it to show.
 */
const val CALENDAR_WINDOW_BACK_MONTHS = 1
const val CALENDAR_WINDOW_FORWARD_MONTHS = 12

/** The span [CALENDAR_WINDOW_BACK_MONTHS]/[CALENDAR_WINDOW_FORWARD_MONTHS] describe, around [today]. */
fun calendarWindow(today: LocalDate): ClosedRange<LocalDate> =
    today.plus(-CALENDAR_WINDOW_BACK_MONTHS, DateTimeUnit.MONTH)..
        today.plus(CALENDAR_WINDOW_FORWARD_MONTHS, DateTimeUnit.MONTH)

/** Months since year 0 — the linear scale month paging counts on. */
private fun monthOrdinal(date: LocalDate): Int = date.year * 12 + (date.month.number - 1)

/** How many month pages [window] spans (both ends inclusive). */
fun monthPageCount(window: ClosedRange<LocalDate>): Int =
    monthOrdinal(window.endInclusive) - monthOrdinal(window.start) + 1

/**
 * The page [date]'s month sits on. Clamped into [window]: a cached event from outside the fetched
 * span must not produce a page the pager has no room for.
 */
fun monthIndexOf(window: ClosedRange<LocalDate>, date: LocalDate): Int =
    (monthOrdinal(date) - monthOrdinal(window.start)).coerceIn(0, monthPageCount(window) - 1)

/** The first of the month [page] shows. Clamped like [monthIndexOf]. */
fun monthAtPage(window: ClosedRange<LocalDate>, page: Int): LocalDate {
    val ordinal = monthOrdinal(window.start) + page.coerceIn(0, monthPageCount(window) - 1)
    return LocalDate(ordinal / 12, ordinal % 12 + 1, 1)
}

/** How many week pages [window] spans, counting from the Monday its first day falls in. */
fun weekPageCount(window: ClosedRange<LocalDate>): Int =
    weekStart(window.start).daysUntil(weekStart(window.endInclusive)) / DaysPerWeek + 1

/** The page [date]'s week sits on, clamped into [window] for the same reason as [monthIndexOf]. */
fun weekIndexOf(window: ClosedRange<LocalDate>, date: LocalDate): Int =
    (weekStart(window.start).daysUntil(weekStart(date)) / DaysPerWeek)
        .coerceIn(0, weekPageCount(window) - 1)

/** The Monday of the week [page] shows. Clamped like [weekIndexOf]. */
fun weekAtPage(window: ClosedRange<LocalDate>, page: Int): LocalDate =
    weekStart(window.start).plus(page.coerceIn(0, weekPageCount(window) - 1) * DaysPerWeek, DateTimeUnit.DAY)

/** How many day pages [window] spans (both ends inclusive) — the Opgaver panel's paging scale. */
fun dayPageCount(window: ClosedRange<LocalDate>): Int =
    window.start.daysUntil(window.endInclusive) + 1

/** The page [date] sits on, clamped into [window] for the same reason as [monthIndexOf]. */
fun dayIndexOf(window: ClosedRange<LocalDate>, date: LocalDate): Int =
    window.start.daysUntil(date).coerceIn(0, dayPageCount(window) - 1)

/** The day [page] shows. Clamped like [dayIndexOf]. */
fun dayAtPage(window: ClosedRange<LocalDate>, page: Int): LocalDate =
    window.start.plus(page.coerceIn(0, dayPageCount(window) - 1), DateTimeUnit.DAY)

/**
 * One event placed in a day column of the week grid: its resolved [startMinute]/[endMinute] bounds
 * (never shorter than [MinEventSpanMinutes]) plus which of the day's overlap [lane]s it takes and how
 * many lanes that cluster of overlapping events splits the column into.
 */
data class PositionedEvent(
    val event: CalendarEvent,
    val startMinute: Int,
    val endMinute: Int,
    val lane: Int,
    val laneCount: Int,
)

/**
 * Shortest block the week grid draws. A zero-length event (or one of a few minutes) would otherwise
 * be a hairline nobody can read or hit, so it is given this much room — and it takes that room in the
 * overlap math too, so the neighbour it would visually collide with is moved aside.
 */
const val MinEventSpanMinutes = 20

/**
 * Split a day's [events] into overlap lanes, the column-splitting every week/day calendar does: walk
 * them in start order, group the ones that (transitively) overlap into a cluster, give each the
 * lowest lane no earlier event still occupies, and stamp every member of the cluster with the number
 * of lanes it ended up needing — so a column of two overlapping events halves cleanly.
 *
 * All-day entries (`startMinute == null`) are not placed: they live in the strip above the grid.
 */
fun layoutDayEvents(events: List<CalendarEvent>): List<PositionedEvent> {
    val timed = events
        .mapNotNull { event ->
            val start = event.startMinute ?: return@mapNotNull null
            val end = (event.endMinute ?: start).coerceAtLeast(start + MinEventSpanMinutes)
            Triple(event, start, end)
        }
        .sortedWith(compareBy({ it.second }, { it.third }))

    val placed = mutableListOf<PositionedEvent>()
    // Cluster scratch: the events placed so far, the end minute each lane is busy until, and how far
    // the cluster as a whole reaches (an event starting at or after that opens a fresh cluster).
    val cluster = mutableListOf<PositionedEvent>()
    val laneEnds = mutableListOf<Int>()
    var clusterEnd = Int.MIN_VALUE

    fun flush() {
        cluster.forEach { placed += it.copy(laneCount = laneEnds.size) }
        cluster.clear()
        laneEnds.clear()
    }

    for ((event, start, end) in timed) {
        if (start >= clusterEnd) flush()
        val free = laneEnds.indexOfFirst { it <= start }
        val lane = if (free >= 0) free else laneEnds.size
        if (free >= 0) laneEnds[free] = end else laneEnds += end
        // laneCount is unknown until the cluster closes; [flush] stamps the final one.
        cluster += PositionedEvent(event, start, end, lane, laneCount = 1)
        clusterEnd = maxOf(clusterEnd, end)
    }
    flush()
    return placed
}

/**
 * How coarsely a dragged block's drop is rounded. Finer than [SlotSnapMinutes]' half hour, because a
 * *move* is aimed at the events around it rather than at a slot picked from nothing: quarter past is
 * a time somebody would put a meeting at, and the finger is already holding the block's own edge to
 * line up with. Squeezed towards the zoom's floor a quarter hour is a few dp, at which point the
 * pointer can no longer resolve it anyway and the snap coarsens on its own.
 */
const val EventDragSnapMinutes = 15

/** Where a dragged block was let go: the event it carries, and the day and minute it landed on. */
data class EventMove(val event: CalendarEvent, val date: LocalDate, val startMinute: Int)

/**
 * Whether this row is the *whole* of its event rather than one day of a longer one — [coveredDays]
 * read off the bounds [expandCalendarEvent] stamped on every row.
 *
 * A multi-day row cannot be dragged: it draws the part of the event falling on its own day (a day
 * merely spanned reads 00:00–24:00), so there is no one start for a drop to move.
 */
fun CalendarEvent.spansOneDay(): Boolean {
    val covered = coveredDays() ?: return false
    return covered.start == date && covered.endInclusive == date
}

/**
 * Whether the week grid may pick [event] up and move it. Everything excluded here is excluded
 * because no write could land: an all-day entry lives in the strip and has no minute to drop on, an
 * event the backend gave no `uid` cannot be addressed at all, a read-only calendar refuses the write
 * ([CalendarSource.canWrite]), and a multi-day row has no single start ([spansOneDay]).
 */
fun canDragEvent(event: CalendarEvent, sources: List<CalendarSource>): Boolean =
    event.startMinute != null &&
        !event.allDay &&
        event.uid != null &&
        event.spansOneDay() &&
        sources.firstOrNull { it.id == event.sourceId }?.canWrite == true

/**
 * Where a drag of [dayDelta] columns and [minuteDelta] minutes from [originDayIndex] puts [event],
 * or `null` if that is where it already is — a long press that wobbled must not write.
 *
 * **This is the week lock**: the column is clamped into [weekDays], so dragging past Monday or
 * Sunday pins the block to that edge instead of paging to the neighbouring week. The minute snaps to
 * [EventDragSnapMinutes] and is held inside the day exactly as `slotTimeAt` holds a tapped one; an
 * event long enough to run past midnight from there simply does, which is the same reading the
 * editor gives a 20:00–02:00 pair.
 */
fun droppedEventSlot(
    event: CalendarEvent,
    weekDays: List<LocalDate>,
    originDayIndex: Int,
    dayDelta: Int,
    minuteDelta: Int,
): EventMove? {
    val from = event.startMinute ?: return null
    if (weekDays.isEmpty()) return null
    val date = weekDays[(originDayIndex + dayDelta).coerceIn(0, weekDays.lastIndex)]
    val raw = from + minuteDelta
    val snapped = ((raw.toFloat() / EventDragSnapMinutes).roundToInt() * EventDragSnapMinutes)
        .coerceIn(0, MinutesPerDay - EventDragSnapMinutes)
    return if (date == event.date && snapped == from) null else EventMove(event, date, snapped)
}

/**
 * The event [move] rewrites [event] into: the dropped day and minute, **keeping its duration** and
 * everything else about it. A drag says *when*, and nothing else.
 *
 * The duration comes off the event's real bounds rather than the row's minutes, since those are what
 * [expandCalendarEvent] stamps with the whole event; an event carried past midnight by its own
 * length rolls onto the next day here rather than being cut off at it.
 *
 * Note what is *not* carried: `description`, which [CalendarEvent] does not hold — the editor's own
 * save drops it for the same reason.
 */
fun movedEventDraft(event: CalendarEvent, move: EventMove): CalendarEventDraft {
    val total = move.startMinute + event.durationMinutes()
    return buildEventDraft(
        // A blank-titled event reads as [UntitledEventTitle] in the panel; writing that placeholder
        // back as its summary would make the stand-in the event's actual name.
        summary = event.title.takeIf { it != UntitledEventTitle }.orEmpty(),
        start = LocalDateTime(move.date, LocalTime(move.startMinute / 60, move.startMinute % 60)),
        end = LocalDateTime(
            move.date.plus(total.floorDiv(MinutesPerDay), DateTimeUnit.DAY),
            LocalTime(total.mod(MinutesPerDay) / 60, total.mod(MinutesPerDay) % 60),
        ),
        allDay = false,
        location = event.location,
        rrule = event.rrule,
    )
}

/**
 * [events] with the one [move] addresses shown at its new slot — the optimistic hold the week grid
 * needs so a dropped block does not snap back to where it was for the length of the round trip (and
 * does not sit in its old place *behind* the scope popup while that is being answered).
 *
 * The row is re-expanded rather than patched, so an event carried past midnight by the drop splits
 * into the two rows it now really is. Matched on what addresses the occurrence — calendar, `uid`,
 * recurrence id — which survives the refetch, making a second application a no-op rather than a
 * double move. A scope covering more than the dropped occurrence moves only that one here; the rest
 * arrive with the refetch.
 */
fun applyEventMove(events: List<CalendarEvent>, move: EventMove): List<CalendarEvent> {
    val target = move.event
    val uid = target.uid ?: return events
    val draft = movedEventDraft(target, move)
    val moved = events.flatMap { event ->
        if (event.uid == uid &&
            event.sourceId == target.sourceId &&
            event.recurrenceId == target.recurrenceId
        ) {
            expandCalendarEvent(
                sourceId = event.sourceId,
                title = draft.summary,
                start = draft.start,
                end = draft.end,
                uid = event.uid,
                recurrenceId = event.recurrenceId,
                location = draft.location,
                rrule = draft.rrule,
            )
        } else {
            listOf(event)
        }
    }
    // The panel reads these already sorted (the adapter sorts upstream) and `groupBy` keeps list
    // order, so a row landing on a new time has to be put back in place or it draws out of order.
    return sortCalendarEvents(moved)
}

/** How long the event runs, in minutes — off its real bounds, falling back to the row's own. */
private fun CalendarEvent.durationMinutes(): Int {
    val start = start
    val end = end
    if (start != null && end != null) {
        return (start.date.daysUntil(end.date) * MinutesPerDay +
            minutesOfDay(end.time) - minutesOfDay(start.time)).coerceAtLeast(0)
    }
    val from = startMinute ?: return 0
    return ((endMinute ?: from) - from).coerceAtLeast(0)
}

/**
 * How many days one event may be expanded across. A calendar can hold an event spanning a year;
 * expanding it would put a dot on every cell of every month and dominate every agenda, so a run this
 * long is truncated rather than allowed to swamp the panel.
 */
private const val MaxEventDays = 62

/**
 * Expand one backend event into the per-day [CalendarEvent]s the panel renders — a multi-day event
 * becomes one entry per day it covers, or it would be missing from every day but its first. [end] is
 * **exclusive** in both forms (iCal's convention, and Home Assistant's): an all-day event ending on
 * the 8th covers through the 7th, and a timed one ending exactly at midnight belongs to the day
 * before, not to a sliver of the next.
 *
 * Pure, and shared by the Home Assistant mapper, the mock store and the offline overlay so all three
 * expand identically — [pending] is what the last of those marks its rows with.
 */
fun expandCalendarEvent(
    sourceId: String,
    title: String,
    start: LocalDateTime,
    end: LocalDateTime,
    allDay: Boolean = false,
    uid: String? = null,
    recurrenceId: String? = null,
    location: String? = null,
    rrule: String? = null,
    pending: Boolean = false,
): List<CalendarEvent> {
    val firstDay = start.date
    val endsExclusively = allDay || end.time == LocalTime(0, 0)
    val lastDay = end.date
        .let { if (endsExclusively && it > firstDay) it.plus(-1, DateTimeUnit.DAY) else it }
        .coerceAtLeast(firstDay)

    val dayCount = (firstDay.daysUntil(lastDay) + 1).coerceIn(1, MaxEventDays)
    return List(dayCount) { offset ->
        val position = when {
            dayCount == 1 -> EventDayPosition.Only
            offset == 0 -> EventDayPosition.First
            offset == dayCount - 1 -> EventDayPosition.Last
            else -> EventDayPosition.Middle
        }
        val time = formatEventTime(start.time, end.time, position, allDay)
        // The bounds of the part that falls on *this* day, which is what gives a week-grid block its
        // position and height. A day the event merely spans reads as a full day and carries none (it
        // belongs in the all-day strip, above the day's timed entries); a day it runs into starts at
        // midnight, and a day it runs out of ends there.
        val fullDay = time == AllDayLabel
        CalendarEvent(
            date = firstDay.plus(offset, DateTimeUnit.DAY),
            title = title.ifBlank { UntitledEventTitle },
            time = time,
            sourceId = sourceId,
            startMinute = when {
                fullDay -> null
                position == EventDayPosition.Last -> 0
                else -> minutesOfDay(start.time)
            },
            endMinute = when {
                fullDay -> null
                position == EventDayPosition.First -> MinutesPerDay
                else -> minutesOfDay(end.time)
            },
            uid = uid,
            recurrenceId = recurrenceId,
            location = location,
            allDay = allDay,
            // The whole event's bounds, on every day of it: the edit surface opens from whichever day
            // was tapped and still has to show — and re-save — the event's real start and end.
            start = start,
            end = end,
            rrule = rrule,
            pending = pending,
        )
    }
}

/** What an event with no summary is shown as, rather than an empty agenda row. */
const val UntitledEventTitle = "(uden titel)"

/** Agenda order: by day, all-day entries first, then by start time, then alphabetically. */
fun sortCalendarEvents(events: List<CalendarEvent>): List<CalendarEvent> =
    events.sortedWith(compareBy({ it.date }, { it.startMinute ?: -1 }, { it.title }))

/** One day's rows under a single date header on an Opgaver page. Never empty. */
data class TodoGroup(val due: LocalDate, val items: List<TodoItem>)

/** What one Opgaver page draws: what is still open, and what has been ticked off. See [todoPage]. */
data class TodoPage(val open: List<TodoGroup>, val done: List<TodoGroup>)

/**
 * The page for [day], whose two halves are scoped by two different questions.
 *
 * **Open** is every todo still to do whose [TodoItem.showsFrom] day has been reached, so an unticked
 * Tuesday keeps standing on Wednesday's page rather than having to be swiped back to. Anything due
 * later belongs to a later page — and so does anything that did not yet exist: a task carries
 * *forward* from the day it was written down, never backwards onto days it was not yet a task on.
 *
 * **Done** is what was ticked off *on this day* — not everything ever finished up to it. Closing
 * Tuesday's task on Wednesday files it under Wednesday, and Thursday opens clean. Both halves group
 * by **due** day, nearest first, so the done half still separates "finished, and it was today's" from
 * "finished, but it had been hanging over from yesterday".
 *
 * Alphabetical inside a group rather than the list's own order because a page mixes days: an append
 * order that spans several days puts no two rows anywhere a reader can predict.
 */
fun todoPage(todos: List<TodoItem>, day: LocalDate): TodoPage {
    fun group(items: List<TodoItem>) = items
        .groupBy { it.due }
        .map { (due, sameDay) -> TodoGroup(due, sameDay.sortedBy { it.label.lowercase() }) }
        .sortedByDescending { it.due }
    return TodoPage(
        open = group(todos.filter { !it.done && it.showsFrom <= day }),
        done = group(todos.filter { it.done && it.closedOn == day }),
    )
}

/**
 * Append a todo bound to [due], written down on [createdOn]. [id] is supplied by the caller (the
 * adapter mints a fresh one) so this stays deterministic/testable. A blank [label] is a no-op —
 * `todo.add_item` requires a summary, so the ghost add-row never commits an empty item. New items
 * append (stable order → rows never jump).
 *
 * [createdOn] defaults to [due] — the day-is-the-day case — so a caller with no clock in reach still
 * gets the behaviour the checklist had before the creation day was recorded.
 */
fun HomeState.addTodo(id: String, due: LocalDate, label: String, createdOn: LocalDate = due): HomeState {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return this
    val item = TodoItem(id, due, trimmed, done = false, createdOn = createdOn)
    return copy(calendar = calendar.copy(todos = calendar.todos + item))
}

/**
 * Flip a todo's `done` (the tap gesture ↔ HA needs_action/completed), stamping [today] as the day it
 * was closed — or clearing that stamp when it is re-opened, so closing it again dates it afresh.
 */
fun HomeState.toggleTodo(id: String, today: LocalDate): HomeState =
    copy(calendar = calendar.copy(
        todos = calendar.todos.map {
            if (it.id != id) it
            else it.copy(done = !it.done, completedOn = if (it.done) null else today)
        },
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
