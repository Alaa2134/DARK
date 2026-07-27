import SwiftUI

/// Two-second opening screen: the car logo, the app name, the team and the university,
/// everything fading and lifting into place on a stagger.
struct SplashView: View {
    let onFinished: () -> Void

    @State private var started = false
    @State private var pulse = false

    var body: some View {
        ZStack {
            RadialGradient(
                colors: [Palette.carbonSurface, Palette.carbonBlack],
                center: .center,
                startRadius: 0,
                endRadius: 600
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                Image(systemName: "bolt.car.fill")
                    .font(.system(size: 74, weight: .bold))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Palette.neonCyan, Palette.neonOrange],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .scaleEffect(pulse ? 1.05 : 0.95)
                    .opacity(started ? 1 : 0)
                    .animation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: pulse)

                staggered(delay: 0.18) {
                    Text(Branding.appName)
                        .font(.system(size: 38, weight: .black))
                        .tracking(2)
                        .foregroundColor(Palette.textPrimary)
                        .padding(.top, 16)
                }

                staggered(delay: 0.32) {
                    Text(Branding.tagline)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Palette.neonCyan)
                        .padding(.top, 6)
                }

                staggered(delay: 0.46) {
                    LinearGradient(
                        colors: [.clear, Palette.neonOrange, .clear],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: 120, height: 2)
                    .padding(.top, 18)
                }

                staggered(delay: 0.56) {
                    Text(Branding.team.uppercased())
                        .font(.system(size: 13, weight: .bold))
                        .tracking(1.5)
                        .foregroundColor(Palette.textPrimary)
                        .padding(.top, 14)
                }

                staggered(delay: 0.68) {
                    Text(Branding.university)
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(1)
                        .foregroundColor(Palette.neonAmber)
                        .padding(.top, 10)
                }

                staggered(delay: 0.78) {
                    Text(Branding.faculty)
                        .font(.system(size: 9, weight: .medium))
                        .tracking(1)
                        .foregroundColor(Palette.textSecondary)
                        .padding(.top, 4)
                }
            }
        }
        .onAppear {
            started = true
            pulse = true
            Task {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                onFinished()
            }
        }
    }

    /// Fades and lifts a single line into place after `delay`.
    @ViewBuilder
    private func staggered<Content: View>(
        delay: Double,
        @ViewBuilder content: () -> Content
    ) -> some View {
        content()
            .opacity(started ? 1 : 0)
            .offset(y: started ? 0 : 18)
            .animation(.easeOut(duration: 0.42).delay(delay), value: started)
    }
}
