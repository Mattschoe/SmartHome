package com.mattschoe.smarthome.ui.pages.homepage

import com.mattschoe.smarthome.data.MockAdapter
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        assertTrue(ready.keepListening.isNotEmpty())
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
