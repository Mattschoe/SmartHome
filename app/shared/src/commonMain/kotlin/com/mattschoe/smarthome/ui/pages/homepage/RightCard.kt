package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.mattschoe.smarthome.data.CalendarFilters
import com.mattschoe.smarthome.data.DaysPerWeek
import com.mattschoe.smarthome.data.MinutesPerDay
import com.mattschoe.smarthome.data.calendarGrid
import com.mattschoe.smarthome.data.danishMonths
import com.mattschoe.smarthome.data.formatTimeOfDay
import com.mattschoe.smarthome.data.layoutDayEvents
import com.mattschoe.smarthome.data.formatTrackTime
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarEventDraft
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.CalendarView
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.BrowseKind
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.data.volumeFractionFromX
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.components.InsetSurface
import com.mattschoe.smarthome.ui.components.PageIndicator
import com.mattschoe.smarthome.ui.components.PillChip
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.components.verticalScrollFade
import com.mattschoe.smarthome.ui.theme.ArtScrim
import com.mattschoe.smarthome.ui.theme.CalendarDotColors
import com.mattschoe.smarthome.ui.theme.Card
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.haCalendarColor
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.InsetFill
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnArt
import com.mattschoe.smarthome.ui.theme.OnForest
import com.mattschoe.smarthome.ui.theme.Rose
import com.mattschoe.smarthome.ui.theme.Teal
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.add_filled
import smarthome.shared.generated.resources.calendar_view_week
import smarthome.shared.generated.resources.calender_filled
import smarthome.shared.generated.resources.checkbox_blank
import smarthome.shared.generated.resources.checkbox_filled
import smarthome.shared.generated.resources.close_filled
import smarthome.shared.generated.resources.drop_down_filled
import smarthome.shared.generated.resources.drop_up_filled
import smarthome.shared.generated.resources.equalizer_filled
import smarthome.shared.generated.resources.media_outline
import smarthome.shared.generated.resources.music_note_filled
import smarthome.shared.generated.resources.pause_filled
import smarthome.shared.generated.resources.play_filled
import smarthome.shared.generated.resources.repeat_filled
import smarthome.shared.generated.resources.arrow_back_filled
import smarthome.shared.generated.resources.search_outline
import smarthome.shared.generated.resources.settings_filled
import smarthome.shared.generated.resources.shuffle_filled
import smarthome.shared.generated.resources.skip_next_filled
import smarthome.shared.generated.resources.skip_previous_filled
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The flex-1.12 right card: a fixed Media/Calendar [PanelTabs] segmented control over a scrolling
 * content region. The Media panel swaps between the now-playing surface and the browse surface
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
    /** Whether the Calendar panel shows the month grid or [selectedDay]'s week. */
    calendarView: CalendarView,
    selectedDayEvents: List<CalendarEvent>,
    selectedDayTodos: List<TodoItem>,
    /** The week view's seven columns (Monday first) and the events falling in them, by day. */
    weekDays: List<LocalDate>,
    weekEvents: Map<LocalDate, List<CalendarEvent>>,
    /** Minutes from midnight — where the week grid draws its "now" line. */
    nowMinutes: Int,
    /** The home's calendars, in the order that assigns each its agenda dot color. */
    calendarSources: List<CalendarSource>,
    /** Whether the calendar is being rendered from the offline cache rather than from a live fetch. */
    calendarStale: Boolean,
    /** Whether Home Assistant exposes a todo list the checklist can write to. */
    calendarHasTodoList: Boolean,
    /** Per-day marks for the displayed month's grid, keyed by day-of-month. */
    dayMarks: Map<Int, DayMarks>,
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
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onAddTodo: (LocalDate, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onEditTodo: (String, String) -> Unit,
    onAddEvent: () -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onOpenEventDetail: (CalendarEvent) -> Unit,
    onEditEventDetail: () -> Unit,
    onDeleteEventDetail: () -> Unit,
    onCloseEventDetail: () -> Unit,
    onOpenCalendarSettings: () -> Unit,
    onCloseCalendarSettings: () -> Unit,
    onToggleCalendarFilter: (String) -> Unit,
    onSaveEvent: (String, CalendarEventDraft) -> Unit,
    onDeleteEvent: () -> Unit,
    onCloseEventEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardContainer(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(24.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            // The tabs are wrap-content, so the trailing edge beside them is free for the control the
            // showing panel needs there: the source badge over Media, the add-event button over the
            // Calendar. Both animate, to keep the row from jumping as the tab changes. The "+" stands
            // down while the editor is open — re-opening a blank form would discard what is typed.
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
                    AddEventButton(onClick = onAddEvent)
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
                    Panel.Calendar -> CalendarSurfaceContent(
                        eventEditor = eventEditor,
                        savingEvent = savingEvent,
                        today = today,
                        displayedMonth = displayedMonth,
                        selectedDay = selectedDay,
                        calendarView = calendarView,
                        selectedDayEvents = selectedDayEvents,
                        selectedDayTodos = selectedDayTodos,
                        weekDays = weekDays,
                        weekEvents = weekEvents,
                        nowMinutes = nowMinutes,
                        calendarSources = calendarSources,
                        calendarStale = calendarStale,
                        calendarHasTodoList = calendarHasTodoList,
                        dayMarks = dayMarks,
                        onSelectCalendarView = onSelectCalendarView,
                        onPrevMonth = onPrevMonth,
                        onNextMonth = onNextMonth,
                        onPrevWeek = onPrevWeek,
                        onNextWeek = onNextWeek,
                        onSelectDay = onSelectDay,
                        onAddTodo = onAddTodo,
                        onToggleTodo = onToggleTodo,
                        onEditTodo = onEditTodo,
                        onOpenEvent = onOpenEvent,
                        onOpenEventDetail = onOpenEventDetail,
                        onOpenCalendarSettings = onOpenCalendarSettings,
                        onSaveEvent = onSaveEvent,
                        onDeleteEvent = onDeleteEvent,
                        onCloseEventEditor = onCloseEventEditor,
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
                    onToggle = onToggleCalendarFilter,
                    onClose = onCloseCalendarSettings,
                )
            }
        }
    }
}

/**
 * Holds on to the last non-null [track]. The mini player and now-playing surface animate out when
 * playback stops, and without a latch they would render their final frames against a null track.
 */
@Composable
private fun rememberLatchedTrack(track: MediaTrack?): MediaTrack? {
    val latched = remember { mutableStateOf(track) }
    if (track != null) latched.value = track
    return latched.value
}

/**
 * Wrap-content pill segmented control switching the right card between Media and Calendar. Sunken
 * [InsetFill] track with two content-sized segments; the active one is a filled Forest pill.
 */
@Composable
private fun PanelTabs(panel: Panel, onSelectPanel: (Panel) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier.clip(shape).background(InsetFill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PanelTab(
            label = "Media",
            icon = Res.drawable.media_outline,
            selected = panel == Panel.Media,
            onClick = { onSelectPanel(Panel.Media) },
        )
        PanelTab(
            label = "Calendar",
            icon = Res.drawable.calender_filled,
            selected = panel == Panel.Calendar,
            onClick = { onSelectPanel(Panel.Calendar) },
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
 * The Calendar panel's add-event button, taking the header slot the Media panel gives its source
 * badge and built like it — a Forest disc inside a full touch target. It opens the editor as a
 * surface *inside* this card, so the rest of the dashboard stays live beside it.
 */
@Composable
private fun AddEventButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dimensions.minTouch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Nyt arrangement" },
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
            Icon(
                painter = painterResource(Res.drawable.add_filled),
                contentDescription = null,
                tint = OnForest,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PanelTab(label: String, icon: DrawableResource, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    val contentColor = if (selected) OnForest else Ink
    Row(
        modifier = Modifier
            .clip(shape)
            .then(if (selected) Modifier.background(Forest, shape) else Modifier)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .heightIn(min = Dimensions.minTouch)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Text(text = label, color = contentColor, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    }
}

/** Danish, Monday-first weekday initials for the grid header (Man, Tir, Ons, Tor, Fre, Lør, Søn). */
private val danishWeekdayInitials = listOf("M", "T", "O", "T", "F", "L", "S")

/** Which of the Calendar panel's two surfaces is showing — the month/week views, or the editor. */
private enum class CalendarSurface { Views, Editor }

/**
 * The Calendar panel's surface swap, built like [MediaPanel]'s: the month/week views, or the event
 * editor over them. Each surface owns its own scroll, so the transition never fights one.
 */
@Composable
private fun CalendarSurfaceContent(
    eventEditor: EventEditorTarget?,
    savingEvent: Boolean,
    today: LocalDate,
    displayedMonth: LocalDate,
    selectedDay: LocalDate,
    calendarView: CalendarView,
    selectedDayEvents: List<CalendarEvent>,
    selectedDayTodos: List<TodoItem>,
    weekDays: List<LocalDate>,
    weekEvents: Map<LocalDate, List<CalendarEvent>>,
    nowMinutes: Int,
    calendarSources: List<CalendarSource>,
    calendarStale: Boolean,
    calendarHasTodoList: Boolean,
    dayMarks: Map<Int, DayMarks>,
    onSelectCalendarView: (CalendarView) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onAddTodo: (LocalDate, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onEditTodo: (String, String) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onOpenEventDetail: (CalendarEvent) -> Unit,
    onOpenCalendarSettings: () -> Unit,
    onSaveEvent: (String, CalendarEventDraft) -> Unit,
    onDeleteEvent: () -> Unit,
    onCloseEventEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = if (eventEditor != null) CalendarSurface.Editor else CalendarSurface.Views,
        modifier = modifier.fillMaxSize(),
        // `using null` for the same reason as [MediaPanel]: both surfaces fill the panel, so there is
        // no container height to animate.
        transitionSpec = {
            (fadeIn(tween(200)) + slideInVertically { h -> h / 8 }) togetherWith
                (fadeOut(tween(120)) + slideOutVertically { h -> h / 8 }) using null
        },
        label = "calendar-surface",
    ) { target ->
        // The editor state is read inside the transition, so it can be null on the frame the surface
        // animates out — the views stand in for that frame, as the browse surface does for Media.
        if (target == CalendarSurface.Editor && eventEditor != null) {
            EventEditorSurface(
                target = eventEditor,
                saving = savingEvent,
                sources = calendarSources,
                onSave = onSaveEvent,
                onDelete = onDeleteEvent,
                onBack = onCloseEventEditor,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CalendarPanel(
                today = today,
                displayedMonth = displayedMonth,
                selectedDay = selectedDay,
                view = calendarView,
                events = selectedDayEvents,
                weekDays = weekDays,
                weekEvents = weekEvents,
                nowMinutes = nowMinutes,
                todos = selectedDayTodos,
                sources = calendarSources,
                stale = calendarStale,
                hasTodoList = calendarHasTodoList,
                dayMarks = dayMarks,
                onSelectView = onSelectCalendarView,
                onPrevMonth = onPrevMonth,
                onNextMonth = onNextMonth,
                onPrevWeek = onPrevWeek,
                onNextWeek = onNextWeek,
                onSelectDay = onSelectDay,
                onAddTodo = onAddTodo,
                onToggleTodo = onToggleTodo,
                onEditTodo = onEditTodo,
                onOpenEvent = onOpenEvent,
                onOpenEventDetail = onOpenEventDetail,
                onOpenCalendarSettings = onOpenCalendarSettings,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The Calendar panel in either of its two views, and the owner of the scrolling.
 *
 * *Month* — month navigation over a Monday-first month grid, then the selected day's read-only
 * agenda and its editable todo checklist, all in one scroll.
 *
 * *Week* — the selected day's Monday-to-Sunday week as a time grid, which **replaces** both the month
 * grid and the agenda: the header, the day columns and the all-day strip are fixed, the hour grid
 * scrolls inside the height it is left, and the todo checklist keeps a bounded strip at the bottom.
 *
 * Either way selecting a day scopes the todos (and, in month view, the agenda) — [events]/[todos]
 * arrive pre-filtered to [selectedDay].
 */
@Composable
private fun CalendarPanel(
    today: LocalDate,
    displayedMonth: LocalDate,
    selectedDay: LocalDate,
    view: CalendarView,
    events: List<CalendarEvent>,
    weekDays: List<LocalDate>,
    weekEvents: Map<LocalDate, List<CalendarEvent>>,
    nowMinutes: Int,
    todos: List<TodoItem>,
    sources: List<CalendarSource>,
    stale: Boolean,
    hasTodoList: Boolean,
    dayMarks: Map<Int, DayMarks>,
    onSelectView: (CalendarView) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onAddTodo: (LocalDate, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onEditTodo: (String, String) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onOpenEventDetail: (CalendarEvent) -> Unit,
    onOpenCalendarSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (view) {
        CalendarView.Month -> {
            val calendarScroll = rememberScrollState()
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .verticalScrollFade(calendarScroll)
                    .verticalScroll(calendarScroll),
            ) {
                CalendarHeader(displayedMonth, weekDays, view, stale, onSelectView, onOpenCalendarSettings)
                Spacer(Modifier.height(16.dp))
                WeekdayHeader()
                Spacer(Modifier.height(4.dp))
                MonthGrid(displayedMonth, today, selectedDay, dayMarks, sources, onSelectDay, onPrevMonth, onNextMonth)
                AgendaSection(selectedDay, today, events, sources, onOpenEvent)
                Spacer(Modifier.height(Dimensions.mediaSectionGap))
                TodoSection(selectedDay, todos, hasTodoList, onAddTodo, onToggleTodo, onEditTodo)
            }
        }
        CalendarView.Week -> Column(modifier.fillMaxWidth()) {
            CalendarHeader(displayedMonth, weekDays, view, stale, onSelectView, onOpenCalendarSettings)
            Spacer(Modifier.height(12.dp))
            WeekHeader(weekDays, today, selectedDay, onSelectDay)
            AllDayStrip(weekDays, weekEvents, sources, onOpenEventDetail)
            WeekGrid(
                days = weekDays,
                weekEvents = weekEvents,
                today = today,
                nowMinutes = nowMinutes,
                sources = sources,
                onPrevWeek = onPrevWeek,
                onNextWeek = onNextWeek,
                onOpenEvent = onOpenEventDetail,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
            // The checklist takes a strip of *fixed* height, not one that fits its rows: the grid gets
            // the rest of the card, and it gets the same amount of it on every day and week, so the
            // hours don't jump as the day's checklist grows and shrinks. A long list scrolls in the
            // strip rather than pushing the hours off the bottom.
            val todoScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .height(Dimensions.weekTodoStripHeight)
                    .verticalScroll(todoScroll)
                    .padding(top = 8.dp),
            ) {
                TodoSection(selectedDay, todos, hasTodoList, onAddTodo, onToggleTodo, onEditTodo)
            }
        }
    }
}

/**
 * The Calendar panel's title row: Danish month + year in month view ("Juli 2026"), the week's date
 * range in week view, and at the trailing edge the [CalendarViewToggle] that swaps between them.
 * Month/week changes themselves come from swiping the grid below. When the calendar is being rendered
 * from the offline cache it says so beside the title, so nobody plans a day around a list that
 * stopped updating. The gear beyond the toggle picks which calendars the view being shown draws —
 * it sits *after* the toggle because what it filters is whichever view that toggle has landed on.
 */
@Composable
private fun CalendarHeader(
    displayedMonth: LocalDate,
    weekDays: List<LocalDate>,
    view: CalendarView,
    stale: Boolean,
    onSelectView: (CalendarView) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val monthName = danishMonths[displayedMonth.month.number - 1].replaceFirstChar { it.uppercase() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (view) {
                CalendarView.Month -> "$monthName ${displayedMonth.year}"
                CalendarView.Week -> weekRangeLabel(weekDays)
            },
            color = Ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        if (stale) {
            Text(text = "Offline", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        CalendarViewToggle(view, onSelectView)
        Box(
            modifier = Modifier
                .size(Dimensions.minTouch)
                .clip(CircleShape)
                .clickable(onClick = onOpenSettings)
                .semantics { contentDescription = "Vælg kalendere" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.settings_filled),
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The week's span as one line: "27. juli – 2. august", collapsing to "27. – 31. juli" when the whole
 * week sits in a single month.
 */
private fun weekRangeLabel(days: List<LocalDate>): String {
    val first = days.firstOrNull() ?: return ""
    val last = days.last()
    val firstMonth = danishMonths[first.month.number - 1]
    val lastMonth = danishMonths[last.month.number - 1]
    return if (first.month == last.month) "${first.day}. – ${last.day}. $lastMonth"
    else "${first.day}. $firstMonth – ${last.day}. $lastMonth"
}

/**
 * Icon-only segmented control swapping the Calendar panel between month and week, built like
 * [PanelTabs] one size down: a sunken [InsetFill] track whose active segment is a filled Forest pill.
 */
@Composable
private fun CalendarViewToggle(
    view: CalendarView,
    onSelectView: (CalendarView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier.clip(shape).background(InsetFill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CalendarViewSegment(
            icon = Res.drawable.calender_filled,
            label = "Månedsvisning",
            selected = view == CalendarView.Month,
            onClick = { onSelectView(CalendarView.Month) },
        )
        CalendarViewSegment(
            icon = Res.drawable.calendar_view_week,
            label = "Ugevisning",
            selected = view == CalendarView.Week,
            onClick = { onSelectView(CalendarView.Week) },
        )
    }
}

@Composable
private fun CalendarViewSegment(
    icon: DrawableResource,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(Dimensions.minTouch)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(Forest, CircleShape) else Modifier)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
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

/** Seven equal-width, muted weekday initials aligned to the [MonthGrid] columns below. */
@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        danishWeekdayInitials.forEach { initial ->
            Text(
                text = initial,
                color = Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A 6×7 Monday-first month grid. Today is the accent cell; the selected day (if not today) is ringed.
 * A horizontal swipe changes month — right → previous, left → next; the same navigation is exposed to
 * screen readers as custom actions since there are no on-screen month buttons.
 */
@Composable
private fun MonthGrid(
    displayedMonth: LocalDate,
    today: LocalDate,
    selectedDay: LocalDate,
    dayMarks: Map<Int, DayMarks>,
    sources: List<CalendarSource>,
    onSelectDay: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val cells = calendarGrid(displayedMonth.year, displayedMonth.month.number)
    fun sameMonth(date: LocalDate) =
        date.year == displayedMonth.year && date.month.number == displayedMonth.month.number
    val currentPrev by rememberUpdatedState(onPrevMonth)
    val currentNext by rememberUpdatedState(onNextMonth)
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                var totalDrag = 0f
                val threshold = 48.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (totalDrag > threshold) currentPrev()
                        else if (totalDrag < -threshold) currentNext()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    totalDrag += dragAmount
                }
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Forrige måned") { onPrevMonth(); true },
                    CustomAccessibilityAction("Næste måned") { onNextMonth(); true },
                )
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = cells[row * 7 + col]
                    DayCell(
                        day = day,
                        isToday = day != null && sameMonth(today) && today.day == day,
                        isSelected = day != null && sameMonth(selectedDay) && selectedDay.day == day,
                        marks = day?.let { dayMarks[it] },
                        sources = sources,
                        onClick = {
                            if (day != null) {
                                onSelectDay(LocalDate(displayedMonth.year, displayedMonth.month.number, day))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * One month-grid cell. The whole cell is the touch target; a 34dp disc carries the number (filled
 * Forest for today, ringed for the selected day) with the day's [DayMarkDots] beneath it.
 */
@Composable
private fun DayCell(
    day: Int?,
    isToday: Boolean,
    isSelected: Boolean,
    marks: DayMarks?,
    sources: List<CalendarSource>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(Dimensions.minTouch)
            .then(if (day != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { if (day != null) contentDescription = "Dag $day" },
        contentAlignment = Alignment.Center,
    ) {
        if (day == null) return@Box
        val disc = when {
            isToday -> Modifier.background(Forest, CircleShape)
            isSelected -> Modifier.border(1.5.dp, Forest, CircleShape)
            else -> Modifier
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(Dimensions.calendarDayDisc).clip(CircleShape).then(disc),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.toString(),
                    color = if (isToday) OnForest else Ink,
                    fontSize = 15.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Spacer(Modifier.height(3.dp))
            DayMarkDots(marks, sources)
        }
    }
}

/** How many dots one month-grid cell shows before the rest are dropped. */
private const val MaxDayMarks = 4

/**
 * The dots under a day's number: **one per calendar** that has an event that day, in the day's
 * source order and each in that calendar's own color, so a day reads as "one of his, two of hers"
 * at a glance. They overlap slightly to stay inside the cell. Todos add a muted dot last.
 *
 * Drawn rather than laid out as bordered boxes: a dot is only [Dimensions.dayMarkDot] across, and a
 * hairline outline that size lands under a pixel — the rasterizer spends the circle's antialiased
 * edge on the stroke and the dot squares off. So the separation between overlapping dots is a
 * *filled* card-colored halo drawn under each one, which stays round at any density.
 *
 * Capped at [MaxDayMarks] — beyond that a cell reads as a smear rather than as "a few things" — and
 * an empty day still occupies the row so the grid doesn't shift.
 */
@Composable
private fun DayMarkDots(marks: DayMarks?, sources: List<CalendarSource>) {
    val colors = buildList {
        marks?.sourceIds?.forEach { add(calendarDotColor(it, sources)) }
        if (marks?.hasTodo == true) add(Muted)
    }.take(MaxDayMarks)
    val diameter = Dimensions.dayMarkDot
    val step = diameter - Dimensions.dayMarkOverlap
    // Each dot after the first adds only [step]; the first needs its full diameter.
    val width = diameter + step * (colors.size - 1).coerceAtLeast(0)
    Canvas(Modifier.size(width = width, height = diameter)) {
        val radius = diameter.toPx() / 2f
        val halo = radius + Dimensions.dayMarkRing.toPx()
        colors.forEachIndexed { index, color ->
            val center = Offset(radius + step.toPx() * index, radius)
            // Under the dot, so it clears the neighbour it laps over without dulling its own edge.
            if (index > 0) drawCircle(Card, radius = halo, center = center)
            drawCircle(color, radius = radius, center = center)
        }
    }
}

/**
 * The week view's day header: seven columns (offset by the grid's hour gutter) of weekday initial +
 * day number, styled like [DayCell] — a filled Forest disc for today, a ring for the selected day.
 * Tapping a column selects that day, which re-scopes the checklist under the grid.
 */
@Composable
private fun WeekHeader(
    days: List<LocalDate>,
    today: LocalDate,
    selectedDay: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(Dimensions.weekTimeGutter))
        days.forEach { date ->
            val isToday = date == today
            val isSelected = date == selectedDay
            val disc = when {
                isToday -> Modifier.background(Forest, CircleShape)
                isSelected -> Modifier.border(1.5.dp, Forest, CircleShape)
                else -> Modifier
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectDay(date) }
                    .semantics {
                        contentDescription = "${date.day}. ${danishMonths[date.month.number - 1]}"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = danishWeekdayInitials[date.dayOfWeek.isoDayNumber - 1],
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier.size(Dimensions.calendarDayDisc).clip(CircleShape).then(disc),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = date.day.toString(),
                        color = if (isToday) OnForest else Ink,
                        fontSize = 15.sp,
                        fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** How many all-day chips a day column shows while the strip is collapsed. */
private const val WeekAllDayCollapsedChips = 1

/**
 * The band between the day header and the hour grid, holding what has no place on a clock: all-day
 * events, and the days a multi-day event merely spans. Since events are expanded per day upstream,
 * such an event shows as one chip in each day's column rather than one bar across them.
 *
 * It stays **collapsed** to a single row — a busy Tuesday would otherwise push the hours off the
 * card — with a "+n" hint on the days holding more, and a caret in the gutter that opens the lot.
 * A week with no all-day entries takes no room at all.
 */
@Composable
private fun AllDayStrip(
    days: List<LocalDate>,
    weekEvents: Map<LocalDate, List<CalendarEvent>>,
    sources: List<CalendarSource>,
    onOpenEvent: (CalendarEvent) -> Unit,
) {
    val byDay = days.associateWith { date ->
        weekEvents[date].orEmpty().filter { it.startMinute == null }
    }
    if (byDay.values.all { it.isEmpty() }) return
    var expanded by remember { mutableStateOf(false) }
    val hasMore = byDay.values.any { it.size > WeekAllDayCollapsedChips }

    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .width(Dimensions.weekTimeGutter)
                .height(Dimensions.weekAllDayChipHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (hasMore) {
                Icon(
                    painter = painterResource(
                        if (expanded) Res.drawable.drop_up_filled else Res.drawable.drop_down_filled,
                    ),
                    contentDescription =
                        if (expanded) "Skjul heldagsbegivenheder" else "Vis alle heldagsbegivenheder",
                    tint = Muted,
                    modifier = Modifier
                        .size(Dimensions.weekChevronSize)
                        .clickable { expanded = !expanded },
                )
            }
        }
        days.forEach { date ->
            val events = byDay.getValue(date)
            val shown = if (expanded) events else events.take(WeekAllDayCollapsedChips)
            val hidden = events.size - shown.size
            Column(
                modifier = Modifier.weight(1f).padding(end = Dimensions.weekBlockGap),
                verticalArrangement = Arrangement.spacedBy(Dimensions.weekBlockGap),
            ) {
                shown.forEach { event ->
                    AllDayChip(event, calendarDotColor(event.sourceId, sources), onOpenEvent)
                }
                if (hidden > 0) {
                    Text(
                        text = "+$hidden",
                        color = Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { expanded = true }
                            .padding(horizontal = Dimensions.weekBlockPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayChip(event: CalendarEvent, color: Color, onOpen: (CalendarEvent) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.weekAllDayChipHeight)
            .clip(RoundedCornerShape(Dimensions.weekBlockRadius))
            .background(eventBlockFill(color))
            .clickable { onOpen(event) }
            .semantics { contentDescription = "Åbn ${event.title}" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = event.title,
            color = Ink,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Dimensions.weekBlockPadding),
        )
    }
}

/** Fill alpha of an event block: its calendar's color dropped back far enough that the title reads. */
private const val WeekBlockFillAlpha = 0.22f

/**
 * An event block's fill: [WeekBlockFillAlpha] of its calendar's color flattened onto the card, so the
 * block is opaque. A translucent fill would let the hour rules under it show through the event.
 */
private fun eventBlockFill(color: Color): Color = color.copy(alpha = WeekBlockFillAlpha).compositeOver(Card)

/** Where the grid opens on a week with no timed event to aim at. */
private const val WeekDefaultOpenMinute = 7 * 60

/** How far above the week's first event the grid opens, so the block isn't flush with the top edge. */
private const val WeekOpenLeadMinutes = 60

/** Vertical distance into the grid of a minute-from-midnight — the one place the hour scale lives. */
private fun minuteOffset(minute: Int): Dp = Dimensions.weekHourHeight * (minute / 60f)

/**
 * The hour grid: a full day of [Dimensions.weekHourHeight] rows, taller than the card, scrolling
 * inside the height the panel leaves it. It opens on the week's first event (an hour above it) rather
 * than at midnight. A horizontal swipe changes week — right → previous, left → next — mirroring
 * [MonthGrid], with the same navigation exposed to screen readers as custom actions.
 */
@Composable
private fun WeekGrid(
    days: List<LocalDate>,
    weekEvents: Map<LocalDate, List<CalendarEvent>>,
    today: LocalDate,
    nowMinutes: Int,
    sources: List<CalendarSource>,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val currentPrev by rememberUpdatedState(onPrevWeek)
    val currentNext by rememberUpdatedState(onNextWeek)

    // The week's earliest timed event; all-day entries (null minutes) are in the strip above, so they
    // count as "no event" here. A week with nothing timed at all opens at [WeekDefaultOpenMinute].
    val firstEventMinute = days.minOf { date ->
        weekEvents[date].orEmpty().minOfOrNull { it.startMinute ?: MinutesPerDay } ?: MinutesPerDay
    }
    val openMinute =
        if (firstEventMinute >= MinutesPerDay) WeekDefaultOpenMinute
        else (firstEventMinute - WeekOpenLeadMinutes).coerceAtLeast(0)

    LaunchedEffect(days.firstOrNull(), openMinute) {
        // The scroll range is only known once the grid has been laid out; scrolling before that
        // clamps to 0 and the week silently opens at midnight.
        snapshotFlow { scroll.maxValue }.first { it > 0 }
        scroll.scrollTo(with(density) { minuteOffset(openMinute).roundToPx() })
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                var totalDrag = 0f
                val threshold = 48.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (totalDrag > threshold) currentPrev()
                        else if (totalDrag < -threshold) currentNext()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    totalDrag += dragAmount
                }
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Forrige uge") { onPrevWeek(); true },
                    CustomAccessibilityAction("Næste uge") { onNextWeek(); true },
                )
            }
            .verticalScroll(scroll),
    ) {
        Row(Modifier.fillMaxWidth().height(minuteOffset(MinutesPerDay))) {
            HourGutter()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                HourRules(Modifier.matchParentSize())
                Row(Modifier.matchParentSize()) {
                    days.forEach { date ->
                        DayColumn(
                            events = weekEvents[date].orEmpty(),
                            sources = sources,
                            onOpenEvent = onOpenEvent,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                if (today in days) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimensions.weekNowLineHeight)
                            .offset(y = minuteOffset(nowMinutes))
                            .background(Rose),
                    )
                }
            }
        }
    }
}

/**
 * The gutter label's line box, trimmed to the glyphs. Newsreader's default line box carries more
 * space above the digits than below, so a centred label reads a hair below the rule it names.
 */
private val hourLabelStyle = TextStyle(
    lineHeight = 9.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/**
 * The grid's left edge: an hour label per hour rule (midnight's needs none). Each label sits in a
 * box centred **on** its rule, so the number lines up with the line it belongs to rather than
 * hanging above it.
 */
@Composable
private fun HourGutter() {
    Box(Modifier.width(Dimensions.weekTimeGutter).fillMaxHeight()) {
        for (hour in 1 until MinutesPerDay / 60) {
            Box(
                modifier = Modifier
                    .offset(y = minuteOffset(hour * 60) - Dimensions.weekHourLabelHeight / 2)
                    .fillMaxWidth()
                    .height(Dimensions.weekHourLabelHeight)
                    .padding(end = 4.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = formatTimeOfDay(LocalTime(hour, 0)),
                    color = Muted,
                    fontSize = 9.sp,
                    style = hourLabelStyle,
                )
            }
        }
    }
}

/** Hairline hour rules and day dividers under the blocks — drawn, since they land under a pixel. */
@Composable
private fun HourRules(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val hourStep = Dimensions.weekHourHeight.toPx()
        for (hour in 1 until MinutesPerDay / 60) {
            val y = hourStep * hour
            drawLine(CardBorder, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val dayStep = size.width / DaysPerWeek
        for (day in 1 until DaysPerWeek) {
            val x = dayStep * day
            drawLine(CardBorder, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }
    }
}

/**
 * One day's blocks, placed by their minute bounds. Overlapping events split the column into lanes
 * ([layoutDayEvents]) and each block is inset to its own, so two things at once read side by side.
 */
@Composable
private fun DayColumn(
    events: List<CalendarEvent>,
    sources: List<CalendarSource>,
    onOpenEvent: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positioned = remember(events) { layoutDayEvents(events) }
    BoxWithConstraints(modifier) {
        val columnWidth = maxWidth
        positioned.forEach { placed ->
            val laneWidth = columnWidth / placed.laneCount
            val blockHeight = minuteOffset(placed.endMinute - placed.startMinute)
                .coerceAtLeast(Dimensions.weekMinBlockHeight)
            EventBlock(
                event = placed.event,
                color = calendarDotColor(placed.event.sourceId, sources),
                showTime = blockHeight >= Dimensions.weekBlockTimeMinHeight,
                onOpen = onOpenEvent,
                modifier = Modifier
                    .offset(x = laneWidth * placed.lane, y = minuteOffset(placed.startMinute))
                    .width(laneWidth - Dimensions.weekBlockGap)
                    .height(blockHeight),
            )
        }
    }
}

/**
 * One event in the grid: its calendar's color at low alpha, with a bar of the full color on the
 * leading edge. Only a block with room for it ([showTime]) prints the start time under the title.
 *
 * Tapping it opens the detail popup rather than the editor: at [Dimensions.weekMinBlockHeight] and a
 * seventh of the card wide, a block is often too small to say what it even is.
 */
@Composable
private fun EventBlock(
    event: CalendarEvent,
    color: Color,
    showTime: Boolean,
    onOpen: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Dimensions.weekBlockRadius))
            .background(eventBlockFill(color))
            .clickable { onOpen(event) }
            .semantics { contentDescription = "Åbn ${event.title}" },
    ) {
        Box(Modifier.width(Dimensions.weekBlockBarWidth).fillMaxHeight().background(color))
        Column(Modifier.padding(horizontal = Dimensions.weekBlockPadding, vertical = 2.dp)) {
            Text(
                text = event.title,
                color = Ink,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showTime) {
                Text(
                    text = event.time,
                    color = InkSoft,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * "I DAG" (or the selected date) label over the day's event rows. A row opens that event in the
 * editor — including one on a read-only calendar, which opens as details that can't be changed.
 */
@Composable
private fun AgendaSection(
    selectedDay: LocalDate,
    today: LocalDate,
    events: List<CalendarEvent>,
    sources: List<CalendarSource>,
    onOpenEvent: (CalendarEvent) -> Unit,
) {
    val label =
        if (selectedDay == today) "I dag"
        else "${selectedDay.day}. ${danishMonths[selectedDay.month.number - 1]}"
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(label, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        if (events.isEmpty()) {
            Text("Ingen begivenheder", color = Muted, fontSize = 15.sp)
        } else {
            events.forEachIndexed { index, event ->
                AgendaRow(event, calendarDotColor(event.sourceId, sources)) { onOpenEvent(event) }
                if (index != events.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * The dot color for an event: its **calendar's**, not its row's — so a day's events read as "one of
 * his, two of hers" rather than as an arbitrary rotation. The calendar's own Home Assistant color
 * wins where it has one (see [haCalendarColor]); otherwise the color falls out of its position in
 * [sources], which is stable across sessions. An event from an unknown calendar (a stale cache read
 * before sources are known) falls back to the first color.
 */
internal fun calendarDotColor(sourceId: String, sources: List<CalendarSource>): Color {
    val index = sources.indexOfFirst { it.id == sourceId }
    haCalendarColor(sources.getOrNull(index)?.color)?.let { return it }
    return CalendarDotColors[index.coerceAtLeast(0) % CalendarDotColors.size]
}

@Composable
private fun AgendaRow(event: CalendarEvent, dotColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The row keeps its own compact height; the target is padded out to a comfortable one
            // rather than the rows being spread apart to reach [Dimensions.minTouch].
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .semantics { contentDescription = "Åbn ${event.title}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        // Wide enough for the longest label an all-day event produces ("Hele dagen").
        Text(event.time, color = InkSoft, fontSize = 15.sp, modifier = Modifier.width(88.dp))
        Text(
            text = event.title,
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * "OPGAVER" label over the day's keyed todo rows, then the ghost add row. A home whose Home Assistant
 * has no due-date-capable todo list ([hasTodoList]) gets a note instead: every todo intent is inert
 * without one, so an add row there would silently swallow whatever is typed into it.
 */
@Composable
private fun TodoSection(
    selectedDay: LocalDate,
    todos: List<TodoItem>,
    hasTodoList: Boolean,
    onAddTodo: (LocalDate, String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onEditTodo: (String, String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel("Opgaver", fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        if (!hasTodoList) {
            Text(
                text = "Ingen opgaveliste i Home Assistant",
                color = Muted,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return@Column
        }
        // Keyed by stable id so a backend echo (Phase 9) re-keys existing rows instead of rebuilding.
        todos.forEach { todo ->
            key(todo.id) {
                TodoRow(
                    todo = todo,
                    onToggle = { onToggleTodo(todo.id) },
                    onCommitEdit = { text -> onEditTodo(todo.id, text) },
                )
            }
        }
        AddTodoRow(onAdd = { text -> onAddTodo(selectedDay, text) })
    }
}

/**
 * A todo row with two targets, the split every checklist uses: **the box toggles done, the label
 * opens the editor**. Editing was a long-press before, which a mouse has no comfortable equivalent
 * for — a plain tap on the label reads the same on the tablet and in the desktop window. Done rows
 * are struck through and muted; committing a blank label removes the item (the delete escape).
 */
@Composable
private fun TodoRow(todo: TodoItem, onToggle: () -> Unit, onCommitEdit: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        TodoInlineEdit(
            initial = todo.label,
            checked = todo.done,
            onCommit = { text, _ -> editing = false; onCommitEdit(text) },
            onCancel = { editing = false },
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().height(Dimensions.minTouch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The glyph stays 24dp wide — anything wider would push the label out of line with the
            // agenda rows above — but its target fills the row's full height, so it is comfortably
            // hittable with a fingertip.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(onClick = onToggle)
                    .semantics { contentDescription = if (todo.done) "Fjern flueben" else "Sæt flueben" },
                contentAlignment = Alignment.Center,
            ) {
                CheckboxGlyph(checked = todo.done)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { editing = true },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = todo.label,
                    color = if (todo.done) Muted else Ink,
                    fontSize = 16.sp,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The ghost add row: a loose unchecked box + faint placeholder that opens an inline field on tap.
 * A keyboard commit reopens the field empty, so a list can be typed in one go (write, Enter, write,
 * Enter) — on the desktop window that is the whole point, and the soft keyboard stays up on touch.
 */
@Composable
private fun AddTodoRow(onAdd: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    // Bumped per keyboard commit purely to remount the field with an empty value.
    var round by remember { mutableIntStateOf(0) }
    if (adding) {
        key(round) {
            TodoInlineEdit(
                initial = "",
                checked = false,
                // Empty commit discards without touching the backend; a non-empty one adds. Only a
                // keyboard commit means "and now the next one" — closing the row on focus loss keeps
                // a tap elsewhere from pulling focus straight back into it.
                onCommit = { text, fromKeyboard ->
                    if (text.isNotBlank()) onAdd(text)
                    if (fromKeyboard && text.isNotBlank()) round++ else adding = false
                },
                onCancel = { adding = false },
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimensions.minTouch)
                .clickable { adding = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CheckboxGlyph(checked = false)
            Text("Tilføj opgave", color = Muted, fontSize = 16.sp)
        }
    }
}

/**
 * Inline single-line editor shared by add + edit. Auto-focuses, commits on Enter / IME-Done or on
 * focus loss, abandons on Escape, and guards against a double commit (Done removes the field, which
 * then fires focus-loss too). [onCommit]'s second argument marks a keyboard commit — what lets the
 * add row stay open for the next item without a stray tap elsewhere doing the same.
 */
@Composable
private fun TodoInlineEdit(
    initial: String,
    checked: Boolean,
    onCommit: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var committed by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    fun commit(fromKeyboard: Boolean) {
        if (!committed) { committed = true; onCommit(value.text, fromKeyboard) }
    }
    fun cancel() { if (!committed) { committed = true; onCancel() } }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth().height(Dimensions.minTouch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CheckboxGlyph(checked = checked)
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp),
            cursorBrush = SolidColor(Forest),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(fromKeyboard = true) }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                // Previewed, so a hardware Enter commits the row instead of being eaten as input.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> { commit(fromKeyboard = true); true }
                        Key.Escape -> { cancel(); true }
                        else -> false
                    }
                }
                .onFocusChanged { state ->
                    if (state.isFocused) hadFocus = true else if (hadFocus) commit(fromKeyboard = false)
                },
        )
    }
}

@Composable
internal fun CheckboxGlyph(checked: Boolean, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(if (checked) Res.drawable.checkbox_filled else Res.drawable.checkbox_blank),
        contentDescription = null,
        tint = if (checked) Forest else Muted,
        modifier = modifier.size(24.dp),
    )
}

/** Which of the Media panel's three surfaces is showing. See [MediaPanel] for the precedence. */
private enum class MediaSurface { NowPlaying, Artist, Browse }

/**
 * The Media panel content: the now-playing surface when the active audio room has a track and the
 * player is expanded, the artist drill-in while one is open, else the browse surface. Collapsing
 * ([minimized]) shows browse *while* audio plays; the floating [MiniPlayerBar] that replaces the
 * surface — and the [MinimizeHandle] that triggers the collapse — are drawn by [RightCard] above
 * this panel's scroll, so all three surfaces reserve room for them at their bottom.
 *
 * An open [artist] outranks now-playing: it is only ever opened by a deliberate tap, and playing
 * anything from it closes it again (the ViewModel does that), so the panel returns to the music.
 */
@Composable
private fun MediaPanel(
    audioState: AudioState,
    minimized: Boolean,
    searchQuery: String,
    search: SearchState,
    pendingPlay: PendingPlay?,
    pendingQueueItemId: String?,
    queueRefreshing: Boolean,
    artist: ArtistUiState?,
    musicSource: MusicSource,
    playlists: List<BrowseItem>,
    quickPicks: List<BrowseItem>,
    mixedForYou: List<BrowseItem>,
    spotifyPlaylists: List<BrowseItem>,
    spotifyRecentlyPlayed: List<BrowseItem>,
    onQueryChange: (String) -> Unit,
    onPlay: (BrowseItem) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    // A pending play paints the tapped item as the (loading) now-playing track right away — the real
    // track takes several seconds to arrive, and this surface is the feedback for the tap.
    val pendingTrack = pendingPlay?.let {
        MediaTrack(
            title = it.title,
            artist = it.subtitle.orEmpty(),
            album = null,
            artworkUrl = it.artworkUrl,
            durationSec = 0,
        )
    }
    val track = pendingTrack ?: rememberLatchedTrack(audioState.nowPlaying)
    val hasTrack = pendingPlay != null || audioState.nowPlaying != null
    val surface = when {
        artist != null -> MediaSurface.Artist
        hasTrack && !minimized -> MediaSurface.NowPlaying
        else -> MediaSurface.Browse
    }
    val bottomInset =
        if (audioState.nowPlaying != null) Dimensions.miniPlayerHeight + Dimensions.mediaSectionGap else 0.dp

    AnimatedContent(
        targetState = surface,
        modifier = modifier.fillMaxSize(),
        // `using null` drops the SizeTransform: every surface fills the panel, so there is no container
        // height to animate — and animating one would only fight the scroll each surface owns.
        transitionSpec = {
            (fadeIn(tween(200)) + slideInVertically { h -> h / 8 }) togetherWith
                (fadeOut(tween(120)) + slideOutVertically { h -> h / 8 }) using null
        },
        label = "media-surface",
    ) { target ->
        when {
            target == MediaSurface.NowPlaying && track != null -> NowPlayingSurface(
                track = track,
                audioState = audioState,
                loading = pendingPlay != null,
                queueRefreshing = queueRefreshing,
                pendingQueueItemId = pendingQueueItemId,
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
            // The state is read inside the transition, so it can be null on the frame the surface
            // animates out — the browse surface stands in for that frame.
            target == MediaSurface.Artist && artist != null -> ArtistSurface(
                artist = artist,
                onBack = onCloseArtist,
                onPlayTopHit = onPlayTopHit,
                onShuffle = onShuffleArtist,
                onPlay = onPlay,
                bottomInset = bottomInset,
                modifier = Modifier.fillMaxSize(),
            )
            else -> BrowseSurface(
                query = searchQuery,
                search = search,
                source = musicSource,
                playlists = playlists,
                quickPicks = quickPicks,
                mixedForYou = mixedForYou,
                spotifyPlaylists = spotifyPlaylists,
                spotifyRecentlyPlayed = spotifyRecentlyPlayed,
                onQueryChange = onQueryChange,
                onPlay = onPlay,
                onOpenArtist = onOpenArtist,
                bottomInset = bottomInset,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Full-width sunken search field over the music library. The text is owned by the ViewModel (which
 * debounces the search behind it), so this only mirrors it into a local [TextFieldValue] for the
 * cursor — the sync effect fires solely on the programmatic clear (playing a result), never while
 * typing. The row is [Dimensions.searchFieldRowHeight] tall so the trailing clear button is a full
 * touch target without growing the pill.
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue(query)) }
    LaunchedEffect(query) {
        if (query != field.text) field = TextFieldValue(query, TextRange(query.length))
    }

    InsetSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(percent = 50),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = Dimensions.searchFieldPadV),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Dimensions.searchFieldRowHeight),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.search_outline),
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(20.dp),
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (field.text.isEmpty()) {
                    Text("Søg sange, kunstnere, podcasts", color = Muted, fontSize = 16.sp)
                }
                BasicTextField(
                    value = field,
                    onValueChange = { value -> field = value; onQueryChange(value.text) },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(Forest),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(Dimensions.searchFieldRowHeight)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .semantics { contentDescription = "Ryd søgning" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.close_filled),
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Card/glyph color for browse + queue items, cycled by list index (matches the reference rails). */
internal fun browseCardColor(index: Int): Color = when (index % 3) {
    0 -> Forest
    1 -> Teal
    else -> Rose
}

/**
 * A rounded tile — the shared visual for album art, browse & queue thumbs. The colored-glyph tile is
 * the base; when [artworkUrl] is set, real cover art is layered on top (cropped to fill) and the glyph
 * shows through only while loading or on failure, so a missing/broken image degrades gracefully. A
 * [label] prints over the art on a bottom scrim, for tiles that carry no caption of their own.
 */
@Composable
internal fun ArtTile(
    background: Color,
    glyph: DrawableResource,
    glyphSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    glyphTint: Color = OnForest,
    artworkUrl: String? = null,
    label: String? = null,
) {
    val shape = RoundedCornerShape(Dimensions.innerBlockRadius)
    Box(
        modifier = modifier.clip(shape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = null,
            tint = glyphTint,
            modifier = Modifier.size(glyphSize),
        )
        if (artworkUrl != null) {
            // Decode to a small multiple of the tile instead of the source's full resolution: handing
            // the GPU a 720–1280px bitmap for a ~100px tile collapses it in one step and grinds any
            // text in the cover into grain. Coil's decoder does the bulk of the downscale, leaving a
            // gentle final step.
            BoxWithConstraints(Modifier.matchParentSize()) {
                val bounded = constraints.hasBoundedWidth || constraints.hasBoundedHeight
                val tilePx = maxOf(
                    if (constraints.hasBoundedWidth) constraints.maxWidth else 0,
                    if (constraints.hasBoundedHeight) constraints.maxHeight else 0,
                )
                val requestSize =
                    if (bounded && tilePx > 0) {
                        val target = tilePx * Dimensions.artOversample
                        coil3.size.Size(target, target)
                    } else {
                        coil3.size.Size.ORIGINAL
                    }
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(artworkUrl)
                        .size(requestSize)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // High-quality (mipmapped) resampling — the default FilterQuality.Low aliases the
                    // cover down into the tile as uniform grain.
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize().clip(shape),
                )
            }
        }
        if (label != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(Dimensions.artLabelHeight)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, ArtScrim))),
            ) {
                Text(
                    text = label,
                    color = OnArt,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(Dimensions.artLabelPadding),
                )
            }
        }
    }
}

/**
 * The playing state: album art + title/subtitle/scrubber, transport, and the up-next queue. Browsing
 * (playlists included) belongs to the other surface — this one is about the track that is on. The
 * collapse caret is not part of it either — [RightCard] floats that over this surface.
 *
 * While [loading] (a tapped item whose stream Music Assistant is still resolving) the art carries a
 * spinner and every control is inert — the surface is pure feedback until the real track arrives.
 *
 * The art/scrubber/transport block is pinned; only [UpNextSection] scrolls, so reaching down the queue
 * never pushes the controls out of the card.
 */
@Composable
private fun NowPlayingSurface(
    track: MediaTrack,
    audioState: AudioState,
    loading: Boolean,
    queueRefreshing: Boolean,
    pendingQueueItemId: String?,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayQueueItem: (String) -> Unit,
    onMoveQueueItem: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(Dimensions.albumArtSize)) {
                ArtTile(
                    background = Forest,
                    glyph = Res.drawable.equalizer_filled,
                    glyphSize = 40.dp,
                    modifier = Modifier.fillMaxSize(),
                    artworkUrl = track.artworkUrl,
                )
                if (loading) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(Dimensions.innerBlockRadius))
                            .background(ArtScrim.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = OnArt)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = track.title,
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.album?.let { "${track.artist} · $it" } ?: track.artist,
                    color = InkSoft,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Scrubber(
                    positionSec = if (loading) 0 else rememberLivePositionSec(audioState, track),
                    durationSec = track.durationSec,
                    enabled = !loading,
                    onSeek = onSeek,
                )
            }
        }
        Spacer(Modifier.height(Dimensions.mediaSectionGap))
        TransportRow(
            isPlaying = audioState.isPlaying,
            isShuffle = audioState.isShuffle,
            repeat = audioState.repeat,
            enabled = !loading,
            onTogglePlay = onTogglePlay,
            onNext = onNext,
            onPrevious = onPrevious,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
        )
        when {
            // The rows on hand belong to the previous track while a play is (re)building the queue —
            // hold a loader instead of showing them (or the freshly played track itself) as "up next".
            loading || queueRefreshing -> {
                Spacer(Modifier.height(Dimensions.mediaSectionGap))
                UpNextLoader(modifier = Modifier.weight(1f))
            }
            audioState.queue.isNotEmpty() -> {
                Spacer(Modifier.height(Dimensions.mediaSectionGap))
                UpNextSection(
                    queue = audioState.queue,
                    enabled = !loading,
                    pendingQueueItemId = pendingQueueItemId,
                    onPlayQueueItem = onPlayQueueItem,
                    onMoveQueueItem = onMoveQueueItem,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Stand-in for [UpNextSection] while the queue behind it is being replaced by a play in flight. */
@Composable
private fun UpNextLoader(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel("Up next")
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Muted)
        }
    }
}

/**
 * The scrubber's displayed position, ticked forward locally once a second while playing. The device
 * state's [AudioState.positionSec] is already projected to "now" when it is built, but it only
 * *emits* on state changes — without this ticker the knob sits still between them. Any new emission
 * (position, play/pause, track change) resets the tick base to the fresher value.
 */
@Composable
private fun rememberLivePositionSec(audioState: AudioState, track: MediaTrack): Int {
    val base = audioState.positionSec
    var live by remember(base, audioState.isPlaying, track.title) { mutableStateOf(base) }
    LaunchedEffect(base, audioState.isPlaying, track.title) {
        while (audioState.isPlaying) {
            delay(1_000)
            live = (live + 1).coerceAtMost(if (track.durationSec > 0) track.durationSec else Int.MAX_VALUE)
        }
    }
    return live
}

/**
 * Drag-to-seek scrubber. The x→fraction math is the unit-tested [volumeFractionFromX]; this only
 * draws the Rose track and forwards seeks. Slider a11y mirrors the center card's VolumeSlider.
 *
 * A drag tracks **locally** and commits a single seek on release (a tap commits at once): every
 * `media_seek` is a real service call the speaker has to act on, and per-move commits measurably
 * flood it (~25 calls in one short drag) and stall other playback commands for seconds after.
 * A committed seek then stays **latched** as the shown position until the device reports a position
 * near it — the device echoes the pre-seek position for a beat, and falling back to that would snap
 * the knob to the old spot before jumping forward again.
 */
@Composable
private fun Scrubber(
    positionSec: Int,
    durationSec: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentDuration by rememberUpdatedState(durationSec)
    // Non-null while a finger is on the track; wins over the ticking device position until release.
    var dragPositionSec by remember { mutableStateOf<Int?>(null) }
    // The last committed seek. The device keeps reporting the pre-seek position until the seek
    // round-trips, so a bare release would snap the knob straight back — the latch holds the target
    // until the device position lands near it (or a timeout concedes the seek was lost).
    var pendingSeekSec by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(positionSec, pendingSeekSec) {
        val target = pendingSeekSec ?: return@LaunchedEffect
        if (abs(positionSec - target) <= SEEK_SYNC_TOLERANCE_SEC) pendingSeekSec = null
    }
    LaunchedEffect(pendingSeekSec) {
        if (pendingSeekSec == null) return@LaunchedEffect
        delay(SEEK_SYNC_TIMEOUT_MS)
        pendingSeekSec = null
    }
    // A new track (or the loading reset) invalidates a latch aimed at the old one.
    LaunchedEffect(enabled, durationSec) { pendingSeekSec = null }
    fun commitSeek(target: Int) {
        pendingSeekSec = target
        currentOnSeek(target)
    }

    val shownPositionSec = dragPositionSec ?: pendingSeekSec ?: positionSec
    val fraction = if (durationSec > 0) (shownPositionSec.toFloat() / durationSec).coerceIn(0f, 1f) else 0f

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(formatTrackTime(shownPositionSec), color = Muted, fontSize = 13.sp)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimensions.scrubberKnobDiameter)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val inset = Dimensions.scrubberKnobDiameter.toPx() / 2f
                    detectTapGestures { pos ->
                        val f = volumeFractionFromX(pos.x, inset, size.width - inset * 2f)
                        commitSeek((f * currentDuration).roundToInt())
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val inset = Dimensions.scrubberKnobDiameter.toPx() / 2f
                    detectDragGestures(
                        onDragEnd = {
                            dragPositionSec?.let { commitSeek(it) }
                            dragPositionSec = null
                        },
                        onDragCancel = { dragPositionSec = null },
                    ) { change, _ ->
                        change.consume()
                        val f = volumeFractionFromX(change.position.x, inset, size.width - inset * 2f)
                        dragPositionSec = (f * currentDuration).roundToInt()
                    }
                }
                .focusable()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Søgning i nummeret"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = positionSec.toFloat(),
                        range = 0f..durationSec.coerceAtLeast(1).toFloat(),
                    )
                    setProgress { target ->
                        commitSeek(target.roundToInt().coerceIn(0, durationSec))
                        true
                    }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionRight -> {
                            commitSeek((shownPositionSec + 5).coerceAtMost(durationSec)); true
                        }
                        Key.DirectionDown, Key.DirectionLeft -> {
                            commitSeek((shownPositionSec - 5).coerceAtLeast(0)); true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(Dimensions.scrubberKnobDiameter)) {
                val trackH = Dimensions.scrubberTrackHeight.toPx()
                val knobRadius = Dimensions.scrubberKnobDiameter.toPx() / 2f
                val cyy = size.height / 2f
                val laneLeft = knobRadius
                val laneWidth = (size.width - knobRadius * 2f).coerceAtLeast(0f)
                val knobX = laneLeft + laneWidth * fraction
                val corner = CornerRadius(trackH / 2f, trackH / 2f)

                drawRoundRect(
                    color = InsetFill,
                    topLeft = Offset(laneLeft, cyy - trackH / 2f),
                    size = Size(laneWidth, trackH),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = Rose,
                    topLeft = Offset(laneLeft, cyy - trackH / 2f),
                    size = Size(laneWidth * fraction, trackH),
                    cornerRadius = corner,
                )
                drawCircle(color = Color.White, radius = knobRadius, center = Offset(knobX, cyy))
                drawCircle(
                    color = Rose,
                    radius = knobRadius,
                    center = Offset(knobX, cyy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        Text(formatTrackTime(durationSec), color = Muted, fontSize = 13.sp)
    }
}

/** How close the device position must come to a latched seek before the latch hands back to it. */
private const val SEEK_SYNC_TOLERANCE_SEC = 3

/** How long an unacknowledged seek latch holds before conceding and showing the device position. */
private const val SEEK_SYNC_TIMEOUT_MS = 8_000L

/** Centered transport controls. Shuffle/repeat tint Forest when active; the play/pause is a Forest disc. */
@Composable
private fun TransportRow(
    isPlaying: Boolean,
    isShuffle: Boolean,
    repeat: RepeatMode,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportIcon(
            glyph = Res.drawable.shuffle_filled,
            description = if (isShuffle) "Bland fra" else "Bland til",
            tint = if (isShuffle) Forest else InkSoft,
            onClick = onToggleShuffle,
            enabled = enabled,
        )
        TransportIcon(Res.drawable.skip_previous_filled, "Forrige", InkSoft, onPrevious, enabled)
        PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlay, enabled = enabled)
        TransportIcon(Res.drawable.skip_next_filled, "Næste", InkSoft, onNext, enabled)
        TransportIcon(
            glyph = Res.drawable.repeat_filled,
            description = "Gentag",
            tint = if (repeat != RepeatMode.Off) Forest else InkSoft,
            onClick = onCycleRepeat,
            enabled = enabled,
        )
    }
}

@Composable
private fun TransportIcon(
    glyph: DrawableResource,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(Dimensions.minTouch)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimensions.transportIconSize),
        )
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .size(Dimensions.transportButtonSize)
            .shadow(Dimensions.pillElevation, CircleShape)
            .clip(CircleShape)
            .background(Forest)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = if (isPlaying) "Pause" else "Afspil" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isPlaying) Res.drawable.pause_filled else Res.drawable.play_filled),
            contentDescription = null,
            tint = OnForest,
            modifier = Modifier.size(Dimensions.playPauseIconSize),
        )
    }
}

/**
 * Caret that collapses the now-playing surface into the [MiniPlayerBar]. [RightCard] pins it to the
 * card's bottom-right — the same end padding as the bar's expand caret — so it lands where the
 * expand caret will be, and stays reachable however far the queue scrolls. It carries a card-filled
 * disc because it floats over that scrolling content.
 */
@Composable
private fun MinimizeHandle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dimensions.minTouch)
            .shadow(Dimensions.pillElevation, CircleShape)
            .clip(CircleShape)
            .background(Forest)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Minimér afspilleren" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.drop_down_filled),
            contentDescription = null,
            tint = OnForest,
            modifier = Modifier.size(Dimensions.minimizeCaretSize),
        )
    }
}

/**
 * The collapsed player: a Forest bar floating over the browse surface with art, track, transport
 * and an expand caret. Inverted against the cream card it overlays so it reads as hovering, and it
 * carries only the controls worth reaching without expanding — no scrubber, shuffle or repeat.
 */
@Composable
private fun MiniPlayerBar(
    track: MediaTrack,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Dimensions.miniPlayerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimensions.miniPlayerHeight)
            .shadow(Dimensions.miniPlayerElevation, shape)
            .clip(shape)
            .background(Forest)
            // Tapping the bar itself expands; the transport children below claim their own taps.
            .clickable(onClick = onExpand)
            .semantics { contentDescription = "Åbn afspilleren" }
            .padding(horizontal = Dimensions.miniPlayerBarPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtTile(
            background = Forest,
            glyph = Res.drawable.music_note_filled,
            glyphSize = 20.dp,
            modifier = Modifier.size(Dimensions.miniPlayerThumbSize),
            artworkUrl = track.artworkUrl,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = OnForest,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                color = OnForest.copy(alpha = 0.7f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MiniTransportIcon(Res.drawable.skip_previous_filled, "Forrige", onPrevious)
        MiniPlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlay)
        MiniTransportIcon(Res.drawable.skip_next_filled, "Næste", onNext)
        MiniTransportIcon(Res.drawable.drop_up_filled, "Åbn afspilleren", onExpand)
    }
}

/** A [TransportIcon] tinted for the Forest bar. */
@Composable
private fun MiniTransportIcon(glyph: DrawableResource, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Dimensions.minTouch)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = null,
            tint = OnForest,
            modifier = Modifier.size(Dimensions.miniPlayerIconSize),
        )
    }
}

/** The bar's play/pause: the accent disc inverted (cream fill, Forest glyph) to stay the anchor. */
@Composable
private fun MiniPlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Dimensions.miniPlayerPlaySize)
            .clip(CircleShape)
            .background(OnForest)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (isPlaying) "Pause" else "Afspil" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isPlaying) Res.drawable.pause_filled else Res.drawable.play_filled),
            contentDescription = null,
            tint = Forest,
            modifier = Modifier.size(Dimensions.miniPlayerIconSize),
        )
    }
}

/** Horizontal snapping rail of playlist/browse cards. Shared by Playlists and Mixed for you. */
@Composable
private fun PlaylistRail(items: List<BrowseItem>, onPlay: (BrowseItem) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.browseGridSpacing),
    ) {
        itemsIndexed(items) { index, item -> PlaylistCard(index = index, playlist = item, onPlay = onPlay) }
    }
}

@Composable
private fun PlaylistCard(index: Int, playlist: BrowseItem, onPlay: (BrowseItem) -> Unit, modifier: Modifier = Modifier) {
    // Tapping plays the item as radio; a card without a uri (rare) stays inert.
    val playable = if (playlist.uri != null) Modifier.clickable { onPlay(playlist) } else Modifier
    Column(
        modifier
            .width(Dimensions.playlistCardWidth)
            .then(playable)
            .semantics { if (playlist.uri != null) contentDescription = "Afspil ${playlist.name}" },
    ) {
        ArtTile(
            background = browseCardColor(index),
            glyph = Res.drawable.equalizer_filled,
            glyphSize = 36.dp,
            glyphTint = OnForest.copy(alpha = 0.9f),
            modifier = Modifier.fillMaxWidth().height(Dimensions.playlistCardHeight),
            artworkUrl = playlist.artworkUrl,
        )
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        playlist.subtitle?.let {
            Text(it, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One browse shelf: its label, its tiles, and the shape it renders in. */
private sealed interface BrowseShelf {
    val label: String
    val items: List<BrowseItem>

    /** A horizontally-paged 3×3 grid — for a pool deep enough to fill pages. */
    data class PagedGrid(override val label: String, override val items: List<BrowseItem>) : BrowseShelf

    /** A grid sized to its content, for a short list a paged one would leave mostly empty. */
    data class Grid(override val label: String, override val items: List<BrowseItem>) : BrowseShelf

    data class Rail(override val label: String, override val items: List<BrowseItem>) : BrowseShelf
}

/**
 * The shelves for [source], in order. YouTube Music leads with its algorithmic grid; Spotify serves
 * no recommendation feed of its own, so it leads with playlists — as a grid, since they are the
 * headline shelf on that side rather than a secondary rail — and keeps the YT Music grid pinned at
 * the bottom rather than ending on nothing. Empty shelves are dropped by the caller.
 */
private fun browseShelvesFor(
    source: MusicSource,
    playlists: List<BrowseItem>,
    quickPicks: List<BrowseItem>,
    mixedForYou: List<BrowseItem>,
    spotifyPlaylists: List<BrowseItem>,
    spotifyRecentlyPlayed: List<BrowseItem>,
): List<BrowseShelf> = when (source) {
    MusicSource.YtMusic -> listOf(
        BrowseShelf.PagedGrid("Quick picks", quickPicks),
        BrowseShelf.Rail("Playlists", playlists),
        BrowseShelf.Rail("Mixed for you", mixedForYou),
    )
    MusicSource.Spotify -> listOf(
        BrowseShelf.PagedGrid("Playlists", spotifyPlaylists),
        BrowseShelf.Grid("Recently played", spotifyRecentlyPlayed),
        BrowseShelf.PagedGrid("Quick picks", quickPicks),
    )
}

/**
 * The browse state: the search field over either the [source]'s shelves ([browseShelvesFor]) or, once
 * [search] leaves [SearchState.Idle], the results grid in their place — every way into the library
 * lives here, since this is the surface reached to pick something. Search is deliberately **not**
 * scoped by [source]: the toggle splits browsing, while a search still spans both providers.
 * No transport: it shows either when nothing is playing or with the player collapsed, and in the
 * latter case [bottomInset] reserves the height the floating [MiniPlayerBar] covers.
 */
@Composable
private fun BrowseSurface(
    query: String,
    search: SearchState,
    source: MusicSource,
    playlists: List<BrowseItem>,
    quickPicks: List<BrowseItem>,
    mixedForYou: List<BrowseItem>,
    spotifyPlaylists: List<BrowseItem>,
    spotifyRecentlyPlayed: List<BrowseItem>,
    onQueryChange: (String) -> Unit,
    onPlay: (BrowseItem) -> Unit,
    onOpenArtist: (BrowseItem) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val scroll = rememberScrollState()
    Column(modifier.fillMaxSize().verticalScrollFade(scroll).verticalScroll(scroll)) {
        SearchBar(query = query, onQueryChange = onQueryChange)
        Spacer(Modifier.height(Dimensions.mediaSectionGap))
        when (search) {
            SearchState.Idle -> {
                // Like the search grid: an artist tile drills in, everything else plays.
                val onSelect: (Int, BrowseItem) -> Unit = { _, item ->
                    if (item.kind == BrowseKind.Artist) onOpenArtist(item) else onPlay(item)
                }
                val shelves = browseShelvesFor(
                    source, playlists, quickPicks, mixedForYou, spotifyPlaylists, spotifyRecentlyPlayed,
                ).filter { it.items.isNotEmpty() }
                shelves.forEachIndexed { index, shelf ->
                    // The search bar already left a gap, so only the shelves after the first add one.
                    if (index > 0) Spacer(Modifier.height(Dimensions.mediaSectionGap))
                    SectionLabel(shelf.label)
                    Spacer(Modifier.height(12.dp))
                    when (shelf) {
                        is BrowseShelf.PagedGrid -> QuickPicksPager(items = shelf.items, onSelect = onSelect)
                        is BrowseShelf.Grid -> BrowseGrid(
                            items = shelf.items,
                            rows = ceil(shelf.items.size / BROWSE_GRID_COLUMNS.toFloat()).toInt(),
                            startIndex = 0,
                            gap = Dimensions.browseGridSpacing,
                            onSelect = onSelect,
                        )
                        is BrowseShelf.Rail -> PlaylistRail(items = shelf.items, onPlay = onPlay)
                    }
                }
            }
            SearchState.Searching -> SearchStatus {
                CircularProgressIndicator(color = Forest)
            }
            SearchState.Failed -> SearchStatus {
                Text("Søgningen fejlede", color = Muted, fontSize = 15.sp)
            }
            is SearchState.Results ->
                if (search.items.isEmpty()) {
                    SearchStatus { Text("Ingen resultater", color = Muted, fontSize = 15.sp) }
                } else {
                    // Results mix kinds: an artist hit drills in, everything else plays.
                    BrowseGrid(
                        items = search.items,
                        rows = ceil(search.items.size / BROWSE_GRID_COLUMNS.toFloat()).toInt(),
                        startIndex = 0,
                        gap = Dimensions.browseGridSpacing,
                        onSelect = { _, item ->
                            if (item.kind == BrowseKind.Artist) onOpenArtist(item) else onPlay(item)
                        },
                    )
                }
        }
        Spacer(Modifier.height(bottomInset))
    }
}

/**
 * The artist drill-in, reached by tapping an artist search result: a back arrow, the artist header
 * with its shuffle pill, then their top hits (a paged grid, like Quick Picks) and albums (a rail,
 * like Playlists). Tapping a hit plays the list **from there** — hence [onPlayTopHit] taking the
 * tapped index rather than the item — while an album is a plain play target.
 *
 * The header renders from the tile that opened the surface, so it is up before the catalogue is; the
 * two sections are what [artist] is still loading (or failed to fetch). Either list may come back
 * empty, in which case its section is simply omitted.
 */
@Composable
private fun ArtistSurface(
    artist: ArtistUiState,
    onBack: () -> Unit,
    onPlayTopHit: (Int) -> Unit,
    onShuffle: () -> Unit,
    onPlay: (BrowseItem) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val scroll = rememberScrollState()
    Column(modifier.fillMaxSize().verticalScrollFade(scroll).verticalScroll(scroll)) {
        Box(
            modifier = Modifier
                // The glyph sits centred in its touch target; bleed the whole box out by that inset
                // so the arrow lines up with the content edge instead of 10dp inside it.
                .offset(x = -Dimensions.backButtonInset)
                .size(Dimensions.backButtonSize)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "Tilbage" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.arrow_back_filled),
                contentDescription = null,
                tint = InkSoft,
                modifier = Modifier.size(Dimensions.backIconSize),
            )
        }
        Spacer(Modifier.height(12.dp))
        // The Row takes the art's height so the text column has one to distribute: name pinned to the
        // top of the portrait, shuffle pill to its bottom.
        Row(
            Modifier.fillMaxWidth().height(Dimensions.artistArtSize),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ArtTile(
                background = Forest,
                glyph = Res.drawable.music_note_filled,
                glyphSize = 40.dp,
                glyphTint = OnForest.copy(alpha = 0.9f),
                modifier = Modifier.size(Dimensions.artistArtSize),
                artworkUrl = artist.artist.artworkUrl,
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    artist.artist.name,
                    color = Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                // An action, not a selection — it never latches, so it stays idle-styled and takes the
                // accent on its glyph instead.
                PillChip(
                    text = "Bland",
                    selected = false,
                    onClick = onShuffle,
                    leadingIcon = Res.drawable.shuffle_filled,
                    contentColor = Forest,
                    modifier = Modifier.wrapContentWidth(),
                )
            }
        }
        Spacer(Modifier.height(Dimensions.mediaSectionGap))
        when (artist) {
            is ArtistUiState.Loading -> SearchStatus { CircularProgressIndicator(color = Forest) }
            is ArtistUiState.Failed -> SearchStatus {
                Text("Kunne ikke hente kunstneren", color = Muted, fontSize = 15.sp)
            }
            is ArtistUiState.Ready -> {
                if (artist.topTracks.isNotEmpty()) {
                    SectionLabel("Top hits")
                    Spacer(Modifier.height(12.dp))
                    QuickPicksPager(
                        items = artist.topTracks,
                        onSelect = { index, _ -> onPlayTopHit(index) },
                    )
                }
                if (artist.albums.isNotEmpty()) {
                    Spacer(Modifier.height(Dimensions.mediaSectionGap))
                    SectionLabel("Albums")
                    Spacer(Modifier.height(12.dp))
                    PlaylistRail(items = artist.albums, onPlay = onPlay)
                }
            }
        }
        Spacer(Modifier.height(bottomInset))
    }
}

/** Spinner / "no hits" / failure line, on a reserved height so the surface doesn't jump between them. */
@Composable
private fun SearchStatus(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(Dimensions.searchStatusHeight),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

private const val QUICK_PICKS_PER_PAGE = 9
private const val BROWSE_GRID_COLUMNS = 3

/**
 * Quick Picks as a horizontally-paged 3×3 grid with a dot indicator. Built from plain Row/Columns
 * (no LazyVerticalGrid inside the vertically-scrolling panel); the pager is height-bounded off the
 * measured square-card size so it lays out inside the scroll.
 */
@Composable
private fun QuickPicksPager(
    items: List<BrowseItem>,
    onSelect: (index: Int, item: BrowseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = ceil(items.size / QUICK_PICKS_PER_PAGE.toFloat()).toInt().coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val gap = Dimensions.browseGridSpacing

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardSize = (maxWidth - gap * (BROWSE_GRID_COLUMNS - 1)) / BROWSE_GRID_COLUMNS
            val gridHeight = cardSize * 3 + gap * 2
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.height(gridHeight),
                pageSpacing = gap,
            ) { page ->
                BrowseGrid(
                    items = items.drop(page * QUICK_PICKS_PER_PAGE).take(QUICK_PICKS_PER_PAGE),
                    rows = QUICK_PICKS_PER_PAGE / BROWSE_GRID_COLUMNS,
                    startIndex = page * QUICK_PICKS_PER_PAGE,
                    gap = gap,
                    onSelect = onSelect,
                )
            }
        }
        if (pageCount > 1) {
            Spacer(Modifier.height(12.dp))
            PageIndicator(
                state = pagerState,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * The shared tile grid: [rows] rows of [BROWSE_GRID_COLUMNS] square [ArtTile]s, each captioned by the
 * title printed over its art. Drawn by a Quick Picks page (a fixed 3 rows), the search results (as
 * many rows as hits) and the artist surface's top hits. [startIndex] offsets the color cycle so a
 * later Quick Picks page continues the previous page's colors instead of restarting them.
 *
 * [onSelect] receives the tile's **global** index (already offset by [startIndex]) alongside the item,
 * because what a tap means differs per call site: playing the item, opening an artist, or playing the
 * whole list from that position.
 */
@Composable
private fun BrowseGrid(
    items: List<BrowseItem>,
    rows: Int,
    startIndex: Int,
    gap: Dp,
    onSelect: (index: Int, item: BrowseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (col in 0 until BROWSE_GRID_COLUMNS) {
                    val i = row * BROWSE_GRID_COLUMNS + col
                    val item = items.getOrNull(i)
                    if (item != null) {
                        val isArtist = item.kind == BrowseKind.Artist
                        val clickable =
                            if (item.uri != null) Modifier.clickable { onSelect(startIndex + i, item) }
                            else Modifier
                        ArtTile(
                            background = browseCardColor(startIndex + i),
                            glyph = Res.drawable.music_note_filled,
                            glyphSize = 34.dp,
                            glyphTint = OnForest.copy(alpha = 0.9f),
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .then(clickable)
                                .semantics {
                                    if (item.uri != null) {
                                        contentDescription =
                                            if (isArtist) "Vis ${item.name}" else "Afspil ${item.name}"
                                    }
                                },
                            artworkUrl = item.artworkUrl,
                            // The tiles carry no caption below them, so the title rides the art itself —
                            // it works on the colored-glyph fallback too, so an artless pick is still named.
                            label = item.name,
                        )
                    } else {
                        // Keep column alignment when the last row is partial.
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

