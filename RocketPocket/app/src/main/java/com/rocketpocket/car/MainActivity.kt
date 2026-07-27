package com.rocketpocket.car

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rocketpocket.car.navigation.AppNavigation
import com.rocketpocket.car.ui.theme.RocketPocketTheme
import com.rocketpocket.car.viewmodel.ControlViewModel

/**
 * The single activity. Locked to landscape in the manifest.
 *
 * The lifecycle overrides are the outer safety net: whenever the dashboard stops being the
 * thing on screen, the car is told to stop. `ControlScreen` registers its own lifecycle
 * observer as well, so a stop is sent even if this activity is bypassed.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ControlViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A dropped screen mid-race would be a lost heat.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Immersive dashboard: hide the system bars but let a swipe bring them back.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            RocketPocketTheme {
                AppNavigation(viewModel = viewModel)
            }
        }
    }

    /** The controls are no longer in front of the user — stop the car. */
    override fun onPause() {
        super.onPause()
        viewModel.onAppLeftForeground()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppLeftForeground()
    }

    /** Stop synchronously, then let the view model close the socket. */
    override fun onDestroy() {
        viewModel.onActivityDestroyed(isFinishing)
        super.onDestroy()
    }
}
