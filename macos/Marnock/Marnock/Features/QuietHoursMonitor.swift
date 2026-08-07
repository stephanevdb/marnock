import AppKit
import Foundation

/// Best-effort lock / screensaver detection for quiet-hours notification suppression.
@MainActor
final class QuietHoursMonitor: ObservableObject {
    @Published private(set) var screenLocked = false

    private var observers: [NSObjectProtocol] = []

    func start() {
        let nc = DistributedNotificationCenter.default()
        let lock = nc.addObserver(forName: .init("com.apple.screenIsLocked"), object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.screenLocked = true }
        }
        let unlock = nc.addObserver(forName: .init("com.apple.screenIsUnlocked"), object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.screenLocked = false }
        }
        observers = [lock, unlock]
    }

    deinit {
        let nc = DistributedNotificationCenter.default()
        observers.forEach { nc.removeObserver($0) }
    }
}
