package com.mattschoe.smarthome.data.offline

import com.mattschoe.smarthome.data.model.TodoItem

/**
 * The checklist half of the offline outbox: how queued todo writes fold into each other, and what the
 * Opgaver panel draws while they wait. Pure — no socket, no store, no clock — so the rules can be
 * read and tested on their own, exactly as the calendar's in `PendingCalendar.kt`.
 */

/**
 * Fold a todo [write] into the queue, so what is replayed on reconnect is a sequence Home Assistant
 * can actually accept:
 *
 * - a **tick or rename of a still-queued add** folds into that add and is itself dropped. The
 *   backend has never heard of the local id, so `todo.update_item` naming it would be rejected —
 *   and there is nothing to update anyway, since the add has not gone out;
 * - a **removal of a still-queued add** takes the add with it and is itself dropped: the task never
 *   existed anywhere but here, so nothing needs to be told about it;
 * - a second **tick or rename of the same real task** replaces the first — last write wins, exactly
 *   as a live connection would have ended up;
 * - a **removal** of a real task drops the ticks and renames it makes moot, and is kept.
 *
 * Nothing here reads a write's queue position: order is send order, and [coalesce] only ever rewrites
 * a write in place or drops one, so a fold never moves a task ahead of one written down before it.
 */
fun coalesceTodo(pending: List<PendingWrite>, write: PendingWrite): List<PendingWrite> = when (write) {
    is PendingWrite.AddTodo -> pending + write

    is PendingWrite.ToggleTodo -> {
        val queuedAdd = pending.queuedAddFor(write.todoId)
        if (queuedAdd != null) {
            pending.map {
                if (it === queuedAdd) queuedAdd.copy(done = write.done, closedOn = write.closedOn) else it
            }
        } else {
            pending.filterNot { it is PendingWrite.ToggleTodo && it.todoId == write.todoId } + write
        }
    }

    is PendingWrite.EditTodo -> {
        val queuedAdd = pending.queuedAddFor(write.todoId)
        if (queuedAdd != null) {
            pending.map { if (it === queuedAdd) queuedAdd.copy(label = write.label) else it }
        } else {
            pending.filterNot { it is PendingWrite.EditTodo && it.todoId == write.todoId } + write
        }
    }

    is PendingWrite.RemoveTodo -> {
        val queuedAdd = pending.queuedAddFor(write.todoId)
        // Either way every queued write about this task goes; only a removal of a task the backend
        // actually has is worth sending, since the local-only one was never anywhere but here.
        val rest = pending.filterNot { it === queuedAdd || it.touchesTodo(write.todoId) }
        if (queuedAdd != null) rest else rest + write
    }

    // A write from another family has nothing to do with the checklist's rules — see the same branch
    // in [coalesceCalendar].
    else -> pending + write
}

/** The queued add that minted [todoId], or `null` — [todoId] names a task the backend already has. */
private fun List<PendingWrite>.queuedAddFor(todoId: String): PendingWrite.AddTodo? =
    filterIsInstance<PendingWrite.AddTodo>().firstOrNull { it.localId == todoId }

/** Whether this write changes the task [todoId] in place — what a removal of it makes moot. */
private fun PendingWrite.touchesTodo(todoId: String): Boolean = when (this) {
    is PendingWrite.ToggleTodo -> this.todoId == todoId
    is PendingWrite.EditTodo -> this.todoId == todoId
    else -> false
}

/**
 * The checklist as it will be once the queue drains: [todos] as the home last reported them, with
 * every queued write applied on top and its rows marked [TodoItem.pending].
 *
 * This is what keeps a task written down during an outage on screen — and, more sharply than on the
 * calendar, what keeps it there through a **reconnect**: the todo list arrives as a whole-list push,
 * so the first push after the socket comes back would otherwise wipe every row that has not been sent
 * yet. Writes are applied in queue order, so an add followed by a removal of the same local task
 * leaves nothing behind.
 *
 * The rows are built here rather than through `DashboardLogic`'s transitions: those flip a task's
 * `done` and re-derive its closing day from a clock, where a replayed write has to land on exactly
 * the state it was made with, however many times it is applied.
 */
fun applyPendingTodos(todos: List<TodoItem>, pending: List<PendingWrite>): List<TodoItem> {
    if (pending.isEmpty()) return todos
    var rows = todos
    for (write in pending) {
        rows = when (write) {
            is PendingWrite.AddTodo -> {
                val row = TodoItem(
                    id = write.localId,
                    due = write.due,
                    label = write.label,
                    done = write.done,
                    completedOn = write.closedOn,
                    createdOn = write.createdOn,
                    pending = true,
                )
                // The optimistic row the adapter already applied carries the same id: replace it
                // rather than adding a twin, so the queue is the one authority on its own rows.
                if (rows.any { it.id == row.id }) rows.map { if (it.id == row.id) row else it }
                else rows + row
            }

            is PendingWrite.ToggleTodo -> rows.map {
                if (it.id != write.todoId) it
                else it.copy(done = write.done, completedOn = write.closedOn, pending = true)
            }

            is PendingWrite.EditTodo -> rows.map {
                if (it.id != write.todoId) it else it.copy(label = write.label, pending = true)
            }

            is PendingWrite.RemoveTodo -> rows.filterNot { it.id == write.todoId }

            // A write from another family leaves the checklist alone, as in [applyPendingEvents].
            else -> rows
        }
    }
    return rows
}
