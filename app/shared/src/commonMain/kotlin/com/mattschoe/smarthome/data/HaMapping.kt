package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Warmth
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure, device-free conversions between Home Assistant's units and the domain model. Kept out of the
 * adapter so every mapping rule is unit-testable without a live instance.
 */

/** Color temperature in Kelvin for each [Warmth] preset (coldest domain name -> warmest light). */
fun Warmth.toKelvin(): Int = when (this) {
    Warmth.Candle -> 2000
    Warmth.Warm -> 2700
    Warmth.Soft -> 3000
    Warmth.Neutral -> 4000
    Warmth.Cool -> 5000
}

/** Nearest [Warmth] preset for a light's `color_temp_kelvin`; `null` (unsupported) -> [Warmth.Neutral]. */
fun warmthFromKelvin(kelvin: Int?): Warmth {
    if (kelvin == null) return Warmth.Neutral
    return Warmth.entries.minBy { abs(it.toKelvin() - kelvin) }
}

/** HA brightness (0–255) -> domain brightness percent (0–100). `null` -> 0. */
fun brightnessPctFrom255(brightness: Int?): Int =
    brightness?.let { ((it / 255.0) * 100.0).roundToInt().coerceIn(0, 100) } ?: 0

/** Domain brightness percent (0–100) -> HA brightness (0–255), for `light.turn_on`. */
fun brightness255FromPct(pct: Int): Int = ((pct.coerceIn(0, 100) / 100.0) * 255.0).roundToInt()

/** HA `volume_level` (0.0–1.0) -> domain volume percent (0–100). `null` -> 0. */
fun volumePctFromLevel(level: Double?): Int =
    level?.let { (it.coerceIn(0.0, 1.0) * 100.0).roundToInt() } ?: 0

/** Domain volume percent (0–100) -> HA `volume_level` (0.0–1.0). */
fun volumeLevelFromPct(pct: Int): Double = pct.coerceIn(0, 100) / 100.0

/** HA `media_player` repeat state (`off`/`all`/`one`) -> domain [RepeatMode] (`one` folds to All). */
fun repeatModeFromHa(repeat: String?): RepeatMode =
    if (repeat == "all" || repeat == "one") RepeatMode.All else RepeatMode.Off

/** Domain [RepeatMode] -> HA `repeat_set` value. */
fun RepeatMode.toHaRepeat(): String = when (this) {
    RepeatMode.Off -> "off"
    RepeatMode.All -> "all"
}
