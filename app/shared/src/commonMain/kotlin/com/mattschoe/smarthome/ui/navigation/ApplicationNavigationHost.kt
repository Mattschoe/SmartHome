package com.mattschoe.smarthome.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mattschoe.smarthome.AppContainer
import com.mattschoe.smarthome.ui.pages.homepage.Homepage
import com.mattschoe.smarthome.ui.pages.homepage.HomepageViewModel

@Composable
fun ApplicationNavigationHost(
    appContainer: AppContainer,
    navController: NavHostController = rememberNavController(),
    startPage: PageNavigation = PageNavigation.Home
) {
    NavHost(
        navController = navController,
        startDestination = startPage,
        modifier = Modifier.fillMaxSize()
    ) {
        //Homepage
        composable<PageNavigation.Home> {
            val viewModel = viewModel<HomepageViewModel> {
                HomepageViewModel(
                    adapter = appContainer.homeAdapter
                )
            }
            Homepage(
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}