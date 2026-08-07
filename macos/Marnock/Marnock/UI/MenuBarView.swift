import SwiftUI

struct MenuBarView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(model.path.rawValue)
            Text(model.status).font(.caption).foregroundStyle(.secondary)
            Divider()
            Toggle("Clipboard sync", isOn: $model.clipboardEnabled)
            Button("Refresh SMS") { model.refreshSmsThreads() }
            Button("Refresh calls") { model.refreshCallHistory() }
            Divider()
            Button("Quit") { NSApplication.shared.terminate(nil) }
        }
        .padding()
        .frame(width: 240)
    }
}
