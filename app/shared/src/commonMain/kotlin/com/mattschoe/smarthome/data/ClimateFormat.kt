package com.mattschoe.smarthome.data

import kotlin.math.roundToInt

/**
 * Pure display formatters for the read-only climate glance tiles, kept out of the composables so the
 * rounding/unit rules are unit-testable.
 */

/** Temperature to a whole-degree string, e.g. `21.5` -> `"22°"`. */
fun formatTemp(celsius: Double): String = "${celsius.roundToInt()}°"

/** Humidity percentage, e.g. `44` -> `"44%"`. */
fun formatHumidity(percent: Int): String = "$percent%"

/** Instantaneous power draw, kept at its source precision, e.g. `1.2` -> `"1.2 kW"`. */
fun formatEnergy(kw: Double): String = "$kw kW"
