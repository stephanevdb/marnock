import SwiftUI
import AppKit

@main
struct MarnockApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup("Marnock") {
            ContentView()
                .environmentObject(model)
                .onAppear {
                    model.start()
                    appDelegate.model = model
                }
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
