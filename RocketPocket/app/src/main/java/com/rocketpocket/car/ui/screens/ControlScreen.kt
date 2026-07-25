package com.rocketpocket.car.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rocketpocket.car.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rocketpocket.car.bluetooth.ConnectionState
import com.rocketpocket.car.ui.components.BluetoothDeviceDialog
import com.rocketpocket.car.ui.components.ConnectionStatusPill
import com.rocketpocket.car.ui.components.DirectionPad
import com.rocketpocket.car.ui.components.SpeedPanel
import com.rocketpocket.car.ui.components.TelemetryStrip
import com.rocketpocket.car.ui.theme.CarbonBlack
import com.rocketpocket.car.ui.theme.CarbonSurface
import com.rocketpocket.car.ui.theme.CarbonSurfaceHigh
import com.rocketpocket.car.ui.theme.NeonAmber
import com.rocketpocket.car.ui.theme.NeonCyan
import com.rocketpocket.car.ui.theme.NeonRed
import com.rocketpocket.car.ui.theme.TextPrimary
import com.rocketpocket.car.ui.theme.TextSecondary
import com.rocketpocket.car.viewmodel.ControlViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The permissions the Connect button needs, which differ sharply either side of Android 12.
 *
 * On API 31+ the Bluetooth permissions are their own runtime group and carry
 * `neverForLocation`, so no location prompt appears. On API 30 and below the platform gates
 * bonded-device access behind location instead.
 */
private val requiredPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun ControlScreen(viewModel: ControlViewModel) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val showDeviceDialog by viewModel.showDeviceDialog.collectAsStateWithLifecycle()
    val lastSent by viewModel.lastSent.collectAsStateWithLifecycle()
    val lastReceived by viewModel.lastReceived.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Drives the one-shot entrance animation for the two dashboard halves.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) viewModel.openDevicePicker() else viewModel.onPermissionsDenied()
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // Whatever the user chose, re-check: openDevicePicker reports the state itself.
        viewModel.openDevicePicker()
    }

    // showSnackbar suspends until the snackbar goes away, so collecting straight into it would
    // stall this collector and let messages pile up behind it. Each message is instead shown in
    // its own short-lived job that replaces the previous one, so the newest notice always wins
    // and a burst of presses can never build a queue.
    var snackbarJob by remember { mutableStateOf<Job?>(null) }
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarJob?.cancel()
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarJob = snackbarScope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.requestEnableBluetooth.collect {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    // Backstop for the activity-level onPause/onStop: whenever the dashboard stops being
    // visible, the car gets a stop command.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppLeftForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showDeviceDialog) {
        BluetoothDeviceDialog(
            devices = pairedDevices,
            onSelect = viewModel::connectTo,
            onDismiss = viewModel::dismissDevicePicker,
        )
    }

    // The dashboard is a physical control surface, not a document: LEFT must be on the left of
    // the pad no matter what language the phone is in. On an Arabic device the layout direction
    // is RTL and every Row mirrors, which put FWD RIGHT where FWD LEFT belongs and moved the
    // speedometer to the wrong side. Pinning this subtree to LTR keeps the geometry fixed while
    // leaving the rest of the system's RTL behaviour alone.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        DashboardContent(
            connectionState = connectionState,
            connectedDeviceName = connectedDeviceName,
            speed = speed,
            lastSent = lastSent,
            lastReceived = lastReceived,
            entered = entered,
            snackbarHostState = snackbarHostState,
            viewModel = viewModel,
            onConnectClick = {
                if (connectionState.isConnected) {
                    viewModel.disconnect()
                } else {
                    permissionLauncher.launch(requiredPermissions)
                }
            },
        )
    }
}

@Composable
private fun DashboardContent(
    connectionState: ConnectionState,
    connectedDeviceName: String?,
    speed: Int,
    lastSent: Pair<Char, Long>?,
    lastReceived: String?,
    entered: Boolean,
    snackbarHostState: SnackbarHostState,
    viewModel: ControlViewModel,
    onConnectClick: () -> Unit,
) {
    Scaffold(
        containerColor = CarbonBlack,
        // Pinned to the top. The Scaffold default is bottom-centre, which in landscape lands
        // directly on top of the BACKWARD / BACK LEFT / BACK RIGHT buttons — and a Snackbar is a
        // Surface, so it swallows the touches meant for them.
        snackbarHost = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                SnackbarHost(hostState = snackbarHostState)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(CarbonSurface, CarbonBlack)),
                )
                .padding(innerPadding)
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            DashboardHeader(
                connectionState = connectionState,
                connectedDeviceName = connectedDeviceName,
                lastSent = lastSent,
                lastReceived = lastReceived,
                onConnectClick = onConnectClick,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // The two halves slide in from their own edges so the dashboard assembles
                // itself rather than snapping into place.
                AnimatedVisibility(
                    visible = entered,
                    enter = fadeIn(tween(420)) +
                        slideInHorizontally(tween(420)) { -it / 3 },
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                ) {
                    DirectionPad(
                        enabled = connectionState.isConnected,
                        onDirectionPressed = viewModel::onDirectionPressed,
                        onDirectionReleased = viewModel::onDirectionReleased,
                        onEmergencyStop = viewModel::onEmergencyStop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                AnimatedVisibility(
                    visible = entered,
                    enter = fadeIn(tween(420, delayMillis = 120)) +
                        slideInHorizontally(tween(420, delayMillis = 120)) { it / 3 },
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxHeight(),
                ) {
                    SpeedPanel(
                        speed = speed,
                        maxSpeed = ControlViewModel.MAX_SPEED,
                        connected = connectionState.isConnected,
                        onIncrease = viewModel::increaseSpeed,
                        onDecrease = viewModel::decreaseSpeed,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    connectionState: ConnectionState,
    connectedDeviceName: String?,
    lastSent: Pair<Char, Long>?,
    lastReceived: String?,
    onConnectClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonSurfaceHigh.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "ROCKET POCKET",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Text(
                text = stringResource(R.string.team_name),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Text(
                text = stringResource(R.string.university_name) +
                    "  •  " + stringResource(R.string.faculty_short),
                style = MaterialTheme.typography.labelSmall,
                color = NeonAmber,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TelemetryStrip(
                lastSent = lastSent,
                lastReceived = lastReceived,
            )

            ConnectionStatusPill(
                state = connectionState,
                deviceName = connectedDeviceName,
            )

            Button(
                onClick = onConnectClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connectionState.isConnected) NeonRed else NeonCyan,
                    contentColor = CarbonBlack,
                ),
            ) {
                Icon(
                    imageVector = if (connectionState.isConnected) {
                        Icons.Filled.LinkOff
                    } else {
                        Icons.Filled.Bluetooth
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (connectionState.isConnected) "DISCONNECT" else "CONNECT",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}
