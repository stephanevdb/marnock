import Foundation

enum MessageTypes {
    static let pairHello = "pair.hello"
    static let pairComplete = "pair.complete"
    static let ping = "ping"
    static let pong = "pong"
    static let sessionFrame = "session.frame"
    static let clipboardSet = "clipboard.set"
    static let clipboardChanged = "clipboard.changed"
    static let notificationPosted = "notification.posted"
    static let notificationRemoved = "notification.removed"
    static let notificationAction = "notification.action"
    static let smsThreads = "sms.threads"
    static let smsMessages = "sms.messages"
    static let smsSend = "sms.send"
    static let smsReceived = "sms.received"
    static let smsThreadsRequest = "sms.threads.request"
    static let smsMessagesRequest = "sms.messages.request"
    static let callState = "call.state"
    static let callHistory = "call.history"
    static let callHistoryRequest = "call.history.request"
    static let callDial = "call.dial"
    static let callAnswer = "call.answer"
    static let callReject = "call.reject"
    static let relayRegister = "relay.register"
    static let relayForward = "relay.forward"

    static let fileOffer = "file.offer"
    static let fileAccept = "file.accept"
    static let fileChunk = "file.chunk"
    static let fileComplete = "file.complete"
    static let fileCancel = "file.cancel"

    static let mediaCommand = "media.command"
    static let mediaState = "media.state"
    static let findRing = "find.ring"
    static let findStop = "find.stop"
    static let deviceStatus = "device.status"

    static let linkOpen = "link.open"
    static let wifiInfo = "wifi.info"
    static let wifiRequest = "wifi.request"
    static let photosListRequest = "photos.list.request"
    static let photosList = "photos.list"
    static let photosGet = "photos.get"
    static let prefsQuiet = "prefs.quiet"
}

struct Envelope: Codable {
    var type: String
    var id: String
    var payload: [String: AnyCodable]

    init(type: String, id: String = UUID().uuidString, payload: [String: AnyCodable] = [:]) {
        self.type = type
        self.id = id
        self.payload = payload
    }
}

/// Lightweight type-erased Codable for JSON payloads.
struct AnyCodable: Codable {
    let value: Any

    init(_ value: Any) { self.value = value }

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { value = NSNull() }
        else if let b = try? c.decode(Bool.self) { value = b }
        else if let i = try? c.decode(Int.self) { value = i }
        else if let d = try? c.decode(Double.self) { value = d }
        else if let s = try? c.decode(String.self) { value = s }
        else if let a = try? c.decode([AnyCodable].self) { value = a.map(\.value) }
        else if let o = try? c.decode([String: AnyCodable].self) { value = o.mapValues(\.value) }
        else { throw DecodingError.dataCorruptedError(in: c, debugDescription: "Unsupported") }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch value {
        case is NSNull: try c.encodeNil()
        case let b as Bool: try c.encode(b)
        case let i as Int: try c.encode(i)
        case let i as Int64: try c.encode(i)
        case let d as Double: try c.encode(d)
        case let s as String: try c.encode(s)
        case let a as [Any]: try c.encode(a.map { AnyCodable($0) })
        case let o as [String: Any]: try c.encode(o.mapValues { AnyCodable($0) })
        default:
            throw EncodingError.invalidValue(value, .init(codingPath: c.codingPath, debugDescription: "Unsupported"))
        }
    }

    var stringValue: String? { value as? String }
    var intValue: Int? {
        if let i = value as? Int { return i }
        if let i = value as? Int64 { return Int(i) }
        if let d = value as? Double { return Int(d) }
        return nil
    }
    var boolValue: Bool? { value as? Bool }
    var int64Value: Int64? {
        if let i = value as? Int64 { return i }
        if let i = value as? Int { return Int64(i) }
        return nil
    }
}
