import SwiftUI
import AppKit

struct MenuBarStatus: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var updates: UpdateModel
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            header

            if model.callState.state == "ringing" {
                incomingCall
                Divider()
            }

            if !model.mediaState.title.isEmpty {
                nowPlaying
                Divider()
            }

            recentNotifications
            Divider()

            VStack(alignment: .leading, spacing: 6) {
                Toggle("Clipboard sync", isOn: $model.clipboardEnabled)
                Toggle("Quiet hours", isOn: $model.quietHoursEnabled)
            }

            if let update = updates.available, !updates.dismissed {
                Divider()
                HStack {
                    Text("Update v\(update.version)")
                        .font(.caption)
                    Spacer()
                    Button("Update") { updates.install() }
                        .disabled(updates.installing)
                    Button("Later") { updates.dismissed = true }
                        .buttonStyle(.borderless)
                }
            }

            Divider()

            HStack {
                Button("Find phone") { model.findPhone() }
                Button("Stop") { model.stopFindPhone() }
            }

            Button("Open Marnock…") {
                openMainWindow()
            }
            .keyboardShortcut("o")

            Button(updates.checking ? "Checking…" : "Check for updates") {
                Task { await updates.check(notify: false) }
            }
            .disabled(updates.checking)

            Divider()
            Button("Quit Marnock") { NSApplication.shared.terminate(nil) }
                .keyboardShortcut("q")
        }
        .padding(14)
        .frame(width: 320)
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Marnock")
                    .font(.headline)
                Text(model.status)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            Spacer()
            ConnectionStatusBadge(path: model.path)
            if model.deviceStatus.battery >= 0 {
                Label(
                    "\(model.deviceStatus.battery)%",
                    systemImage: model.deviceStatus.charging ? "battery.100.bolt" : "battery.100"
                )
                .font(.caption.weight(.medium))
                .labelStyle(.titleAndIcon)
            }
        }
    }

    private var incomingCall: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Incoming call", systemImage: "phone.badge.waveform")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.orange)
            if !model.callState.number.isEmpty || !model.callState.name.isEmpty {
                Text(model.callState.name.isEmpty ? model.callState.number : model.callState.name)
                    .font(.title3.weight(.medium))
            }
            HStack {
                Button("Answer") { model.answerCall() }
                    .buttonStyle(.borderedProminent)
                Button("Reject", role: .destructive) { model.rejectCall() }
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
    }

    private var nowPlaying: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(model.mediaState.title)
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
            if !model.mediaState.artist.isEmpty {
                Text(model.mediaState.artist)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            HStack(spacing: 16) {
                Button {
                    model.mediaCommand("previous")
                } label: {
                    Image(systemName: "backward.fill")
                }
                .buttonStyle(.borderless)
                Button {
                    model.mediaCommand(model.mediaState.playing ? "pause" : "play")
                } label: {
                    Image(systemName: model.mediaState.playing ? "pause.fill" : "play.fill")
                }
                .buttonStyle(.borderless)
                Button {
                    model.mediaCommand("next")
                } label: {
                    Image(systemName: "forward.fill")
                }
                .buttonStyle(.borderless)
            }
            .font(.title3)
        }
    }

    private var recentNotifications: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Recent notifications")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            if model.notifications.isEmpty {
                Text(model.notificationsSuppressed
                      ? "Quiet hours — mirroring paused"
                      : "None yet")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(Array(model.notifications.prefix(3))) { n in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(n.title.isEmpty ? n.packageName : n.title)
                            .font(.caption.weight(.medium))
                            .lineLimit(1)
                        if !n.text.isEmpty {
                            Text(n.text)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                                .lineLimit(2)
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
    }

    private func openMainWindow() {
        NSApp.activate(ignoringOtherApps: true)
        openWindow(id: "main")
        // Ensure the window is key after SwiftUI creates/restores it.
        DispatchQueue.main.async {
            if let window = NSApp.windows.first(where: { $0.identifier?.rawValue.contains("main") == true })
                ?? NSApp.windows.first(where: { $0.isVisible && $0.canBecomeKey }) {
                window.makeKeyAndOrderFront(nil)
            }
        }
    }
}
