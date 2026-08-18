package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.data.model.ReminderRules
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

private val Zone = TimeZone.of("Europe/Copenhagen")

private val Sources = listOf(
    CalendarSource("calendar.matt", "Matt", canWrite = true),
    CalendarSource("calendar.arbejde", "Arbejde", canWrite = false),
)

private fun event(
    sourceId: String = "calendar.matt",
    title: String = "Møde",
    day: LocalDate = LocalDate(2026, 8, 20),
    start: LocalTime = LocalTime(14, 0),
    end: LocalTime = LocalTime(15, 0),
    uid: String? = "uid-1",
    recurrenceId: String? = null,
    allDay: Boolean = false,
    endDay: LocalDate = day,
) = expandCalendarEvent(
    sourceId = sourceId,
    title = title,
    start = LocalDateTime(day, start),
    end = LocalDateTime(endDay, end),
    allDay = allDay,
    uid = uid,
    recurrenceId = recurrenceId,
)

private fun at(day: LocalDate, time: LocalTime) = LocalDateTime(day, time).toInstant(Zone)

class ReminderLogicTest {

    @Test
    fun `event rule wins over the calendar default`() {
        val rules = ReminderRules(
            byEvent = mapOf(reminderKey("calendar.matt", "uid-1") to ReminderRule(10)),
            byCalendar = mapOf("calendar.matt" to 60),
        )
        assertEquals(10, offsetFor(event().first(), rules))
    }

    @Test
    fun `occurrence rule wins over the series rule`() {
        val rules = ReminderRules(
            byEvent = mapOf(
                reminderKey("calendar.matt", "uid-1") to ReminderRule(60),
                reminderKey("calendar.matt", "uid-1", "20260820T120000") to ReminderRule(10),
            ),
        )
        val occurrence = event(recurrenceId = "20260820T120000").first()
        assertEquals(10, offsetFor(occurrence, rules))
        // A different occurrence of the same series still takes the series rule.
        assertEquals(60, offsetFor(event(recurrenceId = "20260821T120000").first(), rules))
    }

    @Test
    fun `an explicit silence overrides the calendar default`() {
        val rules = ReminderRules(
            byEvent = mapOf(reminderKey("calendar.arbejde", "shift-2") to ReminderRule.None),
            byCalendar = mapOf("calendar.arbejde" to 30),
        )
        val muted = event(sourceId = "calendar.arbejde", uid = "shift-2").first()
        val other = event(sourceId = "calendar.arbejde", uid = "shift-3").first()
        assertNull(offsetFor(muted, rules))
        assertEquals(30, offsetFor(other, rules))
    }

    @Test
    fun `the calendar default applies where nothing else does, and says so`() {
        val rules = ReminderRules(byCalendar = mapOf("calendar.arbejde" to 30))
        val shift = event(sourceId = "calendar.arbejde").first()
        assertEquals(30, offsetFor(shift, rules))
        assertTrue(remindsByCalendarDefault(shift, rules))
        assertNull(offsetFor(event().first(), rules))
    }

    @Test
    fun `a timed reminder fires the offset before the start`() {
        val rules = ReminderRules(byCalendar = mapOf("calendar.matt" to 60))
        val due = dueReminders(
            events = event(),
            sources = Sources,
            rules = rules,
            from = at(LocalDate(2026, 8, 20), LocalTime(8, 0)),
            zone = Zone,
        )
        assertEquals(1, due.size)
        assertEquals(at(LocalDate(2026, 8, 20), LocalTime(13, 0)).toEpochMilliseconds(), due[0].whenMillis)
        assertEquals("Møde", due[0].title)
        assertEquals("Matt", due[0].calendarName)
    }

    @Test
    fun `an all-day reminder is anchored at the fixed hour on its first day`() {
        val rules = ReminderRules(byCalendar = mapOf("calendar.matt" to 30))
        val due = dueReminders(
            events = event(
                allDay = true,
                start = LocalTime(0, 0),
                end = LocalTime(0, 0),
                endDay = LocalDate(2026, 8, 22),
            ),
            sources = Sources,
            rules = rules,
            from = at(LocalDate(2026, 8, 19), LocalTime(8, 0)),
            zone = Zone,
        )
        // 09:00 on the 20th, less thirty minutes — and exactly once, for a three-day event.
        assertEquals(1, due.size)
        assertEquals(
            at(LocalDate(2026, 8, 20), LocalTime(8, 30)).toEpochMilliseconds(),
            due[0].whenMillis,
        )
    }

    @Test
    fun `a multi-day event reminds once, not once per day it covers`() {
        val rows = event(end = LocalTime(2, 0), endDay = LocalDate(2026, 8, 23))
        assertTrue(rows.size > 1, "the fixture must actually span several days")
        val due = dueReminders(
            events = rows,
            sources = Sources,
            rules = ReminderRules(byCalendar = mapOf("calendar.matt" to 10)),
            from = at(LocalDate(2026, 8, 19), LocalTime(8, 0)),
            zone = Zone,
        )
        assertEquals(1, due.size)
    }

    @Test
    fun `fire times already past are dropped, and so is anything beyond the horizon`() {
        val rules = ReminderRules(byCalendar = mapOf("calendar.matt" to 60))
        val soon = event(day = LocalDate(2026, 8, 20), uid = "soon")
        val far = event(day = LocalDate(2027, 8, 20), uid = "far")
        val past = event(day = LocalDate(2026, 1, 1), uid = "past")
        val due = dueReminders(
            events = soon + far + past,
            sources = Sources,
            rules = rules,
            from = at(LocalDate(2026, 8, 20), LocalTime(8, 0)),
            zone = Zone,
            horizon = 60.days,
        )
        assertEquals(1, due.size)
        assertTrue(due[0].key.contains("soon"))
    }

    @Test
    fun `the schedule is sorted soonest first and capped`() {
        val rules = ReminderRules(byCalendar = mapOf("calendar.matt" to 0))
        val events = (1..10).flatMap { i ->
            event(day = LocalDate(2026, 8, 20), start = LocalTime(23 - i, 0), end = LocalTime(23, 30), uid = "e$i")
        }
        val due = dueReminders(
            events = events,
            sources = Sources,
            rules = rules,
            from = at(LocalDate(2026, 8, 20), LocalTime(0, 0)),
            zone = Zone,
            cap = 3,
        )
        assertEquals(3, due.size)
        assertContentEquals(due.map { it.whenMillis }.sorted(), due.map { it.whenMillis })
    }

    @Test
    fun `an event with no uid or no stored bounds is skipped`() {
        val rules = ReminderRules(byCalendar = mapOf("calendar.matt" to 10))
        val noUid = event(uid = null)
        val noBounds = noUid.map { it.copy(uid = "uid-9", start = null, end = null) }
        val due = dueReminders(
            events = noUid + noBounds,
            sources = Sources,
            rules = rules,
            from = at(LocalDate(2026, 8, 20), LocalTime(8, 0)),
            zone = Zone,
        )
        assertTrue(due.isEmpty())
    }

    @Test
    fun `offsets read as Danish`() {
        assertEquals("Ved start", formatReminderOffset(0))
        assertEquals("10 min før", formatReminderOffset(10))
        assertEquals("1 time før", formatReminderOffset(60))
        assertEquals("2 timer før", formatReminderOffset(120))
        assertEquals("1 dag før", formatReminderOffset(24 * 60))
    }

    @Test
    fun `the reminder row says where the value came from`() {
        assertEquals("Ingen", formatReminderRule(null, null))
        assertEquals("1 time før (standard)", formatReminderRule(null, 60))
        assertEquals("Ingen", formatReminderRule(ReminderRule.None, 60))
        assertEquals("10 min før", formatReminderRule(ReminderRule(10), 60))
    }
}
