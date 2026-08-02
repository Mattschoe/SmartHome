package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.Warmth
import com.mattschoe.smarthome.data.volumeFractionFromX
import com.mattschoe.smarthome.data.volumeFromFraction
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.controls.BrightnessDial
import com.mattschoe.smarthome.ui.controls.DragCommitInterval
import com.mattschoe.smarthome.ui.controls.WarmthSwatches
import com.mattschoe.smarthome.ui.controls.WrappingRoomChips
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.InsetFill
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.SageGreen
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.music_note_filled
import smarthome.shared.generated.resources.speaker_outline
import smarthome.shared.generated.resources.volume_down_outline
import smarthome.shared.generated.resources.volume_off_outline
import smarthome.shared.generated.resources.volume_up_outline

/**
 * The flex-1 center card. Light and audio are selected **independently**: the top chip row picks the
 * light room (dial + warmth, bound to [lightRoomState]); the AUDIO chip row picks the audio room
 * (volume slider + now-playing status, bound to [audioState]). Neither selection drives the other.
 * [joinTarget] is the room the audio room can play along with — `null` when there is nothing to
 * offer, and then no join action shows at all. Width-agnostic — the `Expanded` assembly point in [Homepage.kt] assigns its width; all page
 * geometry lives there.
 */
@Composable
fun CenterCard(
    activeLightRoom: Room,
    lightRoomState: RoomState,
    activeAudioRoom: Room,
    audioState: AudioState,
    joinTarget: Room?,
    audioJoined: Boolean,
    onSelectLightRoom: (Room) -> Unit,
    onSelectAudioRoom: (Room) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onWarmthChange: (Warmth) -> Unit,
    onToggleLight: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleAudioJoin: () -> Unit,
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
            WrappingRoomChips(
                rooms = Room.entries,
                activeRoom = activeLightRoom,
                onSelectRoom = onSelectLightRoom,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimensions.cardGap))
            BrightnessDial(
                brightnessPct = lightRoomState.brightnessPct,
                isLightOn = lightRoomState.isLightOn,
                warmth = lightRoomState.lightWarmth,
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
            AudioSectionHeader(audioState = audioState, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            WrappingRoomChips(
                rooms = Room.audioRooms,
                activeRoom = activeAudioRoom,
                onSelectRoom = onSelectAudioRoom,
                leadingIcon = Res.drawable.speaker_outline,
                modifier = Modifier.fillMaxWidth(),
            )
            // Weight-1 spacers above and below center the join action in the whitespace between the
            // audio chips and the volume slider (and collapse to one gap when there is no action).
            Spacer(Modifier.weight(1f))
            if (joinTarget != null) {
                JoinRoomAction(
                    text = if (audioJoined) "Leave ${joinTarget.displayName}"
                    else "Join ${joinTarget.displayName}",
                    onClick = onToggleAudioJoin,
                )
                Spacer(Modifier.weight(1f))
            }
            VolumeSlider(
                volumePct = audioState.volumePct,
                onVolumeChange = onVolumeChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The AUDIO section header: the "Audio" label with a trailing now-playing status aligned to the row
 * end. Playing → a music-note glyph + "title — artist" (truncated); idle → just a muted-speaker glyph
 * (no label — the glyph alone reads as "nothing playing", its a11y label carries the meaning).
 */
@Composable
private fun AudioSectionHeader(audioState: AudioState, modifier: Modifier = Modifier) {
    val track = audioState.nowPlaying
    val glyph = if (track != null) Res.drawable.music_note_filled else Res.drawable.volume_off_outline
    val statusColor = if (track != null) InkSoft else Muted

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("Audio", fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(glyph),
                contentDescription = if (track == null) "Der spilles ikke musik" else null,
                tint = statusColor,
                modifier = Modifier.size(16.dp),
            )
            if (track != null) {
                Text(
                    text = "${track.title} — ${track.artist}",
                    color = statusColor,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/**
 * The join/leave action between the audio chips and the volume slider: a bare accent-colored label,
 * no fill or border, so it reads as an offer rather than a fourth control competing with the pills.
 * The pill-shaped clip only shapes its ripple; the padding and [Dimensions.minTouch] height are the
 * touch target.
 */
@Composable
private fun JoinRoomAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Dimensions.minTouch)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Forest, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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

    // Local drag ownership (same pattern as the dial): the slider shows its own value mid-drag so HA
    // echoes can't jitter it, then falls back to the flow on release.
    var dragValue by remember { mutableStateOf<Int?>(null) }
    val displayedPct = dragValue ?: volumePct

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
                painter = painterResource(volumeIcon(displayedPct)),
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
                    // Throttle mid-drag commits; the release value always lands via onDragEnd.
                    var lastCommit = TimeSource.Monotonic.markNow() - DragCommitInterval
                    fun release() {
                        dragValue?.let { currentOnVolumeChange(it) }
                        dragValue = null
                    }
                    detectDragGestures(
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                    ) { change, _ ->
                        change.consume()
                        val fraction = volumeFractionFromX(change.position.x, inset, size.width - inset * 2f)
                        val value = volumeFromFraction(fraction)
                        dragValue = value
                        val now = TimeSource.Monotonic.markNow()
                        if (now - lastCommit >= DragCommitInterval) {
                            currentOnVolumeChange(value)
                            lastCommit = now
                        }
                    }
                }
                .focusable()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Lydstyrke"
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(current = displayedPct.toFloat(), range = 0f..100f)
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
                val fraction = displayedPct / 100f
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
            text = "$displayedPct%",
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
