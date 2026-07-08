package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.angleFromPointer
import com.mattschoe.smarthome.data.brightnessFromAngle
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.Warmth
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.components.PillChip
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InsetFill
import com.mattschoe.smarthome.ui.theme.WarmthOffMuted
import com.mattschoe.smarthome.ui.theme.color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The flex-1 center card: room chips, the brightness dial + warmth swatches (this phase), and an
 * "Audio" section stub reserved for the volume slider (a later phase). Width-agnostic — the
 * `Expanded` assembly point in [Homepage.kt] assigns its width; all page geometry lives there.
 */
@Composable
fun CenterCard(
    activeRoom: Room,
    roomState: RoomState,
    onSelectRoom: (Room) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onWarmthChange: (Warmth) -> Unit,
    onToggleLight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardContainer(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RoomChipsRow(
                activeRoom = activeRoom,
                onSelectRoom = onSelectRoom,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimensions.cardGap))
            BrightnessDial(
                roomState = roomState,
                onBrightnessChange = onBrightnessChange,
                onToggleLight = onToggleLight,
            )
            Text(
                text = if (roomState.isLightOn) "${roomState.brightnessPct}%" else "Off",
                color = Ink,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Dimensions.cardGap))
            SectionLabel("Warmth", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            WarmthSwatches(selected = roomState.lightWarmth, onSelect = onWarmthChange)
            Spacer(Modifier.height(Dimensions.cardGap))
            HorizontalDivider(color = CardBorder, thickness = 1.dp)
            Spacer(Modifier.height(Dimensions.cardGap))
            SectionLabel("Audio", modifier = Modifier.fillMaxWidth())
            // Volume slider lands here in the next phase; this stub just reserves the layout slot.
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Pill toggle per room. Selecting a room swaps the whole card's state (dial/warmth) via [activeRoom]. */
@Composable
private fun RoomChipsRow(
    activeRoom: Room,
    onSelectRoom: (Room) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Room.entries.forEach { room ->
            PillChip(
                text = room.displayName,
                selected = room == activeRoom,
                onClick = { onSelectRoom(room) },
            )
        }
    }
}

/**
 * Half-arc brightness dial. Drag math (`angleFromPointer`/`brightnessFromAngle`) is the pure,
 * unit-tested logic from [com.mattschoe.smarthome.data.DashboardLogic] — this composable only
 * draws the arc/knob/growth-shape and forwards pointer/key events to it.
 *
 * The center "growth" shape is a deliberate deviation from the handoff spec's lightbulb glyph: a
 * circle anchored by its bottom that scales up uniformly (keeping its aspect ratio) as brightness
 * rises — like a sun growing over the horizon — rather than a glow expanding evenly outward. Its
 * fully-grown footprint is reserved by [Dimensions.centerDialHeight] so nothing below it shifts.
 */
@Composable
private fun BrightnessDial(
    roomState: RoomState,
    onBrightnessChange: (Int) -> Unit,
    onToggleLight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cx = with(density) { (Dimensions.centerDialWidth / 2).toPx() }
    val cy = with(density) { Dimensions.centerDialCenterY.toPx() }

    // The gesture detectors are keyed on Unit (they must survive recomposition without restarting
    // mid-drag), so capture the latest callbacks via rememberUpdatedState — otherwise the coroutines
    // would keep calling the first composition's lambdas and mutate the wrong room after a switch.
    val currentOnBrightnessChange by rememberUpdatedState(onBrightnessChange)
    val currentOnToggleLight by rememberUpdatedState(onToggleLight)

    val arcColor = if (roomState.isLightOn) roomState.lightWarmth.color() else WarmthOffMuted
    val valueSweep = roomState.brightnessPct / 100f * 180f

    Box(
        modifier = modifier
            .size(width = Dimensions.centerDialWidth, height = Dimensions.centerDialHeight)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val angle = angleFromPointer(cx, cy, change.position.x, change.position.y)
                    currentOnBrightnessChange(brightnessFromAngle(angle))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    // Only the center bulb toggles the light — not the whole dial. Fixed hit region
                    // (independent of the bulb's current size) centered on the grown-bulb area.
                    val tapRadius = Dimensions.centerBulbTapRadius.toPx()
                    val bulbCenter = Offset(cx, Dimensions.centerGrowthBaselineY.toPx() - tapRadius)
                    if ((pos - bulbCenter).getDistance() <= tapRadius) currentOnToggleLight()
                }
            }
            .focusable()
            .semantics(mergeDescendants = true) {
                contentDescription = "Lysstyrke"
                progressBarRangeInfo =
                    ProgressBarRangeInfo(current = roomState.brightnessPct.toFloat(), range = 0f..100f)
                setProgress { target ->
                    onBrightnessChange(target.roundToInt().coerceIn(0, 100))
                    true
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.DirectionRight -> {
                        onBrightnessChange((roomState.brightnessPct + 5).coerceAtMost(100))
                        true
                    }
                    Key.DirectionDown, Key.DirectionLeft -> {
                        onBrightnessChange((roomState.brightnessPct - 5).coerceAtLeast(0))
                        true
                    }
                    else -> false
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radiusPx = Dimensions.centerDialRadius.toPx()
            val strokeWidth = Dimensions.centerDialArcStroke.toPx()
            val topLeft = Offset(cx - radiusPx, cy - radiusPx)
            val arcSize = Size(radiusPx * 2, radiusPx * 2)

            // Growth bulb: a circle anchored by its bottom at centerGrowthBaselineY that scales
            // uniformly from min→max diameter with brightness (grows upward). Size is keyed to
            // brightness regardless of isLightOn — toggling off only mutes the color, per the
            // off-state spec pattern.
            val t = roomState.brightnessPct / 100f
            val diameter = lerp(Dimensions.centerGrowthMinDiameter, Dimensions.centerGrowthMaxDiameter, t)
            val bulbRadius = diameter.toPx() / 2f
            val baseline = Dimensions.centerGrowthBaselineY.toPx()
            drawCircle(
                color = arcColor.copy(alpha = 0.45f),
                radius = bulbRadius,
                center = Offset(cx, baseline - bulbRadius),
            )

            drawArc(
                color = InsetFill,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = arcColor,
                startAngle = 180f,
                sweepAngle = valueSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            val knobAngleRad = (180f + valueSweep).toDouble() * PI / 180.0
            val knobCenter = Offset(
                x = cx + radiusPx * cos(knobAngleRad).toFloat(),
                y = cy + radiusPx * sin(knobAngleRad).toFloat(),
            )
            val knobRadius = Dimensions.centerDialKnobDiameter.toPx() / 2f
            val knobStroke = Dimensions.centerDialKnobStroke.toPx()
            drawCircle(color = Color.White, radius = knobRadius, center = knobCenter)
            drawCircle(
                color = arcColor,
                radius = knobRadius - knobStroke / 2f,
                center = knobCenter,
                style = Stroke(width = knobStroke),
            )
        }
    }
}

/** Five warmth-preset circles; selecting one recolors the dial (via [roomState]) and turns the light on. */
@Composable
private fun WarmthSwatches(
    selected: Warmth,
    onSelect: (Warmth) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Warmth.entries.forEach { warmth ->
            WarmthSwatch(warmth = warmth, selected = warmth == selected, onSelect = { onSelect(warmth) })
        }
    }
}

@Composable
private fun WarmthSwatch(warmth: Warmth, selected: Boolean, onSelect: () -> Unit) {
    val swatchColor = warmth.color()
    Box(
        modifier = Modifier
            .sizeIn(minWidth = Dimensions.minTouch, minHeight = Dimensions.minTouch)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .semantics { contentDescription = warmth.name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Dimensions.warmthSwatchDiameter)
                .scale(if (selected) 1.08f else 1f)
                .then(
                    if (selected) {
                        Modifier
                            .border(Dimensions.warmthHaloRingWidth, swatchColor, CircleShape)
                            .padding(Dimensions.warmthHaloGap)
                    } else {
                        Modifier
                    },
                )
                .clip(CircleShape)
                .background(swatchColor),
        )
    }
}
