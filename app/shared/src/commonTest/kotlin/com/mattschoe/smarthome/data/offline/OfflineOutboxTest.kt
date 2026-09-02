package com.mattschoe.smarthome.data.offline

import com.mattschoe.smarthome.data.KeyValueStore
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.RecurrenceRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OfflineOutboxTest {

    @Test
    fun keyValueStore_roundTripsEveryCase() {
        // Each case is persisted under its own serial name; a round trip is what proves the whole
        // sealed hierarchy survives the blob rather than only the one case that happened to be used.
        val backing = FakeKeyValueStore()
        val writes = listOf(create("a"), update("b", uid = "uid-1"), delete("c", uid = "uid-2"))
        KeyValueOutboxStore(backing).write(writes)

        assertEquals(writes, KeyValueOutboxStore(backing).read())
    }

    @Test
    fun keyValueStore_roundTripsTodoWritesBesideCalendarOnes() {
        // Both families share the one blob, so the discriminators have to coexist in it: a queue
        // holding a calendar write and a checklist write must come back as both, not as whichever
        // half the decoder happened to recognise.
        val backing = FakeKeyValueStore()
        val writes = listOf(
            create("a"),
            addWrite("b", localId = "local-1"),
            toggleWrite("c", todoId = "uid-1", done = true, closedOn = LocalDate(2026, 9, 2)),
            editWrite("d", todoId = "uid-1", label = "Vask tøj"),
            removeWrite("e", todoId = "uid-2"),
        )
        KeyValueOutboxStore(backing).write(writes)

        assertEquals(writes, KeyValueOutboxStore(backing).read())
    }

    @Test
    fun enqueue_coalescesTodoWritesThroughTheOutbox() {
        // The same dispatcher routes both families, so the checklist's rules have to be reachable
        // from the outbox and not only from their own function.
        val outbox = OfflineOutbox(InMemoryOutboxStore())
        outbox.enqueue(addWrite("a", localId = "local-1"))
        outbox.enqueue(editWrite("b", todoId = "local-1", label = "Køb rugbrød"))

        val queued = assertIs<PendingWrite.AddTodo>(outbox.pending.value.single())
        assertEquals("Køb rugbrød", queued.label)
    }

    @Test
    fun keyValueStore_readsNothingAsAnEmptyQueue() {
        assertEquals(emptyList(), KeyValueOutboxStore(FakeKeyValueStore()).read())
    }

    @Test
    fun keyValueStore_readsACorruptBlobAsAnEmptyQueue() {
        // A record that cannot be decoded cannot be sent either, and a queue that fails to load would
        // otherwise jam the drain on every single reconnect. Losing it is the recoverable failure.
        val backing = FakeKeyValueStore().apply { put("writes.outbox", "{ not json") }
        assertEquals(emptyList(), KeyValueOutboxStore(backing).read())
    }

    @Test
    fun outbox_persistsWhatItQueues() {
        val backing = FakeKeyValueStore()
        val outbox = OfflineOutbox(KeyValueOutboxStore(backing))
        val write = create("a")
        outbox.enqueue(write)

        assertEquals(listOf(write), outbox.pending.value)
        assertEquals(listOf(write), KeyValueOutboxStore(backing).read())
    }

    @Test
    fun outbox_stillHoldsTheQueueAfterARestart() {
        // The whole point of persisting it: an event added while the box was unreachable must still
        // be waiting — and still be drawn — after the tablet has been power-cycled.
        val backing = FakeKeyValueStore()
        OfflineOutbox(KeyValueOutboxStore(backing)).enqueue(create("a"))

        val restarted = OfflineOutbox(KeyValueOutboxStore(backing))
        assertEquals(listOf(create("a")), restarted.pending.value)
    }

    @Test
    fun complete_removesTheWriteAndPersistsTheRest() {
        val backing = FakeKeyValueStore()
        val outbox = OfflineOutbox(KeyValueOutboxStore(backing))
        outbox.enqueue(create("a"))
        outbox.enqueue(create("b"))

        outbox.complete("a")

        assertEquals(listOf("b"), outbox.pending.value.map { it.id })
        assertEquals(listOf("b"), KeyValueOutboxStore(backing).read().map { it.id })
    }

    @Test
    fun enqueue_coalescesThroughTheOutbox() {
        // The outbox is where coalescing actually happens, so an edit of an event that has not gone
        // out yet must never reach the queue as a second write.
        val outbox = OfflineOutbox(InMemoryOutboxStore())
        outbox.enqueue(create("a"))
        outbox.enqueue(update("b", uid = localUid("a"), summary = "Tandlæge"))

        val queued = assertIs<PendingWrite.CreateEvent>(outbox.pending.value.single())
        assertEquals("Tandlæge", queued.draft.summary)
    }

    @Test
    fun inMemoryStore_keepsWhatItIsGiven() {
        val store = InMemoryOutboxStore()
        assertEquals(emptyList(), store.read())
        store.write(listOf(create("a")))
        assertEquals(listOf(create("a")), store.read())
    }
}

internal fun draft(summary: String = "Møde") = CalendarEventDraft(
    summary = summary,
    start = LocalDateTime(2026, 9, 2, 9, 0),
    end = LocalDateTime(2026, 9, 2, 10, 0),
)

internal fun create(id: String, sourceId: String = "calendar.matt", summary: String = "Møde") =
    PendingWrite.CreateEvent(id, queuedAtEpochMs = 0, sourceId = sourceId, draft = draft(summary))

internal fun update(
    id: String,
    uid: String,
    recurrenceId: String? = null,
    range: RecurrenceRange = RecurrenceRange.ThisEvent,
    summary: String = "Møde",
    sourceId: String = "calendar.matt",
) = PendingWrite.UpdateEvent(id, 0, sourceId, uid, draft(summary), recurrenceId, range)

internal fun delete(
    id: String,
    uid: String,
    recurrenceId: String? = null,
    range: RecurrenceRange = RecurrenceRange.ThisEvent,
    sourceId: String = "calendar.matt",
) = PendingWrite.DeleteEvent(id, 0, sourceId, uid, recurrenceId, range)

private class FakeKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
}
