import SwiftUI

/// Circular PWM gauge, 0 to 255.
///
/// The needle animates towards each new reading rather than snapping, so a `SPEED:` line
/// arriving from the ESP32 sweeps smoothly and stays readable while driving.
struct Speedometer: View {
    let speed: Int
    let maxSpeed: Int
    let connected: Bool

    private static let startAngle: Double = 135
    private static let sweepAngle: Double = 270
    private static let tickCount = 9

    private var fraction: Double {
        guard maxSpeed > 0 else { return 0 }
        return min(max(Double(speed) / Double(maxSpeed), 0), 1)
    }

    /// Green through amber to red as the duty cycle climbs.
    private var gaugeColor: Color {
        fraction < 0.5
            ? Palette.neonGreen.blend(to: Palette.neonAmber, amount: fraction / 0.5)
            : Palette.neonAmber.blend(to: Palette.neonRed, amount: (fraction - 0.5) / 0.5)
    }

    var body: some View {
        GeometryReader { geometry in
            let side = min(geometry.size.width, geometry.size.height)
            let stroke = side * 0.085
            let radius = (side - stroke) / 2
            let centre = CGPoint(x: geometry.size.width / 2, y: geometry.size.height / 2)

            ZStack {
                // Unfilled track.
                Arc(start: Self.startAngle, sweep: Self.sweepAngle, radius: radius, centre: centre)
                    .stroke(Palette.carbonSurfaceHigh, style: StrokeStyle(lineWidth: stroke, lineCap: .round))

                // Tick marks around the dial.
                ForEach(0...Self.tickCount, id: \.self) { tick in
                    let t = Double(tick) / Double(Self.tickCount)
                    let angle = (Self.startAngle + Self.sweepAngle * t) * .pi / 180
                    let outer = radius - stroke * 0.75
                    let inner = outer - stroke * (tick % 2 == 0 ? 0.85 : 0.5)

                    Path { path in
                        path.move(to: CGPoint(x: centre.x + outer * cos(angle), y: centre.y + outer * sin(angle)))
                        path.addLine(to: CGPoint(x: centre.x + inner * cos(angle), y: centre.y + inner * sin(angle)))
                    }
                    .stroke(
                        t <= fraction ? gaugeColor : Palette.carbonOutline,
                        style: StrokeStyle(lineWidth: stroke * 0.18, lineCap: .round)
                    )
                }

                // Filled portion.
                if fraction > 0 {
                    Arc(start: Self.startAngle, sweep: Self.sweepAngle * fraction, radius: radius, centre: centre)
                        .stroke(
                            AngularGradient(
                                colors: [Palette.neonGreen, Palette.neonAmber, Palette.neonRed, Palette.neonGreen],
                                center: .center
                            ),
                            style: StrokeStyle(lineWidth: stroke, lineCap: .round)
                        )
                }

                // Needle.
                let needleAngle = (Self.startAngle + Self.sweepAngle * fraction) * .pi / 180
                let needleLength = radius - stroke * 1.6
                Path { path in
                    path.move(to: centre)
                    path.addLine(to: CGPoint(
                        x: centre.x + needleLength * cos(needleAngle),
                        y: centre.y + needleLength * sin(needleAngle)
                    ))
                }
                .stroke(
                    connected ? gaugeColor : Palette.textDisabled,
                    style: StrokeStyle(lineWidth: stroke * 0.34, lineCap: .round)
                )

                Circle()
                    .fill(connected ? gaugeColor : Palette.textDisabled)
                    .frame(width: stroke * 0.84, height: stroke * 0.84)
                    .position(centre)

                VStack(spacing: 0) {
                    Text("\(speed)")
                        .font(.system(size: side * 0.22, weight: .black, design: .rounded))
                        .foregroundColor(connected ? Palette.textPrimary : Palette.textDisabled)
                    Text("PWM  0 - \(maxSpeed)")
                        .font(.system(size: max(8, side * 0.055), weight: .medium))
                        .foregroundColor(Palette.textSecondary)
                }
                .position(x: centre.x, y: centre.y + side * 0.04)
            }
            .animation(.easeInOut(duration: 0.42), value: fraction)
        }
    }
}

/// A stroked arc measured in degrees, clockwise from three o'clock like the Android Canvas.
private struct Arc: Shape {
    let start: Double
    let sweep: Double
    let radius: CGFloat
    let centre: CGPoint

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addArc(
            center: centre,
            radius: radius,
            startAngle: .degrees(start),
            endAngle: .degrees(start + sweep),
            clockwise: false
        )
        return path
    }
}

/// The speedometer plus its `−` / `+` trim buttons.
///
/// This panel sits on the left of the dashboard, so the trim buttons stack down its outer (left)
/// edge where the left thumb rests, leaving the right thumb free for the drive pad.
struct SpeedPanel: View {
    let speed: Int
    let maxSpeed: Int
    let connected: Bool
    let onIncrease: () -> Void
    let onDecrease: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(spacing: 14) {
                SpeedTrimButton(systemImage: "plus", accent: Palette.neonCyan, action: onIncrease)
                SpeedTrimButton(systemImage: "minus", accent: Palette.neonRed, action: onDecrease)
            }

            VStack(spacing: 2) {
                Text("SPEED")
                    .font(.system(size: 13, weight: .bold))
                    .tracking(1.5)
                    .foregroundColor(Palette.textSecondary)

                // Fit rather than fill: a GeometryReader left to expand stretched the dial into
                // the surrounding layout and pushed the labels off the bottom of the screen.
                Speedometer(speed: speed, maxSpeed: maxSpeed, connected: connected)
                    .aspectRatio(1, contentMode: .fit)

                Text("STEP \(ControlViewModel.speedStep)")
                    .font(.system(size: 9, weight: .medium))
                    .tracking(1)
                    .foregroundColor(Palette.textSecondary)
            }
        }
    }
}

private struct SpeedTrimButton: View {
    let systemImage: String
    let accent: Color
    let action: () -> Void

    /// Holding beats tapping: 150 to 255 is seven separate taps otherwise, which is far too slow
    /// to adjust between corners. A short lead-in keeps a deliberate single tap from repeating.
    private static let repeatDelay: TimeInterval = 0.35
    private static let repeatInterval: TimeInterval = 0.15

    @State private var pressed = false
    @State private var repeatTask: Task<Void, Never>?

    private func startRepeating() {
        repeatTask?.cancel()
        repeatTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(Self.repeatDelay * 1_000_000_000))
            while !Task.isCancelled {
                action()
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                try? await Task.sleep(nanoseconds: UInt64(Self.repeatInterval * 1_000_000_000))
            }
        }
    }

    private func stopRepeating() {
        repeatTask?.cancel()
        repeatTask = nil
    }

    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: 26, weight: .bold))
            .foregroundColor(accent)
            .frame(width: 62, height: 62)
            .background(Circle().fill(pressed ? accent.opacity(0.22) : Palette.carbonSurfaceHigh))
            .overlay(Circle().stroke(pressed ? accent : Palette.carbonOutline, lineWidth: pressed ? 2 : 1))
            .scaleEffect(pressed ? 0.88 : 1)
            .animation(.spring(response: 0.18, dampingFraction: 0.5), value: pressed)
            .contentShape(Circle())
            .onPressGesture(
                onPress: {
                    pressed = true
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    action()
                    startRepeating()
                },
                onRelease: {
                    pressed = false
                    stopRepeating()
                }
            )
            .onDisappear { stopRepeating() }
    }
}

extension Color {
    /// Linear blend towards another colour, so the gauge can shade green → amber → red.
    func blend(to other: Color, amount: Double) -> Color {
        let t = min(max(amount, 0), 1)
        let a = UIColor(self)
        let b = UIColor(other)

        var ar: CGFloat = 0, ag: CGFloat = 0, ab: CGFloat = 0, aa: CGFloat = 0
        var br: CGFloat = 0, bg: CGFloat = 0, bb: CGFloat = 0, ba: CGFloat = 0
        a.getRed(&ar, green: &ag, blue: &ab, alpha: &aa)
        b.getRed(&br, green: &bg, blue: &bb, alpha: &ba)

        return Color(
            red: Double(ar + (br - ar) * t),
            green: Double(ag + (bg - ag) * t),
            blue: Double(ab + (bb - ab) * t)
        )
    }
}
