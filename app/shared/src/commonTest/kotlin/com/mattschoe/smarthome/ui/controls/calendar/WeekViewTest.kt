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
        assertEquals(1, hourStride(Dimensions.weekHourHeightMax))
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
    fun steppedHourHeight_walksTheBreakpointsAndStopsAtTheEnds() {
        assertEquals(listOf(6f, 9f, 18f, 24f), WeekZoomSteps)

        assertEquals(18f, steppedHourHeight(24f, expand = false))
        assertEquals(9f, steppedHourHeight(18f, expand = false))
        assertEquals(6f, steppedHourHeight(9f, expand = false))
        assertEquals(6f, steppedHourHeight(6f, expand = false))

        assertEquals(9f, steppedHourHeight(6f, expand = true))
        assertEquals(24f, steppedHourHeight(18f, expand = true))
        assertEquals(24f, steppedHourHeight(24f, expand = true))
    }

    @Test
    fun steppedHourHeight_stepsOffALevelPinchedBetweenTwoBreakpoints() {
        // The pinch is continuous, so the screen-reader actions mostly start from somewhere between
        // two steps: each must move to the neighbouring one, never back to where it already is.
        assertEquals(18f, steppedHourHeight(13.5f, expand = true))
        assertEquals(9f, steppedHourHeight(13.5f, expand = false))
    }
}
