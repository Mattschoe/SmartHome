package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarView
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Which calendars each of the Calendar panel's two views draws — the gear beside the month/week
 * toggle. The setting is **per view**, because the same calendar is noise in one and the point of
 * the other: a work roster buries the month grid's dots under shifts nobody is going *to*, while the
 * week grid is exactly where those shifts belong.
 *
 * Hidden ids are stored, not visible ones, so a calendar added in Home Assistant later shows up by
 * default rather than silently staying out of a list it was never in.
 */
@Serializable
data class CalendarFilters(
    val hiddenInMonth: Set<String> = emptySet(),
    val hiddenInWeek: Set<String> = emptySet(),
) {
    fun hidden(view: CalendarView): Set<String> = when (view) {
        CalendarView.Month -> hiddenInMonth
        CalendarView.Week -> hiddenInWeek
    }

    /** Show a hidden calendar in [view], or hide a shown one. The other view is untouched. */
    fun toggle(view: CalendarView, sourceId: String): CalendarFilters {
        val next = hidden(view).let { if (sourceId in it) it - sourceId else it + sourceId }
        return when (view) {
            CalendarView.Month -> copy(hiddenInMonth = next)
            CalendarView.Week -> copy(hiddenInWeek = next)
        }
    }
}

/** Where [CalendarFilters] are kept between runs. Reads and writes are best-effort, never fatal. */
interface CalendarFilterStore {
    fun read(): CalendarFilters
    fun write(filters: CalendarFilters)
}

/** A [CalendarFilterStore] over a platform [KeyValueStore], holding the filters as one JSON string. */
class KeyValueCalendarFilterStore(private val store: KeyValueStore) : CalendarFilterStore {

    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): CalendarFilters {
        val raw = runCatching { store.get(Key) }.getOrNull() ?: return CalendarFilters()
        // Filters written by an older model shape are discarded: showing every calendar is the safe
        // reading of a blob that can't be understood, since nothing then disappears unexplained.
        return runCatching { json.decodeFromString<CalendarFilters>(raw) }.getOrDefault(CalendarFilters())
    }

    override fun write(filters: CalendarFilters) {
        runCatching { store.put(Key, json.encodeToString(filters)) }
    }

    private companion object {
        const val Key = "calendar.filters"
    }
}

/** Filters that live only as long as the process — the fallback where no [KeyValueStore] exists. */
class InMemoryCalendarFilterStore : CalendarFilterStore {
    private var filters = CalendarFilters()

    override fun read(): CalendarFilters = filters

    override fun write(filters: CalendarFilters) {
        this.filters = filters
    }
}
