import AppKit
import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var updates: UpdateModel
    @State private var section: SidebarSection = .pairing
    @State private var smsBody = ""
    @State private var dialNumber = ""
    @State private var replyText = ""
    @State private var composeAddress = ""
    @State private var denyPackageDraft = ""
    @State private var dropTargeted = false

    var body: some View {
        NavigationSplitView {
            List(SidebarSection.allCases, selection: $section) { item in
                Label(item.title, systemImage: item.icon)
                    .tag(item)
            }
            .navigationSplitViewColumnWidth(180)
        } detail: {
            switch section {
            case .pairing: pairingView
            case .clipboard: clipboardView
            case .notifications: notificationsView
            case .conversations: conversationsView
            case .calls: callsView
            case .files: filesView
            case .photos: photosView
            case .device: deviceView
            case .settings: settingsView
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

    private var pairingView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Marnock")
                    .font(.largeTitle.weight(.bold))
                Text(model.path.rawValue).foregroundStyle(.secondary)
                Text(model.status)

                QRCodeView(payload: model.qrPayload)
                    .padding(.vertical, 8)
                Text("Pairing code: \(model.pairingCode)")
                    .font(.title2.monospaced())
                Text("Scan this QR with the Android app on the same Wi‑Fi.")
                    .foregroundStyle(.secondary)

                Toggle("Local-only (disable internet relay)", isOn: $model.localOnly)
                TextField("Relay WebSocket URL", text: $model.relayURL)
                if let peer = model.pairedPeerId {
                    Text("Paired peer: \(peer)")
                    Button("Clear pairing", role: .destructive) { model.clearPairing() }
                }
                Button("Refresh QR / IP") { model.refreshQR() }
            }
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var clipboardView: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Clipboard").font(.title)
            Toggle("Sync clipboard with phone", isOn: $model.clipboardEnabled)
            Text("Off until you enable it.")
                .foregroundStyle(.secondary)
            if !model.lastClipboard.isEmpty {
                Text("Last synced").font(.headline)
                Text(model.lastClipboard)
                    .textSelection(.enabled)
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.primary.opacity(0.05))
                if model.lastClipboard.lowercased().hasPrefix("http://")
                    || model.lastClipboard.lowercased().hasPrefix("https://") {
                    Button("Open on phone") { model.openLinkOnPhone(model.lastClipboard) }
                }
            }
            Spacer()
        }
        .padding(24)
    }

    private var notificationsView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Notifications").font(.title)
            if model.notificationsSuppressed {
                Text("Quiet hours active — mirrors paused.")
                    .foregroundStyle(.orange)
            }
            if model.notifications.isEmpty {
                Text("Mirrored phone notifications appear here.")
                    .foregroundStyle(.secondary)
            }
            List(model.notifications) { n in
                VStack(alignment: .leading, spacing: 6) {
                    Text(n.title.isEmpty ? n.packageName : n.title).font(.headline)
                    Text(n.text)
                    Text(n.packageName).font(.caption).foregroundStyle(.secondary)
                    HStack {
                        ForEach(n.actions) { action in
                            Button(action.title) {
                                if action.allowsReply {
                                    model.invokeNotificationAction(key: n.id, actionId: action.id, reply: replyText)
                                } else {
                                    model.invokeNotificationAction(key: n.id, actionId: action.id, reply: nil)
                                }
                            }
                        }
                        Button("Block app") {
                            model.deniedNotificationPackages.insert(n.packageName)
                        }
                    }
                    if n.actions.contains(where: \.allowsReply) {
                        TextField("Reply text", text: $replyText)
                    }
                }
                .padding(.vertical, 4)
            }
        }
        .padding(16)
    }

    private var conversationsView: some View {
        HSplitView {
            VStack(alignment: .leading) {
                HStack {
                    Text("Conversations").font(.title2)
                    Spacer()
                    Button("Refresh") { model.refreshSmsThreads() }
                }
                .padding([.horizontal, .top])
                HStack {
                    TextField("New SMS to", text: $composeAddress)
                    ContactPickerButton(title: "Contacts") { composeAddress = $0 }
                    Button("Open") {
                        guard !composeAddress.isEmpty else { return }
                        model.sendSms(address: composeAddress, body: "")
                    }
                    .disabled(composeAddress.isEmpty)
                }
                .padding(.horizontal)
                List(model.smsThreads, selection: Binding(
                    get: { model.selectedThreadId },
                    set: { if let id = $0 { model.openThread(id) } }
                )) { thread in
                    VStack(alignment: .leading) {
                        Text(thread.contactName.isEmpty ? thread.address : thread.contactName)
                            .font(.headline)
                        Text(thread.snippet).lineLimit(1).foregroundStyle(.secondary)
                    }
                    .tag(thread.id)
                }
            }
            .frame(minWidth: 240)

            VStack(alignment: .leading, spacing: 8) {
                if let tid = model.selectedThreadId,
                   let thread = model.smsThreads.first(where: { $0.id == tid }) {
                    Text(thread.contactName.isEmpty ? thread.address : thread.contactName)
                        .font(.title3)
                        .padding(.horizontal)
                    List(model.smsMessages) { msg in
                        HStack {
                            if msg.type == "sent" { Spacer() }
                            Text(msg.body)
                                .padding(8)
                                .background(msg.type == "sent" ? Color.accentColor.opacity(0.2) : Color.primary.opacity(0.06))
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                            if msg.type != "sent" { Spacer() }
                        }
                    }
                    HStack {
                        TextField("Message", text: $smsBody)
                        Button("Send") {
                            model.sendSms(address: thread.address, body: smsBody)
                            smsBody = ""
                        }
                        .disabled(smsBody.isEmpty)
                    }
                    .padding()
                } else {
                    Text("Select a conversation")
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .frame(minWidth: 320)
        }
        .padding(8)
    }

    private var callsView: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Calls").font(.title)
            GroupBox("Live state") {
                VStack(alignment: .leading, spacing: 8) {
                    Text("State: \(model.callState.state)")
                    if !model.callState.number.isEmpty {
                        Text("Number: \(model.callState.number)")
                    }
                    if model.callState.state == "ringing" {
                        HStack {
                            Button("Answer") { model.answerCall() }
                            Button("Reject", role: .destructive) { model.rejectCall() }
                        }
                    }
                    Text("Audio stays on the phone or its Bluetooth headset.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack {
                TextField("Dial number", text: $dialNumber)
                ContactPickerButton { dialNumber = $0.filter { $0.isNumber || $0 == "+" } }
                Button("Dial") { model.dial(dialNumber) }
                    .disabled(dialNumber.isEmpty)
                Button("Refresh history") { model.refreshCallHistory() }
            }
            List(model.callHistory) { entry in
                HStack {
                    VStack(alignment: .leading) {
                        Text(entry.name.isEmpty ? entry.number : entry.name)
                        Text(entry.type).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text("\(entry.duration)s").foregroundStyle(.secondary)
                }
            }
        }
        .padding(24)
    }

    private var filesView: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Files").font(.title)
            Text("Drag files here to send to the phone (LAN only). Received files land in ~/Downloads/Marnock/.")
                .foregroundStyle(.secondary)
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(style: StrokeStyle(lineWidth: 2, dash: [8]))
                    .foregroundStyle(dropTargeted ? Color.accentColor : Color.secondary.opacity(0.4))
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(dropTargeted ? Color.accentColor.opacity(0.08) : Color.clear)
                    )
                VStack(spacing: 8) {
                    Image(systemName: "arrow.down.doc")
                        .font(.largeTitle)
                    Text(model.path == .lan ? "Drop files to send" : "Connect over LAN to transfer files")
                    Button("Choose file…") { pickFileToSend() }
                        .disabled(model.path != .lan)
                }
                .padding(32)
            }
            .frame(maxWidth: .infinity, minHeight: 160)
            .onDrop(of: [UTType.fileURL], isTargeted: $dropTargeted) { providers in
                guard model.path == .lan else { return false }
                for provider in providers {
                    _ = provider.loadObject(ofClass: URL.self) { url, _ in
                        guard let url else { return }
                        Task { @MainActor in model.sendFile(url: url) }
                    }
                }
                return true
            }
            List(model.fileTransfer.transfers) { t in
                HStack {
                    VStack(alignment: .leading) {
                        Text(t.name)
                        Text("\(t.direction) · \(t.status)")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if t.bytesTotal > 0 {
                        ProgressView(value: Double(t.bytesDone), total: Double(t.bytesTotal))
                            .frame(width: 120)
                    }
                    if t.status == "sending" || t.status == "receiving" || t.status == "offering" {
                        Button("Cancel") { model.fileTransfer.cancel(t.id) }
                    }
                }
            }
        }
        .padding(24)
    }

    private var photosView: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Photos").font(.title)
                Spacer()
                Button("Refresh") { model.requestPhotos() }
                    .disabled(model.path == .offline)
            }
            Text("Recent camera roll from the phone. Save copies to ~/Downloads/Marnock/.")
                .foregroundStyle(.secondary)
            if model.phonePhotos.isEmpty {
                Text("No photos loaded yet.")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 12)], spacing: 12) {
                        ForEach(model.phonePhotos) { photo in
                            VStack(spacing: 6) {
                                photoThumb(photo)
                                    .frame(width: 120, height: 120)
                                    .clipped()
                                    .background(Color.primary.opacity(0.06))
                                Text(photo.name).lineLimit(1).font(.caption)
                                Button("Save to Mac") { model.savePhotoToMac(photo.id) }
                                    .controlSize(.small)
                                    .disabled(model.path != .lan)
                            }
                        }
                    }
                }
            }
        }
        .padding(24)
        .onAppear { if model.phonePhotos.isEmpty { model.requestPhotos() } }
    }

    private var deviceView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Device").font(.title)
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
                        Button("Request from phone") { model.requestWifiInfo() }
                        if !model.wifiInfo.ssid.isEmpty {
                            Text("SSID: \(model.wifiInfo.ssid)")
                            Button("Copy SSID") {
                                NSPasteboard.general.clearContents()
                                NSPasteboard.general.setString(model.wifiInfo.ssid, forType: .string)
                            }
                        }
                        if model.wifiInfo.hasPassword, !model.wifiInfo.password.isEmpty {
                            SecureField("Password", text: .constant(model.wifiInfo.password))
                            Button("Copy password") {
                                NSPasteboard.general.clearContents()
                                NSPasteboard.general.setString(model.wifiInfo.password, forType: .string)
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

    private var settingsView: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Settings").font(.title)
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
            Spacer()
        }
        .padding(24)
    }

    @ViewBuilder
    private func photoThumb(_ photo: PhonePhoto) -> some View {
        if let data = Data(base64Encoded: photo.thumbB64),
           let img = NSImage(data: data) {
            Image(nsImage: img)
                .resizable()
                .aspectRatio(contentMode: .fill)
        } else {
            Image(systemName: "photo")
                .font(.largeTitle)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func pickFileToSend() {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = false
        panel.begin { resp in
            guard resp == .OK else { return }
            for url in panel.urls {
                model.sendFile(url: url)
            }
        }
    }
}

enum SidebarSection: String, CaseIterable, Identifiable, Hashable {
    case pairing, clipboard, notifications, conversations, calls, files, photos, device, settings
    var id: String { rawValue }
    var title: String {
        switch self {
        case .pairing: return "Pairing"
        case .clipboard: return "Clipboard"
        case .notifications: return "Notifications"
        case .conversations: return "Conversations"
        case .calls: return "Calls"
        case .files: return "Files"
        case .photos: return "Photos"
        case .device: return "Device"
        case .settings: return "Settings"
        }
    }
    var icon: String {
        switch self {
        case .pairing: return "qrcode"
        case .clipboard: return "doc.on.clipboard"
        case .notifications: return "bell"
        case .conversations: return "message"
        case .calls: return "phone"
        case .files: return "folder"
        case .photos: return "photo.on.rectangle"
        case .device: return "iphone"
        case .settings: return "gearshape"
        }
    }
}
