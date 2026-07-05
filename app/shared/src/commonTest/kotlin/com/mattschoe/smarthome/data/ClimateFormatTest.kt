package com.mattschoe.smarthome.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ClimateFormatTest {

    @Test
    fun temp_roundsToWholeDegrees() {
        assertEquals("22°", formatTemp(21.5))
        assertEquals("21°", formatTemp(21.4))
        assertEquals("24°", formatTemp(24.0))
        assertEquals("0°", formatTemp(-0.4))
    }

    @Test
    fun humidity_appendsPercent() {
        assertEquals("44%", formatHumidity(44))
        assertEquals("0%", formatHumidity(0))
        assertEquals("100%", formatHumidity(100))
    }

    @Test
    fun energy_keepsSourcePrecisionWithUnit() {
        assertEquals("1.2 kW", formatEnergy(1.2))
        assertEquals("2.0 kW", formatEnergy(2.0))
    }
}
