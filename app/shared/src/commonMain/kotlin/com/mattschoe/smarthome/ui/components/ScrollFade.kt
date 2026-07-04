package com.mattschoe.smarthome.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.mattschoe.smarthome.ui.theme.Card
import com.mattschoe.smarthome.ui.theme.Dimens

/**
 * Overlays a bottom fade (transparent → [color]) as a scroll affordance, without wrapping structure.
 * Apply to a scroll region so more-content-below reads clearly.
 */
fun Modifier.bottomScrollFade(color: Color = Card, height: Dp = Dimens.scrollFadeHeight): Modifier =
    this.drawWithContent {
        drawContent()
        val fadeH = height.toPx().coerceAtMost(size.height)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, color),
                startY = size.height - fadeH,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - fadeH),
            size = Size(size.width, fadeH),
        )
    }
