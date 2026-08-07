import AppKit
import Foundation
import UserNotifications
import Combine
import Darwin

enum ConnectionPath: String {
    case offline = "Offline"
    case lan = "Connected (LAN)"
    case relay = "Connected (Relay)"
}

struct NotifAction: Identifiable, Equatable, Hashable {
    let id: String
    let title: String
    let allowsReply: Bool
}

struct MirroredNotification: Identifiable, Equatable {
    let id: String
    let packageName: String
    let title: String
    let text: String
    let actions: [NotifAction]
}

struct SmsThread: Identifiable, Equatable {
    let id: String
    let address: String
    let contactName: String
    let snippet: String
    let date: Int64
}

struct SmsMessage: Identifiable, Equatable {
    let id: String
    let threadId: String
    let address: String
    let body: String
    let date: Int64
    let type: String
}

struct CallHistoryEntry: Identifiable, Equatable {
    let id: String
    let number: String
    let name: String
    let date: Int64
    let duration: Int64
    let type: String
}

struct CallStateInfo: Equatable {
    var state: String = "idle"
    var number: String = ""
    var name: String = ""
}

@MainActor
final class AppModel: ObservableObject {
    @Published var path: ConnectionPath = .offline
    @Published var status: String = "Starting…"
    @Published var pairingCode: String = ""
    @Published var qrPayload: String = ""
    @Published var clipboardEnabled: Bool = false {
        didSet {
            clipboard.enabled = clipboardEnabled
            UserDefaults.standard.set(clipboardEnabled, forKey: "clipboardEnabled")
        }
    }
    @Published var localOnly: Bool = true {
        didSet { UserDefaults.standard.set(localOnly, forKey: "localOnly") }
    }
    @Published var relayURL: String = UserDefaults.standard.string(forKey: "relayURL") ?? "wss://marnock.stephanevdb.com/ws" {
        didSet { UserDefaults.standard.set(relayURL, forKey: "relayURL") }
    }
    @Published var lastClipboard: String = ""
    @Published var notifications: [MirroredNotification] = []
    @Published var smsThreads: [SmsThread] = []
    @Published var smsMessages: [SmsMessage] = []
    @Published var selectedThreadId: String?
    @Published var callHistory: [CallHistoryEntry] = []
    @Published var callState = CallStateInfo()
    @Published var pairedPeerId: String?
    @Published var deviceId: String = ""
    @Published var deviceStatus = DeviceStatusInfo()
    @Published var mediaState = MediaStateInfo()
    @Published var wifiInfo = WifiInfo()
    @Published var phonePhotos: [PhonePhoto] = []
    @Published var deniedNotificationPackages: Set<String> = [] {
        didSet {
            UserDefaults.standard.set(Array(deniedNotificationPackages), forKey: "deniedNotifPackages")
        }
    }
    @Published var quietHoursEnabled: Bool = false {
        didSet {
            UserDefaults.standard.set(quietHoursEnabled, forKey: "quietHoursEnabled")
            syncQuietPrefs()
        }
    }
    @Published var quietHoursForce: Bool = false {
        didSet {
            UserDefaults.standard.set(quietHoursForce, forKey: "quietHoursForce")
            syncQuietPrefs()
        }
    }
    @Published var openLinkDraft: String = ""

    let fileTransfer = FileTransferService()
    let quietMonitor = QuietHoursMonitor()

    private var crypto: CryptoEngine!
    private let server = WebSocketServer(port: 0)
    private let relayClient = WebSocketClient()
    private var advertiser: NetServiceAdvertiser!
    private let clipboard = ClipboardMonitor()
    private var useRelay = false
    private var sessionReady = false
    private var lanPort: UInt16 = 0
    private var cancellables = Set<AnyCancellable>()
    var featureCancellables = Set<AnyCancellable>()
    private var encoder: JSONEncoder { JSONEncoder() }
    private var decoder: JSONDecoder { JSONDecoder() }

    func start() {
        clipboardEnabled = UserDefaults.standard.bool(forKey: "clipboardEnabled")
        localOnly = UserDefaults.standard.object(forKey: "localOnly") as? Bool ?? true
        quietHoursEnabled = UserDefaults.standard.bool(forKey: "quietHoursEnabled")
        quietHoursForce = UserDefaults.standard.bool(forKey: "quietHoursForce")
        deniedNotificationPackages = Set(UserDefaults.standard.stringArray(forKey: "deniedNotifPackages") ?? [])
        wireFeatureServices()

        if let priv = UserDefaults.standard.string(forKey: "priv"),
           let pub = UserDefaults.standard.string(forKey: "pub"),
           let eng = try? CryptoEngine(privateKeyB64: priv, publicKeyB64: pub) {
            crypto = eng
            deviceId = UserDefaults.standard.string(forKey: "deviceId") ?? UUID().uuidString
        } else {
            crypto = CryptoEngine()
            deviceId = UUID().uuidString
            UserDefaults.standard.set(crypto.privateKeyB64, forKey: "priv")
            UserDefaults.standard.set(crypto.publicKeyB64, forKey: "pub")
            UserDefaults.standard.set(deviceId, forKey: "deviceId")
        }

        if let peer = UserDefaults.standard.string(forKey: "peerDeviceId"),
           let session = UserDefaults.standard.string(forKey: "sessionKey"),
           let data = Data(base64Encoded: session) {
            pairedPeerId = peer
            crypto.setSessionKey(data)
            sessionReady = true
        }

        pairingCode = String(format: "%06d", Int.random(in: 0...999999))
        advertiser = NetServiceAdvertiser(deviceId: deviceId, displayName: Host.current().localizedName ?? "Mac")

        server.onMessage = { [weak self] data in
            Task { @MainActor in self?.handleFrame(data, viaRelay: false) }
        }
        server.onClientConnected = { [weak self] in
            Task { @MainActor in
                self?.useRelay = false
                self?.path = .lan
                self?.status = "Phone connected over LAN"
            }
        }
        server.onClientDisconnected = { [weak self] in
            Task { @MainActor in
                if self?.path == .lan {
                    self?.path = .offline
                    self?.status = "LAN disconnected"
                    self?.considerRelayFallback()
                }
            }
        }

        do {
            try server.start()
            // Wait briefly for port assignment
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
                guard let self else { return }
                self.lanPort = self.server.listeningPort
                self.advertiser.start(port: Int(self.lanPort))
                self.refreshQR()
                self.status = "Listening on port \(self.lanPort)"
            }
        } catch {
            status = "Server failed: \(error.localizedDescription)"
        }

        clipboard.onLocalChange = { [weak self] text in
            Task { @MainActor in
                guard let self else { return }
                self.lastClipboard = String(text.prefix(200))
                self.sendApp(Envelope(type: MessageTypes.clipboardChanged, payload: [
                    "text": AnyCodable(text),
                    "originDeviceId": AnyCodable(self.deviceId),
                    "ts": AnyCodable(Int(Date().timeIntervalSince1970 * 1000))
                ]))
            }
        }
        clipboard.enabled = clipboardEnabled
        clipboard.start()

        // UNUserNotificationCenter requires a real .app bundle (bundle id). Bare `swift run` crashes without this.
        if Self.canUseUserNotifications {
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
        }

        if sessionReady {
            considerRelayFallback()
        }

        quietMonitor.$screenLocked
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.syncQuietPrefs() }
            .store(in: &cancellables)
    }

    /// `swift run` launches a bare executable; UserNotifications asserts without an .app bundle id.
    private static var canUseUserNotifications: Bool {
        guard let id = Bundle.main.bundleIdentifier, !id.isEmpty else { return false }
        return Bundle.main.bundleURL.pathExtension == "app"
    }

    func refreshQR() {
        let host = Self.primaryIPv4() ?? "127.0.0.1"
        let dict: [String: Any] = [
            "deviceId": deviceId,
            "publicKey": crypto.publicKeyB64,
            "host": host,
            "port": lanPort,
            "pairingCode": pairingCode
        ]
        if let data = try? JSONSerialization.data(withJSONObject: dict),
           let s = String(data: data, encoding: .utf8) {
            qrPayload = s
        }
    }

    private static func primaryIPv4() -> String? {
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return nil }
        defer { freeifaddrs(ifaddr) }
        var ptr: UnsafeMutablePointer<ifaddrs>? = first
        while let p = ptr {
            let flags = Int32(p.pointee.ifa_flags)
            if (flags & IFF_UP) == IFF_UP, (flags & IFF_LOOPBACK) != IFF_LOOPBACK,
               p.pointee.ifa_addr.pointee.sa_family == UInt8(AF_INET) {
                var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                getnameinfo(p.pointee.ifa_addr, socklen_t(p.pointee.ifa_addr.pointee.sa_len),
                            &hostname, socklen_t(hostname.count), nil, 0, NI_NUMERICHOST)
                let ip = String(cString: hostname)
                if ip.hasPrefix("192.168.") || ip.hasPrefix("10.") || ip.hasPrefix("172.") {
                    return ip
                }
            }
            ptr = p.pointee.ifa_next
        }
        return nil
    }

    private func considerRelayFallback() {
        guard !localOnly, path == .offline, sessionReady else { return }
        connectRelay()
    }

    private func connectRelay() {
        guard let url = URL(string: relayURL) else { return }
        useRelay = true
        status = "Connecting relay…"
        relayClient.onOpen = { [weak self] in
            Task { @MainActor in
                guard let self, let key = self.crypto.sessionKeyData() else { return }
                self.path = .relay
                self.status = "Connected (Relay)"
                self.sendPlain(Envelope(type: MessageTypes.relayRegister, payload: [
                    "deviceId": AnyCodable(self.deviceId),
                    "authToken": AnyCodable(CryptoEngine.relayAuthToken(sessionKey: key))
                ]), forceRelay: true)
            }
        }
        relayClient.onClose = { [weak self] in
            Task { @MainActor in
                if self?.useRelay == true {
                    self?.path = .offline
                    self?.status = "Relay disconnected"
                }
            }
        }
        relayClient.onMessage = { [weak self] data in
            Task { @MainActor in self?.handleFrame(data, viaRelay: true) }
        }
        relayClient.connect(url: url)
    }

    private func handleFrame(_ frame: Data, viaRelay: Bool) {
        let jsonData = Framing.decode(frame)
        guard let env = try? decoder.decode(Envelope.self, from: jsonData) else { return }

        if env.type == MessageTypes.relayForward {
            if let b64 = env.payload["ciphertext"]?.stringValue,
               let blob = Data(base64Encoded: b64) {
                handleFrame(blob, viaRelay: true)
            }
            return
        }

        if env.type == MessageTypes.sessionFrame {
            guard let nonceB64 = env.payload["nonce"]?.stringValue,
                  let ctB64 = env.payload["ciphertext"]?.stringValue,
                  let nonce = Data(base64Encoded: nonceB64),
                  let ct = Data(base64Encoded: ctB64),
                  let plain = try? crypto.decrypt(nonce: nonce, ciphertextAndTag: ct),
                  let inner = try? decoder.decode(Envelope.self, from: plain) else { return }
            handleApp(inner)
            return
        }

        handleApp(env)
    }

    private func handleApp(_ env: Envelope) {
        if handleFeatureMessage(env) { return }
        switch env.type {
        case MessageTypes.pairHello:
            handlePairHello(env)
        case MessageTypes.ping:
            sendPlain(Envelope(type: MessageTypes.pong, id: env.id))
        case MessageTypes.clipboardSet, MessageTypes.clipboardChanged:
            if let text = env.payload["text"]?.stringValue,
               let origin = env.payload["originDeviceId"]?.stringValue,
               origin != deviceId {
                clipboard.applyRemote(text)
                lastClipboard = String(text.prefix(200))
            }
        case MessageTypes.notificationPosted:
            let key = env.payload["key"]?.stringValue ?? UUID().uuidString
            let title = env.payload["title"]?.stringValue ?? ""
            let text = env.payload["text"]?.stringValue ?? ""
            let pkg = env.payload["packageName"]?.stringValue ?? ""
            if deniedNotificationPackages.contains(pkg) { return }
            if notificationsSuppressed { return }
            var actions: [NotifAction] = []
            if let arr = env.payload["actions"]?.value as? [Any] {
                for item in arr {
                    guard let o = item as? [String: Any] else { continue }
                    actions.append(NotifAction(
                        id: o["id"] as? String ?? "",
                        title: o["title"] as? String ?? "",
                        allowsReply: o["allowsReply"] as? Bool ?? false
                    ))
                }
            }
            let n = MirroredNotification(id: key, packageName: pkg, title: title, text: text, actions: actions)
            notifications.removeAll { $0.id == key }
            notifications.insert(n, at: 0)
            postSystemNotification(n)
        case MessageTypes.notificationRemoved:
            if let key = env.payload["key"]?.stringValue {
                notifications.removeAll { $0.id == key }
            }
        case MessageTypes.smsThreads:
            if let arr = env.payload["threads"]?.value as? [Any] {
                smsThreads = arr.compactMap { item in
                    guard let o = item as? [String: Any] else { return nil }
                    return SmsThread(
                        id: o["threadId"] as? String ?? "",
                        address: o["address"] as? String ?? "",
                        contactName: o["contactName"] as? String ?? "",
                        snippet: o["snippet"] as? String ?? "",
                        date: (o["date"] as? NSNumber)?.int64Value ?? 0
                    )
                }
            }
        case MessageTypes.smsMessages:
            if let arr = env.payload["messages"]?.value as? [Any] {
                smsMessages = arr.compactMap { item in
                    guard let o = item as? [String: Any] else { return nil }
                    return SmsMessage(
                        id: o["id"] as? String ?? UUID().uuidString,
                        threadId: o["threadId"] as? String ?? "",
                        address: o["address"] as? String ?? "",
                        body: o["body"] as? String ?? "",
                        date: (o["date"] as? NSNumber)?.int64Value ?? 0,
                        type: o["type"] as? String ?? "other"
                    )
                }
            }
        case MessageTypes.smsReceived:
            if let body = env.payload["body"]?.stringValue,
               let address = env.payload["address"]?.stringValue {
                let msg = SmsMessage(
                    id: env.payload["id"]?.stringValue ?? UUID().uuidString,
                    threadId: env.payload["threadId"]?.stringValue ?? "",
                    address: address,
                    body: body,
                    date: Int64(env.payload["date"]?.intValue ?? 0),
                    type: "inbox"
                )
                smsMessages.append(msg)
                if Self.canUseUserNotifications, !notificationsSuppressed {
                    let content = UNMutableNotificationContent()
                    content.title = address
                    content.body = body
                    UNUserNotificationCenter.current().add(
                        UNNotificationRequest(identifier: msg.id, content: content, trigger: nil)
                    )
                }
            }
        case MessageTypes.callState:
            callState = CallStateInfo(
                state: env.payload["state"]?.stringValue ?? "idle",
                number: env.payload["number"]?.stringValue ?? "",
                name: env.payload["name"]?.stringValue ?? ""
            )
        case MessageTypes.callHistory:
            if let arr = env.payload["entries"]?.value as? [Any] {
                callHistory = arr.compactMap { item in
                    guard let o = item as? [String: Any] else { return nil }
                    return CallHistoryEntry(
                        id: o["id"] as? String ?? UUID().uuidString,
                        number: o["number"] as? String ?? "",
                        name: o["name"] as? String ?? "",
                        date: (o["date"] as? NSNumber)?.int64Value ?? 0,
                        duration: (o["duration"] as? NSNumber)?.int64Value ?? 0,
                        type: o["type"] as? String ?? "other"
                    )
                }
            }
        default:
            break
        }
    }

    private func handlePairHello(_ env: Envelope) {
        let peerId = env.payload["deviceId"]?.stringValue ?? ""
        let peerPub = env.payload["publicKey"]?.stringValue ?? ""
        let code = env.payload["pairingCode"]?.stringValue ?? ""
        guard code == pairingCode else {
            status = "Pairing code mismatch"
            return
        }
        do {
            try crypto.deriveSession(peerPublicKeyB64: peerPub)
            pairedPeerId = peerId
            if let key = crypto.sessionKeyData() {
                UserDefaults.standard.set(peerId, forKey: "peerDeviceId")
                UserDefaults.standard.set(key.base64EncodedString(), forKey: "sessionKey")
                UserDefaults.standard.set(peerPub, forKey: "peerPub")
            }
            sessionReady = true
            sendPlain(Envelope(type: MessageTypes.pairComplete, payload: [
                "deviceId": AnyCodable(deviceId),
                "ok": AnyCodable(true)
            ]))
            path = .lan
            status = "Paired with \(peerId)"
            refreshQR()
        } catch {
            status = "Pairing crypto failed"
        }
    }

    private func postSystemNotification(_ n: MirroredNotification) {
        guard Self.canUseUserNotifications else { return }
        let content = UNMutableNotificationContent()
        content.title = n.title.isEmpty ? n.packageName : n.title
        content.body = n.text
        content.userInfo = ["key": n.id]
        UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: n.id, content: content, trigger: nil)
        )
    }

    func sendApp(_ env: Envelope) {
        guard sessionReady else { return }
        do {
            let plain = try encoder.encode(env)
            let sealed = try crypto.encrypt(plain)
            let frame = Envelope(type: MessageTypes.sessionFrame, payload: [
                "nonce": AnyCodable(sealed.nonce.base64EncodedString()),
                "ciphertext": AnyCodable(sealed.ciphertext.base64EncodedString())
            ])
            if useRelay, let peer = pairedPeerId {
                let sessionData = try encoder.encode(frame)
                let blob = Framing.encode(sessionData)
                sendPlain(Envelope(type: MessageTypes.relayForward, payload: [
                    "toDeviceId": AnyCodable(peer),
                    "fromDeviceId": AnyCodable(deviceId),
                    "ciphertext": AnyCodable(blob.base64EncodedString())
                ]), forceRelay: true)
            } else {
                sendPlain(frame)
            }
        } catch {
            status = "Send failed: \(error.localizedDescription)"
        }
    }

    private func sendPlain(_ env: Envelope, forceRelay: Bool = false) {
        guard let data = try? encoder.encode(env) else { return }
        let frame = Framing.encode(data)
        if forceRelay || useRelay {
            relayClient.send(frame)
        } else {
            server.broadcast(frame)
        }
    }

    // MARK: - Feature actions

    func refreshSmsThreads() {
        sendApp(Envelope(type: MessageTypes.smsThreadsRequest))
    }

    func openThread(_ id: String) {
        selectedThreadId = id
        sendApp(Envelope(type: MessageTypes.smsMessagesRequest, payload: [
            "threadId": AnyCodable(id)
        ]))
    }

    func sendSms(address: String, body: String) {
        sendApp(Envelope(type: MessageTypes.smsSend, payload: [
            "address": AnyCodable(address),
            "body": AnyCodable(body),
            "requestId": AnyCodable(UUID().uuidString)
        ]))
    }

    func refreshCallHistory() {
        sendApp(Envelope(type: MessageTypes.callHistoryRequest))
    }

    func dial(_ number: String) {
        sendApp(Envelope(type: MessageTypes.callDial, payload: ["number": AnyCodable(number)]))
    }

    func answerCall() {
        sendApp(Envelope(type: MessageTypes.callAnswer))
    }

    func rejectCall() {
        sendApp(Envelope(type: MessageTypes.callReject))
    }

    func invokeNotificationAction(key: String, actionId: String, reply: String?) {
        var payload: [String: AnyCodable] = [
            "key": AnyCodable(key),
            "actionId": AnyCodable(actionId)
        ]
        if let reply { payload["replyText"] = AnyCodable(reply) }
        sendApp(Envelope(type: MessageTypes.notificationAction, payload: payload))
    }

    func clearPairing() {
        UserDefaults.standard.removeObject(forKey: "peerDeviceId")
        UserDefaults.standard.removeObject(forKey: "sessionKey")
        pairedPeerId = nil
        sessionReady = false
        pairingCode = String(format: "%06d", Int.random(in: 0...999999))
        refreshQR()
        status = "Pairing cleared"
    }
}
