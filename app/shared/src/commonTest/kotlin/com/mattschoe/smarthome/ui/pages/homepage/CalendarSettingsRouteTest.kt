package com.mattschoe.smarthome.ui.pages.homepage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The settings menu's back behaviour. No back stack is stored — [parent] *is* the stack — so this is
 * the whole of what the back arrow does, and the one place it can be checked.
 */
class CalendarSettingsRouteTest {

    @Test
    fun backSteps_oneLevelAtATime_thenOutOfTheSettings() {
        val calendar = CalendarSettingsRoute.Calendar("calendar.papkassehuset")

        assertEquals(CalendarSettingsRoute.Calendars, calendar.parent())
        // Back from the list closes the settings; the views come back.
        assertNull(CalendarSettingsRoute.Calendars.parent())
    }

    @Test
    fun depthOrdersTheLevels_soTheTransitionKnowsWhichWayToSlide() {
        assertEquals(0, CalendarSettingsRoute.Calendars.depth)
        assertEquals(1, CalendarSettingsRoute.Calendar("calendar.arbejde").depth)
    }
}
