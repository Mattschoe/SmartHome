package com.mattschoe.smarthome.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app-level layout mode. Cards branch on this, never on a window-size API directly, so the seam
 * insulates the whole UI from how "how wide are we" is measured (currently a `BoxWithConstraints` at
 * the page root; a Material3 window-size-class could be swapped in behind [from] without touching any
 * card).
 *
 * [Expanded]: tablet, landscape
 * [Compact]: phone
 */
enum class DashboardLayout {
    Expanded,
    Compact;

    companion object {
        /** Below this smallest dimension we treat the surface as a phone. Matches Material3's compact width class. */
        val compactMaxWidth: Dp = 600.dp

        /**
         * Pure size -> mode mapping. Plain function (no Compose runtime) so it is unit-testable.
         *
         * The rule is on the **smallest** dimension (Android's `smallestScreenWidthDp` convention), so a
         * device keeps one mode through a rotation: a phone is Compact in both orientations, a tablet in
         * neither. Landing points: `1280×800 → Expanded`, `915×411 → Compact`, `411×915 → Compact`.
         */
        fun from(width: Dp, height: Dp): DashboardLayout =
            if (minOf(width, height) < compactMaxWidth) Compact
            else Expanded
    }
}

/**
 * Which phone arrangement the [DashboardLayout.Compact] branch draws: portrait is four horizontally
 * paged single-card screens, landscape three vertically paged two-card screens. Read from the window's
 * aspect ratio rather than a device orientation API, since the desktop window is freely resizable.
 * Square counts as [Landscape], matching the mockups' two-card frame.
 */
enum class CompactArrangement {
    Portrait,
    Landscape;

    companion object {
        fun from(width: Dp, height: Dp): CompactArrangement =
            if (width >= height) Landscape else Portrait
    }
}
