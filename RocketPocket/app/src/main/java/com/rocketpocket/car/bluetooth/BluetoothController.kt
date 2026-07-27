package com.rocketpocket.car.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Owns the Bluetooth Classic (RFCOMM/SPP) link to the car.
 *
 * All socket work happens off the main thread on [Dispatchers.IO]; the UI only ever observes
 * the flows exposed here. Writes are serialised through a [Mutex] so two buttons pressed at
 * once can never interleave their bytes on the wire.
 */
class BluetoothController(private val context: Context) {

    companion object {
        /** Standard Serial Port Profile UUID — the same one the ESP32 BluetoothSerial library uses. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        const val CAR_DEVICE_NAME = "Rocket Pocket"

        const val EVENT_CONNECTION_LOST = "Bluetooth Connection Lost"

        private const val TAG = "BluetoothController"
        private const val MAX_LINE_LENGTH = 128
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var input: InputStream? = null

    @Volatile
    private var output: OutputStream? = null

    private var readJob: Job? = null
    private var connectJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    /** Complete newline-terminated lines received from the ESP32, e.g. `SPEED:150`. */
    private val _incomingLines = MutableSharedFlow<String>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incomingLines: SharedFlow<String> = _incomingLines.asSharedFlow()

    /**
     * The last character actually written to the socket, and a counter that changes on every
     * write. The counter lets the UI flash even when the same character is sent twice in a row,
     * which is what makes the on-screen TX indicator usable for diagnosing button mapping.
     */
    private val _lastSent = MutableStateFlow<Pair<Char, Long>?>(null)
    val lastSent: StateFlow<Pair<Char, Long>?> = _lastSent.asStateFlow()

    private var sendCounter = 0L

    /** User-facing one-off notices such as [EVENT_CONNECTION_LOST]. */
    private val _events = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<String> = _events.asSharedFlow()

    // ---------------------------------------------------------------------------------------
    // Capability and permission checks
    // ---------------------------------------------------------------------------------------

    fun isBluetoothSupported(): Boolean = adapter != null

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /** BLUETOOTH_CONNECT only exists from Android 12; older releases grant it at install time. */
    fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /**
     * Bonded devices, with the car floated to the top of the list so the picker can
     * highlight it without the user hunting for it mid-race.
     */
    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedDevice> {
        if (!hasConnectPermission()) return emptyList()
        val bonded = try {
            adapter?.bondedDevices
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission while reading bonded devices", e)
            null
        } ?: return emptyList()

        return bonded
            .map { device ->
                val name = try {
                    device.name
                } catch (e: SecurityException) {
                    null
                } ?: "Unknown device"
                PairedDevice(name = name, address = device.address, device = device)
            }
            .sortedWith(compareByDescending<PairedDevice> { it.isRocketPocket }.thenBy { it.name })
    }

    // ---------------------------------------------------------------------------------------
    // Connecting
    // ---------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    fun connect(target: PairedDevice) {
        if (!hasConnectPermission()) {
            _events.tryEmit("Bluetooth permission is required to connect")
            return
        }

        connectJob?.cancel()
        connectJob = scope.launch {
            closeConnection()
            _connectionState.value = ConnectionState.CONNECTING
            _connectedDeviceName.value = target.name

            // Discovery is bandwidth-hungry and makes RFCOMM connects fail; always stop it first.
            try {
                adapter?.cancelDiscovery()
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not cancel discovery", e)
            }

            try {
                val openedSocket = openSocket(target.device)
                val outputStream = openedSocket.outputStream
                val inputStream = openedSocket.inputStream

                socket = openedSocket
                output = outputStream
                input = inputStream

                _connectionState.value = ConnectionState.CONNECTED
                _events.tryEmit("Connected to ${target.name}")

                startReader(inputStream)

                // Start from a known-safe state: the car must not be moving on connect.
                send(Command.STOP)
            } catch (e: Exception) {
                Log.e(TAG, "Connection to ${target.address} failed", e)
                closeConnection()
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectedDeviceName.value = null
                _events.tryEmit("Connection failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    /**
     * Opens an RFCOMM socket, falling back to the reflection-based channel-1 constructor.
     *
     * The documented [BluetoothDevice.createRfcommSocketToServiceRecord] path relies on an SDP
     * lookup that some ESP32 builds and older Android stacks answer badly; the hidden
     * `createRfcommSocket(int)` targets channel 1 directly and rescues those pairings.
     */
    @SuppressLint("MissingPermission")
    private fun openSocket(device: BluetoothDevice): BluetoothSocket {
        var primary: BluetoothSocket? = null
        try {
            primary = device.createRfcommSocketToServiceRecord(SPP_UUID)
            primary.connect()
            return primary
        } catch (primaryFailure: Exception) {
            Log.w(TAG, "SPP connect failed, trying channel 1 fallback", primaryFailure)
            try {
                primary?.close()
            } catch (ignored: Exception) {
                // Nothing useful to do — we are already on the failure path.
            }

            var fallback: BluetoothSocket? = null
            try {
                val method = device.javaClass.getMethod(
                    "createRfcommSocket",
                    Int::class.javaPrimitiveType,
                )
                fallback = method.invoke(device, 1) as BluetoothSocket
                fallback.connect()
                return fallback
            } catch (fallbackFailure: Exception) {
                try {
                    fallback?.close()
                } catch (ignored: Exception) {
                    // Same as above.
                }
                throw IOException(
                    primaryFailure.message ?: "Unable to open a Bluetooth socket",
                    primaryFailure,
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Receiving
    // ---------------------------------------------------------------------------------------

    private fun startReader(stream: InputStream) {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(256)
            val line = StringBuilder()
            try {
                while (isActive) {
                    val read = stream.read(buffer)
                    if (read == -1) throw IOException("Stream closed by the car")
                    for (i in 0 until read) {
                        when (val character = buffer[i].toInt().toChar()) {
                            '\n', '\r' -> {
                                if (line.isNotEmpty()) {
                                    _incomingLines.tryEmit(line.toString().trim())
                                    line.setLength(0)
                                }
                            }

                            else -> {
                                line.append(character)
                                // Guard against a chatty peer that never sends a newline.
                                if (line.length > MAX_LINE_LENGTH) line.setLength(0)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(TAG, "Read loop ended", e)
                    handleConnectionLost()
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Sending
    // ---------------------------------------------------------------------------------------

    /**
     * Queues a single character for immediate delivery.
     *
     * Returns false when there is no live link, which is what keeps movement commands from
     * being sent while disconnected.
     */
    fun send(command: Char): Boolean {
        if (_connectionState.value != ConnectionState.CONNECTED) return false
        scope.launch {
            writeMutex.withLock {
                val stream = output ?: return@withLock
                try {
                    stream.write(command.code)
                    stream.flush()
                    _lastSent.value = command to ++sendCounter
                } catch (e: IOException) {
                    Log.w(TAG, "Write of '$command' failed", e)
                    handleConnectionLost()
                }
            }
        }
        return true
    }

    /**
     * Writes the stop byte on the calling thread.
     *
     * Used from `onDestroy`, where a coroutine would very likely be cancelled before it ran and
     * would leave the car driving. The write is a single byte to an already-open local socket,
     * so it costs microseconds; failures are swallowed because the socket is closing anyway.
     */
    fun sendStopImmediately() {
        val stream = output ?: return
        try {
            stream.write(Command.STOP.code)
            stream.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Immediate stop could not be delivered", e)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Tearing down
    // ---------------------------------------------------------------------------------------

    /** User-requested disconnect: stop the car first, then drop the link. */
    fun disconnect() {
        connectJob?.cancel()
        sendStopImmediately()
        scope.launch {
            closeConnection()
            _connectionState.value = ConnectionState.DISCONNECTED
            _connectedDeviceName.value = null
            _events.tryEmit("Disconnected")
        }
    }

    private fun handleConnectionLost() {
        if (_connectionState.value == ConnectionState.DISCONNECTED) return
        closeConnection()
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        _events.tryEmit(EVENT_CONNECTION_LOST)
    }

    /**
     * Closes every stream and the socket, each guarded separately so one failure
     * cannot skip the rest of the teardown.
     */
    private fun closeConnection() {
        readJob?.cancel()
        readJob = null

        try {
            input?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Closing the input stream failed", e)
        }
        input = null

        try {
            output?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Closing the output stream failed", e)
        }
        output = null

        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Closing the socket failed", e)
        }
        socket = null
    }

    /** Final teardown when the app is going away for good. */
    fun shutdown() {
        sendStopImmediately()
        closeConnection()
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        scope.cancel()
    }
}
