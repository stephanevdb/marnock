import SwiftUI
import AppKit

struct MenuBarStatus: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var updates: UpdateModel
    @EnvironmentObject var navigation: NavigationState
    @Environment(\.openWindow) private var openWindow
    @State private var replyDrafts: [String: String] = [:]
    @State private var hostingWindow: NSWindow?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            DeviceStatusHeader(
                deviceName: model.phoneDisplayName,
                path: model.path,
                battery: model.deviceStatus.battery,
                charging: model.deviceStatus.charging,
                wallpaper: model.wallpaperThumb,
                onClose: closePanel
            )

            if model.callState.state == "ringing" {
                incomingCall
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .liquidGlassCard()
            }

            if !model.mediaState.title.isEmpty {
                nowPlaying
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .liquidGlassCard()
            }

            recentNotifications
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .liquidGlassCard()

            VStack(alignment: .leading, spacing: 8) {
                Toggle("Clipboard sync", isOn: $model.clipboardEnabled)
                Toggle("Quiet hours", isOn: $model.quietHoursEnabled)
            }
            .toggleStyle(.switch)
            .controlSize(.small)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .liquidGlassCard()

            if let update = updates.available, !updates.dismissed {
                HStack {
                    Text("Update v\(update.version)")
                        .font(.caption)
                    Spacer()
                    Button("Update") { updates.install() }
                        .disabled(updates.installing)
                    Button("Later") { updates.dismissed = true }
                        .buttonStyle(.borderless)
                }
                .padding(12)
                .liquidGlassCard()
            }

            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Button("Find phone") { model.findPhone() }
                    Button("Stop") { model.stopFindPhone() }
                    Spacer()
                }
                Button("Open Marnock…") {
                    openMainWindow(section: nil)
                }
                .keyboardShortcut("o")
                Button(updates.checking ? "Checking…" : "Check for updates") {
                    Task { await updates.check(notify: false) }
                }
                .disabled(updates.checking)
            }
            .controlSize(.small)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .liquidGlassCard()

            Button("Quit Marnock") { NSApplication.shared.terminate(nil) }
                .buttonStyle(.plain)
                .foregroundStyle(.white.opacity(0.55))
                .font(.caption)
                .keyboardShortcut("q")
                .frame(maxWidth: .infinity)
        }
        .padding(12)
        .frame(width: 340)
        .environment(\.colorScheme, .dark)
        .background {
            ZStack {
                VisualEffectView(material: .hudWindow, blendingMode: .behindWindow)
                LinearGradient(
                    colors: [
                        Color.white.opacity(0.10),
                        Color.clear,
                        Color.black.opacity(0.18)
                    ],
                    startPoint: .topTrailing,
                    endPoint: .bottomLeading
                )
                .allowsHitTesting(false)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: MenuBarGlassChrome.cornerRadius, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: MenuBarGlassChrome.cornerRadius, style: .continuous)
                .strokeBorder(Color.white.opacity(0.14), lineWidth: 0.75)
        }
        .background {
            MenuBarWindowAccessor { window in
                hostingWindow = window
                MenuBarGlassChrome.apply(window)
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
                Button { model.mediaCommand("previous") } label: {
                    Image(systemName: "backward.fill")
                }
                .buttonStyle(.borderless)
                Button {
                    model.mediaCommand(model.mediaState.playing ? "pause" : "play")
                } label: {
                    Image(systemName: model.mediaState.playing ? "pause.fill" : "play.fill")
                }
                .buttonStyle(.borderless)
                Button { model.mediaCommand("next") } label: {
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
                    VStack(alignment: .leading, spacing: 4) {
                        Button {
                            openMainWindow(section: .notifications)
                        } label: {
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
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.plain)

                        if let replyAction = n.actions.first(where: \.allowsReply) {
                            HStack {
                                TextField(
                                    "Reply",
                                    text: Binding(
                                        get: { replyDrafts[n.id] ?? "" },
                                        set: { replyDrafts[n.id] = $0 }
                                    )
                                )
                                .textFieldStyle(.roundedBorder)
                                Button("Send") {
                                    let text = replyDrafts[n.id] ?? ""
                                    guard !text.isEmpty else { return }
                                    model.invokeNotificationAction(
                                        key: n.id,
                                        actionId: replyAction.id,
                                        reply: text
                                    )
                                    replyDrafts[n.id] = ""
                                }
                                .disabled((replyDrafts[n.id] ?? "").isEmpty)
                            }
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
    }

    private func closePanel() {
        hostingWindow?.orderOut(nil)
    }

    private func openMainWindow(section: SidebarSection?) {
        if let section {
            navigation.section = section
        }
        NSApp.activate(ignoringOtherApps: true)
        openWindow(id: "main")
        DispatchQueue.main.async {
            if let window = NSApp.windows.first(where: { $0.identifier?.rawValue.contains("main") == true })
                ?? NSApp.windows.first(where: { $0.isVisible && $0.canBecomeKey }) {
                window.makeKeyAndOrderFront(nil)
            }
        }
    }
}
