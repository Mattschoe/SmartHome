package com.mattschoe.smarthome.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.newsreader

private val lightColorScheme = lightColorScheme()

@Composable
fun SmartHomeTheme(
    content: @Composable () -> Unit
) {
    val newsreaderFamily = FontFamily(
        //Normal
        Font(
            resource = Res.font.newsreader,
            weight = FontWeight.Normal,
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))
        ),

        //Bold
        Font(
            resource = Res.font.newsreader,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700))
        )
    )
    val baseline = Typography()
    val smartHomeTypography = Typography(
        displayLarge = baseline.displayLarge.copy(fontFamily = newsreaderFamily),
        displayMedium = baseline.displayMedium.copy(fontFamily = newsreaderFamily),
        displaySmall = baseline.displaySmall.copy(fontFamily = newsreaderFamily),
        headlineLarge = baseline.headlineLarge.copy(fontFamily = newsreaderFamily),
        headlineMedium = baseline.headlineMedium.copy(fontFamily = newsreaderFamily),
        headlineSmall = baseline.headlineSmall.copy(fontFamily = newsreaderFamily),
        titleLarge = baseline.titleLarge.copy(fontFamily = newsreaderFamily),
        titleMedium = baseline.titleMedium.copy(fontFamily = newsreaderFamily),
        titleSmall = baseline.titleSmall.copy(fontFamily = newsreaderFamily),
        bodyLarge = baseline.bodyLarge.copy(fontFamily = newsreaderFamily),
        bodyMedium = baseline.bodyMedium.copy(fontFamily = newsreaderFamily),
        bodySmall = baseline.bodySmall.copy(fontFamily = newsreaderFamily),
        labelLarge = baseline.labelLarge.copy(fontFamily = newsreaderFamily),
        labelMedium = baseline.labelMedium.copy(fontFamily = newsreaderFamily),
        labelSmall = baseline.labelSmall.copy(fontFamily = newsreaderFamily)
    )
    

    MaterialTheme(
        colorScheme = lightColorScheme,
        typography = smartHomeTypography,
        content = content
    )
}