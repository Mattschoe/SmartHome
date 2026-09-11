package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.ui.unit.dp
import com.mattschoe.smarthome.data.HoursPerDay
import com.mattschoe.smarthome.data.WeekHourHeightRange
import com.mattschoe.smarthome.ui.theme.Dimensions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekViewTest {

    @Test
    fun hourStride_showsEveryHourWhileTheyHaveRoom() {
        assertEquals(1, hourStride(Dimensions.weekHourHeightSafetyLimit))
        assertEquals(1, hourStride(Dimensions.weekHourLabelMinSpacing))
    }

    @Test
    fun hourStride_thinsOutAsTheHoursCloseUp() {
        assertEquals(2, hourStride(Dimensions.weekHourLabelMinSpacing - 1.dp))
        assertEquals(2, hourStride(12.dp))
        assertEquals(3, hourStride(Dimensions.weekHourHeightMin))
    }

    @Test
    fun hourStride_keepsTheLabelsApartAcrossTheWholeRange() {
        // The property the token states: whatever the zoom, two labelled hours are at least
        // [weekHourLabelMinSpacing] apart — that is what stops 24 of them collapsing into mush.
        var height = WeekHourHeightRange.start
        while (height <= WeekHourHeightRange.endInclusive) {
            val dp = height.dp
            assertTrue(
                dp * hourStride(dp) >= Dimensions.weekHourLabelMinSpacing,
                "labels collide at ${height}dp per hour",
            )
            height += 0.5f
        }
    }

    @Test
    fun hourStride_alwaysDividesTheDayEvenly() {
        // A stride the day isn't a multiple of would label 22:00 and then skip midnight's own rule.
        var height = WeekHourHeightRange.start
        while (height <= WeekHourHeightRange.endInclusive) {
            assertEquals(0, HoursPerDay % hourStride(height.dp), "ragged stride at ${height}dp")
            height += 0.5f
        }
    }

    @Test
    fun steppedHourHeight_usesProportionalChangesAcrossTheExpandedRange() {
        val bounds = 6f..480f

        assertEquals(36f, steppedHourHeight(24f, expand = true, bounds))
        assertEquals(240f, steppedHourHeight(360f, expand = false, bounds))
        assertEquals(480f, steppedHourHeight(400f, expand = true, bounds))
        assertEquals(6f, steppedHourHeight(6f, expand = false, bounds))
        assertEquals(480f, steppedHourHeight(480f, expand = true, bounds))
    }

    @Test
    fun steppedHourHeight_respectsTheViewportSpecificFitFloor() {
        val bounds = 17f..480f

        assertEquals(17f, steppedHourHeight(20f, expand = false, bounds))
        assertEquals(25.5f, steppedHourHeight(17f, expand = true, bounds))
        // A level left outside the range by a resize recovers immediately rather than taking a dead step.
        assertEquals(17f, steppedHourHeight(10f, expand = false, bounds))
    }

    @Test
    fun blockHeight_preservesTheBaselineFloorAndLetsDurationGrowBeyondIt() {
        assertEquals(3.5.dp, blockHeight(spanMinutes = 1, hourHeight = 6.dp))
        assertEquals(7.dp, blockHeight(spanMinutes = 1, hourHeight = 12.dp))
        assertEquals(14.dp, blockHeight(spanMinutes = 1, hourHeight = 24.dp))
        assertEquals(14.dp, blockHeight(spanMinutes = 1, hourHeight = 480.dp))
        assertEquals(240.dp, blockHeight(spanMinutes = 30, hourHeight = 480.dp))
    }
}
