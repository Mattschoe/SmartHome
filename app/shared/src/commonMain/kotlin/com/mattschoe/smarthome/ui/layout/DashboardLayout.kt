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
        /** Below this available width we treat the surface as a phone. Matches Material3's compact width class. */
        val compactMaxWidth: Dp = 600.dp

        /** Pure width -> mode mapping. Plain function (no Compose runtime) so it is unit-testable. */
        fun from(width: Dp): DashboardLayout =
            if (width < compactMaxWidth) Compact
            else Expanded
    }
}
