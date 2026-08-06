package com.mattschoe.smarthome.ui.controls.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.OnForest
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.pause_filled
import smarthome.shared.generated.resources.play_filled
import smarthome.shared.generated.resources.repeat_filled
import smarthome.shared.generated.resources.shuffle_filled
import smarthome.shared.generated.resources.skip_next_filled
import smarthome.shared.generated.resources.skip_previous_filled

/** Centered transport controls. Shuffle/repeat tint Forest when active; the play/pause is a Forest disc. */
@Composable
fun TransportRow(
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
