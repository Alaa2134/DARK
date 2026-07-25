package com.rocketpocket.car.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rocketpocket.car.R
import com.rocketpocket.car.ui.theme.CarbonBlack
import com.rocketpocket.car.ui.theme.CarbonSurface
import com.rocketpocket.car.ui.theme.NeonAmber
import com.rocketpocket.car.ui.theme.NeonCyan
import com.rocketpocket.car.ui.theme.NeonOrange
import com.rocketpocket.car.ui.theme.TextPrimary
import com.rocketpocket.car.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private const val SPLASH_DURATION_MS = 2000L

/**
 * Opening screen: the car logo, the app name, the team, and the university.
 *
 * Everything fades and slides in on a stagger so the screen assembles itself rather than
 * appearing all at once, and speed lines sweep behind the car for the two seconds it is up.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        started = true
        delay(SPLASH_DURATION_MS)
        onFinished()
    }

    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logoPulse",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "speedLines",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(CarbonSurface, CarbonBlack),
                    radius = 1400f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        SpeedLines(progress = sweep)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            AnimatedVisibility(
                visible = started,
                enter = fadeIn(tween(500)) + scaleIn(tween(600), initialScale = 0.6f),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_rocket_car),
                    contentDescription = "Rocket Pocket racing car",
                    modifier = Modifier
                        .size(128.dp)
                        .scale(pulse),
                )
            }

            StaggeredItem(visible = started, delayMillis = 180) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            StaggeredItem(visible = started, delayMillis = 320) {
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            StaggeredItem(visible = started, delayMillis = 460) {
                Box(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .width(120.dp)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(CarbonBlack, NeonOrange, CarbonBlack),
                            ),
                        ),
                )
            }

            StaggeredItem(visible = started, delayMillis = 560) {
                Text(
                    text = stringResource(R.string.team_name).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            StaggeredItem(visible = started, delayMillis = 680) {
                Text(
                    text = stringResource(R.string.university_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonAmber,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            StaggeredItem(visible = started, delayMillis = 780) {
                Text(
                    text = stringResource(R.string.faculty_name),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Fades and lifts a single line into place after [delayMillis]. */
@Composable
private fun StaggeredItem(
    visible: Boolean,
    delayMillis: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(420, delayMillis = delayMillis)) +
            slideInVertically(
                animationSpec = tween(420, delayMillis = delayMillis),
                initialOffsetY = { it / 2 },
            ),
    ) {
        content()
    }
}

/** Horizontal streaks sweeping across the background, for a sense of speed. */
@Composable
private fun SpeedLines(progress: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val streaks = 7
        for (index in 0 until streaks) {
            // Each streak starts at a different point in the cycle and wraps around.
            val phase = (progress + index / streaks.toFloat()) % 1f
            val x = size.width * phase
            val y = size.height * (0.12f + 0.11f * index)
            val length = size.width * (0.06f + 0.05f * (index % 3))
            val alpha = 0.05f + 0.06f * (1f - kotlin.math.abs(0.5f - phase) * 2f)

            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = 0f),
                        NeonCyan.copy(alpha = alpha),
                        NeonCyan.copy(alpha = 0f),
                    ),
                    startX = x,
                    endX = x + length,
                ),
                start = Offset(x, y),
                end = Offset(x + length, y),
                strokeWidth = 2f,
            )
        }
    }
}
