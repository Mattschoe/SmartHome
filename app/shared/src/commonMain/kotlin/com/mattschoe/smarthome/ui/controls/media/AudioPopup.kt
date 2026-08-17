package com.mattschoe.smarthome.ui.controls.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.ui.components.PopupCard
import com.mattschoe.smarthome.ui.components.PopupScrim
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.controls.WrappingRoomChips
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.OnForest
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.speaker_group_outline
import smarthome.shared.generated.resources.speaker_outline

/** The gap between the two discs a page floats at its bottom-right, and what [SpeakerButton] is one of. */
val FloatingStackGap = 8.dp

/** Which trigger the card was dropped from, and so where it hangs. */
sealed interface AudioPopupAnchor {
    /** From the browse surface's header — under the search row, at the page's trailing edge. */
    data object Header : AudioPopupAnchor

    /** From the now-playing surface — above the floating disc stack it was opened from. */
    data object Transport : AudioPopupAnchor

    /**
     * From the speaker disc floating over a landscape card's bottom end. [cardWidth] is the card's
     * own width, which the page measures: the popup's right edge lines up with the disc's rather
     * than with the page's — the disc sits inside the card's content padding, not the page's margin.
     */
    data class Card(val cardWidth: Dp) : AudioPopupAnchor
}

/**
 * *Where* the music plays, as a card rather than a page section: the speaker room, the join offer, and
 * that room's volume. On the tablet these live in the center card beside the dial, but a portrait page
 * has no room to spare for a control cluster that is set once and then left alone — so the phone puts
 * them behind [SpeakerButton] and gives the whole page back to browsing.
 *
 * Picking a room leaves the card open: setting the room and then its volume is one errand, not two.
 * The scrim, or the button again, is what closes it.
 */
@Composable
fun BoxScope.AudioPopup(
    anchor: AudioPopupAnchor,
    activeAudioRoom: Room,
    volumePct: Int,
    joinTarget: Room?,
    audioJoined: Boolean,
    onSelectAudioRoom: (Room) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleAudioJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    PopupScrim(onDismiss)
    PopupCard(
        modifier = when (anchor) {
            AudioPopupAnchor.Header -> Modifier
                .align(Alignment.TopEnd)
                // Hangs off the search row it was opened from, the way the calendar's gear popup hangs
                // off the card header: the page's top pad, the row itself, then the usual section gap.
                .offset(
                    y = Dimensions.phonePageTopPad +
                        Dimensions.searchFieldRowHeight +
                        Dimensions.mediaSectionGap,
                )
                .padding(horizontal = Dimensions.phonePagePad)
            AudioPopupAnchor.Transport -> Modifier
                .align(Alignment.BottomEnd)
                // Lined up with the disc stack, which sits outside the page's margin, not with it.
                .padding(horizontal = Dimensions.miniPlayerBarPadding)
                // Clears the two discs it was opened from, which already clear the page's dot row.
                .padding(
                    bottom = Dimensions.phonePageBottomClearance +
                        Dimensions.minTouch * 2 + FloatingStackGap * 2,
                )
            is AudioPopupAnchor.Card -> Modifier
                .align(Alignment.BottomEnd)
                // Lined up with the disc that opened it: the disc hangs at the left card's bottom end,
                // inside the card's content padding, so the popup's right edge follows it there — the
                // card and the page gap are shifted off the page's own right edge.
                .offset(
                    x = -(anchor.cardWidth + Dimensions.phoneCardGap + Dimensions.phoneCardPaddingH),
                )
                // Clears the disc it drops from, which sits [phoneCardPadding] up from the card's
                // bottom edge (the card itself fills the page's padded area, so no page pad term).
                .padding(
                    bottom = Dimensions.phoneCardPadding + Dimensions.minTouch + FloatingStackGap,
                )
        }.widthIn(max = Dimensions.audioPopupMaxWidth),
    ) {
        SectionLabel("Playing in")
        Spacer(Modifier.height(10.dp))
        WrappingRoomChips(
            rooms = Room.audioRooms,
            activeRoom = activeAudioRoom,
            onSelectRoom = onSelectAudioRoom,
            leadingIcon = Res.drawable.speaker_outline,
            modifier = Modifier.fillMaxWidth(),
        )
        if (joinTarget != null) {
            JoinRoomAction(
                text = if (audioJoined) "Stop i ${joinTarget.displayName}"
                else "Spil også i ${joinTarget.displayName}",
                onClick = onToggleAudioJoin,
            )
        }
        Spacer(Modifier.height(Dimensions.mediaSectionGap))
        VolumeSlider(
            volumePct = volumePct,
            onVolumeChange = onVolumeChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The trigger for [AudioPopup] — the Forest disc the right card's source badge and add-event button
 * are, carrying the speaker-group glyph. Icon only: the room it points at is named inside the card,
 * and printing it here would put the audio selector back on the page in miniature.
 */
@Composable
fun SpeakerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dimensions.minTouch)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Vælg højttaler og lydstyrke" },
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
                painter = painterResource(Res.drawable.speaker_group_outline),
                contentDescription = null,
                tint = OnForest,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
