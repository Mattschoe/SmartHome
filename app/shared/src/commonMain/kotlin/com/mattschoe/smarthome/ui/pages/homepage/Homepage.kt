package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mattschoe.smarthome.data.cycle
import com.mattschoe.smarthome.ui.layout.DashboardLayout
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.OnForest
import com.mattschoe.smarthome.ui.theme.SageSurface
import kotlinx.coroutines.delay

@Composable
fun Homepage(
    navController: NavController,
    viewModel: HomepageViewModel,
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    when (val st = state) {
        HomeScreenState.Loading -> SageBackground()
        is HomeScreenState.Ready -> DashboardRoot(st, viewModel)
    }
}

/** Sage-filled background used for the loading state and as every dashboard branch's base surface. */
@Composable
private fun SageBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(SageSurface))
}

/**
 * The layout seam: measure the available window, map it to a [DashboardLayout], and branch. The tablet
 * dashboard is [ExpandedDashboard]; the phone re-flow lives in [CompactDashboard], which sub-branches
 * on orientation itself.
 */
@Composable
private fun DashboardRoot(state: HomeScreenState.Ready, viewModel: HomepageViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        when (DashboardLayout.from(maxWidth, maxHeight)) {
            DashboardLayout.Expanded -> ExpandedDashboard(state, viewModel)
            DashboardLayout.Compact -> CompactDashboard(state, viewModel)
        }
    }
}

/**
 * The tablet dashboard: a full-bleed sage surface with the three cream cards in a fixed row —
 * LEFT 288dp fixed, CENTER flex 1 (min 346dp), RIGHT flex 1.12 (min 392dp). The left card is built;
 * center/right are placeholders filled by Phases 5–7. Geometry lives in [Dimensions].
 */
@Composable
private fun ExpandedDashboard(ready: HomeScreenState.Ready, viewModel: HomepageViewModel) {
    // Capture the specific stable values the callbacks need instead of closing over `ready`. A new
    // `Ready` is emitted on every state change (e.g. flipping the right-card panel), so lambdas that
    // capture `ready` change identity each time and force the card they're passed to to recompose.
    // Capturing the Room/AudioState directly keeps the callbacks stable, so a panel switch no longer
    // recomposes the center card.
    val lightRoom = ready.activeLightRoom
    val audioRoom = ready.activeAudioRoom
    val audioState = ready.audioState
    Box(Modifier.fillMaxSize().background(SageSurface)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimensions.surfacePadH, vertical = Dimensions.surfacePadV),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.cardGap),
        ) {
            LeftCard(
                climate = ready.climate,
                modifier = Modifier.width(Dimensions.leftCardWidth),
            )
            CenterCard(
                activeLightRoom = lightRoom,
                lightRoomState = ready.lightRoomState,
                activeAudioRoom = audioRoom,
                audioState = audioState,
                joinTarget = ready.joinTarget,
                audioJoined = ready.audioJoined,
                onSelectLightRoom = viewModel::selectLightRoom,
                onSelectAudioRoom = viewModel::selectAudioRoom,
                onBrightnessChange = { value -> viewModel.setBrightness(lightRoom, value) },
                onWarmthChange = { warmth -> viewModel.setWarmth(lightRoom, warmth) },
                onToggleLight = { viewModel.toggleLight(lightRoom) },
                onVolumeChange = { value -> viewModel.setVolume(audioRoom, value) },
                onToggleAudioJoin = viewModel::toggleAudioJoin,
                modifier = Modifier.weight(1f).widthIn(min = 346.dp),
            )
            RightCard(
                panel = ready.panel,
                mediaMinimized = ready.mediaMinimized,
                searchQuery = ready.searchQuery,
                search = ready.search,
                pendingPlay = ready.pendingPlay,
                pendingQueueItemId = ready.pendingQueueItemId,
                queueRefreshing = ready.queueRefreshing,
                artist = ready.artist,
                audioState = audioState,
                musicSource = ready.musicSource,
                playlists = ready.playlists,
                quickPicks = ready.quickPicks,
                mixedForYou = ready.mixedForYou,
                spotifyPlaylists = ready.spotifyPlaylists,
                spotifyRecentlyPlayed = ready.spotifyRecentlyPlayed,
                today = ready.today,
                displayedMonth = ready.displayedMonth,
                selectedDay = ready.selectedDay,
                todoDay = ready.todoDay,
                calendarView = ready.calendarView,
                eventsByDay = ready.eventsByDay,
                todos = ready.calendar.todos,
                weekDays = ready.weekDays,
                calendarWindow = ready.calendarWindow,
                nowMinutes = ready.nowMinutes,
                calendarSources = ready.calendar.sources,
                calendarStale = ready.calendar.stale,
                calendarHasTodoList = ready.calendar.hasTodoList,
                dayMarks = ready.dayMarks,
                weekHourHeight = ready.weekHourHeight,
                eventEditor = ready.eventEditor,
                eventDetail = ready.eventDetail,
                calendarFilters = ready.calendarFilters,
                calendarSettingsOpen = ready.calendarSettingsOpen,
                savingEvent = ready.savingEvent,
                onSelectPanel = viewModel::selectPanel,
                onSelectMusicSource = viewModel::selectMusicSource,
                onSetMediaMinimized = viewModel::setMediaMinimized,
                onQueryChange = viewModel::setSearchQuery,
                onPlay = viewModel::play,
                // Like the queue intents, the enqueue resolves the active audio room in the ViewModel.
                onEnqueue = viewModel::enqueue,
                onOpenArtist = viewModel::openArtist,
                onCloseArtist = viewModel::closeArtist,
                onPlayTopHit = viewModel::playTopHits,
                onShuffleArtist = viewModel::shuffleArtist,
                onTogglePlay = { viewModel.togglePlay(audioRoom) },
                onNext = { viewModel.next(audioRoom) },
                onPrevious = { viewModel.previous(audioRoom) },
                onSeek = { sec -> viewModel.seek(audioRoom, sec) },
                onToggleShuffle = { viewModel.setShuffle(audioRoom, !audioState.isShuffle) },
                onCycleRepeat = { viewModel.setRepeat(audioRoom, audioState.repeat.cycle()) },
                // Both queue intents already resolve the active audio room inside the ViewModel, so
                // they need no capture here (unlike the transport lambdas above).
                onPlayQueueItem = viewModel::playQueueItem,
                onMoveQueueItem = viewModel::moveQueueItem,
                onSelectCalendarView = viewModel::setCalendarView,
                onShowMonth = viewModel::showMonth,
                onShowWeek = viewModel::showWeek,
                onSelectDay = viewModel::selectDay,
                onShowTodoDay = viewModel::showTodoDay,
                onAddTodo = viewModel::addTodo,
                onToggleTodo = viewModel::toggleTodo,
                onEditTodo = viewModel::editTodo,
                // The editor's writes resolve which event (and which calendar) they address inside the
                // ViewModel, so like the queue intents they need no capture here.
                onAddEvent = viewModel::openNewEvent,
                onShowToday = viewModel::showToday,
                onOpenEvent = viewModel::openEvent,
                onOpenEventDetail = viewModel::openEventDetail,
                onNewEventAt = viewModel::openNewEventAt,
                onWeekHourHeight = viewModel::setWeekHourHeight,
                onEditEventDetail = viewModel::editEventDetail,
                onDeleteEventDetail = viewModel::deleteEventDetail,
                onCloseEventDetail = viewModel::closeEventDetail,
                onOpenCalendarSettings = viewModel::openCalendarSettings,
                onCloseCalendarSettings = viewModel::closeCalendarSettings,
                onToggleCalendarFilter = viewModel::toggleCalendarFilter,
                onSaveEvent = viewModel::saveEvent,
                onDeleteEvent = viewModel::deleteEvent,
                onCloseEventEditor = viewModel::closeEventEditor,
                modifier = Modifier.weight(1.12f).widthIn(min = 392.dp),
            )
        }
        ToastHost(
            toast = ready.toast,
            onDismiss = viewModel::dismissToast,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Dimensions.surfacePadV),
        )
    }
}

/**
 * Flashes the current [ToastMessage] as a floating Forest pill at the bottom of the screen, then
 * auto-dismisses it. The message is latched so the pill's exit animation doesn't render blank; a new
 * id re-arms the timer even when the text is identical.
 */
@Composable
internal fun ToastHost(toast: ToastMessage?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val latched = remember { mutableStateOf(toast) }
    if (toast != null) latched.value = toast
    toast?.let { LaunchedEffect(it.id) { delay(TOAST_MILLIS); onDismiss() } }
    AnimatedVisibility(
        visible = toast != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        latched.value?.let { message ->
            Text(
                text = message.text,
                color = OnForest,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .shadow(Dimensions.pillElevation, RoundedCornerShape(percent = 50))
                    .background(Forest, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

private const val TOAST_MILLIS = 3_000L
