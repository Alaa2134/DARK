package com.rocketpocket.car.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.rocketpocket.car.ui.theme.CarbonOutline
import com.rocketpocket.car.ui.theme.CarbonSurface
import com.rocketpocket.car.ui.theme.CarbonSurfaceHigh
import com.rocketpocket.car.ui.theme.TextDisabled
import com.rocketpocket.car.ui.theme.TextPrimary

/**
 * A hold-to-drive button.
 *
 * [awaitFirstDown] is the Compose equivalent of `ACTION_DOWN` and [waitForUpOrCancellation]
 * covers both `ACTION_UP` (it returns the change) and `ACTION_CANCEL` (it returns null when the
 * finger slides off the button). Both outcomes call [onRelease], so there is no path through
 * this gesture that leaves the car driving.
 *
 * The gesture stays active even when [enabled] is false: the view model answers a press with
 * "Connect to Rocket Pocket first" instead of silently ignoring it.
 */
@Composable
fun ControlButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    iconRotation: Float = 0f,
    iconSize: Int = 34,
) {
    val haptics = LocalHapticFeedback.current
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)

    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "controlButtonScale",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            pressed && enabled -> accent.copy(alpha = 0.30f)
            enabled -> CarbonSurfaceHigh
            else -> CarbonSurface
        },
        animationSpec = tween(120),
        label = "controlButtonContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            pressed && enabled -> accent
            enabled -> CarbonOutline
            else -> CarbonOutline.copy(alpha = 0.5f)
        },
        animationSpec = tween(120),
        label = "controlButtonBorder",
    )
    val contentColor = when {
        pressed && enabled -> accent
        enabled -> TextPrimary
        else -> TextDisabled
    }

    Box(
        modifier = modifier
            .scale(scale)
            .background(
                brush = Brush.verticalGradient(
                    listOf(containerColor, containerColor.copy(alpha = 0.65f)),
                ),
                shape = RoundedCornerShape(18.dp),
            )
            .border(
                width = if (pressed && enabled) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp),
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    // ACTION_DOWN
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnPress()

                    // Returns the change on ACTION_UP, or null on ACTION_CANCEL.
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    pressed = false
                    currentOnRelease()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier
                    .size(iconSize.dp)
                    .graphicsLayer { rotationZ = iconRotation },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}
