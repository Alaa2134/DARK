import Foundation

/// Where each command sits on the 3x3 drive pad, and which way its arrow points.
///
/// Pulled out of the view so it can be asserted in tests. Getting a cell wrong sends the car in
/// a direction the driver did not ask for, and that is not something a compiler catches — the
/// grid is just eight entries that all typecheck no matter how they are ordered.
enum PadLayout {

    /// Reading order, left to right and top to bottom. `nil` is the emergency stop in the centre.
    static let rows: [[Character?]] = [
        [Command.forwardLeft, Command.forward, Command.forwardRight],
        [Command.left, nil, Command.right],
        [Command.backwardLeft, Command.backward, Command.backwardRight],
    ]

    /// Degrees clockwise from north. The pad draws one upward arrow and rotates it, so these
    /// double as a statement of which way each command actually points.
    static func rotation(for command: Character) -> Double {
        switch command {
        case Command.forward: return 0
        case Command.forwardRight: return 45
        case Command.right: return 90
        case Command.backwardRight: return 135
        case Command.backward: return 180
        case Command.backwardLeft: return 225
        case Command.left: return 270
        case Command.forwardLeft: return 315
        default: return 0
        }
    }

    static func label(for command: Character) -> String {
        switch command {
        case Command.forward: return "FORWARD"
        case Command.backward: return "BACKWARD"
        case Command.left: return "LEFT"
        case Command.right: return "RIGHT"
        case Command.forwardRight: return "FWD RIGHT"
        case Command.forwardLeft: return "FWD LEFT"
        case Command.backwardRight: return "BACK RIGHT"
        case Command.backwardLeft: return "BACK LEFT"
        default: return ""
        }
    }
}
