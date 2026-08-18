@file:OptIn(ExperimentalMaterial3Api::class)

package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.formatLongDate
import com.mattschoe.smarthome.ui.components.PopupCard
import com.mattschoe.smarthome.ui.components.PopupScrim
import com.mattschoe.smarthome.ui.theme.Card
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.InsetFill
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnForest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * The event editor's date and time pickers, floated on the app's own [PopupCard] rather than in a
 * `DatePickerDialog`: a dialog would break out of the card the editor lives in, and the calendar
 * surface exists to keep the lights and volume beside it live.
 *
 * The controls themselves are Material's — a month grid and a clock dial are what a hand reaches for
 * here — and [com.mattschoe.smarthome.ui.theme.SmartHomeTheme] already dresses them in Forest, cream
 * and Newsreader. Only the few colours the scheme can't infer are overridden below.
 */

/**
 * The month grid. Its headline is this app's own Danish long date; the grid's weekday letters and
 * first-day-of-week come from the platform locale, which is the one part Material takes from the
 * device rather than from the caller.
 */
@Composable
fun BoxScope.DatePickerPopup(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
    )
    PopupScrim(onDismiss)
    PopupCard(modifier = Modifier.align(Alignment.Center)) {
        FitScale(DatePickerNaturalWidth, Modifier.weight(1f, fill = false)) {
            DatePicker(
                state = state,
                // No title and no mode toggle: both are English-labelled, and together they cost
                // ~90dp of height a phone page hasn't got to give.
                title = null,
                showModeToggle = false,
                headline = {
                    val shown = state.selectedDateMillis?.let(::localDateFromUtcMillis) ?: initial
                    Text(
                        text = formatLongDate(shown),
                        color = Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = Card,
                    headlineContentColor = Ink,
                    weekdayContentColor = Muted,
                    dayContentColor = Ink,
                    selectedDayContainerColor = Forest,
                    selectedDayContentColor = OnForest,
                    todayContentColor = Forest,
                    navigationContentColor = InkSoft,
                ),
            )
        }
        PickerActions(
            onCancel = onDismiss,
            onConfirm = {
                val picked = state.selectedDateMillis?.let(::localDateFromUtcMillis)
                if (picked != null) onPick(picked) else onDismiss()
            },
        )
    }
}

/**
 * The clock dial. Forced to 24 hours whatever the device is set to, because every time this app
 * prints — the clock, the agenda, the editor's own boundary rows — is `HH:mm`.
 *
 * The layout is pinned to [TimePickerLayoutType.Vertical] rather than left to
 * `TimePickerDefaults.layoutType()`, which reads the platform's idea of landscape and lays the hour
 * box out *beside* a full-size dial — over 500dp wide, and clipped in every panel this popup opens
 * in. Stacked, it is the dial's own 256dp, which fits the tablet card and both phone pages.
 */
@Composable
fun BoxScope.TimePickerPopup(
    initial: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    PopupScrim(onDismiss)
    PopupCard(modifier = Modifier.align(Alignment.Center)) {
        FitScale(TimePickerNaturalWidth, Modifier.weight(1f, fill = false)) {
            TimePicker(
                state = state,
                // Material knocks the selected number out of the selector disc with BlendMode.Clear
                // and Xor, which need somewhere of their own to composite. Without this the disc
                // renders as flat black with no number in it.
                modifier = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                layoutType = TimePickerLayoutType.Vertical,
                colors = TimePickerDefaults.colors(
                    clockDialColor = InsetFill,
                    selectorColor = Forest,
                    clockDialSelectedContentColor = OnForest,
                    clockDialUnselectedContentColor = Ink,
                    timeSelectorSelectedContainerColor = Forest,
                    timeSelectorSelectedContentColor = OnForest,
                    timeSelectorUnselectedContainerColor = InsetFill,
                    timeSelectorUnselectedContentColor = Ink,
                ),
            )
        }
        PickerActions(
            onCancel = onDismiss,
            onConfirm = { onPick(LocalTime(state.hour, state.minute)) },
        )
    }
}

/**
 * Annuller / OK, in the editor's own weight rather than a Material `TextButton` — the calendar's
 * popups are the only place in the app that would pull one in, and it would arrive carrying its own
 * type scale. Shared with the custom-frequency sheet, so every popup that commits, commits alike.
 */
@Composable
internal fun PickerActions(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "OK",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PickerAction("Annuller", onCancel)
        PickerAction(confirmText, onConfirm)
    }
}

@Composable
private fun PickerAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = Dimensions.minTouch)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Forest, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

/** UTC midnight epoch millis — how [androidx.compose.material3.DatePickerState] carries a day. */
private fun localDateFromUtcMillis(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

/**
 * Both Material pickers state their size as a *minimum* — the date picker's `sizeIn(minWidth = …)`,
 * the clock's `size(ClockDialContainerSize)` — so a narrower parent doesn't shrink them, it clips
 * the last weekday column or the right of the dial off. Measure at [naturalWidth] instead and shrink
 * the whole thing uniformly to whatever room the panel has. On the tablet the factor lands on 1f and
 * this is a no-op; on a phone page it is what keeps the control whole.
 *
 * The measure pass fixes the width rather than leaving it unbounded, because the date picker's month
 * pager is a `LazyRow` and would refuse an infinite one. Height is left free — every part of either
 * picker that scrolls vertically states a `requiredHeight` of its own.
 */
@Composable
private fun ColumnScope.FitScale(
    naturalWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = BoxWithConstraints(modifier.align(Alignment.CenterHorizontally)) {
    val maxW = constraints.maxWidth
    val maxH = constraints.maxHeight
    val measureWidth = with(LocalDensity.current) { naturalWidth.roundToPx() }
    Box(
        Modifier.layout { measurable, _ ->
            val placeable =
                measurable.measure(Constraints(minWidth = measureWidth, maxWidth = measureWidth))
            val scale = minOf(
                1f,
                maxW / placeable.width.toFloat(),
                maxH / placeable.height.toFloat(),
            )
            val width = (placeable.width * scale).roundToInt()
            val height = (placeable.height * scale).roundToInt()
            layout(width, height) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        },
    ) {
        content()
    }
}

/** `DatePickerModalTokens.ContainerWidth` and `TimePickerTokens.ClockDialContainerSize` — both
 *  internal to Material, and both the width its picker is drawn to be read at. */
private val DatePickerNaturalWidth = 360.dp
private val TimePickerNaturalWidth = 256.dp
