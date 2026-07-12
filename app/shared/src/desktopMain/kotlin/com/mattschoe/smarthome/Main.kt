package com.mattschoe.smarthome

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * Desktop entry point. Opens the shared dashboard in a resizable window sized to the 1280×800
 * reference geometry. [AppContainer] connects to Home Assistant when local.properties supplies a
 * token (via the generated BuildSecrets), otherwise falls back to the in-memory MockAdapter.
 */
fun main() = application {
    val appContainer = remember { AppContainer() }
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Smart Home",
    ) {
        App(appContainer)
    }
}
