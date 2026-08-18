package com.mattschoe.smarthome.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * How often an event repeats, and the iCal `RRULE` it is written as. Pure — the string form, the
 * Danish wording and the awkward `UNTIL` cases are unit-tested without a live Home Assistant, the
 * same way [buildEventDraft] and the reminder formatters are.
 *
 * Only the shapes the Frekvens surface can produce round-trip: `FREQ`, `INTERVAL`, `BYDAY`, `COUNT`
 * and `UNTIL`. Anything richer (a rule written in Home Assistant's own UI, or by another client)
 * parses to `null`, which the editor reads as "show it, but leave it alone" rather than as "no
 * recurrence" — the difference between displaying a rule and silently deleting one.
 */
enum class RecurrenceFreq { Daily, Weekly, Monthly, Yearly }

/** When a series stops: never, on a last day, or after a number of occurrences. */
sealed interface RecurrenceEnd {
    data object Never : RecurrenceEnd
    data class OnDate(val date: LocalDate) : RecurrenceEnd
    data class AfterCount(val count: Int) : RecurrenceEnd
}

/**
 * A repetition rule. [byDay] is only meaningful for [RecurrenceFreq.Weekly] — the other frequencies
 * take their day from the event's own start — and an empty set means exactly that: repeat on
 * whatever weekday the event starts on, which is what `FREQ=WEEKLY` alone already says.
 */
data class Recurrence(
    val freq: RecurrenceFreq,
    val interval: Int = 1,
    val byDay: Set<DayOfWeek> = emptySet(),
    val end: RecurrenceEnd = RecurrenceEnd.Never,
)

/** The largest interval the custom sheet's number field accepts ("hver 99. uge" is already absurd). */
const val MaxRecurrenceInterval: Int = 99

/** The largest occurrence count the custom sheet accepts. */
const val MaxRecurrenceCount: Int = 999

/**
 * The presets the Frekvens picker offers above "Brugerdefineret", in order. `null` heads the list as
 * the default — an event that does not repeat.
 *
 * None of them set [Recurrence.byDay]: `FREQ=WEEKLY` on its own already repeats on the event's own
 * weekday, so a preset keeps working when the start date is moved. Only the custom sheet names days.
 */
val presetRecurrences: List<Recurrence?> = listOf(
    null,
    Recurrence(RecurrenceFreq.Daily),
    Recurrence(RecurrenceFreq.Weekly),
    Recurrence(RecurrenceFreq.Monthly),
    Recurrence(RecurrenceFreq.Yearly),
)

/** How "does not repeat" reads, both as the picker's first option and as the row's resting value. */
const val RecurrenceNoneLabel = "Gentag ikke"

/** What a rule we cannot read back reads as — see [parseRrule]. */
const val RecurrenceUnknownLabel = "Gentages"

/** `BYDAY` codes indexed by `isoDayNumber - 1`, so Monday leads as it does everywhere else here. */
private val icalDayCodes = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

/**
 * The `RRULE` value for [rule] — the property's value alone (`FREQ=WEEKLY;INTERVAL=2`), which is what
 * Home Assistant's `calendar/event/create` takes, not a full `RRULE:` line.
 *
 * `UNTIL` has to carry the same value type as the event's own `DTSTART` or the iCal layer behind Home
 * Assistant rejects the rule: an all-day event gets a bare date, a timed one the end of that day in
 * UTC. [tz] is the home's zone, which is what the editor's wall-clock fields are already in.
 */
fun buildRrule(rule: Recurrence, allDay: Boolean, tz: TimeZone): String {
    val parts = mutableListOf("FREQ=${rule.freq.icalName}")
    val interval = rule.interval.coerceIn(1, MaxRecurrenceInterval)
    if (interval > 1) parts += "INTERVAL=$interval"
    if (rule.freq == RecurrenceFreq.Weekly && rule.byDay.isNotEmpty()) {
        val days = rule.byDay.sortedBy { it.isoDayNumber }.joinToString(",") { icalDayCodes[it.isoDayNumber - 1] }
        parts += "BYDAY=$days"
    }
    when (val end = rule.end) {
        RecurrenceEnd.Never -> Unit
        is RecurrenceEnd.AfterCount -> parts += "COUNT=${end.count.coerceIn(1, MaxRecurrenceCount)}"
        is RecurrenceEnd.OnDate -> parts += "UNTIL=${formatUntil(end.date, allDay, tz)}"
    }
    return parts.joinToString(";")
}

/**
 * Read [raw] back into a [Recurrence], or `null` when it is absent, malformed, or uses anything the
 * Frekvens surface cannot express (`BYMONTHDAY`, `BYSETPOS`, `FREQ=HOURLY`, …). A `null` from a
 * non-blank rule is not "no recurrence": it means the rule exists but has to be passed through
 * untouched, which is how a series written elsewhere survives an edit here.
 */
fun parseRrule(raw: String?): Recurrence? {
    val text = raw?.trim()?.removePrefix("RRULE:")?.takeIf { it.isNotBlank() } ?: return null
    val parts = text.split(';').mapNotNull { part ->
        val key = part.substringBefore('=', "").trim().uppercase()
        val value = part.substringAfter('=', "").trim()
        if (key.isEmpty() || value.isEmpty()) null else key to value
    }.toMap()

    val freq = when (parts["FREQ"]?.uppercase()) {
        "DAILY" -> RecurrenceFreq.Daily
        "WEEKLY" -> RecurrenceFreq.Weekly
        "MONTHLY" -> RecurrenceFreq.Monthly
        "YEARLY" -> RecurrenceFreq.Yearly
        else -> return null
    }
    // WKST only says where a week starts for interval maths; it changes nothing we can show, and
    // dropping it on the round-trip is harmless. Anything else unread would be silently lost.
    if (parts.keys.any { it !in ReadableRruleKeys }) return null

    val interval = parts["INTERVAL"]?.let { it.toIntOrNull() ?: return null } ?: 1
    if (interval < 1) return null
    val byDay = parts["BYDAY"]?.split(',')?.map { code ->
        val index = icalDayCodes.indexOf(code.trim().uppercase())
        // A positional day ("3TU") is a monthly rule this surface cannot draw.
        if (index < 0) return null
        DayOfWeek(index + 1)
    }?.toSet().orEmpty()
    if (byDay.isNotEmpty() && freq != RecurrenceFreq.Weekly) return null

    val count = parts["COUNT"]?.let { it.toIntOrNull() ?: return null }
    val until = parts["UNTIL"]?.let { parseUntil(it) ?: return null }
    if (count != null && until != null) return null
    val end = when {
        count != null -> if (count < 1) return null else RecurrenceEnd.AfterCount(count)
        until != null -> RecurrenceEnd.OnDate(until)
        else -> RecurrenceEnd.Never
    }
    return Recurrence(freq, interval, byDay, end)
}

/** The `RRULE` keys [parseRrule] can account for; anything else means the rule is not ours to rewrite. */
private val ReadableRruleKeys = setOf("FREQ", "INTERVAL", "BYDAY", "COUNT", "UNTIL", "WKST")

/**
 * What the Frekvens row shows: "Gentag ikke", one of the presets, or the custom rule spelled out —
 * "Hver 2. uge på man., ons.", "Hver dag, 10 gange", "Hver uge indtil 24. december 2026".
 */
fun formatRecurrence(rule: Recurrence?): String {
    if (rule == null) return RecurrenceNoneLabel
    val head = if (rule.interval <= 1) {
        when (rule.freq) {
            RecurrenceFreq.Daily -> "Hver dag"
            RecurrenceFreq.Weekly -> "Hver uge"
            RecurrenceFreq.Monthly -> "Hver måned"
            RecurrenceFreq.Yearly -> "Hvert år"
        }
    } else {
        "Hver ${rule.interval}. ${rule.freq.danishUnit}"
    }
    val days = rule.byDay
        .takeIf { it.isNotEmpty() && rule.freq == RecurrenceFreq.Weekly }
        ?.sortedBy { it.isoDayNumber }
        ?.joinToString(", ") { danishWeekdays[it.isoDayNumber - 1].take(3).lowercase() + "." }
    val tail = when (val end = rule.end) {
        RecurrenceEnd.Never -> null
        is RecurrenceEnd.AfterCount -> "${end.count} gange"
        is RecurrenceEnd.OnDate -> "indtil ${formatRecurrenceEndDate(end.date)}"
    }
    return buildString {
        append(head)
        if (days != null) append(" på $days")
        if (tail != null) append(", $tail")
    }
}

/**
 * The last day a series runs, as the custom sheet's "på dag" row shows it and as the Frekvens row
 * spells it out — "24. december 2026". No weekday: the row is already a date, not an appointment.
 */
fun formatRecurrenceEndDate(date: LocalDate): String = "${formatDayAndMonth(date)} ${date.year}"

/** The unit as it reads after a number — "hver 2. **uge**". */
private val RecurrenceFreq.danishUnit: String
    get() = when (this) {
        RecurrenceFreq.Daily -> "dag"
        RecurrenceFreq.Weekly -> "uge"
        RecurrenceFreq.Monthly -> "måned"
        RecurrenceFreq.Yearly -> "år"
    }

/** How the frequency is named in the custom sheet's radio rows. */
val RecurrenceFreq.pickerLabel: String get() = danishUnit

private val RecurrenceFreq.icalName: String
    get() = when (this) {
        RecurrenceFreq.Daily -> "DAILY"
        RecurrenceFreq.Weekly -> "WEEKLY"
        RecurrenceFreq.Monthly -> "MONTHLY"
        RecurrenceFreq.Yearly -> "YEARLY"
    }

/**
 * `UNTIL` is inclusive, so a series that ends "på dag" runs to the end of that day: a bare `YYYYMMDD`
 * for an all-day event, and 23:59:59 of that day in [tz] rendered as UTC for a timed one.
 */
private fun formatUntil(date: LocalDate, allDay: Boolean, tz: TimeZone): String {
    val stamp = "${date.year.pad(4)}${date.month.number.pad(2)}${date.day.pad(2)}"
    if (allDay) return stamp
    val utc = LocalDateTime(date, LocalTime(23, 59, 59))
        .toInstant(tz)
        .toLocalDateTime(TimeZone.UTC)
    return "${utc.year.pad(4)}${utc.month.number.pad(2)}${utc.day.pad(2)}" +
        "T${utc.hour.pad(2)}${utc.minute.pad(2)}${utc.second.pad(2)}Z"
}

/**
 * The day an `UNTIL` names, in whichever of the two forms it was written. A UTC timestamp is read as
 * its own date rather than converted back: the day it was built from is the one the picker showed,
 * and 23:59:59 local can only ever land on that day or the next in UTC — the date part of a stamp we
 * wrote is the truth, and a stamp somebody else wrote is a boundary either reading calls the same
 * day within a few hours.
 */
private fun parseUntil(raw: String): LocalDate? {
    val digits = raw.trim().substringBefore('T')
    if (digits.length != 8 || !digits.all { it.isDigit() }) return null
    return runCatching {
        LocalDate(digits.take(4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt())
    }.getOrNull()
}

private fun Int.pad(width: Int): String = toString().padStart(width, '0')

/** The most occurrences [expandRecurrence] will produce — a guard on an endless rule, not a limit. */
private const val MaxExpandedOccurrences = 400

/**
 * The days a series starting on [start] falls on within `[from, to]`. Only the mock store needs this:
 * Home Assistant expands its own calendars server-side and sends one event per occurrence. It is here
 * rather than in `MockAdapter` because it is the same pure arithmetic the rest of this file is, and
 * it is what makes the picker verifiable on the desktop build.
 */
fun expandRecurrence(
    start: LocalDate,
    rule: Recurrence,
    from: LocalDate,
    to: LocalDate,
): List<LocalDate> {
    val interval = rule.interval.coerceAtLeast(1)
    val limit = (rule.end as? RecurrenceEnd.AfterCount)?.count?.coerceAtMost(MaxExpandedOccurrences)
        ?: MaxExpandedOccurrences
    val last = (rule.end as? RecurrenceEnd.OnDate)?.date
    val byDay = rule.byDay.takeIf { rule.freq == RecurrenceFreq.Weekly }.orEmpty().sortedBy { it.isoDayNumber }
    val days = mutableListOf<LocalDate>()
    var emitted = 0
    // With named weekdays the cursor walks Mondays rather than the start date itself, so every day a
    // week yields sits at or after it — which is what makes `cursor <= to` a safe bound to stop on.
    var cursor =
        if (byDay.isEmpty()) start
        else start.plus(1 - start.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
    while (emitted < limit && cursor <= to && (last == null || cursor <= last)) {
        val occurrences =
            if (byDay.isEmpty()) listOf(cursor)
            else byDay.map { cursor.plus(it.isoDayNumber - 1, DateTimeUnit.DAY) }
        for (day in occurrences) {
            if (day < start) continue
            if (last != null && day > last) continue
            if (emitted >= limit) break
            emitted++
            if (day in from..to) days += day
        }
        cursor = when (rule.freq) {
            RecurrenceFreq.Daily -> cursor.plus(interval, DateTimeUnit.DAY)
            RecurrenceFreq.Weekly -> cursor.plus(interval, DateTimeUnit.WEEK)
            RecurrenceFreq.Monthly -> cursor.plus(interval, DateTimeUnit.MONTH)
            RecurrenceFreq.Yearly -> cursor.plus(interval, DateTimeUnit.YEAR)
        }
    }
    return days.sorted()
}
