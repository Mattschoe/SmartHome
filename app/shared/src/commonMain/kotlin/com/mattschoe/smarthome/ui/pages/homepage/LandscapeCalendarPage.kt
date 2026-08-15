package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mattschoe.smarthome.data.model.CalendarView
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.controls.calendar.AddEventButton
import com.mattschoe.smarthome.ui.controls.calendar.CalendarPanel
import com.mattschoe.smarthome.ui.controls.calendar.CalendarSettingsButton
import com.mattschoe.smarthome.ui.controls.calendar.CalendarSettingsPopup
import com.mattschoe.smarthome.ui.controls.calendar.EventDetailPopup
import com.mattschoe.smarthome.ui.theme.Dimensions

/**
 * Landscape page 3 — Utility. The reserved Apps slot beside the calendar, which works exactly like
 * the portrait Calendar page: [CalendarPanel] in **week view only** — the month grid and agenda are
 * not part of the phone's calendar at all, the header's view toggle never appears here, and the view
 * is forced at the panel so even a stray state can't render the month view. The gear + add button
 * sit in the header's trailing slot, and the event-detail / settings popups are floated as siblings
 * of the card Row so their scrims cover the whole page.
 *
 * The left card is the same reserved slot as the portrait Apps page and the tablet's `AppsCard`:
 * a section label over blank space, since there is no app model, tile composable or icon set
 * anywhere in the tree — apps land on all three surfaces in one later pass.
 */
@Composable
fun LandscapeCalendarPage(
    ready: HomeScreenState.Ready,
    viewModel: HomepageViewModel,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The cards are equal and fill the page's padded area — what the popups' offsets are measured
        // off, so the detail card centres over the right (calendar) card rather than the page.
        val cardWidth = (maxWidth - Dimensions.phoneCardGap) / 2
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.phoneCardGap),
        ) {
            CardContainer(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(Dimensions.phoneCardPadding),
            ) {
                Column(Modifier.fillMaxSize()) {
                    SectionLabel("Apps")
                    Spacer(Modifier.weight(1f))
                }
            }
            CardContainer(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(Dimensions.phoneCardPadding),
            ) {
                // The month/week pagers nest safely under the page's vertical pager — the axes don't
                // collide at all, which is simpler than the portrait case.
                CalendarPanel(
                    eventEditor = ready.eventEditor,
                    savingEvent = ready.savingEvent,
                    today = ready.today,
                    displayedMonth = ready.displayedMonth,
                    selectedDay = ready.selectedDay,
                    // Week view, always — the phone's calendar is week-only. The settle effect keeps
                    // the *state* in week too, so the event filtering (`visibleEvents`) matches the
                    // week grid that is drawn here; forcing it at the panel is the belt-and-braces
                    // that makes the month grid unreachable on this page no matter what the state is.
                    calendarView = CalendarView.Week,
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
                    // The "+" stands down while the editor is open, as on the tablet — re-opening a
                    // blank form would discard what is typed.
                    headerTrailing = {
                        CalendarSettingsButton(viewModel::openCalendarSettings)
                        if (ready.eventEditor == null) AddEventButton(viewModel::openNewEvent)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Floated as siblings of the cards, not inside the calendar card: the week grid scrolls, and
        // a popup remembered down there would scroll away from the control that opened it. The
        // detail card centres over the right card — the offset moves the page's centre across the
        // left card and the gap, where the calendar card's centre sits.
        ready.eventDetail?.let { event ->
            EventDetailPopup(
                event = event,
                sources = ready.calendar.sources,
                onEdit = viewModel::editEventDetail,
                onDelete = viewModel::deleteEventDetail,
                onClose = viewModel::closeEventDetail,
                modifier = Modifier.offset(x = (cardWidth + Dimensions.phoneCardGap) / 2),
            )
        }
        if (ready.calendarSettingsOpen) {
            CalendarSettingsPopup(
                view = ready.calendarView,
                sources = ready.calendar.sources,
                filters = ready.calendarFilters,
                onToggle = viewModel::toggleCalendarFilter,
                onClose = viewModel::closeCalendarSettings,
                // Its own drop is measured from the header row, so only the card's content inset is
                // added here — and the x-offset lands its right edge on the gear, which sits inside
                // the card's content padding rather than at the page's edge.
                modifier = Modifier.offset(
                    x = -Dimensions.phoneCardPadding,
                    y = Dimensions.phoneCardPadding,
                ),
            )
        }
    }
}
