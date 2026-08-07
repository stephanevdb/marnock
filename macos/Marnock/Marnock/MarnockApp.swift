import SwiftUI
import AppKit
import UserNotifications

@main
struct MarnockApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var model = AppModel()
    @StateObject private var updates = UpdateModel()
    @StateObject private var navigation = NavigationState()

    var body: some Scene {
        Window("Marnock", id: "main") {
            ContentView()
                .environmentObject(model)
                .environmentObject(updates)
                .environmentObject(navigation)
                .onOpenURL { url in
                    model.handleShareURL(url)
                }
        }
        .defaultSize(width: 980, height: 660)
        .commands {
            CommandGroup(replacing: .newItem) {}
        }

        MenuBarExtra {
            MenuBarStatus()
                .environmentObject(model)
                .environmentObject(updates)
                .environmentObject(navigation)
                .onAppear {
                    appDelegate.model = model
                    if !appDelegate.didStartServices {
                        appDelegate.didStartServices = true
                        model.start()
                        updates.checkOnLaunch()
                    }
                }
        } label: {
            HStack(spacing: 3) {
                menuBarIcon
                if model.deviceStatus.battery >= 0 {
                    Text(menuBatteryText)
                        .monospacedDigit()
                }
            }
        }
        .menuBarExtraStyle(.window)
    }

    private var menuBatteryText: String {
        let pct = "\(model.deviceStatus.battery)%"
        return model.deviceStatus.charging ? "\(pct)⚡" : pct
    }

    @ViewBuilder
    private var menuBarIcon: some View {
        Image(systemName: menuIconName)
            .renderingMode(.template)
    }

    private var menuIconName: String {
        switch model.path {
        case .offline: return "iphone.slash"
        case .lan: return "iphone.and.arrow.forward"
        case .relay: return "network"
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate, UNUserNotificationCenterDelegate {
    weak var model: AppModel?
    var didStartServices = false
    private var didSuppressLaunchWindow = false

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        if AppModel.canUseUserNotificationsPublic {
            UNUserNotificationCenter.current().delegate = self
        }
        DispatchQueue.main.async { [weak self] in
            guard let self, !self.didSuppressLaunchWindow else { return }
            self.didSuppressLaunchWindow = true
            for window in NSApp.windows {
                let isMain = window.title == "Marnock"
                    || (window.identifier?.rawValue.contains("main") == true)
                guard isMain, window.styleMask.contains(.titled) else { continue }
                window.close()
            }
        }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        false
    }

    func application(_ application: NSApplication, open urls: [URL]) {
        for url in urls {
            if url.isFileURL {
                model?.sendFile(url: url)
            } else {
                model?.handleShareURL(url)
            }
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        let key = info["key"] as? String ?? response.notification.request.identifier
        let actionId = response.actionIdentifier
        if actionId != UNNotificationDefaultActionIdentifier,
           actionId != UNNotificationDismissActionIdentifier {
            let reply = (response as? UNTextInputNotificationResponse)?.userText
            Task { @MainActor in
                model?.invokeNotificationAction(key: key, actionId: actionId, reply: reply)
            }
        }
        completionHandler()
    }
}
