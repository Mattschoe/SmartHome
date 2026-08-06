package com.mattschoe.smarthome.ui.controls.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.theme.ArtScrim
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnArt
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.equalizer_filled

/**
 * The playing state: album art + title/subtitle/scrubber, transport, and the up-next queue. Browsing
 * (playlists included) belongs to the other surface — this one is about the track that is on. The
 * collapse caret is not part of it either — the host floats that over this surface.
 *
 * [layout] only re-flows the header block: `Tablet` sets the art beside the text, `Phone` stacks a
 * large square of art over centered text. Transport and [UpNextSection] are identical in both.
 *
 * While [loading] (a tapped item whose stream Music Assistant is still resolving) the art carries a
 * spinner and every control is inert — the surface is pure feedback until the real track arrives.
 *
 * The art/scrubber/transport block is pinned; only [UpNextSection] scrolls, so reaching down the queue
 * never pushes the controls out of view.
 */
@Composable
fun NowPlayingSurface(
    track: MediaTrack,
    audioState: AudioState,
    loading: Boolean,
    queueRefreshing: Boolean,
    pendingQueueItemId: String?,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayQueueItem: (String) -> Unit,
    onMoveQueueItem: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    layout: MediaLayout = MediaLayout.Tablet,
) {
    Column(modifier.fillMaxSize()) {
        val positionSec = if (loading) 0 else rememberLivePositionSec(audioState, track)
        when (layout) {
            MediaLayout.Tablet -> TabletNowPlayingHeader(
                track = track,
                loading = loading,
                positionSec = positionSec,
                onSeek = onSeek,
            )
            MediaLayout.Phone -> PhoneNowPlayingHeader(
                track = track,
                loading = loading,
                positionSec = positionSec,
                onSeek = onSeek,
            )
        }
        Spacer(Modifier.height(Dimensions.mediaSectionGap))
        TransportRow(
            isPlaying = audioState.isPlaying,
            isShuffle = audioState.isShuffle,
            repeat = audioState.repeat,
            enabled = !loading,
            onTogglePlay = onTogglePlay,
            onNext = onNext,
            onPrevious = onPrevious,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
        )
        when {
            // The rows on hand belong to the previous track while a play is (re)building the queue —
            // hold a loader instead of showing them (or the freshly played track itself) as "up next".
            loading || queueRefreshing -> {
                Spacer(Modifier.height(Dimensions.mediaSectionGap))
                UpNextLoader(modifier = Modifier.weight(1f))
            }
            audioState.queue.isNotEmpty() -> {
                Spacer(Modifier.height(Dimensions.mediaSectionGap))
                UpNextSection(
                    queue = audioState.queue,
                    enabled = !loading,
                    pendingQueueItemId = pendingQueueItemId,
                    onPlayQueueItem = onPlayQueueItem,
                    onMoveQueueItem = onMoveQueueItem,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The card arrangement: a fixed square of art with the title, subtitle and scrubber beside it. */
@Composable
private fun TabletNowPlayingHeader(
    track: MediaTrack,
    loading: Boolean,
    positionSec: Int,
    onSeek: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AlbumArt(track = track, loading = loading, modifier = Modifier.size(Dimensions.albumArtSize))
        Column(
            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = track.title,
                color = Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = trackSubtitle(track),
                color = InkSoft,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Scrubber(
                positionSec = positionSec,
                durationSec = track.durationSec,
                enabled = !loading,
                onSeek = onSeek,
            )
        }
    }
}

/**
 * The page arrangement: a large centered square of art over centered text, then the full-width
 * scrubber. The art is clamped to the page width, so a narrow phone shrinks it rather than clipping.
 */
@Composable
private fun PhoneNowPlayingHeader(
    track: MediaTrack,
    loading: Boolean,
    positionSec: Int,
    onSeek: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val artSize = minOf(Dimensions.phoneAlbumArtSize, maxWidth)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AlbumArt(track = track, loading = loading, modifier = Modifier.size(artSize))
            Spacer(Modifier.height(Dimensions.phoneMediaTitleGap))
            Text(
                text = track.title,
                color = Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = trackSubtitle(track),
                color = InkSoft,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Dimensions.phoneMediaTitleGap))
            Scrubber(
                positionSec = positionSec,
                durationSec = track.durationSec,
                enabled = !loading,
                onSeek = onSeek,
            )
        }
    }
}

/** `artist · album`, falling back to the artist alone for a track with no album. */
private fun trackSubtitle(track: MediaTrack): String =
    track.album?.let { "${track.artist} · $it" } ?: track.artist

/** The cover art, with the spinner scrim it carries while the stream behind it is still resolving. */
@Composable
private fun AlbumArt(track: MediaTrack, loading: Boolean, modifier: Modifier = Modifier) {
    Box(modifier) {
        ArtTile(
            background = Forest,
            glyph = Res.drawable.equalizer_filled,
            glyphSize = 40.dp,
            modifier = Modifier.fillMaxSize(),
            artworkUrl = track.artworkUrl,
        )
        if (loading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(Dimensions.innerBlockRadius))
                    .background(ArtScrim.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = OnArt)
            }
        }
    }
}

/** Stand-in for [UpNextSection] while the queue behind it is being replaced by a play in flight. */
@Composable
private fun UpNextLoader(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel("Up next")
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Muted)
        }
    }
}
