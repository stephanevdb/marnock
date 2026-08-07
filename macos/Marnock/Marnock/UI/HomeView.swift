import SwiftUI

struct HomeView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Marnock")
                        .font(.largeTitle.weight(.bold))
                    Spacer()
                    ConnectionStatusBadge(path: model.path)
                }

                Text(model.status)
                    .foregroundStyle(.secondary)

                if model.pairedPeerId == nil {
                    pairingCard
                } else {
                    pairedCard
                }

                HStack(alignment: .top, spacing: 16) {
                    statusCard
                    quickActionsCard
                }

                if !model.mediaState.title.isEmpty {
                    mediaCard
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var pairingCard: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 12) {
                Text("Pair your phone")
                    .font(.headline)
                Text("Scan this QR with the Android app on the same Wi‑Fi.")
                    .foregroundStyle(.secondary)
                QRCodeView(payload: model.qrPayload)
                Text("Pairing code: \(model.pairingCode)")
                    .font(.title2.monospaced())
                Button("Refresh QR / IP") { model.refreshQR() }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var pairedCard: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 8) {
                Label("Paired", systemImage: "checkmark.seal.fill")
                    .font(.headline)
                    .foregroundStyle(.green)
                if let peer = model.pairedPeerId {
                    Text("Peer \(peer)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)
                }
                Text(model.path.rawValue)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var statusCard: some View {
        GroupBox("Phone") {
            VStack(alignment: .leading, spacing: 6) {
                if model.deviceStatus.battery >= 0 {
                    Text("Battery \(model.deviceStatus.battery)%\(model.deviceStatus.charging ? " · charging" : "")")
                } else {
                    Text("Waiting for phone status…")
                        .foregroundStyle(.secondary)
                }
                Text("Wi‑Fi: \(model.deviceStatus.wifiSsid.isEmpty ? "—" : model.deviceStatus.wifiSsid)")
                Text("Cellular: \(model.deviceStatus.cellular ? "yes" : "no")")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity)
    }

    private var quickActionsCard: some View {
        GroupBox("Quick actions") {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Button("Find phone") { model.findPhone() }
                    Button("Stop") { model.stopFindPhone() }
                }
                Toggle("Clipboard sync", isOn: $model.clipboardEnabled)
                Toggle("Quiet hours", isOn: $model.quietHoursEnabled)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity)
    }

    private var mediaCard: some View {
        GroupBox("Now playing") {
            VStack(alignment: .leading, spacing: 8) {
                Text(model.mediaState.title).font(.headline)
                if !model.mediaState.artist.isEmpty {
                    Text(model.mediaState.artist).foregroundStyle(.secondary)
                }
                HStack {
                    Button("Prev") { model.mediaCommand("previous") }
                    Button(model.mediaState.playing ? "Pause" : "Play") {
                        model.mediaCommand(model.mediaState.playing ? "pause" : "play")
                    }
                    Button("Next") { model.mediaCommand("next") }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
