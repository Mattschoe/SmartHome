package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.MaxRecurrenceCount
import com.mattschoe.smarthome.data.MaxRecurrenceInterval
import com.mattschoe.smarthome.data.Recurrence
import com.mattschoe.smarthome.data.RecurrenceEnd
import com.mattschoe.smarthome.data.RecurrenceFreq
import com.mattschoe.smarthome.data.RecurrenceNoneLabel
import com.mattschoe.smarthome.data.danishWeekdays
import com.mattschoe.smarthome.data.formatRecurrence
import com.mattschoe.smarthome.data.formatRecurrenceEndDate
import com.mattschoe.smarthome.data.model.EventEditScope
import com.mattschoe.smarthome.data.pickerLabel
import com.mattschoe.smarthome.data.presetRecurrences
import com.mattschoe.smarthome.ui.components.InsetSurface
import com.mattschoe.smarthome.ui.components.PopupCard
import com.mattschoe.smarthome.ui.components.PopupScrim
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.components.verticalScrollFade
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.ChipIdle
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnForest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * How often an event repeats — the same floating card as [ReminderPickerPopup], and a sibling inside
 * the editor rather than a dialog over the dashboard.
 *
 * Five presets over one escape hatch. The presets deliberately name nothing but a frequency: "hver
 * uge" already means the event's own weekday, so a preset survives the start date being moved, and
 * only [CustomRecurrencePopup] writes days down.
 */
@Composable
fun BoxScope.RecurrencePickerPopup(
    /** The rule as it stands, or `null` for an event that does not repeat. */
    selected: Recurrence?,
    onPick: (Recurrence?) -> Unit,
    /** Open the custom sheet. Kept separate from [onPick]: it opens a surface, it does not choose. */
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PopupScrim(onDismiss)
    PopupCard(
        modifier = modifier
            .align(Alignment.Center)
            .widthIn(max = Dimensions.eventDetailMaxWidth)
            .heightIn(max = Dimensions.eventDetailMaxHeight),
    ) {
        SectionLabel("Frekvens")
        Spacer(Modifier.height(8.dp))
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScrollFade(scroll)
                .verticalScroll(scroll),
        ) {
            presetRecurrences.forEach { preset ->
                PickerOptionRow(
                    label = if (preset == null) RecurrenceNoneLabel else formatRecurrence(preset),
                    checked = selected == preset,
                    onClick = { onPick(preset) },
                )
            }
            // Checked whenever the rule is not one of the presets — including on re-entry, so a rule
            // built here reads as the custom one it is rather than as nothing at all.
            PickerOptionRow(
                label = "Brugerdefineret…",
                checked = selected != null && selected !in presetRecurrences,
                onClick = onCustom,
            )
        }
    }
}

/**
 * The custom rule, after the reference: an interval, the unit it counts, the weekdays a weekly rule
 * lands on, and when the whole thing stops.
 *
 * Everything here is local until "Færdig" — a half-typed interval or a date being picked must not
 * reach the event, and "Annuller" has to leave the rule exactly as it was found. The weekday circles
 * appear only for a weekly rule: they say nothing about a monthly or yearly one, and Home Assistant's
 * iCal layer would reject them there.
 */
@Composable
fun BoxScope.CustomRecurrencePopup(
    /** What to open on — the rule being edited, or `null` to start from "every week". */
    initial: Recurrence?,
    /** The event's own start weekday, which is the day a fresh weekly rule lands on. */
    startDay: DayOfWeek,
    /** What the "på dag" date picker opens on before a day has been chosen. */
    defaultEndDate: LocalDate,
    onConfirm: (Recurrence) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seed = initial ?: Recurrence(RecurrenceFreq.Weekly)
    var freq by remember(initial) { mutableStateOf(seed.freq) }
    var interval by remember(initial) { mutableStateOf(seed.interval.toString()) }
    var days by remember(initial) {
        mutableStateOf(seed.byDay.ifEmpty { setOf(startDay) })
    }
    var end by remember(initial) { mutableStateOf(seed.end) }
    // Held apart from [end] so switching away from a choice and back does not lose what was typed in
    // it — the two rows keep their own values while only one of them is the answer.
    var endDate by remember(initial) {
        mutableStateOf((seed.end as? RecurrenceEnd.OnDate)?.date ?: defaultEndDate)
    }
    var count by remember(initial) {
        mutableStateOf(((seed.end as? RecurrenceEnd.AfterCount)?.count ?: 2).toString())
    }
    var pickingEndDate by remember(initial) { mutableStateOf(false) }

    PopupScrim(onDismiss)
    PopupCard(
        modifier = modifier
            .align(Alignment.Center)
            .widthIn(max = Dimensions.eventDetailMaxWidth)
            .heightIn(max = Dimensions.recurrenceSheetMaxHeight),
    ) {
        SectionLabel("Brugerdefineret frekvens")
        Spacer(Modifier.height(8.dp))
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScrollFade(scroll)
                .verticalScroll(scroll),
        ) {
            Text("Gentages hver", color = InkSoft, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            NumberField(
                value = interval,
                onValueChange = { interval = it },
                max = MaxRecurrenceInterval,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            RecurrenceFreq.entries.forEach { option ->
                RadioRow(
                    label = option.pickerLabel,
                    selected = freq == option,
                    onClick = { freq = option },
                )
            }

            if (freq == RecurrenceFreq.Weekly) {
                Spacer(Modifier.height(8.dp))
                BoundsDivider()
                Spacer(Modifier.height(8.dp))
                WeekdayCircles(
                    selected = days,
                    // Never let the last day be cleared: a weekly rule with no days at all is not a
                    // rule, and the reference's circles have no empty state either.
                    onToggle = { day -> days = if (day in days) (days - day).ifEmpty { days } else days + day },
                )
                Spacer(Modifier.height(8.dp))
                BoundsDivider()
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("Slutter")
            Spacer(Modifier.height(4.dp))
            RadioRow(
                label = "aldrig",
                selected = end is RecurrenceEnd.Never,
                onClick = { end = RecurrenceEnd.Never },
            )
            RadioRow(
                label = "på dag",
                selected = end is RecurrenceEnd.OnDate,
                onClick = { end = RecurrenceEnd.OnDate(endDate) },
            ) {
                Text(
                    text = formatRecurrenceEndDate(endDate),
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimensions.insetRadius))
                        .clickable {
                            end = RecurrenceEnd.OnDate(endDate)
                            pickingEndDate = true
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            RadioRow(
                label = "efter",
                selected = end is RecurrenceEnd.AfterCount,
                onClick = { end = RecurrenceEnd.AfterCount(count.toIntOrNull() ?: 1) },
            ) {
                NumberField(
                    value = count,
                    onValueChange = {
                        count = it
                        if (end is RecurrenceEnd.AfterCount) end = RecurrenceEnd.AfterCount(it.toIntOrNull() ?: 1)
                    },
                    max = MaxRecurrenceCount,
                    modifier = Modifier.width(Dimensions.recurrenceCountWidth),
                )
                Spacer(Modifier.width(8.dp))
                Text("gange", color = InkSoft, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        PickerActions(
            onCancel = onDismiss,
            confirmText = "Færdig",
            onConfirm = {
                onConfirm(
                    Recurrence(
                        freq = freq,
                        interval = interval.toIntOrNull()?.coerceIn(1, MaxRecurrenceInterval) ?: 1,
                        // A weekly rule that lands on exactly the start day is the plain "hver uge":
                        // leaving BYDAY off keeps it following the event if the date is moved later.
                        byDay = if (freq == RecurrenceFreq.Weekly && days != setOf(startDay)) days else emptySet(),
                        end = when (val chosen = end) {
                            is RecurrenceEnd.AfterCount ->
                                RecurrenceEnd.AfterCount(count.toIntOrNull()?.coerceIn(1, MaxRecurrenceCount) ?: 1)
                            is RecurrenceEnd.OnDate -> RecurrenceEnd.OnDate(endDate)
                            RecurrenceEnd.Never -> chosen
                        },
                    ),
                )
            },
        )
    }

    if (pickingEndDate) {
        DatePickerPopup(
            initial = endDate,
            onPick = {
                endDate = it
                end = RecurrenceEnd.OnDate(it)
                pickingEndDate = false
            },
            onDismiss = { pickingEndDate = false },
        )
    }
}

/**
 * Which occurrences a save or a delete on a recurring event reaches. Asked *before* the write rather
 * than assumed, because all three answers are ordinary: a moved appointment, a change from here on,
 * and a correction to the whole series.
 *
 * [allowThisEvent] is false when the frequency itself was changed — a single occurrence cannot carry
 * its own repetition rule, so offering the choice would only produce a write Home Assistant refuses.
 */
@Composable
fun BoxScope.EventScopePopup(
    title: String,
    allowThisEvent: Boolean,
    onPick: (EventEditScope) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PopupScrim(onDismiss)
    PopupCard(
        modifier = modifier
            .align(Alignment.Center)
            .widthIn(max = Dimensions.eventDetailMaxWidth)
            .heightIn(max = Dimensions.eventDetailMaxHeight),
    ) {
        SectionLabel(title)
        Spacer(Modifier.height(8.dp))
        if (allowThisEvent) {
            PickerOptionRow("Denne begivenhed", checked = false) { onPick(EventEditScope.ThisEvent) }
        }
        PickerOptionRow("Denne og fremtidige begivenheder", checked = false) {
            onPick(EventEditScope.ThisAndFuture)
        }
        PickerOptionRow("Alle begivenheder", checked = false) { onPick(EventEditScope.AllEvents) }
    }
}

/**
 * One choice in a flat popup list: the label, and a Forest dot on the one that is set — the shape the
 * reminder picker already uses, so the two rows the editor opens read as the same kind of list.
 */
@Composable
internal fun PickerOptionRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.minTouch)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = Ink,
            fontSize = 16.sp,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (checked) Box(Modifier.size(8.dp).clip(CircleShape).background(Forest))
    }
}

/**
 * A radio row from the custom sheet, optionally carrying its own control on the right (the end date,
 * the occurrence count). Drawn rather than taken from Material: the app has no `RadioButton`
 * anywhere, and one would arrive with its own palette and type scale.
 */
@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.minTouch)
            .clip(RoundedCornerShape(Dimensions.insetRadius))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioGlyph(selected)
        Text(
            text = label,
            color = Ink,
            fontSize = 16.sp,
            modifier = if (trailing == null) Modifier.weight(1f) else Modifier,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
private fun RadioGlyph(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(Dimensions.popupIconSize)
            .border(2.dp, if (selected) Forest else Muted, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Forest))
    }
}

/**
 * The seven days a weekly rule can land on, Monday first like every other weekday row here. Filled
 * Forest when picked, the idle chip's face when not — the room chips' two states, drawn round.
 */
@Composable
private fun WeekdayCircles(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        danishWeekdayInitials.forEachIndexed { index, initial ->
            val day = DayOfWeek(index + 1)
            val isOn = day in selected
            Box(
                modifier = Modifier
                    .size(Dimensions.recurrenceDaySize)
                    .clip(CircleShape)
                    .then(
                        if (isOn) Modifier.background(Forest)
                        else Modifier.background(ChipIdle).border(1.dp, CardBorder, CircleShape),
                    )
                    .selectable(selected = isOn, role = Role.Checkbox) { onToggle(day) }
                    .semantics { contentDescription = danishWeekdays[index] },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    color = if (isOn) OnForest else Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * A digits-only field on the editor's sunken plate. Empty is allowed while typing — the value is
 * clamped when the sheet is committed, so backspacing to nothing does not fight the cursor.
 */
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    max: Int,
    modifier: Modifier = Modifier,
) {
    val digits = max.toString().length
    InsetSurface(modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(Dimensions.minTouch),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { raw ->
                    val cleaned = raw.filter { it.isDigit() }.take(digits)
                    if (cleaned.isEmpty() || (cleaned.toIntOrNull() ?: 0) <= max) onValueChange(cleaned)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = Ink, fontSize = 16.sp, textAlign = TextAlign.Start),
                cursorBrush = SolidColor(Forest),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
