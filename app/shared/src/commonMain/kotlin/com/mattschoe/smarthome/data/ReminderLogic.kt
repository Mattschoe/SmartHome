package com.mattschoe.smarthome.data

import androidx.compose.ui.graphics.toArgb
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.data.model.ReminderRules
// The notification must carry the same colour the app gives that calendar, so the one rule for it is
// shared rather than restated here. It is a pure lookup over [CalendarSource]s, no Compose runtime.
import com.mattschoe.smarthome.ui.controls.calendar.calendarDotColor
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Turning the home's events plus its reminder rules into the list of alarms a device should arm.
 * Pure and fully unit-testable: no platform types, no clock of its own — the caller passes the
 * moment it is computing from.
 */

/**
 * When an all-day event reminds *from*. An all-day event has no start time to count back from, so it
 * is anchored at a fixed hour on its first day and the offset applies to that — one rule rather than
 * a second, all-day-only picker nobody would want to fill in.
 */
val ALL_DAY_ANCHOR: LocalTime = LocalTime(9, 0)

/**
 * How far ahead reminders are armed. Deliberately long: a phone that has not opened the dashboard in
 * a week must still fire for events that already existed when it was last awake.
 */
val REMINDER_HORIZON: Duration = 60.days

/**
 * How many alarms a device arms at once. Well above a household's real load, and low enough that a
 * calendar that somehow explodes can't exhaust the platform's alarm quota.
 */
const val REMINDER_CAP: Int = 100

/** The offsets the picker offers, in minutes before the event. `0` is "at the start". */
val REMINDER_OFFSETS: List<Int> = listOf(0, 10, 30, 60, 120, 24 * 60)

/**
 * One armed reminder, flattened so the platform side needs nothing but this to fire it — a receiver
 * woken after a reboot has no calendar, no connection and no Compose tree to look anything up in.
 *
 * [key] identifies this *firing*, not the rule behind it: it carries the occurrence's own start, so
 * every occurrence of a recurring series is its own alarm. [calendarName]/[calendarColorArgb] are
 * what makes the notification say which calendar it came from, which is the whole point of a
 * reminder that lives beside the event rather than on the device.
 */
@Serializable
data class ScheduledReminder(
    val key: String,
    val whenMillis: Long,
    val title: String,
    val body: String?,
    val calendarName: String,
    val calendarColorArgb: Int,
)

/**
 * The key a rule about this event is stored under: `sourceId|uid` for a whole series, and
 * `sourceId|uid#recurrenceId` for a single occurrence of one. Both may exist at once — the
 * occurrence wins, see [offsetFor].
 */
fun reminderKey(sourceId: String, uid: String, recurrenceId: String? = null): String =
    if (recurrenceId == null) "$sourceId|$uid" else "$sourceId|$uid#$recurrenceId"

/** The rule that applies to [event], or `null` when it inherits its calendar's default. */
fun ruleFor(event: CalendarEvent, rules: ReminderRules): ReminderRule? {
    val uid = event.uid ?: return null
    event.recurrenceId?.let { rec ->
        rules.byEvent[reminderKey(event.sourceId, uid, rec)]?.let { return it }
    }
    return rules.byEvent[reminderKey(event.sourceId, uid)]
}

/**
 * How many minutes before [event] to remind, or `null` for no reminder at all. Resolution order:
 * this occurrence's own rule → the series' rule → the calendar's standing default → nothing. A rule
 * whose offset is null is an *explicit* silence and stops the search, which is how one shift of a
 * calendar with a standing default is muted.
 */
fun offsetFor(event: CalendarEvent, rules: ReminderRules): Int? {
    ruleFor(event, rules)?.let { return it.offsetMin }
    return rules.byCalendar[event.sourceId]
}

/** Whether [event]'s reminder comes from its calendar's default rather than from a rule of its own. */
fun remindsByCalendarDefault(event: CalendarEvent, rules: ReminderRules): Boolean =
    ruleFor(event, rules) == null && rules.byCalendar[event.sourceId] != null

/**
 * Every reminder that should be armed on this device: what [events] and [rules] imply, from [from]
 * out to [horizon], soonest first and capped at [cap].
 *
 * [events] is the panel's own expanded list, where a multi-day event appears once per day it covers
 * ([expandCalendarEvent]). Only the row carrying the event's real start counts — the rest are the
 * same event seen again, and reminding once per covered day would be a bug rather than a feature.
 * A row with no [CalendarEvent.start] (one read from a cache written before those bounds were
 * carried) has nothing to count back from and is skipped; the next fetch replaces it.
 */
fun dueReminders(
    events: List<CalendarEvent>,
    sources: List<CalendarSource>,
    rules: ReminderRules,
    from: Instant,
    /** The home's own zone — what a wall-clock event start is read in. */
    zone: TimeZone = TimeZone.currentSystemDefault(),
    horizon: Duration = REMINDER_HORIZON,
    cap: Int = REMINDER_CAP,
): List<ScheduledReminder> {
    val until = from + horizon
    val seen = mutableSetOf<String>()
    val out = mutableListOf<ScheduledReminder>()
    for (event in events) {
        val start = event.start ?: continue
        // The one row that *is* the event, rather than a later day of it.
        if (event.date != start.date) continue
        val uid = event.uid ?: continue
        val offset = offsetFor(event, rules) ?: continue
        val anchor = if (event.allDay) LocalDateTime(start.date, ALL_DAY_ANCHOR) else start
        val fireAt = anchor.toInstant(zone) - offset.minutes
        if (fireAt <= from || fireAt > until) continue
        val key = "${reminderKey(event.sourceId, uid, event.recurrenceId)}@${start}"
        if (!seen.add(key)) continue
        out += ScheduledReminder(
            key = key,
            whenMillis = fireAt.toEpochMilliseconds(),
            title = event.title,
            body = reminderBody(event),
            calendarName = sources.firstOrNull { it.id == event.sourceId }?.displayName ?: event.sourceId,
            calendarColorArgb = calendarDotColor(event.sourceId, sources).toArgb(),
        )
    }
    out.sortBy { it.whenMillis }
    return if (out.size > cap) out.subList(0, cap).toList() else out
}

/**
 * The notification's second line: when the event runs, and where. Short rather than
 * [formatEventWhen]'s full sentence — a reminder arrives shortly before the thing it is about, so
 * the day it falls on is not news.
 */
fun reminderBody(event: CalendarEvent): String? {
    val start = event.start
    val whenPart = when {
        event.allDay -> AllDayLabel
        start == null -> event.time
        event.end != null && event.end.date == start.date ->
            "kl. ${formatTimeOfDay(start.time)}–${formatTimeOfDay(event.end.time)}"
        else -> "kl. ${formatTimeOfDay(start.time)}"
    }
    val location = event.location?.takeIf { it.isNotBlank() }
    return listOfNotNull(whenPart.takeIf { it.isNotBlank() }, location)
        .joinToString(" · ")
        .ifBlank { null }
}

/** "Ved start" / "10 min før" / "1 time før" / "1 dag før" — how an offset reads in the picker. */
fun formatReminderOffset(minutes: Int): String = when {
    minutes <= 0 -> "Ved start"
    minutes < 60 -> "$minutes min før"
    minutes == 60 -> "1 time før"
    minutes < 24 * 60 && minutes % 60 == 0 -> "${minutes / 60} timer før"
    minutes == 24 * 60 -> "1 dag før"
    minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} dage før"
    else -> "${minutes / 60} t ${minutes % 60} min før"
}

/** What the reminder row shows for [rule]: an offset, the explicit silence, or the inherited default. */
fun formatReminderRule(rule: ReminderRule?, calendarDefault: Int?): String = when {
    rule == null && calendarDefault == null -> ReminderNoneLabel
    rule == null -> "${formatReminderOffset(calendarDefault!!)} (standard)"
    rule.offsetMin == null -> ReminderNoneLabel
    else -> formatReminderOffset(rule.offsetMin)
}

/** How "no reminder" reads, both as a picker option and as a set value. */
const val ReminderNoneLabel = "Ingen"

/** How "whatever this calendar says" reads in the picker — the option that deletes an event's rule. */
fun formatReminderInherit(calendarDefault: Int?): String =
    if (calendarDefault == null) "Kalenderens standard (ingen)"
    else "Kalenderens standard (${formatReminderOffset(calendarDefault).lowercase()})"
