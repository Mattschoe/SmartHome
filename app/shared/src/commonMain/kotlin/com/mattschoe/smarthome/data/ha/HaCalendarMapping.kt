package com.mattschoe.smarthome.data.ha

import com.mattschoe.smarthome.data.expandCalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.TodoItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Pure mappers from Home Assistant's calendar/todo wire shapes to the domain models. Kept free of any
 * I/O so the awkward parts — all-day vs. timed boundaries, multi-day spans, missing due dates — are
 * unit-tested without a live instance. The per-day expansion itself lives in `DashboardLogic` and is
 * shared with the mock store.
 */

/** Map [dtos] to the per-day [CalendarEvent]s of the calendar [sourceId]. Unparseable events drop. */
fun mapCalendarEvents(
    sourceId: String,
    dtos: List<HaCalendarEventDto>,
    tz: TimeZone,
): List<CalendarEvent> = dtos.flatMap { dto ->
    val allDay = dto.start.date != null
    val start = dto.start.resolve(tz) ?: return@flatMap emptyList()
    val end = dto.end.resolve(tz) ?: start
    expandCalendarEvent(
        sourceId = sourceId,
        title = dto.summary,
        start = start,
        end = end,
        allDay = allDay,
        uid = dto.uid,
        recurrenceId = dto.recurrence_id,
        location = dto.location,
    )
}

/**
 * Resolve one calendar boundary to a local date-time: an all-day `date` becomes midnight, and a
 * `dateTime` is read as an absolute instant when it carries an offset (what HA sends) and as a plain
 * local time when it doesn't. An unparseable value yields `null`, which drops the event.
 */
private fun HaCalendarDateDto.resolve(tz: TimeZone): LocalDateTime? {
    date?.let { raw ->
        return runCatching { LocalDate.parse(raw) }.getOrNull()?.let { LocalDateTime(it, LocalTime(0, 0)) }
    }
    val raw = dateTime ?: return null
    runCatching { Instant.parse(raw) }.getOrNull()?.let { return it.toLocalDateTime(tz) }
    return runCatching { LocalDateTime.parse(raw) }.getOrNull()
}

/**
 * Map the `todo/item/subscribe` payload to [TodoItem]s. The panel buckets todos by day and the model
 * requires a due date, so an item created without one (from another client, or from Home Assistant's
 * own list view) is bucketed onto [fallbackDue] — today — rather than silently dropped where nobody
 * can see or fix it.
 */
fun mapTodoItems(dtos: List<HaTodoItemDto>, fallbackDue: LocalDate): List<TodoItem> =
    dtos.map { dto ->
        TodoItem(
            id = dto.uid,
            due = dto.due?.let { parseDueDate(it) } ?: fallbackDue,
            label = dto.summary,
            done = dto.status == "completed",
        )
    }

/** A todo `due` is a date on a due-date list, but may arrive as a datetime — either yields its day. */
private fun parseDueDate(raw: String): LocalDate? {
    runCatching { LocalDate.parse(raw) }.getOrNull()?.let { return it }
    runCatching { Instant.parse(raw) }.getOrNull()
        ?.let { return it.toLocalDateTime(TimeZone.currentSystemDefault()).date }
    return runCatching { LocalDateTime.parse(raw) }.getOrNull()?.date
}
