import SwiftUI

struct ContentView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var updates: UpdateModel
    @State private var section: SidebarSection = .home

    var body: some View {
        NavigationSplitView {
            List(SidebarSection.allCases, selection: $section) { item in
                Label(item.title, systemImage: item.icon)
                    .tag(item)
            }
            .navigationSplitViewColumnWidth(min: 160, ideal: 180, max: 220)
            .safeAreaInset(edge: .bottom) {
                HStack(spacing: 8) {
                    ConnectionStatusBadge(path: model.path)
                    if model.deviceStatus.battery >= 0 {
                        Text("\(model.deviceStatus.battery)%")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                }
                .padding(12)
            }
        } detail: {
            switch section {
            case .home: HomeView()
            case .messages: MessagesView()
            case .calls: CallsView()
            case .notifications: NotificationsView()
            case .phone: PhoneView()
            case .transfer: TransferView()
            case .settings: SettingsView()
            }
        }
        .frame(minWidth: 820, minHeight: 560)
        .safeAreaInset(edge: .top) {
            if let update = updates.available, !updates.dismissed {
                updateBanner(update)
            }
        }
    }

    private func updateBanner(_ update: AppUpdateInfo) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.down.app")
            VStack(alignment: .leading, spacing: 2) {
                Text("Update available: v\(update.version)")
                    .font(.headline)
                Text("You have \(UpdateChecker.currentVersion)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if updates.installing {
                ProgressView(value: updates.progress)
                    .frame(width: 100)
            }
            Button("Update") { updates.install() }
                .disabled(updates.installing)
            Button("Later") { updates.dismissed = true }
                .buttonStyle(.borderless)
        }
        .padding(12)
        .background(.ultraThinMaterial)
    }
}
