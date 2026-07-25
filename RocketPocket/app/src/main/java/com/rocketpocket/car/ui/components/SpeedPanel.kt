package com.rocketpocket.car.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.rocketpocket.car.ui.theme.CarbonOutline
import com.rocketpocket.car.ui.theme.CarbonSurfaceHigh
import com.rocketpocket.car.ui.theme.NeonCyan
import com.rocketpocket.car.ui.theme.NeonRed
import com.rocketpocket.car.ui.theme.TextSecondary

/**
 * The speedometer plus its `-` / `+` trim buttons, forming the right half of the dashboard.
 */
@Composable
fun SpeedPanel(
    speed: Int,
    maxSpeed: Int,
    connected: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // This panel occupies the left half of the dashboard, so the trim buttons stack down its
    // outer (left) edge where the left thumb rests, and the gauge sits inboard of them. The
    // right thumb is left free for the drive pad.
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpeedTrimButton(
                icon = Icons.Filled.Add,
                contentDescription = "Increase speed",
                accent = NeonCyan,
                onClick = onIncrease,
            )
            SpeedTrimButton(
                icon = Icons.Filled.Remove,
                contentDescription = "Decrease speed",
                accent = NeonRed,
                onClick = onDecrease,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "SPEED",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
            Speedometer(
                speed = speed,
                maxSpeed = maxSpeed,
                connected = connected,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
            )
            Text(
                text = "STEP 15",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

/**
 * A tap-to-trim button. Unlike the drive buttons this one fires once per tap, so a plain
 * [clickable] with a press animation is the right gesture.
 */
@Composable
private fun SpeedTrimButton(
    icon: ImageVector,
    contentDescription: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 900f),
        label = "speedTrimScale",
    )

    Box(
        modifier = modifier
            .size(66.dp)
            .scale(scale)
            .background(
                if (pressed) accent.copy(alpha = 0.22f) else CarbonSurfaceHigh,
                CircleShape,
            )
            .border(if (pressed) 2.dp else 1.dp, if (pressed) accent else CarbonOutline, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accent,
            modifier = Modifier.size(32.dp),
        )
    }
}
