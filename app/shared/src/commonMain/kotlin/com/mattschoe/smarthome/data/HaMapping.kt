package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Warmth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant

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

/**
 * The playback position *right now*, from HA's frozen pair: `media_position` is the position as of
 * `media_position_updated_at`, not of the current instant — HA only refreshes the pair on state
 * changes. While playing, the elapsed time since the stamp is added on; paused/idle (or with an
 * absent/unparseable stamp) the raw position is returned as-is.
 */
fun livePositionSec(positionSec: Int?, updatedAtIso: String?, isPlaying: Boolean, now: Instant): Int {
    val base = positionSec ?: 0
    if (!isPlaying || updatedAtIso == null) return base
    val updatedAt = runCatching { Instant.parse(updatedAtIso) }.getOrNull() ?: return base
    return base + (now - updatedAt).inWholeSeconds.coerceAtLeast(0L).toInt()
}

// Music Assistant's image proxy re-encodes to whatever `size` asks for; HA's entity_picture asks for
// 512. `size=0` means "the source thumbnail", which is the 600×600 the proxy caps at.
private val PROXY_SIZE = Regex("([?&]size=)\\d+")

/**
 * Raise an `entity_picture` art URL to the largest size its host will serve. Only Music Assistant's
 * `/imageproxy/` URLs carry a size the server honours, so every other URL is returned unchanged.
 * This is the HA-only fallback — with a Music Assistant connection the composite adapter overlays
 * MA's own (larger) source URL instead.
 */
fun String.atFullProxySize(): String =
    if ("/imageproxy/" in this) replace(PROXY_SIZE) { "${it.groupValues[1]}0" } else this

/** Domain [RepeatMode] -> HA `repeat_set` value. */
fun RepeatMode.toHaRepeat(): String = when (this) {
    RepeatMode.Off -> "off"
    RepeatMode.All -> "all"
}
