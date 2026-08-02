package com.mattschoe.smarthome.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageIndicatorTest {

    @Test
    fun settledOnAPage_onlyThatMarkIsCurrent() {
        assertEquals(1f, indicatorNearness(progress = 2f, index = 2))
        assertEquals(0f, indicatorNearness(progress = 2f, index = 1))
        assertEquals(0f, indicatorNearness(progress = 2f, index = 3))
    }

    @Test
    fun midSwipe_neighboursShareTheValue() {
        assertEquals(0.5f, indicatorNearness(progress = 1.5f, index = 1))
        assertEquals(0.5f, indicatorNearness(progress = 1.5f, index = 2))
        // A quarter of the way across, the mark being left keeps three quarters of the emphasis.
        assertEquals(0.75f, indicatorNearness(progress = 1.25f, index = 1))
        assertEquals(0.25f, indicatorNearness(progress = 1.25f, index = 2))
    }

    @Test
    fun marksBeyondTheNeighboursStayIdle() {
        assertEquals(0f, indicatorNearness(progress = 1.5f, index = 0))
        assertEquals(0f, indicatorNearness(progress = 1.5f, index = 3))
        assertEquals(0f, indicatorNearness(progress = 0f, index = 3))
    }

    /**
     * The invariant that keeps a centred indicator from jittering: interpolating each mark's length on
     * this value leaves the group's total length constant while the marks resize.
     */
    @Test
    fun nearnessSumsToOneAcrossTheRange() {
        val count = 4
        var progress = 0f
        while (progress <= count - 1f) {
            val total = (0 until count).sumOf { indicatorNearness(progress, it).toDouble() }
            assertTrue(
                abs(total - 1.0) < 1e-6,
                "sum was $total at progress $progress",
            )
            progress += 0.05f
        }
    }

    private fun abs(v: Double) = if (v < 0) -v else v
}
