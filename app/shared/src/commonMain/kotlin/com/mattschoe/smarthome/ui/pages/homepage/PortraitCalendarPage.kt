package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mattschoe.smarthome.ui.controls.calendar.AddEventButton
import com.mattschoe.smarthome.ui.controls.calendar.CalendarPanel
import com.mattschoe.smarthome.ui.controls.calendar.CalendarSettingsButton
import com.mattschoe.smarthome.ui.controls.calendar.CalendarSettingsPopup
import com.mattschoe.smarthome.ui.controls.calendar.EventDetailPopup
import com.mattschoe.smarthome.ui.theme.Dimensions

/**
 * Portrait page 4 — Calendar. The tablet right card's Calendar panel given the whole screen, and week
 * view throughout: a phone is too narrow for the month grid's agenda-below-grid stack to be worth the
 * scroll, and the page pager already gives it a home the month/week toggle would only duplicate. The
 * page settling is what sets the view (see [CompactDashboard]), so the filter set the panel draws
 * with matches what is on screen.
 *
 * With no tab row to hang controls off, the header's trailing slot carries both: the gear that picks
 * the calendars, then the add button — the same order the tablet uses, minus the toggle between them.
 */
@Composable
fun PortraitCalendarPage(
    ready: HomeScreenState.Ready,
    viewModel: HomepageViewModel,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        CalendarPanel(
            eventEditor = ready.eventEditor,
            savingEvent = ready.savingEvent,
            today = ready.today,
            displayedMonth = ready.displayedMonth,
            selectedDay = ready.selectedDay,
            calendarView = ready.calendarView,
            eventsByDay = ready.eventsByDay,
            selectedDayTodos = ready.selectedDayTodos,
            weekDays = ready.weekDays,
            calendarWindow = ready.calendarWindow,
            nowMinutes = ready.nowMinutes,
            calendarSources = ready.calendar.sources,
            calendarStale = ready.calendar.stale,
            calendarHasTodoList = ready.calendar.hasTodoList,
            dayMarks = ready.dayMarks,
            weekHourHeight = ready.weekHourHeight,
            onShowMonth = viewModel::showMonth,
            onShowWeek = viewModel::showWeek,
            onSelectDay = viewModel::selectDay,
            onAddTodo = viewModel::addTodo,
            onToggleTodo = viewModel::toggleTodo,
            onEditTodo = viewModel::editTodo,
            onOpenEvent = viewModel::openEvent,
            onOpenEventDetail = viewModel::openEventDetail,
            onWeekHourHeight = viewModel::setWeekHourHeight,
            onSaveEvent = viewModel::saveEvent,
            onDeleteEvent = viewModel::deleteEvent,
            onCloseEventEditor = viewModel::closeEventEditor,
            // The "+" stands down while the editor is open, as on the tablet — re-opening a blank form
            // would discard what is typed.
            headerTrailing = {
                CalendarSettingsButton(viewModel::openCalendarSettings)
                if (ready.eventEditor == null) AddEventButton(viewModel::openNewEvent)
            },
            modifier = Modifier
                .padding(horizontal = Dimensions.phonePagePad)
                .padding(
                    top = Dimensions.phonePageTopPad,
                    bottom = Dimensions.phonePageBottomClearance,
                ),
        )

        // Floated as siblings of the panel for the same reason the right card floats them: the week
        // grid scrolls, and a popup remembered down there would scroll away from the control that
        // opened it. Their scrim covers the whole page here rather than a card — nothing beside them
        // stays live on a phone — but the cards themselves take the page's own margins, since there is
        // no card padding to sit inside. The settings card's built-in drop is measured from the header
        // row, so it clears the top pad the panel is inset by too.
        val popupInset = Modifier
            .padding(horizontal = Dimensions.phonePagePad)
            .padding(top = Dimensions.phonePageTopPad, bottom = Dimensions.phonePageBottomClearance)
        ready.eventDetail?.let { event ->
            EventDetailPopup(
                event = event,
                sources = ready.calendar.sources,
                onEdit = viewModel::editEventDetail,
                onDelete = viewModel::deleteEventDetail,
                onClose = viewModel::closeEventDetail,
                modifier = popupInset,
            )
        }
        if (ready.calendarSettingsOpen) {
            CalendarSettingsPopup(
                view = ready.calendarView,
                sources = ready.calendar.sources,
                filters = ready.calendarFilters,
                onToggle = viewModel::toggleCalendarFilter,
                onClose = viewModel::closeCalendarSettings,
                modifier = popupInset,
            )
        }
    }
}
