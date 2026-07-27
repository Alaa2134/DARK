import Foundation

/// The single-character protocol shared with the Rocket Pocket ESP32 firmware.
///
/// Every movement is one byte on the wire — never a word or a line — so the car reacts with the
/// lowest possible latency. Identical to the Android app's `Command.kt`; the matching sketch is
/// `esp32/RocketPocket_ESP32_BLE/RocketPocket_ESP32_BLE.ino`.
enum Command {
    static let forward: Character = "F"
    static let backward: Character = "B"
    static let left: Character = "L"
    static let right: Character = "R"

    /// Curved paths — the inner wheel runs slower so the car arcs instead of pivoting.
    static let forwardRight: Character = "I"
    static let forwardLeft: Character = "G"
    static let backwardRight: Character = "J"
    static let backwardLeft: Character = "H"

    /// Sent on every release, cancel, lifecycle pause and on the emergency stop.
    static let stop: Character = "S"

    static let speedUp: Character = "+"
    static let speedDown: Character = "-"
}

enum ConnectionState {
    case disconnected
    case connecting
    case connected

    var isConnected: Bool { self == .connected }

    var label: String {
        switch self {
        case .disconnected: return "DISCONNECTED"
        case .connecting: return "CONNECTING…"
        case .connected: return "CONNECTED"
        }
    }
}

struct DiscoveredCar: Identifiable, Equatable {
    let id: UUID
    let name: String
    let rssi: Int

    /// True for the car itself, so the picker can highlight and float it to the top.
    var isRocketPocket: Bool {
        name.caseInsensitiveCompare(BluetoothController.carName) == .orderedSame
    }
}
