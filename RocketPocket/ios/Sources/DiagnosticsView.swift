import SwiftUI

/// The full diagnostics page, opened from the dashboard.
///
/// It states plainly what cannot be measured as well as what can. A driver who knows the app
/// has no battery reading will go and check the pack; one shown a plausible-looking invented
/// figure will trust it and run flat mid-heat.
struct DiagnosticsView: View {
    let diagnostics: Diagnostics?
    let rssi: Int?
    let connected: Bool
    let carName: String?

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Palette.carbonBlack.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 14) {
                        if !connected {
                            card("LINK") {
                                row("Status", "Not connected", tint: Palette.neonRed)
                                note("Connect to the car to read its telemetry.")
                            }
                        } else if let d = diagnostics {
                            card("LINK") {
                                row("Car", carName ?? BluetoothController.carName)
                                if let rssi {
                                    row("Signal", "\(rssi) dBm", tint: rssi < -80 ? Palette.neonAmber : Palette.neonGreen)
                                }
                                row("Watchdog stops", "\(d.watchdogTrips)",
                                    tint: d.watchdogTrips == 0 ? Palette.neonGreen : Palette.neonAmber)
                                note(d.watchdogTrips == 0
                                     ? "The car has never had to stop itself. The link is holding."
                                     : "Each one is a moment the commands stopped arriving and the car cut its own motors. Rising numbers mean a weak link.")
                            }

                            card("MOTORS") {
                                row("Left duty", "\(d.leftDuty)")
                                row("Right duty", "\(d.rightDuty)")
                                row("Speed setting", "\(d.speed) / 255")
                                note("Duties are what the firmware last applied. Whether a motor physically responded needs a current sensor.")
                            }

                            card("BOARD") {
                                row("Uptime", d.uptimeText)
                                row("Free memory", d.freeHeapText)
                                note("An uptime that keeps resetting means the car is browning out — usually a flat pack or a shared supply.")
                            }

                            card("WIRING FLAGS") {
                                row("Motors swapped", d.motorsSwapped ? "YES" : "no")
                                row("Left inverted", d.leftInverted ? "YES" : "no")
                                row("Right inverted", d.rightInverted ? "YES" : "no")
                                row("Brake on stop", d.brakeOnStop ? "YES" : "no",
                                    tint: d.brakeOnStop ? Palette.neonGreen : Palette.neonAmber)
                                row("Reverse curves steer like a car", d.reverseCurveLikeCar ? "YES" : "no")
                                note("Read from the sketch actually running on the board, so this shows what was flashed rather than what was intended.")
                            }
                        } else {
                            card("TELEMETRY") {
                                row("Status", "Waiting…", tint: Palette.neonAmber)
                                note("The car sends diagnostics once a second. If nothing appears, the board is running an older sketch.")
                            }
                        }

                        card("NEEDS EXTRA HARDWARE") {
                            unavailable("Battery level", "a voltage divider on an ADC pin — two resistors")
                            unavailable("Temperature", "a DS18B20 or NTC; this ESP32's internal sensor reports a fixed value")
                            unavailable("Motor connected / jammed", "a current sensor such as ACS712 or INA219")
                            note("These are left out rather than estimated. A guessed battery figure gets believed, and that is worse than no figure.")
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Diagnostics")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }.foregroundColor(Palette.neonCyan)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: - Building blocks

    @ViewBuilder
    private func card<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .tracking(1.5)
                .foregroundColor(Palette.neonCyan)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 14).fill(Palette.carbonSurfaceHigh.opacity(0.5)))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.carbonOutline, lineWidth: 1))
    }

    private func row(_ label: String, _ value: String, tint: Color = Palette.textPrimary) -> some View {
        HStack {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(Palette.textSecondary)
            Spacer()
            Text(value)
                .font(.system(size: 13, weight: .semibold, design: .monospaced))
                .foregroundColor(tint)
        }
    }

    private func unavailable(_ label: String, _ requirement: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(Palette.textSecondary)
            Spacer()
            Text("needs \(requirement)")
                .font(.system(size: 11))
                .foregroundColor(Palette.textDisabled)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: 220, alignment: .trailing)
        }
    }

    private func note(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11))
            .foregroundColor(Palette.textDisabled)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 2)
    }
}

/// The dashboard summary: uptime and, when it matters, the watchdog count.
///
/// Only two numbers, because the driving screen is not the place to read a report — it is the
/// place to notice that something has started going wrong.
struct DiagnosticsChip: View {
    let diagnostics: Diagnostics?
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 7) {
                Image(systemName: "waveform.path.ecg")
                    .font(.system(size: 12, weight: .bold))

                if let d = diagnostics {
                    Text(d.uptimeText)
                        .font(.system(size: 10, weight: .semibold, design: .monospaced))
                    if d.watchdogTrips > 0 {
                        // Surfaced only once it is non-zero: a permanent "0" would be noise.
                        Text("⚠︎\(d.watchdogTrips)")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(Palette.neonAmber)
                    }
                } else {
                    Text("DIAG")
                        .font(.system(size: 10, weight: .semibold))
                }
            }
            .foregroundColor(diagnostics == nil ? Palette.textDisabled : Palette.neonCyan)
            .padding(.horizontal, 9)
            .padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 9).fill(Palette.carbonSurfaceHigh.opacity(0.55)))
            .overlay(RoundedRectangle(cornerRadius: 9).stroke(Palette.carbonOutline, lineWidth: 1))
        }
        .accessibilityLabel("Diagnostics")
    }
}
