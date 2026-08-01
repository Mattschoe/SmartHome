package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarFilterStoreTest {

    @Test
    fun toggle_hidesThenShowsAgain() {
        val hidden = CalendarFilters().toggle(CalendarView.Month, "calendar.arbejde")
        assertEquals(setOf("calendar.arbejde"), hidden.hidden(CalendarView.Month))
        assertTrue(hidden.toggle(CalendarView.Month, "calendar.arbejde").hidden(CalendarView.Month).isEmpty())
    }

    @Test
    fun toggle_isIndependentPerView() {
        // The whole point of the setting: the roster is noise in the month overview and the point of
        // the week grid, so hiding it in one must leave the other alone.
        val filters = CalendarFilters().toggle(CalendarView.Month, "calendar.arbejde")
        assertEquals(setOf("calendar.arbejde"), filters.hidden(CalendarView.Month))
        assertTrue(filters.hidden(CalendarView.Week).isEmpty())

        val both = filters.toggle(CalendarView.Week, "calendar.fælles")
        assertEquals(setOf("calendar.arbejde"), both.hidden(CalendarView.Month))
        assertEquals(setOf("calendar.fælles"), both.hidden(CalendarView.Week))
    }

    @Test
    fun keyValueStore_roundTripsTheFilters() {
        val backing = FakeKeyValueStore()
        val filters = CalendarFilters()
            .toggle(CalendarView.Month, "calendar.arbejde")
            .toggle(CalendarView.Week, "calendar.fødselsdage")
        KeyValueCalendarFilterStore(backing).write(filters)

        // A fresh store over the same backing is what a restart looks like.
        assertEquals(filters, KeyValueCalendarFilterStore(backing).read())
    }

    @Test
    fun keyValueStore_readsNothingAsEverythingVisible() {
        assertEquals(CalendarFilters(), KeyValueCalendarFilterStore(FakeKeyValueStore()).read())
    }

    @Test
    fun keyValueStore_readsACorruptBlobAsEverythingVisible() {
        // Showing every calendar is the safe reading of a blob that can't be understood: nothing
        // disappears unexplained, and the next toggle writes a good one.
        val backing = FakeKeyValueStore().apply { put("calendar.filters", "{ not json") }
        assertEquals(CalendarFilters(), KeyValueCalendarFilterStore(backing).read())
    }

    @Test
    fun inMemoryStore_keepsWhatItIsGiven() {
        val store = InMemoryCalendarFilterStore()
        assertEquals(CalendarFilters(), store.read())
        val filters = CalendarFilters().toggle(CalendarView.Week, "calendar.arbejde")
        store.write(filters)
        assertEquals(filters, store.read())
    }
}

private class FakeKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
}
