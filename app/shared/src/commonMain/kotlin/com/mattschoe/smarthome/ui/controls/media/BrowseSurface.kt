package com.mattschoe.smarthome.ui.controls.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.model.BrowseItem
import com.mattschoe.smarthome.data.model.BrowseKind
import com.mattschoe.smarthome.data.model.MusicSource
import com.mattschoe.smarthome.ui.components.InsetSurface
import com.mattschoe.smarthome.ui.components.PageIndicator
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.components.verticalScrollFade
import com.mattschoe.smarthome.ui.pages.homepage.SearchState
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnForest
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.close_filled
import smarthome.shared.generated.resources.equalizer_filled
import smarthome.shared.generated.resources.music_note_filled
import smarthome.shared.generated.resources.search_outline
import kotlin.math.ceil

/**
 * The browse state: the search field over either the [source]'s shelves ([browseShelvesFor]) or, once
 * [search] leaves [SearchState.Idle], the results grid in their place — every way into the library
 * lives here, since this is the surface reached to pick something. Search is deliberately **not**
 * scoped by [source]: the toggle splits browsing, while a search still spans both providers.
 * No transport: it shows either when nothing is playing or with the player collapsed, and in the
 * latter case [bottomInset] reserves the height the floating [MiniPlayerBar] covers.
 *
 * [headerTrailing] takes width off the search field for a control beside it — the phone page puts its
 * speaker button there, since it has no card header to hang one from. The tablet passes nothing and
 * keeps the full-width bar.
 */
@Composable
fun BrowseSurface(
    query: String,
    search: SearchState,
    source: MusicSource,
    playlists: List<BrowseItem>,
    quickPicks: List<BrowseItem>,
    mixedForYou: List<BrowseItem>,
    spotifyPlaylists: List<BrowseItem>,
    spotifyRecentlyPlayed: List<BrowseItem>,
    onQueryChange: (String) -> Unit,
    onPlay: (BrowseItem) -> Unit,
    onOpenArtist: (BrowseItem) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    headerTrailing: (@Composable () -> Unit)? = null,
) {
    val columns = Dimensions.browseGridColumns
    val scroll = rememberScrollState()
    Column(modifier.fillMaxSize().verticalScrollFade(scroll).verticalScroll(scroll)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchBar(query = query, onQueryChange = onQueryChange, modifier = Modifier.weight(1f))
            headerTrailing?.invoke()
        }
        Spacer(Modifier.height(Dimensions.mediaSectionGap))
        when (search) {
            SearchState.Idle -> {
                // Like the search grid: an artist tile drills in, everything else plays.
                val onSelect: (Int, BrowseItem) -> Unit = { _, item ->
                    if (item.kind == BrowseKind.Artist) onOpenArtist(item) else onPlay(item)
                }
                val shelves = browseShelvesFor(
                    source, playlists, quickPicks, mixedForYou, spotifyPlaylists, spotifyRecentlyPlayed,
                ).filter { it.items.isNotEmpty() }
                shelves.forEachIndexed { index, shelf ->
                    // The search bar already left a gap, so only the shelves after the first add one.
                    if (index > 0) Spacer(Modifier.height(Dimensions.mediaSectionGap))
                    SectionLabel(shelf.label)
                    Spacer(Modifier.height(12.dp))
                    when (shelf) {
                        is BrowseShelf.PagedGrid ->
                            QuickPicksPager(items = shelf.items, columns = columns, onSelect = onSelect)
                        is BrowseShelf.Grid ->
                            FlatBrowseGrid(items = shelf.items, columns = columns, onSelect = onSelect)
                        is BrowseShelf.Rail -> PlaylistRail(items = shelf.items, onPlay = onPlay)
                    }
                }
            }
            SearchState.Searching -> SearchStatus {
                CircularProgressIndicator(color = Forest)
            }
            SearchState.Failed -> SearchStatus {
                Text("Søgningen fejlede", color = Muted, fontSize = 15.sp)
            }
            is SearchState.Results ->
                if (search.items.isEmpty()) {
                    SearchStatus { Text("Ingen resultater", color = Muted, fontSize = 15.sp) }
                } else {
                    // Results mix kinds: an artist hit drills in, everything else plays.
                    FlatBrowseGrid(
                        items = search.items,
                        columns = columns,
                        onSelect = { _, item ->
                            if (item.kind == BrowseKind.Artist) onOpenArtist(item) else onPlay(item)
                        },
                    )
                }
        }
        Spacer(Modifier.height(bottomInset))
    }
}

/**
 * The sunken search field over the music library, filling whatever width the header row leaves it.
 * The text is owned by the ViewModel (which
 * debounces the search behind it), so this only mirrors it into a local [TextFieldValue] for the
 * cursor — the sync effect fires solely on the programmatic clear (playing a result), never while
 * typing. The row is [Dimensions.searchFieldRowHeight] tall so the trailing clear button is a full
 * touch target without growing the pill.
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue(query)) }
    LaunchedEffect(query) {
        if (query != field.text) field = TextFieldValue(query, TextRange(query.length))
    }

    InsetSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(percent = 50),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = Dimensions.searchFieldPadV),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Dimensions.searchFieldRowHeight),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.search_outline),
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(20.dp),
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (field.text.isEmpty()) {
                    Text("Søg sange, kunstnere, podcasts", color = Muted, fontSize = 16.sp)
                }
                BasicTextField(
                    value = field,
                    onValueChange = { value -> field = value; onQueryChange(value.text) },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(Forest),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(Dimensions.searchFieldRowHeight)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .semantics { contentDescription = "Ryd søgning" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.close_filled),
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Horizontal snapping rail of playlist/browse cards. Shared by Playlists and Mixed for you. */
@Composable
internal fun PlaylistRail(items: List<BrowseItem>, onPlay: (BrowseItem) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.browseGridSpacing),
    ) {
        itemsIndexed(items) { index, item -> PlaylistCard(index = index, playlist = item, onPlay = onPlay) }
    }
}

@Composable
private fun PlaylistCard(index: Int, playlist: BrowseItem, onPlay: (BrowseItem) -> Unit, modifier: Modifier = Modifier) {
    // Tapping plays the item as radio; a card without a uri (rare) stays inert.
    val playable = if (playlist.uri != null) Modifier.clickable { onPlay(playlist) } else Modifier
    Column(
        modifier
            .width(Dimensions.playlistCardWidth)
            .then(playable)
            .semantics { if (playlist.uri != null) contentDescription = "Afspil ${playlist.name}" },
    ) {
        ArtTile(
            background = browseCardColor(index),
            glyph = Res.drawable.equalizer_filled,
            glyphSize = 36.dp,
            glyphTint = OnForest.copy(alpha = 0.9f),
            modifier = Modifier.fillMaxWidth().height(Dimensions.playlistCardHeight),
            artworkUrl = playlist.artworkUrl,
        )
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        playlist.subtitle?.let {
            Text(it, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One browse shelf: its label, its tiles, and the shape it renders in. */
private sealed interface BrowseShelf {
    val label: String
    val items: List<BrowseItem>

    /** A horizontally-paged grid — for a pool deep enough to fill pages. */
    data class PagedGrid(override val label: String, override val items: List<BrowseItem>) : BrowseShelf

    /** A grid sized to its content, for a short list a paged one would leave mostly empty. */
    data class Grid(override val label: String, override val items: List<BrowseItem>) : BrowseShelf

    data class Rail(override val label: String, override val items: List<BrowseItem>) : BrowseShelf
}

/**
 * The shelves for [source], in order. YouTube Music leads with its algorithmic grid; Spotify serves
 * no recommendation feed of its own, so it leads with playlists — as a grid, since they are the
 * headline shelf on that side rather than a secondary rail — and keeps the YT Music grid pinned at
 * the bottom rather than ending on nothing. Empty shelves are dropped by the caller.
 */
private fun browseShelvesFor(
    source: MusicSource,
    playlists: List<BrowseItem>,
    quickPicks: List<BrowseItem>,
    mixedForYou: List<BrowseItem>,
    spotifyPlaylists: List<BrowseItem>,
    spotifyRecentlyPlayed: List<BrowseItem>,
): List<BrowseShelf> = when (source) {
    MusicSource.YtMusic -> listOf(
        BrowseShelf.PagedGrid("Quick picks", quickPicks),
        BrowseShelf.Rail("Playlists", playlists),
        BrowseShelf.Rail("Mixed for you", mixedForYou),
    )
    MusicSource.Spotify -> listOf(
        BrowseShelf.PagedGrid("Playlists", spotifyPlaylists),
        BrowseShelf.Grid("Recently played", spotifyRecentlyPlayed),
        BrowseShelf.PagedGrid("Quick picks", quickPicks),
    )
}

/** Spinner / "no hits" / failure line, on a reserved height so the surface doesn't jump between them. */
@Composable
internal fun SearchStatus(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(Dimensions.searchStatusHeight),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** How many rows a quick-picks page holds — the pager's page is [QUICK_PICKS_ROWS] × its columns. */
private const val QUICK_PICKS_ROWS = 3

/**
 * Quick Picks as a horizontally-paged grid with a dot indicator. Built from plain Row/Columns (no
 * LazyVerticalGrid inside the vertically-scrolling panel); the pager is height-bounded off the
 * measured square-card size so it lays out inside the scroll.
 */
@Composable
internal fun QuickPicksPager(
    items: List<BrowseItem>,
    columns: Int,
    onSelect: (index: Int, item: BrowseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val perPage = columns * QUICK_PICKS_ROWS
    val pageCount = ceil(items.size / perPage.toFloat()).toInt().coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val gap = Dimensions.browseGridSpacing

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardSize = (maxWidth - gap * (columns - 1)) / columns
            val gridHeight = cardSize * QUICK_PICKS_ROWS + gap * (QUICK_PICKS_ROWS - 1)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.height(gridHeight),
                pageSpacing = gap,
            ) { page ->
                BrowseGrid(
                    items = items.drop(page * perPage).take(perPage),
                    rows = QUICK_PICKS_ROWS,
                    columns = columns,
                    startIndex = page * perPage,
                    gap = gap,
                    onSelect = onSelect,
                )
            }
        }
        if (pageCount > 1) {
            Spacer(Modifier.height(12.dp))
            PageIndicator(
                state = pagerState,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** A [BrowseGrid] sized to hold every one of [items] — the un-paged shelf and the search results. */
@Composable
internal fun FlatBrowseGrid(
    items: List<BrowseItem>,
    columns: Int,
    onSelect: (index: Int, item: BrowseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    BrowseGrid(
        items = items,
        rows = ceil(items.size / columns.toFloat()).toInt(),
        columns = columns,
        startIndex = 0,
        gap = Dimensions.browseGridSpacing,
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * The shared tile grid: [rows] rows of [columns] square [ArtTile]s, each captioned by the title
 * printed over its art. Drawn by a Quick Picks page (a fixed three rows), the search results (as many
 * rows as hits) and the artist surface's top hits. [startIndex] offsets the color cycle so a later
 * Quick Picks page continues the previous page's colors instead of restarting them.
 *
 * [onSelect] receives the tile's **global** index (already offset by [startIndex]) alongside the item,
 * because what a tap means differs per call site: playing the item, opening an artist, or playing the
 * whole list from that position.
 */
@Composable
private fun BrowseGrid(
    items: List<BrowseItem>,
    rows: Int,
    columns: Int,
    startIndex: Int,
    gap: Dp,
    onSelect: (index: Int, item: BrowseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (col in 0 until columns) {
                    val i = row * columns + col
                    val item = items.getOrNull(i)
                    if (item != null) {
                        val isArtist = item.kind == BrowseKind.Artist
                        val clickable =
                            if (item.uri != null) Modifier.clickable { onSelect(startIndex + i, item) }
                            else Modifier
                        ArtTile(
                            background = browseCardColor(startIndex + i),
                            glyph = Res.drawable.music_note_filled,
                            glyphSize = 34.dp,
                            glyphTint = OnForest.copy(alpha = 0.9f),
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .then(clickable)
                                .semantics {
                                    if (item.uri != null) {
                                        contentDescription =
                                            if (isArtist) "Vis ${item.name}" else "Afspil ${item.name}"
                                    }
                                },
                            artworkUrl = item.artworkUrl,
                            // The tiles carry no caption below them, so the title rides the art itself —
                            // it works on the colored-glyph fallback too, so an artless pick is still named.
                            label = item.name,
                        )
                    } else {
                        // Keep column alignment when the last row is partial.
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
