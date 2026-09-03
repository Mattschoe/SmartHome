package com.mattschoe.smarthome.ui.pages.homepage

import com.mattschoe.smarthome.data.EventMove
import com.mattschoe.smarthome.data.HomeAdapter
import com.mattschoe.smarthome.data.InMemoryCalendarFilterStore
import com.mattschoe.smarthome.data.MockAdapter
import com.mattschoe.smarthome.data.model.EventEditScope
import com.mattschoe.smarthome.data.model.RecurrenceRange
import com.mattschoe.smarthome.data.buildEventDraft
import com.mattschoe.smarthome.data.canDragEvent
import com.mattschoe.smarthome.data.todoPage
import com.mattschoe.smarthome.data.weekStart
import com.mattschoe.smarthome.data.model.ArtistDetail
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.BrowseKind
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.CalendarView
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.QueueMode
import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomepageViewModelTest {

    // viewModelScope dispatches on Main; back it with a test dispatcher whose scheduler runTest shares.
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initialScreenState_isLoading() {
        val vm = HomepageViewModel(MockAdapter())
        assertIs<HomeScreenState.Loading>(vm.screenState.value)
    }

    @Test
    fun audioRooms_areSpeakerRoomsOnly() {
        assertEquals(listOf(Room.LivingRoom, Room.Bedroom), Room.audioRooms)
    }

    @Test
    fun lightAndAudioRoomSelection_areIndependent() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        // WhileSubscribed only emits Ready while collected.
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // Pick a light room that is NOT a speaker room, and a different audio room — proving neither
        // selection drives the other.
        vm.selectLightRoom(Room.Kitchen)
        vm.selectAudioRoom(Room.Bedroom)
        vm.selectPanel(Panel.Calendar)
        advanceUntilIdle()

        val ready = vm.screenState.value
        assertIs<HomeScreenState.Ready>(ready)
        assertEquals(Room.Kitchen, ready.activeLightRoom)
        assertEquals(Room.Bedroom, ready.activeAudioRoom)
        assertEquals(Panel.Calendar, ready.panel)
        assertEquals(ready.rooms.getValue(Room.Kitchen), ready.lightRoomState)
        assertEquals(ready.rooms.getValue(Room.Bedroom), ready.audioRoomState)
    }

    @Test
    fun selectMusicSource_swapsTheBrowseShelvesAndDisturbsNothingElse() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // Put some unrelated state up first, so the toggle has something it could wrongly reset.
        vm.selectAudioRoom(Room.Bedroom)
        vm.setSearchQuery("nordlys")
        advanceUntilIdle()

        val before = vm.screenState.value
        assertIs<HomeScreenState.Ready>(before)
        assertEquals(MusicSource.YtMusic, before.musicSource)
        assertEquals(before.playlists, before.browsePlaylists)
        assertIs<SearchState.Results>(before.search)

        vm.selectMusicSource(MusicSource.Spotify)
        advanceUntilIdle()

        val after = vm.screenState.value
        assertIs<HomeScreenState.Ready>(after)
        assertEquals(MusicSource.Spotify, after.musicSource)
        assertTrue(after.spotifyPlaylists.isNotEmpty())
        assertEquals(after.spotifyPlaylists, after.browsePlaylists)
        assertNotEquals(before.browsePlaylists, after.browsePlaylists)

        // The toggle scopes browsing only — search, the panel and the audio selection are untouched.
        assertEquals(before.search, after.search)
        assertEquals(before.searchQuery, after.searchQuery)
        assertEquals(before.panel, after.panel)
        assertEquals(Room.Bedroom, after.activeAudioRoom)
        assertNull(after.pendingPlay)
    }

    @Test
    fun transportForwarder_reachesAdapterAndBrowseListsSurface() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val playingBefore =
            (vm.screenState.value as HomeScreenState.Ready).audioState.isPlaying
        vm.togglePlay(Room.LivingRoom)
        advanceUntilIdle()

        val ready = vm.screenState.value
        assertIs<HomeScreenState.Ready>(ready)
        assertEquals(!playingBefore, ready.audioState.isPlaying)
        // Browse shelves are carried through the combine into Ready.
        assertTrue(ready.quickPicks.isNotEmpty())
        assertTrue(ready.mixedForYou.isNotEmpty())
    }

    @Test
    fun deviceMutation_flowsFromAdapterIntoScreenState() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.setWarmth(Room.LivingRoom, Warmth.Cool)
        vm.setBrightness(Room.LivingRoom, 15)
        advanceUntilIdle()

        val ready = vm.screenState.value
        assertIs<HomeScreenState.Ready>(ready)
        val living = ready.rooms.getValue(Room.LivingRoom)
        assertEquals(Warmth.Cool, living.lightWarmth)
        assertEquals(15, living.brightnessPct)
        assertTrue(living.isLightOn)
    }

    @Test
    fun mediaMinimized_togglesAndResetsWhenSomethingNewIsPlayed() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // The player starts expanded.
        assertEquals(false, (vm.screenState.value as HomeScreenState.Ready).mediaMinimized)

        vm.setMediaMinimized(true)
        advanceUntilIdle()
        val minimized = vm.screenState.value
        assertIs<HomeScreenState.Ready>(minimized)
        assertTrue(minimized.mediaMinimized)
        // Collapsing is a pure view change — it must not touch playback.
        assertEquals(
            (minimized.rooms.getValue(Room.LivingRoom).audio)?.isPlaying,
            minimized.audioState.isPlaying,
        )

        // Playing a browse tile from the collapsed state restores the full surface.
        vm.play(BrowseItem("Fokus", subtitle = "Playlist", uri = "library://playlist/1"))
        advanceUntilIdle()
        assertEquals(false, (vm.screenState.value as HomeScreenState.Ready).mediaMinimized)
    }

    @Test
    fun queueIntents_targetTheActiveAudioRoomAndForwardTheSignedShift() = runTest(mainDispatcher) {
        val adapter = RecordingQueueAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // Move both selections, to a *different* room each, so a queue intent aimed at the light room
        // (or at the default) would show up as the wrong target below.
        vm.selectLightRoom(Room.Kitchen)
        vm.selectAudioRoom(Room.Bedroom)
        advanceUntilIdle()

        vm.playQueueItem("q7")
        vm.moveQueueItem("q7", -3)
        vm.moveQueueItem("q2", 4)
        advanceUntilIdle()

        assertEquals(listOf(Room.Bedroom to "q7"), adapter.played)
        assertEquals(
            listOf(Triple(Room.Bedroom, "q7", -3), Triple(Room.Bedroom, "q2", 4)),
            adapter.moved,
        )
    }

    /** A [MockAdapter] that additionally records the queue intents it is handed. */
    private class RecordingQueueAdapter(
        private val delegate: HomeAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        val played = mutableListOf<Pair<Room, String>>()
        val moved = mutableListOf<Triple<Room, String, Int>>()

        override suspend fun playQueueItem(room: Room, queueItemId: String) {
            played += room to queueItemId
        }

        override fun moveQueueItem(room: Room, queueItemId: String, posShift: Int) {
            moved += Triple(room, queueItemId, posShift)
        }
    }

    @Test
    fun joinAction_isOfferedWheneverEitherRoomHasMusic() = runTest(mainDispatcher) {
        // Seed: the Living Room is playing, the Bedroom idle.
        val vm = HomepageViewModel(RecordingGroupAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // From the playing room, the offer is to put its music on in the other one too…
        val fromPlayingRoom = vm.screenState.value as HomeScreenState.Ready
        assertEquals(Room.Bedroom, fromPlayingRoom.otherAudioRoom)
        assertEquals(Room.Bedroom, fromPlayingRoom.joinTarget)

        // …and from the idle room, to play along with it. Neither side leads.
        vm.selectAudioRoom(Room.Bedroom)
        advanceUntilIdle()
        assertEquals(Room.LivingRoom, (vm.screenState.value as HomeScreenState.Ready).joinTarget)

        // With nothing playing anywhere there is nothing to play together, and no action shows.
        vm.togglePlay(Room.LivingRoom)
        advanceUntilIdle()
        assertNull((vm.screenState.value as HomeScreenState.Ready).joinTarget)
    }

    @Test
    fun toggleAudioJoin_takesTheMusicFromWhicheverRoomHasIt() = runTest(mainDispatcher) {
        val adapter = RecordingGroupAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        vm.selectAudioRoom(Room.Bedroom) // the idle room; the Living Room is what's playing
        advanceUntilIdle()

        vm.toggleAudioJoin()
        advanceUntilIdle()

        // The music is in the Living Room, so that is what the pair plays.
        assertEquals(listOf(Room.LivingRoom to Room.Bedroom), adapter.joins)
        val joined = vm.screenState.value as HomeScreenState.Ready
        assertTrue(joined.audioJoined)
        // The action stays up — a group can always be taken apart again.
        assertEquals(Room.LivingRoom, joined.joinTarget)
    }

    @Test
    fun toggleAudioJoin_fromThePlayingRoomSendsItsMusicToTheOtherOne() = runTest(mainDispatcher) {
        val adapter = RecordingGroupAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle() // viewing the Living Room, which is the room that's playing

        vm.toggleAudioJoin()
        advanceUntilIdle()

        // The music is here, so the other room is the one that comes along.
        assertEquals(listOf(Room.LivingRoom to Room.Bedroom), adapter.joins)
        assertTrue((vm.screenState.value as HomeScreenState.Ready).audioJoined)
    }

    @Test
    fun toggleAudioJoin_isDroppedWhenNeitherRoomHasMusic() = runTest(mainDispatcher) {
        val adapter = RecordingGroupAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        vm.togglePlay(Room.LivingRoom) // now nothing is playing anywhere
        advanceUntilIdle()

        // The action isn't offered at all; a tap that raced the music stopping is dropped.
        vm.toggleAudioJoin()
        advanceUntilIdle()

        assertTrue(adapter.joins.isEmpty())
        assertEquals(false, (vm.screenState.value as HomeScreenState.Ready).audioJoined)
    }

    @Test
    fun toggleAudioJoin_dropsTheOtherRoomWhenTheRoomsAreAlreadyJoined() = runTest(mainDispatcher) {
        val adapter = RecordingGroupAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        vm.selectAudioRoom(Room.Bedroom)
        advanceUntilIdle()

        vm.toggleAudioJoin()
        advanceUntilIdle()
        vm.toggleAudioJoin()
        advanceUntilIdle()

        // Leaving is always phrased about the *other* room, so that is the one dropped — the room
        // being looked at is the one that stays.
        assertEquals(listOf(Room.LivingRoom), adapter.unjoins)
        assertEquals(false, (vm.screenState.value as HomeScreenState.Ready).audioJoined)
    }

    @Test
    fun musicIntents_fromARoomInAGroupAddressTheGroupsSession() = runTest(mainDispatcher) {
        val adapter = RecordingSessionAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        vm.selectAudioRoom(Room.Bedroom)
        advanceUntilIdle()
        vm.toggleAudioJoin() // the Bedroom now plays along with the Living Room's music
        advanceUntilIdle()

        vm.play(BrowseItem("Fokus", subtitle = "Playlist", uri = "library://playlist/1"))
        advanceUntilIdle()
        vm.playQueueItem("q7")
        vm.moveQueueItem("q7", -2)
        vm.togglePlay(Room.Bedroom)
        vm.setVolume(Room.Bedroom, 12)
        advanceUntilIdle()

        // Everything about the music goes to the session the group is playing, so putting a song on
        // from the Bedroom is the whole group's new song…
        assertEquals(listOf(Room.LivingRoom to "library://playlist/1"), adapter.plays)
        assertEquals(listOf(Room.LivingRoom to "q7"), adapter.skips)
        assertEquals(listOf(Triple(Room.LivingRoom, "q7", -2)), adapter.moved)
        assertEquals(listOf(Room.LivingRoom), adapter.toggles)
        // …while volume stays the speaker's own.
        assertEquals(listOf(Room.Bedroom to 12), adapter.volumes)
    }

    @Test
    fun audioPanel_showsTheGroupsMusicInBothRooms() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        vm.selectAudioRoom(Room.Bedroom)
        advanceUntilIdle()
        vm.toggleAudioJoin()
        advanceUntilIdle()

        vm.play(BrowseItem("Fokus", subtitle = "Playlist", uri = "library://playlist/1"))
        advanceUntilIdle()

        // The Bedroom is looking at the group's music, not at its own idle session…
        val fromBedroom = vm.screenState.value as HomeScreenState.Ready
        assertEquals("Fokus", fromBedroom.audioState.nowPlaying?.title)
        assertEquals(
            fromBedroom.rooms.getValue(Room.Bedroom).audio?.volumePct,
            fromBedroom.audioState.volumePct,
        )

        // …and the room it is grouped with shows exactly the same thing.
        vm.selectAudioRoom(Room.LivingRoom)
        advanceUntilIdle()
        val fromLivingRoom = vm.screenState.value as HomeScreenState.Ready
        assertEquals(fromBedroom.audioState.nowPlaying, fromLivingRoom.audioState.nowPlaying)
        assertEquals(fromBedroom.audioState.queue, fromLivingRoom.audioState.queue)
    }

    @Test
    fun toggleAudioJoin_dropsATapWhileTheGroupChangeIsStillInFlight() = runTest(mainDispatcher) {
        // Device state never reflects the join, so the guard stays armed for its whole window.
        val adapter = RecordingGroupAdapter(reflect = false)
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        vm.selectAudioRoom(Room.Bedroom)
        advanceUntilIdle()

        vm.toggleAudioJoin()
        advanceTimeBy(GROUP_CHANGE_GRACE_MS / 2)
        // The label hasn't flipped yet, so a second tap is a retry of the same intent, not a leave.
        vm.toggleAudioJoin()
        advanceTimeBy(GROUP_CHANGE_GRACE_MS / 4)

        assertEquals(listOf(Room.LivingRoom to Room.Bedroom), adapter.joins)
        assertTrue(adapter.unjoins.isEmpty())

        // Once the wait gives up, the action is live again.
        advanceUntilIdle()
        vm.toggleAudioJoin()
        advanceUntilIdle()
        assertEquals(2, adapter.joins.size)
    }

    /**
     * A [MockAdapter] recording the grouping intents. [reflect] `false` swallows them instead of
     * applying them, so device state never flips — the shape of a join HA hasn't echoed yet.
     */
    private class RecordingGroupAdapter(
        private val delegate: MockAdapter = MockAdapter(),
        private val reflect: Boolean = true,
    ) : HomeAdapter by delegate {
        val joins = mutableListOf<Pair<Room, Room>>()
        val unjoins = mutableListOf<Room>()

        override fun joinAudio(leader: Room, follower: Room) {
            joins += leader to follower
            if (reflect) delegate.joinAudio(leader, follower)
        }

        override fun unjoinAudio(room: Room) {
            unjoins += room
            if (reflect) delegate.unjoinAudio(room)
        }
    }

    /**
     * A [MockAdapter] recording which room every music intent was addressed to — the question a sync
     * group makes interesting. Grouping itself is applied, so the state the redirect reads is real.
     */
    private class RecordingSessionAdapter(
        private val delegate: MockAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        val plays = mutableListOf<Pair<Room, String>>()
        val skips = mutableListOf<Pair<Room, String>>()
        val moved = mutableListOf<Triple<Room, String, Int>>()
        val toggles = mutableListOf<Room>()
        val volumes = mutableListOf<Pair<Room, Int>>()

        override suspend fun play(room: Room, uri: String, radio: Boolean) {
            plays += room to uri
            delegate.play(room, uri, radio)
        }

        override suspend fun playQueueItem(room: Room, queueItemId: String) {
            skips += room to queueItemId
        }

        override fun moveQueueItem(room: Room, queueItemId: String, posShift: Int) {
            moved += Triple(room, queueItemId, posShift)
        }

        override fun togglePlay(room: Room) {
            toggles += room
            delegate.togglePlay(room)
        }

        override fun setVolume(room: Room, value: Int) {
            volumes += room to value
            delegate.setVolume(room, value)
        }
    }

    @Test
    fun blankQuery_staysIdleAndNeverReachesTheAdapter() = runTest(mainDispatcher) {
        val adapter = RecordingSearchAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        assertIs<SearchState.Idle>((vm.screenState.value as HomeScreenState.Ready).search)

        // Whitespace is not a search — it must not cost a round trip.
        vm.setSearchQuery("   ")
        advanceUntilIdle()

        assertIs<SearchState.Idle>((vm.screenState.value as HomeScreenState.Ready).search)
        assertTrue(adapter.queries.isEmpty())
    }

    @Test
    fun typedQuery_advancesThroughSearchingIntoResultsAfterTheDebounce() = runTest(mainDispatcher) {
        val adapter = RecordingSearchAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.setSearchQuery("Nordlys")
        // Nothing may go out before the debounce window elapses.
        advanceTimeBy(SEARCH_DEBOUNCE_MS - 50)
        assertIs<SearchState.Idle>((vm.screenState.value as HomeScreenState.Ready).search)
        assertTrue(adapter.queries.isEmpty())

        advanceTimeBy(100)
        assertIs<SearchState.Searching>((vm.screenState.value as HomeScreenState.Ready).search)

        advanceUntilIdle()
        val ready = vm.screenState.value as HomeScreenState.Ready
        assertEquals("Nordlys", ready.searchQuery)
        assertEquals(listOf("Nordlys"), adapter.queries)
        assertEquals(listOf("Hit"), assertIs<SearchState.Results>(ready.search).items.map { it.name })
    }

    @Test
    fun fastKeystrokes_issueASingleSearchForTheFinalQuery() = runTest(mainDispatcher) {
        val adapter = RecordingSearchAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.setSearchQuery("Nord")
        advanceTimeBy(SEARCH_DEBOUNCE_MS / 2)
        vm.setSearchQuery("Nordlys")
        advanceUntilIdle()

        assertEquals(listOf("Nordlys"), adapter.queries)
    }

    @Test
    fun playingAResult_clearsTheQueryBackToTheBrowseShelves() = runTest(mainDispatcher) {
        val adapter = RecordingSearchAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.setSearchQuery("Nordlys")
        advanceUntilIdle()
        assertIs<SearchState.Results>((vm.screenState.value as HomeScreenState.Ready).search)

        vm.play(BrowseItem("Hit", subtitle = "Kunstner", uri = "mock://track/hit"))
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertEquals("", ready.searchQuery)
        assertIs<SearchState.Idle>(ready.search)
    }

    @Test
    fun play_showsPendingUntilTheAdapterResolvesThenClearsIt() = runTest(mainDispatcher) {
        val adapter = SlowPlayAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.play(BrowseItem("Nordlys", subtitle = "Efterklang", uri = "mock://track/nordlys"))
        advanceTimeBy(PLAY_LATENCY_MS / 2)

        // Mid-flight: the tapped item is up as the pending (loading) surface, no toast.
        val pending = (vm.screenState.value as HomeScreenState.Ready).pendingPlay
        assertEquals("Nordlys", pending?.title)

        advanceUntilIdle()
        val ready = vm.screenState.value as HomeScreenState.Ready
        assertEquals(null, ready.pendingPlay)
        assertEquals(null, ready.toast)
        // The mock promoted the tile to now-playing, which is what released the pending state.
        assertEquals("Nordlys", ready.audioState.nowPlaying?.title)
    }

    @Test
    fun play_holdsTheUpNextLoaderUntilTheRefreshedQueueLands() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(SlowPlayAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.play(BrowseItem("Nordlys", subtitle = "Efterklang", uri = "mock://track/nordlys"))
        advanceTimeBy(PLAY_LATENCY_MS / 2)

        // Mid-flight the old queue is hidden behind the loader — its rows belong to the old track.
        assertTrue((vm.screenState.value as HomeScreenState.Ready).queueRefreshing)

        advanceUntilIdle()
        val ready = vm.screenState.value as HomeScreenState.Ready
        assertEquals(false, ready.queueRefreshing)
        // What released the loader: a usable new queue (the mock's instant continuation refill).
        assertTrue(ready.audioState.queue.isNotEmpty())
        assertTrue(ready.audioState.queue.none { it.uri == "mock://track/nordlys" })
    }

    @Test
    fun play_boundsTheUpNextLoaderWhenTheQueueNeverChanges() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(InertPlayAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.play(BrowseItem("Nordlys", uri = "mock://track/nordlys"))
        advanceTimeBy(TRACK_CHANGE_GRACE_MS + 1_000)

        // The play resolved but the device state never moved: the pending surface has let go
        // (its own grace expired), while the queue loader is still inside its window.
        val mid = vm.screenState.value as HomeScreenState.Ready
        assertEquals(null, mid.pendingPlay)
        assertTrue(mid.queueRefreshing)

        // The loader may not hold forever — the grace timeout hands back whatever queue there is.
        advanceUntilIdle()
        assertEquals(false, (vm.screenState.value as HomeScreenState.Ready).queueRefreshing)
    }

    @Test
    fun play_failure_clearsPendingAndRaisesTheToast() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(FailingPlayAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.play(BrowseItem("Nordlys", uri = "mock://track/nordlys"))
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertEquals(null, ready.pendingPlay)
        // The failure also released the up-next loader — the old queue is still the truth.
        assertEquals(false, ready.queueRefreshing)
        assertEquals("Kunne ikke afspille", ready.toast?.text)

        vm.dismissToast()
        advanceUntilIdle()
        assertEquals(null, (vm.screenState.value as HomeScreenState.Ready).toast)
    }

    @Test
    fun queueSkip_blocksReTapsWhileInFlightAndSpinsTheRow() = runTest(mainDispatcher) {
        val adapter = SlowPlayAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.playQueueItem("q7")
        advanceTimeBy(PLAY_LATENCY_MS / 2)
        assertEquals("q7", (vm.screenState.value as HomeScreenState.Ready).pendingQueueItemId)

        // A second tap mid-flight is a retry of the same intent — it must not queue up another skip.
        vm.playQueueItem("q7")
        vm.playQueueItem("q9")
        advanceUntilIdle()

        assertEquals(listOf("q7"), adapter.queueItemsPlayed)
        assertEquals(null, (vm.screenState.value as HomeScreenState.Ready).pendingQueueItemId)
    }

    @Test
    fun pendingPlay_isScopedToTheAudioRoomItWasTappedIn() = runTest(mainDispatcher) {
        val adapter = SlowPlayAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.play(BrowseItem("Nordlys", uri = "mock://track/nordlys")) // pending in LivingRoom
        advanceTimeBy(PLAY_LATENCY_MS / 2)
        vm.selectAudioRoom(Room.Bedroom)
        advanceTimeBy(10)

        // Viewing another room must not paint that room's Media panel with the pending item.
        assertEquals(null, (vm.screenState.value as HomeScreenState.Ready).pendingPlay)
    }

    @Test
    fun enqueue_queuesTheTileAndLeavesEverySurfaceExactlyAsItWas() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // The state a long-press is reached from: a typed search, a drilled-in artist, a collapsed
        // player. Queueing is not "show me this", so none of them may move.
        vm.setMediaMinimized(true)
        vm.setSearchQuery("Nordlys")
        vm.openArtist(ARTIST_TILE)
        advanceUntilIdle()

        vm.enqueue(BrowseItem("Nordlys", uri = "mock://track/nordlys"), QueueMode.Last)
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertEquals("Nordlys", ready.searchQuery)
        assertTrue(ready.mediaMinimized)
        assertNotNull(ready.artist)
        assertNull(ready.pendingPlay)
        // Nothing started — and the tile is in the queue, above what was already lined up.
        assertEquals("Midnight City", ready.audioState.nowPlaying?.title)
        assertEquals("Nordlys", ready.audioState.queue.first().title)
        assertEquals("Tilføjet til køen", ready.toast?.text)
    }

    @Test
    fun enqueue_playsInsteadWhenTheRoomHasNothingToQueueBehind() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.selectAudioRoom(Room.Bedroom) // seeded idle — no track, so nothing to queue behind
        vm.enqueue(BrowseItem("Nordlys", uri = "mock://track/nordlys"), QueueMode.Next)
        advanceUntilIdle()

        // A long-press that produced neither audio nor any visible change would read as dropped.
        val ready = vm.screenState.value as HomeScreenState.Ready
        assertEquals("Nordlys", ready.audioState.nowPlaying?.title)
        assertTrue(ready.audioState.isPlaying)
    }

    @Test
    fun enqueue_failure_raisesTheToastAndStartsNothing() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(FailingEnqueueAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.enqueue(BrowseItem("Nordlys", uri = "mock://track/nordlys"), QueueMode.Next)
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertEquals("Kunne ikke tilføje til kø", ready.toast?.text)
        assertNull(ready.pendingPlay)
        assertEquals("Midnight City", ready.audioState.nowPlaying?.title)
    }

    @Test
    fun openArtist_showsTheHeaderWhileLoadingThenTheCatalogue() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(SlowArtistAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.openArtist(ARTIST_TILE)
        advanceTimeBy(ARTIST_LATENCY_MS / 2)

        // The header is up off the tapped tile long before the catalogue lands.
        val loading = assertIs<ArtistUiState.Loading>((vm.screenState.value as HomeScreenState.Ready).artist)
        assertEquals("Efterklang", loading.artist.name)

        advanceUntilIdle()
        val ready = assertIs<ArtistUiState.Ready>((vm.screenState.value as HomeScreenState.Ready).artist)
        assertEquals(listOf("Hit A", "Hit B", "Hit C"), ready.topTracks.map { it.name })
        assertEquals(listOf("Album 1"), ready.albums.map { it.name })
    }

    @Test
    fun openArtist_failure_surfacesTheFailedStateWithTheHeaderIntact() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(FailingArtistAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.openArtist(ARTIST_TILE)
        advanceUntilIdle()

        val failed = assertIs<ArtistUiState.Failed>((vm.screenState.value as HomeScreenState.Ready).artist)
        assertEquals("Efterklang", failed.artist.name)
    }

    @Test
    fun openArtist_dropsAPreviousFetchStillInFlight() = runTest(mainDispatcher) {
        val adapter = SlowArtistAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.openArtist(ARTIST_TILE)
        advanceTimeBy(ARTIST_LATENCY_MS / 2)
        vm.openArtist(BrowseItem("Anden", uri = "ytmusic://artist/2"))
        advanceUntilIdle()

        // Only the newest one is being looked at, so it — not the superseded fetch — is what shows.
        val ready = assertIs<ArtistUiState.Ready>((vm.screenState.value as HomeScreenState.Ready).artist)
        assertEquals("Anden", ready.artist.name)
    }

    @Test
    fun playTopHits_playsFromTheTappedHitWithTheOnesAboveItAtTheTail() = runTest(mainDispatcher) {
        val adapter = SlowArtistAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.openArtist(ARTIST_TILE)
        advanceUntilIdle()
        vm.playTopHits(1)
        advanceTimeBy(PLAY_LATENCY_MS / 2)

        // Playing closes the surface and puts the tapped hit up as the pending now-playing item.
        val mid = vm.screenState.value as HomeScreenState.Ready
        assertEquals(null, mid.artist)
        assertEquals("Hit B", mid.pendingPlay?.title)

        advanceUntilIdle()
        assertEquals(listOf("uri://b", "uri://c", "uri://a"), adapter.playedAll)
    }

    @Test
    fun shuffleArtist_playsEveryTopHitInSomeOrder() = runTest(mainDispatcher) {
        val adapter = SlowArtistAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.openArtist(ARTIST_TILE)
        advanceUntilIdle()
        vm.shuffleArtist()
        advanceUntilIdle()

        assertEquals(setOf("uri://a", "uri://b", "uri://c"), adapter.playedAll.toSet())
        assertEquals(3, adapter.playedAll.size)
    }

    @Test
    fun closeArtist_andPlay_bothRestoreTheBrowseSurface() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(SlowArtistAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.openArtist(ARTIST_TILE)
        advanceUntilIdle()
        vm.closeArtist()
        advanceUntilIdle()
        assertEquals(null, (vm.screenState.value as HomeScreenState.Ready).artist)

        // Tapping a plain tile from inside the surface closes it too — the pick is what to look at.
        vm.openArtist(ARTIST_TILE)
        advanceUntilIdle()
        vm.play(BrowseItem("Album 1", uri = "uri://album1"))
        advanceUntilIdle()
        assertEquals(null, (vm.screenState.value as HomeScreenState.Ready).artist)
    }

    /** A [MockAdapter] whose play intents take [PLAY_LATENCY_MS] of virtual time, like the real MA. */
    private class SlowPlayAdapter(
        private val delegate: MockAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        val queueItemsPlayed = mutableListOf<String>()

        override suspend fun play(room: Room, uri: String, radio: Boolean) {
            delay(PLAY_LATENCY_MS)
            delegate.play(room, uri, radio)
        }

        override suspend fun playQueueItem(room: Room, queueItemId: String) {
            delay(PLAY_LATENCY_MS)
            queueItemsPlayed += queueItemId
            delegate.playQueueItem(room, queueItemId)
        }
    }

    private class FailingPlayAdapter(
        delegate: HomeAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        override suspend fun play(room: Room, uri: String, radio: Boolean) =
            throw IllegalStateException("no Music Assistant connection")
    }

    private class FailingEnqueueAdapter(
        delegate: HomeAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        override suspend fun enqueue(room: Room, uri: String, mode: QueueMode) =
            throw IllegalStateException("no Music Assistant connection")
    }

    /** A play that "succeeds" without changing any device state — exercises the grace timeouts. */
    private class InertPlayAdapter(
        delegate: HomeAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        override suspend fun play(room: Room, uri: String, radio: Boolean) {}
    }

    /**
     * A [MockAdapter] answering the artist drill-in with a fixed catalogue after [ARTIST_LATENCY_MS]
     * — long enough for the [ArtistUiState.Loading] state to be observable in virtual time — and
     * recording what [playAll] was handed.
     */
    private class SlowArtistAdapter(
        private val delegate: HomeAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        val playedAll = mutableListOf<String>()

        override suspend fun artistDetail(uri: String): ArtistDetail {
            delay(ARTIST_LATENCY_MS)
            return ArtistDetail(
                topTracks = listOf(
                    BrowseItem("Hit A", uri = "uri://a", kind = BrowseKind.Track),
                    BrowseItem("Hit B", uri = "uri://b", kind = BrowseKind.Track),
                    BrowseItem("Hit C", uri = "uri://c", kind = BrowseKind.Track),
                ),
                albums = listOf(BrowseItem("Album 1", uri = "uri://album1", kind = BrowseKind.Album)),
            )
        }

        override suspend fun playAll(room: Room, uris: List<String>) {
            delay(PLAY_LATENCY_MS)
            playedAll += uris
        }
    }

    private class FailingArtistAdapter(
        delegate: HomeAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        override suspend fun artistDetail(uri: String): ArtistDetail =
            throw IllegalStateException("no Music Assistant connection")
    }

    /** Mirrors the ViewModel's own timing constants so the assertions above stay readable. */
    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        const val PLAY_LATENCY_MS = 400L
        const val TRACK_CHANGE_GRACE_MS = 5_000L
        const val ARTIST_LATENCY_MS = 300L
        const val GROUP_CHANGE_GRACE_MS = 5_000L

        val ARTIST_TILE = BrowseItem("Efterklang", uri = "ytmusic://artist/1", kind = BrowseKind.Artist)
    }

    /**
     * A [MockAdapter] whose search records its queries and answers with one fixed hit, after a delay
     * — so the in-flight [SearchState.Searching] state is actually observable in virtual time.
     */
    private class RecordingSearchAdapter(
        private val delegate: HomeAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        val queries = mutableListOf<String>()

        override suspend fun search(query: String): List<BrowseItem> {
            queries += query
            delay(SEARCH_LATENCY_MS)
            return listOf(BrowseItem("Hit", subtitle = "Kunstner", uri = "mock://track/hit"))
        }

        private companion object {
            const val SEARCH_LATENCY_MS = 100L
        }
    }

    @Test
    fun monthNav_shiftsDisplayedMonthAndStaysPinnedToFirst() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val start = (vm.screenState.value as HomeScreenState.Ready).displayedMonth
        assertEquals(1, start.day) // initialized to the first of today's month

        vm.showNextMonth()
        advanceUntilIdle()
        assertEquals(start.plus(1, DateTimeUnit.MONTH), (vm.screenState.value as HomeScreenState.Ready).displayedMonth)

        vm.showPreviousMonth()
        vm.showPreviousMonth()
        advanceUntilIdle()
        val prev = (vm.screenState.value as HomeScreenState.Ready).displayedMonth
        assertEquals(start.minus(1, DateTimeUnit.MONTH), prev)
        assertEquals(1, prev.day)
    }

    @Test
    fun selectDay_scopesEventsToThatDayAndLeavesTodosAlone() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // Seed binds 3 events to today (the initial selected day) and 3 todos across two days.
        val ready = vm.screenState.value as HomeScreenState.Ready
        assertEquals(3, ready.selectedDayEvents.size)
        assertEquals(3, ready.calendar.todos.size)
        assertTrue(ready.dayMarks.containsKey(ready.today))

        // A day the seed put no events on scopes to empty — but Opgaver is its own panel now, so the
        // checklist is unchanged by the calendar's selection.
        vm.selectDay(ready.today.plus(10, DateTimeUnit.DAY))
        advanceUntilIdle()
        val empty = vm.screenState.value as HomeScreenState.Ready
        assertTrue(empty.selectedDayEvents.isEmpty())
        assertEquals(3, empty.calendar.todos.size)
        // And the checklist's own day is untouched by the calendar's.
        assertEquals(ready.today, empty.todoDay)
    }

    @Test
    fun setCalendarView_flipsTheCalendarPanelBetweenMonthAndWeek() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        assertEquals(CalendarView.Month, (vm.screenState.value as HomeScreenState.Ready).calendarView)

        vm.setCalendarView(CalendarView.Week)
        advanceUntilIdle()
        assertEquals(CalendarView.Week, (vm.screenState.value as HomeScreenState.Ready).calendarView)
    }

    @Test
    fun weekNav_movesTheSelectedDayByAWeekAndKeepsTheMonthInStep() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val start = vm.screenState.value as HomeScreenState.Ready
        val startDay = start.selectedDay
        assertEquals(weekStart(startDay), start.weekStart)

        vm.showNextWeek()
        advanceUntilIdle()
        val next = vm.screenState.value as HomeScreenState.Ready
        assertEquals(startDay.plus(7, DateTimeUnit.DAY), next.selectedDay)
        // Weeks stay Monday-anchored, and the month grid follows the week across a month boundary,
        // so toggling back to it lands on the month just been looked at.
        assertEquals(DayOfWeek.MONDAY, next.weekStart.dayOfWeek)
        assertEquals(LocalDate(next.selectedDay.year, next.selectedDay.month.number, 1), next.displayedMonth)
        assertEquals(7, next.weekDays.size)
        assertEquals(next.weekStart, next.weekDays.first())

        vm.showPreviousWeek()
        vm.showPreviousWeek()
        advanceUntilIdle()
        val prev = vm.screenState.value as HomeScreenState.Ready
        assertEquals(startDay.minus(7, DateTimeUnit.DAY), prev.selectedDay)
        assertEquals(LocalDate(prev.selectedDay.year, prev.selectedDay.month.number, 1), prev.displayedMonth)
    }

    @Test
    fun eventsByDay_groupsEveryVisibleEventUnderItsOwnDay() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertTrue(ready.eventsByDay.isNotEmpty())
        assertTrue(ready.eventsByDay.all { (day, events) -> events.all { it.date == day } })
        // Nothing is dropped on the way into the grouping — it spans the window, not one week.
        assertEquals(ready.calendar.events.size, ready.eventsByDay.values.sumOf { it.size })
        // The week view slices its seven columns out of it.
        val days = ready.weekDays.toSet()
        assertTrue(ready.eventsByDay.keys.any { it in days })
    }

    @Test
    fun openNewEvent_opensABlankFormOnTodayWhateverMonthIsBeingBrowsed() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()
        assertNull((vm.screenState.value as HomeScreenState.Ready).eventEditor)

        // Park the grid on another month first: the "+" must not put an event on a month somebody
        // merely browsed past.
        vm.showNextMonth()
        vm.openNewEvent()
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        val target = assertIs<EventEditorTarget.New>(ready.eventEditor)
        assertEquals(ready.today, target.date)
        assertFalse(ready.savingEvent)

        vm.closeEventEditor()
        advanceUntilIdle()
        assertNull((vm.screenState.value as HomeScreenState.Ready).eventEditor)
    }

    @Test
    fun openEvent_onAWritableCalendarOpensItForEditing() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val event = (vm.screenState.value as HomeScreenState.Ready)
            .selectedDayEvents.first { it.sourceId == "calendar.matt" }
        vm.openEvent(event)
        advanceUntilIdle()

        val target = assertIs<EventEditorTarget.Existing>(
            (vm.screenState.value as HomeScreenState.Ready).eventEditor,
        )
        assertEquals(event, target.event)
        assertTrue(target.canWrite)
        // The expansion carries the whole event's bounds, which is what the form opens prefilled on.
        assertEquals(LocalDateTime(event.date, LocalTime(9, 0)), target.event.start)
    }

    @Test
    fun openEvent_onAReadOnlyCalendarOpensItWithoutWriteAccess() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // The subscribed work roster: its details are still worth reaching, they just can't change.
        val event = (vm.screenState.value as HomeScreenState.Ready)
            .selectedDayEvents.first { it.sourceId == "calendar.c_arbejde" }
        vm.openEvent(event)
        advanceUntilIdle()

        val target = assertIs<EventEditorTarget.Existing>(
            (vm.screenState.value as HomeScreenState.Ready).eventEditor,
        )
        assertFalse(target.canWrite)
    }

    @Test
    fun saveEvent_writesItAndClosesTheSurface() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val today = (vm.screenState.value as HomeScreenState.Ready).today
        vm.openNewEvent()
        vm.saveEvent("calendar.matt", draftOn(today, "Yoga"))
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertNull(ready.eventEditor)
        assertFalse(ready.savingEvent)
        assertNull(ready.toast)
        assertTrue(ready.selectedDayEvents.any { it.title == "Yoga" && it.sourceId == "calendar.matt" })
    }

    @Test
    fun saveEvent_failure_keepsTheSurfaceOpenSoNothingTypedIsLost() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(FailingCalendarWriteAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val today = (vm.screenState.value as HomeScreenState.Ready).today
        vm.openNewEvent()
        vm.saveEvent("calendar.matt", draftOn(today, "Yoga"))
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertIs<EventEditorTarget.New>(ready.eventEditor)
        assertFalse(ready.savingEvent)
        assertEquals("Kunne ikke gemme", ready.toast?.text)
    }

    @Test
    fun saveEvent_onAnOpenEventReplacesItRatherThanAppending() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val before = (vm.screenState.value as HomeScreenState.Ready)
            .selectedDayEvents.first { it.sourceId == "calendar.matt" }
        vm.openEvent(before)
        // The event's own calendar: naming another one would be a move, which is a different write.
        vm.saveEvent(before.sourceId, draftOn(before.date, "Morgenmøde (flyttet)"))
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertNull(ready.eventEditor)
        val same = ready.calendar.events.filter { it.uid == before.uid }
        assertEquals(1, same.size)
        assertEquals("Morgenmøde (flyttet)", same.single().title)
        assertEquals("calendar.matt", same.single().sourceId)
    }

    @Test
    fun saveEvent_onAnotherCalendarMovesTheEventThere() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val before = (vm.screenState.value as HomeScreenState.Ready)
            .selectedDayEvents.first { it.sourceId == "calendar.matt" }
        vm.openEvent(before)
        vm.saveEvent("calendar.cecilie", draftOn(before.date, "Morgenmøde"))
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertNull(ready.eventEditor)
        assertNull(ready.toast)
        // A move is a create plus a delete, so the old row is gone and the new one is a new event —
        // same title on the other calendar, addressed by a uid of its own.
        assertTrue(ready.calendar.events.none { it.uid == before.uid })
        val moved = ready.calendar.events.single { it.title == "Morgenmøde" }
        assertEquals("calendar.cecilie", moved.sourceId)
    }

    @Test
    fun saveEvent_movingOneOccurrenceLiftsItOutOfTheSeries() = runTest(mainDispatcher) {
        val recorder = RecordingCalendarAdapter()
        val vm = HomepageViewModel(recorder)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val occurrence = (vm.screenState.value as HomeScreenState.Ready)
            .calendar.events.first { it.uid == "seed-6" && it.recurrenceId != null }

        vm.openEvent(occurrence)
        vm.saveEvent(
            "calendar.cecilie",
            draftOn(occurrence.date, "Fredagshygge").copy(rrule = "FREQ=WEEKLY"),
            scope = EventEditScope.ThisEvent,
        )
        advanceUntilIdle()

        // The copy is a plain event: one occurrence cannot carry the series' repetition rule.
        assertEquals("calendar.cecilie", recorder.lastCreate?.first)
        assertNull(recorder.lastCreate?.second?.rrule)
        // And only that occurrence leaves the old series behind.
        assertEquals(occurrence.recurrenceId to RecurrenceRange.ThisEvent, recorder.lastDelete)
    }

    @Test
    fun saveEvent_movingAWholeSeriesCarriesItsRepetitionAcross() = runTest(mainDispatcher) {
        val recorder = RecordingCalendarAdapter()
        val vm = HomepageViewModel(recorder)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val occurrence = (vm.screenState.value as HomeScreenState.Ready)
            .calendar.events.first { it.uid == "seed-6" && it.recurrenceId != null }

        vm.openEvent(occurrence)
        vm.saveEvent(
            "calendar.cecilie",
            draftOn(occurrence.date, "Fredagshygge").copy(rrule = "FREQ=WEEKLY"),
            scope = EventEditScope.AllEvents,
        )
        advanceUntilIdle()

        assertEquals("FREQ=WEEKLY", recorder.lastCreate?.second?.rrule)
        // "Alle begivenheder" drops the occurrence id — that is how the old series itself is named.
        assertEquals(null to RecurrenceRange.ThisEvent, recorder.lastDelete)
        assertTrue((vm.screenState.value as HomeScreenState.Ready).calendar.events.none { it.uid == "seed-6" })
    }

    @Test
    fun saveEvent_moveThatFailsToWriteKeepsTheSurfaceOpen() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(FailingCalendarWriteAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val before = (vm.screenState.value as HomeScreenState.Ready)
            .selectedDayEvents.first { it.sourceId == "calendar.matt" }
        vm.openEvent(before)
        vm.saveEvent("calendar.cecilie", draftOn(before.date, "Morgenmøde"))
        advanceUntilIdle()

        // The create is what failed, so nothing was deleted either — the event is still where it was.
        val ready = vm.screenState.value as HomeScreenState.Ready
        assertIs<EventEditorTarget.Existing>(ready.eventEditor)
        assertEquals("Kunne ikke gemme", ready.toast?.text)
        assertTrue(ready.calendar.events.any { it.uid == before.uid })
    }

    @Test
    fun saveEvent_moveWhoseDeleteFailsClosesTheSurfaceAndNamesTheCopyLeftBehind() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(FailingCalendarDeleteAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val before = (vm.screenState.value as HomeScreenState.Ready)
            .selectedDayEvents.first { it.sourceId == "calendar.matt" }
        vm.openEvent(before)
        vm.saveEvent("calendar.cecilie", draftOn(before.date, "Morgenmøde"))
        advanceUntilIdle()

        // The create landed, so the save is over: the surface closes rather than inviting a Save that
        // would write a second copy onto the new calendar.
        val ready = vm.screenState.value as HomeScreenState.Ready
        assertNull(ready.eventEditor)
        assertEquals("Flyttet, men den gamle blev ikke slettet", ready.toast?.text)
        // And the toast is true on both counts — the copy is there and the original stayed.
        assertEquals("calendar.cecilie", ready.calendar.events.single { it.title == "Morgenmøde" }.sourceId)
        assertTrue(ready.calendar.events.any { it.uid == before.uid })
    }

    @Test
    fun saveEvent_movingASeriesWithNoNamedOccurrenceMovesTheWholeSeries() = runTest(mainDispatcher) {
        val recorder = RecordingCalendarAdapter()
        val vm = HomepageViewModel(recorder)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // A recurring event whose occurrences have no id of their own yet — the shape a still-queued
        // recurring create is drawn in, before Home Assistant has expanded it.
        val unexpanded = (vm.screenState.value as HomeScreenState.Ready)
            .calendar.events.first { it.uid == "seed-6" && it.recurrenceId != null }
            .copy(recurrenceId = null, rrule = "FREQ=WEEKLY")

        vm.openEvent(unexpanded)
        vm.saveEvent(
            "calendar.cecilie",
            draftOn(unexpanded.date, "Fredagshygge").copy(rrule = "FREQ=WEEKLY"),
            scope = EventEditScope.ThisEvent,
        )
        advanceUntilIdle()

        // There is no single occurrence to name, so the delete takes the series — and the copy has to
        // be the series too, rather than the one plain event "Denne begivenhed" would otherwise make.
        assertEquals(null to RecurrenceRange.ThisEvent, recorder.lastDelete)
        assertEquals("FREQ=WEEKLY", recorder.lastCreate?.second?.rrule)
    }

    @Test
    fun saveEvent_moveCarriesTheReminderOntoTheCopy() = runTest(mainDispatcher) {
        val recorder = RecordingCalendarAdapter()
        val vm = HomepageViewModel(recorder)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val before = (vm.screenState.value as HomeScreenState.Ready)
            .selectedDayEvents.first { it.sourceId == "calendar.matt" }
        vm.openEvent(before)
        vm.saveEvent("calendar.cecilie", draftOn(before.date, "Morgenmøde"), reminder = ReminderRule(15))
        advanceUntilIdle()

        // The rule was keyed on a uid the event has just lost, so it is written again — on the new
        // calendar, against the uid the copy was given there.
        val (sourceId, uid, rule) = assertNotNull(recorder.lastReminder)
        assertEquals("calendar.cecilie", sourceId)
        assertEquals(ReminderRule(15), rule)
        assertNotEquals(before.uid, uid)
    }

    @Test
    fun saveEvent_scopeDecidesWhichOccurrencesTheWriteAddresses() = runTest(mainDispatcher) {
        val recorder = RecordingCalendarAdapter()
        val vm = HomepageViewModel(recorder)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val occurrence = (vm.screenState.value as HomeScreenState.Ready)
            .calendar.events.first { it.uid == "seed-6" && it.recurrenceId != null }

        vm.openEvent(occurrence)
        vm.saveEvent(occurrence.sourceId, draftOn(occurrence.date, "Fredagshygge"), scope = EventEditScope.ThisEvent)
        advanceUntilIdle()
        assertEquals(occurrence.recurrenceId to RecurrenceRange.ThisEvent, recorder.lastUpdate)

        vm.openEvent(occurrence)
        vm.saveEvent(occurrence.sourceId, draftOn(occurrence.date, "Fredagshygge"), scope = EventEditScope.ThisAndFuture)
        advanceUntilIdle()
        assertEquals(occurrence.recurrenceId to RecurrenceRange.ThisAndFuture, recorder.lastUpdate)

        // "Alle begivenheder" drops the occurrence id entirely — that is how the series itself is named.
        vm.openEvent(occurrence)
        vm.saveEvent(occurrence.sourceId, draftOn(occurrence.date, "Fredagshygge"), scope = EventEditScope.AllEvents)
        advanceUntilIdle()
        assertEquals(null to RecurrenceRange.ThisEvent, recorder.lastUpdate)
    }

    @Test
    fun deleteEvent_scopeDecidesWhichOccurrencesAreRemoved() = runTest(mainDispatcher) {
        val recorder = RecordingCalendarAdapter()
        val vm = HomepageViewModel(recorder)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val occurrence = (vm.screenState.value as HomeScreenState.Ready)
            .calendar.events.first { it.uid == "seed-6" && it.recurrenceId != null }

        vm.openEvent(occurrence)
        vm.deleteEvent(EventEditScope.ThisAndFuture)
        advanceUntilIdle()
        assertEquals(occurrence.recurrenceId to RecurrenceRange.ThisAndFuture, recorder.lastDelete)

        // The detail popup's trash has no scope to offer, so it stays on the single occurrence.
        val next = (vm.screenState.value as HomeScreenState.Ready)
            .calendar.events.first { it.uid == "seed-6" && it.recurrenceId != null }
        vm.openEventDetail(next)
        vm.deleteEventDetail()
        advanceUntilIdle()
        assertEquals(next.recurrenceId to RecurrenceRange.ThisEvent, recorder.lastDelete)
    }

    @Test
    fun moveEvent_writesTheDroppedSlotAndKeepsTheDuration() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val before = (vm.screenState.value as HomeScreenState.Ready).let { ready ->
            ready.calendar.events.first { canDragEvent(it, ready.calendar.sources) && it.rrule == null }
        }
        val span = before.endMinute!! - before.startMinute!!
        val onto = before.date.plus(1, DateTimeUnit.DAY)

        vm.moveEvent(EventMove(before, onto, 9 * 60))
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        val after = ready.calendar.events.single { it.uid == before.uid }
        assertEquals(onto, after.date)
        assertEquals(9 * 60, after.startMinute)
        assertEquals(9 * 60 + span, after.endMinute)
        // A one-off is written without asking anything, and the hold is let go once it has landed.
        assertNull(ready.eventMove)
    }

    @Test
    fun moveEvent_onARecurringOccurrenceAsksBeforeWriting() = runTest(mainDispatcher) {
        val recorder = RecordingCalendarAdapter()
        val vm = HomepageViewModel(recorder)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val occurrence = (vm.screenState.value as HomeScreenState.Ready)
            .calendar.events.first { it.uid == "seed-6" && it.recurrenceId != null }
        val onto = occurrence.date.plus(1, DateTimeUnit.DAY)

        vm.moveEvent(EventMove(occurrence, onto, 9 * 60))
        advanceUntilIdle()

        // Nothing is written until the scope card is answered — but the block is already drawn where
        // it was dropped, so it does not sit in its old slot behind the popup.
        assertNull(recorder.lastUpdate)
        val asking = vm.screenState.value as HomeScreenState.Ready
        assertTrue(asking.eventMove?.awaitingScope == true)
        assertTrue(asking.eventsByDay[onto].orEmpty().any { it.uid == "seed-6" && it.startMinute == 9 * 60 })

        vm.pickEventMoveScope(EventEditScope.ThisAndFuture)
        advanceUntilIdle()
        assertEquals(occurrence.recurrenceId to RecurrenceRange.ThisAndFuture, recorder.lastUpdate)
        assertNull((vm.screenState.value as HomeScreenState.Ready).eventMove)
    }

    @Test
    fun cancelEventMove_putsTheBlockBackAndWritesNothing() = runTest(mainDispatcher) {
        val recorder = RecordingCalendarAdapter()
        val vm = HomepageViewModel(recorder)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val occurrence = (vm.screenState.value as HomeScreenState.Ready)
            .calendar.events.first { it.uid == "seed-6" && it.recurrenceId != null }

        vm.moveEvent(EventMove(occurrence, occurrence.date.plus(1, DateTimeUnit.DAY), 9 * 60))
        advanceUntilIdle()
        vm.cancelEventMove()
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertNull(ready.eventMove)
        assertNull(recorder.lastUpdate)
        assertTrue(ready.eventsByDay[occurrence.date].orEmpty().any { it.uid == "seed-6" })
    }

    @Test
    fun moveEvent_failure_leavesTheEventWhereItWasAndSaysSo() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(FailingCalendarUpdateAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val before = (vm.screenState.value as HomeScreenState.Ready).let { ready ->
            ready.calendar.events.first { canDragEvent(it, ready.calendar.sources) && it.rrule == null }
        }
        vm.moveEvent(EventMove(before, before.date.plus(1, DateTimeUnit.DAY), 9 * 60))
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        // The hold is dropped, so the block goes back to the slot the backend still holds it at.
        assertNull(ready.eventMove)
        assertEquals(before, ready.calendar.events.single { it.uid == before.uid })
        assertEquals("Kunne ikke gemme", ready.toast?.text)
    }

    @Test
    fun deleteEvent_removesItAndClosesTheSurface() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val event = (vm.screenState.value as HomeScreenState.Ready)
            .selectedDayEvents.first { it.sourceId == "calendar.matt" }
        vm.openEvent(event)
        vm.deleteEvent()
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertNull(ready.eventEditor)
        assertTrue(ready.calendar.events.none { it.uid == event.uid })
    }

    @Test
    fun openEventDetail_thenEdit_handsTheSameEventToTheEditorAndClosesThePopup() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val event = (vm.screenState.value as HomeScreenState.Ready)
            .selectedDayEvents.first { it.sourceId == "calendar.matt" }
        vm.openEventDetail(event)
        advanceUntilIdle()

        val opened = vm.screenState.value as HomeScreenState.Ready
        assertEquals(event, opened.eventDetail)
        assertNull(opened.eventEditor)

        vm.editEventDetail()
        advanceUntilIdle()

        val editing = vm.screenState.value as HomeScreenState.Ready
        assertNull(editing.eventDetail)
        val target = assertIs<EventEditorTarget.Existing>(editing.eventEditor)
        assertEquals(event, target.event)
        assertTrue(target.canWrite)
    }

    @Test
    fun closeEventDetail_leavesTheEventAlone() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val event = (vm.screenState.value as HomeScreenState.Ready).selectedDayEvents.first()
        vm.openEventDetail(event)
        vm.closeEventDetail()
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertNull(ready.eventDetail)
        assertNull(ready.eventEditor)
        assertTrue(ready.calendar.events.any { it.uid == event.uid })
    }

    @Test
    fun deleteEventDetail_removesItAndClosesThePopup() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val event = (vm.screenState.value as HomeScreenState.Ready)
            .selectedDayEvents.first { it.sourceId == "calendar.matt" }
        vm.openEventDetail(event)
        vm.deleteEventDetail()
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertNull(ready.eventDetail)
        assertFalse(ready.savingEvent)
        assertTrue(ready.calendar.events.none { it.uid == event.uid })
    }

    @Test
    fun deleteEventDetail_failure_keepsThePopupOpenAndSaysSo() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(FailingCalendarDeleteAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val event = (vm.screenState.value as HomeScreenState.Ready)
            .selectedDayEvents.first { it.sourceId == "calendar.matt" }
        vm.openEventDetail(event)
        vm.deleteEventDetail()
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertEquals(event, ready.eventDetail)
        assertFalse(ready.savingEvent)
        assertEquals("Kunne ikke slette", ready.toast?.text)
    }

    @Test
    fun toggleCalendarFilter_appliesToTheViewBeingShownAndIsWrittenThrough() = runTest(mainDispatcher) {
        val store = InMemoryCalendarFilterStore()
        val vm = HomepageViewModel(MockAdapter(), store)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.toggleCalendarFilter("calendar.c_arbejde")
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertEquals(setOf("calendar.c_arbejde"), ready.calendarFilters.hidden(CalendarView.Month))
        assertTrue(ready.calendarFilters.hidden(CalendarView.Week).isEmpty())
        // Written through on every change — a wall tablet is restarted by a power cut, not by anyone
        // closing a settings screen.
        assertEquals(ready.calendarFilters, store.read())
    }

    @Test
    fun hiddenCalendar_dropsOutOfEverythingTheViewShows_andOnlyThatView() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter(), InMemoryCalendarFilterStore())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val roster = "calendar.c_arbejde"
        val before = vm.screenState.value as HomeScreenState.Ready
        assertTrue(before.selectedDayEvents.any { it.sourceId == roster })
        assertTrue(before.dayMarks.getValue(before.today).sourceIds.contains(roster))

        vm.toggleCalendarFilter(roster)
        advanceUntilIdle()

        // Month view: the agenda rows *and* the grid's dots.
        val month = vm.screenState.value as HomeScreenState.Ready
        assertTrue(month.selectedDayEvents.none { it.sourceId == roster })
        assertTrue(month.dayMarks.values.none { roster in it.sourceIds })

        // The week keeps its own setting — the roster is the point of that grid.
        vm.setCalendarView(CalendarView.Week)
        advanceUntilIdle()
        val week = vm.screenState.value as HomeScreenState.Ready
        assertTrue(week.eventsByDay.values.flatten().any { it.sourceId == roster })
    }

    @Test
    fun calendarSettings_stepThroughTheMenuAndBackOutOfIt() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()
        fun settings() = (vm.screenState.value as HomeScreenState.Ready).calendarSettings
        assertNull(settings())

        // The gear opens the list, not a calendar.
        vm.openCalendarSettings()
        advanceUntilIdle()
        assertEquals(CalendarSettingsRoute.Calendars, settings())

        vm.openCalendarSettingsRoute(CalendarSettingsRoute.Calendar("calendar.test"))
        advanceUntilIdle()
        assertEquals(CalendarSettingsRoute.Calendar("calendar.test"), settings())

        // Back is one level at a time, all the way out.
        vm.backFromCalendarSettings()
        advanceUntilIdle()
        assertEquals(CalendarSettingsRoute.Calendars, settings())

        vm.backFromCalendarSettings()
        advanceUntilIdle()
        assertNull(settings())
    }

    @Test
    fun showCalendarViews_leavesBothTheSettingsAndTheEditor() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // Inside a calendar's page: the Kalender tab is a way out, not one more thing to back out of.
        vm.openCalendarSettings()
        vm.openCalendarSettingsRoute(CalendarSettingsRoute.Calendar("calendar.test"))
        vm.showCalendarViews()
        advanceUntilIdle()
        assertNull((vm.screenState.value as HomeScreenState.Ready).calendarSettings)

        vm.openNewEvent()
        advanceUntilIdle()
        assertNotNull((vm.screenState.value as HomeScreenState.Ready).eventEditor)

        vm.showCalendarViews()
        advanceUntilIdle()
        assertNull((vm.screenState.value as HomeScreenState.Ready).eventEditor)
    }

    @Test
    fun todoPage_sinksATickedRowOutOfWhatIsStillOpen() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val today = (vm.screenState.value as HomeScreenState.Ready).today
        vm.addTodo(today, "Nyeste opgave")
        advanceUntilIdle()

        val added = vm.screenState.value as HomeScreenState.Ready
        val first = todoPage(added.calendar.todos, added.todoDay).open.first().items.first()
        vm.toggleTodo(first.id)
        advanceUntilIdle()

        val after = vm.screenState.value as HomeScreenState.Ready
        val page = todoPage(after.calendar.todos, after.todoDay)
        assertTrue(page.open.none { g -> g.items.any { it.id == first.id } })
        assertTrue(page.done.any { g -> g.items.any { it.id == first.id } })
    }

    @Test
    fun showTodoDay_pagesTheChecklistWithoutMovingTheCalendar() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val start = vm.screenState.value as HomeScreenState.Ready
        vm.showTodoDay(start.today.plus(2, DateTimeUnit.DAY))
        advanceUntilIdle()

        val moved = vm.screenState.value as HomeScreenState.Ready
        assertEquals(start.today.plus(2, DateTimeUnit.DAY), moved.todoDay)
        // The two panels swipe independently — the calendar's day is its own.
        assertEquals(start.selectedDay, moved.selectedDay)
    }

    @Test
    fun selectPanel_putsOpgaverBackOnToday() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val start = vm.screenState.value as HomeScreenState.Ready
        vm.showTodoDay(start.today.plus(5, DateTimeUnit.DAY))
        vm.selectPanel(Panel.Media)
        advanceUntilIdle()
        // Leaving keeps it where it was swiped to...
        assertEquals(start.today.plus(5, DateTimeUnit.DAY), (vm.screenState.value as HomeScreenState.Ready).todoDay)

        // ...and coming back asks what is outstanding now, not where the last reader left it.
        vm.selectPanel(Panel.Opgaver)
        advanceUntilIdle()
        assertEquals(start.today, (vm.screenState.value as HomeScreenState.Ready).todoDay)
    }

    /** A one-hour event on [day], the shape the editor's wheels hand up. */
    private fun draftOn(day: LocalDate, title: String): CalendarEventDraft = buildEventDraft(
        summary = title,
        start = LocalDateTime(day, LocalTime(8, 30)),
        end = LocalDateTime(day, LocalTime(9, 30)),
        allDay = false,
        location = null,
    )

    /** Records how a scoped write was addressed, while still performing it against the mock store. */
    private class RecordingCalendarAdapter(
        private val delegate: HomeAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        var lastUpdate: Pair<String?, RecurrenceRange>? = null
        var lastDelete: Pair<String?, RecurrenceRange>? = null

        /** The calendar a create was addressed to and the draft it carried — the move's first half. */
        var lastCreate: Pair<String, CalendarEventDraft>? = null

        /** The calendar, uid and rule a reminder was last written against. */
        var lastReminder: Triple<String, String, ReminderRule?>? = null

        override suspend fun createEvent(sourceId: String, draft: CalendarEventDraft) {
            lastCreate = sourceId to draft
            delegate.createEvent(sourceId, draft)
        }

        override suspend fun setEventReminder(
            sourceId: String,
            uid: String,
            recurrenceId: String?,
            rule: ReminderRule?,
        ) {
            lastReminder = Triple(sourceId, uid, rule)
            delegate.setEventReminder(sourceId, uid, recurrenceId, rule)
        }

        override suspend fun updateEvent(
            sourceId: String,
            uid: String,
            draft: CalendarEventDraft,
            recurrenceId: String?,
            range: RecurrenceRange,
        ) {
            lastUpdate = recurrenceId to range
            delegate.updateEvent(sourceId, uid, draft, recurrenceId, range)
        }

        override suspend fun deleteEvent(
            sourceId: String,
            uid: String,
            recurrenceId: String?,
            range: RecurrenceRange,
        ) {
            lastDelete = recurrenceId to range
            delegate.deleteEvent(sourceId, uid, recurrenceId, range)
        }
    }

    /** Writes that fail the way a Home Assistant round trip does when the connection is down. */
    private class FailingCalendarWriteAdapter(
        delegate: HomeAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        override suspend fun createEvent(sourceId: String, draft: CalendarEventDraft): Unit =
            throw IllegalStateException("no Home Assistant connection")
    }

    /** The same, for the update path — what a dropped block runs into offline. */
    private class FailingCalendarUpdateAdapter(
        delegate: HomeAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        override suspend fun updateEvent(
            sourceId: String,
            uid: String,
            draft: CalendarEventDraft,
            recurrenceId: String?,
            range: RecurrenceRange,
        ): Unit = throw IllegalStateException("no Home Assistant connection")
    }

    /** The same, for the delete path — what the detail popup's trash runs into offline. */
    private class FailingCalendarDeleteAdapter(
        delegate: HomeAdapter = MockAdapter(),
    ) : HomeAdapter by delegate {
        override suspend fun deleteEvent(
            sourceId: String,
            uid: String,
            recurrenceId: String?,
            range: RecurrenceRange,
        ): Unit = throw IllegalStateException("no Home Assistant connection")
    }

    @Test
    fun addTodo_forwardsToAdapterAndSurfacesInTheChecklist() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val today = (vm.screenState.value as HomeScreenState.Ready).today
        vm.addTodo(today, "Støvsug")
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertTrue(ready.calendar.todos.any { it.label == "Støvsug" })
    }
}
