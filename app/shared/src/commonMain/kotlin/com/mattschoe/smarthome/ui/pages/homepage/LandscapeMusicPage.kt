package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.cycle
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.data.model.Room
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.controls.media.AudioPopup
import com.mattschoe.smarthome.ui.controls.media.AudioPopupAnchor
import com.mattschoe.smarthome.ui.controls.media.ArtistSurface
import com.mattschoe.smarthome.ui.controls.media.BrowseSurface
import com.mattschoe.smarthome.ui.controls.media.MediaLayout
import com.mattschoe.smarthome.ui.controls.media.NowPlayingSurface
import com.mattschoe.smarthome.ui.controls.media.SpeakerButton
import com.mattschoe.smarthome.ui.controls.media.pendingTrack
import com.mattschoe.smarthome.ui.controls.media.rememberLatchedTrack
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Muted
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.equalizer_filled

/**
 * Landscape page 2 — Music. The same kit the tablet's right card composes, shown side by side: the
 * now-playing surface (header + transport + scrolling `UP NEXT`) on the left card, the browse
 * surface — or the artist drill-in — on the right. This is a different assembly of the kit, not a
 * third implementation: [MediaPanel]'s surface *swap* is what makes the tablet panel, and here the
 * two surfaces simply sit next to each other instead.
 *
 * The audio half of the tablet's center card hangs behind the [SpeakerButton] floating over the left
 * card, as an [AudioPopup] whose scrim covers the whole page — exactly as on the portrait Music
 * page. There is no mini player here: both cards are always visible, so `mediaMinimized` is never
 * read (and never written — portrait keeps its value).
 */
@Composable
fun LandscapeMusicPage(
    audioRoom: Room,
    audioState: AudioState,
    pendingPlay: PendingPlay?,
    pendingQueueItemId: String?,
    queueRefreshing: Boolean,
    artist: ArtistUiState?,
    searchQuery: String,
    search: SearchState,
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
    // Transient and phone-only, with no consumer outside this page — the same precedent as the
    // portrait page's popup. `activeAudioRoom` itself stays ViewModel-owned, per the CORE RULE.
    var audioPopupOpen by remember { mutableStateOf(false) }

    // A pending play paints the tapped item as the (loading) now-playing track right away — the same
    // synthesis [MediaPanel] uses, so the loading feedback is identical on both surfaces.
    val track = pendingTrack(pendingPlay) ?: rememberLatchedTrack(audioState.nowPlaying)
    val hasTrack = pendingPlay != null || audioState.nowPlaying != null

    BoxWithConstraints(modifier.fillMaxSize()) {
        // The cards are equal and fill the page's padded area, so this is the left card's width —
        // what the floating disc and its popup hang off.
        val cardWidth = (maxWidth - Dimensions.phoneCardGap) / 2
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.phoneCardGap),
        ) {
            CardContainer(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                // Tighter horizontal inset than the light/utility cards: art and text pack the same
                // width, and the queue wants the room.
                contentPadding = PaddingValues(
                    horizontal = Dimensions.phoneCardPaddingH,
                    vertical = Dimensions.phoneCardPadding,
                ),
            ) {
                if (hasTrack && track != null) {
                    // The tablet now-playing header is art-beside-title-and-scrubber, which is exactly
                    // what this card wants — the phone header's large centered art would tower over it.
                    // The full five-button transport stays: no compact variant, per the current design.
                    // The whole card scrolls as one column (header included), so the queue is reachable
                    // even when the card is short, and scrolling pushes the art out of sight — the
                    // landscape arrangement of this surface.
                    NowPlayingSurface(
                        track = track,
                        audioState = audioState,
                        loading = pendingPlay != null,
                        queueRefreshing = queueRefreshing,
                        pendingQueueItemId = pendingQueueItemId,
                        onTogglePlay = { viewModel.togglePlay(audioRoom) },
                        onNext = { viewModel.next(audioRoom) },
                        onPrevious = { viewModel.previous(audioRoom) },
                        onSeek = { sec -> viewModel.seek(audioRoom, sec) },
                        onToggleShuffle = { viewModel.setShuffle(audioRoom, !audioState.isShuffle) },
                        onCycleRepeat = { viewModel.setRepeat(audioRoom, audioState.repeat.cycle()) },
                        // Both queue intents resolve the active audio room inside the ViewModel.
                        onPlayQueueItem = viewModel::playQueueItem,
                        onMoveQueueItem = viewModel::moveQueueItem,
                        layout = MediaLayout.Tablet,
                        wholeSurfaceScroll = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.equalizer_filled),
                            contentDescription = null,
                            tint = Muted,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Der spilles ikke musik", color = Muted, fontSize = 15.sp)
                    }
                }
            }
            CardContainer(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(
                    horizontal = Dimensions.phoneCardPaddingH,
                    vertical = Dimensions.phoneCardPadding,
                ),
            ) {
                if (artist != null) {
                    ArtistSurface(
                        artist = artist,
                        onBack = viewModel::closeArtist,
                        onPlayTopHit = viewModel::playTopHits,
                        onShuffle = viewModel::shuffleArtist,
                        onPlay = viewModel::play,
                        bottomInset = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // No floating mini player over a landscape card, so no bottom inset to reserve —
                    // and no header trigger: the speaker disc floats over the other card instead.
                    BrowseSurface(
                        query = searchQuery,
                        search = search,
                        source = musicSource,
                        playlists = playlists,
                        quickPicks = quickPicks,
                        mixedForYou = mixedForYou,
                        spotifyPlaylists = spotifyPlaylists,
                        spotifyRecentlyPlayed = spotifyRecentlyPlayed,
                        onQueryChange = viewModel::setSearchQuery,
                        onPlay = viewModel::play,
                        onEnqueue = viewModel::enqueue,
                        onOpenArtist = viewModel::openArtist,
                        bottomInset = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // The audio disc floats over the now-playing card's bottom end, inside its content padding —
        // positioned off the page's own corner because the card only spans half of it. The popup it
        // drops is a sibling of the card Row (not of the card), so its scrim covers the whole page.
        SpeakerButton(
            onClick = { audioPopupOpen = !audioPopupOpen },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(
                    x = cardWidth - Dimensions.minTouch - Dimensions.phoneCardPaddingH,
                    y = -Dimensions.phoneCardPadding,
                ),
        )
        if (audioPopupOpen) {
            AudioPopup(
                anchor = AudioPopupAnchor.Card(cardWidth),
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
