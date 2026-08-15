package com.mattschoe.smarthome.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.newsreader

/**
 * The Newsreader [FontFamily], memoized per call-site. `remember`'s calculation lambda is not a
 * composable context, and the [Font] constructor is, so the memo is a state slot filled on first
 * composition — the result is the same stable identity across recompositions.
 */
@Composable
fun newsreaderFamily(): FontFamily {
    var memoized by remember { mutableStateOf<FontFamily?>(null) }
    if (memoized == null) {
        memoized = FontFamily(
            Font(
                resource = Res.font.newsreader,
                weight = FontWeight.Normal, // 400
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(FontVariation.weight(400)),
            ),
            Font(
                resource = Res.font.newsreader,
                weight = FontWeight.Medium, // 500
                variationSettings = FontVariation.Settings(FontVariation.weight(500)),
            ),
            Font(
                resource = Res.font.newsreader,
                weight = FontWeight.SemiBold, // 600
                variationSettings = FontVariation.Settings(FontVariation.weight(600)),
            ),
            Font(
                resource = Res.font.newsreader,
                weight = FontWeight.Bold, // 700
                variationSettings = FontVariation.Settings(FontVariation.weight(700)),
            ),
        )
    }
    return memoized ?: FontFamily.Default
}

/**
 * The dashboard [Typography], memoized per call-site like [newsreaderFamily]. The typography
 * [androidx.compose.runtime.CompositionLocal] keeps a stable identity, so Text-based composables
 * can skip instead of re-reading it on every recomposition.
 */
@Composable
fun smartHomeTypography(): Typography {
    var memoized by remember { mutableStateOf<Typography?>(null) }
    if (memoized == null) {
        val family = newsreaderFamily()
        val baseline = Typography()
        memoized = Typography(
            displayLarge = baseline.displayLarge.copy(fontFamily = family),
            displayMedium = baseline.displayMedium.copy(fontFamily = family),
            displaySmall = baseline.displaySmall.copy(fontFamily = family),
            headlineLarge = baseline.headlineLarge.copy(fontFamily = family),
            headlineMedium = baseline.headlineMedium.copy(fontFamily = family),
            headlineSmall = baseline.headlineSmall.copy(fontFamily = family),
            titleLarge = baseline.titleLarge.copy(fontFamily = family),
            titleMedium = baseline.titleMedium.copy(fontFamily = family),
            titleSmall = baseline.titleSmall.copy(fontFamily = family),
            bodyLarge = baseline.bodyLarge.copy(fontFamily = family),
            bodyMedium = baseline.bodyMedium.copy(fontFamily = family),
            bodySmall = baseline.bodySmall.copy(fontFamily = family),
            labelLarge = baseline.labelLarge.copy(fontFamily = family),
            labelMedium = baseline.labelMedium.copy(fontFamily = family),
            labelSmall = baseline.labelSmall.copy(fontFamily = family),
        )
    }
    return memoized ?: Typography()
}

@Composable
fun sectionLabelStyle(fontSize: TextUnit = 11.sp): TextStyle = TextStyle(
    fontFamily = newsreaderFamily(),
    fontSize = fontSize,
    fontWeight = FontWeight.Medium,
    letterSpacing = 1.5.sp,
    color = Muted,
)
