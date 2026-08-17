package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.TodoGroup
import com.mattschoe.smarthome.data.dayAtPage
import com.mattschoe.smarthome.data.dayIndexOf
import com.mattschoe.smarthome.data.dayPageCount
import com.mattschoe.smarthome.data.formatTodoDue
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.data.todoPage
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.components.verticalScrollFade
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Muted
import kotlinx.datetime.LocalDate

/**
 * The Opgaver panel: the checklist as its own surface beside Media and the Calendar, paged by day.
 *
 * **A page carries its day and everything unfinished before it** (see
 * [com.mattschoe.smarthome.data.todoPage]), so an unticked Tuesday is still standing on Wednesday's
 * page rather than needing to be swiped back to. Rows stack under a date header per day, nearest day
 * first, with what has been ticked off below an UDFØRT rule.
 *
 * Adding is bound to the day being shown, not to today: swiping to tomorrow and typing there is how
 * this surface picks a due date, which is why it needs no date control of its own.
 *
 * A home whose Home Assistant has no due-date-capable todo list ([hasTodoList]) gets a note instead
 * of any rows: every todo intent is inert without one, so an add row would silently swallow whatever
 * is typed into it.
 */
@Composable
fun TodoPanel(
    /** The whole checklist. Each page slices it — the pager composes its neighbours. */
    todos: List<TodoItem>,
    /** The day being shown (VM-owned). */
    day: LocalDate,
    /** The real current day — what the date headers are phrased against. */
    today: LocalDate,
    /** The span the calendar holds data for; the swipe is bounded to it, as both grids are. */
    calendarWindow: ClosedRange<LocalDate>,
    hasTodoList: Boolean,
    onShowDay: (LocalDate) -> Unit,
    onAddTodo: (LocalDate, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onEditTodo: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!hasTodoList) {
        Text(
            text = "Ingen opgaveliste i Home Assistant",
            color = Muted,
            fontSize = 15.sp,
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        return
    }
    Column(modifier.fillMaxWidth()) {
        // Above the pager, not inside it: it stays put while the days slide under it, and there is
        // one inline editor rather than one per composed page.
        AddTodoRow(onAdd = { text -> onAddTodo(day, text) })
        Spacer(Modifier.height(8.dp))
        TodoDayPager(
            todos = todos,
            day = day,
            today = today,
            calendarWindow = calendarWindow,
            onShowDay = onShowDay,
            onToggleTodo = onToggleTodo,
            onEditTodo = onEditTodo,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

/**
 * The checklist as a **pager over [calendarWindow]'s days**, built like the calendar's month and week
 * pagers: the days slide with the finger rather than jumping past a threshold, which is also what
 * lets the phone nest this inside its page pager — the drag is consumed mid-window and handed on at
 * the window's ends, where there is nothing beyond to show anyway.
 *
 * [day] stays the ViewModel's, not the pager's: settling reports the new day up, and a change from
 * anywhere else (entering the panel resets it to today) animates the pager to it. Both directions are
 * guarded on a difference, which is what keeps them from ping-ponging.
 *
 * The same navigation is exposed to screen readers as custom actions — a pager is not swipeable by
 * one, and there are no on-screen day buttons.
 */
@Composable
private fun TodoDayPager(
    todos: List<TodoItem>,
    day: LocalDate,
    today: LocalDate,
    calendarWindow: ClosedRange<LocalDate>,
    onShowDay: (LocalDate) -> Unit,
    onToggleTodo: (String) -> Unit,
    onEditTodo: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the window alone: re-creating the state on a day change would drop the very scroll
    // that produced it.
    val pagerState = rememberPagerState(
        initialPage = dayIndexOf(calendarWindow, day),
        pageCount = { dayPageCount(calendarWindow) },
    )
    val currentShow by rememberUpdatedState(onShowDay)
    val currentDay by rememberUpdatedState(day)
    LaunchedEffect(pagerState, calendarWindow) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val settled = dayAtPage(calendarWindow, page)
            if (settled != currentDay) currentShow(settled)
        }
    }
    LaunchedEffect(day, calendarWindow) {
        val page = dayIndexOf(calendarWindow, day)
        if (page != pagerState.currentPage) pagerState.animateScrollToPage(page)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction("Forrige dag") {
                    onShowDay(dayAtPage(calendarWindow, pagerState.currentPage - 1)); true
                },
                CustomAccessibilityAction("Næste dag") {
                    onShowDay(dayAtPage(calendarWindow, pagerState.currentPage + 1)); true
                },
            )
        },
    ) { page ->
        val pageDay = dayAtPage(calendarWindow, page)
        val content = remember(todos, pageDay) { todoPage(todos, pageDay) }
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollFade(scroll)
                .verticalScroll(scroll),
        ) {
            content.open.forEach { group -> TodoDateGroup(group, today, onToggleTodo, onEditTodo) }
            if (content.open.isEmpty()) {
                Text(
                    text = "Ingen opgaver",
                    color = Muted,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (content.done.isNotEmpty()) {
                DoneSeparator()
                content.done.forEach { group -> TodoDateGroup(group, today, onToggleTodo, onEditTodo) }
            }
        }
    }
}

/**
 * One day's rows under its date header. Rows are keyed by stable id so a backend echo re-keys them
 * instead of rebuilding, and so a row moving between the two sections keeps its inline editor.
 */
@Composable
private fun TodoDateGroup(
    group: TodoGroup,
    today: LocalDate,
    onToggleTodo: (String) -> Unit,
    onEditTodo: (String, String) -> Unit,
) {
    SectionLabel(formatTodoDue(group.due, today))
    Spacer(Modifier.height(4.dp))
    group.items.forEach { todo ->
        key(todo.id) {
            TodoRow(
                todo = todo,
                onToggle = { onToggleTodo(todo.id) },
                onCommitEdit = { text -> onEditTodo(todo.id, text) },
            )
        }
    }
    Spacer(Modifier.height(Dimensions.mediaSectionGap))
}

/**
 * The rule the ticked-off rows sit under: a hairline either side of a centred UDFØRT label. A rule
 * rather than a plain header because what it marks is the end of the list that still wants doing —
 * everything below it is history, and should read as a different half of the page.
 */
@Composable
private fun DoneSeparator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = Dimensions.todoDividerHeight,
            color = CardBorder,
        )
        SectionLabel("Udført")
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = Dimensions.todoDividerHeight,
            color = CardBorder,
        )
    }
}
