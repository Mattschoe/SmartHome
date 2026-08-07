package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.MinutesPerDay
import com.mattschoe.smarthome.data.buildEventDraft
import com.mattschoe.smarthome.data.danishMonths
import com.mattschoe.smarthome.data.danishWeekdays
import com.mattschoe.smarthome.data.minutesOfDay
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.ui.components.InsetSurface
import com.mattschoe.smarthome.ui.components.PillChip
import com.mattschoe.smarthome.ui.components.verticalScrollFade
import com.mattschoe.smarthome.ui.pages.homepage.EventEditorTarget
import com.mattschoe.smarthome.ui.theme.Card
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.ChipIdle
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.InsetFill
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnForest
import com.mattschoe.smarthome.ui.theme.Rose
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.arrow_back_filled
import kotlin.math.abs
import kotlin.time.Clock

/**
 * The Calendar panel's create/edit surface — a **swap inside the right card**, not a dialog: the
 * lights, dial and volume beside it stay live the whole time it is open, exactly as the Media panel
 * swaps between its now-playing, artist and browse surfaces.
 *
 * The field values are local to this composable and go up as one finished draft on save ([onSave]
 * carries the calendar it is written to). Routing a title through the ViewModel would recompose the
 * whole dashboard per keystroke, which is the one thing this surface exists to avoid.
 *
 * An event on a read-only calendar (the subscribed work roster) opens with every field disabled and
 * no save or delete, so its details are still reachable without pretending they can be changed.
 */
@Composable
fun EventEditorSurface(
    target: EventEditorTarget,
    saving: Boolean,
    sources: List<CalendarSource>,
    onSave: (String, CalendarEventDraft) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val existing = (target as? EventEditorTarget.Existing)?.event
    val editable = target !is EventEditorTarget.Existing || target.canWrite

    // Everything below is keyed on [target], so opening another event refills the form and re-opening
    // the same one resumes it unchanged.
    var title by remember(target) { mutableStateOf(existing?.title.orEmpty()) }
    var location by remember(target) { mutableStateOf(existing?.location.orEmpty()) }
    var allDay by remember(target) { mutableStateOf(existing?.allDay == true) }
    val seed = remember(target) { seedEventBounds(target) }
    var startAt by remember(target) { mutableStateOf(seed.first) }
    var endAt by remember(target) { mutableStateOf(seed.second) }

    val writable = remember(sources) { sources.filter { it.canWrite } }
    // The edit path is locked to the event's own calendar: a Home Assistant write addresses one
    // entity, so moving an event between calendars is a delete plus a create — out of scope here.
    var sourceId by remember(target) {
        mutableStateOf(existing?.sourceId ?: writable.firstOrNull()?.id.orEmpty())
    }
    val sourceChips = if (existing != null) sources.filter { it.id == existing.sourceId } else writable

    // One date window shared by both rows, anchored on the day the surface opened. A date wheel on
    // the *end* row as well as the start is what makes multi-day and past-midnight events fall out
    // of the same control instead of needing cases of their own.
    val days = remember(target) { editorDateWindow(target.date) }
    val hours = remember { (0..23).toList() }
    // Five-minute steps, plus whatever off-grid minute an event created elsewhere already has, so
    // re-saving a 09:17 meeting doesn't quietly round it.
    val minutes = remember(target) { minuteOptions(seed.first.time.minute, seed.second.time.minute) }

    val scroll = rememberScrollState()
    Column(modifier.fillMaxSize().verticalScrollFade(scroll).verticalScroll(scroll)) {
        EditorHeader(onBack = onBack)
        Spacer(Modifier.height(16.dp))

        EditorTitle("Titel")
        Spacer(Modifier.height(8.dp))
        EditorTextField(value = title, onValueChange = { title = it }, enabled = editable)
        Spacer(Modifier.height(Dimensions.mediaSectionGap))

        EditorTitle("Kalender")
        Spacer(Modifier.height(8.dp))
        CalendarChips(
            sources = sourceChips,
            allSources = sources,
            selectedId = sourceId,
            // Locked on the edit path — the single chip is which calendar it lives on, not a choice.
            enabled = editable && existing == null,
            onSelect = { sourceId = it },
        )
        Spacer(Modifier.height(Dimensions.mediaSectionGap))

        EditorTitle("Tid")
        Spacer(Modifier.height(8.dp))
        // A switch, not a chip: in a column of calendar pills a "Hele dagen" pill reads as another
        // calendar. Under the Tid title it reads as what it is — which of the two shapes the wheels
        // below take.
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.minTouch),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Hele dagen?", color = InkSoft, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = allDay,
                onCheckedChange = { if (editable) allDay = it },
                enabled = editable,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnForest,
                    checkedTrackColor = Forest,
                    checkedBorderColor = Forest,
                    uncheckedThumbColor = Card,
                    uncheckedTrackColor = InsetFill,
                    uncheckedBorderColor = CardBorder,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))

        EditorSubtitle("Start")
        Spacer(Modifier.height(8.dp))
        DateTimeRow(
            days = days,
            hours = hours,
            minutes = minutes,
            value = startAt,
            showTime = !allDay,
            enabled = editable,
            onChange = { moved ->
                // Moving the start carries the end with it, so an event keeps the length it had
                // rather than collapsing (or inverting) while the date is being picked. The carried
                // end is snapped back onto the minute wheel's own values, so what the wheels show
                // stays exactly what gets saved.
                val span = minutesBetween(startAt, endAt).coerceAtLeast(0)
                startAt = moved
                endAt = snapToMinuteOptions(moved.plusMinutes(span), minutes)
            },
        )
        Spacer(Modifier.height(12.dp))

        EditorSubtitle("Slut")
        Spacer(Modifier.height(8.dp))
        DateTimeRow(
            days = days,
            hours = hours,
            minutes = minutes,
            value = endAt,
            showTime = !allDay,
            enabled = editable,
            onChange = { endAt = it },
        )
        Spacer(Modifier.height(Dimensions.mediaSectionGap))

        EditorTitle("Lokation")
        Spacer(Modifier.height(8.dp))
        EditorTextField(value = location, onValueChange = { location = it }, enabled = editable)

        if (editable) {
            Spacer(Modifier.height(Dimensions.mediaSectionGap))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (existing != null) DeleteAction(enabled = !saving, onDelete = onDelete)
                Spacer(Modifier.weight(1f))
                SaveAction(
                    enabled = title.isNotBlank() && sourceId.isNotEmpty() && !saving,
                    saving = saving,
                    onClick = {
                        onSave(sourceId, buildEventDraft(title, startAt, endAt, allDay, location))
                    },
                )
            }
        }
    }
}

/**
 * The back arrow, and nothing else. The arrow takes the artist surface's treatment: a full touch
 * target bled out by its own glyph inset, so the icon lines up with the content edge rather than
 * sitting inside it. There is no heading — the form's own field titles say what it is, and a
 * "Nyt arrangement" over them only repeated the "+" that was just tapped.
 */
@Composable
private fun EditorHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .offset(x = -Dimensions.backButtonInset)
                .size(Dimensions.backButtonSize)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "Tilbage" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.arrow_back_filled),
                contentDescription = null,
                tint = InkSoft,
                modifier = Modifier.size(Dimensions.backIconSize),
            )
        }
    }
}

/**
 * The form's field titles — Titel, Kalender, Tid, Lokation. Full-size headings rather than the
 * dashboard's 11sp uppercase [com.mattschoe.smarthome.ui.components.SectionLabel]s, which read as
 * smaller than the calendar pills sitting under them.
 */
@Composable
private fun EditorTitle(text: String) {
    Text(text = text, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

/** One step down, for the two boundaries nested under Tid: Start and Slut. */
@Composable
private fun EditorSubtitle(text: String) {
    Text(text = text, color = InkSoft, fontSize = 16.sp, fontWeight = FontWeight.Medium)
}

/**
 * Single-line field on a sunken inset, for the title and the location. No placeholder: the title
 * above it already says what goes in, and a second line of grey prompt copy in every empty field
 * only crowded the form.
 */
@Composable
private fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    InsetSurface(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(Dimensions.minTouch),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(color = if (enabled) Ink else InkSoft, fontSize = 16.sp),
                cursorBrush = SolidColor(Forest),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The calendars an event may be written to. On the edit path this is the event's own calendar alone,
 * shown selected and inert — it says where the event lives without offering a move this surface
 * can't perform.
 *
 * The picked chip fills with the calendar's **own** colour rather than the accent: which calendar an
 * event lands on is the one selection here that is about a colour, and it is the same colour the
 * event's dot, block and detail bar will carry once it is saved.
 */
@Composable
private fun CalendarChips(
    sources: List<CalendarSource>,
    /** Every calendar the home has — a colour is its position among *those*, not among the chips. */
    allSources: List<CalendarSource>,
    selectedId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    if (sources.isEmpty()) {
        Text("Ingen kalender kan skrives til", color = Muted, fontSize = 15.sp)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sources.forEach { source ->
            PillChip(
                text = source.displayName,
                selected = source.id == selectedId,
                onClick = { if (enabled) onSelect(source.id) },
                selectedColor = calendarDotColor(source.id, allSources),
            )
        }
    }
}

/**
 * One boundary of the event as `[date][hour][minute]` wheels on a single inset, with a cream band
 * marking the selected row across all three. An all-day event drops the two time columns
 * ([showTime]) rather than showing times it ignores.
 */
@Composable
private fun DateTimeRow(
    days: List<LocalDate>,
    hours: List<Int>,
    minutes: List<Int>,
    value: LocalDateTime,
    showTime: Boolean,
    enabled: Boolean,
    onChange: (LocalDateTime) -> Unit,
) {
    val dateIndex = days.indexOf(value.date).coerceAtLeast(0)
    val hourIndex = hours.indexOf(value.time.hour).coerceAtLeast(0)
    val minuteIndex = minutes.indexOf(value.time.minute).coerceAtLeast(0)

    InsetSurface(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(Dimensions.wheelHeight)) {
            // The selection band sits *under* the wheels, so the centred row reads as picked whichever
            // column is being turned.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(Dimensions.wheelRowHeight)
                    .background(Card, RoundedCornerShape(Dimensions.insetRadius)),
            )
            Row(Modifier.fillMaxSize()) {
                WheelPicker(
                    items = days,
                    selectedIndex = dateIndex,
                    onSelect = { onChange(LocalDateTime(days[it], value.time)) },
                    label = ::formatWheelDate,
                    enabled = enabled,
                    modifier = Modifier.weight(WheelDateWeight),
                )
                if (showTime) {
                    WheelPicker(
                        items = hours,
                        selectedIndex = hourIndex,
                        onSelect = { onChange(LocalDateTime(value.date, LocalTime(hours[it], value.time.minute))) },
                        label = ::formatWheelNumber,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    )
                    WheelPicker(
                        items = minutes,
                        selectedIndex = minuteIndex,
                        onSelect = { onChange(LocalDateTime(value.date, LocalTime(value.time.hour, minutes[it]))) },
                        label = ::formatWheelNumber,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The date column is the wide one — it carries a weekday and a month name, the others two digits. */
private const val WheelDateWeight = 2f

/**
 * A snapping scroll wheel: a [Dimensions.wheelVisibleRows]-row window over [items], the middle row
 * being the selection. Padded by one row top and bottom so the first and last item can reach the
 * centre, and it reports through [onSelect] only once the fling has **settled** — a value committed
 * mid-scroll would drag the neighbouring wheels along with every row that passed.
 *
 * Generic because date, hour and minute are the same control with different contents.
 */
@Composable
private fun <T> WheelPicker(
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selectedIndex)
    val centered by remember { derivedStateOf { state.centeredItemIndex() } }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (scrolling) return@collect
            val landed = state.centeredItemIndex() ?: return@collect
            if (landed != currentSelected) currentOnSelect(landed)
        }
    }
    // Follow a value the form moved on its own — dragging the start date carries the end date along.
    LaunchedEffect(selectedIndex) {
        if (!state.isScrollInProgress && state.centeredItemIndex() != selectedIndex) {
            state.animateScrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .verticalScrollFade(state, color = InsetFill, height = Dimensions.wheelRowHeight),
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        userScrollEnabled = enabled,
        contentPadding = PaddingValues(vertical = Dimensions.wheelRowHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(items) { index, item ->
            val isSelected = index == (centered ?: selectedIndex)
            Box(
                modifier = Modifier.fillMaxWidth().height(Dimensions.wheelRowHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(item),
                    color = if (isSelected) Ink else Muted,
                    fontSize = if (isSelected) 17.sp else 15.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The item nearest the viewport's centre — the row the band marks, and what
 * [rememberSnapFlingBehavior] settles a fling onto. `null` before the first layout pass.
 */
private fun LazyListState.centeredItemIndex(): Int? {
    val info = layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index
}

/**
 * The Forest commit pill. It carries the spinner itself while the write is in flight: this is the
 * one control the surface can't act on optimistically, and a save that silently dropped an event
 * would be worse than a wait.
 */
@Composable
private fun SaveAction(enabled: Boolean, saving: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .heightIn(min = Dimensions.minTouch)
            .shadow(Dimensions.pillElevation, shape)
            .clip(shape)
            .background(if (enabled || saving) Forest else Forest.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp)
            .semantics { contentDescription = "Gem" },
        contentAlignment = Alignment.Center,
    ) {
        if (saving) {
            CircularProgressIndicator(
                color = OnForest,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text("Gem", color = OnForest, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Delete as an inline two-tap confirm — "Slet", then "Bekræft". A confirmation dialog would put back
 * exactly the blocking modal this whole surface exists to avoid.
 */
@Composable
private fun DeleteAction(enabled: Boolean, onDelete: () -> Unit) {
    TwoTapConfirm(enabled = enabled, onConfirm = onDelete) { armed, onTap ->
        val shape = RoundedCornerShape(percent = 50)
        Box(
            modifier = Modifier
                .heightIn(min = Dimensions.minTouch)
                .shadow(Dimensions.pillElevation, shape)
                .clip(shape)
                .then(
                    if (armed) Modifier.background(Rose, shape)
                    else Modifier.background(ChipIdle, shape).border(1.dp, CardBorder, shape),
                )
                .clickable(enabled = enabled, onClick = onTap)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (armed) "Bekræft" else "Slet",
                color = if (armed) Ink else Rose,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * The two-tap confirm itself, without a look: [content] draws the resting and the armed control and
 * reports a tap, this owns the arming. The armed state disarms itself after [ConfirmWindowMs] so a
 * stray tap can't leave a live delete sitting under someone's finger. Shared with the detail popup's
 * trash, so both deletes confirm the same way and neither opens a dialog to do it.
 */
@Composable
internal fun TwoTapConfirm(
    enabled: Boolean,
    onConfirm: () -> Unit,
    content: @Composable (armed: Boolean, onTap: () -> Unit) -> Unit,
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(ConfirmWindowMs)
            armed = false
        }
    }
    content(armed) {
        if (enabled) {
            if (armed) onConfirm() else armed = true
        }
    }
}

/** How long a tapped delete stays armed before it goes back to being harmless. */
private const val ConfirmWindowMs = 4_000L

/** How far back and forward the date wheels reach from the day the editor opened on. */
private const val WheelDaysBack = 60
private const val WheelDaysForward = 400

/** The date wheel's window: a bounded run of days around [anchor], which sits at index [WheelDaysBack]. */
private fun editorDateWindow(anchor: LocalDate): List<LocalDate> =
    List(WheelDaysBack + WheelDaysForward + 1) { anchor.plus(it - WheelDaysBack, DateTimeUnit.DAY) }

/** Granularity of the minute wheel — what a fingertip can actually aim at on a wall tablet. */
private const val MinuteStep = 5

/**
 * The minute wheel's values: the [MinuteStep] grid, plus any [existing] minute that isn't on it. An
 * event created in another client at 09:17 keeps its 17 rather than being rounded by the act of
 * opening it here.
 */
private fun minuteOptions(vararg existing: Int): List<Int> {
    val grid = (0 until 60 step MinuteStep).toList()
    val extra = existing.filter { it !in grid }
    return if (extra.isEmpty()) grid else (grid + extra).distinct().sorted()
}

/**
 * [at] with its minute pulled to the nearest value the minute wheel offers. Only ever moves anything
 * for an event that arrived off the [MinuteStep] grid — and only by less than the grid itself.
 */
private fun snapToMinuteOptions(at: LocalDateTime, options: List<Int>): LocalDateTime {
    if (at.time.minute in options) return at
    val nearest = options.minByOrNull { abs(it - at.time.minute) } ?: return at
    return LocalDateTime(at.date, LocalTime(at.time.hour, nearest))
}

/** Whole hours ahead of the next one, so a fresh event opens on a round time rather than 14:37. */
private const val NewEventDurationMinutes = 60

/**
 * What the wheels open on: an existing event's own bounds, or — for a new one — the next whole hour
 * on [EventEditorTarget.date] running [NewEventDurationMinutes]. An all-day event's stored end is
 * *exclusive*, so it is pulled back to the last day it actually covers for display; [buildEventDraft]
 * puts the day back on save.
 */
private fun seedEventBounds(target: EventEditorTarget): Pair<LocalDateTime, LocalDateTime> {
    val existing = (target as? EventEditorTarget.Existing)?.event
    var start = existing?.start
    if (existing != null && start != null) {
        val end = existing.end ?: start.plusMinutes(NewEventDurationMinutes)
        val shown =
            if (existing.allDay && end.date > start.date) {
                LocalDateTime(end.date.plus(-1, DateTimeUnit.DAY), LocalTime(0, 0))
            } else {
                end
            }
        return start to shown
    }
    // No stored bounds: a brand-new event, or a cached row from before they were carried. Either way
    // the day is known and the time is a sensible round default.
    val hour = ((currentTimeOfDay().hour + 1) % 24).coerceAtLeast(0)
    start = LocalDateTime(target.date, LocalTime(hour, 0))
    return start to start.plusMinutes(NewEventDurationMinutes)
}

private fun currentTimeOfDay(): LocalTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

/** "man 3. aug" — weekday for orientation, day and month for certainty, short enough for a column. */
private fun formatWheelDate(date: LocalDate): String {
    val weekday = danishWeekdays[date.dayOfWeek.isoDayNumber - 1].take(3).lowercase()
    val month = danishMonths[date.month.number - 1].take(3)
    return "$weekday ${date.day}. $month"
}

/** Zero-padded two digits, matching the clock and the agenda's times. */
private fun formatWheelNumber(value: Int): String = value.toString().padStart(2, '0')

/** [minutes] later, rolling the date over midnight (and back, for a negative shift). */
private fun LocalDateTime.plusMinutes(minutes: Int): LocalDateTime {
    val total = minutesOfDay(time) + minutes
    val within = total.mod(MinutesPerDay)
    return LocalDateTime(
        date.plus(total.floorDiv(MinutesPerDay), DateTimeUnit.DAY),
        LocalTime(within / 60, within % 60),
    )
}

/** Signed distance in minutes, across days — how the end keeps its offset when the start moves. */
private fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Int =
    from.date.daysUntil(to.date) * MinutesPerDay + (minutesOfDay(to.time) - minutesOfDay(from.time))
