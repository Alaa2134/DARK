import XCTest
@testable import RocketPocket

/// The pad is eight entries that all typecheck in any order, so a swap sends the car somewhere
/// the driver did not ask for with nothing to catch it. These assertions pin the grid down.
final class PadLayoutTests: XCTestCase {

    func testGridIsThreeByThreeWithTheStopInTheCentre() {
        XCTAssertEqual(PadLayout.rows.count, 3)
        for row in PadLayout.rows {
            XCTAssertEqual(row.count, 3)
        }
        XCTAssertNil(PadLayout.rows[1][1], "The centre cell is the emergency stop")
    }

    func testLeftIsOnTheLeftAndRightIsOnTheRight() {
        // The fault that prompted these tests: on an Arabic device the whole pad mirrored, so
        // LEFT rendered on the right. The layout direction is pinned in RocketPocketApp; this
        // guards the logical half of the same mistake.
        XCTAssertEqual(PadLayout.rows[1][0], Command.left)
        XCTAssertEqual(PadLayout.rows[1][2], Command.right)
    }

    func testForwardCommandsAreOnTheTopRow() {
        XCTAssertEqual(PadLayout.rows[0][0], Command.forwardLeft)
        XCTAssertEqual(PadLayout.rows[0][1], Command.forward)
        XCTAssertEqual(PadLayout.rows[0][2], Command.forwardRight)
    }

    func testBackwardCommandsAreOnTheBottomRow() {
        XCTAssertEqual(PadLayout.rows[2][0], Command.backwardLeft)
        XCTAssertEqual(PadLayout.rows[2][1], Command.backward)
        XCTAssertEqual(PadLayout.rows[2][2], Command.backwardRight)
    }

    func testEveryMovementCommandAppearsExactlyOnce() {
        let placed = PadLayout.rows.flatMap { $0 }.compactMap { $0 }
        let expected: Set<Character> = [
            Command.forward, Command.backward, Command.left, Command.right,
            Command.forwardLeft, Command.forwardRight,
            Command.backwardLeft, Command.backwardRight,
        ]
        XCTAssertEqual(placed.count, 8)
        XCTAssertEqual(Set(placed), expected)
    }

    func testArrowsPointWhereTheCellSits() {
        // A cell on the left of the grid must not carry an arrow pointing right.
        XCTAssertEqual(PadLayout.rotation(for: Command.forward), 0)
        XCTAssertEqual(PadLayout.rotation(for: Command.right), 90)
        XCTAssertEqual(PadLayout.rotation(for: Command.backward), 180)
        XCTAssertEqual(PadLayout.rotation(for: Command.left), 270)
        XCTAssertEqual(PadLayout.rotation(for: Command.forwardRight), 45)
        XCTAssertEqual(PadLayout.rotation(for: Command.backwardRight), 135)
        XCTAssertEqual(PadLayout.rotation(for: Command.backwardLeft), 225)
        XCTAssertEqual(PadLayout.rotation(for: Command.forwardLeft), 315)
    }

    func testArrowsAgreeWithGridPositions() {
        // Left-hand cells point west-ish (225-315), right-hand cells point east-ish (45-135).
        for row in 0..<3 {
            if let leftCell = PadLayout.rows[row][0] {
                let angle = PadLayout.rotation(for: leftCell)
                XCTAssertTrue((225...315).contains(angle),
                              "\(PadLayout.label(for: leftCell)) sits on the left but points \(angle)°")
            }
            if let rightCell = PadLayout.rows[row][2] {
                let angle = PadLayout.rotation(for: rightCell)
                XCTAssertTrue((45...135).contains(angle),
                              "\(PadLayout.label(for: rightCell)) sits on the right but points \(angle)°")
            }
        }
    }
}
