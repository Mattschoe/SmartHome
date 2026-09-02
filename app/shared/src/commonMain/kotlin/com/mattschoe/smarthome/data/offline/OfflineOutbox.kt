package com.mattschoe.smarthome.data.offline

import com.mattschoe.smarthome.data.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.json.Json

/** Where the queue of unsent writes is kept between runs. Reads and writes are best-effort, never fatal. */
interface OutboxStore {
    fun read(): List<PendingWrite>
    fun write(writes: List<PendingWrite>)
}

/**
 * An [OutboxStore] over a platform [KeyValueStore], holding the whole queue as one JSON array — the
 * same shape as the calendar snapshot beside it. The queue is a handful of small records that only
 * exists between an outage and the next reconnect, so a key/value blob is the right size for it.
 */
class KeyValueOutboxStore(private val store: KeyValueStore) : OutboxStore {

    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): List<PendingWrite> {
        val raw = runCatching { store.get(Key) }.getOrNull() ?: return emptyList()
        // A queue written by an older model shape reads as empty: an unsendable record would jam the
        // drain on every reconnect, and a silently dropped write is recoverable by writing it again.
        return runCatching { json.decodeFromString<List<PendingWrite>>(raw) }.getOrDefault(emptyList())
    }

    override fun write(writes: List<PendingWrite>) {
        runCatching { store.put(Key, json.encodeToString(writes)) }
    }

    private companion object {
        const val Key = "writes.outbox"
    }
}

/** A queue that lasts only as long as the process — the fallback where no [KeyValueStore] exists. */
class InMemoryOutboxStore : OutboxStore {
    private var writes: List<PendingWrite> = emptyList()

    override fun read(): List<PendingWrite> = writes

    override fun write(writes: List<PendingWrite>) {
        this.writes = writes
    }
}

/**
 * The writes waiting for the home to become reachable, in the order they were made.
 *
 * The flow is seeded from [store] at construction, which is what makes an event added during an
 * outage survive an app restart: the write is still queued, and the overlay
 * ([applyPendingEvents]) still draws it, before any socket is up.
 *
 * Order is send order and is never reshuffled — [coalesce] only ever folds a write into an earlier
 * one or drops what a later one makes moot, so what reaches Home Assistant is what the person meant
 * to end up with rather than every keystroke on the way there.
 */
class OfflineOutbox(private val store: OutboxStore) {

    private val _pending = MutableStateFlow(store.read())
    val pending: StateFlow<List<PendingWrite>> = _pending.asStateFlow()

    /** Queue [write], folded into what is already waiting, and persist the result. */
    fun enqueue(write: PendingWrite) = mutate { coalesce(it, write) }

    /** Drop the write [id] — it has been sent, or it can never be. */
    fun complete(id: String) = mutate { queue -> queue.filterNot { it.id == id } }

    private fun mutate(transform: (List<PendingWrite>) -> List<PendingWrite>) {
        store.write(_pending.updateAndGet(transform))
    }
}

/**
 * Fold [write] into the writes already queued — the **one** place that happens, and the extension
 * point for a new family of writes: add a branch here and keep that family's rules in their own
 * file, the way the calendar's live in `PendingCalendar.kt`.
 *
 * Coalescing is not an optimisation. A queue is replayed against a backend that never saw the
 * intermediate states, so it has to be reduced to writes that make sense on their own: an edit to an
 * event Home Assistant has never heard of is not a smaller version of the truth, it is a request
 * that would be rejected.
 */
fun coalesce(pending: List<PendingWrite>, write: PendingWrite): List<PendingWrite> = when (write) {
    is PendingWrite.CreateEvent,
    is PendingWrite.UpdateEvent,
    is PendingWrite.DeleteEvent,
    -> coalesceCalendar(pending, write)

    is PendingWrite.AddTodo,
    is PendingWrite.ToggleTodo,
    is PendingWrite.EditTodo,
    is PendingWrite.RemoveTodo,
    -> coalesceTodo(pending, write)
}
