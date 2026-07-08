package com.mattschoe.smarthome.ui.pages.homepage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattschoe.smarthome.data.HomeAdapter
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Dashboard state holder. Owns the UI-selection state and combines it with the [HomeAdapter]'s
 * device data into a single [screenState] the UI collects. Light and audio rooms are selected
 * **independently** (the top chips vs. the AUDIO chips); [_panel] is the right-card tab. Device
 * intents forward to the adapter; selection intents mutate the ViewModel-owned flows.
 */
class HomepageViewModel(private val adapter: HomeAdapter,) : ViewModel() {
    private val _activeLightRoom = MutableStateFlow(Room.LivingRoom)
    private val _activeAudioRoom = MutableStateFlow(Room.audioRooms.firstOrNull() ?: Room.LivingRoom)
    private val _panel = MutableStateFlow(Panel.Media)

    val screenState: StateFlow<HomeScreenState> =
        combine(
            adapter.subscribe(),
            _activeLightRoom,
            _activeAudioRoom,
            _panel
        ) { home, lightRoom, audioRoom, panel ->
            HomeScreenState.Ready(
                activeLightRoom = lightRoom,
                activeAudioRoom = audioRoom,
                rooms = home.rooms,
                panel = panel,
                climate = home.climate,
                playlists = home.playlists,
                calendar = home.calendar,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeScreenState.Loading
        )

    fun selectLightRoom(room: Room) { _activeLightRoom.value = room }
    fun selectAudioRoom(room: Room) { _activeAudioRoom.value = room }
    fun selectPanel(panel: Panel) { _panel.value = panel }

    fun setBrightness(room: Room, value: Int) = adapter.setBrightness(room, value)
    fun setWarmth(room: Room, warmth: Warmth) = adapter.setWarmth(room, warmth)
    fun setVolume(room: Room, value: Int) = adapter.setVolume(room, value)
    fun toggleLight(room: Room) = adapter.toggleLight(room)
}
