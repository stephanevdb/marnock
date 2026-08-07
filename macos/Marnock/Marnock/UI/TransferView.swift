import AppKit
import SwiftUI
import UniformTypeIdentifiers

struct TransferView: View {
    @EnvironmentObject var model: AppModel
    @State private var tab: TransferTab = .files
    @State private var dropTargeted = false

    private enum TransferTab: String, CaseIterable, Identifiable {
        case files, photos
        var id: String { rawValue }
        var title: String {
            switch self {
            case .files: return "Files"
            case .photos: return "Photos"
            }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                SectionHeader(title: "Transfer", subtitle: "Files and photos over LAN")
                Spacer()
                Picker("Mode", selection: $tab) {
                    ForEach(TransferTab.allCases) { t in
                        Text(t.title).tag(t)
                    }
                }
                .pickerStyle(.segmented)
                .frame(maxWidth: 220)
            }

            switch tab {
            case .files: filesPane
            case .photos: photosPane
            }
        }
        .padding(24)
    }

    private var filesPane: some View {
        VStack(alignment: .leading, spacing: 16) {
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
                    if t.status == "awaiting" {
                        Button("Accept") { model.fileTransfer.acceptIncoming(t.id) }
                        Button("Reject", role: .destructive) { model.fileTransfer.rejectIncoming(t.id) }
                    } else if t.status == "sending" || t.status == "receiving" || t.status == "offering" {
                        Button("Cancel") { model.fileTransfer.cancel(t.id) }
                    }
                }
            }
        }
    }

    private var photosPane: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Recent camera roll from the phone. Save copies to ~/Downloads/Marnock/.")
                    .foregroundStyle(.secondary)
                Spacer()
                Button("Refresh") { model.requestPhotos() }
                    .disabled(model.path == .offline)
            }
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
        .onAppear { if model.phonePhotos.isEmpty { model.requestPhotos() } }
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
