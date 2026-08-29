package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.CalendarFilters
import com.mattschoe.smarthome.data.CalendarPrefs
import com.mattschoe.smarthome.data.EVENT_DURATIONS
import com.mattschoe.smarthome.data.formatDuration
import com.mattschoe.smarthome.data.formatReminderOffset
import com.mattschoe.smarthome.data.model.CalendarPaletteColor
import com.mattschoe.smarthome.data.model.CalendarSource
import com.mattschoe.smarthome.data.model.CalendarView
import com.mattschoe.smarthome.data.model.ReminderRule
import com.mattschoe.smarthome.data.model.ReminderRules
import com.mattschoe.smarthome.ui.components.SectionLabel
import com.mattschoe.smarthome.ui.components.verticalScrollFade
import com.mattschoe.smarthome.ui.pages.homepage.CalendarSettingsRoute
import com.mattschoe.smarthome.ui.pages.homepage.depth
import com.mattschoe.smarthome.ui.theme.CardBorder
import com.mattschoe.smarthome.ui.theme.ChipIdle
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.InkSoft
import com.mattschoe.smarthome.ui.theme.Muted
import com.mattschoe.smarthome.ui.theme.OnForest
import com.mattschoe.smarthome.ui.theme.color
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.arrow_back_filled
import smarthome.shared.generated.resources.notifications_filled

/**
 * The Calendar panel's settings, as a surface of its own rather than the floating card they used to
 * be — and as a **menu** rather than one page: the home's calendars, and one calendar's own settings,
 * each taking the whole panel in turn. The levels exist so that the next setting is a row somewhere
 * rather than another fold on an already full page; [onBack] steps up exactly one of them, and out of
 * the settings altogether from the list.
 *
 * Two of the four per-calendar settings belong to **this device** and two to the home, which is the
 * one thing worth knowing before changing anything here: the colour and the default length are this
 * screen's alone (see [CalendarPrefs]), visibility is per view on this device, and the standing
 * reminder is written to Home Assistant for every device at once. Nothing on the surface says so in
 * as many words — the distinction only matters when two devices disagree, and by then the reminder is
 * the only one that *has* to agree.
 *
 * There is no "Gem": every control writes on the tap, so leaving by the back arrow can never discard
 * anything.
 */
@Composable
fun CalendarSettingsSurface(
    /** Which level is showing. */
    route: CalendarSettingsRoute,
    /** Which view's visibility is being edited — the setting is per view, so this names it. */
    view: CalendarView,
    sources: List<CalendarSource>,
    filters: CalendarFilters,
    /** This device's chosen colours and lengths — what the swatch and length rows show selected. */
    prefs: CalendarPrefs,
    /** The home's rules; each calendar's standing default is read out of these. */
    reminders: ReminderRules,
    onToggleVisible: (String) -> Unit,
    onSetColor: (String, CalendarPaletteColor) -> Unit,
    onSetDuration: (String, Int) -> Unit,
    /** Set or clear a calendar's standing reminder; `null` clears it. */
    onSetReminderDefault: (String, Int?) -> Unit,
    onNavigate: (CalendarSettingsRoute) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A calendar removed in Home Assistant while its page is open leaves the route pointing at
    // nothing; the list it came from is what is left of it, rather than an empty page.
    val level =
        if (route is CalendarSettingsRoute.Calendar && sources.none { it.id == route.sourceId }) {
            CalendarSettingsRoute.Calendars
        } else {
            route
        }
    // Whether the open calendar's reminder picker is showing. The level itself is the ViewModel's,
    // but this is one step inside a page and never outlives it, so it stays here.
    var pickingReminder by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SettingsHeader(title = levelTitle(level, sources), onBack = onBack)
            Spacer(Modifier.height(4.dp))
            AnimatedContent(
                targetState = level,
                modifier = Modifier.fillMaxSize(),
                // Deeper slides in from the trailing edge and back from the leading one, the way the
                // right card's own panel swap reads. `using null` for the same reason it does: every
                // level fills the panel, so there is no container height to animate.
                transitionSpec = {
                    val dir = if (targetState.depth > initialState.depth) 1 else -1
                    (slideInHorizontally { w -> dir * w / 5 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally { w -> -dir * w / 5 } + fadeOut(tween(120))) using null
                },
                label = "calendar-settings-level",
            ) { target ->
                when (target) {
                    CalendarSettingsRoute.Calendars -> CalendarsLevel(
                        sources = sources,
                        onOpen = { onNavigate(CalendarSettingsRoute.Calendar(it.id)) },
                    )
                    is CalendarSettingsRoute.Calendar -> {
                        // Resolved inside the transition, so it can be gone on the frame the level
                        // animates out — that frame draws nothing rather than the wrong calendar.
                        sources.firstOrNull { it.id == target.sourceId }?.let { source ->
                            CalendarLevel(
                                source = source,
                                sources = sources,
                                view = view,
                                visible = source.id !in filters.hidden(view),
                                onToggleVisible = { onToggleVisible(source.id) },
                                selectedColor = prefs.colorById[source.id],
                                onSetColor = { onSetColor(source.id, it) },
                                duration = prefs.durationFor(source.id),
                                onSetDuration = { onSetDuration(source.id, it) },
                                reminderDefault = reminders.byCalendar[source.id],
                                onPickReminder = { pickingReminder = true },
                            )
                        }
                    }
                }
            }
        }

        // A sibling of the column, not a child: the picker floats over the whole surface and must not
        // slide with the page that opened it — the same split the event editor's pickers use.
        val openSource = (level as? CalendarSettingsRoute.Calendar)
            ?.let { open -> sources.firstOrNull { it.id == open.sourceId } }
        if (pickingReminder && openSource != null) {
            ReminderPickerPopup(
                // A calendar's default has nothing above it to inherit from, so "Ingen" is the
                // absence of one rather than an override of anything.
                selected = ReminderRule(reminders.byCalendar[openSource.id]),
                calendarDefault = null,
                showInherit = false,
                title = "Standard for ${openSource.displayName}",
                onPick = { picked ->
                    onSetReminderDefault(openSource.id, picked?.offsetMin)
                    pickingReminder = false
                },
                onDismiss = { pickingReminder = false },
            )
        }
    }
}

/** What the header prints for a level — a calendar's page is titled with the calendar. */
private fun levelTitle(route: CalendarSettingsRoute, sources: List<CalendarSource>): String =
    when (route) {
        CalendarSettingsRoute.Calendars -> "Kalendere"
        is CalendarSettingsRoute.Calendar ->
            sources.firstOrNull { it.id == route.sourceId }?.displayName ?: "Kalendere"
    }

/** The home's calendars, each in its own colour, to drill into — the settings' own front page. */
@Composable
private fun CalendarsLevel(sources: List<CalendarSource>, onOpen: (CalendarSource) -> Unit) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScrollFade(scroll).verticalScroll(scroll)) {
        if (sources.isEmpty()) {
            Text("Ingen kalendere", color = Muted, fontSize = 15.sp)
            return@Column
        }
        sources.forEach { source ->
            SettingsMenuRow(
                title = source.displayName,
                onClick = { onOpen(source) },
                leading = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(calendarDotColor(source.id, sources)),
                    )
                },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** One calendar, whole: whether it is drawn here, its colour, its event length and its reminder. */
@Composable
private fun CalendarLevel(
    source: CalendarSource,
    sources: List<CalendarSource>,
    view: CalendarView,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    selectedColor: CalendarPaletteColor?,
    onSetColor: (CalendarPaletteColor) -> Unit,
    duration: Int,
    onSetDuration: (Int) -> Unit,
    reminderDefault: Int?,
    onPickReminder: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScrollFade(scroll).verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The visibility of this calendar in the view that was open behind the settings — the setting
        // is per view, so the label names which one rather than leaving it to be guessed.
        VisibilityRow(
            label = when (view) {
                CalendarView.Month -> "Vises i månedsvisning"
                CalendarView.Week -> "Vises i ugevisning"
            },
            calendarName = source.displayName,
            visible = visible,
            dotColor = calendarDotColor(source.id, sources),
            onToggle = onToggleVisible,
        )

        SectionLabel("Farve")
        ColorSwatches(selected = selectedColor, onSelect = onSetColor)

        SectionLabel("Længde på nye begivenheder")
        DurationPills(selected = duration, onSelect = onSetDuration)

        SectionLabel("Standardpåmindelse")
        ReminderDefaultPill(offsetMin = reminderDefault, onClick = onPickReminder)
        Spacer(Modifier.height(8.dp))
    }
}

/** Back and the level's name — every control on this surface writes as it is touched. */
@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
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
        Text(text = title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A row that leads somewhere: an optional glyph, the name, and the chevron. */
@Composable
private fun SettingsMenuRow(
    title: String,
    onClick: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.settingsRowHeight)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Åbn $title" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Text(text = title, color = Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Chevron()
    }
}

/**
 * The drill-in arrow. The back arrow mirrored rather than a glyph of its own: it is the same arrow
 * pointing the other way, and the icon set has no second one to add for it.
 */
@Composable
private fun Chevron() {
    Icon(
        painter = painterResource(Res.drawable.arrow_back_filled),
        contentDescription = null,
        tint = Muted,
        modifier = Modifier.size(20.dp).graphicsLayer { scaleX = -1f },
    )
}

/** Whether this calendar is drawn in the view behind the settings. The whole row is the target. */
@Composable
private fun VisibilityRow(
    label: String,
    calendarName: String,
    visible: Boolean,
    dotColor: Color,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.settingsRowHeight)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription =
                    if (visible) "Skjul $calendarName" else "Vis $calendarName"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        Text(text = label, color = Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(Dimensions.minTouch), contentAlignment = Alignment.Center) {
            CheckboxGlyph(checked = visible)
        }
    }
}

/**
 * The colours a calendar may be given. A [FlowRow] rather than a scrolling row: every option should
 * be reachable without a gesture that competes with the page pager behind it on the phone, so ten
 * swatches wrap to a second line instead of running off the edge.
 */
@Composable
private fun ColorSwatches(selected: CalendarPaletteColor?, onSelect: (CalendarPaletteColor) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CalendarPaletteColor.entries.forEach { option ->
            ColorSwatch(
                option = option,
                selected = option == selected,
                onSelect = { onSelect(option) },
            )
        }
    }
}

/**
 * One colour, with the warmth dial's selection treatment: a concentric ring drawn *around* a
 * constant-size fill, so picking one grows its footprint without shrinking the colour itself.
 */
@Composable
private fun ColorSwatch(option: CalendarPaletteColor, selected: Boolean, onSelect: () -> Unit) {
    val swatchColor = option.color()
    Box(
        modifier = Modifier
            .sizeIn(minWidth = Dimensions.minTouch, minHeight = Dimensions.minTouch)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .semantics { contentDescription = option.name },
        contentAlignment = Alignment.Center,
    ) {
        val ringModifier =
            if (selected) {
                Modifier
                    .border(Dimensions.calendarColorHaloRingWidth, swatchColor, CircleShape)
                    .padding(Dimensions.calendarColorHaloRingWidth + Dimensions.calendarColorHaloGap)
            } else {
                Modifier
            }
        Box(ringModifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(Dimensions.calendarColorSwatchDiameter)
                    .shadow(Dimensions.swatchElevation, CircleShape)
                    .clip(CircleShape)
                    .background(swatchColor),
            )
        }
    }
}

/** How long a new event on this calendar lasts. Wraps for the same reason the swatches do. */
@Composable
private fun DurationPills(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EVENT_DURATIONS.forEach { minutes ->
            SmallPill(
                text = formatDuration(minutes),
                selected = minutes == selected,
                onClick = { onSelect(minutes) },
            )
        }
    }
}

/**
 * The calendar's standing reminder. This is where a **read-only** calendar — a work roster — gets
 * reminders at all: there is no event on it to hang one off.
 */
@Composable
private fun ReminderDefaultPill(offsetMin: Int?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .heightIn(min = Dimensions.minTouch)
            .clip(shape)
            .border(1.dp, CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = "Standardpåmindelse" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.notifications_filled),
            contentDescription = null,
            tint = if (offsetMin == null) Muted else InkSoft,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = offsetMin?.let(::formatReminderOffset) ?: "Ingen",
            color = if (offsetMin == null) Muted else Ink,
            fontSize = 15.sp,
        )
    }
}

/**
 * A compact selectable pill for the length row. Smaller than
 * [com.mattschoe.smarthome.ui.components.PillChip] on purpose: eight of these have to fit inside the
 * phone's card, where the full-size chip wraps to four lines.
 */
@Composable
private fun SmallPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .heightIn(min = Dimensions.minTouch)
            .clip(shape)
            .then(
                if (selected) Modifier.background(Forest, shape)
                else Modifier.background(ChipIdle, shape).border(1.dp, CardBorder, shape)
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) OnForest else Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
