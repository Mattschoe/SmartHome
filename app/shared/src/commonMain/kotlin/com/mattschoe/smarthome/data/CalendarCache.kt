package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.TodoItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The last-fetched calendar window, persisted so the panel has something to render before (and
 * without) a connection. This app is the household's only calendar client: with nothing cached, a
 * Home Assistant outage leaves both people with no calendar at all, not even read-only.
 */
@Serializable
data class CachedCalendar(
    val sources: List<CalendarSource> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
)

/** Where a [CachedCalendar] is kept between runs. Reads and writes are best-effort, never fatal. */
interface CalendarCache {
    fun read(): CachedCalendar?
    fun write(snapshot: CachedCalendar)
}

/**
 * A [CalendarCache] over a platform [KeyValueStore], holding the snapshot as one JSON string. The
 * window is a few hundred small rows — small enough that a key/value store is the right shape and a
 * database would be ceremony.
 */
class KeyValueCalendarCache(private val store: KeyValueStore) : CalendarCache {

    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): CachedCalendar? {
        val raw = runCatching { store.get(Key) }.getOrNull() ?: return null
        // A snapshot written by an older model shape is simply discarded — the next fetch replaces it.
        return runCatching { json.decodeFromString<CachedCalendar>(raw) }.getOrNull()
    }

    override fun write(snapshot: CachedCalendar) {
        runCatching { store.put(Key, json.encodeToString(snapshot)) }
    }

    private companion object {
        const val Key = "calendar.snapshot"
    }
}
