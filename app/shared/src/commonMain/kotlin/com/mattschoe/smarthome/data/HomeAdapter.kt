package com.mattschoe.smarthome.data

import com.mattschoe.smarthome.data.model.HomeState
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

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

    // Transport intents — 1:1 with Home Assistant media_player services. No-op on speaker-less rooms.
    fun togglePlay(room: Room)
    fun next(room: Room)
    fun previous(room: Room)
    fun seek(room: Room, positionSec: Int)
    fun setShuffle(room: Room, shuffle: Boolean)
    fun setRepeat(room: Room, mode: RepeatMode)

    // Todo intents — 1:1 with Home Assistant todo services. The adapter mints the id on add (HA `uid`);
    // editing a todo to a blank label removes it (todo.remove_item), the escape hatch for delete.
    fun addTodo(due: LocalDate, label: String)
    fun toggleTodo(id: String)
    fun editTodo(id: String, label: String)
}
