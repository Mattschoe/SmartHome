package com.mattschoe.smarthome

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.mattschoe.smarthome.data.IosNetworkMonitor

fun MainViewController() = ComposeUIViewController {
    val networkMonitor = remember { IosNetworkMonitor() }
    val appContainer = remember(networkMonitor) { AppContainer(networkMonitor = networkMonitor) }
    App(appContainer)
}