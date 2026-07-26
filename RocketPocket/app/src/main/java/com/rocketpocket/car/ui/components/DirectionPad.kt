package com.rocketpocket.car.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rocketpocket.car.bluetooth.Command
import com.rocketpocket.car.ui.theme.NeonCyan
import com.rocketpocket.car.ui.theme.NeonOrange
import com.rocketpocket.car.ui.theme.NeonRed
import com.rocketpocket.car.ui.theme.NeonRedDeep
import com.rocketpocket.car.ui.theme.TextPrimary

/**
 * One cell of the 3x3 pad. The arrow icon is a single upward arrow rotated into place,
 * which keeps all eight directions visually identical.
 */
private data class Direction(
    val command: Char,
    val label: String,
    val rotation: Float,
    val accent: Color,
)

private val Directions = listOf(
    Direction(Command.FORWARD_LEFT, "FWD LEFT", -45f, NeonOrange),
    Direction(Command.FORWARD, "FORWARD", 0f, NeonCyan),
    Direction(Command.FORWARD_RIGHT, "FWD RIGHT", 45f, NeonOrange),
    Direction(Command.LEFT, "LEFT", -90f, NeonCyan),
    Direction(Command.RIGHT, "RIGHT", 90f, NeonCyan),
    Direction(Command.BACKWARD_LEFT, "BACK LEFT", -135f, NeonOrange),
    Direction(Command.BACKWARD, "BACKWARD", 180f, NeonCyan),
    Direction(Command.BACKWARD_RIGHT, "BACK RIGHT", 135f, NeonOrange),
)

private fun direction(command: Char): Direction = Directions.first { it.command == command }

/**
 * The eight-way drive pad with the emergency stop occupying the centre cell.
 *
 * Rows read: forward-left / forward / forward-right, then left / STOP / right,
 * then back-left / backward / back-right.
 */
@Composable
fun DirectionPad(
    enabled: Boolean,
    onDirectionPressed: (Char) -> Unit,
    onDirectionReleased: () -> Unit,
    onEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A 3x3 grid of nine equal cells is square by nature. Letting it stretch to fill a wide
    // tablet half turned every button into a flat rectangle, so the grid is pinned to a square
    // sized by the available height and centred in whatever width it is given.
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        PadGrid(
            enabled = enabled,
            onDirectionPressed = onDirectionPressed,
            onDirectionReleased = onDirectionReleased,
            onEmergencyStop = onEmergencyStop,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f, matchHeightConstraintsFirst = true),
        )
    }
}

@Composable
private fun PadGrid(
    enabled: Boolean,
    onDirectionPressed: (Char) -> Unit,
    onDirectionReleased: () -> Unit,
    onEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PadRow(
            left = Command.FORWARD_LEFT,
            right = Command.FORWARD_RIGHT,
            centreCommand = Command.FORWARD,
            enabled = enabled,
            onDirectionPressed = onDirectionPressed,
            onDirectionReleased = onDirectionReleased,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DirectionCell(
                direction = direction(Command.LEFT),
                enabled = enabled,
                onDirectionPressed = onDirectionPressed,
                onDirectionReleased = onDirectionReleased,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            )
            EmergencyStopButton(
                onStop = onEmergencyStop,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            )
            DirectionCell(
                direction = direction(Command.RIGHT),
                enabled = enabled,
                onDirectionPressed = onDirectionPressed,
                onDirectionReleased = onDirectionReleased,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            )
        }

        PadRow(
            left = Command.BACKWARD_LEFT,
            right = Command.BACKWARD_RIGHT,
            centreCommand = Command.BACKWARD,
            enabled = enabled,
            onDirectionPressed = onDirectionPressed,
            onDirectionReleased = onDirectionReleased,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun PadRow(
    left: Char,
    centreCommand: Char,
    right: Char,
    enabled: Boolean,
    onDirectionPressed: (Char) -> Unit,
    onDirectionReleased: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(left, centreCommand, right).forEach { command ->
            DirectionCell(
                direction = direction(command),
                enabled = enabled,
                onDirectionPressed = onDirectionPressed,
                onDirectionReleased = onDirectionReleased,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DirectionCell(
    direction: Direction,
    enabled: Boolean,
    onDirectionPressed: (Char) -> Unit,
    onDirectionReleased: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ControlButton(
        label = direction.label,
        icon = Icons.Filled.ArrowUpward,
        accent = direction.accent,
        enabled = enabled,
        iconRotation = direction.rotation,
        onPress = { onDirectionPressed(direction.command) },
        onRelease = onDirectionReleased,
        modifier = modifier,
    )
}

/**
 * The emergency stop. It is always live — even while disconnected, where the view model
 * answers with "Connect to Rocket Pocket first" — because a stop must never be swallowed.
 */
@Composable
fun EmergencyStopButton(
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val currentOnStop by rememberUpdatedState(onStop)
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 900f),
        label = "emergencyStopScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .background(
                brush = Brush.radialGradient(
                    listOf(if (pressed) NeonRed else NeonRedDeep, NeonRedDeep.copy(alpha = 0.85f)),
                ),
                shape = CircleShape,
            )
            .border(
                width = if (pressed) 4.dp else 3.dp,
                color = NeonRed,
                shape = CircleShape,
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnStop()

                    val up = waitForUpOrCancellation()
                    up?.consume()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PanTool,
                contentDescription = "Emergency Stop",
                tint = TextPrimary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "EMERGENCY\nSTOP",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
