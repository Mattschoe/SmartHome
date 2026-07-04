package com.mattschoe.smarthome

import androidx.compose.runtime.Composable
import com.mattschoe.smarthome.ui.navigation.ApplicationNavigationHost
import com.mattschoe.smarthome.ui.theme.SmartHomeTheme

@Composable
fun App(appContainer: AppContainer) {
    SmartHomeTheme {
        ApplicationNavigationHost(appContainer)
    }
}