package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.REMINDER_OFFSETS
import com.mattschoe.smarthome.data.ReminderNoneLabel
import com.mattschoe.smarthome.data.formatReminderInherit
import com.mattschoe.smarthome.data.formatReminderOffset
import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.ui.components.PopupCard
import com.mattschoe.smarthome.ui.components.PopupScrim
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.components.verticalScrollFade
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink

/**
 * How long before an event to remind — a plain list of choices on the app's own floating card, the
 * same shape as [TimePickerPopup] and, like it, a sibling inside whatever surface opened it rather
 * than a dialog over the dashboard.
 *
 * Three kinds of answer, not two. Picking an offset or "Ingen" writes a rule **about this event**;
 * picking "Kalenderens standard" removes that rule so the calendar's own default applies again.
 * "Ingen" is therefore not the absence of a rule — it is an explicit silence, which is what lets one
 * shift of a work roster be muted while the roster keeps reminding for the rest.
 */
@Composable
fun BoxScope.ReminderPickerPopup(
    /** What is set now: `null` is "inherit", [ReminderRule.None] the explicit silence. */
    selected: ReminderRule?,
    /**
     * The calendar's standing default, named in the inherit row so the choice says what it means.
     * `null` when the calendar has none — the inherit row then reads as "(ingen)".
     */
    calendarDefault: Int?,
    /**
     * Whether to offer the inherit row at all. False when this picker *is* setting a calendar's
     * default, where there is nothing above it to inherit from.
     */
    showInherit: Boolean,
    title: String,
    onPick: (ReminderRule?) -> Unit,
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
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScrollFade(scroll)
                .verticalScroll(scroll),
        ) {
            if (showInherit) {
                ReminderOption(
                    label = formatReminderInherit(calendarDefault),
                    checked = selected == null,
                    onClick = { onPick(null) },
                )
            }
            ReminderOption(
                label = ReminderNoneLabel,
                checked = selected != null && selected.offsetMin == null,
                onClick = { onPick(ReminderRule.None) },
            )
            REMINDER_OFFSETS.forEach { minutes ->
                ReminderOption(
                    label = formatReminderOffset(minutes),
                    checked = selected?.offsetMin == minutes,
                    onClick = { onPick(ReminderRule(minutes)) },
                )
            }
        }
    }
}

/** One choice: the label, and a Forest dot on the one that is set. */
@Composable
private fun ReminderOption(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.minTouch)
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
