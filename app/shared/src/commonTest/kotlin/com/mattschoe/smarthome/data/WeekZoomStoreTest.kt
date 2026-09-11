package com.mattschoe.smarthome.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekZoomStoreTest {

    @Test
    fun keyValueStore_roundTripsLevelsAcrossTheExpandedRange() {
        val backing = FakeZoomKeyValueStore()
        KeyValueWeekZoomStore(backing).write(240f)

        // A fresh store over the same backing is what a restart looks like.
        assertEquals(240f, KeyValueWeekZoomStore(backing).read())
    }

    @Test
    fun keyValueStore_readsNothingAsTheDesignDefault() {
        assertEquals(
            WeekHourHeightDefault,
            KeyValueWeekZoomStore(FakeZoomKeyValueStore()).read(),
        )
    }

    @Test
    fun keyValueStore_readsMalformedAndNonFiniteValuesAsTheDesignDefault() {
        val backing = FakeZoomKeyValueStore().apply { put("calendar.weekZoom", "sludder") }
        assertEquals(WeekHourHeightDefault, KeyValueWeekZoomStore(backing).read())

        backing.put("calendar.weekZoom", "NaN")
        assertEquals(WeekHourHeightDefault, KeyValueWeekZoomStore(backing).read())

        backing.put("calendar.weekZoom", "Infinity")
        assertEquals(WeekHourHeightDefault, KeyValueWeekZoomStore(backing).read())
    }

    @Test
    fun keyValueStore_clampsAValueOutsideTheRange() {
        // A level written by a build with a different range must never produce a grid that can't be
        // drawn — a 0dp hour would be a day with no height at all.
        val backing = FakeZoomKeyValueStore().apply { put("calendar.weekZoom", "0.0") }
        assertEquals(WeekHourHeightRange.start, KeyValueWeekZoomStore(backing).read())

        backing.put("calendar.weekZoom", "600.0")
        assertEquals(WeekHourHeightSafetyLimit, KeyValueWeekZoomStore(backing).read())
    }

    @Test
    fun clamp_rejectsNonFiniteLevels() {
        // A pinch's scale factor is a ratio of finger distances, so it can hand this a NaN — which
        // every comparison, and therefore a plain coerceIn, would let straight through.
        assertEquals(WeekHourHeightDefault, clampWeekHourHeight(Float.NaN))
        assertEquals(WeekHourHeightDefault, clampWeekHourHeight(Float.POSITIVE_INFINITY))
        assertEquals(WeekHourHeightDefault, clampWeekHourHeight(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun inMemoryStore_keepsWhatItIsGiven() {
        val store = InMemoryWeekZoomStore()
        assertEquals(WeekHourHeightDefault, store.read())
        store.write(300f)
        assertEquals(300f, store.read())
        store.write(1f)
        assertEquals(WeekHourHeightMin, store.read())
    }

    @Test
    fun range_separatesTheDefaultFromItsSafetyCeiling() {
        assertEquals(WeekHourHeightMin, WeekHourHeightRange.start)
        assertEquals(WeekHourHeightSafetyLimit, WeekHourHeightRange.endInclusive)
        assertTrue(WeekHourHeightDefault in WeekHourHeightRange)
        assertTrue(WeekHourHeightDefault < WeekHourHeightSafetyLimit)
        assertEquals(144f, WeekHourHeightMin * HoursPerDay)
        assertEquals(576f, WeekHourHeightDefault * HoursPerDay)
        assertEquals(11_520f, WeekHourHeightSafetyLimit * HoursPerDay)
    }
}

private class FakeZoomKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
}
