package com.mattschoe.smarthome.ui.controls.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.ui.components.PillChip
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.components.verticalScrollFade
import com.mattschoe.smarthome.ui.pages.homepage.ArtistUiState
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnForest
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.arrow_back_filled
import smarthome.shared.generated.resources.music_note_filled
import smarthome.shared.generated.resources.shuffle_filled

/**
 * The artist drill-in, reached by tapping an artist search result: a back arrow, the artist header
 * with its shuffle pill, then their top hits (a paged grid, like Quick Picks) and albums (a rail,
 * like Playlists). Tapping a hit plays the list **from there** — hence [onPlayTopHit] taking the
 * tapped index rather than the item — while an album is a plain play target.
 *
 * The header renders from the tile that opened the surface, so it is up before the catalogue is; the
 * two sections are what [artist] is still loading (or failed to fetch). Either list may come back
 * empty, in which case its section is simply omitted.
 */
@Composable
fun ArtistSurface(
    artist: ArtistUiState,
    onBack: () -> Unit,
    onPlayTopHit: (Int) -> Unit,
    onShuffle: () -> Unit,
    onPlay: (BrowseItem) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    layout: MediaLayout = MediaLayout.Tablet,
) {
    val columns = layout.browseGridColumns
    val scroll = rememberScrollState()
    Column(modifier.fillMaxSize().verticalScrollFade(scroll).verticalScroll(scroll)) {
        Box(
            modifier = Modifier
                // The glyph sits centred in its touch target; bleed the whole box out by that inset
                // so the arrow lines up with the content edge instead of 10dp inside it.
                .offset(x = -Dimensions.backButtonInset)
                .size(Dimensions.backButtonSize)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "Tilbage" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.arrow_back_filled),
                contentDescription = null,
                tint = InkSoft,
                modifier = Modifier.size(Dimensions.backIconSize),
            )
        }
        Spacer(Modifier.height(12.dp))
        // The Row takes the art's height so the text column has one to distribute: name pinned to the
        // top of the portrait, shuffle pill to its bottom.
        Row(
            Modifier.fillMaxWidth().height(Dimensions.artistArtSize),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ArtTile(
                background = Forest,
                glyph = Res.drawable.music_note_filled,
                glyphSize = 40.dp,
                glyphTint = OnForest.copy(alpha = 0.9f),
                modifier = Modifier.size(Dimensions.artistArtSize),
                artworkUrl = artist.artist.artworkUrl,
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    artist.artist.name,
                    color = Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                // An action, not a selection — it never latches, so it stays idle-styled and takes the
                // accent on its glyph instead.
                PillChip(
                    text = "Bland",
                    selected = false,
                    onClick = onShuffle,
                    leadingIcon = Res.drawable.shuffle_filled,
                    contentColor = Forest,
                    modifier = Modifier.wrapContentWidth(),
                )
            }
        }
        Spacer(Modifier.height(Dimensions.mediaSectionGap))
        when (artist) {
            is ArtistUiState.Loading -> SearchStatus { CircularProgressIndicator(color = Forest) }
            is ArtistUiState.Failed -> SearchStatus {
                Text("Kunne ikke hente kunstneren", color = Muted, fontSize = 15.sp)
            }
            is ArtistUiState.Ready -> {
                if (artist.topTracks.isNotEmpty()) {
                    SectionLabel("Top hits")
                    Spacer(Modifier.height(12.dp))
                    // Flattened on the phone for the same reason the browse shelves are — the page
                    // pager already owns the horizontal drag a nested pager would need.
                    if (layout.allowsPagedShelves) {
                        QuickPicksPager(
                            items = artist.topTracks,
                            columns = columns,
                            onSelect = { index, _ -> onPlayTopHit(index) },
                        )
                    } else {
                        FlatBrowseGrid(
                            items = artist.topTracks,
                            columns = columns,
                            onSelect = { index, _ -> onPlayTopHit(index) },
                        )
                    }
                }
                if (artist.albums.isNotEmpty()) {
                    Spacer(Modifier.height(Dimensions.mediaSectionGap))
                    SectionLabel("Albums")
                    Spacer(Modifier.height(12.dp))
                    PlaylistRail(items = artist.albums, onPlay = onPlay)
                }
            }
        }
        Spacer(Modifier.height(bottomInset))
    }
}
