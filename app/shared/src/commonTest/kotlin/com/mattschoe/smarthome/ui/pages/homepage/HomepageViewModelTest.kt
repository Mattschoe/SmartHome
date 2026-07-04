package com.mattschoe.smarthome.ui.pages.homepage

import com.mattschoe.smarthome.data.MockAdapter
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
    fun selectRoomAndPanel_surfaceIntoScreenState() = runTest(mainDispatcher) {
        val vm = HomepageViewModel(MockAdapter())
        // WhileSubscribed only emits Ready while collected.
        backgroundScope.launch { vm.screenState.collect {} }
        advanceUntilIdle()

        vm.selectRoom(Room.Kitchen)
        vm.selectPanel(Panel.Calendar)
        advanceUntilIdle()

        val ready = vm.screenState.value
        assertIs<HomeScreenState.Ready>(ready)
        assertEquals(Room.Kitchen, ready.activeRoom)
        assertEquals(Panel.Calendar, ready.panel)
        assertEquals(ready.rooms.getValue(Room.Kitchen), ready.activeRoomState)
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
}
