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
 * Dashboard state holder. Owns the UI-selection state (active room, panel) and combines it with the
 * [HomeAdapter]'s device data into a single [screenState] the UI collects. Device intents forward to
 * the adapter; selection intents mutate the ViewModel-owned flows.
 */
class HomepageViewModel(private val adapter: HomeAdapter,) : ViewModel() {
    private val _activeRoom = MutableStateFlow(Room.LivingRoom)
    private val _panel = MutableStateFlow(Panel.Media)

    val screenState: StateFlow<HomeScreenState> =
        combine(
            adapter.subscribe(),
            _activeRoom,
            _panel
        ) { home, activeRoom, panel ->
            HomeScreenState.Ready(
                activeRoom = activeRoom,
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

    fun selectRoom(room: Room) { _activeRoom.value = room }
    fun selectPanel(panel: Panel) { _panel.value = panel }

    fun setBrightness(room: Room, value: Int) = adapter.setBrightness(room, value)
    fun setWarmth(room: Room, warmth: Warmth) = adapter.setWarmth(room, warmth)
    fun setVolume(room: Room, value: Int) = adapter.setVolume(room, value)
    fun toggleLight(room: Room) = adapter.toggleLight(room)
}
