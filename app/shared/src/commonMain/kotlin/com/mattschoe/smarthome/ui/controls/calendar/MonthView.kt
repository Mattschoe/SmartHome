package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.calendarGrid
import com.mattschoe.smarthome.data.danishMonths
import com.mattschoe.smarthome.data.monthAtPage
import com.mattschoe.smarthome.data.monthIndexOf
import com.mattschoe.smarthome.data.monthPageCount
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.pages.homepage.DayMarks
import com.mattschoe.smarthome.ui.theme.CalendarDotColors
import com.mattschoe.smarthome.ui.theme.Card
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.haCalendarColor
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnForest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** Seven equal-width, muted weekday initials aligned to the [MonthGrid] columns below. */
@Composable
internal fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        danishWeekdayInitials.forEach { initial ->
            Text(
                text = initial,
                color = Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The month grid as a **pager over [calendarWindow]'s months** — the months slide with the finger
 * rather than jumping once a drag passes a threshold, which is also what lets the phone nest this
 * inside its page pager: the drag is consumed mid-window and handed on at the window's ends, where
 * there is nothing beyond to show anyway.
 *
 * [displayedMonth] stays the ViewModel's, not the pager's: settling scrolls it there, and a change
 * from anywhere else animates the pager to it. Both directions are guarded on a difference, which is
 * what keeps them from ping-ponging.
 *
 * The same navigation is exposed to screen readers as custom actions — a pager is not swipeable by
 * one, and there are no on-screen month buttons.
 */
@Composable
internal fun MonthPager(
    displayedMonth: LocalDate,
    today: LocalDate,
    selectedDay: LocalDate,
    dayMarks: Map<LocalDate, DayMarks>,
    sources: List<CalendarSource>,
    calendarWindow: ClosedRange<LocalDate>,
    onSelectDay: (LocalDate) -> Unit,
    onShowMonth: (LocalDate) -> Unit,
) {
    // Keyed on the window alone: re-creating the state on a month change would drop the very scroll
    // that produced it.
    val pagerState = rememberPagerState(
        initialPage = monthIndexOf(calendarWindow, displayedMonth),
        pageCount = { monthPageCount(calendarWindow) },
    )
    val currentShow by rememberUpdatedState(onShowMonth)
    val currentMonth by rememberUpdatedState(displayedMonthStart(displayedMonth))
    LaunchedEffect(pagerState, calendarWindow) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val month = monthAtPage(calendarWindow, page)
            if (month != currentMonth) currentShow(month)
        }
    }
    LaunchedEffect(displayedMonth, calendarWindow) {
        val page = monthIndexOf(calendarWindow, displayedMonth)
        if (page != pagerState.currentPage) pagerState.animateScrollToPage(page)
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.monthGridHeight)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Forrige måned") {
                        onShowMonth(monthAtPage(calendarWindow, pagerState.currentPage - 1)); true
                    },
                    CustomAccessibilityAction("Næste måned") {
                        onShowMonth(monthAtPage(calendarWindow, pagerState.currentPage + 1)); true
                    },
                )
            },
    ) { page ->
        MonthGrid(
            month = monthAtPage(calendarWindow, page),
            today = today,
            selectedDay = selectedDay,
            dayMarks = dayMarks,
            sources = sources,
            onSelectDay = onSelectDay,
        )
    }
}

/** [displayedMonth] is already pinned to the 1st by the ViewModel; this only makes that explicit. */
private fun displayedMonthStart(displayedMonth: LocalDate) =
    LocalDate(displayedMonth.year, displayedMonth.month.number, 1)

/**
 * One page: a 6×7 Monday-first grid of [month]. Today is the accent cell; the selected day (if not
 * today) is ringed — both only on the page whose month they fall in.
 */
@Composable
private fun MonthGrid(
    month: LocalDate,
    today: LocalDate,
    selectedDay: LocalDate,
    dayMarks: Map<LocalDate, DayMarks>,
    sources: List<CalendarSource>,
    onSelectDay: (LocalDate) -> Unit,
) {
    val cells = calendarGrid(month.year, month.month.number)
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.monthGridRowGap),
    ) {
        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = cells[row * 7 + col]
                    val date = day?.let { LocalDate(month.year, month.month.number, it) }
                    DayCell(
                        day = day,
                        isToday = date == today,
                        isSelected = date == selectedDay,
                        marks = date?.let { dayMarks[it] },
                        sources = sources,
                        onClick = { if (date != null) onSelectDay(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * One month-grid cell. The whole cell is the touch target; a 34dp disc carries the number (filled
 * Forest for today, ringed for the selected day) with the day's [DayMarkDots] beneath it.
 */
@Composable
private fun DayCell(
    day: Int?,
    isToday: Boolean,
    isSelected: Boolean,
    marks: DayMarks?,
    sources: List<CalendarSource>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(Dimensions.minTouch)
            .then(if (day != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { if (day != null) contentDescription = "Dag $day" },
        contentAlignment = Alignment.Center,
    ) {
        if (day == null) return@Box
        val disc = when {
            isToday -> Modifier.background(Forest, CircleShape)
            isSelected -> Modifier.border(1.5.dp, Forest, CircleShape)
            else -> Modifier
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(Dimensions.calendarDayDisc).clip(CircleShape).then(disc),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.toString(),
                    color = if (isToday) OnForest else Ink,
                    fontSize = 15.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Spacer(Modifier.height(3.dp))
            DayMarkDots(marks, sources)
        }
    }
}

/** How many dots one month-grid cell shows before the rest are dropped. */
private const val MaxDayMarks = 4

/**
 * The dots under a day's number: **one per calendar** that has an event that day, in the day's
 * source order and each in that calendar's own color, so a day reads as "one of his, two of hers"
 * at a glance. They overlap slightly to stay inside the cell. Todos add a muted dot last.
 *
 * Drawn rather than laid out as bordered boxes: a dot is only [Dimensions.dayMarkDot] across, and a
 * hairline outline that size lands under a pixel — the rasterizer spends the circle's antialiased
 * edge on the stroke and the dot squares off. So the separation between overlapping dots is a
 * *filled* card-colored halo drawn under each one, which stays round at any density.
 *
 * Capped at [MaxDayMarks] — beyond that a cell reads as a smear rather than as "a few things" — and
 * an empty day still occupies the row so the grid doesn't shift.
 */
@Composable
private fun DayMarkDots(marks: DayMarks?, sources: List<CalendarSource>) {
    val colors = buildList {
        marks?.sourceIds?.forEach { add(calendarDotColor(it, sources)) }
        if (marks?.hasTodo == true) add(Muted)
    }.take(MaxDayMarks)
    val diameter = Dimensions.dayMarkDot
    val step = diameter - Dimensions.dayMarkOverlap
    // Each dot after the first adds only [step]; the first needs its full diameter.
    val width = diameter + step * (colors.size - 1).coerceAtLeast(0)
    Canvas(Modifier.size(width = width, height = diameter)) {
        val radius = diameter.toPx() / 2f
        val halo = radius + Dimensions.dayMarkRing.toPx()
        colors.forEachIndexed { index, color ->
            val center = Offset(radius + step.toPx() * index, radius)
            // Under the dot, so it clears the neighbour it laps over without dulling its own edge.
            if (index > 0) drawCircle(Card, radius = halo, center = center)
            drawCircle(color, radius = radius, center = center)
        }
    }
}

/**
 * "I DAG" (or the selected date) label over the day's event rows. A row opens that event in the
 * editor — including one on a read-only calendar, which opens as details that can't be changed.
 */
@Composable
internal fun AgendaSection(
    selectedDay: LocalDate,
    today: LocalDate,
    events: List<CalendarEvent>,
    sources: List<CalendarSource>,
    onOpenEvent: (CalendarEvent) -> Unit,
) {
    val label =
        if (selectedDay == today) "I dag"
        else "${selectedDay.day}. ${danishMonths[selectedDay.month.number - 1]}"
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(label, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        if (events.isEmpty()) {
            Text("Ingen begivenheder", color = Muted, fontSize = 15.sp)
        } else {
            events.forEachIndexed { index, event ->
                AgendaRow(event, calendarDotColor(event.sourceId, sources)) { onOpenEvent(event) }
                if (index != events.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * The dot color for an event: its **calendar's**, not its row's — so a day's events read as "one of
 * his, two of hers" rather than as an arbitrary rotation. The calendar's own Home Assistant color
 * wins where it has one (see [haCalendarColor]); otherwise the color falls out of its position in
 * [sources], which is stable across sessions. An event from an unknown calendar (a stale cache read
 * before sources are known) falls back to the first color.
 */
internal fun calendarDotColor(sourceId: String, sources: List<CalendarSource>): Color {
    val index = sources.indexOfFirst { it.id == sourceId }
    haCalendarColor(sources.getOrNull(index)?.color)?.let { return it }
    return CalendarDotColors[index.coerceAtLeast(0) % CalendarDotColors.size]
}

@Composable
private fun AgendaRow(event: CalendarEvent, dotColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The row keeps its own compact height; the target is padded out to a comfortable one
            // rather than the rows being spread apart to reach [Dimensions.minTouch].
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .semantics { contentDescription = "Åbn ${event.title}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        // Wide enough for the longest label an all-day event produces ("Hele dagen").
        Text(event.time, color = InkSoft, fontSize = 15.sp, modifier = Modifier.width(88.dp))
        Text(
            text = event.title,
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
