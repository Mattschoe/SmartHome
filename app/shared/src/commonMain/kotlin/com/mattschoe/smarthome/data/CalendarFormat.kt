package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * Where a given day sits within the event it belongs to. A multi-day event is expanded to one row
 * per day, and each row states the part of the event that falls on *that* day: the first shows when
 * it starts, the last when it ends, and the days in between are simply full.
 */
enum class EventDayPosition { Only, First, Middle, Last }

/** What an event occupying the whole day reads as in the agenda's time column. */
const val AllDayLabel = "Hele dagen"

/**
 * The agenda time column for one day of an event. An all-day event ([allDay]) reads [AllDayLabel] on
 * every day it covers; a timed one shows its start on the day it begins, its end on the day it ends,
 * and [AllDayLabel] on any day it merely spans.
 *
 * Pure so the (Danish) wording and the multi-day cases are unit-tested without a live instance.
 */
fun formatEventTime(
    start: LocalTime?,
    end: LocalTime?,
    position: EventDayPosition = EventDayPosition.Only,
    allDay: Boolean = false,
): String {
    if (allDay) return AllDayLabel
    return when (position) {
        EventDayPosition.Only, EventDayPosition.First -> start?.let { formatTimeOfDay(it) } ?: AllDayLabel
        EventDayPosition.Middle -> AllDayLabel
        EventDayPosition.Last -> end?.let { "til ${formatTimeOfDay(it)}" } ?: AllDayLabel
    }
}

/** 24-hour `HH:mm`, matching the left card's clock. */
fun formatTimeOfDay(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

/**
 * The detail popup's when-line — "ons. 19. aug. 2026, 10:00 – 18:00" for a timed event, the day (or
 * the run of days) alone for an all-day one, and the two dates spelled out for anything crossing
 * midnight.
 *
 * It reads the event's **whole** bounds ([CalendarEvent.start]/[CalendarEvent.end]) rather than the
 * per-day display fields, so a day tapped in the middle of a three-day event still says when the
 * whole thing runs. A row cached before those bounds were carried falls back to its own day and its
 * pre-formatted [CalendarEvent.time]. All-day ends are stored *exclusively*, so the last day it
 * covers is the day before the stored end.
 */
fun formatEventWhen(event: CalendarEvent): String {
    val start = event.start ?: return "${formatEventDate(event.date)}, ${event.time}"
    val end = event.end
    if (event.allDay) {
        // Exclusive end → the last covered day. A one-day event ends the morning after it starts.
        val lastDay = end?.date?.plus(-1, DateTimeUnit.DAY) ?: start.date
        return if (lastDay > start.date) {
            "${formatEventDate(start.date)} – ${formatEventDate(lastDay)}"
        } else {
            "${formatEventDate(start.date)}, $AllDayLabel"
        }
    }
    if (end == null) return "${formatEventDate(start.date)}, ${formatTimeOfDay(start.time)}"
    val from = "${formatEventDate(start.date)}, ${formatTimeOfDay(start.time)}"
    return if (end.date == start.date) {
        "$from – ${formatTimeOfDay(end.time)}"
    } else {
        "$from – ${formatEventDate(end.date)}, ${formatTimeOfDay(end.time)}"
    }
}

/** "ons. 19. aug. 2026" — the popup's date, abbreviated to keep the when-line to one row. */
private fun formatEventDate(date: LocalDate): String {
    val weekday = danishWeekdays[date.dayOfWeek.isoDayNumber - 1].take(3).lowercase()
    val month = danishMonths[date.month.number - 1].take(3)
    return "$weekday. ${date.day}. $month. ${date.year}"
}

/**
 * "lørdag den 15. august 2026" — the editor's boundary rows and the date picker's headline, where
 * there is a whole row to spend and an abbreviation would only make the day harder to read back.
 */
fun formatLongDate(date: LocalDate): String {
    val weekday = danishWeekdays[date.dayOfWeek.isoDayNumber - 1].lowercase()
    val month = danishMonths[date.month.number - 1]
    return "$weekday den ${date.day}. $month ${date.year}"
}

/** Minutes from midnight — the agenda's sort key for a timed event. */
fun minutesOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

/** Hours in a full day — the week grid's row count. */
const val HoursPerDay: Int = 24

/** Minutes in a full day: the end of a day that an event runs past, and the week grid's full height. */
const val MinutesPerDay: Int = HoursPerDay * 60

/**
 * Turn what the event editor's fields hold into the draft the write intents take. Pure, so the two
 * awkward normalisations are testable without a composable:
 *
 * - an **all-day** event ends *exclusively* — the day after the last one it covers, matching iCal,
 *   Home Assistant and [expandCalendarEvent]'s own reading — and its times are dropped to midnight;
 * - a **timed** event whose end lands at or before its start is read as running into the next day
 *   (20:00–02:00), which is what the wheels let someone pick without a second date to think about.
 *
 * The summary is trimmed but a blank one is passed through untouched: it is the save button's job to
 * refuse it, not this function's to invent a title.
 */
fun buildEventDraft(
    summary: String,
    start: LocalDateTime,
    end: LocalDateTime,
    allDay: Boolean,
    location: String?,
): CalendarEventDraft {
    val startAt: LocalDateTime
    val endAt: LocalDateTime
    if (allDay) {
        startAt = LocalDateTime(start.date, LocalTime(0, 0))
        val lastDay = maxOf(end.date, start.date)
        endAt = LocalDateTime(lastDay.plus(1, DateTimeUnit.DAY), LocalTime(0, 0))
    } else {
        startAt = start
        // The day after the start is always past it, whatever end date was left behind, so one step
        // settles every backwards case rather than walking days forward.
        endAt = if (end > start) end else LocalDateTime(start.date.plus(1, DateTimeUnit.DAY), end.time)
    }
    return CalendarEventDraft(
        summary = summary.trim(),
        start = startAt,
        end = endAt,
        allDay = allDay,
        location = location?.trim()?.ifBlank { null },
    )
}
