package com.mattschoe.smarthome.data.offline

import com.mattschoe.smarthome.data.expandCalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PendingCalendarTest {

    // --- coalescing ---

    @Test
    fun update_ofAQueuedCreate_rewritesTheCreate() {
        // Home Assistant has never heard of a local uid, so an update naming one would be rejected on
        // reconnect. The edit belongs in the create that has not gone out yet.
        val queued = coalesceCalendar(listOf(create("a")), update("b", localUid("a"), summary = "Tandlæge"))

        val only = assertIs<PendingWrite.CreateEvent>(queued.single())
        assertEquals("a", only.id)
        assertEquals("Tandlæge", only.draft.summary)
    }

    @Test
    fun delete_ofAQueuedCreate_removesBoth() {
        // The event only ever existed on this device: adding and then regretting it while offline
        // must leave the home with nothing to be told about.
        val queued = coalesceCalendar(listOf(create("a")), delete("b", localUid("a")))
        assertTrue(queued.isEmpty())
    }

    @Test
    fun delete_ofAQueuedCreate_leavesOtherWritesAlone() {
        val pending = listOf(create("a"), create("b"))
        val queued = coalesceCalendar(pending, delete("c", localUid("a")))
        assertEquals(listOf("b"), queued.map { it.id })
    }

    @Test
    fun update_ofTheSameTarget_replacesTheEarlierOne() {
        // Last write wins, exactly as a live connection would have ended up — the intermediate state
        // was never anywhere but on this screen.
        val pending = listOf(update("a", "uid-1", summary = "Første"))
        val queued = coalesceCalendar(pending, update("b", "uid-1", summary = "Anden"))

        val only = assertIs<PendingWrite.UpdateEvent>(queued.single())
        assertEquals("Anden", only.draft.summary)
    }

    @Test
    fun update_ofAnotherOccurrence_isKeptBesideTheFirst() {
        // Two occurrences of one series are two different events to the person moving them; only a
        // write that really reaches the same occurrence may swallow another.
        val pending = listOf(update("a", "uid-1", recurrenceId = "20260902T090000"))
        val queued = coalesceCalendar(pending, update("b", "uid-1", recurrenceId = "20260909T090000"))

        assertEquals(listOf("a", "b"), queued.map { it.id })
    }

    @Test
    fun update_ofADifferentEvent_isKept() {
        val pending = listOf(update("a", "uid-1"))
        val queued = coalesceCalendar(pending, update("b", "uid-2"))
        assertEquals(listOf("a", "b"), queued.map { it.id })
    }

    @Test
    fun delete_ofARealUid_dropsTheUpdatesItMakesMoot() {
        // Replaying an edit of an event that is about to be deleted is at best wasted, at worst an
        // error reply for an event the backend no longer has.
        val pending = listOf(update("a", "uid-1"), update("b", "uid-2"))
        val queued = coalesceCalendar(pending, delete("c", "uid-1"))

        assertEquals(listOf("b", "c"), queued.map { it.id })
        assertIs<PendingWrite.DeleteEvent>(queued.last())
    }

    @Test
    fun delete_ofOneOccurrence_keepsAnotherOccurrencesEdit() {
        val pending = listOf(update("a", "uid-1", recurrenceId = "20260909T090000"))
        val queued = coalesceCalendar(pending, delete("b", "uid-1", recurrenceId = "20260902T090000"))
        assertEquals(listOf("a", "b"), queued.map { it.id })
    }

    @Test
    fun delete_ofTheWholeSeries_dropsEveryOccurrencesEdit() {
        val pending = listOf(
            update("a", "uid-1", recurrenceId = "20260902T090000"),
            update("b", "uid-1", recurrenceId = "20260909T090000"),
        )
        val queued = coalesceCalendar(pending, delete("c", "uid-1", recurrenceId = null))
        assertEquals(listOf("c"), queued.map { it.id })
    }

    // --- the overlay ---

    @Test
    fun applyPendingEvents_withNothingQueued_returnsTheEventsUntouched() {
        val events = listOf(row("uid-1", LocalDate(2026, 9, 2)))
        assertEquals(events, applyPendingEvents(events, emptyList()))
    }

    @Test
    fun applyPendingEvents_drawsAQueuedCreate() {
        // The whole point: an event added with the box unreachable is on the calendar at once, marked
        // as not yet agreed with the home, and addressed by the uid its own queue entry mints.
        val added = applyPendingEvents(emptyList(), listOf(create("a", summary = "Tandlæge")))

        val only = added.single()
        assertEquals("Tandlæge", only.title)
        assertEquals(localUid("a"), only.uid)
        assertTrue(only.pending)
    }

    @Test
    fun applyPendingEvents_expandsAMultiDayCreateAcrossItsDays() {
        // A queued event spans days the same way a fetched one does — it must not be missing from
        // every day but its first.
        val write = PendingWrite.CreateEvent(
            id = "a",
            queuedAtEpochMs = 0,
            sourceId = "calendar.matt",
            draft = CalendarEventDraft(
                summary = "Ferie",
                start = LocalDateTime(2026, 9, 2, 0, 0),
                end = LocalDateTime(2026, 9, 5, 0, 0),
                allDay = true,
            ),
        )
        val rows = applyPendingEvents(emptyList(), listOf(write))

        assertEquals(
            listOf(LocalDate(2026, 9, 2), LocalDate(2026, 9, 3), LocalDate(2026, 9, 4)),
            rows.map { it.date },
        )
        assertTrue(rows.all { it.pending })
    }

    @Test
    fun applyPendingEvents_replacesTheRowsAnUpdateTargets() {
        val events = listOf(row("uid-1", LocalDate(2026, 9, 2), title = "Gammel"))
        val rows = applyPendingEvents(events, listOf(update("a", "uid-1", summary = "Ny")))

        val only = rows.single()
        assertEquals("Ny", only.title)
        assertEquals("uid-1", only.uid)
        assertTrue(only.pending)
    }

    @Test
    fun applyPendingEvents_updatesOnlyTheOccurrenceItNames() {
        // Moving one shift of a repeating roster must leave the other shifts exactly where they are.
        val events = listOf(
            row("uid-1", LocalDate(2026, 9, 2), recurrenceId = "20260902T090000", title = "Vagt"),
            row("uid-1", LocalDate(2026, 9, 9), recurrenceId = "20260909T090000", title = "Vagt"),
        )
        val rows = applyPendingEvents(
            events,
            listOf(update("a", "uid-1", recurrenceId = "20260902T090000", summary = "Flyttet vagt")),
        )

        assertEquals(2, rows.size)
        assertEquals(setOf("Flyttet vagt", "Vagt"), rows.map { it.title }.toSet())
        assertEquals(listOf("20260902T090000"), rows.filter { it.pending }.map { it.recurrenceId })
    }

    @Test
    fun applyPendingEvents_dropsTheRowsADeleteTargets() {
        val events = listOf(
            row("uid-1", LocalDate(2026, 9, 2)),
            row("uid-2", LocalDate(2026, 9, 2)),
        )
        val rows = applyPendingEvents(events, listOf(delete("a", "uid-1")))
        assertEquals(listOf("uid-2"), rows.map { it.uid })
    }

    @Test
    fun applyPendingEvents_deletesOnlyTheOccurrenceItNames() {
        val events = listOf(
            row("uid-1", LocalDate(2026, 9, 2), recurrenceId = "20260902T090000"),
            row("uid-1", LocalDate(2026, 9, 9), recurrenceId = "20260909T090000"),
        )
        val rows = applyPendingEvents(
            events,
            listOf(delete("a", "uid-1", recurrenceId = "20260902T090000")),
        )
        assertEquals(listOf("20260909T090000"), rows.map { it.recurrenceId })
    }

    @Test
    fun applyPendingEvents_appliesWritesInQueueOrder() {
        // A create and then a delete of the same local event that were queued in that order (an add
        // regretted before the connection came back) must leave nothing behind.
        val rows = applyPendingEvents(
            emptyList(),
            listOf(create("a"), delete("b", localUid("a"))),
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun applyPendingEvents_sortsTheResult() {
        // The panel filters these rows by day and draws them in list order, so a queued event has to
        // land in its place rather than at the end.
        val events = listOf(row("uid-1", LocalDate(2026, 9, 4)))
        val rows = applyPendingEvents(events, listOf(create("a")))
        assertEquals(listOf(LocalDate(2026, 9, 2), LocalDate(2026, 9, 4)), rows.map { it.date })
    }

    // --- uids ---

    @Test
    fun localUid_isRecognisableAndSpecificToItsWrite() {
        assertTrue(isLocalUid(localUid("a")))
        assertFalse(isLocalUid("2f1b-real-uid-from-ha"))
        assertFalse(isLocalUid(null))
    }
}

/** One fetched row, as the adapter would have published it before anything was queued. */
private fun row(
    uid: String,
    date: LocalDate,
    recurrenceId: String? = null,
    title: String = "Møde",
): CalendarEvent = expandCalendarEvent(
    sourceId = "calendar.matt",
    title = title,
    start = LocalDateTime(date, LocalTime(9, 0)),
    end = LocalDateTime(date, LocalTime(10, 0)),
    uid = uid,
    recurrenceId = recurrenceId,
).single()
