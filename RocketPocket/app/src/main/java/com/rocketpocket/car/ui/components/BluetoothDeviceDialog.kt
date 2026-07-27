package com.rocketpocket.car.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rocketpocket.car.bluetooth.PairedDevice
import com.rocketpocket.car.ui.theme.CarbonSurface
import com.rocketpocket.car.ui.theme.CarbonSurfaceHigh
import com.rocketpocket.car.ui.theme.NeonCyan
import com.rocketpocket.car.ui.theme.NeonGreen
import com.rocketpocket.car.ui.theme.TextPrimary
import com.rocketpocket.car.ui.theme.TextSecondary

/**
 * Paired-device picker.
 *
 * The car is detected automatically by name and pinned to the top with a green border and a
 * "THIS IS THE CAR" tag, so it can be picked at a glance between heats.
 */
@Composable
fun BluetoothDeviceDialog(
    devices: List<PairedDevice>,
    onSelect: (PairedDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Paired Devices",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
        text = {
            if (devices.isEmpty()) {
                Text(
                    text = "No paired devices found. Pair \"Rocket Pocket\" in the Android " +
                        "Bluetooth settings, then try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = devices, key = { it.address }) { device ->
                        DeviceRow(device = device, onSelect = onSelect)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "CANCEL", color = NeonCyan)
            }
        },
    )
}

@Composable
private fun DeviceRow(
    device: PairedDevice,
    onSelect: (PairedDevice) -> Unit,
) {
    val highlight = device.isRocketPocket
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonSurfaceHigh, RoundedCornerShape(12.dp))
            .border(
                width = if (highlight) 2.dp else 1.dp,
                color = if (highlight) NeonGreen else CarbonSurfaceHigh,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onSelect(device) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (highlight) Icons.Filled.DirectionsCar else Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = if (highlight) NeonGreen else TextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = if (highlight) "${device.address}  •  THIS IS THE CAR" else device.address,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlight) NeonGreen else TextSecondary,
            )
        }
    }
}
