package com.mattschoe.smarthome.ui.theme

import androidx.compose.ui.graphics.Color
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
