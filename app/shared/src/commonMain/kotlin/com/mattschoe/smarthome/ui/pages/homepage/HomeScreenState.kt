package com.mattschoe.smarthome.ui.pages.homepage

import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarState
import com.mattschoe.smarthome.data.model.ClimateState
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.Playlist
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.TodoItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

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
        /** Real current day (system clock). The month grid highlights it as the accent cell. */
        val today: LocalDate,
        /** First-of-month of the month the calendar grid is showing (VM-owned nav selection). */
        val displayedMonth: LocalDate,
        /** The day whose agenda + todos are shown; scopes both (VM-owned selection). */
        val selectedDay: LocalDate,
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

        /** Read-only events bound to [selectedDay] (the agenda list). */
        val selectedDayEvents: List<CalendarEvent> get() = calendar.events.filter { it.date == selectedDay }

        /** Todos bound to [selectedDay] (the checklist). */
        val selectedDayTodos: List<TodoItem> get() = calendar.todos.filter { it.due == selectedDay }

        /** Day-of-month numbers in [displayedMonth] that have any event or todo — the grid's item dots. */
        val daysWithItems: Set<Int>
            get() {
                fun inDisplayedMonth(date: LocalDate) =
                    date.year == displayedMonth.year && date.month.number == displayedMonth.month.number
                return buildSet {
                    calendar.events.forEach { if (inDisplayedMonth(it.date)) add(it.date.day) }
                    calendar.todos.forEach { if (inDisplayedMonth(it.due)) add(it.due.day) }
                }
            }
    }
}
