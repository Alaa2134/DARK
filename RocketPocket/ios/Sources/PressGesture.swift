import SwiftUI
import UIKit

/// Touch-down / touch-up / touch-**cancelled** reporting for a hold-to-drive button.
///
/// SwiftUI's `DragGesture` is the obvious tool and the wrong one here. It has no cancelled
/// callback at all: when iOS interrupts a touch — a Control Centre pull, an incoming call, a
/// notification drag — `onEnded` may simply never fire, and a car being driven by that button
/// would keep going. Watching for the gesture to "go quiet" cannot substitute, because a finger
/// held perfectly still produces no updates either and would be indistinguishable from an
/// interruption.
///
/// `UILongPressGestureRecognizer` with a zero minimum duration has the state machine that is
/// actually needed — `.began`, `.ended`, `.cancelled`, `.failed` — mapping exactly onto Android's
/// ACTION_DOWN / ACTION_UP / ACTION_CANCEL, which is what the rest of this project is built
/// around. Every terminal state funnels into `onRelease`, so no path leaves the car driving.
struct PressGesture: UIViewRepresentable {

    let onPress: () -> Void
    let onRelease: () -> Void

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isMultipleTouchEnabled = true

        let recognizer = UILongPressGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handle(_:))
        )
        recognizer.minimumPressDuration = 0
        // The finger will drift while driving; that must not end the press. Sliding off the
        // button entirely still cancels, because the recognizer's view no longer contains it.
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = context.coordinator

        view.addGestureRecognizer(recognizer)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.onPress = onPress
        context.coordinator.onRelease = onRelease
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        // The view being torn down mid-press must not leave the command latched.
        coordinator.releaseIfHeld()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onPress: onPress, onRelease: onRelease)
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onPress: () -> Void
        var onRelease: () -> Void
        private var held = false

        init(onPress: @escaping () -> Void, onRelease: @escaping () -> Void) {
            self.onPress = onPress
            self.onRelease = onRelease
        }

        @objc func handle(_ recognizer: UILongPressGestureRecognizer) {
            switch recognizer.state {
            case .began:
                guard !held else { return }
                held = true
                onPress()

            case .ended, .cancelled, .failed:
                releaseIfHeld()

            default:
                break
            }
        }

        func releaseIfHeld() {
            guard held else { return }
            held = false
            onRelease()
        }

        /// Lets a direction button and a speed button be held at the same time — without this,
        /// UIKit would let only one recognizer win and the second press would be swallowed.
        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
        ) -> Bool {
            true
        }
    }
}

extension View {
    /// Attaches hold-to-drive behaviour with true cancellation support.
    func onPressGesture(
        onPress: @escaping () -> Void,
        onRelease: @escaping () -> Void
    ) -> some View {
        overlay(PressGesture(onPress: onPress, onRelease: onRelease))
    }
}
