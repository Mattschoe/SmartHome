package com.mattschoe.smarthome.data.ha

import com.mattschoe.smarthome.data.AllDayLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HaCalendarMappingTest {

    private companion object {
        const val SOURCE = "calendar.papkassehuset"
        // A fixed zone so the offset-carrying fixtures resolve to a known wall clock.
        val CPH = TimeZone.of("Europe/Copenhagen")
    }

    private fun timed(start: String, end: String, summary: String = "Møde", uid: String? = "u1") =
        HaCalendarEventDto(
            summary = summary,
            uid = uid,
            start = HaCalendarDateDto(dateTime = start),
            end = HaCalendarDateDto(dateTime = end),
        )

    private fun allDay(start: String, end: String, summary: String = "Ferie") =
        HaCalendarEventDto(
            summary = summary,
            uid = "u-allday",
            start = HaCalendarDateDto(date = start),
            end = HaCalendarDateDto(date = end),
        )

    @Test
    fun timedEventBecomesOneRowOnItsOwnDay() {
        val events = mapCalendarEvents(SOURCE, listOf(timed("2026-08-04T09:00:00+02:00", "2026-08-04T10:00:00+02:00")), CPH)

        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(LocalDate(2026, 8, 4), event.date)
        assertEquals("09:00", event.time)
        assertEquals(540, event.startMinute)
        assertEquals(SOURCE, event.sourceId)
        assertEquals("u1", event.uid)
        assertTrue(!event.allDay)
    }

    @Test
    fun offsetLessDateTimeIsReadAsPlainLocalTime() {
        val events = mapCalendarEvents(SOURCE, listOf(timed("2026-08-04T09:00:00", "2026-08-04T10:00:00")), CPH)

        assertEquals("09:00", events.single().time)
    }

    @Test
    fun allDayEventFoldsBackItsExclusiveEndDate() {
        // A one-day all-day event ends on the *next* date; it must not spill onto that day.
        val events = mapCalendarEvents(SOURCE, listOf(allDay("2026-08-04", "2026-08-05")), CPH)

        assertEquals(listOf(LocalDate(2026, 8, 4)), events.map { it.date })
        assertEquals(AllDayLabel, events.single().time)
        assertEquals(null, events.single().startMinute) // all-day sorts above timed entries
        assertTrue(events.single().allDay)
    }

    @Test
    fun multiDayAllDayEventAppearsOnEveryDayItCovers() {
        val events = mapCalendarEvents(SOURCE, listOf(allDay("2026-08-04", "2026-08-07")), CPH)

        assertEquals(
            listOf(LocalDate(2026, 8, 4), LocalDate(2026, 8, 5), LocalDate(2026, 8, 6)),
            events.map { it.date },
        )
        assertTrue(events.all { it.time == AllDayLabel })
    }

    @Test
    fun multiDayTimedEventStatesStartAndEndOnTheRightDays() {
        val events = mapCalendarEvents(
            SOURCE,
            listOf(timed("2026-08-04T20:00:00+02:00", "2026-08-06T02:30:00+02:00")),
            CPH,
        )

        assertEquals(3, events.size)
        assertEquals(listOf("20:00", AllDayLabel, "til 02:30"), events.map { it.time })
        assertEquals(LocalDate(2026, 8, 6), events.last().date)
    }

    @Test
    fun timedEventEndingAtMidnightStaysOnItsOwnDay() {
        val events = mapCalendarEvents(
            SOURCE,
            listOf(timed("2026-08-04T22:00:00+02:00", "2026-08-05T00:00:00+02:00")),
            CPH,
        )

        assertEquals(listOf(LocalDate(2026, 8, 4)), events.map { it.date })
    }

    @Test
    fun unparseableOrTitlelessEventsDegradeInsteadOfCrashing() {
        val broken = HaCalendarEventDto(summary = "Kaput", start = HaCalendarDateDto(), end = HaCalendarDateDto())
        val untitled = timed("2026-08-04T09:00:00+02:00", "2026-08-04T10:00:00+02:00", summary = "")

        val events = mapCalendarEvents(SOURCE, listOf(broken, untitled), CPH)

        assertEquals(1, events.size) // the boundary-less one is dropped
        assertEquals("(uden titel)", events.single().title)
    }

    @Test
    fun todosMapStatusAndDueDate() {
        val today = LocalDate(2026, 8, 1)
        val items = listOf(
            HaTodoItemDto(uid = "a", summary = "Vand planterne", status = "needs_action", due = "2026-08-04"),
            HaTodoItemDto(uid = "b", summary = "Svar udlejeren", status = "completed", due = "2026-08-04"),
            // A datetime due (a list may store either) still resolves to its day.
            HaTodoItemDto(uid = "c", summary = "Ring", status = "needs_action", due = "2026-08-05T17:00:00+02:00"),
        )

        val todos = mapTodoItems(items, fallbackDue = today)

        assertEquals(listOf("a", "b", "c"), todos.map { it.id })
        assertEquals(listOf(false, true, false), todos.map { it.done })
        assertEquals(LocalDate(2026, 8, 4), todos[0].due)
        assertEquals(LocalDate(2026, 8, 5), todos[2].due)
    }

    @Test
    fun todoWithoutADueDateIsBucketedOntoTodayRatherThanDropped() {
        val today = LocalDate(2026, 8, 1)

        val todos = mapTodoItems(listOf(HaTodoItemDto(uid = "x", summary = "Løst punkt")), fallbackDue = today)

        assertEquals(today, todos.single().due)
    }
}
