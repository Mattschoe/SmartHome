package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.controls.BrightnessDial
import com.mattschoe.smarthome.ui.controls.WarmthRows
import com.mattschoe.smarthome.ui.controls.WrappingRoomChips
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Ink

/**
 * Landscape page 1 — Home. The tablet center card's light half and the phone's warmth list, side by
 * side: the left card is the tablet's centered block (wrapping chips + dial + readout, nothing
 * re-flowed), the right card carries [WarmthRows] — the vertical-phone control, unchanged. The audio
 * half of the center card is not here: it lives behind the [SpeakerButton] on the Music page, as in
 * portrait.
 *
 * Both cards are **fixed** — no scroll of their own, so the page never stacks a scroller inside a
 * pager. The left card fits by construction (the chips are the compact size and the dial clamps to
 * the card), and the warmth rows are the whole right card, so both always show everything they hold.
 */
@Composable
fun LandscapeHomePage(
    ready: HomeScreenState.Ready,
    viewModel: HomepageViewModel,
    modifier: Modifier = Modifier,
) {
    // Captured once, per the Expanded assembly convention in Homepage.kt: the callbacks stay bound to
    // the room that was on screen when they were created.
    val lightRoom = ready.activeLightRoom
    val roomState = ready.lightRoomState

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.phoneCardGap),
    ) {
        CardContainer(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(Dimensions.phoneCardPadding),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // The tablet's dial at its native width, shrinking only when the card is narrower.
                // Height trimmed to just under the growth baseline, like the portrait light page —
                // nothing is drawn below it, and the card's dead space would push the readout down.
                val dialWidth = minOf(Dimensions.centerDialWidth, maxWidth)
                val dialScale = dialWidth / Dimensions.centerDialWidth
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    WrappingRoomChips(
                        rooms = Room.entries,
                        activeRoom = lightRoom,
                        onSelectRoom = viewModel::selectLightRoom,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    BrightnessDial(
                        brightnessPct = roomState.brightnessPct,
                        isLightOn = roomState.isLightOn,
                        warmth = roomState.lightWarmth,
                        onBrightnessChange = { value -> viewModel.setBrightness(lightRoom, value) },
                        onToggleLight = { viewModel.toggleLight(lightRoom) },
                        width = dialWidth,
                        height = Dimensions.centerGrowthBaselineY * dialScale + 8.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (roomState.isLightOn) "${roomState.brightnessPct}%" else "Fra",
                        color = Ink,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        CardContainer(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(Dimensions.phoneCardPadding),
        ) {
            // Bare and fixed: no title (the rows are the whole card), centred vertically. Five rows
            // fit every card a landscape window can produce, so there is no scroll here either.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WarmthRows(
                    selected = roomState.lightWarmth,
                    onSelect = { warmth -> viewModel.setWarmth(lightRoom, warmth) },
                )
            }
        }
    }
}
