package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mattschoe.smarthome.ui.components.PageShell

@Composable
fun Homepage(
    navController: NavController,
    viewModel: HomepageViewModel
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    when (val ready = state) {
        HomeScreenState.Loading -> { /* NO-OP */ }
        is HomeScreenState.Ready -> LandscapeLayout(state = ready)
    }
}

@Composable
private fun LandscapeLayout(
    state: HomeScreenState.Ready
) {
    PageShell { innerPadding ->
        // TEMPORARY (Phase 2): proves the StateFlow is live. Replaced by the real left/center/right
        // cards in Phases 4–7.
        val room = state.activeRoomState
        Column(modifier = Modifier.padding(innerPadding)) {
            Text("Aktivt rum: ${state.activeRoom.displayName}")
            Text("Lysstyrke: ${room.brightnessPct}%  (tændt=${room.isLightOn})")
            Text("Varme: ${room.lightWarmth}")
            Text("Lydstyrke: ${room.volumePct}")
            Text("Panel: ${state.panel}")
            Text("Afspiller: ${room.nowPlaying?.title ?: "Intet"}")
            Text("Indendørs: ${state.climate.indoorTempC}°C  ·  Luftfugtighed: ${state.climate.humidityPct}%")
        }
    }
}
