package com.rocketpocket.car.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.rocketpocket.car.ui.theme.CarbonOutline
import com.rocketpocket.car.ui.theme.CarbonSurfaceHigh
import com.rocketpocket.car.ui.theme.NeonAmber
import com.rocketpocket.car.ui.theme.NeonGreen
import com.rocketpocket.car.ui.theme.NeonRed
import com.rocketpocket.car.ui.theme.TextDisabled
import com.rocketpocket.car.ui.theme.TextPrimary
import com.rocketpocket.car.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f
private const val TICK_COUNT = 9

/**
 * Circular PWM gauge, 0 to 255.
 *
 * The needle animates towards each new reading rather than snapping, so a `SPEED:` line
 * arriving from the ESP32 sweeps smoothly and stays readable while driving.
 */
@Composable
fun Speedometer(
    speed: Int,
    maxSpeed: Int,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val fraction = if (maxSpeed <= 0) 0f else (speed.toFloat() / maxSpeed).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "speedometerSweep",
    )

    val arcColor = gaugeColor(animatedFraction)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.085f
            val diameter = min(size.width, size.height) - stroke
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)
            val centre = Offset(size.width / 2f, size.height / 2f)
            val radius = diameter / 2f

            // Unfilled track.
            drawArc(
                color = CarbonSurfaceHigh,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Tick marks around the dial.
            for (tick in 0..TICK_COUNT) {
                val tickFraction = tick.toFloat() / TICK_COUNT
                val angleRadians = ((START_ANGLE + SWEEP_ANGLE * tickFraction) * PI / 180f).toFloat()
                val outer = radius - stroke * 0.75f
                val inner = outer - stroke * (if (tick % 2 == 0) 0.85f else 0.5f)
                drawLine(
                    color = if (tickFraction <= animatedFraction) arcColor else CarbonOutline,
                    start = Offset(
                        x = centre.x + outer * cos(angleRadians),
                        y = centre.y + outer * sin(angleRadians),
                    ),
                    end = Offset(
                        x = centre.x + inner * cos(angleRadians),
                        y = centre.y + inner * sin(angleRadians),
                    ),
                    strokeWidth = stroke * 0.18f,
                    cap = StrokeCap.Round,
                )
            }

            // Filled portion.
            if (animatedFraction > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(NeonGreen, NeonAmber, NeonRed, NeonGreen),
                        center = centre,
                    ),
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP_ANGLE * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            // Needle.
            val needleAngle = ((START_ANGLE + SWEEP_ANGLE * animatedFraction) * PI / 180f).toFloat()
            val needleLength = radius - stroke * 1.6f
            drawLine(
                color = if (connected) arcColor else TextDisabled,
                start = centre,
                end = Offset(
                    x = centre.x + needleLength * cos(needleAngle),
                    y = centre.y + needleLength * sin(needleAngle),
                ),
                strokeWidth = stroke * 0.34f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = if (connected) arcColor else TextDisabled,
                radius = stroke * 0.42f,
                center = centre,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Text(
                text = speed.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = if (connected) TextPrimary else TextDisabled,
            )
            Text(
                text = "PWM  0 - $maxSpeed",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

/** Green through amber to red as the duty cycle climbs. */
private fun gaugeColor(fraction: Float): Color = when {
    fraction < 0.5f -> lerp(NeonGreen, NeonAmber, fraction / 0.5f)
    else -> lerp(NeonAmber, NeonRed, (fraction - 0.5f) / 0.5f)
}
