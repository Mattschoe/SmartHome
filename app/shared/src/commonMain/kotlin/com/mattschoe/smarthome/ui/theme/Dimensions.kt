package com.mattschoe.smarthome.ui.theme

import androidx.compose.ui.unit.dp

/** Shared geometry tokens so no composable hardcodes a corner radius or hit-target size. */
object Dimensions {
    val cardRadius = 22.dp
    val innerBlockRadius = 16.dp
    val insetRadius = 12.dp
    val minTouch = 44.dp
    val scrollFadeHeight = 40.dp

    // Soft elevation shadows. Cards/pills/swatches carry a subtle shadow (a deliberate departure from
    // the original "flat cards" spec) — see CardContainer/PillChip and CenterCard's warmth swatches.
    val cardElevation = 6.dp
    val pillElevation = 2.dp
    // Warmth swatches / dial want only the faintest lift — see Task feedback: their shadow reads best
    // barely-there, so the colored circles look scaled up rather than floating above the card.
    val swatchElevation = 1.dp

    // Expanded-dashboard page geometry (fixed 1280×800 tablet). Only the `Expanded` assembly point
    // consumes these; size-agnostic composables must not hardcode layout numbers.
    val surfacePadV = 24.dp
    val surfacePadH = 26.dp
    val cardGap = 18.dp
    // Wider breathing room around the center card's warmth↔divider↔Audio boundary than the default gap.
    val centerSectionGap = 28.dp
    val leftCardWidth = 288.dp

    // Center-card brightness dial (half-arc). Geometry follows the handoff spec's 260×160 viewBox
    // almost 1:1 in dp, since the app targets one fixed 1280×800 device rather than scaling a
    // responsive SVG — see CenterCard.kt.
    val centerDialWidth = 260.dp
    val centerDialHeight = 208.dp
    val centerDialCenterY = 140.dp
    val centerDialRadius = 116.dp
    val centerDialArcStroke = 17.dp
    val centerDialKnobDiameter = 24.dp
    val centerDialKnobStroke = 3.5.dp

    // Center "growth" bulb: a circle anchored by its bottom at [centerGrowthBaselineY] that scales
    // uniformly from min→max diameter with brightness (grows upward). Its fully-grown size is
    // reserved by [centerDialHeight] so the value text below never shifts. [centerBulbTapRadius] is
    // a fixed hit region for the toggle tap, independent of the current (possibly tiny) bulb size.
    val centerGrowthBaselineY = 150.dp
    val centerGrowthMinDiameter = 14.dp
    val centerGrowthMaxDiameter = 92.dp
    val centerBulbTapRadius = 40.dp

    // Center-card warmth swatches. The fill diameter is constant; the selected swatch adds a concentric
    // outer ring (gap + width) *around* the fill, so its footprint grows without shrinking the fill.
    val warmthSwatchDiameter = 46.dp
    val warmthHaloGap = 3.dp
    val warmthHaloRingWidth = 3.dp

    // Center-card volume slider (Audio section). The row keeps a [minTouch] hit height; the track is
    // a thin rounded lane with a white knob riding its center. See CenterCard.kt.
    val volumeTrackHeight = 7.dp
    val volumeKnobDiameter = 18.dp
    val volumeIconSize = 28.dp
    val volumeRowMinHeight = minTouch
    // Fixed width for the trailing "100%" label so the track (weight 1f) doesn't reflow as digits change.
    val volumePctLabelWidth = 48.dp
}
