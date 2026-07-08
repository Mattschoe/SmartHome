package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.angleFromPointer
import com.mattschoe.smarthome.data.brightnessFromAngle
import com.mattschoe.smarthome.data.volumeFractionFromX
import com.mattschoe.smarthome.data.volumeFromFraction
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.Warmth
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.components.PillChip
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.InsetFill
import com.mattschoe.smarthome.ui.theme.SageGreen
import com.mattschoe.smarthome.ui.theme.WarmthOffMuted
import com.mattschoe.smarthome.ui.theme.color
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.speaker_outline
import smarthome.shared.generated.resources.volume_down_outline
import smarthome.shared.generated.resources.volume_off_outline
import smarthome.shared.generated.resources.volume_up_outline
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The flex-1 center card. Light and audio are selected **independently**: the top chip row picks the
 * light room (dial + warmth, bound to [lightRoomState]); the AUDIO chip row picks the audio room
 * (volume slider, bound to [audioRoomState]). Neither selection drives the other. Width-agnostic —
 * the `Expanded` assembly point in [Homepage.kt] assigns its width; all page geometry lives there.
 */
@Composable
fun CenterCard(
    activeLightRoom: Room,
    lightRoomState: RoomState,
    activeAudioRoom: Room,
    audioRoomState: RoomState,
    onSelectLightRoom: (Room) -> Unit,
    onSelectAudioRoom: (Room) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onWarmthChange: (Warmth) -> Unit,
    onToggleLight: () -> Unit,
    onVolumeChange: (Int) -> Unit,
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
                rooms = Room.entries,
                activeRoom = activeLightRoom,
                onSelectRoom = onSelectLightRoom,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimensions.cardGap))
            BrightnessDial(
                roomState = lightRoomState,
                onBrightnessChange = onBrightnessChange,
                onToggleLight = onToggleLight,
            )
            Text(
                text = if (lightRoomState.isLightOn) "${lightRoomState.brightnessPct}%" else "Off",
                color = Ink,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Dimensions.cardGap))
            WarmthSwatches(selected = lightRoomState.lightWarmth, onSelect = onWarmthChange)
            Spacer(Modifier.height(Dimensions.centerSectionGap))
            HorizontalDivider(color = CardBorder, thickness = 1.dp)
            Spacer(Modifier.height(Dimensions.centerSectionGap))
            SectionLabel("Audio", modifier = Modifier.fillMaxWidth(), fontSize = 14.sp)
            // The now-playing summary (track – artist) shown in this row in the reference is deferred
            // to the Phase 6 Media panel — it renders the same RoomState.nowPlaying data, so it's
            // built once, there. This phase ships the audio-room selector + the per-room volume slider.
            Spacer(Modifier.height(10.dp))
            RoomChipsRow(
                rooms = Room.audioRooms,
                activeRoom = activeAudioRoom,
                onSelectRoom = onSelectAudioRoom,
                leadingIcon = Res.drawable.speaker_outline,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            VolumeSlider(
                volumePct = audioRoomState.volumePct,
                onVolumeChange = onVolumeChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A wrapping row of room pill toggles. Used for both the light selector (all [Room.entries]) and the
 * AUDIO selector ([Room.audioRooms] with a speaker [leadingIcon]); selecting swaps that section's
 * state via [activeRoom].
 */
@Composable
private fun RoomChipsRow(
    rooms: List<Room>,
    activeRoom: Room,
    onSelectRoom: (Room) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: DrawableResource? = null,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rooms.forEach { room ->
            PillChip(
                text = room.displayName,
                selected = room == activeRoom,
                onClick = { onSelectRoom(room) },
                leadingIcon = leadingIcon,
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
            // Slider a11y in Compose is conveyed by progressBarRangeInfo + setProgress (there is no
            // Role.Slider); the arrow-key handler below adds keyboard adjustment.
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
            val bulbCenterY = baseline - bulbRadius
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
            val knobRadius = Dimensions.centerDialKnobDiameter.toPx() / 2f
            val knobStroke = Dimensions.centerDialKnobStroke.toPx()
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

/** Five warmth-preset circles; selecting one recolors the dial (via [roomState]) and turns the light on. */
@Composable
private fun WarmthSwatches(
    selected: Warmth,
    onSelect: (Warmth) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Center-align vertically: a selected swatch's ring makes its box taller, and without this the row
    // top-aligns children so that extra height hangs *below* — reading as the circle sliding "down"
    // rather than scaling up in place. Centered, the growth spreads symmetrically around the row axis.
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        // Selected swatches gain a concentric outer ring (ring + gap) drawn *around* a constant-size
        // fill, so the selection grows the footprint without shrinking the colored circle.
        val ringModifier =
            if (selected) {
                Modifier
                    .border(Dimensions.warmthHaloRingWidth, swatchColor, CircleShape)
                    .padding(Dimensions.warmthHaloRingWidth + Dimensions.warmthHaloGap)
            } else {
                Modifier
            }
        Box(ringModifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(Dimensions.warmthSwatchDiameter)
                    .shadow(Dimensions.swatchElevation, CircleShape)
                    .clip(CircleShape)
                    .background(swatchColor),
            )
        }
    }
}

/**
 * Per-room volume slider bound to the active room. The drag/fraction math
 * (`volumeFractionFromX`/`volumeFromFraction`) is the pure, unit-tested logic from
 * [com.mattschoe.smarthome.data.DashboardLogic]; this composable only draws the track/knob and
 * forwards pointer/key events. The leading glyph reflects the level via [volumeIcon] (muted→down→up).
 * Pointer math insets the usable track by the knob radius on each end so the knob stays in bounds and
 * the touch position lines up with where the knob renders.
 */
@Composable
private fun VolumeSlider(
    volumePct: Int,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on Unit so the gesture survives recomposition; capture the latest callback to avoid
    // mutating a stale room after a room switch (same pattern as the dial).
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)

    // The volume level to restore when un-muting. UI-local (no isMuted model field yet): tapping the
    // icon stashes the current level and drops to 0; tapping again restores it.
    var preMuteVolume by remember { mutableStateOf(if (volumePct > 0) volumePct else 30) }

    Row(
        modifier = modifier.heightIn(min = Dimensions.volumeRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = Dimensions.minTouch, minHeight = Dimensions.minTouch)
                .clip(CircleShape)
                .clickable {
                    if (volumePct > 0) {
                        preMuteVolume = volumePct
                        onVolumeChange(0)
                    } else {
                        onVolumeChange(preMuteVolume)
                    }
                }
                .semantics { contentDescription = if (volumePct > 0) "Slå lyd fra" else "Slå lyd til" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(volumeIcon(volumePct)),
                contentDescription = null,
                tint = InkSoft,
                modifier = Modifier.size(Dimensions.volumeIconSize),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimensions.volumeRowMinHeight)
                .pointerInput(Unit) {
                    val inset = Dimensions.volumeKnobDiameter.toPx() / 2f
                    detectTapGestures { pos ->
                        val fraction = volumeFractionFromX(pos.x, inset, size.width - inset * 2f)
                        currentOnVolumeChange(volumeFromFraction(fraction))
                    }
                }
                .pointerInput(Unit) {
                    val inset = Dimensions.volumeKnobDiameter.toPx() / 2f
                    detectDragGestures { change, _ ->
                        change.consume()
                        val fraction = volumeFractionFromX(change.position.x, inset, size.width - inset * 2f)
                        currentOnVolumeChange(volumeFromFraction(fraction))
                    }
                }
                .focusable()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Lydstyrke"
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(current = volumePct.toFloat(), range = 0f..100f)
                    setProgress { target ->
                        onVolumeChange(target.roundToInt().coerceIn(0, 100))
                        true
                    }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionRight -> {
                            onVolumeChange((volumePct + 5).coerceAtMost(100))
                            true
                        }
                        Key.DirectionDown, Key.DirectionLeft -> {
                            onVolumeChange((volumePct - 5).coerceAtLeast(0))
                            true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(Dimensions.volumeKnobDiameter)) {
                val trackH = Dimensions.volumeTrackHeight.toPx()
                val knobRadius = Dimensions.volumeKnobDiameter.toPx() / 2f
                val cy = size.height / 2f
                // Usable lane is inset by the knob radius on each end so the knob never clips.
                val laneLeft = knobRadius
                val laneWidth = (size.width - knobRadius * 2f).coerceAtLeast(0f)
                val fraction = volumePct / 100f
                val knobX = laneLeft + laneWidth * fraction
                val corner = CornerRadius(trackH / 2f, trackH / 2f)

                drawRoundRect(
                    color = InsetFill,
                    topLeft = Offset(laneLeft, cy - trackH / 2f),
                    size = Size(laneWidth, trackH),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = SageGreen,
                    topLeft = Offset(laneLeft, cy - trackH / 2f),
                    size = Size(laneWidth * fraction, trackH),
                    cornerRadius = corner,
                )
                drawCircle(color = Color.White, radius = knobRadius, center = Offset(knobX, cy))
                drawCircle(
                    color = SageGreen,
                    radius = knobRadius,
                    center = Offset(knobX, cy),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        Text(
            text = "$volumePct%",
            color = Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(Dimensions.volumePctLabelWidth),
        )
    }
}

/** The slider's leading glyph reflects the level: muted at 0, low through 50, high above. */
private fun volumeIcon(volumePct: Int): DrawableResource = when {
    volumePct <= 0 -> Res.drawable.volume_off_outline
    volumePct <= 50 -> Res.drawable.volume_down_outline
    else -> Res.drawable.volume_up_outline
}
