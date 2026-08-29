import AppKit
import Foundation

final class ClipboardMonitor: @unchecked Sendable {
    private var timer: Timer?
    private var lastChangeCount: Int = NSPasteboard.general.changeCount
    private var lastText: String = ""
    private var suppressUntil: Date = .distantPast
    private var activateObserver: NSObjectProtocol?
    var enabled: Bool = false
    var onLocalChange: ((String) -> Void)?

    func start() {
        stop()
        lastChangeCount = NSPasteboard.general.changeCount
        let t = Timer(timeInterval: 0.25, repeats: true) { [weak self] _ in
            self?.tick()
        }
        RunLoop.main.add(t, forMode: .common)
        timer = t
        activateObserver = NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.didActivateApplicationNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.tick()
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        if let activateObserver {
            NSWorkspace.shared.notificationCenter.removeObserver(activateObserver)
            self.activateObserver = nil
        }
    }

    func currentString() -> String? {
        NSPasteboard.general.string(forType: .string)?.nilIfBlank
    }

    /// Mark the current pasteboard as the last-seen local clip and return it (for enable/reconnect flush).
    func snapshotCurrent() -> String? {
        guard enabled, let text = currentString() else { return nil }
        lastText = text
        lastChangeCount = NSPasteboard.general.changeCount
        return text
    }

    private func tick() {
        guard enabled else { return }
        let pb = NSPasteboard.general
        guard pb.changeCount != lastChangeCount else { return }
        lastChangeCount = pb.changeCount
        if Date() < suppressUntil { return }
        guard let text = pb.string(forType: .string)?.nilIfBlank, text != lastText else { return }
        lastText = text
        onLocalChange?(text)
    }

    func applyRemote(_ text: String) {
        if text == lastText { return }
        suppressUntil = Date().addingTimeInterval(0.75)
        let pb = NSPasteboard.general
        pb.clearContents()
        guard pb.setString(text, forType: .string) else { return }
        lastText = text
        lastChangeCount = pb.changeCount
    }
}

private extension String {
    var nilIfBlank: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : self
    }
}
