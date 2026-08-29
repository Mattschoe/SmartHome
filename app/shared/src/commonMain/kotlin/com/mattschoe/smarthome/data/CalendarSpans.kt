package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * The days one event actually covers, first through last, reading [CalendarEvent.end] exclusively
 * exactly as [expandCalendarEvent] does. `null` for a row carrying no bounds (an event read back
 * from an old cache), which callers treat as belonging to its own day alone.
 */
fun CalendarEvent.coveredDays(): ClosedRange<LocalDate>? {
    val start = start ?: return null
    val end = end ?: return null
    val endsExclusively = allDay || end.time == LocalTime(0, 0)
    val last = end.date
        .let { if (endsExclusively && it > start.date) it.plus(-1, DateTimeUnit.DAY) else it }
        .coerceAtLeast(start.date)
    return start.date..last
}

/** Whether this row is one day of an event that runs across more than one. */
fun CalendarEvent.isMultiDay(): Boolean = coveredDays()?.let { it.start != it.endInclusive } == true

/**
 * One event's unbroken run across a strip of consecutive days — a week's seven columns, or one row
 * of the month grid. [startIndex]/[endIndex] are inclusive column indices into that strip, and the
 * two `continues` flags say the event carries on past the bar's own edge, which is what lets it be
 * drawn cut off (square) there rather than closed (rounded).
 *
 * [event] is the row of the span's first shown day. Every row of an expanded event carries the whole
 * event's bounds, so opening any of them opens the same event — which is why one is enough.
 */
data class DaySpan(
    val event: CalendarEvent,
    val startIndex: Int,
    val endIndex: Int,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
) {
    val dayCount: Int get() = endIndex - startIndex + 1

    fun covers(index: Int): Boolean = index in startIndex..endIndex
}

/**
 * The per-day rows in [rowsByDay] folded back into the events they were expanded from, and stacked
 * into lanes of bars over [days] — the layout every calendar draws a multi-day event with: **one
 * continuous bar across the days it covers**, not a repeated chip per day.
 *
 * Lanes are packed longest-span-first, so a multi-day event always takes a lane above the single-day
 * entries and cannot be pushed out of sight by a busy Tuesday. Within a lane, bars never overlap, so
 * single-day entries on different days share one lane instead of each claiming a row.
 *
 * Callers pre-filter [rowsByDay] to the rows the strip is for (the week's all-day rows; the month's
 * multi-day ones). Rows of the same event that are *not* consecutive — a timed multi-day event whose
 * middle days alone land in the all-day strip — become separate spans, each flagged as continuing.
 */
fun daySpanLanes(
    days: List<LocalDate>,
    rowsByDay: Map<LocalDate, List<CalendarEvent>>,
): List<List<DaySpan>> {
    if (days.isEmpty()) return emptyList()
    val runs = LinkedHashMap<SpanKey, MutableList<IndexedValue<CalendarEvent>>>()
    days.forEachIndexed { index, date ->
        rowsByDay[date].orEmpty().forEach { event ->
            runs.getOrPut(spanKeyOf(event)) { mutableListOf() } += IndexedValue(index, event)
        }
    }
    return runs.values
        .flatMap { rows -> spansOf(rows, days) }
        .sortedWith(
            compareByDescending<DaySpan> { it.dayCount }
                .thenBy { it.startIndex }
                .thenBy { it.event.title },
        )
        .fold(mutableListOf<MutableList<DaySpan>>()) { lanes, span ->
            val lane = lanes.firstOrNull { placed -> placed.none { it.overlaps(span) } }
            if (lane == null) lanes += mutableListOf(span) else lane += span
            lanes
        }
        .map { lane -> lane.sortedBy { it.startIndex } }
}

private fun DaySpan.overlaps(other: DaySpan): Boolean =
    startIndex <= other.endIndex && other.startIndex <= endIndex

/** One event's rows over [days] cut into consecutive runs, each becoming a bar. */
private fun spansOf(rows: List<IndexedValue<CalendarEvent>>, days: List<LocalDate>): List<DaySpan> {
    val sorted = rows.sortedBy { it.index }
    val spans = mutableListOf<DaySpan>()
    var runStart = 0
    fun close(from: Int, to: Int) {
        val first = sorted[from]
        val covered = first.value.coveredDays()
        spans += DaySpan(
            event = first.value,
            startIndex = first.index,
            endIndex = sorted[to].index,
            continuesBefore = covered != null && covered.start < days[first.index],
            continuesAfter = covered != null && covered.endInclusive > days[sorted[to].index],
        )
    }
    for (i in 1..sorted.lastIndex) {
        if (sorted[i].index != sorted[i - 1].index + 1) {
            close(runStart, i - 1)
            runStart = i
        }
    }
    close(runStart, sorted.lastIndex)
    return spans
}

/**
 * What identifies the rows of one expanded event. The occurrence's address where the backend gave
 * one, and the event's own shape otherwise — two rows of the same series on consecutive days differ
 * by their bounds, so an uid-less calendar still folds correctly rather than welding neighbouring
 * occurrences into one bar.
 */
private data class SpanKey(
    val sourceId: String,
    val uid: String?,
    val recurrenceId: String?,
    val title: String,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
)

private fun spanKeyOf(event: CalendarEvent) =
    SpanKey(event.sourceId, event.uid, event.recurrenceId, event.title, event.start, event.end)
