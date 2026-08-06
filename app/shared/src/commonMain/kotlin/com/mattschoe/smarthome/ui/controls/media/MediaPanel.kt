package com.mattschoe.smarthome.ui.controls.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.ui.pages.homepage.ArtistUiState
import com.mattschoe.smarthome.ui.pages.homepage.PendingPlay
import com.mattschoe.smarthome.ui.pages.homepage.SearchState
import com.mattschoe.smarthome.ui.theme.Dimensions

/** Which of the Media panel's three surfaces is showing. See [MediaPanel] for the precedence. */
private enum class MediaSurface { NowPlaying, Artist, Browse }

/**
 * The Media panel content: the now-playing surface when the active audio room has a track and the
 * player is expanded, the artist drill-in while one is open, else the browse surface. Collapsing
 * ([minimized]) shows browse *while* audio plays; the floating [MiniPlayerBar] that replaces the
 * surface — and the [MinimizeHandle] that triggers the collapse — are drawn by the panel's **host**
 * above this scroll, so all three surfaces reserve room for them at their bottom.
 *
 * An open [artist] outranks now-playing: it is only ever opened by a deliberate tap, and playing
 * anything from it closes it again (the ViewModel does that), so the panel returns to the music.
 *
 * [layout] is the tablet-card vs. phone-page re-flow; the state and the surface precedence are the
 * same either way.
 */
@Composable
fun MediaPanel(
    audioState: AudioState,
    minimized: Boolean,
    searchQuery: String,
    search: SearchState,
    pendingPlay: PendingPlay?,
    pendingQueueItemId: String?,
    queueRefreshing: Boolean,
    artist: ArtistUiState?,
    musicSource: MusicSource,
    playlists: List<BrowseItem>,
    quickPicks: List<BrowseItem>,
    mixedForYou: List<BrowseItem>,
    spotifyPlaylists: List<BrowseItem>,
    spotifyRecentlyPlayed: List<BrowseItem>,
    onQueryChange: (String) -> Unit,
    onPlay: (BrowseItem) -> Unit,
    onOpenArtist: (BrowseItem) -> Unit,
    onCloseArtist: () -> Unit,
    onPlayTopHit: (Int) -> Unit,
    onShuffleArtist: () -> Unit,
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
    // A pending play paints the tapped item as the (loading) now-playing track right away — the real
    // track takes several seconds to arrive, and this surface is the feedback for the tap.
    val pendingTrack = pendingPlay?.let {
        MediaTrack(
            title = it.title,
            artist = it.subtitle.orEmpty(),
            album = null,
            artworkUrl = it.artworkUrl,
            durationSec = 0,
        )
    }
    val track = pendingTrack ?: rememberLatchedTrack(audioState.nowPlaying)
    val hasTrack = pendingPlay != null || audioState.nowPlaying != null
    val surface = when {
        artist != null -> MediaSurface.Artist
        hasTrack && !minimized -> MediaSurface.NowPlaying
        else -> MediaSurface.Browse
    }
    val bottomInset =
        if (audioState.nowPlaying != null) Dimensions.miniPlayerHeight + Dimensions.mediaSectionGap else 0.dp

    AnimatedContent(
        targetState = surface,
        modifier = modifier.fillMaxSize(),
        // `using null` drops the SizeTransform: every surface fills the panel, so there is no container
        // height to animate — and animating one would only fight the scroll each surface owns.
        transitionSpec = {
            (fadeIn(tween(200)) + slideInVertically { h -> h / 8 }) togetherWith
                (fadeOut(tween(120)) + slideOutVertically { h -> h / 8 }) using null
        },
        label = "media-surface",
    ) { target ->
        when {
            target == MediaSurface.NowPlaying && track != null -> NowPlayingSurface(
                track = track,
                audioState = audioState,
                loading = pendingPlay != null,
                queueRefreshing = queueRefreshing,
                pendingQueueItemId = pendingQueueItemId,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onPlayQueueItem = onPlayQueueItem,
                onMoveQueueItem = onMoveQueueItem,
                layout = layout,
                modifier = Modifier.fillMaxSize(),
            )
            // The state is read inside the transition, so it can be null on the frame the surface
            // animates out — the browse surface stands in for that frame.
            target == MediaSurface.Artist && artist != null -> ArtistSurface(
                artist = artist,
                onBack = onCloseArtist,
                onPlayTopHit = onPlayTopHit,
                onShuffle = onShuffleArtist,
                onPlay = onPlay,
                bottomInset = bottomInset,
                layout = layout,
                modifier = Modifier.fillMaxSize(),
            )
            else -> BrowseSurface(
                query = searchQuery,
                search = search,
                source = musicSource,
                playlists = playlists,
                quickPicks = quickPicks,
                mixedForYou = mixedForYou,
                spotifyPlaylists = spotifyPlaylists,
                spotifyRecentlyPlayed = spotifyRecentlyPlayed,
                onQueryChange = onQueryChange,
                onPlay = onPlay,
                onOpenArtist = onOpenArtist,
                bottomInset = bottomInset,
                layout = layout,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
