package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarPaletteColor
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.CalendarView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarPrefsStoreTest {

    private val sources = listOf(
        CalendarSource("calendar.roster", "Roster", canWrite = false),
        CalendarSource("calendar.matt", "Matt", canWrite = true),
        CalendarSource("calendar.cecilie", "Cecilie", canWrite = true),
        CalendarSource("calendar.home", "Home", canWrite = true),
    )

    @Test
    fun durationFor_fallsBackToTheStandardHour() {
        assertEquals(DefaultEventDurationMinutes, CalendarPrefs().durationFor("calendar.papkassehuset"))
    }

    @Test
    fun withDuration_isPerCalendar() {
        // The point of the setting: the shared calendar books in two-hour blocks without dragging
        // every other calendar's new events along with it.
        val prefs = CalendarPrefs().withDuration("calendar.papkassehuset", 120)
        assertEquals(120, prefs.durationFor("calendar.papkassehuset"))
        assertEquals(DefaultEventDurationMinutes, prefs.durationFor("calendar.matt"))
    }

    @Test
    fun withDuration_clampsWhatTheGridCannotDraw() {
        assertEquals(EVENT_DURATIONS.first(), CalendarPrefs().withDuration("c", 0).durationFor("c"))
        assertEquals(24 * 60, CalendarPrefs().withDuration("c", 99_999).durationFor("c"))
    }

    @Test
    fun withLastCreatedSource_preservesTheOtherPreferences() {
        val before = CalendarPrefs()
            .withColor("calendar.matt", CalendarPaletteColor.Slate)
            .withDuration("calendar.matt", 120)

        val after = before.withLastCreatedSource("calendar.cecilie")

        assertEquals(before.colorById, after.colorById)
        assertEquals(before.durationById, after.durationById)
        assertEquals("calendar.cecilie", after.lastCreatedSourceId)
    }

    @Test
    fun newEventSuggestions_excludeReadOnlyAndCalendarsHiddenInTheCurrentView() {
        val filters = CalendarFilters(
            hiddenInMonth = setOf("calendar.cecilie"),
            hiddenInWeek = setOf("calendar.matt"),
        )

        assertEquals(
            listOf("calendar.matt", "calendar.home"),
            newEventCalendarSuggestions(sources, filters, CalendarView.Month, CalendarPrefs()).map { it.id },
        )
        assertEquals(
            listOf("calendar.cecilie", "calendar.home"),
            newEventCalendarSuggestions(sources, filters, CalendarView.Week, CalendarPrefs()).map { it.id },
        )
    }

    @Test
    fun newEventSuggestions_moveTheRememberedCalendarFirstAndKeepTheRestStable() {
        val suggested = newEventCalendarSuggestions(
            sources = sources,
            filters = CalendarFilters(),
            view = CalendarView.Month,
            prefs = CalendarPrefs(lastCreatedSourceId = "calendar.home"),
        )

        assertEquals(
            listOf("calendar.home", "calendar.matt", "calendar.cecilie"),
            suggested.map { it.id },
        )
    }

    @Test
    fun newEventSuggestions_ignoreAnIneligibleRememberedCalendar() {
        val filters = CalendarFilters(hiddenInMonth = setOf("calendar.cecilie"))
        val expected = listOf("calendar.matt", "calendar.home")

        listOf("calendar.cecilie", "calendar.roster", "calendar.removed").forEach { remembered ->
            assertEquals(
                expected,
                newEventCalendarSuggestions(
                    sources,
                    filters,
                    CalendarView.Month,
                    CalendarPrefs(lastCreatedSourceId = remembered),
                ).map { it.id },
            )
        }
    }

    @Test
    fun applyCalendarPrefs_overridesOnlyWhatWasChosen() {
        val sources = listOf(
            CalendarSource("calendar.papkassehuset", "Papkassehuset", canWrite = true, color = "green"),
            CalendarSource("calendar.matt", "Matt", canWrite = true, color = "primary"),
        )
        val prefs = CalendarPrefs().withColor("calendar.papkassehuset", CalendarPaletteColor.Slate)

        val applied = applyCalendarPrefs(sources, prefs)
        assertEquals(CalendarPaletteColor.Slate, applied[0].colorOverride)
        // Home Assistant's own colour is left on the source rather than replaced: the override wins
        // when the dot is drawn, but nothing about the calendar itself has changed.
        assertEquals("green", applied[0].color)
        assertNull(applied[1].colorOverride)
    }

    @Test
    fun applyCalendarPrefs_ignoresCalendarsThatAreNoLongerThere() {
        // A calendar removed in Home Assistant leaves its entry in the blob behind. It must not
        // resurrect the calendar, and it must not throw.
        val sources = listOf(CalendarSource("calendar.matt", "Matt", canWrite = true))
        val prefs = CalendarPrefs().withColor("calendar.slettet", CalendarPaletteColor.Plum)
        assertEquals(sources, applyCalendarPrefs(sources, prefs))
    }

    @Test
    fun keyValueStore_roundTripsAllSettings() {
        val backing = FakePrefsKeyValueStore()
        val prefs = CalendarPrefs()
            .withColor("calendar.papkassehuset", CalendarPaletteColor.Terracotta)
            .withDuration("calendar.papkassehuset", 120)
            .withDuration("calendar.matt", 30)
            .withLastCreatedSource("calendar.matt")
        KeyValueCalendarPrefsStore(backing).write(prefs)

        // A fresh store over the same backing is what a restart looks like.
        assertEquals(prefs, KeyValueCalendarPrefsStore(backing).read())
    }

    @Test
    fun keyValueStore_readsOlderJsonWithoutARecentCalendar() {
        val backing = FakePrefsKeyValueStore().apply {
            put(
                "calendar.prefs",
                """{"colorById":{"calendar.matt":"Olive"},"durationById":{"calendar.matt":30}}""",
            )
        }

        assertEquals(
            CalendarPrefs()
                .withColor("calendar.matt", CalendarPaletteColor.Olive)
                .withDuration("calendar.matt", 30),
            KeyValueCalendarPrefsStore(backing).read(),
        )
    }

    @Test
    fun keyValueStore_readsNothingAsNothingChosen() {
        assertEquals(CalendarPrefs(), KeyValueCalendarPrefsStore(FakePrefsKeyValueStore()).read())
    }

    @Test
    fun keyValueStore_readsACorruptBlobAsNothingChosen() {
        // Falling back to the HA colours is the safe reading of a blob that can't be understood: the
        // calendars still draw and still differ, and the next choice writes a good one.
        val backing = FakePrefsKeyValueStore().apply { put("calendar.prefs", "{ not json") }
        assertEquals(CalendarPrefs(), KeyValueCalendarPrefsStore(backing).read())
    }

    @Test
    fun keyValueStore_readsAnUnknownColourNameAsNothingChosen() {
        // What a colour removed from the enum by a later version would look like on the way back in.
        val backing = FakePrefsKeyValueStore().apply {
            put("calendar.prefs", """{"colorById":{"calendar.matt":"Chartreuse"}}""")
        }
        assertEquals(CalendarPrefs(), KeyValueCalendarPrefsStore(backing).read())
    }

    @Test
    fun inMemoryStore_keepsWhatItIsGiven() {
        val store = InMemoryCalendarPrefsStore()
        assertEquals(CalendarPrefs(), store.read())
        val prefs = CalendarPrefs().withColor("calendar.matt", CalendarPaletteColor.Olive)
        store.write(prefs)
        assertEquals(prefs, store.read())
    }
}

private class FakePrefsKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
}
