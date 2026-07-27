import AVFoundation
import Foundation

/// The app's three status alerts.
///
/// These exist to be heard while the driver is watching the car rather than the screen, which
/// drives two decisions:
///
/// - The session category is `.playback` with `.mixWithOthers`, so an alert still sounds when the
///   ring switch is off. Overriding the silent switch is normally rude; here the alerts report
///   that a moving vehicle has lost its connection, which is worth hearing. The mute toggle below
///   gives the choice back to the user, and the preference is remembered.
/// - Players are preloaded, because allocating an `AVAudioPlayer` at the moment the link drops
///   would add exactly the delay that makes an alert useless.
@MainActor
final class SoundPlayer {

    enum Alert: String, CaseIterable {
        case connected
        case disconnected
        case emergency
    }

    static let shared = SoundPlayer()

    private static let muteKey = "rocketpocket.soundMuted"

    private var players: [Alert: AVAudioPlayer] = [:]
    private var sessionReady = false

    /// Persisted so the choice survives a relaunch — nobody wants to re-mute before every heat.
    var isMuted: Bool {
        get { UserDefaults.standard.bool(forKey: Self.muteKey) }
        set { UserDefaults.standard.set(newValue, forKey: Self.muteKey) }
    }

    private init() {
        preload()
    }

    private func preload() {
        for alert in Alert.allCases {
            guard let url = Bundle.main.url(forResource: alert.rawValue, withExtension: "wav"),
                  let player = try? AVAudioPlayer(contentsOf: url)
            else { continue }
            player.prepareToPlay()
            players[alert] = player
        }
    }

    /// Activating the session is deferred to the first alert so the app does not touch the audio
    /// system — and briefly duck whatever the user is listening to — merely by launching.
    private func activateSessionIfNeeded() {
        guard !sessionReady else { return }
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
        try? session.setActive(true)
        sessionReady = true
    }

    func play(_ alert: Alert) {
        guard !isMuted, let player = players[alert] else { return }
        activateSessionIfNeeded()
        // Rewind rather than ignore: two drops in quick succession should each be audible.
        player.currentTime = 0
        player.play()
    }
}
