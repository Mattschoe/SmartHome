package com.mattschoe.smarthome

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    val appContainer = remember { AppContainer() }
    App(appContainer)
}