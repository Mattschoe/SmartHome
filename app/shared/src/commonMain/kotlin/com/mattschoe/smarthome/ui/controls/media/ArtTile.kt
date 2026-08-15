package com.mattschoe.smarthome.ui.controls.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.mattschoe.smarthome.ui.theme.ArtScrim
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.OnArt
import com.mattschoe.smarthome.ui.theme.OnForest
import com.mattschoe.smarthome.ui.theme.Rose
import com.mattschoe.smarthome.ui.theme.Teal
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Card/glyph color for browse + queue items, cycled by list index (matches the reference rails). */
fun browseCardColor(index: Int): Color = when (index % 3) {
    0 -> Forest
    1 -> Teal
    else -> Rose
}

/**
 * A rounded tile — the shared visual for album art, browse & queue thumbs. The colored-glyph tile is
 * the base; when [artworkUrl] is set, real cover art is layered on top (cropped to fill) and the glyph
 * shows through only while loading or on failure, so a missing/broken image degrades gracefully. A
 * [label] prints over the art on a bottom scrim, for tiles that carry no caption of their own.
 */
@Composable
fun ArtTile(
    background: Color,
    glyph: DrawableResource,
    glyphSize: Dp,
    modifier: Modifier = Modifier,
    glyphTint: Color = OnForest,
    artworkUrl: String? = null,
    label: String? = null,
) {
    val shape = RoundedCornerShape(Dimensions.innerBlockRadius)
    Box(
        modifier = modifier.clip(shape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = null,
            tint = glyphTint,
            modifier = Modifier.size(glyphSize),
        )
        if (artworkUrl != null) {
            // Decode to a small multiple of the tile instead of the source's full resolution: handing
            // the GPU a 720–1280px bitmap for a ~100px tile collapses it in one step and grinds any
            // text in the cover into grain. Coil's decoder does the bulk of the downscale, leaving a
            // gentle final step.
            BoxWithConstraints(Modifier.matchParentSize()) {
                val bounded = constraints.hasBoundedWidth || constraints.hasBoundedHeight
                val tilePx = maxOf(
                    if (constraints.hasBoundedWidth) constraints.maxWidth else 0,
                    if (constraints.hasBoundedHeight) constraints.maxHeight else 0,
                )
                val requestSize =
                    if (bounded && tilePx > 0) {
                        val target = tilePx * Dimensions.artOversample
                        coil3.size.Size(target, target)
                    } else {
                        coil3.size.Size.ORIGINAL
                    }
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(artworkUrl)
                        .size(requestSize)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // High-quality (mipmapped) resampling — the default FilterQuality.Low aliases the
                    // cover down into the tile as uniform grain.
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize().clip(shape),
                )
            }
        }
        if (label != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(Dimensions.artLabelHeight)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, ArtScrim))),
            ) {
                Text(
                    text = label,
                    color = OnArt,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(Dimensions.artLabelPadding),
                )
            }
        }
    }
}
