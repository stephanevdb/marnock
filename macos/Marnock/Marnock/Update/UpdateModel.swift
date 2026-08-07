import AppKit
import Foundation
import UserNotifications

@MainActor
final class UpdateModel: ObservableObject {
    @Published var available: AppUpdateInfo?
    @Published var dismissed = false
    @Published var checking = false
    @Published var installing = false
    @Published var progress: Double = 0
    @Published var statusMessage: String = ""
    @Published var errorMessage: String?

    private var notifiedVersion: String? {
        get { UserDefaults.standard.string(forKey: "updateNotifiedVersion") }
        set { UserDefaults.standard.set(newValue, forKey: "updateNotifiedVersion") }
    }

    func checkOnLaunch() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
        Task { await check(notify: true) }
    }

    func check(notify: Bool = false) async {
        checking = true
        errorMessage = nil
        defer { checking = false }
        let update = await UpdateChecker.checkLatest()
        available = update
        if let update, notify, notifiedVersion != update.version {
            postNotification(version: update.version)
            notifiedVersion = update.version
        }
        if update == nil {
            statusMessage = "You're on the latest version (\(UpdateChecker.currentVersion))."
        } else {
            statusMessage = "Update \(update!.version) available."
        }
    }

    func install() {
        guard let update = available else { return }
        let alert = NSAlert()
        alert.messageText = "Install Marnock \(update.version)?"
        alert.informativeText = "Marnock will download the update, replace this app, and relaunch."
        alert.addButton(withTitle: "Update")
        alert.addButton(withTitle: "Cancel")
        guard alert.runModal() == .alertFirstButtonReturn else { return }

        installing = true
        errorMessage = nil
        progress = 0
        Task {
            do {
                try await AppUpdater.downloadAndInstall(from: update.downloadURL) { [weak self] p in
                    Task { @MainActor in self?.progress = p }
                }
            } catch {
                await MainActor.run {
                    installing = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }

    private func postNotification(version: String) {
        // Skip if not a real .app (UserNotifications requires bundle).
        guard Bundle.main.bundleURL.pathExtension == "app" else { return }
        let content = UNMutableNotificationContent()
        content.title = "Marnock update available"
        content.body = "Version \(version) is ready to install."
        content.sound = .default
        let req = UNNotificationRequest(
            identifier: "marnock-update-\(version)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(req)
    }
}
