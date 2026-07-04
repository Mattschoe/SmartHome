package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.HomeState
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.flow.StateFlow

/**
 * The device-data boundary. UI observes [subscribe] and issues device intents through the setters;
 * concrete adapters own the state store and mutate it. Climate is read-only (no setter), it is
 * display-only. UI-selection state (active room, panel) is not device data and lives in the ViewModel.
 */
// TODO(Phase 9): add a HomeAssistantAdapter (WebSocket/REST) implementing this same interface and
//  swap it in AppContainer.
interface HomeAdapter {
    fun subscribe(): StateFlow<HomeState>

    fun setBrightness(room: Room, value: Int)
    fun setWarmth(room: Room, warmth: Warmth)
    fun setVolume(room: Room, value: Int)
    fun toggleLight(room: Room)
}
