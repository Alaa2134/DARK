import SwiftUI

@main
struct RocketPocketApp: App {
    @StateObject private var model = ControlViewModel()
    @State private var showSplash = true

    var body: some Scene {
        WindowGroup {
            ZStack {
                if showSplash {
                    SplashView { showSplash = false }
                        .transition(.opacity)
                } else {
                    ControlView(model: model)
                        .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.35), value: showSplash)
            // The dashboard is a physical control surface, not a document. On an Arabic device
            // the layout direction is right-to-left and SwiftUI mirrors every HStack, which put
            // LEFT on the right of the pad, RIGHT on the left, and swapped the speed panel with
            // the drive pad. Mirroring is correct for text and wrong here, because these map to
            // directions a real car will travel. Android already pins this; iOS did not.
            .environment(\.layoutDirection, .leftToRight)
            .preferredColorScheme(.dark)
            .statusBarHidden(true)
            .persistentSystemOverlays(.hidden)
            .onAppear {
                // A dropped screen mid-race would be a lost heat.
                UIApplication.shared.isIdleTimerDisabled = true
            }
        }
    }
}
