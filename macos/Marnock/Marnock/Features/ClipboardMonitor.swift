import AppKit
import Foundation

final class ClipboardMonitor: @unchecked Sendable {
    private var timer: Timer?
    private var lastChangeCount: Int = NSPasteboard.general.changeCount
    private var lastText: String = ""
    private var suppressUntil: Date = .distantPast
    var enabled: Bool = false
    var onLocalChange: ((String) -> Void)?

    func start() {
        stop()
        timer = Timer.scheduledTimer(withTimeInterval: 0.45, repeats: true) { [weak self] _ in
            self?.tick()
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
    }

    private func tick() {
        guard enabled else { return }
        let pb = NSPasteboard.general
        guard pb.changeCount != lastChangeCount else { return }
        lastChangeCount = pb.changeCount
        if Date() < suppressUntil { return }
        guard let text = pb.string(forType: .string), text != lastText else { return }
        lastText = text
        onLocalChange?(text)
    }

    func applyRemote(_ text: String) {
        // Always apply inbound clips when connected; `enabled` only gates Mac→phone outbound.
        if text == lastText { return }
        suppressUntil = Date().addingTimeInterval(0.75)
        lastText = text
        let pb = NSPasteboard.general
        pb.clearContents()
        pb.setString(text, forType: .string)
        lastChangeCount = pb.changeCount
    }
}
