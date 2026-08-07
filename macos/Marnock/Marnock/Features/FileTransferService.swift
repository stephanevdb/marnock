import Foundation
import CryptoKit

struct TransferProgress: Identifiable, Equatable {
    let id: String
    var name: String
    var direction: String
    var bytesDone: Int64
    var bytesTotal: Int64
    var status: String
}

/// Chunked file transfer over encrypted session frames (LAN only).
@MainActor
final class FileTransferService: ObservableObject {
    @Published var transfers: [TransferProgress] = []

    private var pendingOut: [String: URL] = [:]
    private var incoming: [String: Incoming] = [:]
    private let chunkSize = 48 * 1024
    var send: ((Envelope) -> Void)?
    var isLan: (() -> Bool)?

    private struct Incoming {
        let url: URL
        let total: Int64
        let handle: FileHandle
        var done: Int64
    }

    func cancel(_ id: String) {
        pendingOut.removeValue(forKey: id)
        if let inc = incoming.removeValue(forKey: id) {
            try? inc.handle.close()
        }
        send?(Envelope(type: MessageTypes.fileCancel, payload: [
            "transferId": AnyCodable(id)
        ]))
        upsert(TransferProgress(id: id, name: id, direction: "?", bytesDone: 0, bytesTotal: 0, status: "cancelled"))
    }

    func offer(url: URL) {
        guard isLan?() == true else { return }
        guard let data = try? Data(contentsOf: url) else { return }
        let id = UUID().uuidString
        let name = url.lastPathComponent
        let sha = SHA256.hash(data: data).compactMap { String(format: "%02x", $0) }.joined()
        pendingOut[id] = url
        upsert(TransferProgress(id: id, name: name, direction: "out", bytesDone: 0, bytesTotal: Int64(data.count), status: "offering"))
        send?(Envelope(type: MessageTypes.fileOffer, payload: [
            "transferId": AnyCodable(id),
            "name": AnyCodable(name),
            "size": AnyCodable(data.count),
            "mime": AnyCodable("application/octet-stream"),
            "sha256": AnyCodable(sha)
        ]))
    }

    func handle(_ env: Envelope) {
        switch env.type {
        case MessageTypes.fileOffer:
            guard isLan?() == true else { return }
            guard let id = env.payload["transferId"]?.stringValue else { return }
            let name = sanitize(env.payload["name"]?.stringValue ?? "file.bin")
            let size = Int64(env.payload["size"]?.intValue ?? 0)
            let dir = downloadsDir()
            let dest = dir.appendingPathComponent(name)
            FileManager.default.createFile(atPath: dest.path, contents: nil)
            guard let handle = try? FileHandle(forWritingTo: dest) else { return }
            incoming[id] = Incoming(url: dest, total: size, handle: handle, done: 0)
            upsert(TransferProgress(id: id, name: name, direction: "in", bytesDone: 0, bytesTotal: size, status: "receiving"))
            send?(Envelope(type: MessageTypes.fileAccept, payload: [
                "transferId": AnyCodable(id),
                "ok": AnyCodable(true)
            ]))
        case MessageTypes.fileAccept:
            guard let id = env.payload["transferId"]?.stringValue,
                  let url = pendingOut.removeValue(forKey: id) else { return }
            Task { await sendChunks(id: id, url: url) }
        case MessageTypes.fileChunk:
            guard let id = env.payload["transferId"]?.stringValue,
                  let b64 = env.payload["data"]?.stringValue,
                  let data = Data(base64Encoded: b64),
                  var inc = incoming[id] else { return }
            let offset = Int64(env.payload["offset"]?.intValue ?? 0)
            do {
                try inc.handle.seek(toOffset: UInt64(offset))
                try inc.handle.write(contentsOf: data)
                inc.done = offset + Int64(data.count)
                incoming[id] = inc
                upsert(TransferProgress(id: id, name: inc.url.lastPathComponent, direction: "in", bytesDone: inc.done, bytesTotal: inc.total, status: "receiving"))
            } catch { }
        case MessageTypes.fileComplete:
            guard let id = env.payload["transferId"]?.stringValue,
                  let inc = incoming.removeValue(forKey: id) else { return }
            try? inc.handle.close()
            upsert(TransferProgress(id: id, name: inc.url.lastPathComponent, direction: "in", bytesDone: inc.total, bytesTotal: inc.total, status: "complete"))
        case MessageTypes.fileCancel:
            guard let id = env.payload["transferId"]?.stringValue else { return }
            if let inc = incoming.removeValue(forKey: id) {
                try? inc.handle.close()
            }
            pendingOut.removeValue(forKey: id)
            upsert(TransferProgress(id: id, name: id, direction: "?", bytesDone: 0, bytesTotal: 0, status: "cancelled"))
        default:
            break
        }
    }

    private func sendChunks(id: String, url: URL) async {
        guard let data = try? Data(contentsOf: url) else { return }
        var offset = 0
        while offset < data.count {
            let end = min(offset + chunkSize, data.count)
            let slice = data.subdata(in: offset..<end)
            send?(Envelope(type: MessageTypes.fileChunk, payload: [
                "transferId": AnyCodable(id),
                "offset": AnyCodable(offset),
                "data": AnyCodable(slice.base64EncodedString())
            ]))
            offset = end
            upsert(TransferProgress(id: id, name: url.lastPathComponent, direction: "out", bytesDone: Int64(offset), bytesTotal: Int64(data.count), status: "sending"))
            try? await Task.sleep(nanoseconds: 5_000_000)
        }
        send?(Envelope(type: MessageTypes.fileComplete, payload: [
            "transferId": AnyCodable(id),
            "ok": AnyCodable(true)
        ]))
        upsert(TransferProgress(id: id, name: url.lastPathComponent, direction: "out", bytesDone: Int64(data.count), bytesTotal: Int64(data.count), status: "complete"))
    }

    private func downloadsDir() -> URL {
        let base = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first!
        let dir = base.appendingPathComponent("Marnock", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func sanitize(_ name: String) -> String {
        let cleaned = name.replacingOccurrences(of: "/", with: "_")
        return cleaned.isEmpty ? "file.bin" : cleaned
    }

    private func upsert(_ p: TransferProgress) {
        if let i = transfers.firstIndex(where: { $0.id == p.id }) {
            transfers[i] = p
        } else {
            transfers.append(p)
        }
        if transfers.count > 20 { transfers.removeFirst(transfers.count - 20) }
    }
}
