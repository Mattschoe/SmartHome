package com.mattschoe.smarthome.ui.theme

import androidx.compose.ui.unit.dp

/** Shared geometry tokens so no composable hardcodes a corner radius or hit-target size. */
object Dimensions {
    val cardRadius = 22.dp
    val innerBlockRadius = 16.dp
    val insetRadius = 12.dp
    val minTouch = 44.dp
    val scrollFadeHeight = 40.dp

    // Expanded-dashboard page geometry (fixed 1280×800 tablet). Only the `Expanded` assembly point
    // consumes these; size-agnostic composables must not hardcode layout numbers.
    val surfacePadV = 24.dp
    val surfacePadH = 26.dp
    val cardGap = 18.dp
    val leftCardWidth = 288.dp
}
