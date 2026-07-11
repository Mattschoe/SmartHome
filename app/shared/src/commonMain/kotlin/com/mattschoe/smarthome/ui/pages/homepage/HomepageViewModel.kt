package com.mattschoe.smarthome.ui.pages.homepage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattschoe.smarthome.data.HomeAdapter
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

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

    // Real current day, resolved once at construction (the wall tablet stays on one day per session).
    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val _displayedMonth = MutableStateFlow(LocalDate(today.year, today.month.number, 1))
    private val _selectedDay = MutableStateFlow(today)

    val screenState: StateFlow<HomeScreenState> =
        combine(
            adapter.subscribe(),
            _activeLightRoom,
            _activeAudioRoom,
            _panel,
            // Fold the two calendar selections into one flow so the outer combine stays within its
            // 5-arg typed overload (6 top-level flows would fall back to the untyped vararg form).
            combine(_displayedMonth, _selectedDay) { month, day -> month to day },
        ) { home, lightRoom, audioRoom, panel, calendar ->
            val (displayedMonth, selectedDay) = calendar
            HomeScreenState.Ready(
                activeLightRoom = lightRoom,
                activeAudioRoom = audioRoom,
                rooms = home.rooms,
                panel = panel,
                climate = home.climate,
                playlists = home.playlists,
                quickPicks = home.quickPicks,
                keepListening = home.keepListening,
                calendar = home.calendar,
                today = today,
                displayedMonth = displayedMonth,
                selectedDay = selectedDay,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeScreenState.Loading
        )

    fun selectLightRoom(room: Room) { _activeLightRoom.value = room }
    fun selectAudioRoom(room: Room) { _activeAudioRoom.value = room }
    fun selectPanel(panel: Panel) { _panel.value = panel }

    // Calendar selection (VM-owned, never on the adapter). Months stay pinned to the 1st.
    fun showPreviousMonth() { _displayedMonth.update { it.minus(1, DateTimeUnit.MONTH) } }
    fun showNextMonth() { _displayedMonth.update { it.plus(1, DateTimeUnit.MONTH) } }
    fun selectDay(date: LocalDate) { _selectedDay.value = date }

    // Todo intents forward to the adapter (optimistic, synchronous). Add mints the id there.
    fun addTodo(due: LocalDate, label: String) = adapter.addTodo(due, label)
    fun toggleTodo(id: String) = adapter.toggleTodo(id)
    fun editTodo(id: String, label: String) = adapter.editTodo(id, label)

    fun setBrightness(room: Room, value: Int) = adapter.setBrightness(room, value)
    fun setWarmth(room: Room, warmth: Warmth) = adapter.setWarmth(room, warmth)
    fun setVolume(room: Room, value: Int) = adapter.setVolume(room, value)
    fun toggleLight(room: Room) = adapter.toggleLight(room)

    // Transport intents forward to the adapter. Shuffle-toggle / repeat-cycle are computed at the
    // Homepage call site from the current audioState (same closure pattern as brightness/volume).
    fun togglePlay(room: Room) = adapter.togglePlay(room)
    fun next(room: Room) = adapter.next(room)
    fun previous(room: Room) = adapter.previous(room)
    fun seek(room: Room, positionSec: Int) = adapter.seek(room, positionSec)
    fun setShuffle(room: Room, shuffle: Boolean) = adapter.setShuffle(room, shuffle)
    fun setRepeat(room: Room, mode: RepeatMode) = adapter.setRepeat(room, mode)
}
