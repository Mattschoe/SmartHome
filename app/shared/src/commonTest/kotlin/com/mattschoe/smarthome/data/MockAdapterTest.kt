package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockAdapterTest {

    @Test
    fun subscribe_reflectsSeed() {
        val adapter = MockAdapter()
        assertEquals(seedHome(), adapter.subscribe().value)
    }

    @Test
    fun setBrightness_updatesFlowOptimistically() {
        val adapter = MockAdapter()
        val flow = adapter.subscribe()
        adapter.setBrightness(Room.Bedroom, 55)
        assertEquals(55, flow.value.rooms.getValue(Room.Bedroom).brightnessPct)
        assertTrue(flow.value.rooms.getValue(Room.Bedroom).isLightOn)
    }

    @Test
    fun mutations_onlyTouchTargetRoom() {
        val adapter = MockAdapter()
        val kitchenBefore = adapter.subscribe().value.rooms.getValue(Room.Kitchen)
        adapter.setVolume(Room.LivingRoom, 90)
        assertEquals(kitchenBefore, adapter.subscribe().value.rooms.getValue(Room.Kitchen))
        assertEquals(90, adapter.subscribe().value.rooms.getValue(Room.LivingRoom).volumePct)
    }

    @Test
    fun setWarmth_appliesToRoom() {
        val adapter = MockAdapter()
        adapter.setWarmth(Room.Hall, Warmth.Cool)
        val state = adapter.subscribe().value
        assertEquals(Warmth.Cool, state.rooms.getValue(Room.Hall).lightWarmth)
        assertTrue(state.rooms.getValue(Room.Hall).isLightOn)
    }

    @Test
    fun climate_isReadOnlyAcrossMutations() {
        val adapter = MockAdapter()
        val climate = adapter.subscribe().value.climate
        adapter.setBrightness(Room.Kitchen, 10)
        adapter.toggleLight(Room.Bedroom)
        adapter.setVolume(Room.LivingRoom, 70)
        assertEquals(climate, adapter.subscribe().value.climate)
    }
}
