package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mattschoe.smarthome.data.cycle
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.ui.controls.media.AudioPopup
import com.mattschoe.smarthome.ui.controls.media.AudioPopupAnchor
import com.mattschoe.smarthome.ui.controls.media.FloatingStackGap
import com.mattschoe.smarthome.ui.controls.media.MediaLayout
import com.mattschoe.smarthome.ui.controls.media.MediaPanel
import com.mattschoe.smarthome.ui.controls.media.MiniPlayerBar
import com.mattschoe.smarthome.ui.controls.media.MinimizeHandle
import com.mattschoe.smarthome.ui.controls.media.SpeakerButton
import com.mattschoe.smarthome.ui.controls.media.rememberLatchedTrack
import com.mattschoe.smarthome.ui.theme.Dimensions

/**
 * Portrait page 3 — Music. The tablet right card's Media panel given the whole screen: the same
 * [MediaPanel] the tablet composes, only in [MediaLayout.Phone], so browsing, searching, the artist
 * drill-in and the queue are all reachable here without a second surface. The tabs are not: the
 * Calendar lives on its own page, so this one is Media throughout, which is why the mini player and
 * the collapse caret drop the tablet's `panel == Media` term from their visibility.
 *
 * The audio half of the tablet's center card — speaker room, join, volume — is deliberately *not* on
 * the page. It is where the music goes rather than what is playing, so it hangs behind the
 * [SpeakerButton] as an [AudioPopup] instead, and the page is one panel with two floating overlays.
 */
@Composable
fun PortraitMusicPage(
    audioRoom: Room,
    audioState: AudioState,
    mediaMinimized: Boolean,
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
    joinTarget: Room?,
    audioJoined: Boolean,
    viewModel: HomepageViewModel,
    modifier: Modifier = Modifier,
) {
    // Transient and phone-only, with no consumer outside this page — the precedent is the week view's
    // all-day strip. `activeAudioRoom` itself stays ViewModel-owned, per the CORE RULE.
    var audioPopupOpen by remember { mutableStateOf(false) }

    val latchedTrack = rememberLatchedTrack(audioState.nowPlaying)
    val hasTrack = audioState.nowPlaying != null
    // The now-playing surface is up and settled: the two discs float over it, and the popup drops from
    // them rather than from the search row that isn't there.
    val onNowPlaying = hasTrack && !mediaMinimized && artist == null && pendingPlay == null

    Box(modifier.fillMaxSize()) {
        MediaPanel(
            audioState = audioState,
            minimized = mediaMinimized,
            searchQuery = searchQuery,
            search = search,
            pendingPlay = pendingPlay,
            pendingQueueItemId = pendingQueueItemId,
            queueRefreshing = queueRefreshing,
            artist = artist,
            musicSource = musicSource,
            playlists = playlists,
            quickPicks = quickPicks,
            mixedForYou = mixedForYou,
            spotifyPlaylists = spotifyPlaylists,
            spotifyRecentlyPlayed = spotifyRecentlyPlayed,
            onQueryChange = viewModel::setSearchQuery,
            onPlay = viewModel::play,
            onEnqueue = viewModel::enqueue,
            onOpenArtist = viewModel::openArtist,
            onCloseArtist = viewModel::closeArtist,
            onPlayTopHit = viewModel::playTopHits,
            onShuffleArtist = viewModel::shuffleArtist,
            onTogglePlay = { viewModel.togglePlay(audioRoom) },
            onNext = { viewModel.next(audioRoom) },
            onPrevious = { viewModel.previous(audioRoom) },
            onSeek = { sec -> viewModel.seek(audioRoom, sec) },
            onToggleShuffle = { viewModel.setShuffle(audioRoom, !audioState.isShuffle) },
            onCycleRepeat = { viewModel.setRepeat(audioRoom, audioState.repeat.cycle()) },
            // Both queue intents resolve the active audio room inside the ViewModel.
            onPlayQueueItem = viewModel::playQueueItem,
            onMoveQueueItem = viewModel::moveQueueItem,
            layout = MediaLayout.Phone,
            // The browse surface has a header to hang the trigger from; the now-playing one gets the
            // floating disc below instead.
            headerTrailing = { SpeakerButton(onClick = { audioPopupOpen = !audioPopupOpen }) },
            modifier = Modifier
                .padding(horizontal = Dimensions.phonePagePad)
                .padding(
                    top = Dimensions.phonePageTopPad,
                    bottom = Dimensions.phonePageBottomClearance,
                ),
        )

        // The bar and the disc stack float over the panel rather than scrolling with it — same
        // arrangement as the right card, only pinned to the page's bottom edge, outside its side
        // margin, so a long title has the width to scroll in.
        AnimatedVisibility(
            visible = hasTrack && (mediaMinimized || artist != null),
            enter = slideInVertically { h -> h } + fadeIn(),
            exit = slideOutVertically { h -> h } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Dimensions.miniPlayerPageMargin)
                .padding(bottom = Dimensions.phonePageBottomClearance),
        ) {
            latchedTrack?.let { track ->
                MiniPlayerBar(
                    track = track,
                    isPlaying = audioState.isPlaying,
                    onExpand = { viewModel.setMediaMinimized(false) },
                    onTogglePlay = { viewModel.togglePlay(audioRoom) },
                    onNext = { viewModel.next(audioRoom) },
                    onPrevious = { viewModel.previous(audioRoom) },
                )
            }
        }
        AnimatedVisibility(
            visible = onNowPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = Dimensions.miniPlayerBarPadding,
                    bottom = Dimensions.phonePageBottomClearance,
                ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(FloatingStackGap)) {
                SpeakerButton(onClick = { audioPopupOpen = !audioPopupOpen })
                MinimizeHandle(onClick = { viewModel.setMediaMinimized(true) })
            }
        }

        if (audioPopupOpen) {
            AudioPopup(
                anchor = if (onNowPlaying) AudioPopupAnchor.Transport else AudioPopupAnchor.Header,
                activeAudioRoom = audioRoom,
                volumePct = audioState.volumePct,
                joinTarget = joinTarget,
                audioJoined = audioJoined,
                onSelectAudioRoom = viewModel::selectAudioRoom,
                onVolumeChange = { value -> viewModel.setVolume(audioRoom, value) },
                onToggleAudioJoin = viewModel::toggleAudioJoin,
                onDismiss = { audioPopupOpen = false },
            )
        }
    }
}
