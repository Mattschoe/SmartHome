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
 *
 * The closing day is read back out of the item's `description` ([parseClosedOn]); an item Home
 * Assistant reports as open carries none whatever its description says. The creation day comes out of
 * the same field ([parseCreatedOn]) and is simply absent on anything this app did not add.
 */
fun mapTodoItems(dtos: List<HaTodoItemDto>, fallbackDue: LocalDate): List<TodoItem> =
    dtos.map { dto ->
        val done = dto.status == "completed"
        TodoItem(
            id = dto.uid,
            due = dto.due?.let { parseDueDate(it) } ?: fallbackDue,
            label = dto.summary,
            done = done,
            completedOn = if (done) parseClosedOn(dto.description) else null,
            createdOn = parseCreatedOn(dto.description),
        )
    }

/**
 * How the day a task was ticked off is carried in Home Assistant.
 *
 * HA's `todo` API has nowhere to put it — an item is needs_action or completed and that is all — so
 * the day is stamped into the item's `description`, the one free-text field `todo.update_item` can
 * write. Storing it there rather than in each app's own storage is what makes the tablet and the
 * phone agree: it is HA that remembers, so a device that was asleep when the box was ticked still
 * reads the right day when it wakes up.
 *
 * Deliberately human-readable, because it *is* visible in Home Assistant's own todo card. The app
 * owns this field — completing a task overwrites whatever description it had — which is the accepted
 * cost of having a shared answer at all.
 */
private val ClosedMarker = Regex("""\[lukket:(\d{4}-\d{2}-\d{2})]""")

/**
 * The day a task was written down, stamped into the same `description` for the same reason as
 * [ClosedMarker]: `due` is the day a task is *for* and may sit in the past, so it cannot say when the
 * task started existing — and a task must not appear on pages older than itself.
 */
private val CreatedMarker = Regex("""\[oprettet:(\d{4}-\d{2}-\d{2})]""")

/** The description to write when ticking a task off on [day]. See [ClosedMarker]. */
fun formatClosedMarker(day: LocalDate): String = "[lukket:$day]"

/** The description to write when a task is added on [day]. See [CreatedMarker]. */
fun formatCreatedMarker(day: LocalDate): String = "[oprettet:$day]"

/**
 * The whole `description` to write for an item created on [createdOn] and (if it is ticked off)
 * closed on [closedOn] — the one place both markers are assembled, because every write replaces the
 * field outright and must therefore carry back what it is not itself changing.
 */
fun formatTodoDescription(createdOn: LocalDate?, closedOn: LocalDate?): String =
    listOfNotNull(createdOn?.let { formatCreatedMarker(it) }, closedOn?.let { formatClosedMarker(it) })
        .joinToString(" ")

/** The day a completed item was ticked off, or `null` when its description carries no marker. */
fun parseClosedOn(description: String?): LocalDate? = description
    ?.let { ClosedMarker.find(it) }
    ?.let { runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull() }

/** The day an item was added, or `null` when its description carries no marker. */
fun parseCreatedOn(description: String?): LocalDate? = description
    ?.let { CreatedMarker.find(it) }
    ?.let { runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull() }

/** A todo `due` is a date on a due-date list, but may arrive as a datetime — either yields its day. */
private fun parseDueDate(raw: String): LocalDate? {
    runCatching { LocalDate.parse(raw) }.getOrNull()?.let { return it }
    runCatching { Instant.parse(raw) }.getOrNull()
        ?.let { return it.toLocalDateTime(TimeZone.currentSystemDefault()).date }
    return runCatching { LocalDateTime.parse(raw) }.getOrNull()?.date
}
