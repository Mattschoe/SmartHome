package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        val target = seed.calendar.todos.first()
        val after = seed.toggleTodo(target.id)
        assertEquals(!target.done, after.calendar.todos.first { it.id == target.id }.done)
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
