import Foundation
import Security

/// Minimal Keychain wrapper for pairing / identity secrets.
enum KeychainStore {
    private static let service = "com.marnock.macos"

    enum Account: String {
        case identityPriv = "identity.priv"
        case pairingSessionKey = "pairing.sessionKey"
        case pairingPeerPub = "pairing.peerPub"
    }

    static func set(_ value: String, account: Account) {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account.rawValue
        ]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }

    static func get(_ account: Account) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account.rawValue,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var out: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &out)
        guard status == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func delete(_ account: Account) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account.rawValue
        ]
        SecItemDelete(query as CFDictionary)
    }

    /// Move plaintext UserDefaults secrets into Keychain once, then wipe UD keys.
    static func migrateFromUserDefaultsIfNeeded() {
        let ud = UserDefaults.standard
        if get(.identityPriv) == nil, let priv = ud.string(forKey: "priv") {
            set(priv, account: .identityPriv)
            ud.removeObject(forKey: "priv")
        }
        if get(.pairingSessionKey) == nil, let session = ud.string(forKey: "sessionKey") {
            set(session, account: .pairingSessionKey)
            ud.removeObject(forKey: "sessionKey")
        }
        if get(.pairingPeerPub) == nil, let peerPub = ud.string(forKey: "peerPub") {
            set(peerPub, account: .pairingPeerPub)
            ud.removeObject(forKey: "peerPub")
        }
        // Always scrub leftover plaintext keys if present.
        ud.removeObject(forKey: "priv")
        ud.removeObject(forKey: "sessionKey")
        ud.removeObject(forKey: "peerPub")
    }
}
