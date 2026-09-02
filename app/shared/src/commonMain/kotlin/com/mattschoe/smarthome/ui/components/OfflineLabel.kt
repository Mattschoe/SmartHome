package com.mattschoe.smarthome.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.ui.theme.Muted

/**
 * The one word every surface uses to say the home is out of reach — the calendar drawing a cached
 * window, the checklist holding writes that have not gone out yet.
 *
 * A muted word beside whatever titles the surface, never a banner: nothing on screen is wrong, it
 * simply has not been agreed with the home yet, and the rows that are waiting already say so
 * themselves by being drawn back ([com.mattschoe.smarthome.ui.theme.Dimensions.pendingWriteAlpha]).
 */
@Composable
fun OfflineLabel(modifier: Modifier = Modifier) {
    Text(
        text = "Offline",
        color = Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}
