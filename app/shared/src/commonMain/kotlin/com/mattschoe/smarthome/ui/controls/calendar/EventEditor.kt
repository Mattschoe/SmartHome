package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.MinutesPerDay
import com.mattschoe.smarthome.data.buildEventDraft
import com.mattschoe.smarthome.data.formatLongDate
import com.mattschoe.smarthome.data.formatTimeOfDay
import com.mattschoe.smarthome.data.formatReminderRule
import com.mattschoe.smarthome.data.minutesOfDay
import com.mattschoe.smarthome.data.ruleFor
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.data.model.ReminderRules
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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.arrow_back_filled
import smarthome.shared.generated.resources.notifications_filled
import kotlin.time.Clock

/**
 * The Calendar panel's create/edit surface — a **swap inside the right card**, not a dialog: the
 * lights, dial and volume beside it stay live the whole time it is open, exactly as the Media panel
 * swaps between its now-playing, artist and browse surfaces.
 *
 * "Gem" lives in the header rather than under the last field, so saving never depends on reaching the
 * bottom of the form. That leaves the column's scroll as pure overflow relief: the tablet's right
 * card is taller than the form, so it never engages there, while the phone — landscape especially,
 * which is a good 100dp short of the form's height — can still reach the boundary rows and location.
 *
 * The field values are local to this composable and go up as one finished draft on save ([onSave]
 * carries the calendar it is written to). Routing a title through the ViewModel would recompose the
 * whole dashboard per keystroke, which is the one thing this surface exists to avoid.
 *
 * An event on a read-only calendar (the subscribed work roster) opens with every field disabled and
 * no save or delete, so its details are still reachable without pretending they can be changed. The
 * reminder row is the one exception, and deliberately: a reminder is stored beside the event rather
 * than in it, so it can be set on a roster shift nothing else about can be touched.
 */
@Composable
fun EventEditorSurface(
    target: EventEditorTarget,
    saving: Boolean,
    sources: List<CalendarSource>,
    /** The home's reminder rules — what the reminder row opens on, and resolves its label from. */
    reminders: ReminderRules,
    /**
     * Set the reminder on an event that already exists. Applied the moment it is picked rather than
     * on "Gem": it is not part of the event being edited, and committing it here is what lets a
     * read-only calendar's event — which has no "Gem" at all — still get one.
     */
    onSetEventReminder: (ReminderRule?) -> Unit,
    /**
     * Save the form. The third argument is the reminder to attach **once the event exists**, and is
     * only ever non-null on the create path: an existing event's reminder was already committed by
     * [onSetEventReminder]. `null` means there is nothing to attach — the event inherits its
     * calendar's default, which is what a fresh form opens on.
     */
    onSave: (String, CalendarEventDraft, ReminderRule?) -> Unit,
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
    var picking by remember(target) { mutableStateOf<PickTarget?>(null) }
    // The rule as it stands for this event: null is "inherit the calendar's default". On the edit
    // path it is seeded from the store and every pick is written through immediately; on the create
    // path there is no uid to key a rule on yet, so it is held here and rides out with the save.
    var reminder by remember(target) {
        mutableStateOf(existing?.let { ruleFor(it, reminders) })
    }

    val writable = remember(sources) { sources.filter { it.canWrite } }
    // The edit path is locked to the event's own calendar: a Home Assistant write addresses one
    // entity, so moving an event between calendars is a delete plus a create — out of scope here.
    var sourceId by remember(target) {
        mutableStateOf(existing?.sourceId ?: writable.firstOrNull()?.id.orEmpty())
    }
    val sourceChips = if (existing != null) sources.filter { it.id == existing.sourceId } else writable

    // Moving the start carries the end with it, so an event keeps the length it had rather than
    // collapsing (or inverting) while a date is being picked. It is also what makes multi-day and
    // past-midnight events fall out of the same two rows instead of needing cases of their own.
    val moveStart = { moved: LocalDateTime ->
        val span = minutesBetween(startAt, endAt).coerceAtLeast(0)
        startAt = moved
        endAt = moved.plusMinutes(span)
    }

    // The scroll is on the form alone, not on the Box — a picker floats over the whole panel and must
    // not slide with the field that opened it.
    val scroll = rememberScrollState()
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScrollFade(scroll).verticalScroll(scroll)) {
            EditorHeader(
                onBack = onBack,
                showSave = editable,
                saveEnabled = title.isNotBlank() && sourceId.isNotEmpty() && !saving,
                saving = saving,
                onSave = {
                    onSave(
                        sourceId,
                        buildEventDraft(title, startAt, endAt, allDay, location),
                        // Only the create path carries it out; an existing event's rule is already written.
                        reminder.takeIf { existing == null },
                    )
                },
            )
            Spacer(Modifier.height(16.dp))

            EditorTextField(
                value = title,
                onValueChange = { title = it },
                enabled = editable,
                placeholder = "Titel",
            )
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
            // calendar. Under the Tid title it reads as what it is — which of the two shapes the two
            // boundary rows below take.
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
            Spacer(Modifier.height(4.dp))

            EventBoundsRow(
                value = startAt,
                showTime = !allDay,
                enabled = editable,
                onPickDate = { picking = PickTarget.StartDate },
                onPickTime = { picking = PickTarget.StartTime },
            )
            BoundsDivider()
            EventBoundsRow(
                value = endAt,
                showTime = !allDay,
                enabled = editable,
                onPickDate = { picking = PickTarget.EndDate },
                onPickTime = { picking = PickTarget.EndTime },
            )
            Spacer(Modifier.height(Dimensions.mediaSectionGap))

            EditorTextField(
                value = location,
                onValueChange = { location = it },
                enabled = editable,
                placeholder = "Lokation",
            )
            Spacer(Modifier.height(4.dp))

            ReminderRow(
                label = formatReminderRule(reminder, reminders.byCalendar[sourceId]),
                onClick = { picking = PickTarget.Reminder },
            )

            if (editable && existing != null) {
                Spacer(Modifier.height(Dimensions.mediaSectionGap))
                DeleteAction(enabled = !saving, onDelete = onDelete)
            }
        }

        val dismiss = { picking = null }
        when (picking) {
            PickTarget.StartDate -> DatePickerPopup(
                initial = startAt.date,
                onPick = { moveStart(LocalDateTime(it, startAt.time)); picking = null },
                onDismiss = dismiss,
            )
            PickTarget.StartTime -> TimePickerPopup(
                initial = startAt.time,
                onPick = { moveStart(LocalDateTime(startAt.date, it)); picking = null },
                onDismiss = dismiss,
            )
            PickTarget.EndDate -> DatePickerPopup(
                initial = endAt.date,
                onPick = { endAt = LocalDateTime(it, endAt.time); picking = null },
                onDismiss = dismiss,
            )
            PickTarget.EndTime -> TimePickerPopup(
                initial = endAt.time,
                onPick = { endAt = LocalDateTime(endAt.date, it); picking = null },
                onDismiss = dismiss,
            )
            PickTarget.Reminder -> ReminderPickerPopup(
                selected = reminder,
                calendarDefault = reminders.byCalendar[sourceId],
                showInherit = true,
                title = "Påmindelse",
                onPick = { picked ->
                    reminder = picked
                    // An event that already exists can be keyed on now; a new one has no uid yet, so
                    // its rule waits for the save that mints one.
                    if (existing?.uid != null) onSetEventReminder(picked)
                    picking = null
                },
                onDismiss = dismiss,
            )
            null -> Unit
        }
    }
}

/** Which half of which boundary row opened a picker — the surface shows at most one at a time. */
private enum class PickTarget { StartDate, StartTime, EndDate, EndTime, Reminder }

/**
 * "Påmindelse", and what it is set to, as one tappable row. Bare like the boundary rows rather than
 * boxed like the title and location fields: it is a value being read back, not something typed.
 *
 * Never disabled, even on a read-only calendar. The reminder is not part of the event — it lives
 * beside it — which is exactly why a work roster nobody can write to can still remind.
 */
@Composable
private fun ReminderRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.minTouch)
            .clip(RoundedCornerShape(Dimensions.insetRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.notifications_filled),
            contentDescription = null,
            tint = InkSoft,
            modifier = Modifier.size(18.dp),
        )
        Text("Påmindelse", color = InkSoft, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Back on the left, "Gem" on the right — the shape a phone calendar's editor has, and the reason the
 * form below it never has to be scrolled to reach a save. The arrow takes the artist surface's
 * treatment: a full touch target bled out by its own glyph inset, so the icon lines up with the
 * content edge rather than sitting inside it. There is no heading between them — the fields say what
 * this is, and a "Nyt arrangement" over them only repeated the "+" that was just tapped.
 */
@Composable
private fun EditorHeader(
    onBack: () -> Unit,
    showSave: Boolean,
    saveEnabled: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
) {
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
        Spacer(Modifier.weight(1f))
        if (showSave) SaveAction(enabled = saveEnabled, saving = saving, onClick = onSave)
    }
}

/**
 * The form's group titles — Kalender and Tid. Full-size headings rather than the dashboard's 11sp
 * uppercase [com.mattschoe.smarthome.ui.components.SectionLabel]s, which read as smaller than the
 * calendar pills sitting under them. Only a group of controls gets one; the title and location
 * fields carry their own placeholder instead.
 */
@Composable
private fun EditorTitle(text: String) {
    Text(text = text, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

/**
 * Single-line field on a sunken inset, for the title and the location. What goes in is said by the
 * grey placeholder inside the empty field rather than by a heading above it: two bare fields with no
 * label is what makes the rest of the form fit the panel without scrolling.
 */
@Composable
private fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
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
            if (value.isEmpty()) {
                Text(text = placeholder, color = Muted, fontSize = 16.sp)
            }
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
 * One boundary of the event as a flat row — the long date on the left, the time on the right — each
 * half opening its own picker. An all-day event drops the time ([showTime]) rather than showing one
 * it ignores.
 *
 * Bare rows rather than the form's [InsetSurface] fields: these are two readings of one value, and
 * boxing each in its own sunken plate made the pair look like four separate inputs.
 */
@Composable
private fun EventBoundsRow(
    value: LocalDateTime,
    showTime: Boolean,
    enabled: Boolean,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    val color = if (enabled) Ink else InkSoft
    val shape = RoundedCornerShape(Dimensions.insetRadius)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.minTouch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Dimensions.minTouch)
                .clip(shape)
                .clickable(enabled = enabled, onClick = onPickDate)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = formatLongDate(value.date), color = color, fontSize = 16.sp)
        }
        if (showTime) {
            Box(
                modifier = Modifier
                    .widthIn(min = Dimensions.minTouch)
                    .heightIn(min = Dimensions.minTouch)
                    .clip(shape)
                    .clickable(enabled = enabled, onClick = onPickTime)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatTimeOfDay(value.time),
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** The hairline between the two boundary rows — what keeps them one control rather than two lines. */
@Composable
private fun BoundsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
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

/** Whole hours ahead of the next one, so a fresh event opens on a round time rather than 14:37. */
private const val NewEventDurationMinutes = 60

/**
 * What the boundary rows open on: an existing event's own bounds, or — for a new one — the slot tapped
 * in the week grid ([EventEditorTarget.New.time]), falling back to the next whole hour on
 * [EventEditorTarget.date]; either way running [NewEventDurationMinutes]. An all-day event's stored end
 * is *exclusive*, so it is pulled back to the last day it actually covers for display;
 * [buildEventDraft] puts the day back on save.
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
    // the day is known and the time is either the one tapped or a sensible round default.
    val tapped = (target as? EventEditorTarget.New)?.time
    val hour = ((currentTimeOfDay().hour + 1) % 24).coerceAtLeast(0)
    start = LocalDateTime(target.date, tapped ?: LocalTime(hour, 0))
    return start to start.plusMinutes(NewEventDurationMinutes)
}

private fun currentTimeOfDay(): LocalTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

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
