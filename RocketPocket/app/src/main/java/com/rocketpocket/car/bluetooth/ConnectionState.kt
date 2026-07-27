package com.rocketpocket.car.bluetooth

/** The three states shown in the dashboard status pill: red, orange, green. */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED;

    val isConnected: Boolean get() = this == CONNECTED
}
