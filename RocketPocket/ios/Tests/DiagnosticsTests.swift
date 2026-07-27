import XCTest
@testable import RocketPocket

/// DIAG shares the wire with SPEED and ACK, so the parser has to be strict about what it
/// accepts — a loose match would let unrelated traffic overwrite the telemetry display.
final class DiagnosticsTests: XCTestCase {

    private let sample = "DIAG up=125 heap=184320 wd=3 spd=150 L=150 R=82 " +
                         "swap=0 invL=1 invR=0 brake=1 curve=1"

    func testParsesAFullReport() {
        let d = Diagnostics.parse(sample)
        XCTAssertNotNil(d)
        XCTAssertEqual(d?.uptimeSeconds, 125)
        XCTAssertEqual(d?.freeHeap, 184320)
        XCTAssertEqual(d?.watchdogTrips, 3)
        XCTAssertEqual(d?.speed, 150)
        XCTAssertEqual(d?.leftDuty, 150)
        XCTAssertEqual(d?.rightDuty, 82)
    }

    func testReadsTheWiringFlags() {
        let d = Diagnostics.parse(sample)
        XCTAssertEqual(d?.motorsSwapped, false)
        XCTAssertEqual(d?.leftInverted, true)
        XCTAssertEqual(d?.rightInverted, false)
        XCTAssertEqual(d?.brakeOnStop, true)
        XCTAssertEqual(d?.reverseCurveLikeCar, true)
    }

    func testRejectsTheOtherTrafficOnTheSameWire() {
        XCTAssertNil(Diagnostics.parse("SPEED:150"))
        XCTAssertNil(Diagnostics.parse("ACK L=150 R=150"))
        XCTAssertNil(Diagnostics.parse("ACK BRAKE"))
        XCTAssertNil(Diagnostics.parse(""))
        XCTAssertNil(Diagnostics.parse("DIAG"))          // prefix alone carries no fields
        XCTAssertNil(Diagnostics.parse("DIAGNOSTIC up=1"))
    }

    func testToleratesWhitespaceAndMissingFields() {
        let d = Diagnostics.parse("  DIAG up=60 wd=0  \n")
        XCTAssertEqual(d?.uptimeSeconds, 60)
        XCTAssertEqual(d?.watchdogTrips, 0)
        // Absent fields default rather than failing the whole line, so an older sketch that
        // reports fewer keys still drives the parts of the screen it can.
        XCTAssertEqual(d?.freeHeap, 0)
    }

    func testIgnoresMalformedTokens() {
        let d = Diagnostics.parse("DIAG up=10 broken heap= wd=2")
        XCTAssertEqual(d?.uptimeSeconds, 10)
        XCTAssertEqual(d?.watchdogTrips, 2)
        XCTAssertEqual(d?.freeHeap, 0)
    }

    func testUptimeReadsAsHoursOnceItPassesOne() {
        XCTAssertEqual(Diagnostics(uptimeSeconds: 65).uptimeText, "1m 05s")
        XCTAssertEqual(Diagnostics(uptimeSeconds: 3725).uptimeText, "1h 02m")
    }
}
