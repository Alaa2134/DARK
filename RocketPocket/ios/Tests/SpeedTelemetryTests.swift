import XCTest
@testable import RocketPocket

/// The car's telemetry is the one input the app cannot control, so these cover the malformed
/// cases as carefully as the good ones — a parser that accepts junk would move the gauge on
/// noise, and one that rejects a valid line would freeze it mid-race.
final class SpeedTelemetryTests: XCTestCase {

    func testParsesAWellFormedLine() {
        XCTAssertEqual(SpeedTelemetry.parse("SPEED:150"), 150)
        XCTAssertEqual(SpeedTelemetry.parse("SPEED:0"), 0)
        XCTAssertEqual(SpeedTelemetry.parse("SPEED:255"), 255)
    }

    func testToleratesWhitespaceAndCase() {
        XCTAssertEqual(SpeedTelemetry.parse("  SPEED:150  "), 150)
        XCTAssertEqual(SpeedTelemetry.parse("SPEED : 150"), 150)
        XCTAssertEqual(SpeedTelemetry.parse("speed:150"), 150)
        XCTAssertEqual(SpeedTelemetry.parse("SPEED:150\n"), 150)
    }

    func testRejectsAnythingThatIsNotASpeedLine() {
        // ACK lines share the wire with SPEED lines and must never move the gauge.
        XCTAssertNil(SpeedTelemetry.parse("ACK L=150 R=150"))
        XCTAssertNil(SpeedTelemetry.parse(""))
        XCTAssertNil(SpeedTelemetry.parse("SPEED:"))
        XCTAssertNil(SpeedTelemetry.parse("SPEED:abc"))
        XCTAssertNil(SpeedTelemetry.parse("SPEED:150 R=2"))
        XCTAssertNil(SpeedTelemetry.parse("XSPEED:150"))
        // Four digits cannot be a byte, so this is a corrupt line rather than a large speed.
        XCTAssertNil(SpeedTelemetry.parse("SPEED:1500"))
    }

    func testClampsAnOutOfRangeReport() {
        // A misbehaving car should still leave the gauge showing something sane.
        XCTAssertEqual(SpeedTelemetry.parse("SPEED:300"), 255)
    }

    func testClampHoldsTheBounds() {
        XCTAssertEqual(SpeedTelemetry.clamp(-40), 0)
        XCTAssertEqual(SpeedTelemetry.clamp(999), 255)
        XCTAssertEqual(SpeedTelemetry.clamp(150), 150)
    }

    func testSteppingNeverLeavesTheRange() {
        XCTAssertEqual(SpeedTelemetry.stepped(from: 150, up: true), 165)
        XCTAssertEqual(SpeedTelemetry.stepped(from: 150, up: false), 135)
        // 255 is not a multiple of the step, so the top and bottom must be pinned explicitly.
        XCTAssertEqual(SpeedTelemetry.stepped(from: 250, up: true), 255)
        XCTAssertEqual(SpeedTelemetry.stepped(from: 10, up: false), 0)
        XCTAssertEqual(SpeedTelemetry.stepped(from: 255, up: true), 255)
        XCTAssertEqual(SpeedTelemetry.stepped(from: 0, up: false), 0)
    }
}
