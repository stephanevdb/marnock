import AppKit
import SwiftUI

struct PhoneView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                SectionHeader(title: "Phone", subtitle: "Battery, find, media, and Wi‑Fi tools")

                GroupBox("Battery & connectivity") {
                    VStack(alignment: .leading, spacing: 6) {
                        if model.deviceStatus.battery >= 0 {
                            Text("Battery: \(model.deviceStatus.battery)%\(model.deviceStatus.charging ? " (charging)" : "")")
                        } else {
                            Text("Waiting for phone status…")
                        }
                        Text("Wi‑Fi: \(model.deviceStatus.wifiSsid.isEmpty ? "—" : model.deviceStatus.wifiSsid)")
                        Text("Cellular: \(model.deviceStatus.cellular ? "yes" : "no")")
                        Text("Hotspot: \(model.deviceStatus.hotspotActive ? "active" : "off")")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                GroupBox("Find phone") {
                    HStack {
                        Button("Ring phone") { model.findPhone() }
                        Button("Stop") { model.stopFindPhone() }
                    }
                }

                GroupBox("Media") {
                    VStack(alignment: .leading, spacing: 8) {
                        if model.mediaState.title.isEmpty {
                            Text("No active media session reported.")
                                .foregroundStyle(.secondary)
                        } else {
                            Text(model.mediaState.title).font(.headline)
                            Text(model.mediaState.artist).foregroundStyle(.secondary)
                        }
                        HStack {
                            Button("Prev") { model.mediaCommand("previous") }
                            Button(model.mediaState.playing ? "Pause" : "Play") {
                                model.mediaCommand(model.mediaState.playing ? "pause" : "play")
                            }
                            Button("Next") { model.mediaCommand("next") }
                        }
                        HStack {
                            Text("Volume")
                            Slider(
                                value: Binding(
                                    get: { Double(model.mediaState.volume) },
                                    set: { model.mediaCommand("volume", level: Int($0)) }
                                ),
                                in: 0...100
                            )
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                GroupBox("Wi‑Fi share") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("SSID only — Android apps cannot read the Wi‑Fi password.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Button("Request from phone") { model.requestWifiInfo() }
                        if !model.wifiInfo.ssid.isEmpty {
                            Text("SSID: \(model.wifiInfo.ssid)")
                            Button("Copy SSID") {
                                NSPasteboard.general.clearContents()
                                NSPasteboard.general.setString(model.wifiInfo.ssid, forType: .string)
                            }
                        }
                        if !model.wifiInfo.note.isEmpty {
                            Text(model.wifiInfo.note).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                GroupBox("Open link on phone") {
                    HStack {
                        TextField("https://…", text: $model.openLinkDraft)
                        Button("Open") { model.openLinkOnPhone(model.openLinkDraft) }
                            .disabled(model.openLinkDraft.isEmpty)
                    }
                }
            }
            .padding(24)
        }
    }
}
