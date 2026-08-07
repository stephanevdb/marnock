import Combine
import Foundation
import SwiftUI

/// Shared navigation for main window + menu-bar deep links.
@MainActor
final class NavigationState: ObservableObject {
    @Published var section: SidebarSection = .home
}
