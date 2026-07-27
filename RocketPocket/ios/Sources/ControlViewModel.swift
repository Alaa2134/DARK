import Combine
import Foundation
import SwiftUI

/// Holds every piece of dashboard state and is the only place that decides whether a command is
/// allowed to reach the car. Mirrors the Android app's `ControlViewModel`.
@MainActor
final class ControlViewModel: ObservableObject {

    static let defaultSpeed = SpeedTelemetry.defaultSpeed
    static let speedStep = SpeedTelemetry.step
    static let minSpeed = SpeedTelemetry.minSpeed
    static let maxSpeed = SpeedTelemetry.maxSpeed

    static let notConnectedMessage = "Connect to Rocket Pocket first"

    /// Comfortably inside the firmware's 1000 ms watchdog window.
    private static let keepAliveInterval: TimeInterval = 0.3

    /// One "connect first" notice per this window, however many buttons get pressed.
    private static let noticeInterval: TimeInterval = 3

    let bluetooth = BluetoothController()

    @Published private(set) var speed = ControlViewModel.defaultSpeed
    @Published private(set) var activeCommand: Character?
    @Published var showDevicePicker = false
    @Published var showDiagnostics = false

    /// Latest telemetry from the car; nil until a DIAG line arrives.
    @Published private(set) var diagnostics: Diagnostics?
    @Published var message: String?

    private var keepAliveTask: Task<Void, Never>?
    private var lastNoticeAt: Date = .distantPast
    private var cancellables = Set<AnyCancellable>()

    init() {
        bluetooth.$lastReceived
            .compactMap { $0 }
            .sink { [weak self] line in self?.handle(line: line) }
            .store(in: &cancellables)

        bluetooth.messages
            .sink { [weak self] text in
                guard let self else { return }
                if text == BluetoothController.connectionLostMessage {
                    self.stopKeepAlive()
                    self.activeCommand = nil
                }
                self.message = text
            }
            .store(in: &cancellables)

        // Alerts follow the connection state itself rather than message strings, so rewording a
        // notice can never silently break the sound that goes with it.
        bluetooth.$connectionState
            .removeDuplicates()
            .scan((ConnectionState.disconnected, ConnectionState.disconnected)) { pair, next in
                (pair.1, next)
            }
            .sink { previous, current in
                switch (previous, current) {
                case (_, .connected):
                    SoundPlayer.shared.play(.connected)
                case (.connected, .disconnected):
                    // Only a link that was actually up counts as lost; a failed attempt is not.
                    SoundPlayer.shared.play(.disconnected)
                default:
                    break
                }
            }
            .store(in: &cancellables)
    }

    var isMuted: Bool { SoundPlayer.shared.isMuted }

    func toggleMute() {
        SoundPlayer.shared.isMuted.toggle()
        objectWillChange.send()
    }

    // MARK: - Incoming telemetry

    /// The car is authoritative about its own PWM, so `SPEED:xxx` overwrites the local value.
    private func handle(line: String) {
        // DIAG first: it is the more specific format, and a SPEED parse would reject it anyway.
        if let report = Diagnostics.parse(line) {
            diagnostics = report
            return
        }
        guard let value = SpeedTelemetry.parse(line) else { return }
        speed = value
    }

    // MARK: - Connection

    func onConnectTapped() {
        if bluetooth.connectionState.isConnected {
            disconnect()
        } else {
            bluetooth.startScan()
            showDevicePicker = true
        }
    }

    func connect(to car: DiscoveredCar) {
        showDevicePicker = false
        bluetooth.connect(to: car)
    }

    /// Called when the dashboard appears. Reconnecting to the car this phone last drove makes
    /// the app race-ready on launch without anyone opening the picker.
    func attemptAutoConnect() {
        bluetooth.reconnectToLastCar()
    }

    /// Jumps straight to a speed instead of stepping there one press at a time.
    func setSpeed(_ target: Int) {
        guard requireConnection() else { return }
        let clamped = min(max(target, Self.minSpeed), Self.maxSpeed)
        // The car only understands + and -, so walk it there in the steps it does understand.
        let difference = clamped - speed
        let steps = abs(difference) / Self.speedStep
        guard steps > 0 else { return }
        let character = difference > 0 ? Command.speedUp : Command.speedDown
        for _ in 0..<steps { bluetooth.send(character) }
        speed = min(max(speed + (difference > 0 ? steps : -steps) * Self.speedStep,
                        Self.minSpeed), Self.maxSpeed)
    }

    func disconnect() {
        stopKeepAlive()
        activeCommand = nil
        bluetooth.disconnect()
    }

    // MARK: - Driving

    /// Touch down on one of the eight direction buttons.
    func pressDirection(_ command: Character) {
        guard requireConnection() else { return }
        activeCommand = command
        bluetooth.send(command)
        startKeepAlive(command)
    }

    /// Touch up and touch cancel both land here, so the car always stops.
    func releaseDirection() {
        stopKeepAlive()
        activeCommand = nil
        bluetooth.send(Command.stop)
    }

    /// Re-sends the held command on a short interval.
    ///
    /// A press only puts one character on the wire, so without this the car cannot tell "still
    /// holding forward" from "the phone went out of range mid-corner". The repeat feeds the
    /// firmware watchdog: stop arriving, and the ESP32 cuts the motors within a second.
    private func startKeepAlive(_ command: Character) {
        keepAliveTask?.cancel()
        keepAliveTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: UInt64(Self.keepAliveInterval * 1_000_000_000))
                guard let self, !Task.isCancelled else { return }
                guard self.bluetooth.connectionState.isConnected else { return }

                self.bluetooth.send(command)
            }
        }
    }

    private func stopKeepAlive() {
        keepAliveTask?.cancel()
        keepAliveTask = nil
    }

    func emergencyStop() {
        stopKeepAlive()
        activeCommand = nil
        SoundPlayer.shared.play(.emergency)
        if bluetooth.send(Command.stop) {
            message = "Emergency stop sent"
        } else {
            message = Self.notConnectedMessage
        }
    }

    func increaseSpeed() {
        guard requireConnection() else { return }
        speed = min(speed + Self.speedStep, Self.maxSpeed)
        bluetooth.send(Command.speedUp)
    }

    func decreaseSpeed() {
        guard requireConnection() else { return }
        speed = max(speed - Self.speedStep, Self.minSpeed)
        bluetooth.send(Command.speedDown)
    }

    /// Gate for every command that would move the car. The notice is rate-limited: without it,
    /// mashing the pad while disconnected buries the UI in identical banners.
    private func requireConnection() -> Bool {
        if bluetooth.connectionState.isConnected { return true }
        if Date().timeIntervalSince(lastNoticeAt) > Self.noticeInterval {
            lastNoticeAt = Date()
            message = Self.notConnectedMessage
        }
        return false
    }

    // MARK: - Lifecycle safety

    /// The controls are no longer in front of the user, so the car must not keep moving.
    func appLeftForeground() {
        stopKeepAlive()
        activeCommand = nil
        bluetooth.sendImmediately(Command.stop)
    }
}
