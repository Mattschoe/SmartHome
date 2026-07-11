package com.mattschoe.smarthome.data

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaFormatTest {

    @Test
    fun formatTrackTime_formatsMinutesAndSeconds() {
        assertEquals("0:00", formatTrackTime(0))
        assertEquals("0:09", formatTrackTime(9))
        assertEquals("1:52", formatTrackTime(112))
        assertEquals("4:03", formatTrackTime(243))
    }

    @Test
    fun formatTrackTime_clampsNegativeToZero() {
        assertEquals("0:00", formatTrackTime(-5))
    }
}
