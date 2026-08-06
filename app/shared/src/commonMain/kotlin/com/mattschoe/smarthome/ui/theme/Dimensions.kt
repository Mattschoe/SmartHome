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

    // The phone's warmth rows (the same presets as the swatches above, laid out as a full-width list).
    // Selection thickens the border and adds the check badge; the row height never changes, so the list
    // doesn't reflow as the selection moves. Measured off mobile_phone_layout/vertical/homepage.png.
    val warmthRowHeight = 52.dp
    val warmthRowRadius = 14.dp
    val warmthRowGap = 10.dp
    val warmthRowInset = 15.dp
    val warmthRowDot = 26.dp
    val warmthRowCheck = 36.dp
    val warmthRowBorder = 1.dp
    val warmthRowSelectedBorder = 1.5.dp

    // Center-card volume slider (Audio section). The row keeps a [minTouch] hit height; the track is
    // a thin rounded lane with a white knob riding its center. See CenterCard.kt.
    val volumeTrackHeight = 7.dp
    val volumeKnobDiameter = 18.dp
    val volumeIconSize = 28.dp
    val volumeRowMinHeight = minTouch
    // Fixed width for the trailing "100%" label so the track (weight 1f) doesn't reflow as digits change.
    val volumePctLabelWidth = 48.dp

    // Right-card Calendar panel: the day-mark dots under a month-grid number. One per calendar with
    // something that day, laid out with a slight overlap so a busy day still fits inside its cell.
    val dayMarkDot = 5.dp
    val dayMarkOverlap = 1.5.dp
    val dayMarkRing = 0.5.dp
    // The disc carrying a day number, in the month grid and in the week view's day header.
    val calendarDayDisc = 34.dp

    // Right-card Calendar panel (week view). The grid is 24 [weekHourHeight] rows — far taller than
    // the ~500dp it is shown in, so it scrolls inside the panel — beside a gutter of hour labels.
    // Widths stay proportional (seven weight(1f) columns), so the same composables re-flow on a phone.
    // The hour row is deliberately tight: a day at a glance beats a legible 15 minutes, and a taller
    // row spends the card's height on empty morning hours.
    val weekHourHeight = 24.dp
    val weekTimeGutter = 34.dp
    val weekHourLabelHeight = 14.dp
    // An event block: its floor height, the leading bar in the calendar's full color, and the height
    // below which only the title fits (the start time is dropped rather than clipped).
    val weekMinBlockHeight = 14.dp
    val weekBlockRadius = 6.dp
    val weekBlockBarWidth = 3.dp
    val weekBlockGap = 2.dp
    val weekBlockPadding = 4.dp
    val weekBlockTimeMinHeight = 40.dp
    val weekAllDayChipHeight = 20.dp
    // The caret in the gutter that expands the all-day strip past its collapsed single row.
    val weekChevronSize = 18.dp
    val weekNowLineHeight = 2.dp
    // The to-do list under the week grid: its label plus two rows, the rest scrolling inside the
    // strip. Deliberately tight — every dp here is an hour the grid can't show, and the grid is what
    // the week view is for. The strip takes this height whatever the day holds, so switching day or
    // week doesn't resize the grid above it.
    val weekTodoStripHeight = 116.dp

    // Right-card Calendar popups (CalendarPopups.kt): the week view's event detail card and the
    // header gear's calendar-filter card, both floated inside the right card rather than over the
    // dashboard. Bounded rather than sized, so a short event's card is only as tall as it needs and a
    // long one scrolls instead of outgrowing the card it floats in.
    val eventDetailMaxWidth = 340.dp
    val eventDetailMaxHeight = 420.dp
    // The detail card's leading bar in the event's calendar colour — the week block's bar, scaled to
    // a 20sp title rather than an 11sp one — and the glyphs in a popup's action row.
    val eventDetailBarWidth = 4.dp
    val popupIconSize = 20.dp
    // How far under the card's top edge the gear's popup hangs, so it reads as dropped from the
    // header it was opened from rather than floating free.
    val calendarSettingsTopOffset = minTouch

    // Right-card Calendar panel (event editor, EventEditor.kt). The scroll wheels pick date and time
    // inline rather than through a picker dialog: [wheelVisibleRows] rows of [minTouch] each, so the
    // row above and below the selected one show what turning the wheel would land on.
    val wheelRowHeight = minTouch
    const val wheelVisibleRows = 3
    val wheelHeight = wheelRowHeight * wheelVisibleRows

    // The media kit (ui/controls/media/). The now-playing surface (album art + scrubber + transport +
    // queue + playlists rail) and the idle browse surface (Quick Picks grid + Keep Listening rail)
    // share these tokens, on the tablet card and the phone page alike.
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
    // Tiles per row in a browse/search grid — the same three on the card and on the phone page, where
    // the wider viewport lands a tile at nearly the card's tile size anyway.
    const val browseGridColumns = 3
    val pageDotSize = 7.dp
    val pageDotGap = 6.dp
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
    // On a phone page the bar floats *outside* the page's [phonePagePad] side margin, at this one — the
    // extra width is the track title's. On the tablet the bar sits inside the card's padding instead.
    val miniPlayerPageMargin = 12.dp

    // The browse-source badge beside the panel tabs (SourceToggle in RightCard.kt): the drawn disc,
    // inside a full [minTouch] target.
    val sourceBadgeSize = 32.dp

    // Artist drill-in surface (ArtistSurface.kt): the header portrait and the back arrow
    // that leaves the surface, which is a plain full touch target rather than a drawn control.
    val artistArtSize = 140.dp
    val backButtonSize = minTouch
    val backIconSize = 24.dp
    // How far the centred glyph sits inside its touch target — the offset that re-aligns the arrow
    // with the content edge without shrinking the target.
    val backButtonInset = (backButtonSize - backIconSize) / 2

    // Compact (phone) paging scaffold — see CompactDashboard.kt. Measured off the mockups under
    // app/docs/mobile_phone_layout/. Portrait is full-bleed cream with no card; only landscape frames
    // its two cards on the sage surface, which is why there is no portrait equivalent of the gap.
    val phoneSurfacePad = 14.dp
    val phoneCardGap = 24.dp
    val phonePagePad = 24.dp
    // A portrait page pads itself rather than being padded by the pager, so a control can still run to
    // the screen edge. [phonePageTopPad] sits under the status-bar inset; [phonePageBottomClearance]
    // keeps scrolling content from ending underneath the floating dot row.
    val phonePageTopPad = 16.dp
    val phonePageBottomClearance = 44.dp
    // The phone dial's box width, chosen so the scaled arc matches the mockup's radius. Clamped to the
    // page's content width on narrow screens.
    val phoneDialWidth = 300.dp
    // The portrait Music page. The art is a centered square (clamped to the page width on a narrow
    // phone) with the title block under it — deliberately smaller than the page could fit, so the up-next
    // queue below it shows a few rows rather than one. [audioPopupMaxWidth] bounds the card the speaker
    // button drops: where the music plays is a setting reached from the page, not a control on it.
    val phoneAlbumArtSize = 240.dp
    val phoneMediaTitleGap = 18.dp
    val audioPopupMaxWidth = 320.dp
    // The phone indicator's active mark is an elongated pill on the paging axis rather than a dot;
    // [pageDotSize] stays its thickness. The row/column floats over the page, inset from the edge it
    // hugs (bottom in portrait, right in landscape).
    val pageIndicatorActive = 22.dp
    val pageIndicatorGap = 7.dp
    val pageIndicatorInset = 16.dp
}
