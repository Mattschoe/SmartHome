package com.mattschoe.smarthome.ui.pages.homepage

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mattschoe.smarthome.data.cycle
import com.mattschoe.smarthome.ui.layout.DashboardLayout
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.SageSurface

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
 * The layout seam: measure the available width, map it to a [DashboardLayout], and branch. Only the
 * [DashboardLayout.Expanded] (tablet) branch is designed in v1; [DashboardLayout.Compact] (phone) is
 * an explicit stub so phone support drops in here later without touching the cards.
 */
@Composable
private fun DashboardRoot(state: HomeScreenState.Ready, viewModel: HomepageViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        when (DashboardLayout.from(maxWidth)) {
            DashboardLayout.Expanded -> ExpandedDashboard(state, viewModel)
            DashboardLayout.Compact -> CompactDashboard(state)
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
                onSelectLightRoom = viewModel::selectLightRoom,
                onSelectAudioRoom = viewModel::selectAudioRoom,
                onBrightnessChange = { value -> viewModel.setBrightness(lightRoom, value) },
                onWarmthChange = { warmth -> viewModel.setWarmth(lightRoom, warmth) },
                onToggleLight = { viewModel.toggleLight(lightRoom) },
                onVolumeChange = { value -> viewModel.setVolume(audioRoom, value) },
                modifier = Modifier.weight(1f).widthIn(min = 346.dp),
            )
            RightCard(
                panel = ready.panel,
                audioState = audioState,
                playlists = ready.playlists,
                quickPicks = ready.quickPicks,
                keepListening = ready.keepListening,
                today = ready.today,
                displayedMonth = ready.displayedMonth,
                selectedDay = ready.selectedDay,
                selectedDayEvents = ready.selectedDayEvents,
                selectedDayTodos = ready.selectedDayTodos,
                daysWithItems = ready.daysWithItems,
                onSelectPanel = viewModel::selectPanel,
                onTogglePlay = { viewModel.togglePlay(audioRoom) },
                onNext = { viewModel.next(audioRoom) },
                onPrevious = { viewModel.previous(audioRoom) },
                onSeek = { sec -> viewModel.seek(audioRoom, sec) },
                onToggleShuffle = { viewModel.setShuffle(audioRoom, !audioState.isShuffle) },
                onCycleRepeat = { viewModel.setRepeat(audioRoom, audioState.repeat.cycle()) },
                onPrevMonth = viewModel::showPreviousMonth,
                onNextMonth = viewModel::showNextMonth,
                onSelectDay = viewModel::selectDay,
                onAddTodo = viewModel::addTodo,
                onToggleTodo = viewModel::toggleTodo,
                onEditTodo = viewModel::editTodo,
                modifier = Modifier.weight(1.12f).widthIn(min = 392.dp),
            )
        }
    }
}

/** Phone layout is not designed in this phase — the seam exists so it can be built here later. */
@Composable
private fun CompactDashboard(ready: HomeScreenState.Ready) {
    Box(
        modifier = Modifier.fillMaxSize().background(SageSurface),
        contentAlignment = Alignment.Center,
    ) {
        Text("Phone-layout er endnu ikke designet", color = InkSoft, fontSize = 16.sp)
    }
}
