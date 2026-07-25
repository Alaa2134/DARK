package com.rocketpocket.car.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rocketpocket.car.ui.theme.CarbonOutline
import com.rocketpocket.car.ui.theme.CarbonSurfaceHigh
import com.rocketpocket.car.ui.theme.NeonAmber
import com.rocketpocket.car.ui.theme.NeonCyan
import com.rocketpocket.car.ui.theme.TextDisabled
import com.rocketpocket.car.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Live wire monitor: the last byte sent to the car and the last line it sent back.
 *
 * This exists so the car can be diagnosed on the phone, with no laptop and no Serial Monitor.
 * Press a button and read TX: if the character is right, the app is fine and any wrong movement
 * is wiring; if it is wrong or missing, the fault is on this side.
 */
@Composable
fun TelemetryStrip(
    lastSent: Pair<Char, Long>?,
    lastReceived: String?,
    modifier: Modifier = Modifier,
) {
    // Flash on every write, including a repeat of the same character — the counter in the pair
    // changes each time, so holding a button gives a visible pulse per keep-alive send.
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(lastSent?.second) {
        if (lastSent == null) return@LaunchedEffect
        flashing = true
        delay(120)
        flashing = false
    }

    val txColor by animateColorAsState(
        targetValue = if (flashing) NeonAmber else NeonCyan,
        animationSpec = tween(90),
        label = "txFlash",
    )

    Row(
        modifier = modifier
            .background(CarbonSurfaceHigh.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .border(1.dp, CarbonOutline, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "TX",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Text(
            text = lastSent?.first?.toString() ?: "–",
            style = MaterialTheme.typography.titleMedium,
            color = txColor,
        )

        Text(
            text = "RX",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Text(
            text = lastReceived ?: "–",
            style = MaterialTheme.typography.labelSmall,
            color = if (lastReceived == null) TextDisabled else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Wide enough for the car's full "ACK L=150 R=150" reply without truncating it.
            modifier = Modifier.widthIn(max = 132.dp),
        )
    }
}
