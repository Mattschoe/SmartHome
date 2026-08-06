package com.mattschoe.smarthome.ui.controls.media

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.OnForest
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.drop_down_filled
import smarthome.shared.generated.resources.music_note_filled
import smarthome.shared.generated.resources.pause_filled
import smarthome.shared.generated.resources.play_filled
import smarthome.shared.generated.resources.skip_next_filled
import smarthome.shared.generated.resources.skip_previous_filled

/**
 * Holds on to the last non-null [track]. The mini player and now-playing surface animate out when
 * playback stops, and without a latch they would render their final frames against a null track.
 */
@Composable
fun rememberLatchedTrack(track: MediaTrack?): MediaTrack? {
    val latched = remember { mutableStateOf(track) }
    if (track != null) latched.value = track
    return latched.value
}

/**
 * Caret that collapses the now-playing surface into the [MiniPlayerBar]. Its host pins it to the
 * surface's bottom-right, where the bar it collapses into will appear, so it stays reachable however
 * far the queue scrolls. It carries an accent disc because it floats over that scrolling content.
 */
@Composable
fun MinimizeHandle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dimensions.minTouch)
            .shadow(Dimensions.pillElevation, CircleShape)
            .clip(CircleShape)
            .background(Forest)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Minimér afspilleren" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.drop_down_filled),
            contentDescription = null,
            tint = OnForest,
            modifier = Modifier.size(Dimensions.minimizeCaretSize),
        )
    }
}

/**
 * The collapsed player: a Forest bar floating over the browse surface with art, track and transport.
 * Inverted against the cream surface it overlays so it reads as hovering, and it carries only the
 * controls worth reaching without expanding — no scrubber, shuffle or repeat.
 *
 * There is no expand caret: the whole bar is the expand target, so a caret would have been a second
 * affordance for it, spending a touch target's width on what the title wants. The title marquees for
 * the same reason — the bar is narrow enough on a phone that a long one would otherwise be two words.
 */
@Composable
fun MiniPlayerBar(
    track: MediaTrack,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Dimensions.miniPlayerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimensions.miniPlayerHeight)
            .shadow(Dimensions.miniPlayerElevation, shape)
            .clip(shape)
            .background(Forest)
            // Tapping the bar itself expands; the transport children below claim their own taps.
            .clickable(onClick = onExpand)
            .semantics { contentDescription = "Åbn afspilleren" }
            .padding(horizontal = Dimensions.miniPlayerBarPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtTile(
            background = Forest,
            glyph = Res.drawable.music_note_filled,
            glyphSize = 20.dp,
            modifier = Modifier.size(Dimensions.miniPlayerThumbSize),
            artworkUrl = track.artworkUrl,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = OnForest,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                text = track.artist,
                color = OnForest.copy(alpha = 0.7f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MiniTransportIcon(Res.drawable.skip_previous_filled, "Forrige", onPrevious)
        MiniPlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlay)
        MiniTransportIcon(Res.drawable.skip_next_filled, "Næste", onNext)
    }
}

/** A transport icon tinted for the Forest bar. */
@Composable
private fun MiniTransportIcon(glyph: DrawableResource, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Dimensions.minTouch)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = null,
            tint = OnForest,
            modifier = Modifier.size(Dimensions.miniPlayerIconSize),
        )
    }
}

/** The bar's play/pause: the accent disc inverted (cream fill, Forest glyph) to stay the anchor. */
@Composable
private fun MiniPlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Dimensions.miniPlayerPlaySize)
            .clip(CircleShape)
            .background(OnForest)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (isPlaying) "Pause" else "Afspil" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isPlaying) Res.drawable.pause_filled else Res.drawable.play_filled),
            contentDescription = null,
            tint = Forest,
            modifier = Modifier.size(Dimensions.miniPlayerIconSize),
        )
    }
}
