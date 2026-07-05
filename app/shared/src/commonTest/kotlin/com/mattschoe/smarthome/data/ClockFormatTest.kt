package com.mattschoe.smarthome.data

import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ClockFormatTest {

    private fun at(iso: String) = formatClock(Instant.parse(iso), TimeZone.UTC)

    @Test
    fun formats_referenceInstant_danish24h() {
        val text = at("2026-07-04T16:05:00Z")
        assertEquals("Lørdag", text.weekday)
        assertEquals("4. juli 2026", text.date)
        assertEquals("16:05", text.time)
    }

    @Test
    fun time_padsAndCoversMidnightAndNoon_24h() {
        assertEquals("00:00", at("2026-07-04T00:00:00Z").time)
        assertEquals("12:00", at("2026-07-04T12:00:00Z").time)
        assertEquals("09:07", at("2026-07-04T09:07:00Z").time)
        assertEquals("23:59", at("2026-07-04T23:59:00Z").time)
    }

    @Test
    fun time_12hMeridiem_atEdges() {
        fun t12(iso: String) =
            formatClock(Instant.parse(iso), TimeZone.UTC, use24h = false).time
        assertEquals("12:00 AM", t12("2026-07-04T00:00:00Z")) // midnight
        assertEquals("12:00 PM", t12("2026-07-04T12:00:00Z")) // noon
        assertEquals("4:05 PM", t12("2026-07-04T16:05:00Z"))
        assertEquals("9:07 AM", t12("2026-07-04T09:07:00Z"))
    }

    @Test
    fun weekday_selectsAcrossAFullWeek() {
        // 2026-07-04 is a Saturday; the next six days walk the rest of the week and wrap.
        val expected = listOf("Lørdag", "Søndag", "Mandag", "Tirsdag", "Onsdag", "Torsdag", "Fredag")
        val actual = (4..10).map { day -> at("2026-07-${p2(day)}T08:00:00Z").weekday }
        assertEquals(expected, actual)
    }

    @Test
    fun month_nameSelectedForEachMonth() {
        val months = listOf(
            "januar", "februar", "marts", "april", "maj", "juni",
            "juli", "august", "september", "oktober", "november", "december",
        )
        months.forEachIndexed { index, name ->
            assertEquals("15. $name 2026", at("2026-${p2(index + 1)}-15T08:00:00Z").date)
        }
    }
}

private fun p2(value: Int): String = value.toString().padStart(2, '0')
