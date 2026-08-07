import AppKit
import Foundation

/// Best-effort lock / screensaver detection for quiet-hours notification suppression.
final class QuietHoursMonitor: ObservableObject {
    @Published private(set) var screenLocked = false

    private var observers: [NSObjectProtocol] = []

    func start() {
        stop()
        let nc = DistributedNotificationCenter.default()
        // Callbacks already hop to the main queue — no Task / @MainActor capture needed.
        let lock = nc.addObserver(forName: .init("com.apple.screenIsLocked"), object: nil, queue: .main) { [weak self] _ in
            self?.screenLocked = true
        }
        let unlock = nc.addObserver(forName: .init("com.apple.screenIsUnlocked"), object: nil, queue: .main) { [weak self] _ in
            self?.screenLocked = false
        }
        observers = [lock, unlock]
    }

    func stop() {
        let nc = DistributedNotificationCenter.default()
        observers.forEach { nc.removeObserver($0) }
        observers.removeAll()
    }

    deinit {
        stop()
    }
}
