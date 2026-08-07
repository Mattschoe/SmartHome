package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.HoursPerDay
import com.mattschoe.smarthome.data.danishMonths
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.CalendarView
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.ui.components.verticalScrollFade
import com.mattschoe.smarthome.ui.pages.homepage.DayMarks
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InsetFill
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnForest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.add_filled
import smarthome.shared.generated.resources.calendar_view_week
import smarthome.shared.generated.resources.calender_filled
import smarthome.shared.generated.resources.settings_filled

/** Danish, Monday-first weekday initials for both grids' headers (Man, Tir, Ons, Tor, Fre, Lør, Søn). */
internal val danishWeekdayInitials = listOf("M", "T", "O", "T", "F", "L", "S")

/** The hairline between the week grid and the checklist under it. */
private val StripDividerHeight = 1.dp

/**
 * The calendar in either of its two views, and the owner of the scrolling.
 *
 * *Month* — a Monday-first month grid that pages by month, then the selected day's read-only agenda
 * and its editable todo checklist, all in one scroll.
 *
 * *Week* — the selected day's Monday-to-Sunday week as a time grid that pages by week, which
 * **replaces** both the month grid and the agenda: the header is fixed, the hour grid takes the
 * height the day it is showing needs (bounded, so it scrolls when the hours are expanded), and the
 * todo checklist takes what is left — which is how pinching the hours together grows the checklist.
 *
 * Either way selecting a day scopes the todos (and, in month view, the agenda); [todos] arrive
 * pre-filtered to [selectedDay], while [eventsByDay] spans the whole window and each page slices it.
 */
@Composable
internal fun CalendarViews(
    today: LocalDate,
    displayedMonth: LocalDate,
    selectedDay: LocalDate,
    view: CalendarView,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    weekDays: List<LocalDate>,
    calendarWindow: ClosedRange<LocalDate>,
    nowMinutes: Int,
    todos: List<TodoItem>,
    sources: List<CalendarSource>,
    stale: Boolean,
    hasTodoList: Boolean,
    dayMarks: Map<LocalDate, DayMarks>,
    /** The week grid's hour-row height in dp — the reader's pinch level. */
    weekHourHeight: Float,
    onShowMonth: (LocalDate) -> Unit,
    onShowWeek: (LocalDate) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onAddTodo: (LocalDate, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onEditTodo: (String, String) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onOpenEventDetail: (CalendarEvent) -> Unit,
    onWeekHourHeight: (Float) -> Unit,
    headerTrailing: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    when (view) {
        CalendarView.Month -> {
            val calendarScroll = rememberScrollState()
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .verticalScrollFade(calendarScroll)
                    .verticalScroll(calendarScroll),
            ) {
                CalendarHeader(displayedMonth, weekDays, view, stale, headerTrailing)
                Spacer(Modifier.height(16.dp))
                WeekdayHeader()
                Spacer(Modifier.height(4.dp))
                MonthPager(
                    displayedMonth = displayedMonth,
                    today = today,
                    selectedDay = selectedDay,
                    dayMarks = dayMarks,
                    sources = sources,
                    calendarWindow = calendarWindow,
                    onSelectDay = onSelectDay,
                    onShowMonth = onShowMonth,
                )
                AgendaSection(selectedDay, today, eventsByDay[selectedDay].orEmpty(), sources, onOpenEvent)
                Spacer(Modifier.height(Dimensions.mediaSectionGap))
                TodoSection(selectedDay, todos, hasTodoList, onAddTodo, onToggleTodo, onEditTodo)
            }
        }
        CalendarView.Week -> Column(modifier.fillMaxWidth()) {
            CalendarHeader(displayedMonth, weekDays, view, stale, headerTrailing)
            Spacer(Modifier.height(12.dp))
            // The grid asks for exactly the day it holds and the checklist takes whatever is left, so
            // pinching the hours together hands every dp the day gives up straight down to OPGAVER.
            // Which is still a *fixed* split for any one zoom level — the strip's height doesn't
            // follow its own rows, so the hours don't jump as the day's checklist grows and shrinks,
            // and a long list scrolls in the strip rather than pushing the hours off the bottom.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // What the pager keeps above its scrolling hours — the day header and the all-day
                // strip. Reported by [WeekPager] rather than assumed, since the strip's height is the
                // shown week's, not a constant.
                var chrome by remember { mutableStateOf(0.dp) }
                val ceiling = (maxHeight - Dimensions.weekTodoStripHeight - StripDividerHeight)
                    .coerceAtLeast(Dimensions.weekMinGridHeight)
                val hourHeight = weekHourHeight.dp
                val gridHeight = (chrome + hourHeight * HoursPerDay)
                    .coerceIn(Dimensions.weekMinGridHeight, ceiling)
                Column(Modifier.fillMaxSize()) {
                    WeekPager(
                        weekDays = weekDays,
                        eventsByDay = eventsByDay,
                        today = today,
                        selectedDay = selectedDay,
                        calendarWindow = calendarWindow,
                        nowMinutes = nowMinutes,
                        sources = sources,
                        hourHeight = hourHeight,
                        onSelectDay = onSelectDay,
                        onShowWeek = onShowWeek,
                        onOpenEvent = onOpenEventDetail,
                        onHourHeight = onWeekHourHeight,
                        onChrome = { chrome = it },
                        modifier = Modifier.height(gridHeight),
                    )
                    Box(Modifier.fillMaxWidth().height(StripDividerHeight).background(CardBorder))
                    val todoScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(todoScroll)
                            .padding(top = 8.dp),
                    ) {
                        TodoSection(selectedDay, todos, hasTodoList, onAddTodo, onToggleTodo, onEditTodo)
                    }
                }
            }
        }
    }
}

/**
 * The calendar's title row: Danish month + year in month view ("Juli 2026"), the week's date range in
 * week view, and whatever the host hangs at the trailing edge ([trailing] — the tablet's month/week
 * toggle and gear, the phone's gear and add button). Month/week changes themselves come from paging
 * the grid below. When the calendar is being rendered from the offline cache it says so beside the
 * title, so nobody plans a day around a list that stopped updating.
 */
@Composable
private fun CalendarHeader(
    displayedMonth: LocalDate,
    weekDays: List<LocalDate>,
    view: CalendarView,
    stale: Boolean,
    trailing: @Composable RowScope.() -> Unit,
) {
    val monthName = danishMonths[displayedMonth.month.number - 1].replaceFirstChar { it.uppercase() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (view) {
                CalendarView.Month -> "$monthName ${displayedMonth.year}"
                CalendarView.Week -> weekRangeLabel(weekDays)
            },
            color = Ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        if (stale) {
            Text(text = "Offline", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        trailing()
    }
}

/**
 * The week's span as one line: "27. juli – 2. august", collapsing to "27. – 31. juli" when the whole
 * week sits in a single month.
 */
private fun weekRangeLabel(days: List<LocalDate>): String {
    val first = days.firstOrNull() ?: return ""
    val last = days.last()
    val firstMonth = danishMonths[first.month.number - 1]
    val lastMonth = danishMonths[last.month.number - 1]
    return if (first.month == last.month) "${first.day}. – ${last.day}. $lastMonth"
    else "${first.day}. $firstMonth – ${last.day}. $lastMonth"
}

/**
 * Icon-only segmented control swapping the calendar between month and week, built like the right
 * card's panel tabs one size down: a sunken [InsetFill] track whose active segment is a filled Forest
 * pill. A header-trailing control, and tablet-only — the phone's Calendar page is week throughout.
 */
@Composable
fun CalendarViewToggle(
    view: CalendarView,
    onSelectView: (CalendarView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier.clip(shape).background(InsetFill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CalendarViewSegment(
            icon = Res.drawable.calender_filled,
            label = "Månedsvisning",
            selected = view == CalendarView.Month,
            onClick = { onSelectView(CalendarView.Month) },
        )
        CalendarViewSegment(
            icon = Res.drawable.calendar_view_week,
            label = "Ugevisning",
            selected = view == CalendarView.Week,
            onClick = { onSelectView(CalendarView.Week) },
        )
    }
}

@Composable
private fun CalendarViewSegment(
    icon: DrawableResource,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(Dimensions.minTouch)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(Forest, CircleShape) else Modifier)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (selected) OnForest else Ink,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The gear that picks which calendars the view being shown draws. A header-trailing control, placed
 * *after* the view toggle where there is one, since what it filters is whichever view that toggle
 * has landed on.
 */
@Composable
fun CalendarSettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dimensions.minTouch)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Vælg kalendere" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.settings_filled),
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The add-event button: a Forest disc inside a full touch target, built like the Media panel's source
 * badge. It opens the editor as a surface *inside* the calendar, so whatever is beside it stays live.
 * The tablet hangs it off the right card's tab row; the phone page, which has none, hangs it off the
 * calendar header instead.
 */
@Composable
fun AddEventButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dimensions.minTouch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Nyt arrangement" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Dimensions.sourceBadgeSize)
                .shadow(Dimensions.pillElevation, CircleShape)
                .clip(CircleShape)
                .background(Forest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.add_filled),
                contentDescription = null,
                tint = OnForest,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
