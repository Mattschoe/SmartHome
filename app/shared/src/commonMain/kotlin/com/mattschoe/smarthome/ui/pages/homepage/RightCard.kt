package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.CalendarFilters
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.data.model.ReminderRules
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.CalendarView
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.QueueMode
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.data.formatDayAndMonth
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.controls.calendar.AddEventButton
import com.mattschoe.smarthome.ui.controls.calendar.CalendarPanel
import com.mattschoe.smarthome.ui.controls.calendar.CalendarSettingsButton
import com.mattschoe.smarthome.ui.controls.calendar.CalendarSettingsPopup
import com.mattschoe.smarthome.ui.controls.calendar.CalendarViewToggle
import com.mattschoe.smarthome.ui.controls.calendar.EventDetailPopup
import com.mattschoe.smarthome.ui.controls.calendar.TodayButton
import com.mattschoe.smarthome.ui.controls.calendar.TodoPanel
import com.mattschoe.smarthome.ui.controls.media.MediaPanel
import com.mattschoe.smarthome.ui.controls.media.MiniPlayerBar
import com.mattschoe.smarthome.ui.controls.media.MinimizeHandle
import com.mattschoe.smarthome.ui.controls.media.rememberLatchedTrack
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InsetFill
import com.mattschoe.smarthome.ui.theme.OnForest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.calender_filled
import smarthome.shared.generated.resources.checklist
import smarthome.shared.generated.resources.media_outline

/**
 * The flex-1.12 right card: a fixed Musik/Kalender/Opgaver [PanelTabs] segmented control over a
 * scrolling content region. The Media panel swaps between the now-playing surface and the browse surface
 * (Quick Picks + Mixed for you) by playback state *and* [mediaMinimized] — collapsing the player
 * hands the panel back to browsing while a [MiniPlayerBar] floats over it, keeping transport
 * reachable. Width-agnostic; the `Expanded` assembly point in [Homepage.kt] assigns its width.
 */
@Composable
fun RightCard(
    panel: Panel,
    mediaMinimized: Boolean,
    searchQuery: String,
    search: SearchState,
    pendingPlay: PendingPlay?,
    pendingQueueItemId: String?,
    queueRefreshing: Boolean,
    artist: ArtistUiState?,
    audioState: AudioState,
    musicSource: MusicSource,
    playlists: List<BrowseItem>,
    quickPicks: List<BrowseItem>,
    mixedForYou: List<BrowseItem>,
    spotifyPlaylists: List<BrowseItem>,
    spotifyRecentlyPlayed: List<BrowseItem>,
    today: LocalDate,
    displayedMonth: LocalDate,
    selectedDay: LocalDate,
    /** The day the Opgaver panel is paged to — its own selection, independent of [selectedDay]. */
    todoDay: LocalDate,
    /** Whether the Calendar panel shows the month grid or [selectedDay]'s week. */
    calendarView: CalendarView,
    /** Every visible event grouped by day; each calendar view's pages slice it themselves. */
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    /** The whole checklist; the Opgaver panel's pages slice it themselves, as the calendar's do. */
    todos: List<TodoItem>,
    /** The week view's seven columns (Monday first) for the week being shown. */
    weekDays: List<LocalDate>,
    /** The span the adapter holds events for — the range the calendar's pagers are bounded to. */
    calendarWindow: ClosedRange<LocalDate>,
    /** Minutes from midnight — where the week grid draws its "now" line. */
    nowMinutes: Int,
    /** The home's calendars, in the order that assigns each its agenda dot color. */
    calendarSources: List<CalendarSource>,
    /** Whether the calendar is being rendered from the offline cache rather than from a live fetch. */
    calendarStale: Boolean,
    /** Whether Home Assistant exposes a todo list the checklist can write to. */
    calendarHasTodoList: Boolean,
    /** The home's reminder rules — read by the editor's row, the detail popup and the gear popup. */
    calendarReminders: ReminderRules,
    /** Per-day marks for the month grid's cells, keyed by date. */
    dayMarks: Map<LocalDate, DayMarks>,
    /** The week grid's hour-row height in dp — the reader's pinch level. */
    weekHourHeight: Float,
    /** The event editor showing over the calendar views, or `null` when they are. */
    eventEditor: EventEditorTarget?,
    /** The event the week view's detail popup is open on, or `null` when none is. */
    eventDetail: CalendarEvent?,
    /** Which calendars each view draws — what the header's gear popup edits. */
    calendarFilters: CalendarFilters,
    /** Whether the gear's popup is showing. */
    calendarSettingsOpen: Boolean,
    /** Whether a save/delete from the editor is in flight. */
    savingEvent: Boolean,
    onSelectPanel: (Panel) -> Unit,
    onSelectMusicSource: (MusicSource) -> Unit,
    onSetMediaMinimized: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onPlay: (BrowseItem) -> Unit,
    onEnqueue: (BrowseItem, QueueMode) -> Unit,
    onOpenArtist: (BrowseItem) -> Unit,
    onCloseArtist: () -> Unit,
    onPlayTopHit: (Int) -> Unit,
    onShuffleArtist: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayQueueItem: (String) -> Unit,
    onMoveQueueItem: (String, Int) -> Unit,
    onSelectCalendarView: (CalendarView) -> Unit,
    onShowMonth: (LocalDate) -> Unit,
    onShowWeek: (LocalDate) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onShowTodoDay: (LocalDate) -> Unit,
    onAddTodo: (LocalDate, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onEditTodo: (String, String) -> Unit,
    onAddEvent: () -> Unit,
    onShowToday: () -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onOpenEventDetail: (CalendarEvent) -> Unit,
    onNewEventAt: (LocalDate, LocalTime) -> Unit,
    onWeekHourHeight: (Float) -> Unit,
    onEditEventDetail: () -> Unit,
    onDeleteEventDetail: () -> Unit,
    onCloseEventDetail: () -> Unit,
    onOpenCalendarSettings: () -> Unit,
    onCloseCalendarSettings: () -> Unit,
    onToggleCalendarFilter: (String) -> Unit,
    /** Set the reminder on the event the editor is open on (existing events only). */
    onSetEventReminder: (ReminderRule?) -> Unit,
    /** Set or clear a calendar's standing reminder — the gear popup's per-calendar row. */
    onSetCalendarReminderDefault: (String, Int?) -> Unit,
    onSaveEvent: (String, CalendarEventDraft, ReminderRule?) -> Unit,
    onDeleteEvent: () -> Unit,
    onCloseEventEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardContainer(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(24.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            // The tabs are wrap-content, so the trailing edge beside them is free for whatever the
            // showing panel needs there: the source badge over Media, the today-and-add pair over the
            // Calendar, and over Opgaver the day it is paged to — which is a title rather than a
            // control, but this is the card's free corner and the checklist has no header of its own.
            // All three animate, to keep the row from jumping as the tab changes. The pair stands
            // down while the editor is open — re-opening a blank form would discard what is typed, and
            // paging the grid underneath the editor moves nothing anybody can see.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PanelTabs(panel = panel, onSelectPanel = onSelectPanel)
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(visible = panel == Panel.Media, enter = fadeIn(), exit = fadeOut()) {
                    SourceToggle(source = musicSource, onToggle = onSelectMusicSource)
                }
                AnimatedVisibility(
                    visible = panel == Panel.Calendar && eventEditor == null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TodayButton(today = today, onClick = onShowToday)
                        AddEventButton(onClick = onAddEvent)
                    }
                }
                AnimatedVisibility(visible = panel == Panel.Opgaver, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = formatDayAndMonth(todoDay),
                        color = Ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(Dimensions.mediaSectionGap))
            AnimatedContent(
                targetState = panel,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally { w -> dir * w / 5 } + fadeIn()) togetherWith
                        (slideOutHorizontally { w -> -dir * w / 5 } + fadeOut())
                },
                label = "panel",
            ) { target ->
                // Each panel owns its own scroll: the Media panel pins the now-playing controls and
                // scrolls only the queue beneath them, which it can't do from a scroll out here.
                when (target) {
                    Panel.Media -> MediaPanel(
                        audioState = audioState,
                        minimized = mediaMinimized,
                        searchQuery = searchQuery,
                        search = search,
                        pendingPlay = pendingPlay,
                        pendingQueueItemId = pendingQueueItemId,
                        queueRefreshing = queueRefreshing,
                        artist = artist,
                        musicSource = musicSource,
                        playlists = playlists,
                        quickPicks = quickPicks,
                        mixedForYou = mixedForYou,
                        spotifyPlaylists = spotifyPlaylists,
                        spotifyRecentlyPlayed = spotifyRecentlyPlayed,
                        onQueryChange = onQueryChange,
                        onPlay = onPlay,
                        onEnqueue = onEnqueue,
                        onOpenArtist = onOpenArtist,
                        onCloseArtist = onCloseArtist,
                        onPlayTopHit = onPlayTopHit,
                        onShuffleArtist = onShuffleArtist,
                        onTogglePlay = onTogglePlay,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onPlayQueueItem = onPlayQueueItem,
                        onMoveQueueItem = onMoveQueueItem,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // The Calendar panel owns its scroll too: its week view scrolls the hour grid
                    // inside a fixed frame, which a scroll out here would fight. It swaps between the
                    // calendar views and the event editor the same way the Media panel swaps surfaces
                    // — the editor is a surface in this card, never a dialog over the dashboard.
                    Panel.Calendar -> CalendarPanel(
                        eventEditor = eventEditor,
                        savingEvent = savingEvent,
                        today = today,
                        displayedMonth = displayedMonth,
                        selectedDay = selectedDay,
                        calendarView = calendarView,
                        eventsByDay = eventsByDay,
                        weekDays = weekDays,
                        calendarWindow = calendarWindow,
                        nowMinutes = nowMinutes,
                        calendarSources = calendarSources,
                        calendarStale = calendarStale,
                        dayMarks = dayMarks,
                        weekHourHeight = weekHourHeight,
                        onShowMonth = onShowMonth,
                        onShowWeek = onShowWeek,
                        onSelectDay = onSelectDay,
                        onOpenEvent = onOpenEvent,
                        onOpenEventDetail = onOpenEventDetail,
                        onNewEventAt = onNewEventAt,
                        onWeekHourHeight = onWeekHourHeight,
                        reminders = calendarReminders,
                        onSetEventReminder = onSetEventReminder,
                        onSaveEvent = onSaveEvent,
                        onDeleteEvent = onDeleteEvent,
                        onCloseEventEditor = onCloseEventEditor,
                        // The gear sits *after* the toggle because what it filters is whichever view
                        // that toggle has landed on.
                        headerTrailing = {
                            CalendarViewToggle(calendarView, onSelectCalendarView)
                            CalendarSettingsButton(onOpenCalendarSettings)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Opgaver owns its scroll too — it pins its add row above a day pager and scrolls
                    // only the list beneath it, which a scroll out here would fight. It needs no
                    // control at the trailing edge: the ghost add row is its own add affordance.
                    Panel.Opgaver -> TodoPanel(
                        todos = todos,
                        day = todoDay,
                        today = today,
                        calendarWindow = calendarWindow,
                        hasTodoList = calendarHasTodoList,
                        onShowDay = onShowTodoDay,
                        onAddTodo = onAddTodo,
                        onToggleTodo = onToggleTodo,
                        onEditTodo = onEditTodo,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // The collapsed player floats over the browse content rather than scrolling with it, so it
        // lives here as a sibling of the panel Column — CardContainer's Box already applies the
        // card's content padding, and its rounded clip contains the slide-in from the bottom edge.
        val latchedTrack = rememberLatchedTrack(audioState.nowPlaying)
        val hasTrack = panel == Panel.Media && audioState.nowPlaying != null
        // The artist surface takes the panel from the now-playing one, so the player rides the mini
        // bar while it's open — transport stays reachable, and expanding it closes the drill-in.
        AnimatedVisibility(
            visible = hasTrack && (mediaMinimized || artist != null),
            enter = slideInVertically { h -> h } + fadeIn(),
            exit = slideOutVertically { h -> h } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            latchedTrack?.let { track ->
                MiniPlayerBar(
                    track = track,
                    isPlaying = audioState.isPlaying,
                    onExpand = { onSetMediaMinimized(false) },
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onPrevious = onPrevious,
                )
            }
        }

        // The collapse caret floats too: the up-next queue is unbounded, so anchoring the caret to
        // the end of the surface would push it out of reach. Pinned bottom-right it stays where the
        // mini bar's expand caret lands, making the collapse read as the control staying put. Hidden
        // while a play is pending — collapsing a loading surface would strand the spinner offscreen.
        AnimatedVisibility(
            visible = hasTrack && !mediaMinimized && artist == null && pendingPlay == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            MinimizeHandle(
                onClick = { onSetMediaMinimized(true) },
                modifier = Modifier.padding(end = Dimensions.miniPlayerBarPadding),
            )
        }

        // The Calendar panel's popups float here, as siblings of the panel rather than inside it: the
        // month view scrolls, and a popup remembered down there would scroll away from the control
        // that opened it and be clipped by the scroll region on the way.
        if (panel == Panel.Calendar) {
            eventDetail?.let { event ->
                EventDetailPopup(
                    event = event,
                    sources = calendarSources,
                    reminders = calendarReminders,
                    onEdit = onEditEventDetail,
                    onDelete = onDeleteEventDetail,
                    onClose = onCloseEventDetail,
                )
            }
            if (calendarSettingsOpen) {
                CalendarSettingsPopup(
                    view = calendarView,
                    sources = calendarSources,
                    filters = calendarFilters,
                    reminders = calendarReminders,
                    onToggle = onToggleCalendarFilter,
                    onSetReminderDefault = onSetCalendarReminderDefault,
                    onClose = onCloseCalendarSettings,
                )
            }
        }
    }
}

/**
 * Wrap-content pill segmented control switching the right card between its three panels. Sunken
 * [InsetFill] track with square glyph segments; the active one is a filled Forest pill. The segments
 * carry no labels — see [PanelTab].
 */
@Composable
private fun PanelTabs(panel: Panel, onSelectPanel: (Panel) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier.clip(shape).background(InsetFill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PanelTab(
            label = "Musik",
            icon = Res.drawable.media_outline,
            selected = panel == Panel.Media,
            onClick = { onSelectPanel(Panel.Media) },
        )
        PanelTab(
            label = "Kalender",
            icon = Res.drawable.calender_filled,
            selected = panel == Panel.Calendar,
            onClick = { onSelectPanel(Panel.Calendar) },
        )
        PanelTab(
            label = "Opgaver",
            icon = Res.drawable.checklist,
            selected = panel == Panel.Opgaver,
            onClick = { onSelectPanel(Panel.Opgaver) },
        )
    }
}

/**
 * The browse-source badge at the right card's trailing edge: a Forest disc carrying the current
 * [MusicSource]'s letter, tapped to swap to the other one. Two people's accounts feed one Music
 * Assistant library, and this is what keeps their browse shelves apart — the letter differentiates
 * them, not colour, since the accent is a single fixed token.
 */
@Composable
private fun SourceToggle(source: MusicSource, onToggle: (MusicSource) -> Unit, modifier: Modifier = Modifier) {
    val next = if (source == MusicSource.YtMusic) MusicSource.Spotify else MusicSource.YtMusic
    Box(
        modifier = modifier
            .size(Dimensions.minTouch)
            .clickable { onToggle(next) }
            .semantics { contentDescription = "Skift musikkilde til ${next.badge}" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Dimensions.sourceBadgeSize)
                .shadow(Dimensions.pillElevation, CircleShape)
                .clip(CircleShape)
                .background(Forest),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = source.badge, color = OnForest, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

/**
 * One segment: a square glyph target, no label. Three labelled pills would run wider than the right
 * card's own minimum with the trailing control beside them, so [label] is carried as the segment's
 * accessible name instead of being printed.
 */
@Composable
private fun PanelTab(label: String, icon: DrawableResource, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(if (selected) Modifier.background(Forest, shape) else Modifier)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .size(Dimensions.minTouch)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (selected) OnForest else Ink,
            modifier = Modifier.size(20.dp),
        )
    }
}
