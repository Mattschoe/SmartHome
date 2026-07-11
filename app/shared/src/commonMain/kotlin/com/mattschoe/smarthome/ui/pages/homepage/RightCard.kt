package com.mattschoe.smarthome.ui.pages.homepage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.formatTrackTime
import com.mattschoe.smarthome.data.model.AudioState
import com.mattschoe.smarthome.data.model.MediaTrack
import com.mattschoe.smarthome.data.model.Panel
import com.mattschoe.smarthome.data.model.Playlist
import com.mattschoe.smarthome.data.model.RepeatMode
import com.mattschoe.smarthome.data.volumeFractionFromX
import com.mattschoe.smarthome.ui.components.CardContainer
import com.mattschoe.smarthome.ui.components.InsetSurface
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.components.bottomScrollFade
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.InsetFill
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnForest
import com.mattschoe.smarthome.ui.theme.Rose
import com.mattschoe.smarthome.ui.theme.Teal
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.calender_filled
import smarthome.shared.generated.resources.equalizer_filled
import smarthome.shared.generated.resources.media_outline
import smarthome.shared.generated.resources.music_note_filled
import smarthome.shared.generated.resources.pause_filled
import smarthome.shared.generated.resources.play_filled
import smarthome.shared.generated.resources.repeat_filled
import smarthome.shared.generated.resources.search_outline
import smarthome.shared.generated.resources.shuffle_filled
import smarthome.shared.generated.resources.skip_next_filled
import smarthome.shared.generated.resources.skip_previous_filled
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The flex-1.12 right card: a fixed Media/Calendar [PanelTabs] segmented control over a scrolling
 * content region. The Media panel swaps entirely by playback state — now-playing surface when the
 * active audio room is playing, a browse surface (Quick Picks + Keep Listening) when it is idle.
 * Width-agnostic; the `Expanded` assembly point in [Homepage.kt] assigns its width.
 */
@Composable
fun RightCard(
    panel: Panel,
    audioState: AudioState,
    playlists: List<Playlist>,
    quickPicks: List<Playlist>,
    keepListening: List<Playlist>,
    onSelectPanel: (Panel) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardContainer(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(24.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            PanelTabs(panel = panel, onSelectPanel = onSelectPanel)
            Spacer(Modifier.height(Dimensions.mediaSectionGap))
            AnimatedContent(
                targetState = panel,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally { w -> dir * w / 5 } + fadeIn()) togetherWith
                        (slideOutHorizontally { w -> -dir * w / 5 } + fadeOut())
                },
                label = "panel",
            ) { target ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .bottomScrollFade(),
                ) {
                    when (target) {
                        Panel.Media -> MediaPanel(
                            audioState = audioState,
                            playlists = playlists,
                            quickPicks = quickPicks,
                            keepListening = keepListening,
                            onTogglePlay = onTogglePlay,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onSeek = onSeek,
                            onToggleShuffle = onToggleShuffle,
                            onCycleRepeat = onCycleRepeat,
                        )
                        Panel.Calendar -> CalendarPlaceholder()
                    }
                }
            }
        }
    }
}

/**
 * Wrap-content pill segmented control switching the right card between Media and Calendar. Sunken
 * [InsetFill] track with two content-sized segments; the active one is a filled Forest pill.
 */
@Composable
private fun PanelTabs(panel: Panel, onSelectPanel: (Panel) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier.clip(shape).background(InsetFill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PanelTab(
            label = "Media",
            icon = Res.drawable.media_outline,
            selected = panel == Panel.Media,
            onClick = { onSelectPanel(Panel.Media) },
        )
        PanelTab(
            label = "Calendar",
            icon = Res.drawable.calender_filled,
            selected = panel == Panel.Calendar,
            onClick = { onSelectPanel(Panel.Calendar) },
        )
    }
}

@Composable
private fun PanelTab(label: String, icon: DrawableResource, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    val contentColor = if (selected) OnForest else Ink
    Row(
        modifier = Modifier
            .clip(shape)
            .then(if (selected) Modifier.background(Forest, shape) else Modifier)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .heightIn(min = Dimensions.minTouch)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Text(text = label, color = contentColor, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    }
}

/** Phase-7 seam: the Calendar panel body is built later; for now a centered muted placeholder. */
@Composable
private fun CalendarPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Kalender kommer snart", color = Muted, fontSize = 16.sp)
    }
}

/**
 * The Media panel content: a (visual-only) search bar over content that swaps by playback state —
 * the now-playing surface when [AudioState.nowPlaying] is set, else the idle browse surface.
 */
@Composable
private fun MediaPanel(
    audioState: AudioState,
    playlists: List<Playlist>,
    quickPicks: List<Playlist>,
    keepListening: List<Playlist>,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SearchBar()
        Spacer(Modifier.height(Dimensions.mediaSectionGap))
        val track = audioState.nowPlaying
        if (track != null) {
            NowPlayingSurface(
                track = track,
                audioState = audioState,
                playlists = playlists,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )
        } else {
            BrowseSurface(quickPicks = quickPicks, keepListening = keepListening)
        }
    }
}

/** Full-width sunken search field. Visual only — the browse_media/search wiring lands in Phase 9. */
@Composable
private fun SearchBar(modifier: Modifier = Modifier) {
    InsetSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(percent = 50),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.search_outline),
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(20.dp),
            )
            Text("Søg sange, kunstnere, podcasts", color = Muted, fontSize = 16.sp)
        }
    }
}

/** Card/glyph color for browse + queue items, cycled by list index (matches the reference rails). */
private fun browseCardColor(index: Int): Color = when (index % 3) {
    0 -> Forest
    1 -> Teal
    else -> Rose
}

/** A rounded colored tile with a centered glyph — the shared visual for album art, browse & queue thumbs. */
@Composable
private fun ArtTile(
    background: Color,
    glyph: DrawableResource,
    glyphSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    glyphTint: Color = OnForest,
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(Dimensions.innerBlockRadius)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = null,
            tint = glyphTint,
            modifier = Modifier.size(glyphSize),
        )
    }
}

/** The playing state: album art + title/subtitle/scrubber, transport, up-next queue, playlists rail. */
@Composable
private fun NowPlayingSurface(
    track: MediaTrack,
    audioState: AudioState,
    playlists: List<Playlist>,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ArtTile(
                background = Forest,
                glyph = Res.drawable.equalizer_filled,
                glyphSize = 40.dp,
                modifier = Modifier.size(Dimensions.albumArtSize),
            )
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
                    text = track.album?.let { "${track.artist} · $it" } ?: track.artist,
                    color = InkSoft,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Scrubber(
                    positionSec = audioState.positionSec,
                    durationSec = track.durationSec,
                    onSeek = onSeek,
                )
            }
        }
        Spacer(Modifier.height(Dimensions.mediaSectionGap))
        TransportRow(
            isPlaying = audioState.isPlaying,
            isShuffle = audioState.isShuffle,
            repeat = audioState.repeat,
            onTogglePlay = onTogglePlay,
            onNext = onNext,
            onPrevious = onPrevious,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
        )
        if (audioState.queue.isNotEmpty()) {
            Spacer(Modifier.height(Dimensions.mediaSectionGap))
            SectionLabel("Up next")
            Spacer(Modifier.height(12.dp))
            audioState.queue.forEachIndexed { index, item ->
                QueueRow(index = index, track = item)
                Spacer(Modifier.height(12.dp))
            }
        }
        if (playlists.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Playlists")
            Spacer(Modifier.height(12.dp))
            PlaylistRail(items = playlists)
        }
    }
}

/**
 * Drag-to-seek scrubber. The x→fraction math is the unit-tested [volumeFractionFromX]; this only
 * draws the Rose track and forwards seeks. Slider a11y mirrors the center card's VolumeSlider.
 */
@Composable
private fun Scrubber(
    positionSec: Int,
    durationSec: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentDuration by rememberUpdatedState(durationSec)
    val fraction = if (durationSec > 0) positionSec.toFloat() / durationSec else 0f

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(formatTrackTime(positionSec), color = Muted, fontSize = 13.sp)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimensions.scrubberKnobDiameter)
                .pointerInput(Unit) {
                    val inset = Dimensions.scrubberKnobDiameter.toPx() / 2f
                    detectTapGestures { pos ->
                        val f = volumeFractionFromX(pos.x, inset, size.width - inset * 2f)
                        currentOnSeek((f * currentDuration).roundToInt())
                    }
                }
                .pointerInput(Unit) {
                    val inset = Dimensions.scrubberKnobDiameter.toPx() / 2f
                    detectDragGestures { change, _ ->
                        change.consume()
                        val f = volumeFractionFromX(change.position.x, inset, size.width - inset * 2f)
                        currentOnSeek((f * currentDuration).roundToInt())
                    }
                }
                .focusable()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Søgning i nummeret"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = positionSec.toFloat(),
                        range = 0f..durationSec.coerceAtLeast(1).toFloat(),
                    )
                    setProgress { target ->
                        onSeek(target.roundToInt().coerceIn(0, durationSec))
                        true
                    }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionRight -> {
                            onSeek((positionSec + 5).coerceAtMost(durationSec)); true
                        }
                        Key.DirectionDown, Key.DirectionLeft -> {
                            onSeek((positionSec - 5).coerceAtLeast(0)); true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(Dimensions.scrubberKnobDiameter)) {
                val trackH = Dimensions.scrubberTrackHeight.toPx()
                val knobRadius = Dimensions.scrubberKnobDiameter.toPx() / 2f
                val cyy = size.height / 2f
                val laneLeft = knobRadius
                val laneWidth = (size.width - knobRadius * 2f).coerceAtLeast(0f)
                val knobX = laneLeft + laneWidth * fraction
                val corner = CornerRadius(trackH / 2f, trackH / 2f)

                drawRoundRect(
                    color = InsetFill,
                    topLeft = Offset(laneLeft, cyy - trackH / 2f),
                    size = Size(laneWidth, trackH),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = Rose,
                    topLeft = Offset(laneLeft, cyy - trackH / 2f),
                    size = Size(laneWidth * fraction, trackH),
                    cornerRadius = corner,
                )
                drawCircle(color = Color.White, radius = knobRadius, center = Offset(knobX, cyy))
                drawCircle(
                    color = Rose,
                    radius = knobRadius,
                    center = Offset(knobX, cyy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        Text(formatTrackTime(durationSec), color = Muted, fontSize = 13.sp)
    }
}

/** Centered transport controls. Shuffle/repeat tint Forest when active; the play/pause is a Forest disc. */
@Composable
private fun TransportRow(
    isPlaying: Boolean,
    isShuffle: Boolean,
    repeat: RepeatMode,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
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
        )
        TransportIcon(Res.drawable.skip_previous_filled, "Forrige", InkSoft, onPrevious)
        PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlay)
        TransportIcon(Res.drawable.skip_next_filled, "Næste", InkSoft, onNext)
        TransportIcon(
            glyph = Res.drawable.repeat_filled,
            description = "Gentag",
            tint = if (repeat != RepeatMode.Off) Forest else InkSoft,
            onClick = onCycleRepeat,
        )
    }
}

@Composable
private fun TransportIcon(glyph: DrawableResource, description: String, tint: Color, onClick: () -> Unit) {
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
            tint = tint,
            modifier = Modifier.size(Dimensions.transportIconSize),
        )
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Dimensions.transportButtonSize)
            .shadow(Dimensions.pillElevation, CircleShape)
            .clip(CircleShape)
            .background(Forest)
            .clickable(onClick = onClick)
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

/** An up-next queue row: index-colored thumb + title/artist + duration. Display-only in v1. */
@Composable
private fun QueueRow(index: Int, track: MediaTrack, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtTile(
            background = browseCardColor(index),
            glyph = Res.drawable.music_note_filled,
            glyphSize = 20.dp,
            modifier = Modifier.size(Dimensions.queueThumbSize),
        )
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTrackTime(track.durationSec), color = Muted, fontSize = 14.sp)
    }
}

/** Horizontal snapping rail of playlist/browse cards. Shared by Playlists and Keep Listening. */
@Composable
private fun PlaylistRail(items: List<Playlist>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.browseGridSpacing),
    ) {
        itemsIndexed(items) { index, item -> PlaylistCard(index = index, playlist = item) }
    }
}

@Composable
private fun PlaylistCard(index: Int, playlist: Playlist, modifier: Modifier = Modifier) {
    Column(modifier.width(Dimensions.playlistCardWidth)) {
        ArtTile(
            background = browseCardColor(index),
            glyph = Res.drawable.equalizer_filled,
            glyphSize = 36.dp,
            glyphTint = OnForest.copy(alpha = 0.9f),
            modifier = Modifier.fillMaxWidth().height(Dimensions.playlistCardHeight),
        )
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${playlist.trackCount} sange", color = Muted, fontSize = 13.sp)
    }
}

/** The idle state: a paginated Quick Picks 3×3 grid over a Keep Listening rail. No transport. */
@Composable
private fun BrowseSurface(
    quickPicks: List<Playlist>,
    keepListening: List<Playlist>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (quickPicks.isNotEmpty()) {
            SectionLabel("Quick picks")
            Spacer(Modifier.height(12.dp))
            QuickPicksPager(items = quickPicks)
        }
        if (keepListening.isNotEmpty()) {
            Spacer(Modifier.height(Dimensions.mediaSectionGap))
            SectionLabel("Keep listening")
            Spacer(Modifier.height(12.dp))
            PlaylistRail(items = keepListening)
        }
    }
}

private const val QUICK_PICKS_PER_PAGE = 9
private const val QUICK_PICKS_COLUMNS = 3

/**
 * Quick Picks as a horizontally-paged 3×3 grid with a dot indicator. Built from plain Row/Columns
 * (no LazyVerticalGrid inside the vertically-scrolling panel); the pager is height-bounded off the
 * measured square-card size so it lays out inside the scroll.
 */
@Composable
private fun QuickPicksPager(items: List<Playlist>, modifier: Modifier = Modifier) {
    val pageCount = ceil(items.size / QUICK_PICKS_PER_PAGE.toFloat()).toInt().coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val gap = Dimensions.browseGridSpacing

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardSize = (maxWidth - gap * (QUICK_PICKS_COLUMNS - 1)) / QUICK_PICKS_COLUMNS
            val gridHeight = cardSize * 3 + gap * 2
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.height(gridHeight),
                pageSpacing = gap,
            ) { page ->
                QuickPicksGridPage(
                    pageItems = items.drop(page * QUICK_PICKS_PER_PAGE).take(QUICK_PICKS_PER_PAGE),
                    startIndex = page * QUICK_PICKS_PER_PAGE,
                    gap = gap,
                )
            }
        }
        if (pageCount > 1) {
            Spacer(Modifier.height(12.dp))
            PageDots(
                current = pagerState.currentPage,
                count = pageCount,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun QuickPicksGridPage(
    pageItems: List<Playlist>,
    startIndex: Int,
    gap: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        for (row in 0 until 3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (col in 0 until QUICK_PICKS_COLUMNS) {
                    val i = row * QUICK_PICKS_COLUMNS + col
                    val item = pageItems.getOrNull(i)
                    if (item != null) {
                        ArtTile(
                            background = browseCardColor(startIndex + i),
                            glyph = Res.drawable.music_note_filled,
                            glyphSize = 34.dp,
                            glyphTint = OnForest.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        )
                    } else {
                        // Keep column alignment when the last page is partial.
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Small centered pager dots; the current page is a filled Forest dot, the rest muted. */
@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(Dimensions.pageDotSize)
                    .clip(CircleShape)
                    .background(if (i == current) Forest else Muted.copy(alpha = 0.5f)),
            )
        }
    }
}
