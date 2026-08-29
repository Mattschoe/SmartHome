package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarSpansTest {

    private val monday = LocalDate(2026, 8, 24)
    private val week = List(DaysPerWeek) { monday.plus(it, DateTimeUnit.DAY) }

    private fun rows(
        title: String,
        from: LocalDate,
        toExclusive: LocalDate,
        allDay: Boolean = true,
        sourceId: String = "cal",
    ) = expandCalendarEvent(
        sourceId = sourceId,
        title = title,
        start = LocalDateTime(from, LocalTime(0, 0)),
        end = LocalDateTime(toExclusive, LocalTime(0, 0)),
        allDay = allDay,
        uid = title,
    )

    private fun byDay(vararg events: List<CalendarEvent>) =
        events.toList().flatten().groupBy { it.date }

    @Test
    fun multiDayEventBecomesOneBarAcrossItsDays() {
        // Mon–Fri inclusive: an all-day event ends exclusively, so Saturday is not covered.
        val lanes = daySpanLanes(week, byDay(rows("Paris", monday, monday.plus(5, DateTimeUnit.DAY))))
        assertEquals(1, lanes.size)
        val span = lanes.single().single()
        assertEquals(0, span.startIndex)
        assertEquals(4, span.endIndex)
        assertEquals(5, span.dayCount)
        assertFalse(span.continuesBefore)
        assertFalse(span.continuesAfter)
    }

    @Test
    fun aRunLeavingTheStripIsFlaggedCutAtThatEdge() {
        val lanes = daySpanLanes(
            week,
            byDay(rows("Ferie", monday.plus(-2, DateTimeUnit.DAY), monday.plus(9, DateTimeUnit.DAY))),
        )
        val span = lanes.single().single()
        assertEquals(0, span.startIndex)
        assertEquals(6, span.endIndex)
        assertTrue(span.continuesBefore)
        assertTrue(span.continuesAfter)
    }

    @Test
    fun theLongestRunTakesTheTopLaneWhateverDayTheOthersFallOn() {
        val lanes = daySpanLanes(
            week,
            byDay(
                // Three single-day entries all on the Tuesday — what used to bury the trip.
                rows("Backflush", week[1], week[2], sourceId = "a"),
                rows("Skift sengetøj", week[1], week[2], sourceId = "b"),
                rows("Tandlæge", week[1], week[2], sourceId = "c"),
                rows("Paris", monday, monday.plus(5, DateTimeUnit.DAY)),
            ),
        )
        assertEquals("Paris", lanes.first().single().event.title)
        // The three single-day entries sit under it, one per lane, since they share a day.
        assertEquals(4, lanes.size)
    }

    @Test
    fun singleDayEntriesOnDifferentDaysShareOneLane() {
        val lanes = daySpanLanes(
            week,
            byDay(
                rows("Mandag", week[0], week[1]),
                rows("Onsdag", week[2], week[3]),
                rows("Fredag", week[4], week[5]),
            ),
        )
        assertEquals(1, lanes.size)
        assertEquals(listOf(0, 2, 4), lanes.single().map { it.startIndex })
    }

    @Test
    fun theMiddleDaysOfATimedRunAreOneBarCutAtBothEnds() {
        // Mon 14:00 → Thu 10:00: only Tue and Wed have no clock time, so only those reach the strip.
        val all = expandCalendarEvent(
            sourceId = "cal",
            title = "Konference",
            start = LocalDateTime(week[0], LocalTime(14, 0)),
            end = LocalDateTime(week[3], LocalTime(10, 0)),
            uid = "konference",
        )
        val strip = all.filter { it.startMinute == null }.groupBy { it.date }
        val span = daySpanLanes(week, strip).single().single()
        assertEquals(1, span.startIndex)
        assertEquals(2, span.endIndex)
        assertTrue(span.continuesBefore)
        assertTrue(span.continuesAfter)
    }

    @Test
    fun aOneDayEventIsNotMultiDay() {
        val row = rows("Fridag", week[0], week[1]).single()
        assertFalse(row.isMultiDay())
        assertTrue(rows("Paris", monday, monday.plus(3, DateTimeUnit.DAY)).first().isMultiDay())
    }
}
