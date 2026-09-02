package com.mattschoe.smarthome.data.offline

import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.RecurrenceRange
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One write the home has not been told about yet — an add, an edit or a delete made while Home
 * Assistant was unreachable, kept until the socket comes back and it can go out.
 *
 * [id] is minted by whoever queues the write, and is the outbox's own key: it is what [complete]
 * removes, and — for a create — what the event is addressed by locally until Home Assistant mints a
 * real uid (see [localUid]).
 *
 * **Every case carries an explicit [SerialName].** The queue is persisted, so the discriminator is
 * on disk: renaming a class without keeping its serial name would silently discard writes somebody
 * is waiting to see land.
 */
@Serializable
sealed interface PendingWrite {
    val id: String
    val queuedAtEpochMs: Long

    /** Add [draft] to the calendar [sourceId]. Until it goes out, the event lives under [localUid]. */
    @Serializable
    @SerialName("calendar.create")
    data class CreateEvent(
        override val id: String,
        override val queuedAtEpochMs: Long,
        val sourceId: String,
        val draft: CalendarEventDraft,
    ) : PendingWrite

    /** Replace the event [uid] with [draft], over the occurrences [recurrenceId]/[range] name. */
    @Serializable
    @SerialName("calendar.update")
    data class UpdateEvent(
        override val id: String,
        override val queuedAtEpochMs: Long,
        val sourceId: String,
        val uid: String,
        val draft: CalendarEventDraft,
        val recurrenceId: String? = null,
        val range: RecurrenceRange = RecurrenceRange.ThisEvent,
    ) : PendingWrite

    /** Delete the event [uid], scoped by [recurrenceId]/[range] exactly as [UpdateEvent] is. */
    @Serializable
    @SerialName("calendar.delete")
    data class DeleteEvent(
        override val id: String,
        override val queuedAtEpochMs: Long,
        val sourceId: String,
        val uid: String,
        val recurrenceId: String? = null,
        val range: RecurrenceRange = RecurrenceRange.ThisEvent,
    ) : PendingWrite

    /**
     * Write a new task down. [localId] is the id the optimistic row already carries, and is how a
     * later tick or rename finds this add while it is still queued — Home Assistant mints the real
     * uid only when `todo.add_item` finally lands, and has never heard of [localId].
     *
     * [done]/[closedOn] are carried so the row stays as the person left it while it waits; they are
     * the overlay's business rather than the wire's, since `todo.add_item` can only add an open item.
     */
    @Serializable
    @SerialName("todo.add")
    data class AddTodo(
        override val id: String,
        override val queuedAtEpochMs: Long,
        val localId: String,
        val due: LocalDate,
        val label: String,
        val createdOn: LocalDate? = null,
        val done: Boolean = false,
        val closedOn: LocalDate? = null,
    ) : PendingWrite

    /**
     * Tick the task [todoId] off, or re-open it. The **resulting** state travels, not a flip, so
     * replaying the queue twice cannot land the box the wrong way up — and so the `description`
     * markers ([com.mattschoe.smarthome.data.ha.formatTodoDescription]) can be rebuilt from the write
     * alone: [closedOn] is the day it was ticked off, [createdOn] the day it was written down, which
     * the same field carries and which the write must therefore carry back through it.
     */
    @Serializable
    @SerialName("todo.toggle")
    data class ToggleTodo(
        override val id: String,
        override val queuedAtEpochMs: Long,
        val todoId: String,
        val done: Boolean,
        val closedOn: LocalDate? = null,
        val createdOn: LocalDate? = null,
    ) : PendingWrite

    /** Rename the task [todoId]. Never blank — a blank rename is a [RemoveTodo]. */
    @Serializable
    @SerialName("todo.edit")
    data class EditTodo(
        override val id: String,
        override val queuedAtEpochMs: Long,
        val todoId: String,
        val label: String,
    ) : PendingWrite

    /**
     * Delete the task [todoId]. The checklist has no delete control — editing a row to a blank label
     * is the escape hatch — but the *queue* must know which of the two it is holding, so the choice
     * is made at the intent rather than left for the drain to re-derive from an empty string.
     */
    @Serializable
    @SerialName("todo.remove")
    data class RemoveTodo(
        override val id: String,
        override val queuedAtEpochMs: Long,
        val todoId: String,
    ) : PendingWrite
}
