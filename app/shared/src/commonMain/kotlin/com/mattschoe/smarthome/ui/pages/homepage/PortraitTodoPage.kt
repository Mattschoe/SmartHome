package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.formatDayAndMonth
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.ui.components.OfflineLabel
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.controls.calendar.TodoPanel
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Ink
import kotlinx.datetime.LocalDate

/**
 * Portrait page 5 — Opgaver. The tablet right card's Opgaver panel given the whole screen. It sits
 * after Kalender because it used to live inside it; the page pager is what selects the panel here,
 * there being no tab row to do it (see [CompactDashboard]).
 *
 * The panel carries no header of its own — on the tablet the tab row names it and its trailing edge
 * prints the day — so the page prints both: the other phone pages' label, and the day the checklist
 * is paged to beside it, without which its own day swipe would be unlabelled. That header is also
 * where the checklist says it is offline, since it is the only line the surface has.
 *
 * Takes the narrow slices it reads rather than the whole [HomeScreenState.Ready] — the phone pagers
 * destructure, so a page whose slices didn't change skips recomposition entirely.
 */
@Composable
fun PortraitTodoPage(
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimensions.phonePagePad)
            // Stops short of the dot row by the clearance *plus* the dead zone
            // ([Dimensions.phoneTodoDeadZone]): the checklist pages by day, so without a strip of bare
            // page below it a horizontal drag has nowhere to reach the page pager from. The rows scroll
            // under it rather than shrinking — the list is lazy and fades at whichever edge still has
            // rows beyond it.
            .padding(
                top = Dimensions.phonePageTopPad,
                bottom = Dimensions.phonePageBottomClearance + Dimensions.phoneTodoDeadZone,
            ),
    ) {
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

/**
 * The phone pages' Opgaver header: the section label, the offline word where the home is out of
 * reach, and the paged-to day at the trailing edge.
 */
@Composable
internal fun TodoPageHeader(todoDay: LocalDate, offline: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("Opgaver")
        Spacer(Modifier.weight(1f))
        if (offline) OfflineLabel()
        Text(
            text = formatDayAndMonth(todoDay),
            color = Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
