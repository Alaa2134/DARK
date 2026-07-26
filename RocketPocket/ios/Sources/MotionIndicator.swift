import SwiftUI

/// A live picture of what the car is doing right now.
///
/// The pad shows what is being *asked* for; this shows the command that is actually latched and
/// repeating on the wire. During a run the driver is watching the car, and a glance back at a
/// rotating arrow answers "is it still driving, and which way" faster than reading button states.
/// It also makes a stuck command obvious — if the car has stopped but this is still lit, the
/// problem is the link, not the pad.
struct MotionIndicator: View {

    let command: Character?
    let connected: Bool

    /// Degrees clockwise from "forward", matching the arrow rotations used on the pad.
    private var heading: Double? {
        switch command {
        case Command.forward?: return 0
        case Command.forwardRight?: return 45
        case Command.right?: return 90
        case Command.backwardRight?: return 135
        case Command.backward?: return 180
        case Command.backwardLeft?: return 225
        case Command.left?: return 270
        case Command.forwardLeft?: return 315
        default: return nil
        }
    }

    private var isSpinning: Bool {
        command == Command.left || command == Command.right
    }

    private var label: String {
        switch command {
        case Command.forward?: return "FORWARD"
        case Command.backward?: return "REVERSE"
        case Command.left?: return "SPIN LEFT"
        case Command.right?: return "SPIN RIGHT"
        case Command.forwardRight?: return "FWD RIGHT"
        case Command.forwardLeft?: return "FWD LEFT"
        case Command.backwardRight?: return "BACK RIGHT"
        case Command.backwardLeft?: return "BACK LEFT"
        default: return connected ? "IDLE" : "NO LINK"
        }
    }

    private var accent: Color {
        guard connected else { return Palette.textDisabled }
        return command == nil ? Palette.textSecondary : Palette.neonCyan
    }

    var body: some View {
        HStack(spacing: 8) {
            ZStack {
                Circle()
                    .stroke(accent.opacity(0.35), lineWidth: 1)
                    .frame(width: 26, height: 26)

                if let heading {
                    // arrow.clockwise, not one of the newer trianglehead variants: those only
                    // exist from iOS 18 and this app targets 16.
                    Image(systemName: isSpinning ? "arrow.clockwise" : "arrow.up")
                        .font(.system(size: 13, weight: .black))
                        .foregroundColor(accent)
                        // Spin commands rotate the car on the spot, so an arrow pointing sideways
                        // would misdescribe them; they get a rotation glyph instead.
                        .rotationEffect(.degrees(isSpinning ? 0 : heading))
                        .scaleEffect(command == Command.right ? CGSize(width: -1, height: 1)
                                                              : CGSize(width: 1, height: 1))
                } else {
                    Circle()
                        .fill(accent.opacity(0.5))
                        .frame(width: 6, height: 6)
                }
            }

            Text(label)
                .font(.system(size: 9, weight: .bold, design: .monospaced))
                .tracking(0.8)
                .foregroundColor(accent)
                .frame(width: 68, alignment: .leading)
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 5)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Palette.carbonSurfaceHigh.opacity(0.55))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(command == nil ? Palette.carbonOutline : accent.opacity(0.7), lineWidth: 1)
        )
        .animation(.spring(response: 0.25, dampingFraction: 0.7), value: command)
    }
}
