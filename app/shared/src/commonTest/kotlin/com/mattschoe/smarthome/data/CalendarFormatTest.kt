package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarFormatTest {

    @Test
    fun timedEventOnASingleDayShowsItsStart() {
        assertEquals("09:00", formatEventTime(LocalTime(9, 0), LocalTime(10, 30)))
        // Zero-padded on both parts, matching the left card's clock.
        assertEquals("07:05", formatEventTime(LocalTime(7, 5), LocalTime(8, 0)))
    }

    @Test
    fun allDayEventReadsAsAFullDayWhateverItsTimes() {
        assertEquals(
            AllDayLabel,
            formatEventTime(LocalTime(0, 0), LocalTime(0, 0), EventDayPosition.Only, allDay = true),
        )
        // Even on a middle/last day of a multi-day all-day run.
        assertEquals(
            AllDayLabel,
            formatEventTime(LocalTime(0, 0), LocalTime(0, 0), EventDayPosition.Last, allDay = true),
        )
    }

    @Test
    fun multiDayTimedEventStatesTheDayItIsOn() {
        val start = LocalTime(20, 0)
        val end = LocalTime(2, 30)
        assertEquals("20:00", formatEventTime(start, end, EventDayPosition.First))
        assertEquals(AllDayLabel, formatEventTime(start, end, EventDayPosition.Middle))
        assertEquals("til 02:30", formatEventTime(start, end, EventDayPosition.Last))
    }

    @Test
    fun minutesOfDayIsTheAgendaSortKey() {
        assertEquals(0, minutesOfDay(LocalTime(0, 0)))
        assertEquals(540, minutesOfDay(LocalTime(9, 0)))
        assertEquals(1439, minutesOfDay(LocalTime(23, 59)))
    }

    @Test
    fun allDayDraftEndsExclusivelyOnTheDayAfterTheLastOneItCovers() {
        val draft = buildEventDraft(
            summary = "Sommerhus",
            start = LocalDateTime(LocalDate(2026, 8, 3), LocalTime(14, 30)),
            end = LocalDateTime(LocalDate(2026, 8, 5), LocalTime(9, 0)),
            allDay = true,
            location = null,
        )
        // Times are dropped to midnight, and the 5th (the last day it covers) ends on the 6th.
        assertEquals(LocalDateTime(LocalDate(2026, 8, 3), LocalTime(0, 0)), draft.start)
        assertEquals(LocalDateTime(LocalDate(2026, 8, 6), LocalTime(0, 0)), draft.end)
        assertTrue(draft.allDay)
    }

    @Test
    fun singleDayAllDayDraftStillEndsOnTheNextDay() {
        val day = LocalDate(2026, 8, 3)
        val draft = buildEventDraft(
            summary = "Fridag",
            start = LocalDateTime(day, LocalTime(0, 0)),
            end = LocalDateTime(day, LocalTime(0, 0)),
            allDay = true,
            location = null,
        )
        assertEquals(LocalDateTime(LocalDate(2026, 8, 4), LocalTime(0, 0)), draft.end)
    }

    @Test
    fun timedDraftEndingAtOrBeforeItsStartRollsIntoTheNextDay() {
        val day = LocalDate(2026, 8, 3)
        val night = buildEventDraft(
            summary = "Fest",
            start = LocalDateTime(day, LocalTime(20, 0)),
            end = LocalDateTime(day, LocalTime(2, 0)),
            allDay = false,
            location = null,
        )
        assertEquals(LocalDateTime(LocalDate(2026, 8, 4), LocalTime(2, 0)), night.end)
        // Which is exactly what the agenda already renders on the day it runs out.
        assertEquals("til 02:00", formatEventTime(night.start.time, night.end.time, EventDayPosition.Last))

        // An end left equal to the start is the same case, not a zero-length event.
        val equal = buildEventDraft(
            summary = "Fest",
            start = LocalDateTime(day, LocalTime(20, 0)),
            end = LocalDateTime(day, LocalTime(20, 0)),
            allDay = false,
            location = null,
        )
        assertEquals(LocalDateTime(LocalDate(2026, 8, 4), LocalTime(20, 0)), equal.end)
    }

    @Test
    fun timedDraftSpanningDaysForwardIsLeftAlone() {
        val draft = buildEventDraft(
            summary = "Konference",
            start = LocalDateTime(LocalDate(2026, 8, 3), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 8, 5), LocalTime(16, 0)),
            allDay = false,
            location = "Aarhus",
        )
        assertEquals(LocalDateTime(LocalDate(2026, 8, 5), LocalTime(16, 0)), draft.end)
        assertEquals("Aarhus", draft.location)
    }

    // --- The detail popup's when-line ---

    @Test
    fun whenLine_ofATimedEventIsOneDayAndTwoTimes() {
        val event = eventWithBounds(
            start = LocalDateTime(LocalDate(2026, 8, 19), LocalTime(10, 0)),
            end = LocalDateTime(LocalDate(2026, 8, 19), LocalTime(18, 0)),
        )
        assertEquals("ons. 19. aug. 2026, 10:00 – 18:00", formatEventWhen(event))
    }

    @Test
    fun whenLine_ofATimedEventCrossingMidnightSpellsOutBothDays() {
        val event = eventWithBounds(
            start = LocalDateTime(LocalDate(2026, 8, 19), LocalTime(20, 0)),
            end = LocalDateTime(LocalDate(2026, 8, 20), LocalTime(2, 30)),
        )
        assertEquals("ons. 19. aug. 2026, 20:00 – tor. 20. aug. 2026, 02:30", formatEventWhen(event))
    }

    @Test
    fun whenLine_ofAnAllDayEventPullsItsExclusiveEndBackADay() {
        // Stored as 19 Aug → 20 Aug: one day, ending the morning after.
        val single = eventWithBounds(
            start = LocalDateTime(LocalDate(2026, 8, 19), LocalTime(0, 0)),
            end = LocalDateTime(LocalDate(2026, 8, 20), LocalTime(0, 0)),
            allDay = true,
        )
        assertEquals("ons. 19. aug. 2026, $AllDayLabel", formatEventWhen(single))

        // Stored as 19 Aug → 22 Aug: it covers the 19th through the 21st, not the 22nd.
        val run = eventWithBounds(
            start = LocalDateTime(LocalDate(2026, 8, 19), LocalTime(0, 0)),
            end = LocalDateTime(LocalDate(2026, 8, 22), LocalTime(0, 0)),
            allDay = true,
        )
        assertEquals("ons. 19. aug. 2026 – fre. 21. aug. 2026", formatEventWhen(run))
    }

    @Test
    fun whenLine_ofARowWithNoStoredBoundsFallsBackToItsOwnDayAndLabel() {
        // What a cache row written before start/end were carried looks like.
        val cached = CalendarEvent(
            date = LocalDate(2026, 8, 19),
            title = "Tandlæge",
            time = "09:00",
        )
        assertEquals("ons. 19. aug. 2026, 09:00", formatEventWhen(cached))
    }

    private fun eventWithBounds(
        start: LocalDateTime,
        end: LocalDateTime?,
        allDay: Boolean = false,
    ) = CalendarEvent(
        date = start.date,
        title = "Prøve",
        time = if (allDay) AllDayLabel else formatTimeOfDay(start.time),
        allDay = allDay,
        start = start,
        end = end,
    )

    @Test
    fun draftTrimsItsTextAndDropsABlankLocation() {
        val at = LocalDateTime(LocalDate(2026, 8, 3), LocalTime(9, 0))
        val draft = buildEventDraft(
            summary = "  Tandlæge  ",
            start = at,
            end = at.let { LocalDateTime(it.date, LocalTime(10, 0)) },
            allDay = false,
            location = "   ",
        )
        assertEquals("Tandlæge", draft.summary)
        assertNull(draft.location)

        // A blank title is passed through untouched — refusing it is the save button's job, and this
        // function inventing a placeholder would write that placeholder to the calendar.
        assertEquals("", buildEventDraft("   ", at, at, allDay = false, location = null).summary)
    }
}
