import Foundation

/// Parsing and clamping for the car's speed, kept apart from CoreBluetooth and SwiftUI so it can
/// be tested directly. These are the rules a wrong value would break most visibly — a stuck
/// gauge, or a speed the firmware never agreed to.
enum SpeedTelemetry {

    static let minSpeed = 0
    static let maxSpeed = 255
    static let step = 15
    static let defaultSpeed = 150

    private static let pattern = try? NSRegularExpression(
        pattern: "^SPEED\\s*:\\s*(\\d{1,3})$",
        options: .caseInsensitive
    )

    /// Reads a `SPEED:<n>` line from the car. Returns nil for anything else — `ACK` lines, noise,
    /// a half-received line — so unrelated traffic can never move the gauge.
    static func parse(_ line: String) -> Int? {
        let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
        let range = NSRange(trimmed.startIndex..., in: trimmed)
        guard let match = pattern?.firstMatch(in: trimmed, range: range),
              let digits = Range(match.range(at: 1), in: trimmed),
              let value = Int(trimmed[digits])
        else { return nil }
        // A car reporting 300 is misbehaving, but the gauge should still show something sane.
        return clamp(value)
    }

    static func clamp(_ value: Int) -> Int {
        min(max(value, minSpeed), maxSpeed)
    }

    static func stepped(from current: Int, up: Bool) -> Int {
        clamp(current + (up ? step : -step))
    }
}
