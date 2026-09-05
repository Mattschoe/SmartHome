package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.DaySpan
import com.mattschoe.smarthome.data.DaysPerWeek
import com.mattschoe.smarthome.data.daySpanLanes
import com.mattschoe.smarthome.data.EventMove
import com.mattschoe.smarthome.data.HoursPerDay
import com.mattschoe.smarthome.data.MinutesPerDay
import com.mattschoe.smarthome.data.PositionedEvent
import com.mattschoe.smarthome.data.WeekHourHeightRange
import com.mattschoe.smarthome.data.canDragEvent
import com.mattschoe.smarthome.data.danishMonths
import com.mattschoe.smarthome.data.droppedEventSlot
import com.mattschoe.smarthome.data.formatMinuteOfDay
import com.mattschoe.smarthome.data.formatTimeOfDay
import com.mattschoe.smarthome.data.isoWeekNumber
import com.mattschoe.smarthome.data.MinEventSpanMinutes
import com.mattschoe.smarthome.data.layoutDayEvents
import com.mattschoe.smarthome.data.weekAtPage
import com.mattschoe.smarthome.data.weekIndexOf
import com.mattschoe.smarthome.data.weekPageCount
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.ui.theme.Card
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnForest
import com.mattschoe.smarthome.ui.theme.onCalendarColor
import com.mattschoe.smarthome.ui.theme.Rose
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.drop_down_filled
import smarthome.shared.generated.resources.drop_up_filled

/**
 * The week as a **pager over [calendarWindow]'s weeks**, the month grid's sibling: one page is one
 * week, and everything week-scoped — the day header, the all-day strip, the hour grid — slides
 * together, while the chrome the calendar hangs above and below it does not.
 *
 * The week shown stays the ViewModel's (it is the week [weekDays] falls in, which is the selected
 * day's), so settling reports the new week up and a change from anywhere else animates the pager to
 * it; both guarded on a difference so they don't ping-pong.
 *
 * **The hour scroll is hoisted above the pages**, so swiping weeks keeps the same hours on screen —
 * every page's grid is exactly 24 [hourHeight] rows tall, so one state fits them all. It is aimed at
 * the opening week's first event once, on entering the surface, not per page.
 *
 * [hourHeight] is the pinch level (see [WeekGrid]); [onChrome] reports what the page keeps *above*
 * its scrolling hours — the day header plus the all-day strip, whose height is the shown week's, not
 * a constant — so the host can size the grid to exactly the day it now holds.
 */
@Composable
internal fun WeekPager(
    weekDays: List<LocalDate>,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    today: LocalDate,
    selectedDay: LocalDate,
    calendarWindow: ClosedRange<LocalDate>,
    nowMinutes: Int,
    sources: List<CalendarSource>,
    hourHeight: Dp,
    onSelectDay: (LocalDate) -> Unit,
    onShowWeek: (LocalDate) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onNewEventAt: (LocalDate, LocalTime) -> Unit,
    /** A block was long-pressed and dropped on a new slot of the same week. */
    onMoveEvent: (EventMove) -> Unit,
    /** Whether blocks may be picked up at all — false while a write is already in flight. */
    dragEnabled: Boolean,
    onHourHeight: (Float) -> Unit,
    onChrome: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekStart = weekDays.first()
    // Keyed on the window alone, for the same reason the month pager is.
    val pagerState = rememberPagerState(
        initialPage = weekIndexOf(calendarWindow, weekStart),
        pageCount = { weekPageCount(calendarWindow) },
    )
    val currentShow by rememberUpdatedState(onShowWeek)
    val currentWeek by rememberUpdatedState(weekStart)
    LaunchedEffect(pagerState, calendarWindow) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val start = weekAtPage(calendarWindow, page)
            if (start != currentWeek) currentShow(start)
        }
    }
    LaunchedEffect(weekStart, calendarWindow) {
        val page = weekIndexOf(calendarWindow, weekStart)
        if (page != pagerState.currentPage) pagerState.animateScrollToPage(page)
    }

    val hourScroll = rememberScrollState()
    val density = LocalDensity.current
    // The week the surface *opened* on, so a late-arriving event still aims the scroll at it while a
    // swipe to another week never yanks the hours the reader is looking at.
    val openingWeek = remember { weekDays }
    val openingPage = remember { pagerState.currentPage }
    val openMinute = weekOpenMinute(openingWeek, eventsByDay)
    val currentHourHeight by rememberUpdatedState(hourHeight)
    LaunchedEffect(openMinute) {
        if (pagerState.currentPage != openingPage) return@LaunchedEffect
        // The scroll range is only known once the grid has been laid out; scrolling before that
        // clamps to 0 and the week silently opens at midnight.
        snapshotFlow { hourScroll.maxValue }.first { it > 0 }
        hourScroll.scrollTo(with(density) { minuteOffset(openMinute, currentHourHeight).roundToPx() })
    }

    // What each page holds above its hours. Keyed by page rather than letting whichever page composed
    // last win: the pager composes its neighbours, and a week with a busier all-day strip than the one
    // on screen would otherwise flicker the grid's height as it slides past.
    val chromeByPage = remember { mutableStateMapOf<Int, Dp>() }
    val chrome = chromeByPage[pagerState.currentPage] ?: 0.dp
    val currentOnChrome by rememberUpdatedState(onChrome)
    LaunchedEffect(chrome) { currentOnChrome(chrome) }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            // A pager is not swipeable by a screen reader, and there are no on-screen week buttons —
            // nor is a pinch reachable at all, so the zoom's own steps are offered here beside them.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Forrige uge") {
                        onShowWeek(weekAtPage(calendarWindow, pagerState.currentPage - 1)); true
                    },
                    CustomAccessibilityAction("Næste uge") {
                        onShowWeek(weekAtPage(calendarWindow, pagerState.currentPage + 1)); true
                    },
                    CustomAccessibilityAction("Komprimér timer") {
                        onHourHeight(steppedHourHeight(hourHeight.value, expand = false)); true
                    },
                    CustomAccessibilityAction("Udvid timer") {
                        onHourHeight(steppedHourHeight(hourHeight.value, expand = true)); true
                    },
                )
            },
    ) { page ->
        val days = weekDaysAt(calendarWindow, page)
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.onSizeChanged { chromeByPage[page] = with(density) { it.height.toDp() } },
            ) {
                WeekHeader(days, today, selectedDay, onSelectDay)
                AllDayStrip(days, eventsByDay, sources, onOpenEvent)
            }
            WeekGrid(
                days = days,
                eventsByDay = eventsByDay,
                today = today,
                nowMinutes = nowMinutes,
                sources = sources,
                scroll = hourScroll,
                hourHeight = hourHeight,
                onHourHeight = onHourHeight,
                onOpenEvent = onOpenEvent,
                onNewEventAt = onNewEventAt,
                onMoveEvent = onMoveEvent,
                // Only the page on screen may be dragged: the pager composes its neighbours, and a
                // drag started on one of those would resolve against a week nobody is looking at.
                dragEnabled = dragEnabled && page == pagerState.currentPage,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The seven columns, Monday first, of the week on [page]. */
private fun weekDaysAt(window: ClosedRange<LocalDate>, page: Int): List<LocalDate> =
    weekAtPage(window, page).let { start -> List(DaysPerWeek) { start.plus(it, DateTimeUnit.DAY) } }

/**
 * The minute the hour grid opens on: an hour above [days]' earliest timed event, or
 * [WeekDefaultOpenMinute] on a week with nothing timed at all. All-day entries (null minutes) live in
 * the strip above the grid, so they count as "no event" here.
 */
private fun weekOpenMinute(days: List<LocalDate>, eventsByDay: Map<LocalDate, List<CalendarEvent>>): Int {
    val first = days.minOf { date ->
        eventsByDay[date].orEmpty().minOfOrNull { it.startMinute ?: MinutesPerDay } ?: MinutesPerDay
    }
    return if (first >= MinutesPerDay) WeekDefaultOpenMinute
    else (first - WeekOpenLeadMinutes).coerceAtLeast(0)
}

/**
 * The week view's day header: seven columns (offset by the grid's hour gutter) of weekday initial +
 * day number, styled like the month grid's cells — a filled Forest disc for today, a ring for the
 * selected day. Tapping a column selects that day, which re-scopes the checklist under the grid.
 */
@Composable
private fun WeekHeader(
    days: List<LocalDate>,
    today: LocalDate,
    selectedDay: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        WeekNumberCell(days.first())
        days.forEach { date ->
            val isToday = date == today
            val isSelected = date == selectedDay
            val disc = when {
                isToday -> Modifier.background(Forest, CircleShape)
                isSelected -> Modifier.border(1.5.dp, Forest, CircleShape)
                else -> Modifier
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectDay(date) }
                    .semantics {
                        contentDescription = "${date.day}. ${danishMonths[date.month.number - 1]}"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = danishWeekdayInitials[date.dayOfWeek.isoDayNumber - 1],
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier.size(Dimensions.calendarDayDisc).clip(CircleShape).then(disc),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = date.day.toString(),
                        color = if (isToday) OnForest else Ink,
                        fontSize = 15.sp,
                        fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * The gutter's corner above the hours: the ISO week the shown page covers, bare of any label and set
 * on the day numbers' own line, so it reads across the header as one row of numbers rather than as a
 * stacked caption. It borrows the jump-to-today button's rounded outline so the number reads as
 * chrome and not as an eighth day, but drawn in the muted hairline: it is the only box in the header
 * that cannot be pressed, and it should sit behind the dates rather than compete with them. The cell
 * adds no row of its own — the header's height, which the page reports as its chrome and which sets
 * the grid's zoom floor, stays exactly what the day columns make it.
 */
@Composable
private fun WeekNumberCell(monday: LocalDate) {
    val week = isoWeekNumber(monday)
    val shape = RoundedCornerShape(Dimensions.weekNumberBoxRadius)
    Box(
        modifier = Modifier
            .width(Dimensions.weekTimeGutter)
            .height(Dimensions.calendarDayDisc)
            .clearAndSetSemantics { contentDescription = "Uge $week" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Dimensions.weekNumberBoxWidth, Dimensions.weekNumberBoxHeight)
                .border(Dimensions.weekNumberBoxBorder, Muted, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = week.toString(),
                color = Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** How many lanes of all-day bars the strip shows while it is collapsed. */
private const val WeekAllDayCollapsedLanes = 2

/**
 * The band between the day header and the hour grid, holding what has no place on a clock: all-day
 * events, and the days a multi-day event merely spans. An event covering several of the week's days
 * is **one bar drawn across them** ([daySpanLanes]) rather than a chip repeated in each column — the
 * layout every other calendar uses, and the only one in which a trip reads as a trip.
 *
 * Bars are packed into lanes longest-first, so a multi-day run always sits at the top of the strip
 * and cannot be pushed under the fold by a day that happens to hold three single-day entries. Those
 * single-day entries then share whatever lanes are left, since two bars on different days sit in the
 * same lane.
 *
 * The strip stays **collapsed** to [WeekAllDayCollapsedLanes] lanes — a busy Tuesday would otherwise
 * push the hours off the card — with a "+n" hint on the days holding more, and a caret in the gutter
 * that opens the lot. A week with no all-day entries takes no room at all.
 */
@Composable
private fun AllDayStrip(
    days: List<LocalDate>,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    sources: List<CalendarSource>,
    onOpenEvent: (CalendarEvent) -> Unit,
) {
    val byDay = days.associateWith { date ->
        eventsByDay[date].orEmpty().filter { it.startMinute == null }
    }
    if (byDay.values.all { it.isEmpty() }) return
    val lanes = daySpanLanes(days, byDay)
    var expanded by remember(days) { mutableStateOf(false) }
    val shown = if (expanded) lanes else lanes.take(WeekAllDayCollapsedLanes)
    // What the fold is hiding, per column: the days each dropped bar covers, so the hint lands on the
    // day the reader would look for the event on.
    val hidden = IntArray(days.size)
    lanes.drop(shown.size).forEach { lane ->
        lane.forEach { span -> for (index in span.startIndex..span.endIndex) hidden[index]++ }
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .width(Dimensions.weekTimeGutter)
                .height(Dimensions.weekAllDayChipHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (lanes.size > WeekAllDayCollapsedLanes) {
                Icon(
                    painter = painterResource(
                        if (expanded) Res.drawable.drop_up_filled else Res.drawable.drop_down_filled,
                    ),
                    contentDescription =
                        if (expanded) "Skjul heldagsbegivenheder" else "Vis alle heldagsbegivenheder",
                    tint = Muted,
                    modifier = Modifier
                        .size(Dimensions.weekChevronSize)
                        .clickable { expanded = !expanded },
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimensions.weekBlockGap),
        ) {
            shown.forEach { lane -> AllDayLane(lane, days.size, sources, onOpenEvent) }
            if (hidden.any { it > 0 }) {
                Row(Modifier.fillMaxWidth()) {
                    hidden.forEach { count ->
                        Box(Modifier.weight(1f).padding(end = Dimensions.weekBlockGap)) {
                            if (count > 0) {
                                Text(
                                    text = "+$count",
                                    color = Muted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable { expanded = true }
                                        .padding(horizontal = Dimensions.weekBlockPadding),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One lane of the strip: its bars laid across [dayCount] equal columns, with weighted spacers
 * standing in for the days nothing covers. Weights rather than offsets, so a bar lines up with the
 * hour grid's columns under it at any card width.
 */
@Composable
private fun AllDayLane(
    lane: List<DaySpan>,
    dayCount: Int,
    sources: List<CalendarSource>,
    onOpenEvent: (CalendarEvent) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(Dimensions.weekAllDayChipHeight)) {
        var column = 0
        lane.forEach { span ->
            if (span.startIndex > column) Spacer(Modifier.weight((span.startIndex - column).toFloat()))
            AllDayBar(
                span = span,
                color = calendarDotColor(span.event.sourceId, sources),
                onOpen = onOpenEvent,
                modifier = Modifier.weight(span.dayCount.toFloat()),
            )
            column = span.endIndex + 1
        }
        if (column < dayCount) Spacer(Modifier.weight((dayCount - column).toFloat()))
    }
}

/**
 * One bar: its calendar's colour at full strength, unlike the hour grid's blocks — the strip is read
 * across the week rather than down a column, and a solid run is what makes a span legible as one
 * thing. The label rides on it in [onCalendarColor]'s ink.
 *
 * An edge the event carries on past is drawn square, so a run continuing into the neighbouring week
 * (or, in the month grid, the next row) reads as cut rather than as ending here.
 */
@Composable
private fun AllDayBar(
    span: DaySpan,
    color: Color,
    onOpen: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(end = Dimensions.weekBlockGap)
            .fillMaxHeight()
            // As in the timed blocks: an event still queued for the home is drawn back, not badged.
            .alpha(if (span.event.pending) Dimensions.pendingWriteAlpha else 1f)
            .clip(spanShape(span, Dimensions.weekBlockRadius))
            .background(color)
            .clickable { onOpen(span.event) }
            .semantics { contentDescription = "Åbn ${span.event.title}" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = span.event.title,
            color = onCalendarColor(color),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Dimensions.weekBlockPadding),
        )
    }
}

/** A bar's corners: rounded where the event begins or ends, square where it runs on past the edge. */
internal fun spanShape(span: DaySpan, radius: Dp): RoundedCornerShape {
    val leading = if (span.continuesBefore) 0.dp else radius
    val trailing = if (span.continuesAfter) 0.dp else radius
    return RoundedCornerShape(
        topStart = leading,
        bottomStart = leading,
        topEnd = trailing,
        bottomEnd = trailing,
    )
}

/** Fill alpha of an event block: its calendar's color dropped back far enough that the title reads. */
private const val WeekBlockFillAlpha = 0.22f

/**
 * An event block's fill: [WeekBlockFillAlpha] of its calendar's color flattened onto the card, so the
 * block is opaque. A translucent fill would let the hour rules under it show through the event.
 */
private fun eventBlockFill(color: Color): Color = color.copy(alpha = WeekBlockFillAlpha).compositeOver(Card)

/** Where the grid opens on a week with no timed event to aim at. */
private const val WeekDefaultOpenMinute = 7 * 60

/** How far above the week's first event the grid opens, so the block isn't flush with the top edge. */
private const val WeekOpenLeadMinutes = 60

/**
 * Vertical distance into the grid of a minute-from-midnight — the one place the hour scale lives.
 * [hourHeight] is the scale itself (the reader's pinch level), passed rather than read from a token
 * so that stays true.
 */
private fun minuteOffset(minute: Int, hourHeight: Dp): Dp = hourHeight * (minute / 60f)

/**
 * How coarsely a tapped slot is rounded. Half an hour is forgiving on a touch screen and always lands
 * on a time somebody would have picked anyway — and at the pinched-in end of the zoom a single dp is
 * ten minutes, so anything finer would be pointing at a time nobody can aim for.
 */
private const val SlotSnapMinutes = 30

/**
 * Where a tap in the grid landed, as a time of day: [minuteOffset]'s inverse, snapped down to
 * [SlotSnapMinutes] and held short of midnight so the slot it names is always inside the day.
 */
private fun Density.slotTimeAt(y: Float, hourHeight: Dp): LocalTime {
    val minute = ((y / hourHeight.toPx()) * 60f).toInt().coerceIn(0, MinutesPerDay - SlotSnapMinutes)
    val snapped = minute - minute % SlotSnapMinutes
    return LocalTime(snapped / 60, snapped % 60)
}

/** The strides [hourStride] may pick between, finest first. Each divides 24 evenly. */
private val HourStrides = intArrayOf(1, 2, 3, 4, 6, 12)

/**
 * Show every Nth hour: the coarsest stride is chosen only when the finer ones would collide. Squeezed
 * to the floor, 24 labels and hairlines are mush, so labels and rules thin out as the hours close up —
 * every hour down to [Dimensions.weekHourLabelMinSpacing], then every 2nd, 3rd… Tune that one token
 * to move the breakpoints.
 */
internal fun hourStride(hourHeight: Dp): Int =
    HourStrides.firstOrNull { hourHeight * it >= Dimensions.weekHourLabelMinSpacing } ?: HourStrides.last()

/**
 * The levels the screen-reader zoom actions step between: the heights at which [hourStride] changes,
 * bounded by the range's own ends. A pinch is continuous and has no screen-reader equivalent, so the
 * actions move by the only steps at which the grid visibly reads differently.
 */
internal val WeekZoomSteps: List<Float> = buildList {
    add(WeekHourHeightRange.start)
    HourStrides.forEach { stride ->
        val height = Dimensions.weekHourLabelMinSpacing.value / stride
        if (height in WeekHourHeightRange) add(height)
    }
    add(WeekHourHeightRange.endInclusive)
}.distinct().sorted()

/** How far apart two levels must be to count as different steps, in dp. */
private const val ZoomStepEpsilon = 0.01f

/** The next [WeekZoomSteps] level above (or below) [current], stopping at the range's ends. */
internal fun steppedHourHeight(current: Float, expand: Boolean): Float =
    if (expand) WeekZoomSteps.firstOrNull { it > current + ZoomStepEpsilon } ?: WeekHourHeightRange.endInclusive
    else WeekZoomSteps.lastOrNull { it < current - ZoomStepEpsilon } ?: WeekHourHeightRange.start

/** Where a pinch's fingers were when it began, in the terms the grid re-aims itself with. */
private data class ZoomAnchor(
    /** Hours from midnight under the centroid — the point the zoom is asked to hold still. */
    val hours: Float,
    /** How far down the grid's viewport the centroid sat, in px. */
    val viewportY: Float,
)

/**
 * One page's hour grid: a full day of [hourHeight] rows, scrolling inside the height the page leaves
 * it whenever the day is taller than that. The [scroll] is the pager's, shared by every page, so
 * swiping weeks holds the hours still — see [WeekPager], which also aims it.
 *
 * **Two fingers squeeze the hours together** ([onHourHeight]), the classic calendar pinch: the hour
 * row shrinks, labels and rules thin out ([hourStride]), blocks shrink true to their real duration,
 * and once the whole day fits, the host hands the height the grid no longer needs to the checklist
 * below it. The minute under the fingers is held still across the scale change.
 */
@Composable
private fun WeekGrid(
    days: List<LocalDate>,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    today: LocalDate,
    nowMinutes: Int,
    sources: List<CalendarSource>,
    scroll: ScrollState,
    hourHeight: Dp,
    onHourHeight: (Float) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onNewEventAt: (LocalDate, LocalTime) -> Unit,
    onMoveEvent: (EventMove) -> Unit,
    dragEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // The gesture detector is keyed on Unit (it must survive recomposition without restarting
    // mid-pinch), so it reaches the current scale and callback through rememberUpdatedState — the
    // same reason the brightness dial's drag does.
    val currentHourHeight by rememberUpdatedState(hourHeight)
    val currentOnHourHeight by rememberUpdatedState(onHourHeight)
    // Null on every page but the one being pinched — which is what makes the re-aim below safe in a
    // pager that composes its neighbours too.
    var anchor by remember { mutableStateOf<ZoomAnchor?>(null) }

    // The drag is held **here**, not in the day column that started it: the block has to draw across
    // the column boundaries it is carried over (a column's siblings would paint over it), and only
    // this level knows how wide a column is — which is what a sideways delta has to be measured in.
    var drag by remember { mutableStateOf<WeekDrag?>(null) }
    var gridWidthPx by remember { mutableIntStateOf(0) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }

    // Where the held block currently points, resolved every frame it moves. `null` while it still
    // covers its own slot, which is also what makes a wobbled long press write nothing.
    val target: EventMove? = drag?.let { held ->
        val columnPx = gridWidthPx.toFloat() / DaysPerWeek
        if (columnPx <= 0f) return@let null
        droppedEventSlot(
            event = held.placed.event,
            weekDays = days,
            originDayIndex = held.originDayIndex,
            dayDelta = (held.offset.x / columnPx).roundToInt(),
            // The scroll travelled since the pickup counts as travel: the block is being carried
            // over the grid, and the grid moves under it when the edges auto-scroll.
            minuteDelta = (
                (held.offset.y + (scroll.value - held.scrollAtPickup)) /
                    with(density) { hourHeight.toPx() } * 60f
                ).roundToInt(),
        )
    }
    val slot = drag?.let { held -> target ?: held.origin }

    // The three read from inside the drag callbacks, which are captured once by a `pointerInput`
    // keyed on the block rather than on this state — so they have to be reached through a holder.
    val currentTarget by rememberUpdatedState(target)
    val currentOnMoveEvent by rememberUpdatedState(onMoveEvent)
    val dragging by rememberUpdatedState(drag != null)

    // Auto-scroll: a slot off the top or bottom of the card is unreachable otherwise, since the hour
    // scroll under this drag is locked out for the whole gesture. Runs per frame while a block is
    // held; the scroll it applies feeds straight back into [target] above, so the block keeps
    // pointing where the finger is rather than sliding away with the hours.
    LaunchedEffect(dragEnabled, viewportHeightPx, density) {
        snapshotFlow { drag != null }.collect { held ->
            if (!held || viewportHeightPx <= 0) return@collect
            val edge = with(density) { Dimensions.weekDragAutoScrollEdge.toPx() }
            val rate = with(density) { WeekDragAutoScrollRate.toPx() }
            while (drag != null) {
                withFrameNanos {}
                val current = drag ?: break
                val where = currentTarget ?: current.origin
                val top = with(density) {
                    minuteOffset(where.startMinute, currentHourHeight).toPx()
                } - scroll.value
                val bottom = top + with(density) {
                    blockHeight(current.spanMinutes, currentHourHeight).toPx()
                }
                val step = when {
                    top < edge -> -((edge - top) / edge).coerceIn(0f, 1f) * rate
                    bottom > viewportHeightPx - edge ->
                        ((bottom - (viewportHeightPx - edge)) / edge).coerceIn(0f, 1f) * rate
                    else -> 0f
                }
                if (step != 0f) scroll.scrollBy(step)
            }
        }
    }

    LaunchedEffect(scroll, density) {
        // Collected rather than keyed on the height: a pinch changes it every frame, and an effect
        // restarting that often would cancel each re-aim before it landed.
        snapshotFlow { currentHourHeight }.collect { height ->
            val held = anchor ?: return@collect
            // The new scroll range only exists once the grid has been laid out at the new height, and
            // layout trails this frame's composition — so re-aim on the next frame, or the scroll
            // clamps against the range the old height had.
            withFrameNanos {}
            val target = with(density) { (height * held.hours).toPx() } - held.viewportY
            scroll.scrollTo(target.roundToInt())
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .onSizeChanged { viewportHeightPx = it.height }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Two fingers only: one still belongs to the hour scroll under this modifier, to
                    // the week pager around it, and — on a phone — to the page pager around that.
                    // Watching the **Initial** pass is what lets the pinch outrank all three: once a
                    // second pointer is down, consuming there locks them out for the whole gesture.
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var zooming = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        // A held block outranks the pinch, or a second finger landing anywhere while
                        // one carries an event would rescale the grid out from under it.
                        if (!dragging && event.changes.count { it.pressed } >= 2) {
                            if (!zooming) {
                                zooming = true
                                val centroid = event.calculateCentroid(useCurrent = true)
                                anchor = ZoomAnchor(
                                    hours = (scroll.value + centroid.y) / currentHourHeight.toPx(),
                                    viewportY = centroid.y,
                                )
                            }
                            // A zero or non-finite factor is two fingers landing on the same point,
                            // not a request to collapse the day.
                            val zoom = event.calculateZoom()
                            if (zoom.isFinite() && zoom > 0f) {
                                currentOnHourHeight(currentHourHeight.value * zoom)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .verticalScroll(scroll),
    ) {
        val stride = hourStride(hourHeight)
        Row(Modifier.fillMaxWidth().height(minuteOffset(MinutesPerDay, hourHeight))) {
            HourGutter(hourHeight, stride)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { gridWidthPx = it.width },
            ) {
                HourRules(hourHeight, stride, Modifier.matchParentSize())
                Row(Modifier.matchParentSize()) {
                    days.forEachIndexed { index, date ->
                        DayColumn(
                            date = date,
                            dayIndex = index,
                            events = eventsByDay[date].orEmpty(),
                            sources = sources,
                            hourHeight = hourHeight,
                            // Deliberately not gated on a drag being in progress: the detector is
                            // *inside* this flag, so dropping it the moment a block is picked up
                            // would tear down the very gesture that picked it up. A second block
                            // taken at the same time is turned away below instead.
                            dragEnabled = dragEnabled,
                            draggedEvent = drag?.placed?.event,
                            onOpenEvent = onOpenEvent,
                            onNewEventAt = onNewEventAt,
                            onDragStart = { placed, dayIndex ->
                                if (drag == null) {
                                    drag = WeekDrag(placed, dayIndex, Offset.Zero, scroll.value)
                                }
                            },
                            onDragDelta = { amount ->
                                drag = drag?.let { it.copy(offset = it.offset + amount) }
                            },
                            onDragEnd = { placed, cancelled ->
                                // Only the block actually in hand may end the drag — a second finger
                                // that long-pressed elsewhere was turned away, and letting *it* go
                                // must not drop what the first one is still carrying.
                                if (drag?.placed?.event == placed.event) {
                                    if (!cancelled) currentTarget?.let(currentOnMoveEvent)
                                    drag = null
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                if (today in days) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimensions.weekNowLineHeight)
                            .offset(y = minuteOffset(nowMinutes, hourHeight))
                            .background(Rose),
                    )
                }
                // The held block, drawn last so it rides over every column it is carried across —
                // the whole reason the drag lives up here. It takes a column outright rather than
                // its origin's lane: it is being placed, not laid out beside anything yet.
                if (drag != null && slot != null) {
                    HeldBlock(
                        drag = drag!!,
                        slot = slot,
                        days = days,
                        columnWidth = with(density) { (gridWidthPx.toFloat() / DaysPerWeek).toDp() },
                        sources = sources,
                        hourHeight = hourHeight,
                    )
                }
            }
        }
    }
}

/** How fast the hours run past under a block held against the grid's edge, per frame at full depth. */
private val WeekDragAutoScrollRate = 14.dp

/** How far a held block's own slot is dropped back, so what is under it stays readable. */
private const val HeldBlockAlpha = 0.28f

/**
 * A block picked up by a long press: what was taken, from which column, and how far it has been
 * carried. [offset] is raw pointer travel — the hours scrolling underneath is added at the point of
 * resolution, since it is travel over the grid too.
 */
private data class WeekDrag(
    val placed: PositionedEvent,
    val originDayIndex: Int,
    val offset: Offset,
    val scrollAtPickup: Int,
) {
    /** Where it was taken from — what it reads as while it still covers its own slot. */
    val origin: EventMove get() = EventMove(placed.event, placed.event.date, placed.startMinute)

    /** Its drawn length, which a move keeps: a drag says *when*, never *how long*. */
    val spanMinutes: Int get() = placed.endMinute - placed.startMinute

    /**
     * The **event's** own length, for printing an end time by — `null` where the backend gave no
     * distinct end. [layoutDayEvents] floors a short span to [MinEventSpanMinutes] so the block can
     * be grabbed at all, and labelling the held block with that would be a lie about the event.
     */
    val labelSpanMinutes: Int? get() {
        val start = placed.event.startMinute ?: return null
        return placed.event.endMinute?.takeIf { it != start }?.minus(start)
    }
}

/**
 * The dragged block itself, floated over the columns on a shadowed plate. It prints the times of the
 * slot it is **pointing at**, not the ones it came from, so the drop is read off the block rather
 * than off the gutter behind it.
 */
@Composable
private fun HeldBlock(
    drag: WeekDrag,
    slot: EventMove,
    days: List<LocalDate>,
    columnWidth: Dp,
    sources: List<CalendarSource>,
    hourHeight: Dp,
) {
    val density = LocalDensity.current
    val height = blockHeight(drag.spanMinutes, hourHeight)
    val endLabel = drag.labelSpanMinutes?.let { formatMinuteOfDay(slot.startMinute + it) }
    EventBlock(
        event = drag.placed.event,
        color = calendarDotColor(drag.placed.event.sourceId, sources),
        startLabel = formatMinuteOfDay(slot.startMinute),
        endLabel = endLabel,
        text = blockText(
            blockHeight = height,
            titleLine = with(density) { WeekBlockTitleLineHeight.toDp() },
            timeLine = with(density) { WeekBlockTimeLineHeight.toDp() },
            timeLines = if (endLabel == null) 1 else 2,
        ),
        // A block in hand is not a tap target; the finger holding it is the gesture.
        onOpen = null,
        modifier = Modifier
            .offset(
                x = columnWidth * days.indexOf(slot.date).coerceAtLeast(0),
                y = minuteOffset(slot.startMinute, hourHeight),
            )
            .width(columnWidth - Dimensions.weekBlockGap)
            .height(height)
            .shadow(Dimensions.weekDragElevation, RoundedCornerShape(Dimensions.weekBlockRadius)),
    )
}

/**
 * How tall a block of [spanMinutes] draws. The floor it is held to scales with the zoom, so a
 * pinched grid stays *true to time*: blocks shrink with the day rather than a half-hour meeting
 * standing as tall as the two hours below it. At full expansion the floor is exactly
 * [Dimensions.weekMinBlockHeight].
 */
private fun blockHeight(spanMinutes: Int, hourHeight: Dp): Dp =
    minuteOffset(spanMinutes, hourHeight).coerceAtLeast(
        (Dimensions.weekMinBlockHeight * (hourHeight / Dimensions.weekHourHeightMax))
            .coerceAtLeast(MinBlockHeightFloor),
    )

/**
 * The gutter label's line box, trimmed to the glyphs. Newsreader's default line box carries more
 * space above the digits than below, so a centred label reads a hair below the rule it names.
 */
private val hourLabelStyle = TextStyle(
    lineHeight = 9.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/**
 * The grid's left edge: an hour label per hour rule the grid draws (midnight's needs none), thinning
 * to every [stride]th hour as the rows close up. Each label sits in a box centred **on** its rule, so
 * the number lines up with the line it belongs to rather than hanging above it.
 */
@Composable
private fun HourGutter(hourHeight: Dp, stride: Int) {
    Box(Modifier.width(Dimensions.weekTimeGutter).fillMaxHeight()) {
        for (hour in stride until HoursPerDay step stride) {
            Box(
                modifier = Modifier
                    .offset(y = minuteOffset(hour * 60, hourHeight) - Dimensions.weekHourLabelHeight / 2)
                    .fillMaxWidth()
                    .height(Dimensions.weekHourLabelHeight)
                    .padding(end = 4.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = formatTimeOfDay(LocalTime(hour, 0)),
                    color = Muted,
                    fontSize = 9.sp,
                    style = hourLabelStyle,
                )
            }
        }
    }
}

/**
 * Hairline hour rules and day dividers under the blocks — drawn, since they land under a pixel. The
 * rules follow the gutter's [stride], so the labelled hours are exactly the ruled ones; the day
 * dividers are unaffected by the zoom.
 */
@Composable
private fun HourRules(hourHeight: Dp, stride: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val hourStep = hourHeight.toPx()
        for (hour in stride until HoursPerDay step stride) {
            val y = hourStep * hour
            drawLine(CardBorder, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val dayStep = size.width / DaysPerWeek
        for (day in 1 until DaysPerWeek) {
            val x = dayStep * day
            drawLine(CardBorder, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }
    }
}

/**
 * One day's blocks, placed by their minute bounds. Overlapping events split the column into lanes
 * ([layoutDayEvents]) and each block is inset to its own, so two things at once read side by side.
 * Their height is [blockHeight]'s, which scales its floor with the zoom.
 *
 * **Tapping the empty space between blocks opens a new event there** ([onNewEventAt]), on this column's
 * [date] at the half-hour under the finger. The blocks are this box's children, so a tap on one reaches
 * *it* first and still opens the detail popup; only what nothing catches lands here. The tap is safe
 * beside the three drag gestures wrapped around this column — the hour scroll, the week pager and the
 * grid's pinch — because each of those consumes movement, which cancels a tap rather than firing it.
 *
 * **Holding a block picks it up** ([onDragStart]), where the event is one a write could actually
 * land on ([canDragEvent]). What the column reports is raw pointer travel and nothing else — the day
 * and minute it resolves to are the grid's to work out, since only the grid knows how wide a column
 * is and where the hours have scrolled to. The picked-up block stays drawn in its own slot, dropped
 * back to [HeldBlockAlpha], while the grid floats the real one over the columns.
 *
 * The long press outranks the taps and drags around it the way the queue's drag handle does: it
 * consumes from the moment it fires, which cancels the block's own click and locks the hour scroll,
 * the week pager and the page pager out for the rest of the gesture. Until it fires, any of them
 * moving first cancels the pickup — which is exactly right, since that was a scroll, not a hold.
 */
@Composable
private fun DayColumn(
    date: LocalDate,
    dayIndex: Int,
    events: List<CalendarEvent>,
    sources: List<CalendarSource>,
    hourHeight: Dp,
    dragEnabled: Boolean,
    /** The event currently in hand, whose own slot this column draws faintly. */
    draggedEvent: CalendarEvent?,
    onOpenEvent: (CalendarEvent) -> Unit,
    onNewEventAt: (LocalDate, LocalTime) -> Unit,
    onDragStart: (PositionedEvent, Int) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: (PositionedEvent, cancelled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positioned = remember(events) { layoutDayEvents(events) }
    // The detector is keyed on the column's day, not on the scale, and reaches the current scale and
    // callback through rememberUpdatedState — a pinch changes the scale every frame, and restarting the
    // gesture that often would drop the tap that produced it.
    val currentNewEventAt by rememberUpdatedState(onNewEventAt)
    val currentHourHeight by rememberUpdatedState(hourHeight)
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDragDelta by rememberUpdatedState(onDragDelta)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val haptics = LocalHapticFeedback.current
    // The two line boxes the block's text is fitted to, in dp, so the font scale is honoured.
    val density = LocalDensity.current
    val titleLine = with(density) { WeekBlockTitleLineHeight.toDp() }
    val timeLine = with(density) { WeekBlockTimeLineHeight.toDp() }
    BoxWithConstraints(
        modifier.pointerInput(date) {
            detectTapGestures { offset ->
                currentNewEventAt(date, slotTimeAt(offset.y, currentHourHeight))
            }
        },
    ) {
        val columnWidth = maxWidth
        positioned.forEach { placed ->
            val laneWidth = columnWidth / placed.laneCount
            val height = blockHeight(placed.endMinute - placed.startMinute, hourHeight)
            // The **event's** own bounds, not the placed ones: [layoutDayEvents] floors a short span to
            // [MinEventSpanMinutes] so the block can be hit, and printing that as its end time would be
            // a lie. Its start it copies through, so that one is the event's either way.
            val endLabel = placed.event.endMinute
                ?.takeIf { it != placed.startMinute }
                ?.let(::formatMinuteOfDay)
            val draggable = dragEnabled && canDragEvent(placed.event, sources)
            EventBlock(
                event = placed.event,
                color = calendarDotColor(placed.event.sourceId, sources),
                startLabel = formatMinuteOfDay(placed.startMinute),
                endLabel = endLabel,
                text = blockText(height, titleLine, timeLine, if (endLabel == null) 1 else 2),
                onOpen = onOpenEvent,
                modifier = Modifier
                    .offset(x = laneWidth * placed.lane, y = minuteOffset(placed.startMinute, hourHeight))
                    .width(laneWidth - Dimensions.weekBlockGap)
                    .height(height)
                    // Held under a finger outranks unsent: a dragged block's own slot has to fall
                    // back far enough to read what is under it, whichever of the two it is.
                    .alpha(
                        when {
                            placed.event == draggedEvent -> HeldBlockAlpha
                            placed.event.pending -> Dimensions.pendingWriteAlpha
                            else -> 1f
                        },
                    )
                    .then(
                        // Keyed on what it carries, not on the scale or the drag: it has to survive
                        // both a pinch and its own drag's recompositions without restarting.
                        if (!draggable) Modifier else Modifier.pointerInput(placed.event.uid, dayIndex) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentDragStart(placed, dayIndex)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    currentDragDelta(amount)
                                },
                                onDragEnd = { currentDragEnd(placed, false) },
                                onDragCancel = { currentDragEnd(placed, true) },
                            )
                        },
                    ),
            )
        }
    }
}

/** Below this a block would be a hairline: the one height the scaled floor never goes under. */
private val MinBlockHeightFloor = 3.dp

/** The block's two line boxes. Stated once, so [blockText]'s budget and the `Text`s can't drift apart. */
private val WeekBlockTitleLineHeight = 13.sp
private val WeekBlockTimeLineHeight = 11.sp

/** How a block spends its height: how many of its boundary times fit, and how many title lines. */
private data class BlockText(val timeLines: Int, val titleMaxLines: Int)

/**
 * The block's text budget — **the title comes first**. The times are printed only where the block has
 * room for them *and* a line of title, so a block too short for both spends every dp it has on the
 * title before that ellipses; a block too short even for one line is its colour alone, which still says
 * *when* and *whose* where no word would fit.
 *
 * Whatever is left after the times goes to the title as whole lines, which is what lets a tall block
 * print a long title in full instead of clipping it at an arbitrary two.
 *
 * [timeLines] is what the event *has* to show — two ends normally, one where the backend gave no
 * distinct end. Both-or-neither is about this budget, so a known start is never dropped merely because
 * the end is missing.
 */
private fun blockText(blockHeight: Dp, titleLine: Dp, timeLine: Dp, timeLines: Int): BlockText {
    val inner = blockHeight - Dimensions.weekBlockVerticalPadding * 2
    if (inner < titleLine) return BlockText(timeLines = 0, titleMaxLines = 0)
    val withTimes = inner - timeLine * timeLines
    return if (withTimes >= titleLine) BlockText(timeLines, (withTimes / titleLine).toInt())
    else BlockText(0, (inner / titleLine).toInt())
}

/**
 * One event in the grid: its calendar's color at low alpha, with a bar of the full color on the
 * leading edge. Inside, the shape every calendar uses — **start time on the top edge, end time on the
 * bottom edge, and the title spending everything between them** — as far as the block's height allows
 * ([blockText] decides; a squeezed block drops the times to keep the title, then the title too).
 *
 * Tapping it opens the detail popup rather than the editor: at [Dimensions.weekMinBlockHeight] and a
 * seventh of the card wide, a block is often too small to say what it even is — and a text-less one
 * says nothing at all, which is why its description stays whatever it is showing.
 *
 * A null [onOpen] is the copy floating under a finger ([HeldBlock]): it is neither a tap target nor
 * a second thing for a screen reader to find, since the block it was lifted off is still there.
 */
@Composable
private fun EventBlock(
    event: CalendarEvent,
    color: Color,
    startLabel: String,
    endLabel: String?,
    text: BlockText,
    onOpen: ((CalendarEvent) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Dimensions.weekBlockRadius))
            .background(eventBlockFill(color))
            .then(
                if (onOpen == null) Modifier
                else Modifier
                    .clickable { onOpen(event) }
                    .semantics { contentDescription = "Åbn ${event.title}" },
            ),
    ) {
        Box(Modifier.width(Dimensions.weekBlockBarWidth).fillMaxHeight().background(color))
        if (text.titleMaxLines > 0) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(
                        horizontal = Dimensions.weekBlockPadding,
                        vertical = Dimensions.weekBlockVerticalPadding,
                    ),
            ) {
                if (text.timeLines > 0) BlockTime(startLabel)
                Text(
                    text = event.title,
                    color = Ink,
                    fontSize = 11.sp,
                    lineHeight = WeekBlockTitleLineHeight,
                    fontWeight = FontWeight.Medium,
                    maxLines = text.titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    // The title takes everything the times leave, which is what pins the end time to
                    // the bottom edge — and caps the title's own box, so a line box measuring a hair
                    // taller than [blockText] budgeted clips inside the block rather than pushing
                    // that time out of it.
                    modifier = Modifier.weight(1f),
                )
                if (text.timeLines > 1 && endLabel != null) BlockTime(endLabel)
            }
        }
    }
}

/** One boundary time. Its line box is [WeekBlockTimeLineHeight] — the one [blockText] budgeted for it. */
@Composable
private fun BlockTime(label: String) {
    Text(
        text = label,
        color = InkSoft,
        fontSize = 10.sp,
        lineHeight = WeekBlockTimeLineHeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
