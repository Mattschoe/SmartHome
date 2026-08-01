package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MockAdapterTest {

    @Test
    fun subscribe_reflectsSeed() {
        val adapter = MockAdapter()
        assertEquals(seedHome(), adapter.subscribe().value)
    }

    @Test
    fun setBrightness_updatesFlowOptimistically() {
        val adapter = MockAdapter()
        val flow = adapter.subscribe()
        adapter.setBrightness(Room.Bedroom, 55)
        assertEquals(55, flow.value.rooms.getValue(Room.Bedroom).brightnessPct)
        assertTrue(flow.value.rooms.getValue(Room.Bedroom).isLightOn)
    }

    @Test
    fun mutations_onlyTouchTargetRoom() {
        val adapter = MockAdapter()
        val kitchenBefore = adapter.subscribe().value.rooms.getValue(Room.Kitchen)
        adapter.setVolume(Room.LivingRoom, 90)
        assertEquals(kitchenBefore, adapter.subscribe().value.rooms.getValue(Room.Kitchen))
        assertEquals(90, adapter.subscribe().value.rooms.getValue(Room.LivingRoom).audio?.volumePct)
    }

    @Test
    fun transportSetters_mutateTheStore() {
        val adapter = MockAdapter()
        val playingBefore = adapter.subscribe().value.rooms.getValue(Room.LivingRoom).audio!!.isPlaying
        adapter.togglePlay(Room.LivingRoom)
        adapter.setShuffle(Room.LivingRoom, true)
        adapter.setRepeat(Room.LivingRoom, RepeatMode.All)
        val audio = adapter.subscribe().value.rooms.getValue(Room.LivingRoom).audio!!
        assertEquals(!playingBefore, audio.isPlaying)
        assertTrue(audio.isShuffle)
        assertEquals(RepeatMode.All, audio.repeat)
    }

    @Test
    fun transportSetters_areNoOpOnSpeakerlessRoom() {
        val adapter = MockAdapter()
        adapter.togglePlay(Room.Kitchen)
        adapter.setVolume(Room.Kitchen, 40)
        assertNull(adapter.subscribe().value.rooms.getValue(Room.Kitchen).audio)
    }

    @Test
    fun seed_exposesNonEmptyBrowseShelves() {
        val state = MockAdapter().subscribe().value
        assertTrue(state.quickPicks.size >= 19)  // fills three 3×3 grid pages, so the page dots have work to do
        assertTrue(state.mixedForYou.isNotEmpty())
    }

    @Test
    fun setWarmth_appliesToRoom() {
        val adapter = MockAdapter()
        adapter.setWarmth(Room.Hall, Warmth.Cool)
        val state = adapter.subscribe().value
        assertEquals(Warmth.Cool, state.rooms.getValue(Room.Hall).lightWarmth)
        assertTrue(state.rooms.getValue(Room.Hall).isLightOn)
    }

    @Test
    fun addTodo_generatesIdAndAppends() {
        val adapter = MockAdapter()
        val before = adapter.subscribe().value.calendar.todos.size
        adapter.addTodo(LocalDate(2026, 7, 20), "Køb mælk")
        val todos = adapter.subscribe().value.calendar.todos
        assertEquals(before + 1, todos.size)
        val added = todos.last()
        assertEquals("Køb mælk", added.label)
        assertTrue(added.id.isNotBlank())
    }

    @Test
    fun toggleTodo_flipsThroughAdapter() {
        val adapter = MockAdapter()
        val todo = adapter.subscribe().value.calendar.todos.first()
        adapter.toggleTodo(todo.id)
        val after = adapter.subscribe().value.calendar.todos.first { it.id == todo.id }
        assertEquals(!todo.done, after.done)
    }

    @Test
    fun editTodo_blankRemovesThroughAdapter() {
        val adapter = MockAdapter()
        val id = adapter.subscribe().value.calendar.todos.first().id
        adapter.editTodo(id, "")
        assertNull(adapter.subscribe().value.calendar.todos.firstOrNull { it.id == id })
    }

    @Test
    fun createEvent_landsOnEveryDayItCovers() = runTest {
        val adapter = MockAdapter()
        val start = LocalDate(2026, 8, 4)

        adapter.createEvent(
            "calendar.papkassehuset",
            CalendarEventDraft(
                summary = "Sommerhus",
                start = LocalDateTime(start, LocalTime(0, 0)),
                end = LocalDateTime(LocalDate(2026, 8, 7), LocalTime(0, 0)),
                allDay = true,
            ),
        )

        val added = adapter.subscribe().value.calendar.events.filter { it.title == "Sommerhus" }
        assertEquals(
            listOf(start, LocalDate(2026, 8, 5), LocalDate(2026, 8, 6)),
            added.map { it.date },
        )
    }

    @Test
    fun calendarWrites_refuseAReadOnlySource() = runTest {
        val adapter = MockAdapter()
        val draft = CalendarEventDraft(
            summary = "Vagt",
            start = LocalDateTime(LocalDate(2026, 8, 4), LocalTime(16, 0)),
            end = LocalDateTime(LocalDate(2026, 8, 4), LocalTime(23, 0)),
        )

        // The subscribed work roster is not a write target, and neither is a calendar that isn't there.
        assertFailsWith<IllegalArgumentException> { adapter.createEvent("calendar.c_arbejde", draft) }
        assertFailsWith<IllegalArgumentException> { adapter.createEvent("calendar.nonexistent", draft) }
        assertFailsWith<IllegalArgumentException> { adapter.deleteEvent("calendar.c_arbejde", "seed-3") }
    }

    @Test
    fun deleteEvent_removesEveryDayOfTheEvent() = runTest {
        val adapter = MockAdapter()
        val uid = adapter.subscribe().value.calendar.events.first { it.title == "Sommerhus" }.uid!!

        adapter.deleteEvent("calendar.papkassehuset", uid)

        assertTrue(adapter.subscribe().value.calendar.events.none { it.uid == uid })
    }

    @Test
    fun updateEvent_replacesTheWholeEventRatherThanAppending() = runTest {
        val adapter = MockAdapter()
        val original = adapter.subscribe().value.calendar.events.first { it.uid == "seed-1" }

        adapter.updateEvent(
            "calendar.matt",
            uid = "seed-1",
            draft = CalendarEventDraft(
                summary = "Morgenmøde (flyttet)",
                start = LocalDateTime(original.date, LocalTime(11, 0)),
                end = LocalDateTime(original.date, LocalTime(12, 0)),
            ),
        )

        val replaced = adapter.subscribe().value.calendar.events.filter { it.uid == "seed-1" }
        assertEquals(1, replaced.size)
        assertEquals("Morgenmøde (flyttet)", replaced.single().title)
        assertEquals("11:00", replaced.single().time)
    }

    @Test
    fun climate_isReadOnlyAcrossMutations() {
        val adapter = MockAdapter()
        val climate = adapter.subscribe().value.climate
        adapter.setBrightness(Room.Kitchen, 10)
        adapter.toggleLight(Room.Bedroom)
        adapter.setVolume(Room.LivingRoom, 70)
        assertEquals(climate, adapter.subscribe().value.climate)
    }
}
