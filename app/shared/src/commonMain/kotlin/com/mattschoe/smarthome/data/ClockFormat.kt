package com.mattschoe.smarthome.data

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The three pieces the date/time header renders. */
data class ClockText(
    val weekday: String,
    val date: String,
    val time: String,
)

/**
 * Danish weekday names indexed by `dayOfWeek.isoDayNumber - 1` (Monday = 1 … Sunday = 7).
 * Capitalized because the header shows the weekday as a title ("Lørdag").
 */
val danishWeekdays: List<String> = listOf(
    "Mandag", "Tirsdag", "Onsdag", "Torsdag", "Fredag", "Lørdag", "Søndag",
)

/** Danish month names indexed by `monthNumber - 1`. Lowercase per Danish orthography ("4. juli 2026"). */
val danishMonths: List<String> = listOf(
    "januar", "februar", "marts", "april", "maj", "juni",
    "juli", "august", "september", "oktober", "november", "december",
)

/**
 * Pure clock formatter: an [instant] in [tz] becomes weekday/date/time strings. Locale name lists are
 * injected ([weekdayNames]/[monthNames]) so this stays locale-agnostic and unit-testable against fixed
 * instants — no platform locale calls. Date is `"$day. $monthName $year"`; time is 24h `HH:mm` when
 * [use24h], otherwise 12h `h:mm AM/PM`.
 */
fun formatClock(
    instant: Instant,
    tz: TimeZone,
    weekdayNames: List<String> = danishWeekdays,
    monthNames: List<String> = danishMonths,
    use24h: Boolean = true,
): ClockText {
    val dt = instant.toLocalDateTime(tz)
    val weekday = weekdayNames[dt.dayOfWeek.ordinal]
    val date = "${dt.day}. ${monthNames[dt.month.ordinal]} ${dt.year}"
    val time =
        if (use24h) { "${pad2(dt.hour)}:${pad2(dt.minute)}" }
        else {
            val meridiem = if (dt.hour < 12) "AM" else "PM"
            val hour12 = when (val h = dt.hour % 12) { 0 -> 12; else -> h
        }
        "$hour12:${pad2(dt.minute)} $meridiem"
    }
    return ClockText(weekday, date, time)
}

private fun pad2(value: Int): String = value.toString().padStart(2, '0')
