package com.mattschoe.smarthome.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class PageNavigation {
    @Serializable object Home : PageNavigation()
}