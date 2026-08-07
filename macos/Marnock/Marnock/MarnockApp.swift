import SwiftUI
import AppKit

@main
struct MarnockApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var model = AppModel()
    @StateObject private var updates = UpdateModel()

    var body: some Scene {
        Window("Marnock", id: "main") {
            ContentView()
                .environmentObject(model)
                .environmentObject(updates)
                .onOpenURL { url in
                    model.handleShareURL(url)
                }
        }
        .defaultSize(width: 980, height: 660)
        .commands {
            CommandGroup(replacing: .newItem) {}
        }

        MenuBarExtra(menuTitle, systemImage: menuIcon) {
            MenuBarStatus()
                .environmentObject(model)
                .environmentObject(updates)
                .onAppear {
                    appDelegate.model = model
                    if !appDelegate.didStartServices {
                        appDelegate.didStartServices = true
                        model.start()
                        updates.checkOnLaunch()
                    }
                }
        }
        .menuBarExtraStyle(.window)
    }

    private var menuTitle: String {
        if model.deviceStatus.battery >= 0 {
            return "\(model.deviceStatus.battery)%"
        }
        return "Marnock"
    }

    private var menuIcon: String {
        switch model.path {
        case .lan: return "iphone.and.arrow.forward"
        case .relay: return "network"
        case .offline: return "iphone.slash"
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    weak var model: AppModel?
    var didStartServices = false
    private var didSuppressLaunchWindow = false

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        // The main Window scene may appear at launch; close only that titled window.
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
}
