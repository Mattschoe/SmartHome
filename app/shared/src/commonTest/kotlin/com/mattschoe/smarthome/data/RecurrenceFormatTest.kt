package com.mattschoe.smarthome.data

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecurrenceFormatTest {

    private companion object {
        // A fixed zone so the UNTIL conversions land on a known UTC stamp.
        val CPH = TimeZone.of("Europe/Copenhagen")
    }

    @Test
    fun plainFrequencyIsWrittenWithoutAnIntervalOfOne() {
        assertEquals("FREQ=DAILY", buildRrule(Recurrence(RecurrenceFreq.Daily), allDay = false, tz = CPH))
        assertEquals("FREQ=YEARLY", buildRrule(Recurrence(RecurrenceFreq.Yearly), allDay = false, tz = CPH))
    }

    @Test
    fun intervalAndDaysAreWrittenInIsoOrder() {
        val rule = Recurrence(
            freq = RecurrenceFreq.Weekly,
            interval = 2,
            byDay = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY),
        )

        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE", buildRrule(rule, allDay = false, tz = CPH))
    }

    @Test
    fun daysAreDroppedOnAFrequencyThatCannotCarryThem() {
        val rule = Recurrence(RecurrenceFreq.Monthly, byDay = setOf(DayOfWeek.MONDAY))

        assertEquals("FREQ=MONTHLY", buildRrule(rule, allDay = false, tz = CPH))
    }

    @Test
    fun untilCarriesTheSameValueTypeAsTheEventsOwnStart() {
        val rule = Recurrence(RecurrenceFreq.Weekly, end = RecurrenceEnd.OnDate(LocalDate(2026, 12, 24)))

        // An all-day event's DTSTART is a bare date, so UNTIL has to be one too.
        assertEquals("FREQ=WEEKLY;UNTIL=20261224", buildRrule(rule, allDay = true, tz = CPH))
        // A timed one runs to the end of that day, in UTC — Copenhagen is +01:00 in December.
        assertEquals("FREQ=WEEKLY;UNTIL=20261224T225959Z", buildRrule(rule, allDay = false, tz = CPH))
    }

    @Test
    fun countIsWrittenInsteadOfAnEndDate() {
        val rule = Recurrence(RecurrenceFreq.Daily, end = RecurrenceEnd.AfterCount(10))

        assertEquals("FREQ=DAILY;COUNT=10", buildRrule(rule, allDay = false, tz = CPH))
    }

    @Test
    fun everyRuleTheSurfaceCanBuildRoundTrips() {
        val rules = listOf(
            Recurrence(RecurrenceFreq.Daily),
            Recurrence(RecurrenceFreq.Weekly, interval = 3),
            Recurrence(RecurrenceFreq.Weekly, byDay = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            Recurrence(RecurrenceFreq.Monthly, interval = 2, end = RecurrenceEnd.AfterCount(4)),
            Recurrence(RecurrenceFreq.Yearly, end = RecurrenceEnd.OnDate(LocalDate(2030, 1, 1))),
        )

        rules.forEach { rule ->
            assertEquals(rule, parseRrule(buildRrule(rule, allDay = true, tz = CPH)), "round-trip of $rule")
        }
    }

    @Test
    fun aRulePrefixedOrLowercasedIsStillRead() {
        val expected = Recurrence(RecurrenceFreq.Weekly, interval = 2, byDay = setOf(DayOfWeek.FRIDAY))

        assertEquals(expected, parseRrule("RRULE:freq=weekly;interval=2;byday=fr"))
    }

    @Test
    fun aRuleThisSurfaceCannotDrawIsNotReadBackAsNoRecurrence() {
        // The point of each: it must be held as its own string rather than replaced with "no repeat".
        assertNull(parseRrule("FREQ=MONTHLY;BYSETPOS=3;BYDAY=TU"))
        assertNull(parseRrule("FREQ=MONTHLY;BYMONTHDAY=15"))
        assertNull(parseRrule("FREQ=HOURLY"))
        assertNull(parseRrule("FREQ=WEEKLY;BYDAY=3TU"))
        // A positional day on a frequency that cannot carry days at all.
        assertNull(parseRrule("FREQ=DAILY;BYDAY=MO"))
        // Both bounds at once is not a rule this surface could have written.
        assertNull(parseRrule("FREQ=DAILY;COUNT=3;UNTIL=20261224"))
    }

    @Test
    fun nothingAtAllIsNoRule() {
        assertNull(parseRrule(null))
        assertNull(parseRrule("   "))
        assertNull(parseRrule("nonsense"))
    }

    @Test
    fun presetsReadAsTheirOwnLabels() {
        assertEquals(RecurrenceNoneLabel, formatRecurrence(null))
        assertEquals("Hver dag", formatRecurrence(Recurrence(RecurrenceFreq.Daily)))
        assertEquals("Hver uge", formatRecurrence(Recurrence(RecurrenceFreq.Weekly)))
        assertEquals("Hver måned", formatRecurrence(Recurrence(RecurrenceFreq.Monthly)))
        assertEquals("Hvert år", formatRecurrence(Recurrence(RecurrenceFreq.Yearly)))
    }

    @Test
    fun aCustomRuleIsSpelledOut() {
        assertEquals(
            "Hver 2. uge på man., ons.",
            formatRecurrence(
                Recurrence(
                    RecurrenceFreq.Weekly,
                    interval = 2,
                    byDay = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY),
                ),
            ),
        )
        assertEquals(
            "Hver dag, 10 gange",
            formatRecurrence(Recurrence(RecurrenceFreq.Daily, end = RecurrenceEnd.AfterCount(10))),
        )
        assertEquals(
            "Hver uge indtil 24. december 2026",
            formatRecurrence(
                Recurrence(RecurrenceFreq.Weekly, end = RecurrenceEnd.OnDate(LocalDate(2026, 12, 24))),
            ),
        )
    }

    @Test
    fun aWeeklySeriesFallsOnTheStartsOwnWeekdayWhenNoDaysAreNamed() {
        // 2026-08-05 is a Wednesday.
        val days = expandRecurrence(
            start = LocalDate(2026, 8, 5),
            rule = Recurrence(RecurrenceFreq.Weekly),
            from = LocalDate(2026, 8, 1),
            to = LocalDate(2026, 8, 31),
        )

        assertEquals(listOf(5, 12, 19, 26).map { LocalDate(2026, 8, it) }, days)
    }

    @Test
    fun namedDaysBeforeTheStartInItsOwnWeekAreNotOccurrences() {
        // Starts Wednesday; the rule names Monday and Friday, so that week yields only the Friday.
        val days = expandRecurrence(
            start = LocalDate(2026, 8, 5),
            rule = Recurrence(RecurrenceFreq.Weekly, byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            from = LocalDate(2026, 8, 1),
            to = LocalDate(2026, 8, 18),
        )

        assertEquals(
            listOf(LocalDate(2026, 8, 7), LocalDate(2026, 8, 10), LocalDate(2026, 8, 14), LocalDate(2026, 8, 17)),
            days,
        )
    }

    @Test
    fun aCountStopsTheSeriesEvenOutsideTheWindowAskedFor() {
        // Three occurrences exist; the window covers more, so the count is what ends it.
        val days = expandRecurrence(
            start = LocalDate(2026, 8, 5),
            rule = Recurrence(RecurrenceFreq.Daily, end = RecurrenceEnd.AfterCount(3)),
            from = LocalDate(2026, 8, 1),
            to = LocalDate(2026, 9, 30),
        )

        assertEquals(listOf(5, 6, 7).map { LocalDate(2026, 8, it) }, days)
    }

    @Test
    fun occurrencesBeforeTheWindowAreCountedButNotReturned() {
        // The fourth of five daily occurrences is the first one inside the window.
        val days = expandRecurrence(
            start = LocalDate(2026, 8, 5),
            rule = Recurrence(RecurrenceFreq.Daily, end = RecurrenceEnd.AfterCount(5)),
            from = LocalDate(2026, 8, 8),
            to = LocalDate(2026, 9, 30),
        )

        assertEquals(listOf(LocalDate(2026, 8, 8), LocalDate(2026, 8, 9)), days)
    }

    @Test
    fun anEndDateStopsTheSeriesOnTheDayItNames() {
        val days = expandRecurrence(
            start = LocalDate(2026, 8, 5),
            rule = Recurrence(RecurrenceFreq.Daily, end = RecurrenceEnd.OnDate(LocalDate(2026, 8, 7))),
            from = LocalDate(2026, 8, 1),
            to = LocalDate(2026, 8, 31),
        )

        // UNTIL is inclusive, so the 7th is still an occurrence.
        assertEquals(listOf(5, 6, 7).map { LocalDate(2026, 8, it) }, days)
    }

    @Test
    fun monthlyAndYearlyKeepTheDayOfTheMonth() {
        assertEquals(
            listOf(LocalDate(2026, 8, 5), LocalDate(2026, 10, 5), LocalDate(2026, 12, 5)),
            expandRecurrence(
                start = LocalDate(2026, 8, 5),
                rule = Recurrence(RecurrenceFreq.Monthly, interval = 2),
                from = LocalDate(2026, 8, 1),
                to = LocalDate(2026, 12, 31),
            ),
        )
        assertEquals(
            listOf(LocalDate(2026, 8, 5), LocalDate(2027, 8, 5)),
            expandRecurrence(
                start = LocalDate(2026, 8, 5),
                rule = Recurrence(RecurrenceFreq.Yearly),
                from = LocalDate(2026, 1, 1),
                to = LocalDate(2027, 12, 31),
            ),
        )
    }
}
