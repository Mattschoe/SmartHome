package com.mattschoe.smarthome.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.WeekHourHeightRange

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
    // A row is a bare bar in its warmth color — the color alone is the option, no dot/label/check
    // glyphs. Selection thickens and darkens its border; the row height never changes, so the list
    // doesn't reflow as the selection moves.
    val warmthRowHeight = 52.dp
    val warmthRowRadius = 14.dp
    val warmthRowGap = 10.dp
    val warmthRowInset = 15.dp
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
    // Under each week of the month grid, the band its multi-day events run their bars across (see
    // MonthView.kt). Reserved on every row rather than only the rows that need it: a page whose rows
    // changed height as the reader paged months would walk the grid up and down under their finger.
    val monthSpanLanes = 2
    val monthSpanBar = 4.dp
    val monthSpanGap = 2.dp
    // Held off the cell's edges, so two events sharing a lane on neighbouring days read as two bars.
    val monthSpanInset = 1.dp
    val monthSpanBand = (monthSpanBar + monthSpanGap) * monthSpanLanes
    // A month page: six rows of a [minTouch] cell over that band, gapped. Stated rather than wrapped,
    // because the grid pages inside a vertical scroll — a pager there has no height to measure
    // against, and every month is this tall anyway, so the agenda under it doesn't shift as the
    // months slide past.
    val monthGridRowGap = 2.dp
    val monthGridHeight = (minTouch + monthSpanBand) * 6 + monthGridRowGap * 5

    // Right-card Calendar panel (week view). The grid is 24 hour rows beside a gutter of hour labels.
    // Widths stay proportional (seven weight(1f) columns), so the same composables re-flow on a phone.
    // The hour row is a **range the reader sets** by pinching the grid, not a constant: at
    // [weekHourHeightMax] the day is 576dp and scrolls inside the card. The floor is not a token at
    // all — the grid fills the card, so collapsing stops the moment all 24 hours exactly fit the
    // height on hand, which `CalendarViews` computes from its own constraints. These two are the
    // persisted level's storage bounds (`WeekZoomStore`) and the ceiling that layout still honours.
    val weekHourHeightMin = WeekHourHeightRange.start.dp
    val weekHourHeightMax = WeekHourHeightRange.endInclusive.dp
    // Below this much room per hour, labels and rules thin out to every 2nd, 3rd… hour rather than
    // collapsing into mush. See `hourStride` in WeekView.kt, which this token alone tunes.
    val weekHourLabelMinSpacing = 18.dp
    val weekTimeGutter = 34.dp
    val weekHourLabelHeight = 14.dp
    // An event block: its floor height *at full expansion* (it scales down with the zoom, so a block
    // stays true to its minutes), the leading bar in the calendar's full color, and the padding its
    // text sits inside — which is also the budget the block's own line-fitting spends from.
    val weekMinBlockHeight = 14.dp
    val weekBlockRadius = 6.dp
    val weekBlockBarWidth = 3.dp
    val weekBlockGap = 2.dp
    val weekBlockPadding = 4.dp
    val weekBlockVerticalPadding = 2.dp
    val weekAllDayChipHeight = 20.dp
    // The caret in the gutter that expands the all-day strip past its collapsed single row.
    val weekChevronSize = 18.dp
    val weekNowLineHeight = 2.dp
    // A block picked up by a long press and dragged to a new slot (WeekView.kt). It rides on a
    // shadowed plate like the up-next row does, and the hours scroll on their own once it comes
    // within this much of the grid's top or bottom edge — a band wide enough to reach with the
    // finger still on the block, since the block itself is under it.
    val weekDragElevation = 8.dp
    val weekDragAutoScrollEdge = 44.dp

    /**
     * How far back an event written while the home was unreachable is drawn — the month agenda's rows
     * and the week's blocks alike. One restrained cue rather than a badge or a second colour: the
     * event *is* on the calendar as far as this device is concerned, it simply has not been agreed
     * with the home yet, and it stops looking unsent the moment the write goes out.
     */
    const val pendingWriteAlpha = 0.55f

    // Right-card Calendar popups (CalendarPopups.kt): the week view's event detail card and the
    // header gear's calendar-filter card, both floated inside the right card rather than over the
    // dashboard. Bounded rather than sized, so a short event's card is only as tall as it needs and a
    // long one scrolls instead of outgrowing the card it floats in.
    val eventDetailMaxWidth = 340.dp
    val eventDetailMaxHeight = 420.dp
    // The custom-frequency sheet (RecurrencePickerPopup) is the one popup that outgrows the detail
    // card's ceiling: an interval field, four units, seven weekday circles and three end rows. It is
    // still bounded — a short right card scrolls the sheet rather than letting it run off the edge.
    val recurrenceSheetMaxHeight = 560.dp
    // The weekday circles in that sheet: a full touch target, sized rather than padded so the seven
    // of them stay a row of equal circles at any width.
    val recurrenceDaySize = minTouch
    // The occurrence-count field on the sheet's "efter … gange" row: sized rather than stretched, so
    // the word after it stays beside the number instead of being pushed off the row.
    val recurrenceCountWidth = 88.dp
    // The detail card's leading bar in the event's calendar colour — the week block's bar, scaled to
    // a 20sp title rather than an 11sp one — and the glyphs in a popup's action row.
    val eventDetailBarWidth = 4.dp
    val popupIconSize = 20.dp
    // What every [PopupCard] insets its content by — stated here because a popup sized from its
    // content ([browseMenuHeight]) has to add it back.
    val popupCardPadding = 16.dp
    // The calendar settings surface. A colour swatch is smaller than the warmth dial's, because ten
    // of them have to wrap into the phone's narrower card without becoming three rows.
    val calendarColorSwatchDiameter = 32.dp
    val calendarColorHaloGap = 2.dp
    val calendarColorHaloRingWidth = 2.dp
    // A row that leads into a settings level. Taller than a bare touch target: these are read down
    // a list rather than aimed at, and the extra air is what makes the list scannable.
    val settingsRowHeight = 52.dp

    // The Opgaver panel's UDFØRT rule — the hairline on either side of the label that separates what
    // is still open from what has been ticked off.
    val todoDividerHeight = 1.dp

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
    // The queue menu a browse tile's long-press drops (BrowseItemMenu.kt): two rows on a [PopupCard].
    // Its height is *derived* from what it holds — two touch rows, the divider between them and the
    // card's own padding — rather than measured, so placing it against the surface's edges doesn't
    // pop a frame late.
    val browseMenuWidth = 200.dp
    val browseMenuDividerHeight = 1.dp
    val browseMenuHeight = minTouch * 2 + browseMenuDividerHeight + popupCardPadding * 2
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
    // The jump-to-today button beside the add-event "+" (TodayButton in CalendarViews.kt): the same
    // [sourceBadgeSize] footprint, rounded as a square rather than a disc so it reads as a date and
    // not as a second action button. Its outline is drawn at the weight the calendar grids ring the
    // selected day with, so the two read as the same line.
    val todayButtonRadius = 10.dp
    val todayButtonBorder = 1.5.dp

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
    // The landscape cards' inner padding: one value for the light/utility cards, and a tighter
    // horizontal inset for the music cards, which pack art + text + queue into the same width.
    // The speaker disc and its audio popup hang off [phoneCardPadding] too (see AudioPopup).
    val phoneCardPadding = 18.dp
    val phoneCardPaddingH = 14.dp
    // The landscape light card's room pills — a step down from the 44dp chips so the card's fixed
    // content (chips, dial, readout) fits without scrolling. 36dp still clears most touch guidance,
    // and it is what the fixed card needs; the tablet's chips are untouched.
    val phonePillMinHeight = 36.dp
    val phonePillHorizontalPadding = 12.dp
    val phonePillTextSize = 15.sp
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
    // The portrait dead zone: an empty strip below the Kalender and Opgaver content, above the dot
    // row, kept clear so a horizontal drag started there reaches the *page* pager. Both surfaces
    // otherwise run to the bottom clearance and both consume horizontal drags of their own — the week
    // grid pages by week, the checklist pages by day — which leaves a thumb nowhere on the screen to
    // swipe between pages from. Sized by feel, per surface: the calendar gives up grid height for it,
    // the checklist scrolls its rows under it. Tune these two, not the clearance, which the other
    // pages share.
    val phoneCalendarDeadZone = 84.dp
    val phoneTodoDeadZone = 84.dp
}
