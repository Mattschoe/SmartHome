package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.components.verticalScrollFade
import com.mattschoe.smarthome.ui.controls.BrightnessDial
import com.mattschoe.smarthome.ui.controls.ScrollingRoomChips
import com.mattschoe.smarthome.ui.controls.WarmthRows
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.Muted

/**
 * Portrait page 1 — Apps. App shortcuts are deferred until the tablet has them too (its `AppsCard` is
 * the same reserved slot), so this holds the layout: the section label over an empty flexible region
 * that becomes the launcher grid once there is a model to fill it.
 */
@Composable
fun PortraitAppsPage(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = Dimensions.phonePagePad)) {
        Spacer(Modifier.height(Dimensions.phonePageTopPad))
        SectionLabel("Apps")
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Portrait page 2 — Light Control, and the page the phone opens on. The tablet center card's light
 * half, re-flowed down the screen: the room selector becomes a scrolling row rather than a wrapping
 * one, and warmth becomes a full-width list rather than a swatch row. Same controls, same state.
 *
 * The chip row is the one thing drawn edge to edge — it pads itself, so its trailing chip runs off
 * the screen under the fade instead of stopping at the page margin.
 */
@Composable
fun PortraitLightPage(
    activeLightRoom: Room,
    lightRoomState: RoomState,
    viewModel: HomepageViewModel,
    modifier: Modifier = Modifier,
) {
    val lightRoom = activeLightRoom
    val roomState = lightRoomState
    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier) {
        val dialWidth = minOf(
            Dimensions.phoneDialWidth,
            maxWidth - Dimensions.phonePagePad * 2,
        )
        val dialScale = dialWidth / Dimensions.centerDialWidth
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollFade(scrollState)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Dimensions.phonePageTopPad))
            ScrollingRoomChips(
                rooms = Room.entries,
                activeRoom = lightRoom,
                onSelectRoom = viewModel::selectLightRoom,
                contentPadding = Dimensions.phonePagePad,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(30.dp))
            BrightnessDial(
                brightnessPct = roomState.brightnessPct,
                isLightOn = roomState.isLightOn,
                warmth = roomState.lightWarmth,
                onBrightnessChange = { value -> viewModel.setBrightness(lightRoom, value) },
                onToggleLight = { viewModel.toggleLight(lightRoom) },
                width = dialWidth,
                // Trimmed to just under the growth baseline: nothing is drawn below it, and the tablet's
                // generous dead space would push the value readout far off the mock.
                height = Dimensions.centerGrowthBaselineY * dialScale + 8.dp,
            )
            Text(
                text = if (roomState.isLightOn) "${roomState.brightnessPct}%" else "Fra",
                color = Ink,
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(52.dp))
            WarmthRows(
                selected = roomState.lightWarmth,
                onSelect = { warmth -> viewModel.setWarmth(lightRoom, warmth) },
                modifier = Modifier.padding(horizontal = Dimensions.phonePagePad),
            )
            // Lets the list scroll clear of the dot row floating over the page.
            Spacer(Modifier.height(Dimensions.phonePageBottomClearance))
        }
    }
}
