package com.mattschoe.smarthome.ui.pages.homepage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattschoe.smarthome.data.CalendarFilterStore
import com.mattschoe.smarthome.data.CalendarFilters
import com.mattschoe.smarthome.data.DaysPerWeek
import com.mattschoe.smarthome.data.HomeAdapter
import com.mattschoe.smarthome.data.InMemoryCalendarFilterStore
import com.mattschoe.smarthome.data.InMemoryWeekZoomStore
import com.mattschoe.smarthome.data.WeekZoomStore
import com.mattschoe.smarthome.data.clampWeekHourHeight
import com.mattschoe.smarthome.data.MediaCommands
import com.mattschoe.smarthome.data.NowPlayingBridge
import com.mattschoe.smarthome.data.nowPlayingSnapshot
import com.mattschoe.smarthome.data.audioJoined
import com.mattschoe.smarthome.data.audioSessionOf
import com.mattschoe.smarthome.data.audioSessionRoom
import com.mattschoe.smarthome.data.minutesOfDay
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.CalendarView
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.QueueMode
import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.Warmth
import com.mattschoe.smarthome.data.rotateFrom
// Aliased: [showWeek] takes a week start, the import is the math that snaps one to its Monday.
import com.mattschoe.smarthome.data.weekStart as weekStartOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Dashboard state holder. Owns the UI-selection state and combines it with the [HomeAdapter]'s
 * device data into a single [screenState] the UI collects. Light and audio rooms are selected
 * **independently** (the top chips vs. the AUDIO chips); [_panel] is the right-card tab. Device
 * intents forward to the adapter; selection intents mutate the ViewModel-owned flows.
 */
class HomepageViewModel(
    private val adapter: HomeAdapter,
    private val filterStore: CalendarFilterStore = InMemoryCalendarFilterStore(),
    /** Where the week grid's pinch level is kept between runs. See [setWeekHourHeight]. */
    private val zoomStore: WeekZoomStore = InMemoryWeekZoomStore(),
    /**
     * Where the active audio room's playback is published for the platform's own media surfaces (the
     * Android notification / lock screen). A default instance keeps the ViewModel constructible on a
     * platform — or in a test — with nothing listening.
     */
    private val nowPlaying: NowPlayingBridge = NowPlayingBridge(),
) : ViewModel() {
    private val _activeLightRoom = MutableStateFlow(Room.LivingRoom)
    private val _activeAudioRoom = MutableStateFlow(Room.audioRooms.firstOrNull() ?: Room.LivingRoom)
    private val _panel = MutableStateFlow(Panel.Media)
    private val _mediaMinimized = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _pendingPlay = MutableStateFlow<PendingPlay?>(null)
    private val _pendingQueueItem = MutableStateFlow<PendingQueueItem?>(null)
    // Room whose queue a play in flight is replacing (the up-next section loads instead of showing
    // the previous track's rows), or null.
    private val _queueRefreshRoom = MutableStateFlow<Room?>(null)
    private val _toast = MutableStateFlow<ToastMessage?>(null)
    private var toastCounter = 0L
    private val _artist = MutableStateFlow<ArtistUiState?>(null)
    private val _musicSource = MutableStateFlow(MusicSource.YtMusic)

    // The in-flight artistDetail fetch, so opening another artist (or closing the surface) drops it.
    private var artistJob: Job? = null

    // The in-flight join/leave, whose lifetime is the re-tap guard in [toggleAudioJoin].
    private var joinJob: Job? = null

    // The real current day, *ticking*: a wall-mounted tablet runs for weeks, so a day resolved once
    // at construction would leave the grid highlighting yesterday from the first midnight onward.
    // The tick is coarse (the day changes once a day) but cheap, and mirrors the left card's clock.
    private val today: StateFlow<LocalDate> = flow {
        while (true) {
            emit(Clock.System.todayIn(TimeZone.currentSystemDefault()))
            delay(TODAY_TICK_MS)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, currentDay())

    // Minutes from midnight, on the same coarse tick as [today] — the week grid's "now" line only
    // needs to be right to the minute, and a wall tablet redraws it for hours on end.
    private val nowMinutes: StateFlow<Int> = flow {
        while (true) {
            emit(currentMinuteOfDay())
            delay(TODAY_TICK_MS)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, currentMinuteOfDay())

    private val _displayedMonth = MutableStateFlow(currentDay().let { LocalDate(it.year, it.month.number, 1) })
    private val _selectedDay = MutableStateFlow(currentDay())
    // The Opgaver panel's own day, kept apart from [_selectedDay] on purpose — see Ready.todoDay.
    private val _todoDay = MutableStateFlow(currentDay())
    private val _calendarView = MutableStateFlow(CalendarView.Month)
    private val _eventEditor = MutableStateFlow<EventEditorTarget?>(null)
    private val _eventDetail = MutableStateFlow<CalendarEvent?>(null)
    private val _savingEvent = MutableStateFlow(false)
    private val _calendarFilters = MutableStateFlow(filterStore.read())
    private val _calendarSettingsOpen = MutableStateFlow(false)
    private val _weekHourHeight = MutableStateFlow(clampWeekHourHeight(zoomStore.read()))

    /**
     * The debounced search pipeline. A blank query passes straight through so clearing the field
     * restores the browse shelves instantly — and so the initial (blank) emission doesn't hold the
     * whole dashboard in `Loading` for the debounce window. [flatMapLatest] drops an in-flight search
     * as soon as a newer keystroke lands.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val searchState: Flow<SearchState> = _searchQuery
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(SearchState.Idle)
            } else {
                // Explicit type argument: without it the `flowOf(Idle)` branch pins the inference.
                flow<SearchState> {
                    emit(SearchState.Searching)
                    // Cancellation is how a superseded keystroke drops this search — it is not a
                    // failure, so it must pass through rather than surface as Failed.
                    val result = try {
                        SearchState.Results(adapter.search(query))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log("search '$query' failed: ${e.message}")
                        SearchState.Failed
                    }
                    emit(result)
                }
            }
        }

    val screenState: StateFlow<HomeScreenState> =
        combine(
            adapter.subscribe(),
            _activeLightRoom,
            _activeAudioRoom,
            // The right-card selections fold into one flow (the transient ones fold once more inside
            // it), as do the two calendar ones, so the outer combine stays within its 5-arg typed
            // overload (more top-level flows would fall back to the untyped vararg form).
            combine(
                _panel, _mediaMinimized, _searchQuery, searchState,
                combine(
                    _pendingPlay, _pendingQueueItem, _queueRefreshRoom, _toast,
                    // The browse source rides along with the artist flow: this combine's typed
                    // overload is full at 5, and so is the one outside it.
                    combine(_artist, _musicSource) { artist, source -> artist to source },
                ) { pendingPlay, pendingQueueItem, queueRefreshRoom, toast, (artist, source) ->
                    MediaSelection(pendingPlay, pendingQueueItem, queueRefreshRoom, toast, artist, source)
                },
            ) { panel, minimized, query, search, media ->
                RightCardSelection(panel, minimized, query, search, media)
            },
            // Everything the calendar's own surfaces own folds into one flow, since this combine's
            // typed overload is full at 5 — and so, once more, is the fold's (hence the filters, the
            // gear and the week's zoom riding along as a triple).
            combine(
                _displayedMonth, _selectedDay, today, nowMinutes,
                combine(
                    _calendarView, _eventEditor, _eventDetail, _savingEvent,
                    combine(
                        _calendarFilters, _calendarSettingsOpen, _weekHourHeight, _todoDay,
                    ) { filters, open, hourHeight, todoDay ->
                        CalendarChrome(filters, open, hourHeight, todoDay)
                    },
                ) { view, editor, detail, saving, chrome ->
                    CalendarSurfaceSelection(view, editor, detail, saving, chrome)
                },
            ) { month, day, now, minute, surface ->
                CalendarSelection(month, day, now, minute, surface)
            },
        ) { home, lightRoom, audioRoom, rightCard, calendar ->
            HomeScreenState.Ready(
                activeLightRoom = lightRoom,
                activeAudioRoom = audioRoom,
                rooms = home.rooms,
                panel = rightCard.panel,
                mediaMinimized = rightCard.mediaMinimized,
                searchQuery = rightCard.searchQuery,
                search = rightCard.search,
                // Pending states are room-scoped: the Media panel shows the *active audio room*, so a
                // pending play for another room must not paint this room's panel.
                pendingPlay = rightCard.media.pendingPlay?.takeIf { it.room == audioRoom },
                pendingQueueItemId = rightCard.media.pendingQueueItem?.takeIf { it.room == audioRoom }?.queueItemId,
                queueRefreshing = rightCard.media.queueRefreshRoom == audioRoom,
                toast = rightCard.media.toast,
                artist = rightCard.media.artist,
                climate = home.climate,
                playlists = home.playlists,
                quickPicks = home.quickPicks,
                mixedForYou = home.mixedForYou,
                musicSource = rightCard.media.musicSource,
                spotifyPlaylists = home.spotifyPlaylists,
                spotifyRecentlyPlayed = home.spotifyRecentlyPlayed,
                calendar = home.calendar,
                today = calendar.today,
                displayedMonth = calendar.displayedMonth,
                selectedDay = calendar.selectedDay,
                todoDay = calendar.surface.chrome.todoDay,
                calendarView = calendar.surface.calendarView,
                nowMinutes = calendar.nowMinutes,
                eventEditor = calendar.surface.eventEditor,
                eventDetail = calendar.surface.eventDetail,
                calendarFilters = calendar.surface.chrome.filters,
                calendarSettingsOpen = calendar.surface.chrome.settingsOpen,
                weekHourHeight = calendar.surface.chrome.weekHourHeight,
                savingEvent = calendar.surface.savingEvent,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeScreenState.Loading
        )

    init {
        // The pinch settles far more often than it ends, so the level reaches disk on a pause in the
        // gesture rather than per frame. The initial emission re-writes what was just read, which is
        // a no-op write of the same number.
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _weekHourHeight.debounce(ZOOM_WRITE_DEBOUNCE_MS).collect(zoomStore::write)
        }
        // Published off the adapter directly rather than off [screenState]: that flow is
        // `WhileSubscribed`, and collecting it here would pin the whole dashboard hot for as long as
        // the ViewModel lives. This is the notification's read-model — the active room's track and
        // nothing else — so it needs only the two flows it combines.
        viewModelScope.launch {
            combine(_activeAudioRoom, adapter.subscribe()) { room, home ->
                // The session's audio, not the room's own: a room following a sync group mirrors
                // that group, so the notification shows what the speaker is really playing.
                nowPlayingSnapshot(room, home.rooms.audioSessionOf(room))
            }.distinctUntilChanged().collect(nowPlaying::publish)
        }
        nowPlaying.commands = object : MediaCommands {
            override fun togglePlay() = togglePlay(_activeAudioRoom.value)
            override fun next() = next(_activeAudioRoom.value)
            override fun previous() = previous(_activeAudioRoom.value)
            override fun seek(positionSec: Int) = seek(_activeAudioRoom.value, positionSec)
            override fun setVolume(pct: Int) = setVolume(_activeAudioRoom.value, pct)
        }
    }

    override fun onCleared() {
        // The session outlives the ViewModel (it is bound to the service, not to the screen), so hand
        // back the transport rather than leaving it pointing at a dead scope.
        nowPlaying.commands = null
        super.onCleared()
    }

    fun selectLightRoom(room: Room) { _activeLightRoom.value = room }
    fun selectAudioRoom(room: Room) { _activeAudioRoom.value = room }

    /**
     * Switch the right card's tab. Opening the Calendar asks the adapter for a fresh window — events
     * are fetched rather than pushed, and a subscribed calendar (a work roster) is polled only once a
     * day unless something forces it. Which tab is showing stays here, never on the adapter.
     *
     * Opening Opgaver puts it back on today, rather than wherever it was last swiped to: on a wall
     * tablet the checklist is opened to ask what is outstanding *now*, and this is also what carries
     * it over midnight.
     *
     * The refresh only fires when the panel actually *changes*: the phone pager reads `targetPage`,
     * which flips mid-drag, so swiping toward the Calendar page — and back and forth — re-selects
     * the same panel many times per gesture. Re-selection is a no-op; a real Media→Calendar (or
     * back) switch still fetches, exactly as before.
     */
    fun selectPanel(panel: Panel) {
        if (_panel.value == panel) return
        _panel.value = panel
        if (panel == Panel.Calendar) adapter.refreshCalendar()
        if (panel == Panel.Opgaver) _todoDay.value = currentDay()
    }

    /** Switch which provider's listening the browse shelves show. Search is unaffected by design. */
    fun selectMusicSource(source: MusicSource) { _musicSource.value = source }

    /**
     * Collapse the now-playing surface into the floating mini-player, or restore it. Purely a view
     * of the same audio — playback is untouched either way. Expanding also closes the artist surface,
     * so the panel is never asked to show two surfaces at once.
     */
    fun setMediaMinimized(minimized: Boolean) {
        _mediaMinimized.value = minimized
        if (!minimized) closeArtist()
    }

    /**
     * Open the artist drill-in for an artist tile. The header shows straight away off [item] while
     * the catalogue loads behind it; a previous fetch is dropped, since only the newest one is being
     * looked at.
     */
    fun openArtist(item: BrowseItem) {
        val uri = item.uri ?: return
        artistJob?.cancel()
        _artist.value = ArtistUiState.Loading(item)
        artistJob = viewModelScope.launch {
            // Cancellation is how a superseded/closed drill-in drops this fetch — not a failure, so
            // it must pass through rather than surface as Failed (same rule as the search pipeline).
            val next = try {
                val detail = adapter.artistDetail(uri)
                ArtistUiState.Ready(item, detail.topTracks, detail.albums)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("artist '${item.name}' failed: ${e.message}")
                ArtistUiState.Failed(item)
            }
            _artist.value = next
        }
    }

    /** Back out of the artist surface, dropping any fetch still in flight. */
    fun closeArtist() {
        artistJob?.cancel()
        artistJob = null
        _artist.value = null
    }

    /**
     * Play the artist's top hits from the tapped one: it plays first and the rest follow in order,
     * with the hits above it appended to the tail ([rotateFrom]). Starting playback closes the artist
     * surface, so what was just picked is what the panel shows.
     */
    fun playTopHits(startIndex: Int) {
        val ready = _artist.value as? ArtistUiState.Ready ?: return
        playBlock(rotateFrom(ready.topTracks, startIndex))
    }

    /** The artist header's shuffle pill: the same top hits, in random order. */
    fun shuffleArtist() {
        val ready = _artist.value as? ArtistUiState.Ready ?: return
        playBlock(ready.topTracks.shuffled())
    }

    /**
     * Play [items] as an ordered block on the active audio room. The head stands in as the pending
     * surface (it is what starts), so this reads exactly like tapping a single tile.
     */
    private fun playBlock(items: List<BrowseItem>) {
        val playable = items.filter { it.uri != null }
        val head = playable.firstOrNull() ?: return
        val uris = playable.mapNotNull { it.uri }
        startPlay(head) { room -> adapter.playAll(room, uris) }
    }

    /** The search field's text. Searching itself is debounced behind this in [searchState]. */
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    // Calendar selection (VM-owned, never on the adapter). Months stay pinned to the 1st. Navigating
    // is also the moment to nudge the adapter, for the same reason opening the panel is.
    /**
     * Show the month [month] falls in — the **one** month-navigation path. The grid is a pager, and a
     * pager settles on an absolute page rather than on a delta, so this takes the month itself; the
     * steppers and the screen-reader actions below are thin deltas over it.
     */
    fun showMonth(month: LocalDate) {
        _displayedMonth.value = LocalDate(month.year, month.month.number, 1)
        adapter.refreshCalendar()
    }

    fun showPreviousMonth() = showMonth(_displayedMonth.value.minus(1, DateTimeUnit.MONTH))

    fun showNextMonth() = showMonth(_displayedMonth.value.plus(1, DateTimeUnit.MONTH))

    fun selectDay(date: LocalDate) { _selectedDay.value = date }

    /** Switch the Calendar panel between the month grid and the week time grid. */
    fun setCalendarView(view: CalendarView) { _calendarView.value = view }

    // Which calendars each view draws — the header gear's popup. The setting is per view, so the
    // toggle is applied to whichever view is being looked at, and it is written through on every
    // change: a wall tablet is restarted by a power cut, not by anyone closing a settings screen.
    fun openCalendarSettings() { _calendarSettingsOpen.value = true }

    fun closeCalendarSettings() { _calendarSettingsOpen.value = false }

    fun toggleCalendarFilter(sourceId: String) {
        filterStore.write(_calendarFilters.updateAndGet { it.toggle(_calendarView.value, sourceId) })
    }

    /**
     * How tall one hour row of the week grid is, in dp — what pinching the grid sets, clamped to what
     * it can draw. Unlike the filters this is *not* written through on every change: a pinch emits a
     * value per frame and disk has no use for the ones in between, so the write is debounced (see
     * [init]).
     */
    fun setWeekHourHeight(hourHeightDp: Float) {
        _weekHourHeight.value = clampWeekHourHeight(hourHeightDp)
    }

    /**
     * Open a week-view event's detail popup. The week's blocks are as small as a few minutes are
     * tall, so tapping one has to answer "what *is* this" before it offers to change it — the month
     * agenda's rows, which are already legible, keep opening the editor directly.
     */
    fun openEventDetail(event: CalendarEvent) { _eventDetail.value = event }

    fun closeEventDetail() { _eventDetail.value = null }

    /** The popup's pencil: hand the same event to the editor, which decides whether it may be changed. */
    fun editEventDetail() {
        _eventDetail.value?.let {
            openEvent(it)
            _eventDetail.value = null
        }
    }

    /** The popup's trash. Same guards and failure handling as the editor's [deleteEvent]. */
    fun deleteEventDetail() {
        val event = _eventDetail.value ?: return
        deleteEvent(event) { _eventDetail.value = null }
    }

    /**
     * Show the week starting [weekStart] — the **one** week-navigation path, [showMonth]'s sibling
     * and, like it, absolute because the week grid is a pager.
     *
     * Week navigation is [_selectedDay] navigation: the week shown is the one the selected day falls
     * in, so there is no separate week-start state to keep in step. The weekday within the week is
     * kept, so paging weeks doesn't quietly re-scope the checklist to another day. The displayed
     * month follows along, so toggling back to the month grid lands on the month just been looked at
     * rather than the one left behind.
     */
    fun showWeek(weekStart: LocalDate) {
        val day = weekStartOf(weekStart).plus(_selectedDay.value.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        _selectedDay.value = day
        _displayedMonth.value = LocalDate(day.year, day.month.number, 1)
        adapter.refreshCalendar()
    }

    fun showPreviousWeek() = shiftWeek(-DaysPerWeek)

    fun showNextWeek() = shiftWeek(DaysPerWeek)

    private fun shiftWeek(days: Int) =
        showWeek(weekStartOf(_selectedDay.value).plus(days, DateTimeUnit.DAY))

    /**
     * Back to today, in whichever view is showing — the header's date button. Both grids are pagers
     * driven off this state, so moving the selected day and the displayed month is the whole of it:
     * each pager animates itself there, and the week is the one the selected day falls in.
     */
    fun showToday() {
        val day = today.value
        _selectedDay.value = day
        showMonth(day)
    }

    /**
     * Open a blank event form from the "+". Always on **today**, not on the selected day: the button is
     * reached from wherever the grid happens to be parked, and an event landing on a month somebody
     * merely browsed past is a worse surprise than re-picking the date in the editor.
     */
    fun openNewEvent() { _eventEditor.value = EventEditorTarget.New(today.value) }

    /**
     * Open a blank event form on the empty week-grid slot that was tapped. [openNewEvent]'s reason to
     * ignore where the grid is parked doesn't apply here: the day and time *are* what was pointed at.
     */
    fun openNewEventAt(date: LocalDate, time: LocalTime) {
        _eventEditor.value = EventEditorTarget.New(date, time)
    }

    /**
     * Open an agenda row. An event on a read-only calendar — or one the backend gave no uid, which no
     * write intent can address — opens as details that cannot be saved rather than not opening at all.
     */
    fun openEvent(event: CalendarEvent) {
        val source = adapter.subscribe().value.calendar.sources.firstOrNull { it.id == event.sourceId }
        val canWrite = source?.canWrite == true && event.uid != null
        _eventEditor.value = EventEditorTarget.Existing(event, canWrite)
    }

    /** Leave the editor, dropping whatever was typed. The surface's back arrow. */
    fun closeEventEditor() { _eventEditor.value = null }

    /**
     * Write [draft] — creating it on [sourceId] from the "+" path, replacing the open event on the
     * edit path (where the calendar is the event's own; moving between calendars is a delete plus a
     * create, which this surface does not offer). Same shape as [startPlay]: the button spins until
     * the adapter replies, and a **failure leaves the surface open** so nothing typed is lost.
     */
    fun saveEvent(sourceId: String, draft: CalendarEventDraft, reminder: ReminderRule? = null) {
        val target = _eventEditor.value ?: return
        if (_savingEvent.value) return
        val existing = (target as? EventEditorTarget.Existing)?.event
        _savingEvent.value = true
        viewModelScope.launch {
            try {
                val uid = existing?.uid
                if (existing != null && uid != null) {
                    // No recurrence UI, so an occurrence of a series is edited as just that occurrence.
                    adapter.updateEvent(existing.sourceId, uid, draft, existing.recurrenceId)
                } else {
                    adapter.createEvent(sourceId, draft)
                    // A reminder is keyed on the event's uid, and a create doesn't return one — Home
                    // Assistant's `calendar/event/create` replies with nothing. The create already
                    // triggers a refetch, so the uid is recovered by finding the event that just
                    // appeared. The event itself is saved either way: a reminder that could not be
                    // attached is worth a notice, not a rollback.
                    if (reminder != null) attachReminder(sourceId, draft, reminder)
                }
                _eventEditor.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("saving '${draft.summary}' failed: ${e.message}")
                showToast(SAVE_FAILED_TOAST)
            } finally {
                _savingEvent.value = false
            }
        }
    }

    /**
     * Wait for the just-created event to come back from the refetch and put [reminder] on its uid.
     * Matched on the calendar, the title and the start — the three fields the draft fixes — since
     * that is all there is to match on before the backend has named it.
     */
    private suspend fun attachReminder(sourceId: String, draft: CalendarEventDraft, reminder: ReminderRule) {
        val created = withTimeoutOrNull(CREATED_UID_TIMEOUT_MS) {
            adapter.subscribe()
                .map { home -> home.calendar.events.firstOrNull { it.matches(sourceId, draft) } }
                .first { it != null }
        }
        val uid = created?.uid
        if (uid == null) {
            log("created '${draft.summary}' but never saw it come back; reminder not set")
            showToast(REMINDER_FAILED_TOAST)
            return
        }
        adapter.setEventReminder(sourceId, uid, created.recurrenceId, reminder)
    }

    private fun CalendarEvent.matches(sourceId: String, draft: CalendarEventDraft): Boolean =
        uid != null && this.sourceId == sourceId && title == draft.summary && start == draft.start

    /**
     * Set (or clear) the reminder on the event the editor is open on. Applied straight away rather
     * than on a save: a reminder lives beside the event, not in it, which is what lets one be put on
     * an event of a read-only calendar that has no save at all. Which event that is is resolved here
     * rather than at the call site, like [deleteEvent].
     *
     * A brand-new event has no uid to key a rule on and is simply left alone — its reminder rides
     * out with [saveEvent] instead, once the create has minted one.
     */
    fun setEventReminder(rule: ReminderRule?) {
        val event = (_eventEditor.value as? EventEditorTarget.Existing)?.event ?: return
        val uid = event.uid ?: return
        viewModelScope.launch {
            try {
                adapter.setEventReminder(event.sourceId, uid, event.recurrenceId, rule)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("setting reminder on '${event.title}' failed: ${e.message}")
                showToast(REMINDER_FAILED_TOAST)
            }
        }
    }

    /** Set (or clear, with a null [offsetMin]) the standing reminder every event on a calendar gets. */
    fun setCalendarReminderDefault(sourceId: String, offsetMin: Int?) {
        viewModelScope.launch {
            try {
                adapter.setCalendarReminderDefault(sourceId, offsetMin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("setting reminder default on '$sourceId' failed: ${e.message}")
                showToast(REMINDER_FAILED_TOAST)
            }
        }
    }

    /** Delete the event the editor is open on. Scoped like [saveEvent]: one occurrence of a series. */
    fun deleteEvent() {
        val existing = (_eventEditor.value as? EventEditorTarget.Existing)?.event ?: return
        deleteEvent(existing) { _eventEditor.value = null }
    }

    /**
     * The one delete, shared by the editor and the detail popup: the [_savingEvent] re-tap guard, the
     * write, and closing only the surface the delete came from ([onDone]) once it lands. An event the
     * backend gave no uid can't be addressed at all, so it is simply left alone.
     */
    private fun deleteEvent(event: CalendarEvent, onDone: () -> Unit) {
        val uid = event.uid ?: return
        if (_savingEvent.value) return
        _savingEvent.value = true
        viewModelScope.launch {
            try {
                adapter.deleteEvent(event.sourceId, uid, event.recurrenceId)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("deleting '${event.title}' failed: ${e.message}")
                showToast(DELETE_FAILED_TOAST)
            } finally {
                _savingEvent.value = false
            }
        }
    }

    /**
     * Page the checklist to [date] — pure UI selection, and deliberately not [selectDay]: the two
     * panels swipe independently.
     */
    fun showTodoDay(date: LocalDate) { _todoDay.value = date }

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
    /**
     * Start playing a browse tile on the active audio room. Radio stays off: the queue's always-on
     * "Don't Stop the Music" already auto-fills continuations, and per-tap `radio_mode` stalls the
     * play command past its timeout (see [com.mattschoe.smarthome.data.MusicAssistantAdapter.play]).
     *
     * Music Assistant takes seconds to resolve the stream, so the tapped item goes up as a
     * [PendingPlay] immediately — the Media panel shows it as a loading now-playing surface — and
     * comes down when the adapter reports the music starting (waiting out the short gap until the
     * device state echoes the new track) or fails (→ toast). Tapping another tile mid-flight simply
     * replaces the pending item; the compare-and-set keeps a stale completion from clearing it.
     *
     * Picking something new is a request to see it, so this also restores a minimized player, ends
     * any search, and closes the artist surface — the tapped result is what all three were for.
     */
    fun play(item: BrowseItem) {
        val uri = item.uri ?: return
        startPlay(item) { room -> adapter.play(room, uri, radio = false) }
    }

    /**
     * Queue a browse tile behind what the active audio room is already playing — the long-press
     * actions on a tile. [QueueMode.Next] lands it at the top of the block of user-queued entries,
     * [QueueMode.Last] at its bottom; neither interrupts the music.
     *
     * Deliberately **not** [startPlay]: nothing is starting, so the search query, the artist surface,
     * a minimized player and any pending play all stay exactly as they are — the browse surface the
     * long-press came from is where the user still is. With nothing playing there is nothing to queue
     * behind, and a long-press that produced neither audio nor any visible change would read as a
     * dropped gesture, so that case falls back to [play].
     *
     * Both outcomes are announced: an enqueue changes nothing on screen (the up-next list isn't even
     * showing when the browse surface is), so the confirmation is the only feedback there is.
     */
    fun enqueue(item: BrowseItem, mode: QueueMode) {
        val uri = item.uri ?: return
        val room = sessionRoom(_activeAudioRoom.value)
        if (nowPlayingOf(room) == null) return play(item)
        viewModelScope.launch {
            try {
                adapter.enqueue(room, uri, mode)
                showToast(if (mode == QueueMode.Next) PLAY_NEXT_TOAST else ENQUEUED_TOAST)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("enqueue '${item.name}' ($mode) on $room failed: ${e.message}")
                showToast(ENQUEUE_FAILED_TOAST)
            }
        }
    }

    /**
     * The shared body of every "start something new" intent: hand the panel back to now-playing (a
     * pick is a request to see it, so this restores a minimized player, ends any search and closes
     * the artist surface), put [item] up as the pending surface, then run [start] and hold that
     * surface until the device state catches up.
     */
    private fun startPlay(item: BrowseItem, start: suspend (Room) -> Unit) {
        val room = _activeAudioRoom.value
        // Starting something new goes to the *session* — with a sync group that is the group's
        // playback, so the new music replaces what every speaker in it is playing. The pending
        // surface stays keyed on the room being looked at, which is where it has to show.
        val target = sessionRoom(room)
        _mediaMinimized.value = false
        _searchQuery.value = ""
        closeArtist()
        val pending = PendingPlay(room, item.name, item.subtitle, item.artworkUrl)
        _pendingPlay.value = pending
        val trackBefore = nowPlayingOf(target)
        val queueBefore = queueOf(target)
        // The rows on hand belong to the track being replaced — the up-next section loads instead of
        // showing them, and keeps loading until a usable new queue arrives (see [awaitQueueChange]).
        _queueRefreshRoom.value = room
        viewModelScope.launch {
            try {
                start(target)
                awaitTrackChange(target, trackBefore)
                // The track is real now — release the full loading surface, but keep the up-next
                // loader until the queue refresh (which trails the track by ~a second) lands too.
                _pendingPlay.compareAndSet(pending, null)
                awaitQueueChange(target, queueBefore)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("play '${item.name}' on $target failed: ${e.message}")
                showToast(PLAY_FAILED_TOAST)
            } finally {
                _pendingPlay.compareAndSet(pending, null)
                _queueRefreshRoom.compareAndSet(room, null)
            }
        }
    }

    /**
     * Skip the active audio room's playback to the tapped "up next" row. Same pending pattern as
     * [play], but per-row: the tapped row spins ([PendingQueueItem]) and further queue taps are
     * dropped until it resolves — a second tap of a row that hasn't visibly reacted is a retry of
     * the same intent, not a new one.
     */
    fun playQueueItem(queueItemId: String) {
        if (_pendingQueueItem.value != null) return
        val room = _activeAudioRoom.value
        val target = sessionRoom(room)
        val pending = PendingQueueItem(room, queueItemId)
        _pendingQueueItem.value = pending
        val trackBefore = nowPlayingOf(target)
        viewModelScope.launch {
            try {
                adapter.playQueueItem(target, queueItemId)
                awaitTrackChange(target, trackBefore)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("skip to $queueItemId on $target failed: ${e.message}")
                showToast(PLAY_FAILED_TOAST)
            } finally {
                _pendingQueueItem.compareAndSet(pending, null)
            }
        }
    }

    private fun nowPlayingOf(room: Room) = adapter.subscribe().value.rooms[room]?.audio?.nowPlaying

    private fun queueOf(room: Room) = adapter.subscribe().value.rooms[room]?.audio?.queue.orEmpty()

    /**
     * Bridge the small gap between the adapter's "music is starting" reply and the device state
     * actually carrying the new track, so the pending surface never flashes back to the old one.
     * Bounded: if the track never changes (e.g. re-playing what was already on), just move on.
     */
    private suspend fun awaitTrackChange(room: Room, before: MediaTrack?) {
        withTimeoutOrNull(TRACK_CHANGE_GRACE_MS) {
            adapter.subscribe().first { it.rooms[room]?.audio?.nowPlaying != before }
        }
    }

    /**
     * Same bridging for the queue, which trails the track by a lot more: a replace-play first empties
     * the queue (the immediate refresh sees only the playing entry), and Music Assistant's
     * Don't-Stop-the-Music takes several more seconds to append the actual continuation. The up-next
     * loader must hold through *both* — so this waits for a **usable** queue (non-empty, and not the
     * previous track's rows), not merely the first change. Bounded: if no usable queue ever arrives,
     * give up and show what there is.
     */
    private suspend fun awaitQueueChange(room: Room, before: List<MediaTrack>) {
        withTimeoutOrNull(QUEUE_CHANGE_GRACE_MS) {
            adapter.subscribe().first {
                val queue = it.rooms[room]?.audio?.queue.orEmpty()
                queue.isNotEmpty() && queue != before
            }
        }
    }

    private fun showToast(text: String) { _toast.value = ToastMessage(++toastCounter, text) }

    /**
     * Every failure toast is deliberately vague on screen — the wall tablet is not a console — so the
     * reason behind it is logged instead, next to the adapter's own line for the same call.
     */
    private fun log(message: String) = println("HomepageViewModel: $message")

    /** Clears the current toast; the UI calls this after its display window. */
    fun dismissToast() { _toast.value = null }

    /** Move a queue row [posShift] positions (negative = earlier) in the active audio room's queue. */
    fun moveQueueItem(queueItemId: String, posShift: Int) {
        adapter.moveQueueItem(sessionRoom(_activeAudioRoom.value), queueItemId, posShift)
    }

    /**
     * Where [room]'s music intents are addressed: its own session, or the sync group's when it follows
     * one (see [com.mattschoe.smarthome.data.audioSessionRoom]). A group has no leader as far as the
     * user is concerned — every speaker in it plays whatever was asked for last, from whichever room
     * it was asked in — so *content* intents (play, enqueue, skip, transport) all resolve through
     * here. [setVolume] deliberately does not: volume belongs to the speaker, not to the session.
     *
     * This is the single place the redirect happens; the adapters stay plain room-addressed device
     * intents and know nothing about it.
     */
    private fun sessionRoom(room: Room): Room = adapter.subscribe().value.rooms.audioSessionRoom(room)

    /**
     * Put the home's two speaker rooms on the same music, or take them apart again — the center
     * card's join/leave action, always phrased about the *other* room since the one being looked at
     * is the one staying put.
     *
     * Neither room leads. Joining plays whatever music the pair already has going in both of them —
     * the room being viewed if it is playing, the other room otherwise — and leaving drops the other
     * room, the viewed one playing on.
     *
     * A tap is dropped while a previous one is still in flight: the label only flips when device
     * truth lands (grouping has no optimistic apply — see
     * [com.mattschoe.smarthome.data.HomeAssistantAdapter.joinAudio]), so a second tap on a label
     * that hasn't reacted yet would immediately contradict the first. [joinJob] holds that guard —
     * it fires the intent, then waits (bounded) for the group state to actually flip.
     */
    fun toggleAudioJoin() {
        if (joinJob?.isActive == true) return
        val room = _activeAudioRoom.value
        val other = Room.audioRooms.singleOrNull { it != room } ?: return
        val rooms = adapter.subscribe().value.rooms
        val joinedBefore = rooms.audioJoined(room, other)
        if (joinedBefore) {
            adapter.unjoinAudio(other)
        } else {
            // Whichever side has music is the one the pair adopts. Neither playing is nothing to
            // join — the action isn't offered then, so this only guards a tap that raced the music
            // stopping.
            when {
                rooms.audioSessionOf(room)?.isPlaying == true ->
                    adapter.joinAudio(leader = room, follower = other)
                rooms[other]?.audio?.isPlaying == true ->
                    adapter.joinAudio(leader = other, follower = room)
                else -> return
            }
        }
        joinJob = viewModelScope.launch {
            withTimeoutOrNull(GROUP_CHANGE_GRACE_MS) {
                adapter.subscribe().first { it.rooms.audioJoined(room, other) != joinedBefore }
            }
        }
    }

    // Transport acts on what is playing, so it goes to the session ([sessionRoom]) — pausing from
    // either room of a group pauses the group. Volume is the exception, further up: it is the
    // speaker's own.
    fun togglePlay(room: Room) = adapter.togglePlay(sessionRoom(room))
    fun next(room: Room) = adapter.next(sessionRoom(room))
    fun previous(room: Room) = adapter.previous(sessionRoom(room))
    fun seek(room: Room, positionSec: Int) = adapter.seek(sessionRoom(room), positionSec)
    fun setShuffle(room: Room, shuffle: Boolean) = adapter.setShuffle(sessionRoom(room), shuffle)
    fun setRepeat(room: Room, mode: RepeatMode) = adapter.setRepeat(sessionRoom(room), mode)

    private companion object {
        /** Long enough that a typed word issues one search, short enough to feel like live results. */
        const val SEARCH_DEBOUNCE_MS = 350L

        /** How long the week grid's zoom must hold still before it is written to disk. */
        const val ZOOM_WRITE_DEBOUNCE_MS = 500L

        /**
         * How long after a successful play/skip reply to keep the pending state up while the device
         * state catches up. HA echoed the new track within ~0.3s in measurements; this only bounds
         * the wait when the track never changes at all.
         */
        const val TRACK_CHANGE_GRACE_MS = 5_000L

        /**
         * How long after a play to keep the up-next loader waiting for a usable refreshed queue.
         * Generous: the Don't-Stop-the-Music continuation that fills the queue after a replace-play
         * routinely takes ~5s beyond the track itself starting.
         */
        const val QUEUE_CHANGE_GRACE_MS = 15_000L

        /**
         * How long a join/leave blocks further taps while waiting for HA to echo the players'
         * new `group_members`. A normal `state_changed` round-trip, with room to spare.
         */
        const val GROUP_CHANGE_GRACE_MS = 5_000L

        const val PLAY_FAILED_TOAST = "Kunne ikke afspille"

        // Queueing is invisible where it is triggered from, so its *success* is announced too.
        const val PLAY_NEXT_TOAST = "Afspilles som næste"
        const val ENQUEUED_TOAST = "Tilføjet til køen"
        const val ENQUEUE_FAILED_TOAST = "Kunne ikke tilføje til kø"

        const val SAVE_FAILED_TOAST = "Kunne ikke gemme"
        const val DELETE_FAILED_TOAST = "Kunne ikke slette"
        const val REMINDER_FAILED_TOAST = "Kunne ikke gemme påmindelsen"

        /**
         * How long to wait for a just-created event to come back from the refetch, so its reminder
         * can be keyed on the uid the backend minted. Generous: it spans a write, a coordinator
         * re-poll and a REST window fetch.
         */
        const val CREATED_UID_TIMEOUT_MS = 15_000L

        /**
         * How often the real current day is re-read. Only one tick a day changes anything, so this
         * is about *how soon after* midnight the grid catches up, not about precision.
         */
        const val TODAY_TICK_MS = 60_000L
    }
}

private fun currentDay(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

private fun currentMinuteOfDay(): Int =
    minutesOfDay(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time)

/** The calendar's selections plus the ticking current day and minute, as one combine input. */
private data class CalendarSelection(
    val displayedMonth: LocalDate,
    val selectedDay: LocalDate,
    val today: LocalDate,
    val nowMinutes: Int,
    val surface: CalendarSurfaceSelection,
)

/**
 * Which of the Calendar panel's surfaces are showing and what they are showing — the view, the
 * editor, the week view's detail popup and the gear's filter popup (all VM-owned).
 */
private data class CalendarSurfaceSelection(
    val calendarView: CalendarView,
    val eventEditor: EventEditorTarget?,
    val eventDetail: CalendarEvent?,
    val savingEvent: Boolean,
    val chrome: CalendarChrome,
)

/**
 * What [CalendarSurfaceSelection]'s own combine has no room left for: the gear's filters and popup,
 * the week grid's zoom, and the Opgaver panel's day. Not a family so much as the overflow — both
 * folds above it are full at their typed overload's five arguments.
 */
private data class CalendarChrome(
    val filters: CalendarFilters,
    val settingsOpen: Boolean,
    val weekHourHeight: Float,
    val todoDay: LocalDate,
)

/**
 * The Media panel's transient state — the in-flight play states, the toast, and the artist drill-in
 * — folded into one combine input (all VM-owned).
 */
private data class MediaSelection(
    val pendingPlay: PendingPlay?,
    val pendingQueueItem: PendingQueueItem?,
    val queueRefreshRoom: Room?,
    val toast: ToastMessage?,
    val artist: ArtistUiState?,
    val musicSource: MusicSource,
)

/** The right card's UI selections (incl. everything in [MediaSelection]), as one combine input. */
private data class RightCardSelection(
    val panel: Panel,
    val mediaMinimized: Boolean,
    val searchQuery: String,
    val search: SearchState,
    val media: MediaSelection,
)
