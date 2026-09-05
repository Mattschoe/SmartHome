package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.QueueMode
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardLogicTest {

    // --- Brightness dial ---

    @Test
    fun brightnessFromAngle_boundaries() {
        assertEquals(100, brightnessFromAngle(0f))    // right end = full
        assertEquals(50, brightnessFromAngle(90f))    // top = half
        assertEquals(0, brightnessFromAngle(180f))    // left end = off
    }

    @Test
    fun brightnessFromAngle_clampsOutOfRange() {
        assertEquals(100, brightnessFromAngle(-30f))
        assertEquals(0, brightnessFromAngle(240f))
    }

    @Test
    fun angleFromBrightness_isInverse() {
        assertEquals(0f, angleFromBrightness(100), 0.001f)
        assertEquals(90f, angleFromBrightness(50), 0.001f)
        assertEquals(180f, angleFromBrightness(0), 0.001f)
    }

    @Test
    fun angleFromPointer_topHalfGeometry() {
        val cx = 130f
        val cy = 140f
        assertEquals(0f, angleFromPointer(cx, cy, px = 246f, py = 140f), 0.5f)   // due right
        assertEquals(90f, angleFromPointer(cx, cy, px = 130f, py = 24f), 0.5f)   // straight up
        assertEquals(180f, angleFromPointer(cx, cy, px = 14f, py = 140f), 0.5f)  // due left
    }

    @Test
    fun angleFromPointer_clampsBelowCenter() {
        // A touch below the dial center clamps to the nearest end rather than wrapping.
        assertEquals(0f, angleFromPointer(130f, 140f, px = 200f, py = 220f), 0.001f)   // below-right → 100% end
        assertEquals(180f, angleFromPointer(130f, 140f, px = 60f, py = 220f), 0.001f)  // below-left → 0% end
    }

    // --- Volume slider ---

    @Test
    fun volumeFractionFromX_clamps() {
        assertEquals(0.5f, volumeFractionFromX(x = 150f, left = 100f, width = 100f), 0.001f)
        assertEquals(0f, volumeFractionFromX(x = 50f, left = 100f, width = 100f), 0.001f)
        assertEquals(1f, volumeFractionFromX(x = 500f, left = 100f, width = 100f), 0.001f)
    }

    @Test
    fun volumeFractionFromX_zeroWidthIsSafe() {
        assertEquals(0f, volumeFractionFromX(x = 150f, left = 100f, width = 0f), 0.001f)
    }

    @Test
    fun volumeFromFraction_rounds() {
        assertEquals(0, volumeFromFraction(0f))
        assertEquals(50, volumeFromFraction(0.5f))
        assertEquals(100, volumeFromFraction(1.2f))
    }

    // --- State transitions ---

    @Test
    fun withBrightness_forcesLightOnAndCoerces() {
        val state = seedHome().withBrightness(Room.Bedroom, 150)
        val bedroom = state.rooms.getValue(Room.Bedroom)
        assertEquals(100, bedroom.brightnessPct)
        assertTrue(bedroom.isLightOn) // Bedroom seeded off; dragging forces it on.
        // Other rooms untouched.
        assertEquals(seedHome().rooms.getValue(Room.Kitchen), state.rooms.getValue(Room.Kitchen))
    }

    @Test
    fun withWarmth_recolorsAndTurnsOn() {
        val state = seedHome().withWarmth(Room.Bathroom, Warmth.Candle)
        val bathroom = state.rooms.getValue(Room.Bathroom)
        assertEquals(Warmth.Candle, bathroom.lightWarmth)
        assertTrue(bathroom.isLightOn)
    }

    @Test
    fun withVolume_onlyChangesNestedVolume() {
        val before = seedHome().rooms.getValue(Room.Bedroom)
        val after = seedHome().withVolume(Room.Bedroom, 80).rooms.getValue(Room.Bedroom)
        assertEquals(80, after.audio?.volumePct)
        // Only the audio volume changed; the light and the rest of the audio session are untouched.
        assertEquals(before.copy(audio = before.audio?.copy(volumePct = 80)), after)
    }

    @Test
    fun toggleLight_flips() {
        val on = seedHome().rooms.getValue(Room.LivingRoom).isLightOn
        val toggled = seedHome().toggleLight(Room.LivingRoom).rooms.getValue(Room.LivingRoom).isLightOn
        assertEquals(!on, toggled)
    }

    @Test
    fun transitions_leaveClimateUntouched() {
        val seed = seedHome()
        val mutated = seed.withBrightness(Room.Hall, 10).withVolume(Room.Hall, 5).toggleLight(Room.Hall)
        assertEquals(seed.climate, mutated.climate)
        assertFalse(seed === mutated)
    }

    // --- Audio transport transitions ---

    @Test
    fun togglePlay_flipsIsPlaying() {
        val before = seedHome().rooms.getValue(Room.LivingRoom).audio!!.isPlaying
        val after = seedHome().togglePlay(Room.LivingRoom).rooms.getValue(Room.LivingRoom).audio!!.isPlaying
        assertEquals(!before, after)
    }

    @Test
    fun next_rotatesQueueRoundRobin() {
        val before = seedHome().rooms.getValue(Room.LivingRoom).audio!!
        val after = seedHome().next(Room.LivingRoom).rooms.getValue(Room.LivingRoom).audio!!
        assertEquals(before.queue.first(), after.nowPlaying)          // head becomes now-playing
        assertEquals(before.queue.drop(1) + before.nowPlaying!!, after.queue) // old current to tail
        assertEquals(0, after.positionSec)
    }

    @Test
    fun previous_restartsWhenPastThreeSeconds() {
        // Seed LivingRoom position is 112s (> 3s) → restart current, keep the queue.
        val before = seedHome().rooms.getValue(Room.LivingRoom).audio!!
        val after = seedHome().previous(Room.LivingRoom).rooms.getValue(Room.LivingRoom).audio!!
        assertEquals(before.nowPlaying, after.nowPlaying)
        assertEquals(before.queue, after.queue)
        assertEquals(0, after.positionSec)
    }

    @Test
    fun previous_rotatesBackWhenNearStart() {
        val seeded = seedHome().seek(Room.LivingRoom, 2)              // 2s ≤ 3s → rotate back
        val before = seeded.rooms.getValue(Room.LivingRoom).audio!!
        val after = seeded.previous(Room.LivingRoom).rooms.getValue(Room.LivingRoom).audio!!
        assertEquals(before.queue.last(), after.nowPlaying)          // queue tail becomes now-playing
        assertEquals(listOf(before.nowPlaying!!) + before.queue.dropLast(1), after.queue)
        assertEquals(0, after.positionSec)
    }

    @Test
    fun playQueueItem_skipsToTheEntryAndRotatesWhatItJumpedOver() {
        // Fixtures carry no MA handle, so the title is the queue key. Skip to the third entry.
        val before = seedHome().rooms.getValue(Room.LivingRoom).audio!!
        val target = before.queue[2]
        val after = seedHome().playQueueItem(Room.LivingRoom, target.title)
            .rooms.getValue(Room.LivingRoom).audio!!
        assertEquals(target, after.nowPlaying)
        assertEquals(listOf(before.nowPlaying!!, before.queue[0], before.queue[1]), after.queue)
        assertEquals(0, after.positionSec)
    }

    @Test
    fun playQueueItem_isANoOpForAnUnknownHandle() {
        val seed = seedHome()
        assertEquals(seed, seed.playQueueItem(Room.LivingRoom, "not-in-the-queue"))
    }

    @Test
    fun playBrowseItem_promotesTheTileToNowPlayingAndStartsPlayback() {
        val seed = seedHome()
        val tile = seed.quickPicks.first()
        val after = seed.playBrowseItem(Room.Bedroom, tile).rooms.getValue(Room.Bedroom).audio!!
        assertEquals(tile.name, after.nowPlaying?.title)
        assertEquals(tile.subtitle, after.nowPlaying?.artist)
        assertEquals(tile.uri, after.nowPlaying?.uri)
        assertTrue(after.isPlaying)
        assertEquals(0, after.positionSec)
        // A play replaces the queue: the previous track's rows are gone, and the mock's instant
        // "continuation" (standing in for Don't-Stop-the-Music) never includes the played item.
        assertTrue(after.queue.isNotEmpty())
        assertTrue(after.queue.none { it.uri == tile.uri })
    }

    @Test
    fun enqueueBrowseItem_ordersTheUserBlockAndLeavesPlaybackAlone() {
        val seed = seedHome()
        val (a, b, c) = seed.quickPicks
        val playing = seed.rooms.getValue(Room.LivingRoom).audio!!
        val continuations = playing.queue

        // "Tilføj til kø" A, then B, then "Afspil som næste" C.
        val after = seed
            .enqueueBrowseItem(Room.LivingRoom, a, QueueMode.Last)
            .enqueueBrowseItem(Room.LivingRoom, b, QueueMode.Last)
            .enqueueBrowseItem(Room.LivingRoom, c, QueueMode.Next)
            .rooms.getValue(Room.LivingRoom).audio!!

        // C at the top of the block, A and B in the order they were queued, then what was already on.
        assertEquals(
            listOf(c.name, a.name, b.name) + continuations.map { it.title },
            after.queue.map { it.title },
        )
        // Queueing is not playing: the track, its position and the playing flag are all untouched.
        assertEquals(playing.nowPlaying, after.nowPlaying)
        assertEquals(playing.positionSec, after.positionSec)
        assertTrue(after.isPlaying)
    }

    @Test
    fun enqueueBrowseItem_replacesTheBlockOnTheNextPlay() {
        val seed = seedHome()
        val tile = seed.quickPicks.first()
        val queued = seed.enqueueBrowseItem(Room.LivingRoom, tile, QueueMode.Last)
        // A play replaces the queue, so the block it minted is gone — the next enqueue starts one anew
        // at the head rather than behind rows that are no longer there.
        val after = queued
            .playBrowseItem(Room.LivingRoom, seed.quickPicks[1])
            .enqueueBrowseItem(Room.LivingRoom, seed.quickPicks[2], QueueMode.Last)
            .rooms.getValue(Room.LivingRoom).audio!!
        assertEquals(seed.quickPicks[2].name, after.queue.first().title)
    }

    @Test
    fun moveQueueItem_shiftsRelativelyAndClampsToTheQueue() {
        val before = seedHome().rooms.getValue(Room.LivingRoom).audio!!.queue
        val moved = seedHome().moveQueueItem(Room.LivingRoom, before[0].title, 2)
            .rooms.getValue(Room.LivingRoom).audio!!.queue
        assertEquals(listOf(before[1], before[2], before[0]), moved)

        // Overshooting either end lands on it rather than throwing.
        val clamped = seedHome().moveQueueItem(Room.LivingRoom, before[2].title, -9)
            .rooms.getValue(Room.LivingRoom).audio!!.queue
        assertEquals(listOf(before[2], before[0], before[1]), clamped)
    }

    @Test
    fun seek_clampsToTrackBounds() {
        val duration = seedHome().rooms.getValue(Room.LivingRoom).audio!!.nowPlaying!!.durationSec
        assertEquals(0, seedHome().seek(Room.LivingRoom, -10).rooms.getValue(Room.LivingRoom).audio!!.positionSec)
        assertEquals(duration, seedHome().seek(Room.LivingRoom, duration + 100).rooms.getValue(Room.LivingRoom).audio!!.positionSec)
    }

    @Test
    fun setShuffleAndRepeat_apply() {
        val shuffled = seedHome().setShuffle(Room.LivingRoom, true)
        assertTrue(shuffled.rooms.getValue(Room.LivingRoom).audio!!.isShuffle)
        val repeated = seedHome().setRepeat(Room.LivingRoom, RepeatMode.All)
        assertEquals(RepeatMode.All, repeated.rooms.getValue(Room.LivingRoom).audio!!.repeat)
    }

    @Test
    fun repeatMode_cycleOrder() {
        assertEquals(RepeatMode.All, RepeatMode.Off.cycle())
        assertEquals(RepeatMode.Off, RepeatMode.All.cycle())
    }

    // --- Sync groups ---

    @Test
    fun joinAudio_pointsBothRoomsAtTheLeaderAndReadsAsJoined() {
        val joined = seedHome().joinAudio(leader = Room.LivingRoom, follower = Room.Bedroom)

        assertEquals(Room.LivingRoom, joined.rooms.getValue(Room.LivingRoom).audio!!.syncLeader)
        assertEquals(Room.LivingRoom, joined.rooms.getValue(Room.Bedroom).audio!!.syncLeader)
        assertTrue(joined.rooms.audioJoined(Room.LivingRoom, Room.Bedroom))
        // Joining is grouping only — it doesn't touch either room's playback or volume.
        val seededLiving = seedHome().rooms.getValue(Room.LivingRoom).audio!!
        assertEquals(
            seededLiving.copy(syncLeader = Room.LivingRoom),
            joined.rooms.getValue(Room.LivingRoom).audio,
        )
    }

    @Test
    fun unjoinAudio_onTheFollowerLeavesTheLeaderPlaying() {
        val joined = seedHome().joinAudio(leader = Room.LivingRoom, follower = Room.Bedroom)
        val left = joined.unjoinAudio(Room.Bedroom)

        assertNull(left.rooms.getValue(Room.Bedroom).audio!!.syncLeader)
        // The leader is alone now, so it is no longer in a group either — but it kept playing.
        assertNull(left.rooms.getValue(Room.LivingRoom).audio!!.syncLeader)
        assertEquals(
            seedHome().rooms.getValue(Room.LivingRoom).audio!!.isPlaying,
            left.rooms.getValue(Room.LivingRoom).audio!!.isPlaying,
        )
        assertFalse(left.rooms.audioJoined(Room.LivingRoom, Room.Bedroom))
    }

    @Test
    fun unjoinAudio_onTheLeaderDissolvesTheGroup() {
        val joined = seedHome().joinAudio(leader = Room.LivingRoom, follower = Room.Bedroom)
        val dissolved = joined.unjoinAudio(Room.LivingRoom)

        assertNull(dissolved.rooms.getValue(Room.LivingRoom).audio!!.syncLeader)
        assertNull(dissolved.rooms.getValue(Room.Bedroom).audio!!.syncLeader)
        assertFalse(dissolved.rooms.audioJoined(Room.LivingRoom, Room.Bedroom))
    }

    @Test
    fun audioSession_ofAFollowerIsTheGroupsWithItsOwnVolume() {
        val joined = seedHome()
            .withVolume(Room.Bedroom, 12)
            .joinAudio(leader = Room.LivingRoom, follower = Room.Bedroom)
        val leaderAudio = joined.rooms.getValue(Room.LivingRoom).audio!!

        // The follower is addressed as, and plays, the group's session…
        assertEquals(Room.LivingRoom, joined.rooms.audioSessionRoom(Room.Bedroom))
        val session = joined.rooms.audioSessionOf(Room.Bedroom)!!
        assertEquals(leaderAudio.nowPlaying, session.nowPlaying)
        assertEquals(leaderAudio.queue, session.queue)
        assertEquals(leaderAudio.isPlaying, session.isPlaying)
        // …but the volume is the speaker's own, not the leader's.
        assertEquals(12, session.volumePct)
    }

    @Test
    fun audioSession_ofAnUngroupedRoomIsItsOwn() {
        val home = seedHome()
        // A room playing alone leads nothing and follows nothing — it is its own session, unchanged.
        assertEquals(Room.Bedroom, home.rooms.audioSessionRoom(Room.Bedroom))
        assertEquals(home.rooms.getValue(Room.Bedroom).audio, home.rooms.audioSessionOf(Room.Bedroom))
        // The leader of a group is likewise its own session.
        val joined = home.joinAudio(leader = Room.LivingRoom, follower = Room.Bedroom)
        assertEquals(Room.LivingRoom, joined.rooms.audioSessionRoom(Room.LivingRoom))
        // A speaker-less room has no session at all.
        assertNull(home.rooms.audioSessionOf(Room.Kitchen))
    }

    @Test
    fun audioJoined_isFalseForUngroupedRooms() {
        // Two rooms playing alone (null leaders) are not joined — null must not match null.
        assertFalse(seedHome().rooms.audioJoined(Room.LivingRoom, Room.Bedroom))
        // Nor is a speaker-less room ever joined to anything.
        assertFalse(seedHome().rooms.audioJoined(Room.LivingRoom, Room.Kitchen))
    }

    // --- Calendar grid ---

    @Test
    fun calendarGrid_structureMatchesMonth() {
        val year = 2026
        val month = 7
        val grid = calendarGrid(year, month)
        assertEquals(42, grid.size)

        val first = LocalDate(year, month, 1)
        val leading = first.dayOfWeek.isoDayNumber - 1
        val daysInMonth = first.daysUntil(first.plus(1, DateTimeUnit.MONTH))

        repeat(leading) { assertNull(grid[it]) }                       // leading blanks
        for (d in 1..daysInMonth) assertEquals(d, grid[leading + d - 1]) // days in order
        for (i in leading + daysInMonth until 42) assertNull(grid[i])  // trailing blanks
        assertEquals(daysInMonth, grid.count { it != null })
    }

    @Test
    fun calendarGrid_isMondayFirst() {
        // 1 Feb 2021 is a Monday → zero leading blanks; February 2021 has 28 days.
        val grid = calendarGrid(2021, 2)
        assertEquals(1, grid[0])
        assertEquals(28, grid[27])
        assertNull(grid[28])
    }

    // --- Calendar paging ---

    @Test
    fun calendarWindow_spansTheFetchedMonthsAroundToday() {
        val today = LocalDate(2026, 8, 7)
        val window = calendarWindow(today)
        assertEquals(LocalDate(2026, 7, 7), window.start)
        assertEquals(LocalDate(2027, 8, 7), window.endInclusive)
        assertTrue(today in window)
    }

    @Test
    fun monthPaging_isAOneToOneMapBetweenMonthsAndPages() {
        val window = calendarWindow(LocalDate(2026, 8, 7))
        // July 2026 through August 2027 inclusive.
        assertEquals(14, monthPageCount(window))
        assertEquals(0, monthIndexOf(window, LocalDate(2026, 7, 31)))
        assertEquals(1, monthIndexOf(window, LocalDate(2026, 8, 7)))
        assertEquals(6, monthIndexOf(window, LocalDate(2027, 1, 1)))    // across the year boundary
        assertEquals(LocalDate(2027, 1, 1), monthAtPage(window, 6))
        // Every page round-trips to its own month, pinned to the 1st.
        for (page in 0 until monthPageCount(window)) {
            assertEquals(page, monthIndexOf(window, monthAtPage(window, page)))
            assertEquals(1, monthAtPage(window, page).day)
        }
    }

    @Test
    fun weekPaging_isAOneToOneMapBetweenWeeksAndPages() {
        val window = calendarWindow(LocalDate(2026, 8, 7))
        assertEquals(weekStart(window.start), weekAtPage(window, 0))
        assertEquals(0, weekIndexOf(window, window.start))
        // A week on is a page on, and every page lands on its own Monday.
        assertEquals(1, weekIndexOf(window, window.start.plus(7, DateTimeUnit.DAY)))
        for (page in 0 until weekPageCount(window)) {
            val monday = weekAtPage(window, page)
            assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
            assertEquals(page, weekIndexOf(window, monday))
        }
        // The last page is the week the window's end falls in — nothing beyond it, nothing short of it.
        assertEquals(weekStart(window.endInclusive), weekAtPage(window, weekPageCount(window) - 1))
    }

    @Test
    fun dayPaging_isAOneToOneMapBetweenDaysAndPages() {
        val window = calendarWindow(LocalDate(2026, 8, 7))
        assertEquals(window.start, dayAtPage(window, 0))
        assertEquals(0, dayIndexOf(window, window.start))
        assertEquals(1, dayIndexOf(window, window.start.plus(1, DateTimeUnit.DAY)))
        // Both ends are pages of their own — the window is inclusive.
        assertEquals(window.endInclusive, dayAtPage(window, dayPageCount(window) - 1))
        assertEquals(dayPageCount(window) - 1, dayIndexOf(window, window.endInclusive))
        for (page in 0 until dayPageCount(window)) {
            assertEquals(page, dayIndexOf(window, dayAtPage(window, page)))
        }
    }

    @Test
    fun paging_clampsDatesFromOutsideTheWindow() {
        // A cached event from outside the fetched span must not produce a page the pager has no room
        // for — in either direction.
        val window = calendarWindow(LocalDate(2026, 8, 7))
        assertEquals(0, monthIndexOf(window, LocalDate(2020, 1, 1)))
        assertEquals(monthPageCount(window) - 1, monthIndexOf(window, LocalDate(2030, 1, 1)))
        assertEquals(0, weekIndexOf(window, LocalDate(2020, 1, 1)))
        assertEquals(weekPageCount(window) - 1, weekIndexOf(window, LocalDate(2030, 1, 1)))
        assertEquals(0, dayIndexOf(window, LocalDate(2020, 1, 1)))
        assertEquals(dayPageCount(window) - 1, dayIndexOf(window, LocalDate(2030, 1, 1)))
        // And a page index from outside the range clamps the same way rather than throwing.
        assertEquals(monthAtPage(window, 0), monthAtPage(window, -1))
        assertEquals(weekAtPage(window, 0), weekAtPage(window, -1))
        assertEquals(dayAtPage(window, 0), dayAtPage(window, -1))
    }

    // --- Week view ---

    @Test
    fun weekStart_isTheMondayOfThatWeek() {
        val monday = LocalDate(2026, 7, 27)
        assertEquals(monday, weekStart(monday))                     // a Monday is its own week start
        assertEquals(monday, weekStart(LocalDate(2026, 7, 29)))     // Wednesday
        assertEquals(monday, weekStart(LocalDate(2026, 8, 2)))      // Sunday closes the same week
    }

    @Test
    fun weekStart_crossesMonthAndYearBoundaries() {
        // 1 Jan 2027 is a Friday, so its week began in the previous December.
        assertEquals(LocalDate(2026, 12, 28), weekStart(LocalDate(2027, 1, 1)))
    }

    @Test
    fun isoWeekNumber_isTheSameForEveryDayOfTheWeek() {
        val monday = LocalDate(2026, 8, 31)
        assertEquals(36, isoWeekNumber(monday))
        for (offset in 0 until DaysPerWeek) {
            assertEquals(36, isoWeekNumber(monday.plus(offset, DateTimeUnit.DAY)))
        }
        assertEquals(37, isoWeekNumber(monday.plus(DaysPerWeek, DateTimeUnit.DAY)))
    }

    @Test
    fun isoWeekNumber_countsTheNewYearStraddleByItsThursday() {
        // Week 1 of 2026 is the one holding Thursday 1 Jan — it opens in the previous December and
        // carries that whole week, both sides of the year boundary.
        assertEquals(1, isoWeekNumber(LocalDate(2025, 12, 29)))  // Monday, still December
        assertEquals(1, isoWeekNumber(LocalDate(2026, 1, 1)))
        assertEquals(1, isoWeekNumber(LocalDate(2026, 1, 4)))    // Sunday closes week 1
        assertEquals(2, isoWeekNumber(LocalDate(2026, 1, 5)))
    }

    @Test
    fun isoWeekNumber_reaches53InALongYear() {
        // 2026 opens on a Thursday, so it runs to 53 weeks: the last begins 28 Dec and spills into
        // January, whose own week 1 only starts on the Monday after.
        assertEquals(53, isoWeekNumber(LocalDate(2026, 12, 28)))
        assertEquals(53, isoWeekNumber(LocalDate(2027, 1, 3)))
        assertEquals(1, isoWeekNumber(LocalDate(2027, 1, 4)))
        // And in a leap year, where the deciding Thursday is day 366.
        assertEquals(53, isoWeekNumber(LocalDate(2021, 1, 1)))
    }

    @Test
    fun layoutDayEvents_givesNonOverlappingEventsTheFullColumn() {
        val placed = layoutDayEvents(listOf(timedEvent("A", 540, 600), timedEvent("B", 660, 720)))
        assertEquals(listOf(0, 0), placed.map { it.lane })
        assertTrue(placed.all { it.laneCount == 1 })
    }

    @Test
    fun layoutDayEvents_splitsOverlappingEventsIntoLanes() {
        val placed = layoutDayEvents(listOf(timedEvent("A", 540, 660), timedEvent("B", 600, 720)))
        assertEquals(listOf(0, 1), placed.map { it.lane })
        assertTrue(placed.all { it.laneCount == 2 })
    }

    @Test
    fun layoutDayEvents_stampsAClusterWithItsOwnLaneCount() {
        // A—B and B—C overlap, A—C do not: one cluster of three, but only two lanes, and C reuses
        // the lane A vacated. Every member reports the cluster's count, so the column splits evenly.
        val placed = layoutDayEvents(
            listOf(timedEvent("A", 540, 660), timedEvent("B", 600, 720), timedEvent("C", 660, 780)),
        )
        assertEquals(listOf(0, 1, 0), placed.map { it.lane })
        assertTrue(placed.all { it.laneCount == 2 })

        // Three at once really do take three lanes.
        val concurrent = layoutDayEvents(
            listOf(timedEvent("A", 540, 720), timedEvent("B", 570, 720), timedEvent("C", 600, 720)),
        )
        assertEquals(listOf(0, 1, 2), concurrent.map { it.lane })
        assertTrue(concurrent.all { it.laneCount == 3 })
    }

    @Test
    fun layoutDayEvents_givesAZeroLengthEventTheMinimumSpan() {
        val placed = layoutDayEvents(listOf(timedEvent("Punkt", 600, 600))).single()
        assertEquals(600, placed.startMinute)
        assertEquals(600 + MinEventSpanMinutes, placed.endMinute)
    }

    @Test
    fun layoutDayEvents_ignoresAllDayEntries() {
        val allDay = CalendarEvent(LocalDate(2026, 7, 29), "Ferie", AllDayLabel)
        assertTrue(layoutDayEvents(listOf(allDay)).isEmpty())
        assertEquals("Møde", layoutDayEvents(listOf(allDay, timedEvent("Møde", 540, 600))).single().event.title)
    }

    // --- Dragging a week block to a new slot ---

    /** Monday–Sunday of the week the drag fixtures live in (29 Jul 2026 is a Wednesday). */
    private val dragWeek = List(DaysPerWeek) { LocalDate(2026, 7, 27).plus(it, DateTimeUnit.DAY) }
    private val writable = listOf(CalendarSource("cal.home", "Hjem", canWrite = true))

    /** A movable 10:00–11:00 event on Wednesday, on a writable calendar. */
    private fun movable(
        uid: String? = "e1",
        sourceId: String = "cal.home",
        date: LocalDate = LocalDate(2026, 7, 29),
        start: LocalTime = LocalTime(10, 0),
        end: LocalDateTime = LocalDateTime(LocalDate(2026, 7, 29), LocalTime(11, 0)),
    ) = CalendarEvent(
        date = date,
        title = "Møde",
        time = "10:00",
        sourceId = sourceId,
        startMinute = minutesOfDay(start),
        endMinute = minutesOfDay(end.time),
        uid = uid,
        start = LocalDateTime(date, start),
        end = end,
    )

    @Test
    fun canDragEvent_acceptsAWritableSingleDayTimedEvent() {
        assertTrue(canDragEvent(movable(), writable))
    }

    @Test
    fun canDragEvent_refusesWhatNoWriteCouldAddress() {
        // No uid to name it by.
        assertFalse(canDragEvent(movable(uid = null), writable))
        // A read-only calendar.
        assertFalse(
            canDragEvent(movable(), listOf(CalendarSource("cal.home", "Arbejde", canWrite = false))),
        )
        // A calendar that isn't there at all.
        assertFalse(canDragEvent(movable(sourceId = "cal.other"), writable))
        // An all-day entry: it lives in the strip and has no minute to drop on.
        assertFalse(
            canDragEvent(
                CalendarEvent(LocalDate(2026, 7, 29), "Ferie", AllDayLabel, "cal.home", uid = "e2"),
                writable,
            ),
        )
    }

    @Test
    fun canDragEvent_refusesOneDayOfAMultiDayEvent() {
        // A Wed 10:00 → Fri 11:00 event, as its three rows come out of the expansion.
        val rows = expandCalendarEvent(
            sourceId = "cal.home",
            title = "Konference",
            start = LocalDateTime(LocalDate(2026, 7, 29), LocalTime(10, 0)),
            end = LocalDateTime(LocalDate(2026, 7, 31), LocalTime(11, 0)),
            uid = "e3",
        )
        assertEquals(3, rows.size)
        assertTrue(rows.none { canDragEvent(it, writable) })

        // The same event confined to one day is movable again — including one ending exactly at
        // midnight, which iCal reads as closing the day it started on.
        val toMidnight = expandCalendarEvent(
            sourceId = "cal.home",
            title = "Aften",
            start = LocalDateTime(LocalDate(2026, 7, 29), LocalTime(20, 0)),
            end = LocalDateTime(LocalDate(2026, 7, 30), LocalTime(0, 0)),
            uid = "e4",
        ).single()
        assertTrue(canDragEvent(toMidnight, writable))
    }

    @Test
    fun droppedEventSlot_movesTheDayAndSnapsTheMinute() {
        // Two columns right, and 40 minutes down — which snaps to the nearest quarter hour.
        val move = assertNotNull(droppedEventSlot(movable(), dragWeek, originDayIndex = 2, 2, 40))
        assertEquals(LocalDate(2026, 7, 31), move.date)   // Friday
        assertEquals(minutesOfDay(LocalTime(10, 45)), move.startMinute)
    }

    @Test
    fun droppedEventSlot_isLockedToTheWeekOnScreen() {
        // Dragged far past Sunday, and far past Monday: each pins to that column, never to the week
        // beyond it.
        assertEquals(dragWeek.last(), droppedEventSlot(movable(), dragWeek, 2, 12, 0)?.date)
        assertEquals(dragWeek.first(), droppedEventSlot(movable(), dragWeek, 2, -9, 0)?.date)
    }

    @Test
    fun droppedEventSlot_holdsTheStartInsideTheDay() {
        assertEquals(0, droppedEventSlot(movable(), dragWeek, 2, 0, -10_000)?.startMinute)
        assertEquals(
            MinutesPerDay - EventDragSnapMinutes,
            droppedEventSlot(movable(), dragWeek, 2, 0, 10_000)?.startMinute,
        )
    }

    @Test
    fun droppedEventSlot_isNullWhereNothingMoved() {
        // A long press that wobbled: same column, and a nudge too small to reach the next quarter.
        assertNull(droppedEventSlot(movable(), dragWeek, 2, 0, 0))
        assertNull(droppedEventSlot(movable(), dragWeek, 2, 0, 7))
    }

    @Test
    fun movedEventDraft_keepsTheDurationAndEverythingButTheTime() {
        val event = movable().copy(location = "Køkkenet", rrule = "FREQ=WEEKLY;BYDAY=WE")
        val draft = movedEventDraft(event, EventMove(event, LocalDate(2026, 7, 31), 14 * 60 + 30))
        assertEquals(LocalDateTime(LocalDate(2026, 7, 31), LocalTime(14, 30)), draft.start)
        assertEquals(LocalDateTime(LocalDate(2026, 7, 31), LocalTime(15, 30)), draft.end)
        assertEquals("Møde", draft.summary)
        assertEquals("Køkkenet", draft.location)
        assertEquals("FREQ=WEEKLY;BYDAY=WE", draft.rrule)
        assertFalse(draft.allDay)
    }

    @Test
    fun movedEventDraft_carriesAnEventPastMidnightRatherThanCuttingIt() {
        val event = movable(
            start = LocalTime(20, 0),
            end = LocalDateTime(LocalDate(2026, 7, 29), LocalTime(23, 0)),
        )
        val draft = movedEventDraft(event, EventMove(event, LocalDate(2026, 7, 29), 22 * 60))
        assertEquals(LocalDateTime(LocalDate(2026, 7, 29), LocalTime(22, 0)), draft.start)
        assertEquals(LocalDateTime(LocalDate(2026, 7, 30), LocalTime(1, 0)), draft.end)
    }

    @Test
    fun applyEventMove_showsOnlyTheAddressedOccurrenceAtItsNewSlot() {
        val moved = movable()
        val other = movable(uid = "e9").copy(title = "Andet")
        val result = applyEventMove(
            listOf(moved, other),
            EventMove(moved, LocalDate(2026, 7, 30), 9 * 60),
        )
        val landed = result.single { it.uid == "e1" }
        assertEquals(LocalDate(2026, 7, 30), landed.date)
        assertEquals(9 * 60, landed.startMinute)
        assertEquals(10 * 60, landed.endMinute)
        // Everything else is left exactly as it was.
        assertEquals(other, result.single { it.uid == "e9" })
    }

    @Test
    fun applyEventMove_isIdempotentSoTheRefetchCanOverlapTheHold() {
        val moved = movable()
        val move = EventMove(moved, LocalDate(2026, 7, 30), 9 * 60)
        val once = applyEventMove(listOf(moved), move)
        assertEquals(once, applyEventMove(once, move))
    }

    // --- Per-day event bounds ---

    @Test
    fun expandCalendarEvent_boundsASameDayEvent() {
        val event = expandCalendarEvent(
            sourceId = "calendar.matt",
            title = "Møde",
            start = LocalDateTime(2026, 7, 29, 9, 0),
            end = LocalDateTime(2026, 7, 29, 10, 30),
        ).single()
        assertEquals(540, event.startMinute)
        assertEquals(630, event.endMinute)
    }

    @Test
    fun expandCalendarEvent_boundsEachDayOfAMultiDayEvent() {
        // 22:00 on the 29th until 02:00 on the 31st: the first day runs to midnight, the middle day
        // is a full day (no clock bounds at all), and the last day starts at midnight.
        val days = expandCalendarEvent(
            sourceId = "calendar.matt",
            title = "Nattevagt",
            start = LocalDateTime(2026, 7, 29, 22, 0),
            end = LocalDateTime(2026, 7, 31, 2, 0),
        )
        assertEquals(3, days.size)
        assertEquals(1320 to 1440, days[0].startMinute to days[0].endMinute)
        assertEquals(null to null, days[1].startMinute to days[1].endMinute)
        assertEquals(0 to 120, days[2].startMinute to days[2].endMinute)

        // A two-day event is just the same run without a middle.
        val twoDays = expandCalendarEvent(
            sourceId = "calendar.matt",
            title = "Nattevagt",
            start = LocalDateTime(2026, 7, 29, 22, 0),
            end = LocalDateTime(2026, 7, 30, 2, 0),
        )
        assertEquals(2, twoDays.size)
        assertEquals(1320 to 1440, twoDays[0].startMinute to twoDays[0].endMinute)
        assertEquals(0 to 120, twoDays[1].startMinute to twoDays[1].endMinute)
    }

    @Test
    fun expandCalendarEvent_leavesAnAllDayEventUnbounded() {
        val days = expandCalendarEvent(
            sourceId = "calendar.papkassehuset",
            title = "Sommerhus",
            start = LocalDateTime(2026, 7, 29, 0, 0),
            end = LocalDateTime(2026, 8, 1, 0, 0),
            allDay = true,
        )
        assertEquals(3, days.size)
        assertTrue(days.all { it.startMinute == null && it.endMinute == null })
    }

    @Test
    fun expandCalendarEvent_stampsTheWholeEventsBoundsOnEveryDayOfIt() {
        val start = LocalDateTime(2026, 7, 29, 22, 0)
        val end = LocalDateTime(2026, 7, 31, 2, 0)
        val days = expandCalendarEvent(
            sourceId = "calendar.matt",
            title = "Nattevagt",
            start = start,
            end = end,
        )
        // Every day carries the *event's* real bounds, not its own slice of them: the editor opens
        // from whichever day was tapped and has to show the event's actual start and end.
        assertEquals(3, days.size)
        assertTrue(days.all { it.start == start && it.end == end })

        // Including an all-day run, whose stored end stays the exclusive one it was given.
        val allDay = expandCalendarEvent(
            sourceId = "calendar.papkassehuset",
            title = "Sommerhus",
            start = LocalDateTime(2026, 7, 29, 0, 0),
            end = LocalDateTime(2026, 8, 1, 0, 0),
            allDay = true,
        )
        assertTrue(allDay.all { it.start == LocalDateTime(2026, 7, 29, 0, 0) })
        assertTrue(allDay.all { it.end == LocalDateTime(2026, 8, 1, 0, 0) })
    }

    private fun timedEvent(title: String, start: Int, end: Int) = CalendarEvent(
        date = LocalDate(2026, 7, 29),
        title = title,
        time = "",
        startMinute = start,
        endMinute = end,
    )

    // --- Todos ---

    @Test
    fun addTodo_appendsTrimmedItem() {
        val due = LocalDate(2026, 7, 15)
        val state = seedHome().addTodo("t1", due, "  Ny opgave  ")
        val added = state.calendar.todos.last()
        assertEquals("t1", added.id)
        assertEquals(due, added.due)
        assertEquals("Ny opgave", added.label)
        assertFalse(added.done)
    }

    @Test
    fun addTodo_blankLabelIsNoOp() {
        val before = seedHome()
        val after = before.addTodo("t1", LocalDate(2026, 7, 15), "   ")
        assertEquals(before.calendar.todos, after.calendar.todos)
    }

    @Test
    fun toggleTodo_flipsDone() {
        val seed = seedHome()
        val target = seed.calendar.todos.first { !it.done }
        val today = LocalDate(2026, 7, 15)
        val after = seed.toggleTodo(target.id, today)
        assertEquals(true, after.calendar.todos.first { it.id == target.id }.done)
    }

    @Test
    fun toggleTodo_stampsTheDayItWasClosedAndClearsItOnReopening() {
        val seed = seedHome()
        val target = seed.calendar.todos.first { !it.done }
        val closedOn = LocalDate(2026, 7, 15)

        val closed = seed.toggleTodo(target.id, closedOn).calendar.todos.first { it.id == target.id }
        assertEquals(closedOn, closed.completedOn)
        assertEquals(closedOn, closed.closedOn)

        // Re-opening drops the stamp, so closing it again dates it afresh rather than keeping the old day.
        val reopened = seed.toggleTodo(target.id, closedOn)
            .toggleTodo(target.id, closedOn.plus(1, DateTimeUnit.DAY))
            .calendar.todos.first { it.id == target.id }
        assertNull(reopened.completedOn)
        assertFalse(reopened.done)
    }

    @Test
    fun closedOn_fallsBackToTheDueDayWithoutAStamp() {
        // What a task ticked off in the Home Assistant app looks like: completed, no marker written.
        val due = LocalDate(2026, 7, 12)
        assertEquals(due, TodoItem("x", due, "Vask op", done = true).closedOn)
    }

    @Test
    fun editTodo_setsLabel() {
        val seed = seedHome()
        val id = seed.calendar.todos.first().id
        val after = seed.editTodo(id, "Ændret tekst")
        assertEquals("Ændret tekst", after.calendar.todos.first { it.id == id }.label)
    }

    @Test
    fun editTodo_blankRemovesItem() {
        val seed = seedHome()
        val id = seed.calendar.todos.first().id
        val after = seed.editTodo(id, "   ")
        assertNull(after.calendar.todos.firstOrNull { it.id == id })
        assertEquals(seed.calendar.todos.size - 1, after.calendar.todos.size)
    }

    @Test
    fun todoPage_carriesWhatIsStillOpenFromEveryPassedDay() {
        val today = LocalDate(2026, 7, 15)
        val todos = listOf(
            TodoItem("today-open", today, "Køb kaffe", done = false),
            TodoItem("late-a", today.plus(-3, DateTimeUnit.DAY), "Ring til tandlæge", done = false),
            TodoItem("later", today.plus(4, DateTimeUnit.DAY), "Skift dæk", done = false),
            TodoItem("late-b", today.plus(-1, DateTimeUnit.DAY), "Svar udlejeren", done = false),
        )
        val page = todoPage(todos, today)
        // One group per day, nearest first — and the day after today is not on today's page.
        assertEquals(listOf(today, today.plus(-1, DateTimeUnit.DAY), today.plus(-3, DateTimeUnit.DAY)), page.open.map { it.due })
        assertEquals(listOf("today-open", "late-b", "late-a"), page.open.flatMap { g -> g.items.map { it.id } })
        assertTrue(page.done.isEmpty())
    }

    @Test
    fun todoPage_showsALaterDayOnceItsOwnPageIsReached() {
        val today = LocalDate(2026, 7, 15)
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val todos = listOf(
            TodoItem("today-open", today, "Køb kaffe", done = false),
            TodoItem("tomorrow", tomorrow, "Skift dæk", done = false),
        )
        assertEquals(listOf("today-open"), todoPage(todos, today).open.flatMap { g -> g.items.map { it.id } })
        // Tomorrow's page still carries today's — nothing has to be swiped back to.
        assertEquals(listOf("tomorrow", "today-open"), todoPage(todos, tomorrow).open.flatMap { g -> g.items.map { it.id } })
    }

    @Test
    fun todoPage_sinksWhatIsTickedIntoItsOwnSection() {
        val today = LocalDate(2026, 7, 15)
        val yesterday = today.plus(-1, DateTimeUnit.DAY)
        val todos = listOf(
            TodoItem("today-done", today, "Vand planter", done = true, completedOn = today),
            TodoItem("today-open", today, "Køb kaffe", done = false),
            // Due yesterday, but closed today — the case the done half's grouping exists for.
            TodoItem("late-done", yesterday, "Vask op", done = true, completedOn = today),
        )
        val page = todoPage(todos, today)
        assertEquals(listOf("today-open"), page.open.flatMap { g -> g.items.map { it.id } })
        // Both were closed today, so both are on today's page — grouped by the day they were *due*,
        // which is what separates "it was today's" from "it had been hanging over from yesterday".
        assertEquals(listOf(today, yesterday), page.done.map { it.due })
        assertEquals(listOf("today-done", "late-done"), page.done.flatMap { g -> g.items.map { it.id } })
    }

    @Test
    fun todoPage_leavesTheNextDayACleanSlate() {
        val today = LocalDate(2026, 7, 15)
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val todos = listOf(
            TodoItem("today-done", today, "Vand planter", done = true, completedOn = today),
            TodoItem("late-done", today.plus(-1, DateTimeUnit.DAY), "Vask op", done = true, completedOn = today),
        )
        assertEquals(2, todoPage(todos, today).done.sumOf { it.items.size })
        // Closed on the 15th means closed on the 15th — the 16th does not inherit them, and with
        // nothing open either it opens blank.
        val next = todoPage(todos, tomorrow)
        assertTrue(next.done.isEmpty())
        assertTrue(next.open.isEmpty())
    }

    @Test
    fun todoPage_doesNotShowATaskClosedAfterTheDayBeingLookedAt() {
        val today = LocalDate(2026, 7, 15)
        val closedTomorrow = TodoItem(
            "later-close", today, "Vand planter", done = true, completedOn = today.plus(1, DateTimeUnit.DAY),
        )
        // Swiping back to the day it was *due* must not show it as finished there — it wasn't yet.
        assertTrue(todoPage(listOf(closedTomorrow), today).done.isEmpty())
        assertTrue(todoPage(listOf(closedTomorrow), today).open.isEmpty())
        assertEquals(1, todoPage(listOf(closedTomorrow), today.plus(1, DateTimeUnit.DAY)).done.sumOf { it.items.size })
    }

    @Test
    fun todoPage_filesAnUnstampedDoneTaskOnItsDueDay() {
        // Ticked off in the Home Assistant app, so no marker was ever written. Every client derives
        // the same fallback, so they still agree on where it sits.
        val due = LocalDate(2026, 7, 12)
        val todos = listOf(TodoItem("ha-side", due, "Vask op", done = true))
        assertEquals(1, todoPage(todos, due).done.sumOf { it.items.size })
        assertTrue(todoPage(todos, due.plus(1, DateTimeUnit.DAY)).done.isEmpty())
    }

    @Test
    fun todoPage_doesNotShowATaskOnDaysBeforeItWasWrittenDown() {
        val today = LocalDate(2026, 8, 8)
        val yesterday = today.plus(-1, DateTimeUnit.DAY)
        // Due in the past but only written down today: a task cannot have been standing on a day it
        // did not exist on, so it starts on today's page and carries forward from there.
        val backdated = TodoItem("backdated", yesterday, "Vask op", done = false, createdOn = today)

        assertTrue(todoPage(listOf(backdated), yesterday).open.isEmpty())
        assertEquals(1, todoPage(listOf(backdated), today).open.sumOf { it.items.size })
        assertEquals(1, todoPage(listOf(backdated), today.plus(3, DateTimeUnit.DAY)).open.sumOf { it.items.size })
    }

    @Test
    fun todoPage_stillStartsALaterTaskOnItsOwnDay() {
        val today = LocalDate(2026, 8, 8)
        val friday = today.plus(4, DateTimeUnit.DAY)
        // Written down today for Friday — adding on a later page is how this surface picks a due
        // date, so the creation day must not drag it forward onto today.
        val ahead = TodoItem("ahead", friday, "Book flybilletter", done = false, createdOn = today)

        assertTrue(todoPage(listOf(ahead), today).open.isEmpty())
        assertEquals(1, todoPage(listOf(ahead), friday).open.sumOf { it.items.size })
    }

    @Test
    fun todoPage_carriesATaskWithNoRecordedCreationDayFromItsDueDay() {
        val due = LocalDate(2026, 8, 5)
        // Added from the Home Assistant app, or before the creation day was recorded: unchanged
        // behaviour — it stands from its due day forward.
        val old = TodoItem("old", due, "Skift dæk", done = false)

        assertTrue(todoPage(listOf(old), due.plus(-1, DateTimeUnit.DAY)).open.isEmpty())
        assertEquals(1, todoPage(listOf(old), due).open.sumOf { it.items.size })
        assertEquals(1, todoPage(listOf(old), due.plus(9, DateTimeUnit.DAY)).open.sumOf { it.items.size })
    }

    @Test
    fun addTodo_recordsTheDayItWasWrittenDown() {
        val seed = seedHome()
        val due = LocalDate(2026, 8, 5)
        val today = LocalDate(2026, 8, 8)

        val added = seed.addTodo("new", due, "Vask op", createdOn = today).calendar.todos.last()

        assertEquals(due, added.due)
        assertEquals(today, added.createdOn)
        assertEquals(today, added.showsFrom)
    }

    @Test
    fun todoPage_ordersADayAlphabeticallyIgnoringCase() {
        val today = LocalDate(2026, 7, 15)
        val todos = listOf(
            TodoItem("c", today, "ærinde", done = false),
            TodoItem("a", today, "Bage brød", done = false),
            TodoItem("b", today, "aflever pakke", done = false),
        )
        // A page mixes days, so append order would put no two rows anywhere predictable.
        assertEquals(listOf("b", "a", "c"), todoPage(todos, today).open.single().items.map { it.id })
    }

    @Test
    fun todoPage_onAnEmptyListIsTwoEmptySections() {
        val page = todoPage(emptyList(), LocalDate(2026, 7, 15))
        assertTrue(page.open.isEmpty())
        assertTrue(page.done.isEmpty())
    }

    @Test
    fun audioMutations_areNoOpsOnSpeakerlessRoom() {
        // Kitchen has no speaker (audio == null); every audio transition must leave it untouched.
        val seed = seedHome()
        assertNull(seed.rooms.getValue(Room.Kitchen).audio)
        val mutated = seed
            .withVolume(Room.Kitchen, 50)
            .togglePlay(Room.Kitchen)
            .next(Room.Kitchen)
            .previous(Room.Kitchen)
            .seek(Room.Kitchen, 30)
            .setShuffle(Room.Kitchen, true)
            .setRepeat(Room.Kitchen, RepeatMode.All)
        assertNull(mutated.rooms.getValue(Room.Kitchen).audio)
    }

    @Test
    fun rotateFrom_startsAtTheIndexAndWrapsTheRestToTheTail() {
        val hits = listOf("a", "b", "c", "d")
        assertEquals(hits, rotateFrom(hits, 0))
        assertEquals(listOf("c", "d", "a", "b"), rotateFrom(hits, 2))
        assertEquals(listOf("d", "a", "b", "c"), rotateFrom(hits, 3))
    }

    @Test
    fun rotateFrom_leavesAnOutOfRangeOrEmptyListUntouched() {
        val hits = listOf("a", "b")
        assertEquals(hits, rotateFrom(hits, -1))
        assertEquals(hits, rotateFrom(hits, 2))
        assertEquals(emptyList(), rotateFrom(emptyList<String>(), 0))
    }
}
