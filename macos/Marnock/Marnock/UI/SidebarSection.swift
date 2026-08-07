import Foundation

enum SidebarSection: String, CaseIterable, Identifiable, Hashable {
    case home, messages, calls, notifications, phone, transfer, settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .home: return "Home"
        case .messages: return "Messages"
        case .calls: return "Calls"
        case .notifications: return "Notifications"
        case .phone: return "Phone"
        case .transfer: return "Transfer"
        case .settings: return "Settings"
        }
    }

    var icon: String {
        switch self {
        case .home: return "house"
        case .messages: return "message"
        case .calls: return "phone"
        case .notifications: return "bell"
        case .phone: return "iphone"
        case .transfer: return "arrow.left.arrow.right"
        case .settings: return "gearshape"
        }
    }
}
