package com.pasoseguro.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pasoseguro.app.data.PreferencesRepository
import com.pasoseguro.app.screens.HomeScreen
import com.pasoseguro.app.screens.FeatureScreen
import com.pasoseguro.app.screens.NavigateScreen
import com.pasoseguro.app.screens.ContactsScreen
import com.pasoseguro.app.screens.ConfigScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    repository: PreferencesRepository,
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Home.route,
        enterTransition  = {
            slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))
        },
        exitTransition   = {
            slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(200))
        },
        popEnterTransition = {
            slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300))
        },
        popExitTransition  = {
            slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200))
        },
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        Feature.entries.forEach { feature ->
            composable(feature.route) {
                when (feature) {
                    Feature.NAVIGATE -> NavigateScreen(navController = navController)
                    Feature.CONTACTS -> ContactsScreen(navController = navController)
                    Feature.CONFIG   -> ConfigScreen(
                        navController = navController,
                        repository    = repository,
                    )
                    else             -> FeatureScreen(feature = feature, navController = navController)
                }
            }
        }
    }
}
