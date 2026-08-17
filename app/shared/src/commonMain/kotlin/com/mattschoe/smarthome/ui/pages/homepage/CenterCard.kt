package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.data.model.RoomState
import com.mattschoe.smarthome.data.model.Warmth
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.controls.BrightnessDial
import com.mattschoe.smarthome.ui.controls.WarmthSwatches
import com.mattschoe.smarthome.ui.controls.WrappingRoomChips
import com.mattschoe.smarthome.ui.controls.media.JoinRoomAction
import com.mattschoe.smarthome.ui.controls.media.VolumeSlider
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.Muted
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.music_note_filled
import smarthome.shared.generated.resources.speaker_outline
import smarthome.shared.generated.resources.volume_off_outline

/**
 * The flex-1 center card. Light and audio are selected **independently**: the top chip row picks the
 * light room (dial + warmth, bound to [lightRoomState]); the AUDIO chip row picks the audio room
 * (volume slider + now-playing status, bound to [audioState]). Neither selection drives the other.
 * [joinTarget] is the room the audio room can play along with — `null` when there is nothing to
 * offer, and then no join action shows at all. Width-agnostic — the `Expanded` assembly point in [Homepage.kt] assigns its width; all page
 * geometry lives there.
 */
@Composable
fun CenterCard(
    activeLightRoom: Room,
    lightRoomState: RoomState,
    activeAudioRoom: Room,
    audioState: AudioState,
    joinTarget: Room?,
    audioJoined: Boolean,
    onSelectLightRoom: (Room) -> Unit,
    onSelectAudioRoom: (Room) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onWarmthChange: (Warmth) -> Unit,
    onToggleLight: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleAudioJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardContainer(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WrappingRoomChips(
                rooms = Room.entries,
                activeRoom = activeLightRoom,
                onSelectRoom = onSelectLightRoom,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimensions.cardGap))
            BrightnessDial(
                brightnessPct = lightRoomState.brightnessPct,
                isLightOn = lightRoomState.isLightOn,
                warmth = lightRoomState.lightWarmth,
                onBrightnessChange = onBrightnessChange,
                onToggleLight = onToggleLight,
            )
            Text(
                text = if (lightRoomState.isLightOn) "${lightRoomState.brightnessPct}%" else "Off",
                color = Ink,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Dimensions.cardGap))
            WarmthSwatches(selected = lightRoomState.lightWarmth, onSelect = onWarmthChange)
            Spacer(Modifier.height(Dimensions.centerSectionGap))
            HorizontalDivider(color = CardBorder, thickness = 1.dp)
            Spacer(Modifier.height(Dimensions.centerSectionGap))
            AudioSectionHeader(audioState = audioState, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            WrappingRoomChips(
                rooms = Room.audioRooms,
                activeRoom = activeAudioRoom,
                onSelectRoom = onSelectAudioRoom,
                leadingIcon = Res.drawable.speaker_outline,
                modifier = Modifier.fillMaxWidth(),
            )
            // Weight-1 spacers above and below center the join action in the whitespace between the
            // audio chips and the volume slider (and collapse to one gap when there is no action).
            Spacer(Modifier.weight(1f))
            if (joinTarget != null) {
                JoinRoomAction(
                    text = if (audioJoined) "Leave ${joinTarget.displayName}"
                    else "Join ${joinTarget.displayName}",
                    onClick = onToggleAudioJoin,
                )
                Spacer(Modifier.weight(1f))
            }
            VolumeSlider(
                volumePct = audioState.volumePct,
                onVolumeChange = onVolumeChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The AUDIO section header: the "Audio" label with a trailing now-playing status aligned to the row
 * end. Playing → a music-note glyph + "title — artist" (truncated); idle → just a muted-speaker glyph
 * (no label — the glyph alone reads as "nothing playing", its a11y label carries the meaning).
 */
@Composable
private fun AudioSectionHeader(audioState: AudioState, modifier: Modifier = Modifier) {
    val track = audioState.nowPlaying
    val glyph = if (track != null) Res.drawable.music_note_filled else Res.drawable.volume_off_outline
    val statusColor = if (track != null) InkSoft else Muted

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("Audio", fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(glyph),
                contentDescription = if (track == null) "Der spilles ikke musik" else null,
                tint = statusColor,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
