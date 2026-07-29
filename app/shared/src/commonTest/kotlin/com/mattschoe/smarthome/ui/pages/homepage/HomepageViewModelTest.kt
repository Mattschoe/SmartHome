package com.mattschoe.smarthome.ui.pages.homepage

import com.mattschoe.smarthome.data.HomeAdapter
import com.mattschoe.smarthome.data.MockAdapter
import com.mattschoe.smarthome.data.model.ArtistDetail
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.BrowseKind
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
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
    fun joinAction_isOfferedOnlyForARoomThatHasMusicToJoin() = runTest(mainDispatcher) {
        // Seed: the Living Room is playing, the Bedroom idle.
        val vm = HomepageViewModel(RecordingGroupAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // Viewing the playing room, the idle one has nothing to join — no action shows.
        val fromPlayingRoom = vm.screenState.value as HomeScreenState.Ready
        assertEquals(Room.Bedroom, fromPlayingRoom.otherAudioRoom)
        assertNull(fromPlayingRoom.joinTarget)

        // Viewing the idle room, the playing one is exactly what there is to join.
        vm.selectAudioRoom(Room.Bedroom)
        advanceUntilIdle()
        assertEquals(Room.LivingRoom, (vm.screenState.value as HomeScreenState.Ready).joinTarget)
    }

    @Test
    fun toggleAudioJoin_adoptsTheOtherRoomsMusicByFollowingIt() = runTest(mainDispatcher) {
        val adapter = RecordingGroupAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        vm.selectAudioRoom(Room.Bedroom) // the idle room; the Living Room is what's playing
        advanceUntilIdle()

        vm.toggleAudioJoin()
        advanceUntilIdle()

        // "Join Stue" means playing *its* music, so it leads and the viewed room follows.
        assertEquals(listOf(Room.LivingRoom to Room.Bedroom), adapter.joins)
        val joined = vm.screenState.value as HomeScreenState.Ready
        assertTrue(joined.audioJoined)
        // The action stays up as "Leave Stue" — a group can always be taken apart again.
        assertEquals(Room.LivingRoom, joined.joinTarget)
    }

    @Test
    fun toggleAudioJoin_dropsAJoinWhenTheOtherRoomHasNothingPlaying() = runTest(mainDispatcher) {
        val adapter = RecordingGroupAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // Viewing the playing room: the action isn't offered, and a tap that raced it is dropped.
        vm.toggleAudioJoin()
        advanceUntilIdle()

        assertTrue(adapter.joins.isEmpty())
        assertEquals(false, (vm.screenState.value as HomeScreenState.Ready).audioJoined)
    }

    @Test
    fun toggleAudioJoin_unjoinsTheFollowerWhenTheRoomsAreAlreadyJoined() = runTest(mainDispatcher) {
        val adapter = RecordingGroupAdapter()
        val vm = HomepageViewModel(adapter)
        backgroundScope.launch { vm.screenState.collect {} }
        vm.selectAudioRoom(Room.Bedroom)
        advanceUntilIdle()

        vm.toggleAudioJoin()
        advanceUntilIdle()
        vm.toggleAudioJoin()
        advanceUntilIdle()

        // Leaving drops the follower, so the leader's playback is left alone.
        assertEquals(listOf(Room.Bedroom), adapter.unjoins)
        assertEquals(false, (vm.screenState.value as HomeScreenState.Ready).audioJoined)
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
    fun selectDay_scopesEventsAndTodosToThatDay() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        // Seed binds 2 events + 2 todos to today (the initial selected day).
        val ready = vm.screenState.value as HomeScreenState.Ready
        assertEquals(2, ready.selectedDayEvents.size)
        assertEquals(2, ready.selectedDayTodos.size)
        assertTrue(ready.daysWithItems.contains(ready.today.day))

        // A day the seed put nothing on scopes to empty.
        vm.selectDay(ready.today.plus(10, DateTimeUnit.DAY))
        advanceUntilIdle()
        val empty = vm.screenState.value as HomeScreenState.Ready
        assertTrue(empty.selectedDayEvents.isEmpty())
        assertTrue(empty.selectedDayTodos.isEmpty())
    }

    @Test
    fun addTodo_forwardsToAdapterAndSurfacesOnSelectedDay() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        val today = (vm.screenState.value as HomeScreenState.Ready).today
        vm.addTodo(today, "Støvsug")
        advanceUntilIdle()

        val ready = vm.screenState.value as HomeScreenState.Ready
        assertTrue(ready.selectedDayTodos.any { it.label == "Støvsug" })
    }
}
