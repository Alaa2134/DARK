package com.rocketpocket.car.bluetooth

import android.bluetooth.BluetoothDevice

/**
 * A bonded device ready to be shown in the picker.
 *
 * The name and address are read once, behind a permission check, so the UI never has to
 * touch [BluetoothDevice] getters that would throw a SecurityException on Android 12+.
 */
data class PairedDevice(
    val name: String,
    val address: String,
    val device: BluetoothDevice,
) {
    /** True for the car itself, so the dialog can highlight and pre-select it. */
    val isRocketPocket: Boolean
        get() = name.equals(BluetoothController.CAR_DEVICE_NAME, ignoreCase = true)
}
