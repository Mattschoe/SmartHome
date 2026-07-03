package com.mattschoe.smarthome.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PageShell(
    topBar: @Composable (() -> Unit) = {},
    pageContent: @Composable (PaddingValues) -> Unit
) {
    Box {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = topBar,
        ) { innerPadding -> pageContent(innerPadding) }
    }
}