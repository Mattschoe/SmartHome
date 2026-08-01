package com.mattschoe.smarthome.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mattschoe.smarthome.data.model.Warmth

val SageSurface = Color(0xFFB2C488)
val Card = Color(0xFFFAF8EA)
val CardBorder = Color(0xFFA7BB7C)
val InsetFill = Color(0xFFECE6CF)


val Ink = Color(0xFF23301C)
val InkSoft = Color(0xFF5C6650)
val Muted = Color(0xFFA7A88C)

val SageGreen = Color(0xFF839958)
val Teal = Color(0xFF105666)
val Rose = Color(0xFFD3968C)
val WarmAmber = Color(0xFFE0A24E)

val Forest = Color(0xFF0A3323)
val OnForest = Color(0xFFF6EEC7)

val ChipIdle = Color(0xFFFFFFFF)

/**
 * Agenda dot colors, one per calendar — so his, hers, the shared one and the work roster read apart
 * at a glance. Assigned by the calendar's position in `CalendarState.sources` (which is ordered by
 * entity id, so a calendar keeps its color), wrapping if a home ever has more calendars than colors.
 */
val CalendarDotColors: List<Color> = listOf(Forest, Teal, Rose, WarmAmber, SageGreen)

/**
 * Home Assistant's own per-calendar color, folded onto this dashboard's palette. HA stores a *name*
 * from its fixed token set (`amber`, `primary`, `dark-grey`, …) on the calendar entity; its raw
 * Material hexes read far louder than the cream/sage surface, so each name maps to the nearest
 * palette family instead — the calendar's HA color still drives its dot, it just stays on-palette.
 * Several HA names share a family (`amber` and `orange` both land on [WarmAmber]). An unset or
 * unknown name yields `null`, falling the dot back to its position-assigned [CalendarDotColors] entry.
 */
fun haCalendarColor(name: String?): Color? = when (name) {
    "primary", "blue", "light-blue", "cyan", "indigo", "teal", "blue-grey", "purple", "deep-purple" -> Teal
    "red", "pink", "deep-orange" -> Rose
    "accent", "amber", "orange", "yellow", "brown" -> WarmAmber
    "green", "light-green", "lime" -> SageGreen
    "black" -> Forest
    "grey", "dark-grey", "disabled" -> InkSoft
    "light-grey", "white" -> Muted
    else -> null
}

/**
 * Above which relative luminance a fill needs dark text on it rather than cream. Placed so the
 * calendar palette splits where it actually reads: [Teal] and [Forest] carry [OnForest], while
 * [Rose], [WarmAmber], [Muted] and [SageGreen] — all far too light for cream — carry [Ink].
 */
private const val OnColorLuminanceCut = 0.25f

/** Legible ink over a calendar's own colour when it fills a pill — cream on dark, ink on light. */
fun onCalendarColor(color: Color): Color =
    if (color.luminance() > OnColorLuminanceCut) Ink else OnForest

/** Legibility scrim under a label printed over cover art, and the label's own color. */
val ArtScrim = Color(0xE6000000)
val OnArt = Color(0xFFFFFFFF)

/**
 * The wash a popup lays over the card behind it — [Ink] at a fraction, so the card is still legibly
 * *there* rather than blacked out. The popups are bounded to the right card, and the lights and dial
 * beside it stay live and untouched.
 */
val PopupScrim = Ink.copy(alpha = 0.28f)

val WarmthCandle = Color(0xFFFF7E00)
val WarmthWarm = Color(0xFFFF932C)
val WarmthSoft = Color(0xFFFFA957)
val WarmthNeutral = Color(0xFFFFD1A3)
val WarmthCool = Color(0xFFD6E8F5)

/** The dial arc/knob/growth-shape color when a room's light is off. */
val WarmthOffMuted = Color(0xFFCDC7AB)

/** Maps a [Warmth] preset to its dial/swatch color token. */
fun Warmth.color(): Color = when (this) {
    Warmth.Candle -> WarmthCandle
    Warmth.Warm -> WarmthWarm
    Warmth.Soft -> WarmthSoft
    Warmth.Neutral -> WarmthNeutral
    Warmth.Cool -> WarmthCool
}
