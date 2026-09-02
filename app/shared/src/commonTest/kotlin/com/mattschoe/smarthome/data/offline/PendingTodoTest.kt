package com.mattschoe.smarthome.data.offline

import com.mattschoe.smarthome.data.model.TodoItem
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PendingTodoTest {

    // --- coalescing ---

    @Test
    fun edit_ofAQueuedAdd_rewritesTheAdd() {
        // Home Assistant has never heard of a local id, so `todo.update_item` naming one would be
        // rejected on reconnect. The rename belongs in the add that has not gone out yet.
        val queued = coalesceTodo(listOf(addWrite("a", localId = "local-1")), editWrite("b", "local-1", "Køb mælk"))

        val only = assertIs<PendingWrite.AddTodo>(queued.single())
        assertEquals("a", only.id)
        assertEquals("Køb mælk", only.label)
    }

    @Test
    fun toggle_ofAQueuedAdd_foldsIntoTheAdd() {
        // Writing a task down and ticking it off during the same outage must leave one write, not a
        // pair whose second half names an id the home has never seen.
        val queued = coalesceTodo(
            listOf(addWrite("a", localId = "local-1")),
            toggleWrite("b", "local-1", done = true, closedOn = Day),
        )

        val only = assertIs<PendingWrite.AddTodo>(queued.single())
        assertTrue(only.done)
        assertEquals(Day, only.closedOn)
    }

    @Test
    fun remove_ofAQueuedAdd_removesBoth() {
        // The task only ever existed on this device: writing it down and regretting it while offline
        // must leave the home with nothing to be told about.
        val queued = coalesceTodo(listOf(addWrite("a", localId = "local-1")), removeWrite("b", "local-1"))
        assertTrue(queued.isEmpty())
    }

    @Test
    fun remove_ofAQueuedAdd_leavesOtherWritesAlone() {
        val pending = listOf(addWrite("a", localId = "local-1"), addWrite("b", localId = "local-2"))
        val queued = coalesceTodo(pending, removeWrite("c", "local-1"))
        assertEquals(listOf("b"), queued.map { it.id })
    }

    @Test
    fun remove_ofAQueuedAdd_takesTheWritesAgainstItTooEvenIfOneSurvived() {
        // Belt and braces on the invariant the drain depends on: nothing naming a local id may be
        // left in the queue once its add is gone, whatever order the writes arrived in.
        val pending = listOf(addWrite("a", localId = "local-1"), toggleWrite("b", "local-1", done = true))
        val queued = coalesceTodo(pending, removeWrite("c", "local-1"))
        assertTrue(queued.isEmpty())
    }

    @Test
    fun toggle_ofTheSameTask_replacesTheEarlierOne() {
        // Last write wins, exactly as a live connection would have ended up — the intermediate state
        // was never anywhere but on this screen. It also keeps a fidgeted checkbox from queueing a
        // write per tap.
        val pending = listOf(toggleWrite("a", "uid-1", done = true, closedOn = Day))
        val queued = coalesceTodo(pending, toggleWrite("b", "uid-1", done = false, closedOn = null))

        val only = assertIs<PendingWrite.ToggleTodo>(queued.single())
        assertEquals(false, only.done)
        assertEquals(null, only.closedOn)
    }

    @Test
    fun edit_ofTheSameTask_replacesTheEarlierOne() {
        val pending = listOf(editWrite("a", "uid-1", "Første"))
        val queued = coalesceTodo(pending, editWrite("b", "uid-1", "Anden"))

        val only = assertIs<PendingWrite.EditTodo>(queued.single())
        assertEquals("Anden", only.label)
    }

    @Test
    fun toggle_ofADifferentTask_isKept() {
        val pending = listOf(toggleWrite("a", "uid-1", done = true))
        val queued = coalesceTodo(pending, toggleWrite("b", "uid-2", done = true))
        assertEquals(listOf("a", "b"), queued.map { it.id })
    }

    @Test
    fun edit_andToggle_ofOneTaskCoexist() {
        // They say different things about the same row, so neither may swallow the other: renaming a
        // task and ticking it off are both worth replaying.
        val pending = listOf(editWrite("a", "uid-1", "Vask op"))
        val queued = coalesceTodo(pending, toggleWrite("b", "uid-1", done = true, closedOn = Day))
        assertEquals(listOf("a", "b"), queued.map { it.id })
    }

    @Test
    fun remove_ofARealId_dropsTheWritesItMakesMoot() {
        // Replaying a rename or a tick of a task that is about to be deleted is at best wasted, at
        // worst an error reply for a uid the backend no longer has.
        val pending = listOf(editWrite("a", "uid-1", "Vask op"), toggleWrite("b", "uid-1", done = true), editWrite("c", "uid-2", "Andet"))
        val queued = coalesceTodo(pending, removeWrite("d", "uid-1"))

        assertEquals(listOf("c", "d"), queued.map { it.id })
    }

    @Test
    fun aCalendarWrite_passesThroughUntouched() {
        // The two families share a queue but not their rules; each leaves the other's writes alone.
        val pending = listOf(addWrite("a", localId = "local-1"))
        val queued = coalesceTodo(pending, create("b"))
        assertEquals(listOf("a", "b"), queued.map { it.id })
    }

    // --- the overlay ---

    @Test
    fun anEmptyQueue_leavesTheListAlone() {
        val todos = listOf(todo("uid-1", "Vask op"))
        assertEquals(todos, applyPendingTodos(todos, emptyList()))
    }

    @Test
    fun add_appendsAPendingRowToAnEmptyList() {
        val rows = applyPendingTodos(emptyList(), listOf(addWrite("a", localId = "local-1", label = "Køb mælk")))

        val only = rows.single()
        assertEquals("local-1", only.id)
        assertEquals("Køb mælk", only.label)
        assertTrue(only.pending)
    }

    @Test
    fun add_keepsWhatTheHomeAlreadyReported() {
        val todos = listOf(todo("uid-1", "Vask op"))
        val rows = applyPendingTodos(todos, listOf(addWrite("a", localId = "local-1", label = "Køb mælk")))

        assertEquals(listOf("Vask op", "Køb mælk"), rows.map { it.label })
        assertEquals(listOf(false, true), rows.map { it.pending })
    }

    @Test
    fun add_replacesTheOptimisticRowRatherThanTwinningIt() {
        // The adapter applies the same row optimistically under the same id. The queue is the one
        // authority on its own rows, so the overlay overwrites it instead of adding a second copy.
        val todos = listOf(todo("local-1", "Køb mæl"))
        val rows = applyPendingTodos(todos, listOf(addWrite("a", localId = "local-1", label = "Køb mælk")))

        val only = rows.single()
        assertEquals("Køb mælk", only.label)
        assertTrue(only.pending)
    }

    @Test
    fun aWholeListPushFromHomeAssistant_cannotEraseAQueuedAdd() {
        // The regression that matters most. The todo list arrives as a whole-list push, so the very
        // first push after the socket comes back is a list that knows nothing about what was written
        // down during the outage. Re-applying the queue on every publish is what keeps the task on
        // screen until it has actually gone out.
        val queue = listOf(addWrite("a", localId = "local-1", label = "Køb mælk"))
        val beforeReconnect = applyPendingTodos(listOf(todo("local-1", "Køb mælk")), queue)
        val homeAssistantsList = listOf(todo("uid-1", "Vask op"))

        val afterPush = applyPendingTodos(homeAssistantsList, queue)

        assertEquals(listOf("Køb mælk"), beforeReconnect.map { it.label })
        assertEquals(listOf("Vask op", "Køb mælk"), afterPush.map { it.label })
    }

    @Test
    fun toggle_setsTheStateItWasMadeWith() {
        // The resulting state travels rather than a flip, so applying the overlay twice — which every
        // rebuild does — lands the box the same way up both times.
        val todos = listOf(todo("uid-1", "Vask op"))
        val queue = listOf(toggleWrite("a", "uid-1", done = true, closedOn = Day))

        val once = applyPendingTodos(todos, queue)
        val twice = applyPendingTodos(once, queue)

        assertEquals(true, once.single().done)
        assertEquals(Day, once.single().completedOn)
        assertTrue(once.single().pending)
        assertEquals(once, twice)
    }

    @Test
    fun toggle_ofATaskTheHomeNoLongerHas_changesNothing() {
        // Deleted on another device while this one was asleep: the write is replayed and rejected,
        // but until then the overlay must not conjure a row back onto the page.
        val todos = listOf(todo("uid-1", "Vask op"))
        assertEquals(todos, applyPendingTodos(todos, listOf(toggleWrite("a", "uid-2", done = true))))
    }

    @Test
    fun edit_relabelsTheRowItNames() {
        val todos = listOf(todo("uid-1", "Vask op"), todo("uid-2", "Køb mælk"))
        val rows = applyPendingTodos(todos, listOf(editWrite("a", "uid-1", "Vask tøj")))

        assertEquals(listOf("Vask tøj", "Køb mælk"), rows.map { it.label })
        assertEquals(listOf(true, false), rows.map { it.pending })
    }

    @Test
    fun remove_takesTheRowOffThePage() {
        val todos = listOf(todo("uid-1", "Vask op"), todo("uid-2", "Køb mælk"))
        val rows = applyPendingTodos(todos, listOf(removeWrite("a", "uid-1")))
        assertEquals(listOf("Køb mælk"), rows.map { it.label })
    }

    @Test
    fun writesApplyInQueueOrder() {
        // An add followed by a removal of the same local task leaves nothing behind, the way it does
        // on the calendar — the two writes have already coalesced away in practice, but the overlay
        // must not depend on that to draw the right thing.
        val queue = listOf(
            addWrite("a", localId = "local-1", label = "Køb mælk"),
            removeWrite("b", "local-1"),
        )
        assertTrue(applyPendingTodos(emptyList(), queue).isEmpty())
    }

    @Test
    fun aCalendarWrite_leavesTheChecklistAlone() {
        val todos = listOf(todo("uid-1", "Vask op"))
        assertEquals(todos, applyPendingTodos(todos, listOf(create("a"))))
    }

    @Test
    fun add_carriesTheDayItWasWrittenDown() {
        // A task must not appear on pages older than itself, and the creation day is the only thing
        // that can say so — `due` is the day the task is *for* and is free to sit in the past.
        val rows = applyPendingTodos(
            emptyList(),
            listOf(addWrite("a", localId = "local-1", due = LocalDate(2026, 8, 30), createdOn = Day)),
        )
        assertEquals(Day, rows.single().showsFrom)
    }
}

private val Day = LocalDate(2026, 9, 2)

private fun todo(id: String, label: String, done: Boolean = false) =
    TodoItem(id = id, due = Day, label = label, done = done)

internal fun addWrite(
    id: String,
    localId: String,
    label: String = "Køb mælk",
    due: LocalDate = LocalDate(2026, 9, 2),
    createdOn: LocalDate? = LocalDate(2026, 9, 2),
) = PendingWrite.AddTodo(id, queuedAtEpochMs = 0, localId = localId, due = due, label = label, createdOn = createdOn)

internal fun toggleWrite(
    id: String,
    todoId: String,
    done: Boolean,
    closedOn: LocalDate? = null,
    createdOn: LocalDate? = null,
) = PendingWrite.ToggleTodo(id, 0, todoId, done, closedOn, createdOn)

internal fun editWrite(id: String, todoId: String, label: String) = PendingWrite.EditTodo(id, 0, todoId, label)

internal fun removeWrite(id: String, todoId: String) = PendingWrite.RemoveTodo(id, 0, todoId)
