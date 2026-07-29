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

    // Right-card Media panel (RightCard.kt). The now-playing surface (album art + scrubber +
    // transport + queue + playlists rail) and the idle browse surface (Quick Picks grid + Keep
    // Listening rail) share these tokens.
    val mediaSectionGap = 20.dp
    val albumArtSize = 132.dp
    val transportButtonSize = 64.dp
    val transportIconSize = 26.dp
    val playPauseIconSize = 30.dp
    val queueThumbSize = 56.dp
    // The over-art title label on a browse tile: a scrim band this tall, text inset by the padding.
    val artLabelHeight = 52.dp
    val artLabelPadding = 8.dp
    // Cover art is decoded at this multiple of the tile's measured size, so the final GPU downscale is
    // gentle instead of collapsing a full-resolution bitmap in one step. See ArtTile.
    val artOversample = 2
    // A picked-up (long-press dragged) up-next row: lifted onto a shadowed plate and barely scaled up,
    // so it reads as held above the rows it passes. See QueueSection.kt.
    val queueDragElevation = 8.dp
    val queueDragScale = 1.02f
    val playlistCardWidth = 150.dp
    val playlistCardHeight = 130.dp
    val scrubberTrackHeight = 6.dp
    val scrubberKnobDiameter = 14.dp
    // Quick-picks 3×3 grid + browse rail spacing, and the pager dot indicator.
    val browseGridSpacing = 12.dp
    val pageDotSize = 7.dp
    // The search field's inner row, sized so the trailing clear button is a full [minTouch] target
    // while the pill keeps the height a plain 16sp line gave it.
    val searchFieldRowHeight = minTouch
    val searchFieldPadV = 4.dp
    // Reserved for the search spinner / "no hits" line, so the surface doesn't jump between them.
    val searchStatusHeight = 120.dp
    // Collapsed now-playing: a Forest bar floating over the browse surface, plus the caret that
    // collapses the full surface into it. The elevation exceeds [cardElevation] so the bar reads as
    // hovering above the card rather than sitting in it.
    val miniPlayerHeight = 68.dp
    val miniPlayerRadius = 18.dp
    val miniPlayerBarPadding = 12.dp
    val miniPlayerElevation = 10.dp
    val miniPlayerIconSize = 22.dp
    // Its own thumb size rather than [queueThumbSize] — the bar is only [miniPlayerHeight] tall.
    val miniPlayerThumbSize = 44.dp
    val miniPlayerPlaySize = 40.dp
    val minimizeCaretSize = 20.dp

    // The browse-source badge beside the panel tabs (SourceToggle in RightCard.kt): the drawn disc,
    // inside a full [minTouch] target.
    val sourceBadgeSize = 32.dp

    // Artist drill-in surface (ArtistSurface in RightCard.kt): the header portrait and the back arrow
    // that leaves the surface, which is a plain full touch target rather than a drawn control.
    val artistArtSize = 140.dp
    val backButtonSize = minTouch
    val backIconSize = 24.dp
    // How far the centred glyph sits inside its touch target — the offset that re-aligns the arrow
    // with the content edge without shrinking the target.
    val backButtonInset = (backButtonSize - backIconSize) / 2
}
