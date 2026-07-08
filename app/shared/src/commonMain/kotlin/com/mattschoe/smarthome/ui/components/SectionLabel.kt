package com.mattschoe.smarthome.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.ui.theme.sectionLabelStyle

/**
 * Uppercase muted section header (APPS, WARMTH, AUDIO…). [fontSize] defaults to the standard 11sp;
 * callers that need an emphasized header (e.g. the center card's AUDIO) can pass a larger size.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 11.sp) {
    Text(text = text.uppercase(), style = sectionLabelStyle(fontSize), modifier = modifier)
}
