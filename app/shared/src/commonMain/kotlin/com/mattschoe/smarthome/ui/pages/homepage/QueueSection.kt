package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.formatTrackTime
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.components.verticalScrollFade
import com.mattschoe.smarthome.ui.theme.ArtScrim
import com.mattschoe.smarthome.ui.theme.Card
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnArt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.music_note_filled

/**
 * The now-playing surface's "Up next" list: the next entries of the active audio room's queue, in
 * YouTube-Music's two gestures — **tap a row to skip to it**, **long-press to pick it up** and drop
 * it elsewhere in the queue.
 *
 * It is the only scrolling part of the now-playing surface (art, scrubber and transport stay pinned
 * above it), so it fills whatever height is left and drag auto-scroll carries a picked-up row past
 * the bottom of that short viewport.
 */
@Composable
fun UpNextSection(
    queue: List<MediaTrack>,
    enabled: Boolean,
    pendingQueueItemId: String?,
    onPlayQueueItem: (String) -> Unit,
    onMoveQueueItem: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Skipping to a row takes Music Assistant seconds of stream resolution; while one skip is in
    // flight the tapped row spins and the whole list stops accepting taps — a re-tap of a row that
    // hasn't visibly reacted is a retry, not a new intent.
    val tapsBlocked = !enabled || pendingQueueItemId != null
    // The dropped order shows immediately instead of waiting for the server round-trip. Keyed on the
    // incoming queue, so a refresh that genuinely reorders resets it, while the identical polls that
    // follow our own move (data-class equality) leave the drag result standing.
    var localQueue by remember(queue) { mutableStateOf(queue) }
    // Where the row being dragged started. MA's move_item is *relative*, and the lazy API reports
    // every swap along the way, so the net shift is only known once the drag ends.
    var dragFrom by remember { mutableStateOf(-1) }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        localQueue = localQueue.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Column(modifier.fillMaxWidth()) {
        SectionLabel("Up next")
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScrollFade(listState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // Clearance for the floating [MinimizeHandle] the card pins over this surface, so the last
            // row can scroll out from under it.
            contentPadding = PaddingValues(bottom = Dimensions.minTouch),
        ) {
            itemsIndexed(localQueue, key = { _, track -> track.queueKey() }) { index, track ->
                ReorderableItem(reorderState, key = track.queueKey()) { isDragging ->
                    val queueItemId = track.queueItemId
                    QueueRow(
                        index = index,
                        track = track,
                        dragging = isDragging,
                        loading = queueItemId != null && queueItemId == pendingQueueItemId,
                        modifier = Modifier
                            .longPressDraggableHandle(
                                onDragStarted = { dragFrom = index },
                                onDragStopped = {
                                    // The live swaps already moved the row inside [localQueue]; where it
                                    // landed there is the drop target. One net call, not one per swap.
                                    val to = localQueue.indexOfFirst { it.queueKey() == track.queueKey() }
                                    if (dragFrom >= 0 && to >= 0 && to != dragFrom) {
                                        queueItemId?.let { onMoveQueueItem(it, to - dragFrom) }
                                    }
                                    dragFrom = -1
                                },
                            )
                            // A track with no queue handle (the mock's fixtures, an HA-only session) still
                            // renders — it just isn't a target for either gesture.
                            .clickable(enabled = queueItemId != null && !tapsBlocked) {
                                queueItemId?.let(onPlayQueueItem)
                            }
                            .semantics { if (queueItemId != null) contentDescription = "Afspil ${track.title}" },
                    )
                }
            }
        }
    }
}

/** List/reorder identity, matching `DashboardLogic`'s handle convention for the fixture tracks. */
private fun MediaTrack.queueKey(): String = queueItemId ?: title

/**
 * An up-next queue row: index-colored thumb + title/artist + duration. While [dragging] it lifts onto
 * a card-colored plate with a shadow and a touch of scale, so the picked-up row reads as held above
 * the ones it is passing. While [loading] (its skip is in flight) the thumb carries a spinner.
 */
@Composable
internal fun QueueRow(
    index: Int,
    track: MediaTrack,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    loading: Boolean = false,
) {
    val shape = RoundedCornerShape(Dimensions.innerBlockRadius)
    val scale by animateFloatAsState(if (dragging) Dimensions.queueDragScale else 1f, label = "queue-row-lift")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.minTouch)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (dragging) Dimensions.queueDragElevation else 0.dp, shape)
            .background(if (dragging) Card else Color.Transparent, shape),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Dimensions.queueThumbSize)) {
            ArtTile(
                background = browseCardColor(index),
                glyph = Res.drawable.music_note_filled,
                glyphSize = 24.dp,
                modifier = Modifier.fillMaxSize(),
                artworkUrl = track.artworkUrl,
            )
            if (loading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                        .background(ArtScrim.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = OnArt,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(Dimensions.queueThumbSize / 2),
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTrackTime(track.durationSec), color = Muted, fontSize = 14.sp)
    }
}
