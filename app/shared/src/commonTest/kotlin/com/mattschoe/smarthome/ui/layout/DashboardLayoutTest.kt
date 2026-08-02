package com.mattschoe.smarthome.ui.layout

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardLayoutTest {

    @Test
    fun belowBreakpoint_isCompact() {
        assertEquals(DashboardLayout.Compact, DashboardLayout.from(320.dp, 640.dp))
        assertEquals(DashboardLayout.Compact, DashboardLayout.from(599.dp, 900.dp))
    }

    @Test
    fun atOrAboveBreakpoint_isExpanded() {
        assertEquals(
            DashboardLayout.Expanded,
            DashboardLayout.from(DashboardLayout.compactMaxWidth, DashboardLayout.compactMaxWidth),
        )
        assertEquals(DashboardLayout.Expanded, DashboardLayout.from(1280.dp, 800.dp)) // Redmi Pad 2 landscape
        assertEquals(DashboardLayout.Expanded, DashboardLayout.from(800.dp, 1280.dp))
    }

    @Test
    fun tallerThanWide_isPortrait() {
        assertEquals(CompactArrangement.Portrait, CompactArrangement.from(390.dp, 844.dp)) // phone upright
        assertEquals(CompactArrangement.Portrait, CompactArrangement.from(399.dp, 400.dp))
    }

    @Test
    fun widerThanOrEqualToTall_isLandscape() {
        assertEquals(CompactArrangement.Landscape, CompactArrangement.from(844.dp, 390.dp)) // phone rotated
        // A square window has no long axis to page along; it resolves to Landscape by the >= rule.
        assertEquals(CompactArrangement.Landscape, CompactArrangement.from(400.dp, 400.dp))
    }
}
