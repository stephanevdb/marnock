import Foundation
import os.log

private let log = Logger(subsystem: "com.marnock.macos", category: "bonjour")

/// Bonjour advertiser for `_marnock._tcp` (does not accept connections; WebSocketServer owns the port).
final class NetServiceAdvertiser: NSObject, NetServiceDelegate, @unchecked Sendable {
    private var service: NetService?
    private let deviceId: String
    private let displayName: String

    init(deviceId: String, displayName: String) {
        self.deviceId = deviceId
        self.displayName = displayName
    }

    func start(port: Int) {
        stop()
        // Bonjour service names must be short / DNS-label safe
        let safeName = Self.sanitizeServiceName(displayName)
        let svc = NetService(domain: "local.", type: "_marnock._tcp.", name: "Marnock-\(safeName)", port: Int32(port))
        let txt: [String: Data] = [
            "deviceId": Data(deviceId.utf8),
            "name": Data(displayName.utf8),
            "ver": Data("1".utf8)
        ]
        svc.setTXTRecord(NetService.data(fromTXTRecord: txt))
        svc.delegate = self
        svc.publish()
        service = svc
        log.info("Publishing _marnock._tcp name=\(safeName, privacy: .public) port=\(port)")
    }

    func stop() {
        service?.stop()
        service = nil
    }

    func netServiceDidPublish(_ sender: NetService) {
        log.info("Bonjour published: \(sender.name, privacy: .public)")
    }

    func netService(_ sender: NetService, didNotPublish errorDict: [String: NSNumber]) {
        log.error("Bonjour publish failed: \(errorDict, privacy: .public)")
    }

    private static func sanitizeServiceName(_ raw: String) -> String {
        let trimmed = raw
            .replacingOccurrences(of: " ", with: "-")
            .filter { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" }
        let clipped = String(trimmed.prefix(40))
        return clipped.isEmpty ? "Mac" : clipped
    }
}
