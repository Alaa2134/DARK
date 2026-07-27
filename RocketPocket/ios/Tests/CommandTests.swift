import XCTest
@testable import RocketPocket

/// The protocol characters are shared by hand with two Arduino sketches and the Android app.
/// A silent change here would send the car in the wrong direction with nothing to catch it, so
/// the mapping is pinned down in a test.
final class CommandTests: XCTestCase {

    func testProtocolCharactersMatchTheFirmware() {
        XCTAssertEqual(Command.forward, "F")
        XCTAssertEqual(Command.backward, "B")
        XCTAssertEqual(Command.left, "L")
        XCTAssertEqual(Command.right, "R")
        XCTAssertEqual(Command.forwardRight, "I")
        XCTAssertEqual(Command.forwardLeft, "G")
        XCTAssertEqual(Command.backwardRight, "J")
        XCTAssertEqual(Command.backwardLeft, "H")
        XCTAssertEqual(Command.stop, "S")
        XCTAssertEqual(Command.speedUp, "+")
        XCTAssertEqual(Command.speedDown, "-")
    }

    func testEveryCommandIsDistinct() {
        let all: [Character] = [
            Command.forward, Command.backward, Command.left, Command.right,
            Command.forwardRight, Command.forwardLeft,
            Command.backwardRight, Command.backwardLeft,
            Command.stop, Command.speedUp, Command.speedDown,
        ]
        XCTAssertEqual(Set(all).count, all.count, "Two commands share a character")
    }

    func testConnectionStateReportsConnectionCorrectly() {
        XCTAssertTrue(ConnectionState.connected.isConnected)
        XCTAssertFalse(ConnectionState.connecting.isConnected)
        XCTAssertFalse(ConnectionState.disconnected.isConnected)
    }

    func testTheCarIsRecognisedByNameRegardlessOfCase() {
        let car = DiscoveredCar(id: UUID(), name: "rocket pocket", rssi: -50)
        XCTAssertTrue(car.isRocketPocket)

        let other = DiscoveredCar(id: UUID(), name: "Some Speaker", rssi: -50)
        XCTAssertFalse(other.isRocketPocket)
    }
}
