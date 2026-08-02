package com.mattschoe.smarthome.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Muted
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** Which axis the indicator lays its marks along — matching the pager it belongs to. */
enum class PageIndicatorOrientation { Horizontal, Vertical }

/**
 * How "current" the mark at [index] is for a pager sitting at continuous position [progress]
 * (`currentPage + currentPageOffsetFraction`): 1 at its own page, falling linearly to 0 at either
 * neighbour. Mid-swipe two adjacent marks share the value, which is what makes the indicator morph
 * with the drag instead of flipping at the snap threshold.
 *
 * The values over all marks sum to 1 for any [progress] inside the pager's range, so interpolating
 * each mark's length on it leaves the group's total length constant — a centred indicator doesn't
 * shift while the marks resize.
 */
internal fun indicatorNearness(progress: Float, index: Int): Float =
    (1f - abs(progress - index)).coerceIn(0f, 1f)

/**
 * Pager position marks that track [state] continuously: each mark's length and color interpolate on
 * [indicatorNearness], so a slow drag shows the current mark shrinking toward a dot as its neighbour
 * grows, and releasing before the snap threshold morphs it back. Fling and tap already animate the
 * pager's own scroll, so no animation spec is needed here — the marks inherit that motion.
 *
 * Each mark also jumps to its page — the only non-drag way to page on the desktop target, where the
 * pager can't be dragged. The clickable stays inside the mark's own size so the indicator keeps its
 * exact footprint.
 *
 * [activeLength] stretches the current mark along the paging axis into a pill (the phone's look);
 * left null every mark stays a round [dotSize] dot that only crossfades in color (the tablet's Quick
 * Picks pager). Colors are parameters because the phone draws the same indicator on two different
 * backgrounds — cream in portrait, sage in landscape — and the idle mark has to stay visible on both.
 */
@Composable
fun PageIndicator(
    state: PagerState,
    modifier: Modifier = Modifier,
    orientation: PageIndicatorOrientation = PageIndicatorOrientation.Horizontal,
    activeColor: Color = Forest,
    idleColor: Color = Muted.copy(alpha = 0.5f),
    dotSize: Dp = Dimensions.pageDotSize,
    activeLength: Dp? = null,
    gap: Dp = Dimensions.pageDotGap,
) {
    val count = state.pageCount
    if (count <= 0) return
    val scope = rememberCoroutineScope()
    val longLength = activeLength ?: dotSize

    // A lambda, not a value: read in the layout/draw phases below so a swipe relayouts and redraws the
    // marks without recomposing them every frame.
    val nearness: (Int) -> Float =
        { i -> indicatorNearness(state.currentPage + state.currentPageOffsetFraction, i) }

    val marks: @Composable (Int) -> Unit = { i ->
        Box(
            Modifier
                .layout { measurable, _ ->
                    val long = lerp(dotSize, longLength, nearness(i)).roundToPx()
                    val short = dotSize.roundToPx()
                    val placeable = measurable.measure(
                        if (orientation == PageIndicatorOrientation.Horizontal) {
                            Constraints.fixed(long, short)
                        } else {
                            Constraints.fixed(short, long)
                        }
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .drawBehind {
                    drawRoundRect(
                        color = lerp(idleColor, activeColor, nearness(i)),
                        cornerRadius = CornerRadius(min(size.width, size.height) / 2f),
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { scope.launch { state.animateScrollToPage(i) } }
                .semantics { contentDescription = "Side ${i + 1}" },
        )
    }
    when (orientation) {
        PageIndicatorOrientation.Horizontal -> Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) { repeat(count) { marks(it) } }

        PageIndicatorOrientation.Vertical -> Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { repeat(count) { marks(it) } }
    }
}
