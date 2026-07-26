import SwiftUI

/// Dark racing dashboard palette — near-black carbon with neon instrument accents. Matches the
/// Android app's `Color.kt` value for value.
enum Palette {
    static let carbonBlack = Color(red: 0.027, green: 0.035, blue: 0.051)      // #07090D
    static let carbonSurface = Color(red: 0.071, green: 0.086, blue: 0.122)    // #12161F
    static let carbonSurfaceHigh = Color(red: 0.106, green: 0.129, blue: 0.188) // #1B2130
    static let carbonOutline = Color(red: 0.165, green: 0.196, blue: 0.263)    // #2A3243

    static let neonCyan = Color(red: 0.0, green: 0.898, blue: 1.0)             // #00E5FF
    static let neonOrange = Color(red: 1.0, green: 0.478, blue: 0.102)         // #FF7A1A
    static let neonAmber = Color(red: 1.0, green: 0.773, blue: 0.192)          // #FFC531
    static let neonGreen = Color(red: 0.169, green: 0.910, blue: 0.478)        // #2BE87A
    static let neonRed = Color(red: 1.0, green: 0.176, blue: 0.247)            // #FF2D3F
    static let neonRedDeep = Color(red: 0.702, green: 0.0, blue: 0.106)        // #B3001B

    static let textPrimary = Color(red: 0.910, green: 0.925, blue: 0.957)      // #E8ECF4
    static let textSecondary = Color(red: 0.545, green: 0.580, blue: 0.659)    // #8B94A8
    static let textDisabled = Color(red: 0.322, green: 0.361, blue: 0.439)     // #525C70

    /// Connection status colours, mapped one-to-one onto `ConnectionState`.
    static func status(for state: ConnectionState) -> Color {
        switch state {
        case .disconnected: return neonRed
        case .connecting: return neonOrange
        case .connected: return neonGreen
        }
    }
}

enum Branding {
    static let appName = "Rocket Pocket"
    static let tagline = "Bluetooth Racing Car"
    static let team = "Team Rocket Pocket"
    static let university = "Horus University of Egypt"
    static let faculty = "Faculty of Artificial Intelligence"
    static let facultyShort = "Faculty of AI"
}
