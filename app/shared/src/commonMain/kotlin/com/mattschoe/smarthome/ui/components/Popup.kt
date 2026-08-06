package com.mattschoe.smarthome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.mattschoe.smarthome.ui.theme.Card
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.PopupScrim

/**
 * The two pieces every floating card in this app is built from: the plate and the wash behind it.
 *
 * They are plain [Box] children of whatever surface opens them — a card, a portrait page — not
 * `androidx.compose.ui.window.Popup`, so a popup stays bounded by the surface it belongs to and
 * everything beside that surface keeps running.
 */

/**
 * The cream plate a popup sits on: the card treatment one step up, floating *above* a surface that
 * already carries a shadow, so its elevation is the mini player's rather than [Dimensions.cardElevation].
 */
@Composable
fun PopupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Dimensions.innerBlockRadius)
    Column(
        modifier = modifier
            .shadow(Dimensions.miniPlayerElevation, shape)
            .clip(shape)
            .background(Card)
            .border(1.dp, CardBorder, shape)
            .padding(16.dp),
        content = content,
    )
}

/**
 * The wash over the surface behind an open popup, and the tap-outside that closes it. No ripple: it is
 * a dismissal, not a control, and a ripple across the whole surface would read as one.
 */
@Composable
fun BoxScope.PopupScrim(onClose: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .matchParentSize()
            .background(PopupScrim)
            .clickable(interactionSource = interaction, indication = null, onClick = onClose),
    )
}
