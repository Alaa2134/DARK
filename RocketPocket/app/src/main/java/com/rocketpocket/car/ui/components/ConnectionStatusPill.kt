package com.rocketpocket.car.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rocketpocket.car.bluetooth.ConnectionState
import com.rocketpocket.car.ui.theme.StatusConnected
import com.rocketpocket.car.ui.theme.StatusConnecting
import com.rocketpocket.car.ui.theme.StatusDisconnected

/**
 * The connection indicator: red when disconnected, orange while connecting (pulsing),
 * green once the SPP link is up.
 */
@Composable
fun ConnectionStatusPill(
    state: ConnectionState,
    deviceName: String?,
    modifier: Modifier = Modifier,
) {
    val statusColor: Color = when (state) {
        ConnectionState.DISCONNECTED -> StatusDisconnected
        ConnectionState.CONNECTING -> StatusConnecting
        ConnectionState.CONNECTED -> StatusConnected
    }
    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(220),
        label = "statusColor",
    )

    val label = when (state) {
        ConnectionState.DISCONNECTED -> "DISCONNECTED"
        ConnectionState.CONNECTING -> "CONNECTING..."
        ConnectionState.CONNECTED -> deviceName?.uppercase()?.let { "CONNECTED  •  $it" } ?: "CONNECTED"
    }

    val icon = when (state) {
        ConnectionState.DISCONNECTED -> Icons.Filled.BluetoothDisabled
        ConnectionState.CONNECTING -> Icons.Filled.Bluetooth
        ConnectionState.CONNECTED -> Icons.Filled.BluetoothConnected
    }

    // Only the connecting state pulses; a steady pill is easier to read at a glance.
    val transition = rememberInfiniteTransition(label = "statusPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "statusPulseAlpha",
    )
    val contentAlpha = if (state == ConnectionState.CONNECTING) pulse else 1f

    Row(
        modifier = modifier
            .background(animatedColor.copy(alpha = 0.14f), RoundedCornerShape(50))
            .border(1.dp, animatedColor.copy(alpha = 0.65f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .alpha(contentAlpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = animatedColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = animatedColor,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
