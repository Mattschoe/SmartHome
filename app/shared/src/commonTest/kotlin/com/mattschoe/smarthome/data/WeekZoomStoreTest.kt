package com.mattschoe.smarthome.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekZoomStoreTest {

    @Test
    fun keyValueStore_roundTripsTheLevel() {
        val backing = FakeZoomKeyValueStore()
        KeyValueWeekZoomStore(backing).write(11f)

        // A fresh store over the same backing is what a restart looks like.
        assertEquals(11f, KeyValueWeekZoomStore(backing).read())
    }

    @Test
    fun keyValueStore_readsNothingAsFullyExpanded() {
        // The grid was designed around the expanded end, so that is what an unpinched app opens at.
        assertEquals(
            WeekHourHeightRange.endInclusive,
            KeyValueWeekZoomStore(FakeZoomKeyValueStore()).read(),
        )
    }

    @Test
    fun keyValueStore_readsGarbageAsFullyExpanded() {
        val backing = FakeZoomKeyValueStore().apply { put("calendar.weekZoom", "sludder") }
        assertEquals(WeekHourHeightRange.endInclusive, KeyValueWeekZoomStore(backing).read())
    }

    @Test
    fun keyValueStore_clampsAValueOutsideTheRange() {
        // A level written by a build with a different range must never produce a grid that can't be
        // drawn — a 0dp hour would be a day with no height at all.
        val backing = FakeZoomKeyValueStore().apply { put("calendar.weekZoom", "0.0") }
        assertEquals(WeekHourHeightRange.start, KeyValueWeekZoomStore(backing).read())

        backing.put("calendar.weekZoom", "400.0")
        assertEquals(WeekHourHeightRange.endInclusive, KeyValueWeekZoomStore(backing).read())
    }

    @Test
    fun clamp_rejectsNonFiniteLevels() {
        // A pinch's scale factor is a ratio of finger distances, so it can hand this a NaN — which
        // every comparison, and therefore a plain coerceIn, would let straight through.
        assertEquals(WeekHourHeightRange.endInclusive, clampWeekHourHeight(Float.NaN))
        assertEquals(WeekHourHeightRange.endInclusive, clampWeekHourHeight(Float.POSITIVE_INFINITY))
        assertEquals(WeekHourHeightRange.endInclusive, clampWeekHourHeight(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun inMemoryStore_keepsWhatItIsGiven() {
        val store = InMemoryWeekZoomStore()
        assertEquals(WeekHourHeightRange.endInclusive, store.read())
        store.write(9f)
        assertEquals(9f, store.read())
        store.write(1f)
        assertEquals(WeekHourHeightRange.start, store.read())
    }

    @Test
    fun range_spansAScrollingDayDownToOneThatFits() {
        assertTrue(WeekHourHeightRange.start < WeekHourHeightRange.endInclusive)
        assertEquals(576f, WeekHourHeightRange.endInclusive * HoursPerDay)
        assertEquals(144f, WeekHourHeightRange.start * HoursPerDay)
    }
}

private class FakeZoomKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
}
