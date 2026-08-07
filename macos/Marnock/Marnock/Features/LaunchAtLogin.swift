import Foundation
import ServiceManagement
import SwiftUI

enum LaunchAtLogin {
    static var isEnabled: Bool {
        SMAppService.mainApp.status == .enabled
    }

    static var statusDescription: String {
        switch SMAppService.mainApp.status {
        case .enabled:
            return "Opens automatically at login"
        case .requiresApproval:
            return "Allow Marnock in System Settings → General → Login Items"
        case .notFound:
            return "Packaged .app required (not available from bare swift run)"
        case .notRegistered:
            return "Off"
        @unknown default:
            return "Unknown"
        }
    }

    @discardableResult
    static func setEnabled(_ enabled: Bool) -> Bool {
        do {
            if enabled {
                try SMAppService.mainApp.register()
            } else {
                try SMAppService.mainApp.unregister()
            }
            return true
        } catch {
            return false
        }
    }
}
