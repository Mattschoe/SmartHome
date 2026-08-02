package com.mattschoe.smarthome.ui.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.mattschoe.smarthome.data.angleFromPointer
import com.mattschoe.smarthome.data.brightnessFromAngle
import com.mattschoe.smarthome.data.model.Warmth
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.InsetFill
import com.mattschoe.smarthome.ui.theme.WarmthOffMuted
import com.mattschoe.smarthome.ui.theme.color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Minimum spacing between HA commits while dragging the dial/slider (the on-screen value stays live). */
internal val DragCommitInterval = 100.milliseconds

/**
 * Half-arc brightness dial, shared by the tablet's center card and the phone's light page. Drag math
 * (`angleFromPointer`/`brightnessFromAngle`) is the pure, unit-tested logic from
 * [com.mattschoe.smarthome.data.DashboardLogic] — this composable only draws the arc/knob/growth-shape
 * and forwards pointer/key events to it.
 *
 * The center "growth" shape is a deliberate deviation from the handoff spec's lightbulb glyph: a
 * circle anchored by its bottom that scales up uniformly (keeping its aspect ratio) as brightness
 * rises — like a sun growing over the horizon — rather than a glow expanding evenly outward. Its
 * fully-grown footprint is reserved by the dial's own height so nothing below it shifts.
 *
 * [width] scales the whole dial: every geometry token is multiplied by `width / centerDialWidth`, so
 * the phone's wider dial keeps the tablet's exact proportions rather than forking the geometry.
 * [height] defaults to the scaled tablet height, which leaves generous dead space under the arc;
 * a caller that wants what follows to sit closer can shorten it, since nothing is drawn below the
 * growth baseline anyway.
 */
@Composable
fun BrightnessDial(
    brightnessPct: Int,
    isLightOn: Boolean,
    warmth: Warmth,
    onBrightnessChange: (Int) -> Unit,
    onToggleLight: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = Dimensions.centerDialWidth,
    height: Dp = Dimensions.centerDialHeight * (width / Dimensions.centerDialWidth),
) {
    val scale = width / Dimensions.centerDialWidth
    val centerY = Dimensions.centerDialCenterY * scale
    val baselineY = Dimensions.centerGrowthBaselineY * scale
    val tapRadiusDp = Dimensions.centerBulbTapRadius * scale

    val density = LocalDensity.current
    val cx = with(density) { (width / 2).toPx() }
    val cy = with(density) { centerY.toPx() }

    // The gesture detectors are keyed on Unit (they must survive recomposition without restarting
    // mid-drag), so capture the latest callbacks via rememberUpdatedState — otherwise the coroutines
    // would keep calling the first composition's lambdas and mutate the wrong room after a switch.
    val currentOnBrightnessChange by rememberUpdatedState(onBrightnessChange)
    val currentOnToggleLight by rememberUpdatedState(onToggleLight)

    // While a drag is in flight the dial owns its value locally (non-null), so HA's interim echoes
    // can't jitter it; on release it falls back to the flow (which the adapter's optimistic hold keeps
    // at the target). See the plan's "local drag ownership".
    var dragValue by remember { mutableStateOf<Int?>(null) }
    val displayedPct = dragValue ?: brightnessPct

    val arcColor = if (isLightOn) warmth.color() else WarmthOffMuted
    val valueSweep = displayedPct / 100f * 180f

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .pointerInput(cx, cy) {
                // Throttle mid-drag commits so HA isn't hit per-pixel; the last value always lands via
                // onDragEnd. `lastCommit` lives for the pointer coroutine's lifetime (survives gestures).
                var lastCommit = TimeSource.Monotonic.markNow() - DragCommitInterval
                fun release() {
                    dragValue?.let { currentOnBrightnessChange(it) }
                    dragValue = null
                }
                detectDragGestures(
                    onDragEnd = { release() },
                    onDragCancel = { release() },
                ) { change, _ ->
                    change.consume()
                    val value = brightnessFromAngle(angleFromPointer(cx, cy, change.position.x, change.position.y))
                    dragValue = value
                    val now = TimeSource.Monotonic.markNow()
                    if (now - lastCommit >= DragCommitInterval) {
                        currentOnBrightnessChange(value)
                        lastCommit = now
                    }
                }
            }
            .pointerInput(cx, baselineY, tapRadiusDp) {
                detectTapGestures { pos ->
                    // Only the center bulb toggles the light — not the whole dial. Fixed hit region
                    // (independent of the bulb's current size) centered on the grown-bulb area.
                    val tapRadius = tapRadiusDp.toPx()
                    val bulbCenter = Offset(cx, baselineY.toPx() - tapRadius)
                    if ((pos - bulbCenter).getDistance() <= tapRadius) currentOnToggleLight()
                }
            }
            .focusable()
            // Slider a11y in Compose is conveyed by progressBarRangeInfo + setProgress (there is no
            // Role.Slider); the arrow-key handler below adds keyboard adjustment.
            .semantics(mergeDescendants = true) {
                contentDescription = "Lysstyrke"
                progressBarRangeInfo =
                    ProgressBarRangeInfo(current = displayedPct.toFloat(), range = 0f..100f)
                setProgress { target ->
                    onBrightnessChange(target.roundToInt().coerceIn(0, 100))
                    true
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.DirectionRight -> {
                        onBrightnessChange((brightnessPct + 5).coerceAtMost(100))
                        true
                    }
                    Key.DirectionDown, Key.DirectionLeft -> {
                        onBrightnessChange((brightnessPct - 5).coerceAtLeast(0))
                        true
                    }
                    else -> false
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radiusPx = (Dimensions.centerDialRadius * scale).toPx()
            val strokeWidth = (Dimensions.centerDialArcStroke * scale).toPx()
            val topLeft = Offset(cx - radiusPx, cy - radiusPx)
            val arcSize = Size(radiusPx * 2, radiusPx * 2)

            // Growth bulb: a circle anchored by its bottom at the growth baseline that scales
            // uniformly from min→max diameter with brightness (grows upward). Size is keyed to
            // brightness regardless of isLightOn — toggling off only mutes the color, per the
            // off-state spec pattern.
            val t = displayedPct / 100f
            val diameter = lerp(
                Dimensions.centerGrowthMinDiameter * scale,
                Dimensions.centerGrowthMaxDiameter * scale,
                t,
            )
            val bulbRadius = diameter.toPx() / 2f
            val bulbCenterY = baselineY.toPx() - bulbRadius
            // Fake soft shadow: a slightly larger, low-alpha dark circle offset below the bulb. Canvas
            // draws can't use Modifier.shadow, and this stays multiplatform (no native shadow layer).
            // Kept very faint so the bulb reads as sitting on the card, not floating above it.
            drawCircle(
                color = Color.Black.copy(alpha = 0.05f),
                radius = bulbRadius + 1.dp.toPx(),
                center = Offset(cx, bulbCenterY + 1.5.dp.toPx()),
            )
            drawCircle(
                color = arcColor.copy(alpha = 0.45f),
                radius = bulbRadius,
                center = Offset(cx, bulbCenterY),
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
            val knobRadius = (Dimensions.centerDialKnobDiameter * scale).toPx() / 2f
            val knobStroke = (Dimensions.centerDialKnobStroke * scale).toPx()
            drawCircle(
                color = Color.Black.copy(alpha = 0.07f),
                radius = knobRadius + 0.5.dp.toPx(),
                center = knobCenter + Offset(0f, 1.dp.toPx()),
            )
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
