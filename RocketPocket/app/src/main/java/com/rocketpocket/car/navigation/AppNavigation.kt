package com.rocketpocket.car.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rocketpocket.car.ui.screens.ControlScreen
import com.rocketpocket.car.ui.screens.SplashScreen
import com.rocketpocket.car.viewmodel.ControlViewModel

object Route {
    const val SPLASH = "splash"
    const val CONTROL = "control"
}

/**
 * Splash for two seconds, then the dashboard.
 *
 * The splash entry is popped inclusively so the back button from the control screen leaves the
 * app rather than replaying the intro mid-race.
 */
@Composable
fun AppNavigation(viewModel: ControlViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Route.SPLASH) {
        composable(Route.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Route.CONTROL) {
                        popUpTo(Route.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.CONTROL) {
            ControlScreen(viewModel = viewModel)
        }
    }
}
