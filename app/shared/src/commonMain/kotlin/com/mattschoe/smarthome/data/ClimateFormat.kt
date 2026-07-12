package com.mattschoe.smarthome.data

import kotlin.math.roundToInt

/**
 * Pure display formatters for the read-only climate glance tiles, kept out of the composables so the
 * rounding/unit rules are unit-testable. A `null` value (no backing sensor) renders as [NO_VALUE].
 */

/** Placeholder shown when a climate value has no backing sensor. */
const val NO_VALUE: String = "—"

/** Temperature to a whole-degree string, e.g. `21.5` -> `"22°"`; `null` -> `"—"`. */
fun formatTemp(celsius: Double?): String = celsius?.let { "${it.roundToInt()}°" } ?: NO_VALUE

/** Humidity percentage, e.g. `44` -> `"44%"`; `null` -> `"—"`. */
fun formatHumidity(percent: Int?): String = percent?.let { "$it%" } ?: NO_VALUE

/** Instantaneous power draw, kept at its source precision, e.g. `1.2` -> `"1.2 kW"`; `null` -> `"—"`. */
fun formatEnergy(kw: Double?): String = kw?.let { "$it kW" } ?: NO_VALUE
