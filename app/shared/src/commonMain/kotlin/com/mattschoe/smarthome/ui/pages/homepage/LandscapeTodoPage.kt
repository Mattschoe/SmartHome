package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.controls.calendar.TodoPanel
import com.mattschoe.smarthome.ui.theme.Dimensions
import kotlinx.datetime.LocalDate

/**
 * Landscape page 4 — Opgaver. **Deliberately one card**, where every other landscape page is a pair:
 * the checklist is a single column of short rows, so splitting it across two cards would either
 * duplicate the list or strand a half-empty one beside it on a quiet day. The width goes to the rows
 * instead. Don't "fix" this back into a pair.
 *
 * Takes the narrow slices it reads rather than the whole [HomeScreenState.Ready] — the phone pagers
 * destructure, so a page whose slices didn't change skips recomposition entirely.
 */
@Composable
fun LandscapeTodoPage(
    todos: List<TodoItem>,
    todoDay: LocalDate,
    today: LocalDate,
    calendarWindow: ClosedRange<LocalDate>,
    hasTodoList: Boolean,
    /** Whether the home is out of reach — what makes a tick or a new task wait in the outbox. */
    offline: Boolean,
    viewModel: HomepageViewModel,
    modifier: Modifier = Modifier,
) {
    CardContainer(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimensions.phoneCardPadding),
    ) {
        Column(Modifier.fillMaxSize()) {
            TodoPageHeader(todoDay, offline)
            Spacer(Modifier.height(8.dp))
            TodoPanel(
                todos = todos,
                day = todoDay,
                today = today,
                calendarWindow = calendarWindow,
                hasTodoList = hasTodoList,
                onShowDay = viewModel::showTodoDay,
                onAddTodo = viewModel::addTodo,
                onToggleTodo = viewModel::toggleTodo,
                onEditTodo = viewModel::editTodo,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
