package com.rocketpocket.car.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rocketpocket.car.bluetooth.BluetoothController
import com.rocketpocket.car.bluetooth.Command
import com.rocketpocket.car.bluetooth.ConnectionState
import com.rocketpocket.car.bluetooth.PairedDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Holds every piece of dashboard state and is the only place that decides whether a
 * command is allowed to reach the car.
 */
class ControlViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val DEFAULT_SPEED = 150
        const val SPEED_STEP = 15
        const val MIN_SPEED = 0
        const val MAX_SPEED = 255

        const val MESSAGE_NOT_CONNECTED = "Connect to Rocket Pocket first"

        /** Comfortably inside the firmware's 1000 ms watchdog window. */
        const val KEEP_ALIVE_INTERVAL_MS = 300L

        private val SPEED_REGEX = Regex("""^SPEED\s*:\s*(\d{1,3})$""", RegexOption.IGNORE_CASE)
    }

    private val controller = BluetoothController(application.applicationContext)

    /** Repeats the held direction so the firmware watchdog can tell holding from signal loss. */
    private var keepAliveJob: Job? = null

    val connectionState: StateFlow<ConnectionState> = controller.connectionState
    val connectedDeviceName: StateFlow<String?> = controller.connectedDeviceName

    private val _speed = MutableStateFlow(DEFAULT_SPEED)
    val speed: StateFlow<Int> = _speed.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    private val _showDeviceDialog = MutableStateFlow(false)
    val showDeviceDialog: StateFlow<Boolean> = _showDeviceDialog.asStateFlow()

    /** The command currently held down, so the pressed button can glow. */
    private val _activeCommand = MutableStateFlow<Char?>(null)
    val activeCommand: StateFlow<Char?> = _activeCommand.asStateFlow()

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Raised when the user taps Connect while the adapter is off, so the UI can show the system prompt. */
    private val _requestEnableBluetooth = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val requestEnableBluetooth: SharedFlow<Unit> = _requestEnableBluetooth.asSharedFlow()

    init {
        viewModelScope.launch {
            controller.incomingLines.collect { line -> handleIncomingLine(line) }
        }
        viewModelScope.launch {
            controller.events.collect { event ->
                if (event == BluetoothController.EVENT_CONNECTION_LOST) {
                    stopKeepAlive()
                    _activeCommand.value = null
                }
                _messages.tryEmit(event)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Incoming telemetry
    // ---------------------------------------------------------------------------------------

    /** The car is authoritative about its own PWM, so `SPEED:xxx` overwrites the local value. */
    private fun handleIncomingLine(line: String) {
        val match = SPEED_REGEX.find(line.trim()) ?: return
        val reported = match.groupValues[1].toIntOrNull() ?: return
        _speed.value = reported.coerceIn(MIN_SPEED, MAX_SPEED)
    }

    // ---------------------------------------------------------------------------------------
    // Connection handling
    // ---------------------------------------------------------------------------------------

    /** Called once the runtime permissions have been granted. */
    fun openDevicePicker() {
        if (!controller.isBluetoothSupported()) {
            _messages.tryEmit("This device has no Bluetooth adapter")
            return
        }
        if (!controller.isBluetoothEnabled()) {
            _messages.tryEmit("Turn Bluetooth on to continue")
            _requestEnableBluetooth.tryEmit(Unit)
            return
        }

        val devices = controller.pairedDevices()
        _pairedDevices.value = devices
        if (devices.isEmpty()) {
            _messages.tryEmit("No paired devices. Pair \"Rocket Pocket\" in Android settings first")
            return
        }
        _showDeviceDialog.value = true
    }

    fun onPermissionsDenied() {
        _messages.tryEmit("Bluetooth permission denied. Grant it to connect to the car")
    }

    fun dismissDevicePicker() {
        _showDeviceDialog.value = false
    }

    fun connectTo(device: PairedDevice) {
        _showDeviceDialog.value = false
        controller.connect(device)
    }

    fun disconnect() {
        stopKeepAlive()
        _activeCommand.value = null
        controller.disconnect()
    }

    // ---------------------------------------------------------------------------------------
    // Driving
    // ---------------------------------------------------------------------------------------

    /** ACTION_DOWN on one of the eight direction buttons. */
    fun onDirectionPressed(command: Char) {
        if (!requireConnection()) return
        _activeCommand.value = command
        controller.send(command)
        startKeepAlive(command)
    }

    /** ACTION_UP and ACTION_CANCEL both land here, so the car always stops. */
    fun onDirectionReleased() {
        stopKeepAlive()
        _activeCommand.value = null
        controller.send(Command.STOP)
    }

    /**
     * Re-sends the held command on a short interval.
     *
     * A press only puts one character on the wire, so without this the car has no way to tell
     * "still holding forward" from "the phone went out of range mid-corner". The repeat feeds
     * the firmware watchdog: stop arriving, and the ESP32 cuts the motors within a second.
     */
    private fun startKeepAlive(command: Char) {
        keepAliveJob?.cancel()
        keepAliveJob = viewModelScope.launch {
            while (isActive) {
                delay(KEEP_ALIVE_INTERVAL_MS)
                if (!connectionState.value.isConnected) break
                controller.send(command)
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    fun onEmergencyStop() {
        stopKeepAlive()
        _activeCommand.value = null
        if (!controller.send(Command.STOP)) {
            _messages.tryEmit(MESSAGE_NOT_CONNECTED)
            return
        }
        _messages.tryEmit("Emergency stop sent")
    }

    fun increaseSpeed() {
        if (!requireConnection()) return
        _speed.value = (_speed.value + SPEED_STEP).coerceIn(MIN_SPEED, MAX_SPEED)
        controller.send(Command.SPEED_UP)
    }

    fun decreaseSpeed() {
        if (!requireConnection()) return
        _speed.value = (_speed.value - SPEED_STEP).coerceIn(MIN_SPEED, MAX_SPEED)
        controller.send(Command.SPEED_DOWN)
    }

    private fun requireConnection(): Boolean {
        if (connectionState.value.isConnected) return true
        _messages.tryEmit(MESSAGE_NOT_CONNECTED)
        return false
    }

    // ---------------------------------------------------------------------------------------
    // Lifecycle safety
    // ---------------------------------------------------------------------------------------

    /** onPause / onStop: the user can no longer see the controls, so the car must not move. */
    fun onAppLeftForeground() {
        stopKeepAlive()
        _activeCommand.value = null
        controller.send(Command.STOP)
    }

    /** onDestroy: stop synchronously, then close the socket. */
    fun onActivityDestroyed(isFinishing: Boolean) {
        stopKeepAlive()
        _activeCommand.value = null
        controller.sendStopImmediately()
        if (isFinishing) controller.shutdown()
    }

    override fun onCleared() {
        controller.shutdown()
        super.onCleared()
    }
}
