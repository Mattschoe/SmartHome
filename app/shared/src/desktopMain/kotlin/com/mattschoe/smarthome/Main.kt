package com.mattschoe.smarthome

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * Desktop entry point. Opens the shared dashboard in a resizable window sized to the 1280×800
 * reference geometry. Uses the default [AppContainer] (in-memory [com.mattschoe.smarthome.data.MockAdapter]).
 */
fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Smart Home",
    ) {
        App(AppContainer())
    }
}
