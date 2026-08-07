import AppKit
import Combine
import Foundation

struct DeviceStatusInfo: Equatable {
    var battery: Int = -1
    var charging: Bool = false
    var wifiSsid: String = ""
    var cellular: Bool = false
    var hotspotActive: Bool = false
}

struct MediaStateInfo: Equatable {
    var title: String = ""
    var artist: String = ""
    var playing: Bool = false
    var volume: Int = 50
}

struct PhonePhoto: Identifiable, Equatable {
    let id: String
    let date: Int64
    let name: String
    let thumbB64: String
}

struct WifiInfo: Equatable {
    var ssid: String = ""
    var password: String = ""
    var hasPassword: Bool = false
    var hotspotActive: Bool = false
    var note: String = ""
}

extension AppModel {
    func wireFeatureServices() {
        fileTransfer.send = { [weak self] env in self?.sendApp(env) }
        fileTransfer.isConnected = { [weak self] in self?.path != .offline }
        fileTransfer.objectWillChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &featureCancellables)
        quietMonitor.objectWillChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &featureCancellables)
        quietMonitor.start()
        syncQuietPrefs()
    }

    func syncQuietPrefs() {
        sendApp(Envelope(type: MessageTypes.prefsQuiet, payload: [
            "enabled": AnyCodable(quietHoursEnabled && (quietMonitor.screenLocked || quietHoursForce))
        ]))
    }

    var notificationsSuppressed: Bool {
        quietHoursEnabled && (quietMonitor.screenLocked || quietHoursForce)
    }

    func findPhone() {
        sendApp(Envelope(type: MessageTypes.findRing))
    }

    func stopFindPhone() {
        sendApp(Envelope(type: MessageTypes.findStop))
    }

    func mediaCommand(_ command: String, level: Int? = nil) {
        var payload: [String: AnyCodable] = ["command": AnyCodable(command)]
        if let level { payload["level"] = AnyCodable(level) }
        sendApp(Envelope(type: MessageTypes.mediaCommand, payload: payload))
    }

    func openLinkOnPhone(_ url: String) {
        sendApp(Envelope(type: MessageTypes.linkOpen, payload: [
            "url": AnyCodable(url),
            "originDeviceId": AnyCodable(deviceId)
        ]))
    }

    func requestWifiInfo() {
        sendApp(Envelope(type: MessageTypes.wifiRequest))
    }

    func requestPhotos() {
        sendApp(Envelope(type: MessageTypes.photosListRequest, payload: [
            "limit": AnyCodable(100)
        ]))
    }

    func savePhotoToMac(_ id: String) {
        sendApp(Envelope(type: MessageTypes.photosGet, payload: [
            "id": AnyCodable(id)
        ]))
    }

    func sendFile(url: URL) {
        fileTransfer.offer(url: url)
    }

    func handleShareURL(_ url: URL) {
        // marnock://send?path=/tmp/file or marnock://open?url=https://...
        guard let comps = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return }
        if comps.host == "send" || url.path == "/send" {
            if let path = comps.queryItems?.first(where: { $0.name == "path" })?.value {
                sendFile(url: URL(fileURLWithPath: path))
            }
        } else if comps.host == "open" || url.path == "/open" {
            if let link = comps.queryItems?.first(where: { $0.name == "url" })?.value {
                openLinkOnPhone(link)
            }
        }
    }

    func handleFeatureMessage(_ env: Envelope) -> Bool {
        switch env.type {
        case MessageTypes.fileOffer, MessageTypes.fileAccept, MessageTypes.fileChunk,
             MessageTypes.fileComplete, MessageTypes.fileCancel:
            fileTransfer.handle(env)
            return true
        case MessageTypes.deviceStatus:
            deviceStatus = DeviceStatusInfo(
                battery: env.payload["battery"]?.intValue ?? -1,
                charging: env.payload["charging"]?.boolValue ?? false,
                wifiSsid: env.payload["wifiSsid"]?.stringValue ?? "",
                cellular: env.payload["cellular"]?.boolValue ?? false,
                hotspotActive: env.payload["hotspotActive"]?.boolValue ?? false
            )
            return true
        case MessageTypes.mediaState:
            mediaState = MediaStateInfo(
                title: env.payload["title"]?.stringValue ?? "",
                artist: env.payload["artist"]?.stringValue ?? "",
                playing: env.payload["playing"]?.boolValue ?? false,
                volume: env.payload["volume"]?.intValue ?? 50
            )
            return true
        case MessageTypes.linkOpen:
            if let urlStr = env.payload["url"]?.stringValue,
               let url = URL(string: urlStr),
               env.payload["originDeviceId"]?.stringValue != deviceId {
                NSWorkspace.shared.open(url)
            }
            return true
        case MessageTypes.wifiInfo:
            wifiInfo = WifiInfo(
                ssid: env.payload["ssid"]?.stringValue ?? "",
                password: env.payload["password"]?.stringValue ?? "",
                hasPassword: env.payload["hasPassword"]?.boolValue ?? false,
                hotspotActive: env.payload["hotspotActive"]?.boolValue ?? false,
                note: env.payload["note"]?.stringValue ?? ""
            )
            return true
        case MessageTypes.photosList:
            if let arr = env.payload["photos"]?.value as? [Any] {
                phonePhotos = arr.compactMap { item in
                    guard let o = item as? [String: Any] else { return nil }
                    return PhonePhoto(
                        id: o["id"] as? String ?? "",
                        date: (o["date"] as? NSNumber)?.int64Value ?? Int64(o["date"] as? Int ?? 0),
                        name: o["name"] as? String ?? "",
                        thumbB64: o["thumb"] as? String ?? ""
                    )
                }
            }
            return true
        default:
            return false
        }
    }
}
