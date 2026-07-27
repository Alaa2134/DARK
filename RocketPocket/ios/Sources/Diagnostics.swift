import Foundation

/// What the car reports about itself once a second.
///
/// Deliberately limited to things the firmware already knows. Battery level, temperature and
/// "is this motor plugged in" cannot be answered by an ESP32 and an L298N alone — they need a
/// voltage divider, a temperature sensor and a current sensor respectively. Showing an invented
/// battery percentage would be worse than showing none, because it would be believed.
struct Diagnostics: Equatable {

    /// Seconds since the car booted. A value that keeps resetting means it is browning out.
    var uptimeSeconds: Int = 0

    /// Free heap in bytes. Steadily falling across a session would mean a leak in the firmware.
    var freeHeap: Int = 0

    /// How many times the car has cut its own motors because commands stopped arriving. The
    /// single most useful number here: it counts real link failures while driving.
    var watchdogTrips: Int = 0

    var speed: Int = 0
    var leftDuty: Int = 0
    var rightDuty: Int = 0

    /// The wiring corrections currently compiled into the firmware, so the app can show which
    /// sketch is actually on the board rather than what anyone assumes is on it.
    var motorsSwapped = false
    var leftInverted = false
    var rightInverted = false
    var brakeOnStop = false
    var reverseCurveLikeCar = false

    var uptimeText: String {
        let h = uptimeSeconds / 3600, m = (uptimeSeconds % 3600) / 60, s = uptimeSeconds % 60
        return h > 0 ? String(format: "%dh %02dm", h, m) : String(format: "%dm %02ds", m, s)
    }

    var freeHeapText: String {
        String(format: "%.0f KB", Double(freeHeap) / 1024)
    }

    /// Parses a `DIAG key=value ...` line. Returns nil for anything else, so ACK and SPEED
    /// traffic on the same wire cannot be mistaken for telemetry.
    static func parse(_ line: String) -> Diagnostics? {
        let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("DIAG ") else { return nil }

        var pairs: [String: String] = [:]
        for token in trimmed.dropFirst(5).split(separator: " ") {
            let parts = token.split(separator: "=", maxSplits: 1)
            guard parts.count == 2 else { continue }
            pairs[String(parts[0])] = String(parts[1])
        }
        guard !pairs.isEmpty else { return nil }

        func int(_ key: String) -> Int { Int(pairs[key] ?? "") ?? 0 }
        func flag(_ key: String) -> Bool { pairs[key] == "1" }

        return Diagnostics(
            uptimeSeconds: int("up"),
            freeHeap: int("heap"),
            watchdogTrips: int("wd"),
            speed: int("spd"),
            leftDuty: int("L"),
            rightDuty: int("R"),
            motorsSwapped: flag("swap"),
            leftInverted: flag("invL"),
            rightInverted: flag("invR"),
            brakeOnStop: flag("brake"),
            reverseCurveLikeCar: flag("curve")
        )
    }
}
