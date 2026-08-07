package com.mattschoe.smarthome.ui.controls.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.QueueMode
import com.mattschoe.smarthome.ui.components.PopupCard
import com.mattschoe.smarthome.ui.components.PopupScrim
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Ink
import kotlin.math.roundToInt

/**
 * The queue gestures a browse tile carries where its surface offers them. [onOpenMenu] is the
 * long-press — the tile hands back its own bounds, so [BrowseItemMenu] can be placed beside it —
 * and [onEnqueue] is the same two actions reached without the gesture, from a screen reader.
 *
 * `null` on a surface that deliberately doesn't offer them: the artist drill-in's tiles play the
 * whole list from where they sit, which is not something to queue.
 */
data class BrowseQueueActions(
    val onOpenMenu: (BrowseItem, Rect) -> Unit,
    val onEnqueue: (BrowseItem, QueueMode) -> Unit,
)

/** What a long-pressed browse tile offers. The labels are the menu's rows, in this order. */
private val QueueActions = listOf(
    QueueMode.Next to "Afspil som næste",
    QueueMode.Last to "Tilføj til kø",
)

/**
 * The queue menu a browse tile's long-press drops: play the tile next, or add it to the end of what
 * has already been queued. A tap plays the tile, which is the only thing a tile could do before this;
 * everything the long-press adds lives here.
 *
 * Placed beside the pressed tile ([tileBounds], in root coordinates) inside the surface that opened
 * it — the box's own [boxOrigin]/[boxSize], likewise from the layout pass. It takes **the roomier
 * side** and is then clamped into the box rather than being placed only where it fits: neither the
 * right card (~344 dp of content) nor a phone page (~300 dp) has room for a menu beside a tile in
 * most columns, so overlapping the tile's neighbours is the normal case. That costs nothing — the
 * scrim has already taken the surface out of play.
 */
@Composable
fun BoxScope.BrowseItemMenu(
    tileBounds: Rect,
    boxOrigin: Offset,
    boxSize: IntSize,
    onEnqueue: (QueueMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val menuWidth = with(density) { Dimensions.browseMenuWidth.toPx() }
    val menuHeight = with(density) { Dimensions.browseMenuHeight.toPx() }

    // The tile, in the opening box's coordinate space. [boxOrigin] is the box's `positionInRoot`,
    // which is unclipped — its `boundsInRoot` would be cut off wherever the box is scrolled or
    // animated past an edge, and the menu would then be placed against the wrong rectangle.
    val left = tileBounds.left - boxOrigin.x
    val right = tileBounds.right - boxOrigin.x
    val x = if (boxSize.width - right >= left) right else left - menuWidth
    val y = tileBounds.top - boxOrigin.y

    PopupScrim(onDismiss)
    PopupCard(
        modifier = Modifier
            .align(Alignment.TopStart)
            // The px lambda, not the Dp overload: these are layout coordinates, and reading them at
            // placement keeps the menu off the recomposition path.
            .offset {
                IntOffset(
                    x.coerceIn(0f, (boxSize.width - menuWidth).coerceAtLeast(0f)).roundToInt(),
                    y.coerceIn(0f, (boxSize.height - menuHeight).coerceAtLeast(0f)).roundToInt(),
                )
            }
            .width(Dimensions.browseMenuWidth),
    ) {
        QueueActions.forEachIndexed { index, (mode, label) ->
            if (index > 0) HorizontalDivider(thickness = Dimensions.browseMenuDividerHeight, color = CardBorder)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimensions.minTouch)
                    .clickable {
                        onEnqueue(mode)
                        onDismiss()
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
