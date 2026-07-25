package com.rocketpocket.car.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The dashboard is always dark — a racing instrument panel should never flip to a light
 * theme mid-run, so the system setting is deliberately ignored.
 */
private val RocketPocketColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CarbonBlack,
    primaryContainer = CarbonSurfaceHigh,
    onPrimaryContainer = NeonCyan,
    secondary = NeonOrange,
    onSecondary = CarbonBlack,
    tertiary = NeonGreen,
    onTertiary = CarbonBlack,
    background = CarbonBlack,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = CarbonSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = CarbonOutline,
    error = NeonRed,
    onError = TextPrimary,
)

@Composable
fun RocketPocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RocketPocketColorScheme,
        typography = RocketPocketTypography,
        content = content,
    )
}
