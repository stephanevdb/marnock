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
    private var pendingIn: [String: PendingOffer] = [:]
    private var incoming: [String: Incoming] = [:]
    private let chunkSize = 48 * 1024
    var send: ((Envelope) -> Void)?
    var isLan: (() -> Bool)?

    private struct PendingOffer {
        let name: String
        let size: Int64
        let sha256: String
    }

    private struct Incoming {
        let url: URL
        let total: Int64
        let handle: FileHandle
        var done: Int64
    }

    func cancel(_ id: String) {
        pendingOut.removeValue(forKey: id)
        pendingIn.removeValue(forKey: id)
        if let inc = incoming.removeValue(forKey: id) {
            try? inc.handle.close()
        }
        send?(Envelope(type: MessageTypes.fileCancel, payload: [
            "transferId": AnyCodable(id)
        ]))
        upsert(TransferProgress(id: id, name: id, direction: "?", bytesDone: 0, bytesTotal: 0, status: "cancelled"))
    }

    func acceptIncoming(_ id: String) {
        guard let offer = pendingIn.removeValue(forKey: id) else { return }
        guard isLan?() == true else { return }
        let dest = downloadsDir().appendingPathComponent(offer.name)
        FileManager.default.createFile(atPath: dest.path, contents: nil)
        guard let handle = try? FileHandle(forWritingTo: dest) else {
            rejectIncoming(id)
            return
        }
        incoming[id] = Incoming(url: dest, total: offer.size, handle: handle, done: 0)
        upsert(TransferProgress(id: id, name: offer.name, direction: "in", bytesDone: 0, bytesTotal: offer.size, status: "receiving"))
        send?(Envelope(type: MessageTypes.fileAccept, payload: [
            "transferId": AnyCodable(id),
            "ok": AnyCodable(true)
        ]))
    }

    func rejectIncoming(_ id: String) {
        guard let offer = pendingIn.removeValue(forKey: id) else { return }
        upsert(TransferProgress(id: id, name: offer.name, direction: "in", bytesDone: 0, bytesTotal: offer.size, status: "rejected"))
        send?(Envelope(type: MessageTypes.fileAccept, payload: [
            "transferId": AnyCodable(id),
            "ok": AnyCodable(false)
        ]))
    }

    func offer(url: URL) {
        guard isLan?() == true else { return }
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
              let size = attrs[.size] as? NSNumber else { return }
        guard let sha = streamSHA256(url: url) else { return }
        let id = UUID().uuidString
        let name = url.lastPathComponent
        let total = size.int64Value
        pendingOut[id] = url
        upsert(TransferProgress(id: id, name: name, direction: "out", bytesDone: 0, bytesTotal: total, status: "offering"))
        send?(Envelope(type: MessageTypes.fileOffer, payload: [
            "transferId": AnyCodable(id),
            "name": AnyCodable(name),
            "size": AnyCodable(total),
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
            let sha = env.payload["sha256"]?.stringValue ?? ""
            pendingIn[id] = PendingOffer(name: name, size: size, sha256: sha)
            upsert(TransferProgress(id: id, name: name, direction: "in", bytesDone: 0, bytesTotal: size, status: "awaiting"))
        case MessageTypes.fileAccept:
            guard let id = env.payload["transferId"]?.stringValue else { return }
            let ok = env.payload["ok"]?.boolValue ?? true
            guard let url = pendingOut.removeValue(forKey: id) else { return }
            if !ok {
                upsert(TransferProgress(id: id, name: url.lastPathComponent, direction: "out", bytesDone: 0, bytesTotal: 0, status: "rejected"))
                return
            }
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
            pendingIn.removeValue(forKey: id)
            upsert(TransferProgress(id: id, name: id, direction: "?", bytesDone: 0, bytesTotal: 0, status: "cancelled"))
        default:
            break
        }
    }

    private func sendChunks(id: String, url: URL) async {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return }
        defer { try? handle.close() }
        let total = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.int64Value ?? 0
        var offset: Int64 = 0
        while offset < total {
            autoreleasepool {
                handle.seek(toFileOffset: UInt64(offset))
                let data = handle.readData(ofLength: chunkSize)
                guard !data.isEmpty else { return }
                send?(Envelope(type: MessageTypes.fileChunk, payload: [
                    "transferId": AnyCodable(id),
                    "offset": AnyCodable(offset),
                    "data": AnyCodable(data.base64EncodedString())
                ]))
                offset += Int64(data.count)
            }
            upsert(TransferProgress(id: id, name: url.lastPathComponent, direction: "out", bytesDone: offset, bytesTotal: total, status: "sending"))
            try? await Task.sleep(nanoseconds: 5_000_000)
        }
        send?(Envelope(type: MessageTypes.fileComplete, payload: [
            "transferId": AnyCodable(id),
            "ok": AnyCodable(true)
        ]))
        upsert(TransferProgress(id: id, name: url.lastPathComponent, direction: "out", bytesDone: total, bytesTotal: total, status: "complete"))
    }

    private func streamSHA256(url: URL) -> String? {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? handle.close() }
        var hasher = SHA256()
        while true {
            let chunk = handle.readData(ofLength: 64 * 1024)
            if chunk.isEmpty { break }
            hasher.update(data: chunk)
        }
        return hasher.finalize().compactMap { String(format: "%02x", $0) }.joined()
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
