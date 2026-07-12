package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Warmth
import kotlin.test.Test
import kotlin.test.assertEquals

class HaMappingTest {

    @Test
    fun warmth_toKelvin_isMonotonicColdToWarm() {
        // Candle is the warmest light (lowest Kelvin), Cool the coolest (highest).
        assertEquals(2000, Warmth.Candle.toKelvin())
        assertEquals(5000, Warmth.Cool.toKelvin())
        val kelvins = Warmth.entries.map { it.toKelvin() }
        assertEquals(kelvins.sorted(), kelvins)
    }

    @Test
    fun warmthFromKelvin_snapsToNearestPreset() {
        assertEquals(Warmth.Candle, warmthFromKelvin(1900))
        assertEquals(Warmth.Warm, warmthFromKelvin(2650))
        assertEquals(Warmth.Soft, warmthFromKelvin(3100))
        assertEquals(Warmth.Neutral, warmthFromKelvin(3800))
        assertEquals(Warmth.Cool, warmthFromKelvin(6500))
    }

    @Test
    fun warmthFromKelvin_nullDefaultsToNeutral() {
        assertEquals(Warmth.Neutral, warmthFromKelvin(null))
    }

    @Test
    fun warmth_roundTripsThroughKelvin() {
        for (w in Warmth.entries) assertEquals(w, warmthFromKelvin(w.toKelvin()))
    }

    @Test
    fun brightness_255ToPctAndBack() {
        assertEquals(0, brightnessPctFrom255(0))
        assertEquals(100, brightnessPctFrom255(255))
        assertEquals(50, brightnessPctFrom255(128))
        assertEquals(0, brightnessPctFrom255(null))
        assertEquals(255, brightness255FromPct(100))
        assertEquals(0, brightness255FromPct(0))
    }

    @Test
    fun volume_levelToPctAndBack() {
        assertEquals(0, volumePctFromLevel(0.0))
        assertEquals(100, volumePctFromLevel(1.0))
        assertEquals(40, volumePctFromLevel(0.4))
        assertEquals(0, volumePctFromLevel(null))
        assertEquals(0.4, volumeLevelFromPct(40))
        assertEquals(1.0, volumeLevelFromPct(100))
    }

    @Test
    fun repeat_mapsBothWays() {
        assertEquals(RepeatMode.Off, repeatModeFromHa("off"))
        assertEquals(RepeatMode.All, repeatModeFromHa("all"))
        assertEquals(RepeatMode.All, repeatModeFromHa("one")) // one folds to All
        assertEquals(RepeatMode.Off, repeatModeFromHa(null))
        assertEquals("off", RepeatMode.Off.toHaRepeat())
        assertEquals("all", RepeatMode.All.toHaRepeat())
    }
}
