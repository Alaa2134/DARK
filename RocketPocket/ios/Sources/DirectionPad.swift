import SwiftUI

/// One cell of the 3×3 pad. A single upward arrow rotated into place keeps all eight
/// directions visually identical.
private struct Direction {
    let command: Character
    let label: String
    let rotation: Double
    let accent: Color
}

private let directions: [Character: Direction] = [
    Command.forwardLeft:   Direction(command: Command.forwardLeft,   label: "FWD LEFT",   rotation: -45,  accent: Palette.neonOrange),
    Command.forward:       Direction(command: Command.forward,       label: "FORWARD",    rotation: 0,    accent: Palette.neonCyan),
    Command.forwardRight:  Direction(command: Command.forwardRight,  label: "FWD RIGHT",  rotation: 45,   accent: Palette.neonOrange),
    Command.left:          Direction(command: Command.left,          label: "LEFT",       rotation: -90,  accent: Palette.neonCyan),
    Command.right:         Direction(command: Command.right,         label: "RIGHT",      rotation: 90,   accent: Palette.neonCyan),
    Command.backwardLeft:  Direction(command: Command.backwardLeft,  label: "BACK LEFT",  rotation: -135, accent: Palette.neonOrange),
    Command.backward:      Direction(command: Command.backward,      label: "BACKWARD",   rotation: 180,  accent: Palette.neonCyan),
    Command.backwardRight: Direction(command: Command.backwardRight, label: "BACK RIGHT", rotation: 135,  accent: Palette.neonOrange),
]

/// The eight-way drive pad with the emergency stop in the centre cell.
struct DirectionPad: View {
    let enabled: Bool
    let onPress: (Character) -> Void
    let onRelease: () -> Void
    let onEmergencyStop: () -> Void

    private let rows: [[Character?]] = [
        [Command.forwardLeft, Command.forward, Command.forwardRight],
        [Command.left, nil, Command.right],
        [Command.backwardLeft, Command.backward, Command.backwardRight],
    ]

    var body: some View {
        VStack(spacing: 10) {
            ForEach(0..<3, id: \.self) { row in
                HStack(spacing: 10) {
                    ForEach(0..<3, id: \.self) { column in
                        if let command = rows[row][column], let direction = directions[command] {
                            ControlButton(
                                label: direction.label,
                                systemImage: "arrow.up",
                                rotation: direction.rotation,
                                accent: direction.accent,
                                enabled: enabled,
                                onPress: { onPress(direction.command) },
                                onRelease: onRelease
                            )
                        } else {
                            EmergencyStopButton(onStop: onEmergencyStop)
                        }
                    }
                }
            }
        }
    }
}

/// A hold-to-drive button.
///
/// `DragGesture(minimumDistance: 0)` is how UIKit's touch-down/up/cancel maps onto SwiftUI:
/// `onChanged` fires the moment a finger lands, and `onEnded` fires when it lifts — while
/// `.onDisappear` and the gesture being interrupted both funnel into the same release handler.
/// There is no path through this gesture that leaves the car driving.
///
/// The gesture stays active even when `enabled` is false: the view model answers a press with
/// "Connect to Rocket Pocket first" instead of silently ignoring it.
struct ControlButton: View {
    let label: String
    let systemImage: String
    var rotation: Double = 0
    let accent: Color
    let enabled: Bool
    let onPress: () -> Void
    let onRelease: () -> Void

    @State private var pressed = false

    private var container: Color {
        if pressed && enabled { return accent.opacity(0.30) }
        return enabled ? Palette.carbonSurfaceHigh : Palette.carbonSurface
    }

    private var content: Color {
        if pressed && enabled { return accent }
        return enabled ? Palette.textPrimary : Palette.textDisabled
    }

    var body: some View {
        VStack(spacing: 3) {
            Image(systemName: systemImage)
                .font(.system(size: 30, weight: .bold))
                .rotationEffect(.degrees(rotation))
            Text(label)
                .font(.system(size: 9, weight: .medium))
                .tracking(1)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .foregroundColor(content)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(container)
                .shadow(color: pressed && enabled ? accent.opacity(0.55) : .clear, radius: 14)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(
                    pressed && enabled ? accent : Palette.carbonOutline,
                    lineWidth: pressed && enabled ? 2 : 1
                )
        )
        .scaleEffect(pressed ? 0.92 : 1)
        .animation(.spring(response: 0.18, dampingFraction: 0.55), value: pressed)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard !pressed else { return }   // onChanged repeats while the finger moves
                    pressed = true
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    onPress()
                }
                .onEnded { _ in
                    pressed = false
                    onRelease()
                }
        )
        .onDisappear {
            // The view going away mid-press must not leave the car running.
            if pressed {
                pressed = false
                onRelease()
            }
        }
    }
}

/// The emergency stop. Always live — even while disconnected, where the view model answers with
/// "Connect to Rocket Pocket first" — because a stop must never be swallowed.
struct EmergencyStopButton: View {
    let onStop: () -> Void
    @State private var pressed = false

    var body: some View {
        VStack(spacing: 3) {
            Image(systemName: "hand.raised.fill")
                .font(.system(size: 24, weight: .bold))
            Text("EMERGENCY\nSTOP")
                .font(.system(size: 9, weight: .semibold))
                .multilineTextAlignment(.center)
        }
        .foregroundColor(Palette.textPrimary)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            Circle()
                .fill(pressed ? Palette.neonRed : Palette.neonRedDeep)
                .shadow(color: Palette.neonRed.opacity(pressed ? 0.7 : 0.3), radius: pressed ? 18 : 8)
        )
        .overlay(Circle().stroke(Palette.neonRed, lineWidth: pressed ? 4 : 3))
        .scaleEffect(pressed ? 0.90 : 1)
        .animation(.spring(response: 0.18, dampingFraction: 0.5), value: pressed)
        .contentShape(Circle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard !pressed else { return }
                    pressed = true
                    UINotificationFeedbackGenerator().notificationOccurred(.warning)
                    onStop()
                }
                .onEnded { _ in pressed = false }
        )
    }
}
