import SwiftUI
import AppKit

struct MenuBarStatus: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Marnock").font(.headline)
            Text(model.path.rawValue)
            Text(model.status).font(.caption).foregroundStyle(.secondary)

            if model.deviceStatus.battery >= 0 {
                Text("Battery \(model.deviceStatus.battery)%\(model.deviceStatus.charging ? " · charging" : "")")
                    .font(.caption)
            }

            Divider()

            HStack {
                Button("Find") { model.findPhone() }
                Button("Stop") { model.stopFindPhone() }
            }

            if !model.mediaState.title.isEmpty {
                Text(model.mediaState.title).lineLimit(1).font(.caption)
                HStack {
                    Button("⏮") { model.mediaCommand("previous") }
                    Button(model.mediaState.playing ? "⏸" : "▶️") {
                        model.mediaCommand(model.mediaState.playing ? "pause" : "play")
                    }
                    Button("⏭") { model.mediaCommand("next") }
                }
            }

            Divider()
            Toggle("Clipboard sync", isOn: $model.clipboardEnabled)
            Toggle("Quiet hours", isOn: $model.quietHoursEnabled)
            Button("Refresh SMS") { model.refreshSmsThreads() }
            Button("Refresh calls") { model.refreshCallHistory() }
            if model.callState.state == "ringing" {
                Button("Answer") { model.answerCall() }
                Button("Reject") { model.rejectCall() }
            }
            Divider()
            Button("Quit") { NSApplication.shared.terminate(nil) }
        }
        .padding(12)
        .frame(width: 280)
    }
}
