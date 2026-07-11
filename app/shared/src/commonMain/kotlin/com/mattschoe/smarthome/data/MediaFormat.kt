package com.mattschoe.smarthome.data

/**
 * Pure, Compose-free formatter for media durations/positions. Mirrors `ClockFormat`/`ClimateFormat`:
 * a plain function so it stays multiplatform and unit-testable without platform locale calls.
 */

/** Format a whole-second duration/position as `"m:ss"` (e.g. 112 → "1:52", 0 → "0:00", 243 → "4:03"). */
fun formatTrackTime(sec: Int): String {
    val safe = sec.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
