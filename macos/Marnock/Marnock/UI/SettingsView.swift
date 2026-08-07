import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var updates: UpdateModel
    @State private var denyPackageDraft = ""
    @State private var launchTick = 0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                SectionHeader(title: "Settings", subtitle: "Clipboard, relay, quiet hours, and updates")

                GroupBox("General") {
                    VStack(alignment: .leading, spacing: 8) {
                        Toggle("Open at login", isOn: Binding(
                            get: { LaunchAtLogin.isEnabled },
                            set: { _ = LaunchAtLogin.setEnabled($0); launchTick += 1 }
                        ))
                        Text(LaunchAtLogin.statusDescription)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .id(launchTick)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                GroupBox("Clipboard") {
                    VStack(alignment: .leading, spacing: 8) {
                        Toggle("Sync clipboard with phone", isOn: $model.clipboardEnabled)
                        Text("Off until you enable it.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if !model.lastClipboard.isEmpty {
                            Text("Last synced").font(.headline)
                            Text(model.lastClipboard)
                                .textSelection(.enabled)
                                .padding(8)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 6))
                            if model.lastClipboard.lowercased().hasPrefix("http://")
                                || model.lastClipboard.lowercased().hasPrefix("https://") {
                                Button("Open on phone") { model.openLinkOnPhone(model.lastClipboard) }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                GroupBox("Connection") {
                    VStack(alignment: .leading, spacing: 8) {
                        Toggle("Local-only (disable internet relay)", isOn: $model.localOnly)
                        TextField("Relay WebSocket URL", text: $model.relayURL)
                        if let peer = model.pairedPeerId {
                            Text("Paired peer: \(peer)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .textSelection(.enabled)
                            Button("Clear pairing", role: .destructive) { model.clearPairing() }
                        } else {
                            Text("Not paired — use Home to show the QR code.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Button("Refresh QR / IP") { model.refreshQR() }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                GroupBox("Updates") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Installed: \(UpdateChecker.currentVersion)")
                        if let err = updates.errorMessage {
                            Text(err).foregroundStyle(.red).font(.caption)
                        } else if !updates.statusMessage.isEmpty {
                            Text(updates.statusMessage).foregroundStyle(.secondary).font(.caption)
                        }
                        HStack {
                            Button(updates.checking ? "Checking…" : "Check for updates") {
                                Task { await updates.check(notify: false) }
                            }
                            .disabled(updates.checking || updates.installing)
                            if updates.available != nil {
                                Button("Update") { updates.install() }
                                    .disabled(updates.installing)
                            }
                        }
                        if updates.installing {
                            ProgressView(value: updates.progress)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                GroupBox("Quiet hours") {
                    VStack(alignment: .leading, spacing: 8) {
                        Toggle("Pause notification mirroring when Mac is locked", isOn: $model.quietHoursEnabled)
                        Toggle("Force quiet now (also when unlocked)", isOn: $model.quietHoursForce)
                        Text(model.quietMonitor.screenLocked ? "Screen: locked" : "Screen: unlocked")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                GroupBox("Blocked notification apps") {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            TextField("package name (e.g. com.example.app)", text: $denyPackageDraft)
                            Button("Block") {
                                let p = denyPackageDraft.trimmingCharacters(in: .whitespaces)
                                guard !p.isEmpty else { return }
                                model.deniedNotificationPackages.insert(p)
                                denyPackageDraft = ""
                            }
                        }
                        if model.deniedNotificationPackages.isEmpty {
                            Text("None — block from a notification or enter a package name.")
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(Array(model.deniedNotificationPackages).sorted(), id: \.self) { pkg in
                                HStack {
                                    Text(pkg)
                                    Spacer()
                                    Button("Allow") { model.deniedNotificationPackages.remove(pkg) }
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(24)
        }
    }
}
