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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
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
import com.mattschoe.smarthome.data.DaysPerWeek
import com.mattschoe.smarthome.data.HoursPerDay
import com.mattschoe.smarthome.data.MinutesPerDay
import com.mattschoe.smarthome.data.WeekHourHeightRange
import com.mattschoe.smarthome.data.danishMonths
import com.mattschoe.smarthome.data.formatMinuteOfDay
import com.mattschoe.smarthome.data.formatTimeOfDay
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
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(Dimensions.weekTimeGutter))
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

/** How many all-day chips a day column shows while the strip is collapsed. */
private const val WeekAllDayCollapsedChips = 1

/**
 * The band between the day header and the hour grid, holding what has no place on a clock: all-day
 * events, and the days a multi-day event merely spans. Since events are expanded per day upstream,
 * such an event shows as one chip in each day's column rather than one bar across them.
 *
 * It stays **collapsed** to a single row — a busy Tuesday would otherwise push the hours off the
 * card — with a "+n" hint on the days holding more, and a caret in the gutter that opens the lot.
 * A week with no all-day entries takes no room at all.
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
    var expanded by remember { mutableStateOf(false) }
    val hasMore = byDay.values.any { it.size > WeekAllDayCollapsedChips }

    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .width(Dimensions.weekTimeGutter)
                .height(Dimensions.weekAllDayChipHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (hasMore) {
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
        days.forEach { date ->
            val events = byDay.getValue(date)
            val shown = if (expanded) events else events.take(WeekAllDayCollapsedChips)
            val hidden = events.size - shown.size
            Column(
                modifier = Modifier.weight(1f).padding(end = Dimensions.weekBlockGap),
                verticalArrangement = Arrangement.spacedBy(Dimensions.weekBlockGap),
            ) {
                shown.forEach { event ->
                    AllDayChip(event, calendarDotColor(event.sourceId, sources), onOpenEvent)
                }
                if (hidden > 0) {
                    Text(
                        text = "+$hidden",
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

@Composable
private fun AllDayChip(event: CalendarEvent, color: Color, onOpen: (CalendarEvent) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.weekAllDayChipHeight)
            .clip(RoundedCornerShape(Dimensions.weekBlockRadius))
            .background(eventBlockFill(color))
            .clickable { onOpen(event) }
            .semantics { contentDescription = "Åbn ${event.title}" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = event.title,
            color = Ink,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Dimensions.weekBlockPadding),
        )
    }
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
                        if (event.changes.count { it.pressed } >= 2) {
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
            Box(Modifier.weight(1f).fillMaxHeight()) {
                HourRules(hourHeight, stride, Modifier.matchParentSize())
                Row(Modifier.matchParentSize()) {
                    days.forEach { date ->
                        DayColumn(
                            date = date,
                            events = eventsByDay[date].orEmpty(),
                            sources = sources,
                            hourHeight = hourHeight,
                            onOpenEvent = onOpenEvent,
                            onNewEventAt = onNewEventAt,
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
            }
        }
    }
}

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
 *
 * The floor a short block is held to scales with the zoom, so a pinched grid stays *true to time*:
 * blocks shrink with the day rather than a half-hour meeting standing as tall as the two hours below
 * it. At full expansion the floor is exactly [Dimensions.weekMinBlockHeight].
 *
 * **Tapping the empty space between blocks opens a new event there** ([onNewEventAt]), on this column's
 * [date] at the half-hour under the finger. The blocks are this box's children, so a tap on one reaches
 * *it* first and still opens the detail popup; only what nothing catches lands here. The tap is safe
 * beside the three drag gestures wrapped around this column — the hour scroll, the week pager and the
 * grid's pinch — because each of those consumes movement, which cancels a tap rather than firing it.
 */
@Composable
private fun DayColumn(
    date: LocalDate,
    events: List<CalendarEvent>,
    sources: List<CalendarSource>,
    hourHeight: Dp,
    onOpenEvent: (CalendarEvent) -> Unit,
    onNewEventAt: (LocalDate, LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positioned = remember(events) { layoutDayEvents(events) }
    val floor = (Dimensions.weekMinBlockHeight * (hourHeight / Dimensions.weekHourHeightMax))
        .coerceAtLeast(MinBlockHeightFloor)
    // The detector is keyed on the column's day, not on the scale, and reaches the current scale and
    // callback through rememberUpdatedState — a pinch changes the scale every frame, and restarting the
    // gesture that often would drop the tap that produced it.
    val currentNewEventAt by rememberUpdatedState(onNewEventAt)
    val currentHourHeight by rememberUpdatedState(hourHeight)
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
            val blockHeight = minuteOffset(placed.endMinute - placed.startMinute, hourHeight)
                .coerceAtLeast(floor)
            // The **event's** own bounds, not the placed ones: [layoutDayEvents] floors a short span to
            // [MinEventSpanMinutes] so the block can be hit, and printing that as its end time would be
            // a lie. Its start it copies through, so that one is the event's either way.
            val endLabel = placed.event.endMinute
                ?.takeIf { it != placed.startMinute }
                ?.let(::formatMinuteOfDay)
            EventBlock(
                event = placed.event,
                color = calendarDotColor(placed.event.sourceId, sources),
                startLabel = formatMinuteOfDay(placed.startMinute),
                endLabel = endLabel,
                text = blockText(blockHeight, titleLine, timeLine, if (endLabel == null) 1 else 2),
                onOpen = onOpenEvent,
                modifier = Modifier
                    .offset(x = laneWidth * placed.lane, y = minuteOffset(placed.startMinute, hourHeight))
                    .width(laneWidth - Dimensions.weekBlockGap)
                    .height(blockHeight),
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
 */
@Composable
private fun EventBlock(
    event: CalendarEvent,
    color: Color,
    startLabel: String,
    endLabel: String?,
    text: BlockText,
    onOpen: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Dimensions.weekBlockRadius))
            .background(eventBlockFill(color))
            .clickable { onOpen(event) }
            .semantics { contentDescription = "Åbn ${event.title}" },
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
