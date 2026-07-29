package com.mattschoe.smarthome.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.mattschoe.smarthome.ui.theme.Card
import com.mattschoe.smarthome.ui.theme.Dimensions

/**
 * Overlays a [color] fade at whichever edge of [state] still has content beyond it, as a scroll
 * affordance and to stop content being sliced off at a hard edge. Each band fades in only when its
 * direction can actually scroll, so a region that fits its viewport gets no fade at all.
 *
 * [state] is a [ScrollableState], so this covers `ScrollState`, `LazyListState` and `PagerState` alike.
 *
 * Apply it **outside** the scroll — `Modifier.verticalScrollFade(s).verticalScroll(s)`, or as the
 * modifier handed to a lazy container. Chained after `verticalScroll` it measures against the content
 * rather than the viewport, and both bands land off-screen.
 */
@Composable
fun Modifier.verticalScrollFade(
    state: ScrollableState,
    color: Color = Card,
    height: Dp = Dimensions.scrollFadeHeight,
): Modifier {
    val top by animateFloatAsState(if (state.canScrollBackward) 1f else 0f, label = "scroll-fade-top")
    val bottom by animateFloatAsState(if (state.canScrollForward) 1f else 0f, label = "scroll-fade-bottom")
    return this.drawWithContent {
        drawContent()
        val fadeH = height.toPx().coerceAtMost(size.height)
        if (top > 0f) {
            drawRect(
                brush = Brush.verticalGradient(listOf(color, Color.Transparent), startY = 0f, endY = fadeH),
                topLeft = Offset.Zero,
                size = Size(size.width, fadeH),
                alpha = top,
            )
        }
        if (bottom > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, color),
                    startY = size.height - fadeH,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - fadeH),
                size = Size(size.width, fadeH),
                alpha = bottom,
            )
        }
    }
}
