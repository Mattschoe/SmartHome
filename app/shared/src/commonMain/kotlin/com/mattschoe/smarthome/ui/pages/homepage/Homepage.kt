package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.components.InsetSurface
import com.mattschoe.smarthome.ui.components.PageShell
import com.mattschoe.smarthome.ui.components.PillChip
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.components.bottomScrollFade
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.Muted
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.search_outline
import smarthome.shared.generated.resources.speaker_outline

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

// TEMPORARY (Phase 3) — a primitives gallery hosted on the dashboard screen so /android-verify can
// confirm each component kit primitive on-device. Replaced by the real left/center/right cards in
// Phases 4–7. Still collects the live StateFlow so nothing regresses.
@Composable
private fun LandscapeLayout(
    state: HomeScreenState.Ready
) {
    PageShell { innerPadding ->
        var selectedChip by remember { mutableStateOf(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 26.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // CardContainer + SectionLabel
            CardContainer(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Apps")
                    Text(
                        "Aktivt rum: ${state.activeRoom.displayName} · ${state.activeRoomState.brightnessPct}%",
                        fontWeight = FontWeight.Medium,
                    )

                    // PillChips: selected / idle / idle-with-icon
                    SectionLabel("Warmth")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillChip("Living room", selected = selectedChip == 0, onClick = { selectedChip = 0 })
                        PillChip("Kitchen", selected = selectedChip == 1, onClick = { selectedChip = 1 })
                        PillChip(
                            "Bedroom",
                            selected = selectedChip == 2,
                            onClick = { selectedChip = 2 },
                            leadingIcon = Res.drawable.speaker_outline,
                        )
                    }

                    // InsetSurface as a search field
                    InsetSurface(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 14.dp, vertical = 12.dp,
                        ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.search_outline),
                                tint = Muted,
                                modifier = Modifier.size(20.dp),
                                contentDescription = null
                            )
                            Text("Search songs, artists, podcasts", color = Muted, fontSize = 15.sp)
                        }
                    }
                }
            }

            // Scroll region + bottom fade cue
            CardContainer(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .height(140.dp)
                        .bottomScrollFade()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(12) { i ->
                        Text("Up next item ${i + 1}", color = InkSoft, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
