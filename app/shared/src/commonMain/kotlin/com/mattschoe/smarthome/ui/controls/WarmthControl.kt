package com.mattschoe.smarthome.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.model.Warmth
import com.mattschoe.smarthome.ui.theme.ChipIdle
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.HairlineBorder
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.color
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.check_filled

/**
 * Five warmth-preset circles — the tablet arrangement, where the row sits inline under the dial.
 * Selecting one recolors the dial and turns the light on.
 */
@Composable
fun WarmthSwatches(
    selected: Warmth,
    onSelect: (Warmth) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Center-align vertically: a selected swatch's ring makes its box taller, and without this the row
    // top-aligns children so that extra height hangs *below* — reading as the circle sliding "down"
    // rather than scaling up in place. Centered, the growth spreads symmetrically around the row axis.
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Warmth.entries.forEach { warmth ->
            WarmthSwatch(warmth = warmth, selected = warmth == selected, onSelect = { onSelect(warmth) })
        }
    }
}

@Composable
private fun WarmthSwatch(warmth: Warmth, selected: Boolean, onSelect: () -> Unit) {
    val swatchColor = warmth.color()
    Box(
        modifier = Modifier
            .sizeIn(minWidth = Dimensions.minTouch, minHeight = Dimensions.minTouch)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .semantics { contentDescription = warmth.displayName },
        contentAlignment = Alignment.Center,
    ) {
        // Selected swatches gain a concentric outer ring (ring + gap) drawn *around* a constant-size
        // fill, so the selection grows the footprint without shrinking the colored circle.
        val ringModifier =
            if (selected) {
                Modifier
                    .border(Dimensions.warmthHaloRingWidth, swatchColor, CircleShape)
                    .padding(Dimensions.warmthHaloRingWidth + Dimensions.warmthHaloGap)
            } else {
                Modifier
            }
        Box(ringModifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(Dimensions.warmthSwatchDiameter)
                    .shadow(Dimensions.swatchElevation, CircleShape)
                    .clip(CircleShape)
                    .background(swatchColor),
            )
        }
    }
}

/**
 * The same five presets as a full-width vertical list — the phone arrangement, where there is height
 * to spend and no room for the swatch row's horizontal spread. Same state, same effect as
 * [WarmthSwatches]: selecting a row recolors the dial and turns the light on.
 *
 * The selected row reads as raised purely through white-on-cream plus a border in its own warmth
 * color; it carries **no** elevation and is the same height as its siblings, matching the mock.
 */
@Composable
fun WarmthRows(
    selected: Warmth,
    onSelect: (Warmth) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimensions.warmthRowGap),
    ) {
        Warmth.entries.forEach { warmth ->
            WarmthRow(warmth = warmth, selected = warmth == selected, onSelect = { onSelect(warmth) })
        }
    }
}

@Composable
private fun WarmthRow(warmth: Warmth, selected: Boolean, onSelect: () -> Unit) {
    val warmthColor = warmth.color()
    val shape = RoundedCornerShape(Dimensions.warmthRowRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.warmthRowHeight)
            .clip(shape)
            .background(warmthColor)
            .border(
                width = if (selected) Dimensions.warmthRowSelectedBorder else Dimensions.warmthRowBorder,
                color = if (selected) Color.Black.copy(alpha = 0.3f).compositeOver(warmthColor) else Color.Transparent,
                shape = shape,
            )
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = Dimensions.warmthRowInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

    }
}
