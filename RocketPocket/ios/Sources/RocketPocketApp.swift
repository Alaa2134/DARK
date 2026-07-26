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
