package com.mattschoe.smarthome.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SmartHomeColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = OnForest,
    background = SageSurface,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = InsetFill,
    onSurfaceVariant = InkSoft,
    outline = CardBorder,
    secondary = SageGreen,
    tertiary = Teal,
)

@Composable
fun SmartHomeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SmartHomeColorScheme,
        typography = smartHomeTypography(),
        content = content
    )
}
