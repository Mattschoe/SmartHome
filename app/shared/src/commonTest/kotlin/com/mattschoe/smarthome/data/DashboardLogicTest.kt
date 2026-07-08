package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardLogicTest {

    // --- Brightness dial ---

    @Test
    fun brightnessFromAngle_boundaries() {
        assertEquals(100, brightnessFromAngle(0f))    // right end = full
        assertEquals(50, brightnessFromAngle(90f))    // top = half
        assertEquals(0, brightnessFromAngle(180f))    // left end = off
    }

    @Test
    fun brightnessFromAngle_clampsOutOfRange() {
        assertEquals(100, brightnessFromAngle(-30f))
        assertEquals(0, brightnessFromAngle(240f))
    }

    @Test
    fun angleFromBrightness_isInverse() {
        assertEquals(0f, angleFromBrightness(100), 0.001f)
        assertEquals(90f, angleFromBrightness(50), 0.001f)
        assertEquals(180f, angleFromBrightness(0), 0.001f)
    }

    @Test
    fun angleFromPointer_topHalfGeometry() {
        val cx = 130f
        val cy = 140f
        assertEquals(0f, angleFromPointer(cx, cy, px = 246f, py = 140f), 0.5f)   // due right
        assertEquals(90f, angleFromPointer(cx, cy, px = 130f, py = 24f), 0.5f)   // straight up
        assertEquals(180f, angleFromPointer(cx, cy, px = 14f, py = 140f), 0.5f)  // due left
    }

    @Test
    fun angleFromPointer_clampsBelowCenter() {
        // A touch below the dial center clamps to the nearest end rather than wrapping.
        assertEquals(0f, angleFromPointer(130f, 140f, px = 200f, py = 220f), 0.001f)   // below-right → 100% end
        assertEquals(180f, angleFromPointer(130f, 140f, px = 60f, py = 220f), 0.001f)  // below-left → 0% end
    }

    // --- Volume slider ---

    @Test
    fun volumeFractionFromX_clamps() {
        assertEquals(0.5f, volumeFractionFromX(x = 150f, left = 100f, width = 100f), 0.001f)
        assertEquals(0f, volumeFractionFromX(x = 50f, left = 100f, width = 100f), 0.001f)
        assertEquals(1f, volumeFractionFromX(x = 500f, left = 100f, width = 100f), 0.001f)
    }

    @Test
    fun volumeFractionFromX_zeroWidthIsSafe() {
        assertEquals(0f, volumeFractionFromX(x = 150f, left = 100f, width = 0f), 0.001f)
    }

    @Test
    fun volumeFromFraction_rounds() {
        assertEquals(0, volumeFromFraction(0f))
        assertEquals(50, volumeFromFraction(0.5f))
        assertEquals(100, volumeFromFraction(1.2f))
    }

    // --- State transitions ---

    @Test
    fun withBrightness_forcesLightOnAndCoerces() {
        val state = seedHome().withBrightness(Room.Bedroom, 150)
        val bedroom = state.rooms.getValue(Room.Bedroom)
        assertEquals(100, bedroom.brightnessPct)
        assertTrue(bedroom.isLightOn) // Bedroom seeded off; dragging forces it on.
        // Other rooms untouched.
        assertEquals(seedHome().rooms.getValue(Room.Kitchen), state.rooms.getValue(Room.Kitchen))
    }

    @Test
    fun withWarmth_recolorsAndTurnsOn() {
        val state = seedHome().withWarmth(Room.Bathroom, Warmth.Candle)
        val bathroom = state.rooms.getValue(Room.Bathroom)
        assertEquals(Warmth.Candle, bathroom.lightWarmth)
        assertTrue(bathroom.isLightOn)
    }

    @Test
    fun withVolume_onlyChangesVolume() {
        val before = seedHome().rooms.getValue(Room.Kitchen)
        val after = seedHome().withVolume(Room.Kitchen, 80).rooms.getValue(Room.Kitchen)
        assertEquals(80, after.volumePct)
        assertEquals(before.copy(volumePct = 80), after)
    }

    @Test
    fun toggleLight_flips() {
        val on = seedHome().rooms.getValue(Room.LivingRoom).isLightOn
        val toggled = seedHome().toggleLight(Room.LivingRoom).rooms.getValue(Room.LivingRoom).isLightOn
        assertEquals(!on, toggled)
    }

    @Test
    fun transitions_leaveClimateUntouched() {
        val seed = seedHome()
        val mutated = seed.withBrightness(Room.Hall, 10).withVolume(Room.Hall, 5).toggleLight(Room.Hall)
        assertEquals(seed.climate, mutated.climate)
        assertFalse(seed === mutated)
    }
}
