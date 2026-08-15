package com.mattschoe.smarthome.ui.controls.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.formatTrackTime
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.volumeFractionFromX
import com.mattschoe.smarthome.data.livePositionSec
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.InsetFill
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.Rose
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The scrubber's displayed position, ticked forward locally once a second while playing. The device
 * state's [AudioState.positionSec] is HA's raw position frozen at its update stamp, so this anchors
 * it to *now* ([livePositionSec]) when the raw pair changes and ticks from there — the single place
 * the elapsed time is added. Any new device emission (position, play/pause, track change) resets
 * the tick base to the fresher value.
 */
@Composable
internal fun rememberLivePositionSec(audioState: AudioState, track: MediaTrack): Int {
    // The ticker is keyed on the *raw* pair rather than on a projected value: projecting here at
    // every composition would make the base shift with each `Ready` emission and tear the ticker
    // down at HA-event rate, which is itself a source of scrubber jitter.
    val basePositionSec = audioState.positionSec
    val updatedAtIso = audioState.positionUpdatedAtIso
    val playing = audioState.isPlaying
    var live by remember(basePositionSec, updatedAtIso, playing, track.title) {
        mutableStateOf(
            livePositionSec(
                positionSec = basePositionSec,
                updatedAtIso = updatedAtIso,
                isPlaying = playing,
                now = Clock.System.now(),
            )
        )
    }
    LaunchedEffect(basePositionSec, updatedAtIso, playing, track.title) {
        while (playing) {
            delay(1_000)
            live = (live + 1).coerceAtMost(if (track.durationSec > 0) track.durationSec else Int.MAX_VALUE)
        }
    }
    return live
}

/**
 * Drag-to-seek scrubber. The x→fraction math is the unit-tested [volumeFractionFromX]; this only
 * draws the Rose track and forwards seeks. Slider a11y mirrors the [VolumeSlider]'s.
 *
 * A drag tracks **locally** and commits a single seek on release (a tap commits at once): every
 * `media_seek` is a real service call the speaker has to act on, and per-move commits measurably
 * flood it (~25 calls in one short drag) and stall other playback commands for seconds after.
 * A committed seek then stays **latched** as the shown position until the device reports a position
 * near it — the device echoes the pre-seek position for a beat, and falling back to that would snap
 * the knob to the old spot before jumping forward again.
 */
@Composable
fun Scrubber(
    positionSec: Int,
    durationSec: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentDuration by rememberUpdatedState(durationSec)
    // Non-null while a finger is on the track; wins over the ticking device position until release.
    var dragPositionSec by remember { mutableStateOf<Int?>(null) }
    // The last committed seek. The device keeps reporting the pre-seek position until the seek
    // round-trips, so a bare release would snap the knob straight back — the latch holds the target
    // until the device position lands near it (or a timeout concedes the seek was lost).
    var pendingSeekSec by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(positionSec, pendingSeekSec) {
        val target = pendingSeekSec ?: return@LaunchedEffect
        if (abs(positionSec - target) <= SEEK_SYNC_TOLERANCE_SEC) pendingSeekSec = null
    }
    LaunchedEffect(pendingSeekSec) {
        if (pendingSeekSec == null) return@LaunchedEffect
        delay(SEEK_SYNC_TIMEOUT_MS)
        pendingSeekSec = null
    }
    // A new track (or the loading reset) invalidates a latch aimed at the old one.
    LaunchedEffect(enabled, durationSec) { pendingSeekSec = null }
    fun commitSeek(target: Int) {
        pendingSeekSec = target
        currentOnSeek(target)
    }

    val shownPositionSec = dragPositionSec ?: pendingSeekSec ?: positionSec
    val fraction = if (durationSec > 0) (shownPositionSec.toFloat() / durationSec).coerceIn(0f, 1f) else 0f

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(formatTrackTime(shownPositionSec), color = Muted, fontSize = 13.sp)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimensions.scrubberKnobDiameter)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val inset = Dimensions.scrubberKnobDiameter.toPx() / 2f
                    detectTapGestures { pos ->
                        val f = volumeFractionFromX(pos.x, inset, size.width - inset * 2f)
                        commitSeek((f * currentDuration).roundToInt())
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val inset = Dimensions.scrubberKnobDiameter.toPx() / 2f
                    detectDragGestures(
                        onDragEnd = {
                            dragPositionSec?.let { commitSeek(it) }
                            dragPositionSec = null
                        },
                        onDragCancel = { dragPositionSec = null },
                    ) { change, _ ->
                        change.consume()
                        val f = volumeFractionFromX(change.position.x, inset, size.width - inset * 2f)
                        dragPositionSec = (f * currentDuration).roundToInt()
                    }
                }
                .focusable()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Søgning i nummeret"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = positionSec.toFloat(),
                        range = 0f..durationSec.coerceAtLeast(1).toFloat(),
                    )
                    setProgress { target ->
                        commitSeek(target.roundToInt().coerceIn(0, durationSec))
                        true
                    }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionRight -> {
                            commitSeek((shownPositionSec + 5).coerceAtMost(durationSec)); true
                        }
                        Key.DirectionDown, Key.DirectionLeft -> {
                            commitSeek((shownPositionSec - 5).coerceAtLeast(0)); true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(Dimensions.scrubberKnobDiameter)) {
                val trackH = Dimensions.scrubberTrackHeight.toPx()
                val knobRadius = Dimensions.scrubberKnobDiameter.toPx() / 2f
                val cyy = size.height / 2f
                val laneLeft = knobRadius
                val laneWidth = (size.width - knobRadius * 2f).coerceAtLeast(0f)
                val knobX = laneLeft + laneWidth * fraction
                val corner = CornerRadius(trackH / 2f, trackH / 2f)

                drawRoundRect(
                    color = InsetFill,
                    topLeft = Offset(laneLeft, cyy - trackH / 2f),
                    size = Size(laneWidth, trackH),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = Rose,
                    topLeft = Offset(laneLeft, cyy - trackH / 2f),
                    size = Size(laneWidth * fraction, trackH),
                    cornerRadius = corner,
                )
                drawCircle(color = Color.White, radius = knobRadius, center = Offset(knobX, cyy))
                drawCircle(
                    color = Rose,
                    radius = knobRadius,
                    center = Offset(knobX, cyy),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        Text(formatTrackTime(durationSec), color = Muted, fontSize = 13.sp)
    }
}

/** How close the device position must come to a latched seek before the latch hands back to it. */
private const val SEEK_SYNC_TOLERANCE_SEC = 3

/** How long an unacknowledged seek latch holds before conceding and showing the device position. */
private const val SEEK_SYNC_TIMEOUT_MS = 8_000L
