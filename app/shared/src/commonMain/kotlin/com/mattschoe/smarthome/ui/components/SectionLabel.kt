package com.mattschoe.smarthome.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mattschoe.smarthome.ui.theme.sectionLabelStyle

/** Uppercase muted section header (APPS, WARMTH, AUDIO…). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text.uppercase(), style = sectionLabelStyle(), modifier = modifier)
}
