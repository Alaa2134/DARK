package com.rocketpocket.car.bluetooth

/**
 * The single-character protocol shared with the Rocket Pocket ESP32 firmware.
 *
 * Every movement is one byte on the wire — never a word or a line — so the car reacts
 * with the lowest possible latency. The matching sketch lives in `esp32/RocketPocket_ESP32.ino`.
 */
object Command {

    const val FORWARD = 'F'
    const val BACKWARD = 'B'
    const val LEFT = 'L'
    const val RIGHT = 'R'

    /** Curved forward paths — the inner wheel runs slower so the car arcs instead of pivoting. */
    const val FORWARD_RIGHT = 'I'
    const val FORWARD_LEFT = 'G'
    const val BACKWARD_RIGHT = 'J'
    const val BACKWARD_LEFT = 'H'

    /** Sent on every release, cancel, lifecycle pause and on the emergency stop. */
    const val STOP = 'S'

    const val SPEED_UP = '+'
    const val SPEED_DOWN = '-'
}
