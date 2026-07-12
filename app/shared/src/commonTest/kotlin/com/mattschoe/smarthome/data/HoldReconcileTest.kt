package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.Warmth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class HoldReconcileTest {

    // reconcileHold takes `now` explicitly, so deadlines are pinned relative to a single mark: no
    // real waiting, fully deterministic.
    private val now = TimeSource.Monotonic.markNow()
    private val future = now + 10.seconds // hold still armed
    private val past = now - 1.seconds    // hold timed out

    private fun room(
        brightness: Int = 0,
        on: Boolean = false,
        warmth: Warmth = Warmth.Neutral,
        volume: Int = 0,
        playing: Boolean = false,
        hasAudio: Boolean = true,
    ) = RoomState(
        brightnessPct = brightness,
        isLightOn = on,
        lightWarmth = warmth,
        audio = if (hasAudio) AudioState(volume, playing, null, 0, emptyList()) else null,
    )

    @Test
    fun interimEcho_belowTarget_holdsTheOptimisticValue() {
        // User set 80; HA is still echoing the pre-change 20. The held value must win — no snap.
        val hold = RoomHold(brightnessPct = 80, deadline = future)
        val (display, reduced) = reconcileHold(hold, room(brightness = 20), now)

        assertEquals(80, display.brightnessPct)
        assertEquals(80, reduced?.brightnessPct) // still held
    }

    @Test
    fun exactMatch_releasesEarly_andDropsField() {
        // HA reports the target → release now (before the deadline) so a later external change shows.
        val hold = RoomHold(brightnessPct = 80, deadline = future)
        val (display, reduced) = reconcileHold(hold, room(brightness = 80), now)

        assertEquals(80, display.brightnessPct)
        assertNull(reduced) // fully settled
    }

    @Test
    fun withinTolerance_releases_usingHaValue() {
        // Aggregate rounding leaves HA at 78 for a target of 80 (±2) → treated as reached.
        val hold = RoomHold(brightnessPct = 80, deadline = future)
        val (display, reduced) = reconcileHold(hold, room(brightness = 78), now)

        assertEquals(78, display.brightnessPct) // HA value, not the held target
        assertNull(reduced)
    }

    @Test
    fun justOutsideTolerance_keepsHolding() {
        val hold = RoomHold(brightnessPct = 80, deadline = future)
        val (display, reduced) = reconcileHold(hold, room(brightness = 77), now)

        assertEquals(80, display.brightnessPct)
        assertEquals(80, reduced?.brightnessPct)
    }

    @Test
    fun expiredDeadline_fallsBackToHa_evenWithoutMatch() {
        // Timeout backstop: the device never reached the target (e.g. failed / slow) → stop lying.
        val hold = RoomHold(brightnessPct = 80, deadline = past)
        val (display, reduced) = reconcileHold(hold, room(brightness = 20), now)

        assertEquals(20, display.brightnessPct)
        assertNull(reduced)
    }

    @Test
    fun exactFields_holdUntilEqual_thenRelease() {
        // Warmth + isPlaying are exact-match (no tolerance).
        val hold = RoomHold(lightWarmth = Warmth.Warm, isPlaying = true, deadline = future)

        val (heldDisplay, held) = reconcileHold(hold, room(warmth = Warmth.Neutral, playing = false), now)
        assertEquals(Warmth.Warm, heldDisplay.lightWarmth)
        assertEquals(true, heldDisplay.audio?.isPlaying)
        assertEquals(Warmth.Warm, held?.lightWarmth)
        assertEquals(true, held?.isPlaying)

        val (relDisplay, released) = reconcileHold(hold, room(warmth = Warmth.Warm, playing = true), now)
        assertEquals(Warmth.Warm, relDisplay.lightWarmth)
        assertNull(released)
    }

    @Test
    fun partialReduction_dropsSettledFieldsKeepsUnsettled() {
        // Brightness has converged (drop it) but warmth hasn't (keep it) → reduced hold survives, thinner.
        val hold = RoomHold(brightnessPct = 80, lightWarmth = Warmth.Warm, deadline = future)
        val (_, reduced) = reconcileHold(hold, room(brightness = 80, warmth = Warmth.Neutral), now)

        assertNull(reduced?.brightnessPct)
        assertEquals(Warmth.Warm, reduced?.lightWarmth)
    }

    @Test
    fun audioHoldOnSpeakerlessRoom_isIgnored() {
        // A volume hold on a room whose HA state has no audio must neither crash nor linger.
        val hold = RoomHold(brightnessPct = 60, volumePct = 40, deadline = future)
        val (display, reduced) = reconcileHold(hold, room(brightness = 10, hasAudio = false), now)

        assertNull(display.audio)
        assertEquals(60, display.brightnessPct) // light hold still applies
        assertNull(reduced?.volumePct)          // audio hold dropped
        assertEquals(60, reduced?.brightnessPct)
    }
}
