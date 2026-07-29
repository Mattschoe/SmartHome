package com.mattschoe.smarthome

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.mattschoe.smarthome.ui.navigation.ApplicationNavigationHost
import com.mattschoe.smarthome.ui.theme.SmartHomeTheme

@Composable
fun App(appContainer: AppContainer) {
    // One shared Coil image loader for the whole app, fetching artwork over the Ktor engine each
    // platform already bundles. Used by the album/browse tiles in the Media panel.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }
    SmartHomeTheme {
        ApplicationNavigationHost(appContainer)
    }
}
