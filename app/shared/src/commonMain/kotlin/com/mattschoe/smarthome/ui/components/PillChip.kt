package com.mattschoe.smarthome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.ChipIdle
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.onCalendarColor
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Pill toggle chip. Active = filled Forest accent; idle = white with sage border.
 * Serves room chips, audio speaker chips (with [leadingIcon]) and the Media/Calendar tabs.
 *
 * [contentColor] overrides the icon/text color of an **idle** pill, for the few that are actions
 * rather than selections (the artist surface's shuffle pill) and want the accent on their glyph.
 *
 * [selectedColor] overrides the fill of a **selected** pill, for the one selection that is about a
 * colour: the editor's calendar chips, which fill with the calendar's own. The text follows it
 * through [onCalendarColor], since half the palette is far too light to carry cream.
 *
 * [compact] is the landscape light card's room pills — a step down from the 44dp chip (height,
 * side padding, type) so the fixed card fits without scrolling. Everything else keeps the full size.
 */
@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: DrawableResource? = null,
    contentColor: Color? = null,
    selectedColor: Color = Forest,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(percent = 50)
    val resolvedContentColor =
        if (selected) onCalendarColor(selectedColor)
        else contentColor ?: Ink
    val base =
        if (selected) Modifier.background(selectedColor, shape)
        else Modifier.background(ChipIdle, shape).border(1.dp, CardBorder, shape)
    Row(
        modifier = modifier
            .shadow(Dimensions.pillElevation, shape)
            .clip(shape)
            .then(base)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .heightIn(min = if (compact) Dimensions.phonePillMinHeight else Dimensions.minTouch)
            .padding(horizontal = if (compact) Dimensions.phonePillHorizontalPadding else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = resolvedContentColor,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp)
            )
        }
        Text(
            text = text,
            color = resolvedContentColor,
            fontWeight = FontWeight.Medium,
            fontSize = if (compact) Dimensions.phonePillTextSize else 17.sp,
        )
    }
}
