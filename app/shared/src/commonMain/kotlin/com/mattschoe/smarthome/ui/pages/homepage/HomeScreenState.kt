package com.mattschoe.smarthome.ui.pages.homepage

import com.mattschoe.smarthome.data.CalendarFilters
import com.mattschoe.smarthome.data.DaysPerWeek
import com.mattschoe.smarthome.data.audioJoined
import com.mattschoe.smarthome.data.sortTodos
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarState
import com.mattschoe.smarthome.data.model.CalendarView
import com.mattschoe.smarthome.data.model.ClimateState
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.TodoItem
// Aliased: [Ready.weekStart]/[Ready.calendarWindow] are the properties this state exposes, the
// imports are the pure math they call.
import com.mattschoe.smarthome.data.weekStart as weekStartOf
import com.mattschoe.smarthome.data.calendarWindow as calendarWindowOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plus

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

/**
 * What one month-grid cell marks under its number: the calendars with an event that day (by source
 * id, in `CalendarState.sources` order, each dot taking its calendar's color) and whether the day
 * carries a todo. A day with neither has no entry at all.
 */
data class DayMarks(val sourceIds: List<String>, val hasTodo: Boolean)

/** An up-next skip in flight (VM-owned): the tapped row shows a spinner and re-taps are blocked. */
data class PendingQueueItem(val room: Room, val queueItemId: String)

/**
 * A transient notice: a failure, or the confirmation of an action that leaves nothing visible behind
 * (queueing a tile changes only a list that isn't on screen). [id] distinguishes repeats so an
 * identical text still re-shows.
 */
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

/**
 * What the Calendar panel's editor surface is open on; `null` while the month/week views are showing.
 *
 * Only *which* event is being edited lives here — the field values are local to the editor
 * composable and come back as one finished draft on save. Routing every keystroke through the
 * ViewModel's `combine` would recompose the whole dashboard per character, and the editor is
 * deliberately a surface beside live lights and audio, not a modal that can afford that.
 */
sealed interface EventEditorTarget {
    /** The day the start wheels open on. */
    val date: LocalDate

    /** The "+" path — a blank form on [date] (today). */
    data class New(override val date: LocalDate) : EventEditorTarget

    /**
     * An existing event opened from the agenda. [canWrite] is false for an event on a read-only
     * calendar (the subscribed work roster): the fields render disabled and no save or delete is
     * offered, so its details stay reachable without pretending they can be changed.
     */
    data class Existing(val event: CalendarEvent, val canWrite: Boolean) : EventEditorTarget {
        override val date: LocalDate get() = event.start?.date ?: event.date
    }
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
        /** Whether the Calendar panel draws the month grid or [selectedDay]'s week (VM-owned). */
        val calendarView: CalendarView,
        /** The event the editor surface is open on, or `null` when the calendar views show (VM-owned). */
        val eventEditor: EventEditorTarget?,
        /** The event the week view's detail popup is open on, or `null` when none is (VM-owned). */
        val eventDetail: CalendarEvent?,
        /** Which calendars each view draws — what the header's gear edits (VM-owned, persisted). */
        val calendarFilters: CalendarFilters,
        /** Whether the gear's popup is showing (VM-owned). */
        val calendarSettingsOpen: Boolean,
        /**
         * How tall one hour row of the week grid is, in dp — what pinching the grid sets (VM-owned,
         * persisted). At the top of its range the day is 576dp and scrolls; at the bottom the whole
         * day fits and the height it gave up goes to the checklist under it.
         */
        val weekHourHeight: Float,
        /** Whether a save or delete from the editor is in flight — the button spins and re-taps drop. */
        val savingEvent: Boolean,
        /** Minutes from midnight, ticking — where the week grid draws its "now" line. */
        val nowMinutes: Int,
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

        /**
         * The events the view being shown is allowed to draw — everything minus the calendars the
         * header's gear hides *in that view*. Every calendar consumer reads this rather than
         * `calendar.events`, so hiding a calendar hides it from all of what the view shows: in month
         * view the grid's dots **and** the agenda list, in week view the blocks **and** the all-day
         * chips.
         */
        private val visibleEvents: List<CalendarEvent>
            get() = calendarFilters.hidden(calendarView).let { hidden ->
                if (hidden.isEmpty()) calendar.events else calendar.events.filterNot { it.sourceId in hidden }
            }

        /**
         * Every event the showing view may draw, grouped by day, in `sortCalendarEvents` order (the
         * adapter sorts them upstream). A day with nothing on it is absent.
         *
         * Deliberately **not** scoped to a month or a week: the month and week views are pagers, and
         * a pager composes its neighbours, so each page slices the days it draws out of this one
         * grouping rather than each needing its own derivation.
         */
        val eventsByDay: Map<LocalDate, List<CalendarEvent>> get() = visibleEvents.groupBy { it.date }

        /** Read-only events bound to [selectedDay] (the agenda list). */
        val selectedDayEvents: List<CalendarEvent> get() = eventsByDay[selectedDay].orEmpty()

        /** Todos bound to [selectedDay] (the checklist), unfinished ones first. */
        val selectedDayTodos: List<TodoItem>
            get() = sortTodos(calendar.todos.filter { it.due == selectedDay })

        /** Monday of the week [selectedDay] falls in — the week view's first column. */
        val weekStart: LocalDate get() = weekStartOf(selectedDay)

        /** The week view's seven columns, Monday first. */
        val weekDays: List<LocalDate>
            get() = weekStart.let { start -> List(DaysPerWeek) { start.plus(it, DateTimeUnit.DAY) } }

        /**
         * The span the adapter holds events for, around [today] — the range both view pagers are
         * bounded to, so every page they can reach has data behind it.
         */
        val calendarWindow: ClosedRange<LocalDate> get() = calendarWindowOf(today)

        /**
         * What each day marks in the month grid, keyed by date; a day with nothing on it is simply
         * absent. Keyed by the full date rather than by day-of-month because the month view pages —
         * each page looks its own days up here. Calendars are listed in `calendar.sources` order so a
         * day's dots keep the same left-to-right identity from one month to the next.
         */
        val dayMarks: Map<LocalDate, DayMarks>
            get() {
                val sourceOrder = calendar.sources.withIndex().associate { (i, source) -> source.id to i }
                val sourcesByDay = mutableMapOf<LocalDate, MutableSet<String>>()
                visibleEvents.forEach { sourcesByDay.getOrPut(it.date) { mutableSetOf() } += it.sourceId }
                val todoDays = calendar.todos.mapTo(mutableSetOf()) { it.due }
                return (sourcesByDay.keys + todoDays).associateWith { date ->
                    DayMarks(
                        // An id no source claims (a cached event from a since-removed calendar) sorts last.
                        sourceIds = sourcesByDay[date].orEmpty().sortedBy { sourceOrder[it] ?: Int.MAX_VALUE },
                        hasTodo = date in todoDays,
                    )
                }
            }
    }
}
