package com.mattschoe.smarthome.ui.pages.homepage

import com.mattschoe.smarthome.data.audioJoined
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarState
import com.mattschoe.smarthome.data.model.ClimateState
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.data.model.Panel
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
/** What the browse surface shows in place of its shelves while a music search is running. */
sealed interface SearchState {
    /** Blank query — the browse shelves are showing. */
    data object Idle : SearchState

    /** A request is in flight; the surface shows a spinner. */
    data object Searching : SearchState

    /** The reply. An empty [items] is a completed search with no hits, not an error. */
    data class Results(val items: List<BrowseItem>) : SearchState

    data object Failed : SearchState
}

/**
 * A browse-tile play request in flight (VM-owned). Music Assistant spends several seconds resolving
 * the stream before anything plays, so the Media panel shows the tapped item as a **loading**
 * now-playing surface — [title]/[subtitle]/[artworkUrl] are copied off the tapped `BrowseItem` —
 * until the adapter call completes (cleared, real state takes over) or fails (cleared + toast).
 * [room] scopes it: it only shows while that room is the active audio room.
 */
data class PendingPlay(
    val room: Room,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
)

/** An up-next skip in flight (VM-owned): the tapped row shows a spinner and re-taps are blocked. */
data class PendingQueueItem(val room: Room, val queueItemId: String)

/** A transient failure notice. [id] distinguishes repeats so an identical text still re-shows. */
data class ToastMessage(val id: Long, val text: String)

/**
 * The artist drill-in surface, opened by tapping an artist search result. [artist] is the tile that
 * opened it, so the header renders immediately while the detail is still loading. A `null` on
 * [HomeScreenState.Ready] means no artist surface is showing.
 */
sealed interface ArtistUiState {
    val artist: BrowseItem

    data class Loading(override val artist: BrowseItem) : ArtistUiState

    /** Either list may be empty — the surface simply omits that section, as the browse shelves do. */
    data class Ready(
        override val artist: BrowseItem,
        val topTracks: List<BrowseItem>,
        val albums: List<BrowseItem>,
    ) : ArtistUiState

    data class Failed(override val artist: BrowseItem) : ArtistUiState
}

sealed interface HomeScreenState {
    data object Loading : HomeScreenState

    data class Ready(
        val activeLightRoom: Room,
        val activeAudioRoom: Room,
        val rooms: Map<Room, RoomState>,
        val panel: Panel,
        /**
         * Whether the Media panel's now-playing surface is collapsed to the floating mini-player,
         * freeing the panel to show the browse surface while audio keeps playing (VM-owned).
         */
        val mediaMinimized: Boolean,
        /** The music search field's text (VM-owned); blank means the browse shelves are showing. */
        val searchQuery: String,
        /** Result of searching [searchQuery], debounced behind it. */
        val search: SearchState,
        /** In-flight browse-tile play for the active audio room, or `null`. See [PendingPlay]. */
        val pendingPlay: PendingPlay?,
        /** Queue-item id of an in-flight up-next skip in the active audio room, or `null`. */
        val pendingQueueItemId: String?,
        /**
         * Whether the active audio room's queue is being replaced by a play in flight. The rows on
         * hand belong to the *previous* track, so the up-next section shows a loader instead of them
         * until the refreshed queue lands (VM-owned, bounded by a grace timeout).
         */
        val queueRefreshing: Boolean,
        /** Failure notice to flash, or `null`. The UI auto-dismisses it via `dismissToast`. */
        val toast: ToastMessage?,
        /** The artist drill-in showing over the Media panel, or `null` when none is open (VM-owned). */
        val artist: ArtistUiState?,
        val climate: ClimateState,
        val playlists: List<BrowseItem>,
        val quickPicks: List<BrowseItem>,
        val mixedForYou: List<BrowseItem>,
        /** Which provider's listening the browse shelves show (VM-owned). Search ignores it. */
        val musicSource: MusicSource,
        val spotifyPlaylists: List<BrowseItem>,
        val spotifyRecentlyPlayed: List<BrowseItem>,
        val calendar: CalendarState,
        /** Real current day (system clock). The month grid highlights it as the accent cell. */
        val today: LocalDate,
        /** First-of-month of the month the calendar grid is showing (VM-owned nav selection). */
        val displayedMonth: LocalDate,
        /** The day whose agenda + todos are shown; scopes both (VM-owned selection). */
        val selectedDay: LocalDate,
    ) : HomeScreenState {
        /** The playlist shelf for the selected [musicSource]. */
        val browsePlaylists: List<BrowseItem>
            get() = if (musicSource == MusicSource.Spotify) spotifyPlaylists else playlists

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

        /**
         * The one *other* speaker room — what the join/leave action targets — when the home has
         * exactly two. `null` with a single speaker (nothing to join) or with three or more, where
         * there is no unambiguous "other room" and grouping needs a list-based surface instead.
         */
        val otherAudioRoom: Room? get() = Room.audioRooms.singleOrNull { it != activeAudioRoom }

        /** Whether [activeAudioRoom] and [otherAudioRoom] are playing as one sync group. */
        val audioJoined: Boolean
            get() = otherAudioRoom?.let { rooms.audioJoined(activeAudioRoom, it) } ?: false

        /**
         * The room the join/leave action names, or `null` when there is nothing to offer and no
         * action shows. *Joining* a room means adopting **its** music, so it is only offered while
         * that room is actually playing something; a group that already exists can always be left.
         */
        val joinTarget: Room?
            get() = otherAudioRoom?.takeIf { audioJoined || rooms[it]?.audio?.isPlaying == true }

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
