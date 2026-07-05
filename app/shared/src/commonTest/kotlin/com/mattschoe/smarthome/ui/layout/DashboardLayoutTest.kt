package com.mattschoe.smarthome.ui.layout

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardLayoutTest {

    @Test
    fun belowBreakpoint_isCompact() {
        assertEquals(DashboardLayout.Compact, DashboardLayout.from(320.dp))
        assertEquals(DashboardLayout.Compact, DashboardLayout.from(599.dp))
    }

    @Test
    fun atOrAboveBreakpoint_isExpanded() {
        assertEquals(DashboardLayout.Expanded, DashboardLayout.from(DashboardLayout.compactMaxWidth))
        assertEquals(DashboardLayout.Expanded, DashboardLayout.from(800.dp)) // Redmi Pad 2 landscape
        assertEquals(DashboardLayout.Expanded, DashboardLayout.from(1280.dp))
    }
}
