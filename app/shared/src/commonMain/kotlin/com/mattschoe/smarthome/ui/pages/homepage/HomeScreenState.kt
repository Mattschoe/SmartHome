package com.mattschoe.smarthome.ui.pages.homepage

import com.mattschoe.smarthome.data.model.CalendarState
import com.mattschoe.smarthome.data.model.ClimateState
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.Playlist
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState

/**
 * The state the dashboard UI collects: device data from the `HomeAdapter` combined with the
 * ViewModel-owned UI selection ([Ready.activeRoom]/[Ready.panel]).
 */
sealed interface HomeScreenState {
    data object Loading : HomeScreenState

    data class Ready(
        val activeRoom: Room,
        val rooms: Map<Room, RoomState>,
        val panel: Panel,
        val climate: ClimateState,
        val playlists: List<Playlist>,
        val calendar: CalendarState,
    ) : HomeScreenState {
        /** The currently-selected room's device state. */
        val activeRoomState: RoomState get() = rooms.getValue(activeRoom)
    }
}
