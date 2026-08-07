import Foundation
import CryptoKit

/// X25519 key agreement + ChaCha20-Poly1305 AEAD (12-byte nonce), matching Android.
final class CryptoEngine: @unchecked Sendable {
    private let privateKey: Curve25519.KeyAgreement.PrivateKey
    let publicKeyData: Data
    private var sessionKey: SymmetricKey?

    init() {
        privateKey = Curve25519.KeyAgreement.PrivateKey()
        publicKeyData = privateKey.publicKey.rawRepresentation
    }

    init(privateKeyB64: String, publicKeyB64: String) throws {
        guard let priv = Data(base64Encoded: privateKeyB64),
              let pub = Data(base64Encoded: publicKeyB64) else {
            throw CryptoError.invalidKey
        }
        privateKey = try Curve25519.KeyAgreement.PrivateKey(rawRepresentation: priv)
        publicKeyData = pub
    }

    var publicKeyB64: String { publicKeyData.base64EncodedString() }
    var privateKeyB64: String { privateKey.rawRepresentation.base64EncodedString() }

    func deriveSession(peerPublicKeyB64: String) throws {
        guard let peerData = Data(base64Encoded: peerPublicKeyB64) else { throw CryptoError.invalidKey }
        let peer = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: peerData)
        let shared = try privateKey.sharedSecretFromKeyAgreement(with: peer)
        let keyData = shared.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: Data("marnock".utf8),
            sharedInfo: Data("session-v1".utf8),
            outputByteCount: 32
        )
        sessionKey = keyData
    }

    func setSessionKey(_ data: Data) {
        sessionKey = SymmetricKey(data: data)
    }

    func sessionKeyData() -> Data? {
        guard let sessionKey else { return nil }
        return sessionKey.withUnsafeBytes { Data($0) }
    }

    func encrypt(_ plaintext: Data) throws -> (nonce: Data, ciphertext: Data) {
        guard let sessionKey else { throw CryptoError.noSession }
        let nonce = ChaChaPoly.Nonce()
        let sealed = try ChaChaPoly.seal(plaintext, using: sessionKey, nonce: nonce)
        return (Data(nonce), sealed.ciphertext + sealed.tag)
    }

    func decrypt(nonce: Data, ciphertextAndTag: Data) throws -> Data {
        guard let sessionKey else { throw CryptoError.noSession }
        guard ciphertextAndTag.count >= 16 else { throw CryptoError.decryptFailed }
        let ct = ciphertextAndTag.dropLast(16)
        let tag = ciphertextAndTag.suffix(16)
        let box = try ChaChaPoly.SealedBox(nonce: ChaChaPoly.Nonce(data: nonce), ciphertext: ct, tag: tag)
        return try ChaChaPoly.open(box, using: sessionKey)
    }

    static func relayAuthToken(sessionKey: Data) -> String {
        sessionKey.prefix(16).map { String(format: "%02x", $0) }.joined()
    }

    enum CryptoError: Error {
        case invalidKey, noSession, decryptFailed
    }
}
