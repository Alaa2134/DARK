import SwiftUI

/// The landscape dark racing dashboard: speed panel on the left, eight-way drive pad on the
/// right, with the emergency stop in the middle of the pad.
struct ControlView: View {
    @ObservedObject var model: ControlViewModel
    @Environment(\.scenePhase) private var scenePhase

    @State private var entered = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Palette.carbonSurface, Palette.carbonBlack],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 8) {
                header

                HStack(spacing: 16) {
                    SpeedPanel(
                        speed: model.speed,
                        maxSpeed: ControlViewModel.maxSpeed,
                        connected: model.bluetooth.connectionState.isConnected,
                        onIncrease: model.increaseSpeed,
                        onDecrease: model.decreaseSpeed
                    )
                    .frame(maxWidth: .infinity)
                    .offset(x: entered ? 0 : -60)
                    .opacity(entered ? 1 : 0)

                    // A 3x3 grid of nine equal cells is square by nature. Letting it fill half a
                    // landscape screen flattened every button into a wide rectangle, so it is
                    // fitted to a square sized by the available height instead.
                    DirectionPad(
                        enabled: model.bluetooth.connectionState.isConnected,
                        onPress: model.pressDirection,
                        onRelease: model.releaseDirection,
                        onEmergencyStop: model.emergencyStop
                    )
                    .aspectRatio(1, contentMode: .fit)
                    .offset(x: entered ? 0 : 60)
                    .opacity(entered ? 1 : 0)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            // Pinned to the top so it can never cover the drive pad — a banner over the bottom
            // row would swallow the touches meant for BACKWARD.
            if let message = model.message {
                VStack {
                    Text(message)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(Palette.textPrimary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Capsule().fill(Palette.carbonSurfaceHigh))
                        .overlay(Capsule().stroke(Palette.carbonOutline, lineWidth: 1))
                        .shadow(radius: 8)
                    Spacer()
                }
                .padding(.top, 6)
                .transition(.move(edge: .top).combined(with: .opacity))
                .allowsHitTesting(false)
            }
        }
        .animation(.easeOut(duration: 0.42), value: entered)
        .animation(.easeInOut(duration: 0.25), value: model.message)
        .onAppear {
            entered = true
            model.attemptAutoConnect()
        }
        .onChange(of: model.message) { newValue in
            guard newValue != nil else { return }
            // Auto-dismiss. Replacing rather than queueing keeps a burst of presses from
            // building a backlog of identical banners.
            Task {
                try? await Task.sleep(nanoseconds: 2_500_000_000)
                if model.message == newValue { model.message = nil }
            }
        }
        .onChange(of: scenePhase) { phase in
            // Leaving the foreground must stop the car.
            if phase != .active { model.appLeftForeground() }
        }
        .sheet(isPresented: $model.showDevicePicker) {
            DevicePickerView(model: model)
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 1) {
                Text("ROCKET POCKET")
                    .font(.system(size: 20, weight: .black))
                    .tracking(1.5)
                    .foregroundColor(Palette.textPrimary)
                Text(Branding.team)
                    .font(.system(size: 9, weight: .medium))
                    .foregroundColor(Palette.textSecondary)
                Text("\(Branding.university)  •  \(Branding.facultyShort)")
                    .font(.system(size: 9, weight: .medium))
                    .foregroundColor(Palette.neonAmber)
            }

            Spacer()

            MotionIndicator(
                command: model.activeCommand,
                connected: model.bluetooth.connectionState.isConnected
            )
            .padding(.trailing, 8)

            TelemetryStrip(
                lastSent: model.bluetooth.lastSent,
                lastReceived: model.bluetooth.lastReceived
            )

            Button(action: model.toggleMute) {
                Image(systemName: model.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(model.isMuted ? Palette.textDisabled : Palette.neonCyan)
                    .frame(width: 34, height: 30)
                    .background(
                        RoundedRectangle(cornerRadius: 9)
                            .fill(Palette.carbonSurfaceHigh.opacity(0.55))
                    )
            }
            .accessibilityLabel(model.isMuted ? "Unmute alerts" : "Mute alerts")
            .padding(.leading, 8)

            ConnectionStatusPill(
                state: model.bluetooth.connectionState,
                deviceName: model.bluetooth.connectedCarName,
                rssi: model.bluetooth.rssi
            )
            .padding(.horizontal, 10)

            Button(action: model.onConnectTapped) {
                HStack(spacing: 6) {
                    Image(systemName: model.bluetooth.connectionState.isConnected
                          ? "link.badge.plus" : "dot.radiowaves.left.and.right")
                    Text(model.bluetooth.connectionState.isConnected ? "DISCONNECT" : "CONNECT")
                        .font(.system(size: 12, weight: .bold))
                        .tracking(1)
                }
                .foregroundColor(Palette.carbonBlack)
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(model.bluetooth.connectionState.isConnected ? Palette.neonRed : Palette.neonCyan)
                )
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 16).fill(Palette.carbonSurfaceHigh.opacity(0.5)))
    }
}

/// Red when disconnected, orange while connecting (pulsing), green once the link is up.
struct ConnectionStatusPill: View {
    let state: ConnectionState
    let deviceName: String?
    var rssi: Int?

    @State private var pulse = false

    private var label: String {
        if state == .connected, let name = deviceName {
            return "CONNECTED  •  \(name.uppercased())"
        }
        return state.label
    }

    /// Driving out of range is the likeliest way to lose the car, so the link is shown as bars
    /// and goes amber while there is still time to walk back towards it.
    private var signalBars: Int {
        guard let rssi else { return 0 }
        switch rssi {
        case (-60)...: return 4
        case (-70)..<(-60): return 3
        case (-80)..<(-70): return 2
        default: return 1
        }
    }

    private var signalColour: Color {
        signalBars <= 2 ? Palette.neonAmber : Palette.neonGreen
    }

    var body: some View {
        let colour = Palette.status(for: state)
        HStack(spacing: 7) {
            Image(systemName: state == .connected ? "wave.3.right.circle.fill" : "wave.3.right")
                .font(.system(size: 14, weight: .bold))
            Text(label)
                .font(.system(size: 10, weight: .semibold))
                .tracking(1)
                .lineLimit(1)

            if state == .connected, rssi != nil {
                HStack(alignment: .bottom, spacing: 1.5) {
                    ForEach(1...4, id: \.self) { bar in
                        RoundedRectangle(cornerRadius: 0.5)
                            .fill(bar <= signalBars ? signalColour : Palette.textDisabled)
                            .frame(width: 2.5, height: CGFloat(bar) * 2.6 + 2)
                    }
                }
                .accessibilityLabel("Signal \(signalBars) of 4")
            }
        }
        .foregroundColor(colour)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Capsule().fill(colour.opacity(0.14)))
        .overlay(Capsule().stroke(colour.opacity(0.65), lineWidth: 1))
        .opacity(state == .connecting && pulse ? 0.45 : 1)
        .animation(
            state == .connecting
                ? .easeInOut(duration: 0.65).repeatForever(autoreverses: true)
                : .default,
            value: pulse
        )
        .onAppear { pulse = true }
    }
}

/// Live wire monitor: the last byte sent to the car and the last line it sent back.
///
/// This exists so the car can be diagnosed on the phone with no laptop. Press a button and read
/// TX: if the character is right, the app is fine and any wrong movement is wiring.
struct TelemetryStrip: View {
    let lastSent: (character: Character, counter: UInt64)?
    let lastReceived: String?

    @State private var flashing = false

    var body: some View {
        HStack(spacing: 8) {
            Text("TX").font(.system(size: 9, weight: .medium)).foregroundColor(Palette.textSecondary)
            Text(lastSent.map { String($0.character) } ?? "–")
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .foregroundColor(flashing ? Palette.neonAmber : Palette.neonCyan)

            Text("RX").font(.system(size: 9, weight: .medium)).foregroundColor(Palette.textSecondary)
            Text(lastReceived ?? "–")
                .font(.system(size: 9, weight: .medium, design: .monospaced))
                .foregroundColor(lastReceived == nil ? Palette.textDisabled : Palette.textSecondary)
                .lineLimit(1)
                .frame(maxWidth: 132, alignment: .leading)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(RoundedRectangle(cornerRadius: 10).fill(Palette.carbonSurfaceHigh.opacity(0.55)))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.carbonOutline, lineWidth: 1))
        .onChange(of: lastSent?.counter) { _ in
            // The counter changes on every write, so holding a button pulses once per repeat.
            flashing = true
            Task {
                try? await Task.sleep(nanoseconds: 120_000_000)
                flashing = false
            }
        }
    }
}

/// Nearby cars, with Rocket Pocket detected by name and pinned to the top.
struct DevicePickerView: View {
    @ObservedObject var model: ControlViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Palette.carbonBlack.ignoresSafeArea()

                if model.bluetooth.discovered.isEmpty {
                    VStack(spacing: 12) {
                        if model.bluetooth.isScanning {
                            ProgressView().tint(Palette.neonCyan)
                            Text("Searching for the car…")
                        } else {
                            Text("No cars found.\nMake sure the ESP32 is powered and running the BLE sketch.")
                                .multilineTextAlignment(.center)
                        }
                    }
                    .font(.system(size: 14))
                    .foregroundColor(Palette.textSecondary)
                    .padding()
                } else {
                    List(model.bluetooth.discovered) { car in
                        Button {
                            model.connect(to: car)
                            dismiss()
                        } label: {
                            HStack {
                                Image(systemName: car.isRocketPocket ? "car.fill" : "dot.radiowaves.left.and.right")
                                    .foregroundColor(car.isRocketPocket ? Palette.neonGreen : Palette.textSecondary)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(car.name)
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundColor(Palette.textPrimary)
                                    Text(car.isRocketPocket ? "THIS IS THE CAR" : "\(car.rssi) dBm")
                                        .font(.system(size: 9, weight: .medium))
                                        .foregroundColor(car.isRocketPocket ? Palette.neonGreen : Palette.textSecondary)
                                }
                            }
                        }
                        .listRowBackground(Palette.carbonSurfaceHigh)
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .navigationTitle("Nearby Cars")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        model.bluetooth.stopScan()
                        dismiss()
                    }
                    .foregroundColor(Palette.neonCyan)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Rescan") { model.bluetooth.startScan() }
                        .foregroundColor(Palette.neonCyan)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
