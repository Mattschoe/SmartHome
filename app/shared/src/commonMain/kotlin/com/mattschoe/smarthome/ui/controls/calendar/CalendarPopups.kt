package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.CalendarFilters
import com.mattschoe.smarthome.data.formatEventWhen
import com.mattschoe.smarthome.data.model.CalendarEvent
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.CalendarView
import com.mattschoe.smarthome.ui.components.PopupCard
import com.mattschoe.smarthome.ui.components.PopupScrim
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.components.verticalScrollFade
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.Rose
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.calender_filled
import smarthome.shared.generated.resources.close_filled
import smarthome.shared.generated.resources.delete_filled
import smarthome.shared.generated.resources.edit_filled

/**
 * The Calendar panel's two floating cards, emitted as siblings inside the right card's
 * [com.mattschoe.smarthome.ui.components.CardContainer] rather than as dialogs over the dashboard —
 * so, like every other surface in this card, they leave the lights, dial and volume beside them live.
 * The container already clips to the card's rounded rect and insets by its content padding, which is
 * what bounds them to the card.
 *
 * Both take a `modifier` for that inset, because the phone's Calendar page floats them over a bare
 * page with no card padding to inherit — it passes the page's own margins instead. The modifier
 * insets the *card*, never the scrim: the scrim covers whatever box the popup was emitted into, which
 * on the phone is the whole page and on the tablet is the card.
 */

/**
 * What a week-view event *is*. A block in the hour grid can be a few minutes tall and a seventh of a
 * card wide — often too little for even a title — so tapping one answers that before it offers to
 * change anything. The month agenda's rows are already legible and keep opening the editor directly.
 *
 * Editing and deleting are offered only for an event this app can actually address: one on a
 * writable calendar that the backend gave a uid. Anything else is details, and says so by having no
 * actions but the close.
 */
@Composable
fun BoxScope.EventDetailPopup(
    event: CalendarEvent,
    sources: List<CalendarSource>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = sources.firstOrNull { it.id == event.sourceId }
    val canWrite = source?.canWrite == true && event.uid != null
    val color = calendarDotColor(event.sourceId, sources)

    PopupScrim(onClose)
    PopupCard(
        modifier = modifier
            .align(Alignment.Center)
            .widthIn(max = Dimensions.eventDetailMaxWidth)
            .heightIn(max = Dimensions.eventDetailMaxHeight),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (canWrite) {
                PopupAction(
                    icon = { tint -> GlyphIcon(Res.drawable.edit_filled, tint) },
                    description = "Rediger",
                    onClick = onEdit,
                )
                DeleteAction(onDelete)
                // Separates what changes the event from what merely closes the card.
                Box(
                    Modifier
                        .align(Alignment.CenterVertically)
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(Dimensions.popupIconSize)
                        .background(CardBorder),
                )
            }
            PopupAction(
                icon = { tint -> GlyphIcon(Res.drawable.close_filled, tint) },
                description = "Luk",
                onClick = onClose,
            )
        }

        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScrollFade(scroll)
                .verticalScroll(scroll),
        ) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(Dimensions.eventDetailBarWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(color),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = event.title,
                    color = Ink,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(formatEventWhen(event), color = InkSoft, fontSize = 15.sp)
            event.location?.takeIf { it.isNotBlank() }?.let { location ->
                Spacer(Modifier.height(8.dp))
                Text(location, color = InkSoft, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.calender_filled),
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(16.dp),
                )
                Text(source?.displayName ?: "Ukendt kalender", color = Muted, fontSize = 14.sp)
            }
        }
    }
}

/**
 * The delete affordance in the detail popup's action row: the same two-tap confirm the editor's
 * "Slet" pill uses ([TwoTapConfirm]), worn as an icon — armed, the trash sits on a filled Rose disc,
 * and it disarms itself if nothing follows.
 */
@Composable
private fun DeleteAction(onDelete: () -> Unit) {
    TwoTapConfirm(enabled = true, onConfirm = onDelete) { armed, onTap ->
        PopupAction(
            icon = { tint ->
                Box(
                    modifier = Modifier
                        .size(Dimensions.popupIconSize + 8.dp)
                        .clip(CircleShape)
                        .then(if (armed) Modifier.background(Rose) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Res.drawable.delete_filled, if (armed) Ink else tint)
                }
            },
            description = if (armed) "Bekræft sletning" else "Slet",
            onClick = onTap,
        )
    }
}

/**
 * Which calendars the view being looked at draws. The setting is per view, so the heading names the
 * view it applies to rather than leaving it to be inferred; the other view keeps its own.
 */
@Composable
fun BoxScope.CalendarSettingsPopup(
    view: CalendarView,
    sources: List<CalendarSource>,
    filters: CalendarFilters,
    onToggle: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hidden = filters.hidden(view)
    PopupScrim(onClose)
    PopupCard(
        modifier = modifier
            .align(Alignment.TopEnd)
            .offset(y = Dimensions.calendarSettingsTopOffset)
            .widthIn(max = Dimensions.eventDetailMaxWidth)
            .heightIn(max = Dimensions.eventDetailMaxHeight),
    ) {
        SectionLabel(
            when (view) {
                CalendarView.Month -> "Vises i månedsvisning"
                CalendarView.Week -> "Vises i ugevisning"
            },
        )
        Spacer(Modifier.height(8.dp))
        if (sources.isEmpty()) {
            Text("Ingen kalendere", color = Muted, fontSize = 15.sp)
            return@PopupCard
        }
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScrollFade(scroll)
                .verticalScroll(scroll),
        ) {
            sources.forEach { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.minTouch)
                        .clickable { onToggle(source.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(calendarDotColor(source.id, sources)),
                    )
                    Text(
                        text = source.displayName,
                        color = Ink,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    CheckboxGlyph(checked = source.id !in hidden)
                }
            }
        }
    }
}

/** One icon action in a popup's top row: a full touch target around a plain glyph. */
@Composable
private fun PopupAction(
    icon: @Composable (tint: Color) -> Unit,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(Dimensions.minTouch)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        icon(InkSoft)
    }
}

@Composable
private fun GlyphIcon(glyph: DrawableResource, tint: Color) {
    Icon(
        painter = painterResource(glyph),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(Dimensions.popupIconSize),
    )
}
