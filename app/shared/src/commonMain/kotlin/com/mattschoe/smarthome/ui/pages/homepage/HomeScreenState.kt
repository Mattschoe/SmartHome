package com.mattschoe.smarthome.ui.pages.homepage

import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.CalendarState
import com.mattschoe.smarthome.data.model.ClimateState
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.Playlist
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState

/**
 * The state the dashboard UI collects: device data from the `HomeAdapter` combined with the
 * ViewModel-owned UI selection. Light and audio are selected **independently** — the top chip row
 * picks [Ready.activeLightRoom] (dial/warmth), the AUDIO chip row picks [Ready.activeAudioRoom]
 * (volume, and later the Media panel). Neither drives the other.
 */
sealed interface HomeScreenState {
    data object Loading : HomeScreenState

    data class Ready(
        val activeLightRoom: Room,
        val activeAudioRoom: Room,
        val rooms: Map<Room, RoomState>,
        val panel: Panel,
        val climate: ClimateState,
        val playlists: List<Playlist>,
        val quickPicks: List<Playlist>,
        val keepListening: List<Playlist>,
        val calendar: CalendarState,
    ) : HomeScreenState {
        /** Device state of the room whose lights are being viewed (dial, warmth, brightness). */
        val lightRoomState: RoomState get() = rooms.getValue(activeLightRoom)

        /** Device state of the room whose audio is being viewed (volume slider, Media panel). */
        val audioRoomState: RoomState get() = rooms.getValue(activeAudioRoom)

        /**
         * Audio session of the active audio room. [activeAudioRoom] is always a speaker room (the VM
         * seeds it from [Room.audioRooms] and only feeds `selectAudioRoom` speaker rooms), so the
         * `audio` is never null here — the assertion documents that invariant.
         */
        val audioState: AudioState
            get() = requireNotNull(audioRoomState.audio) {
                "activeAudioRoom ($activeAudioRoom) must be a speaker room"
            }
    }
}
