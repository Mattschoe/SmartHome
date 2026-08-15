package com.mattschoe.smarthome.ui.controls

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.ui.components.PillChip
import com.mattschoe.smarthome.ui.components.horizontalScrollFade
import com.mattschoe.smarthome.ui.theme.Card
import org.jetbrains.compose.resources.DrawableResource

/** Gap between room chips, shared by both arrangements. */
private val ChipGap = 10.dp

/** The wrapping row's gap in the compact (landscape light card) size. */
private val ChipGapCompact = 8.dp

/**
 * A wrapping row of room pill toggles — the tablet arrangement, where every chip fits the card. Used
 * for both the light selector (all [Room.entries]) and the AUDIO selector ([Room.audioRooms] with a
 * speaker [leadingIcon]); selecting swaps that section's state via [activeRoom].
 *
 * [compact] shrinks the chips (see [PillChip]) and tightens the row's gaps — the landscape light
 * card's arrangement, where the fixed card needs the room the full-size row would take.
 */
@Composable
fun WrappingRoomChips(
    rooms: List<Room>,
    activeRoom: Room,
    onSelectRoom: (Room) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: DrawableResource? = null,
    compact: Boolean = false,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            if (compact) ChipGapCompact else ChipGap,
            Alignment.CenterHorizontally,
        ),
        verticalArrangement = Arrangement.spacedBy(if (compact) ChipGapCompact else ChipGap),
    ) {
        rooms.forEach { room ->
            PillChip(
                text = room.displayName,
                selected = room == activeRoom,
                onClick = { onSelectRoom(room) },
                leadingIcon = leadingIcon,
                compact = compact,
            )
        }
    }
}

/**
 * The same chips on one scrolling line — the phone arrangement, where the row is wider than the
 * screen. Draw it **edge to edge**, outside the page's side margin: [contentPadding] insets the chips
 * to that margin instead, so the trailing chip can run past the screen edge under the fade rather than
 * stopping short of it. The fade is the scroll affordance the layout guide asks for.
 *
 * Its horizontal scroll doesn't steal the page pager's gesture — nested scroll hands the drag on once
 * the row hits its end.
 */
@Composable
fun ScrollingRoomChips(
    rooms: List<Room>,
    activeRoom: Room,
    onSelectRoom: (Room) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    fadeColor: Color = Card,
    leadingIcon: DrawableResource? = null,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .horizontalScrollFade(scrollState, color = fadeColor)
            .horizontalScroll(scrollState)
            .padding(horizontal = contentPadding),
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rooms.forEach { room ->
            PillChip(
                text = room.displayName,
                selected = room == activeRoom,
                onClick = { onSelectRoom(room) },
                leadingIcon = leadingIcon,
            )
        }
    }
}
