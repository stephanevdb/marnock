package com.marnock.app.crypto

import android.util.Base64
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

/**
 * X25519 key agreement + ChaCha20-Poly1305 AEAD (12-byte nonce).
 * Compatible across Android (BC) and macOS (CryptoKit ChaChaPoly).
 */
class CryptoEngine(
    private val privateKey: ByteArray,
    val publicKey: ByteArray
) {
    private var sessionKey: ByteArray? = null

    fun deriveSession(peerPublicKey: ByteArray) {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), shared, 0)

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(shared, "marnock".toByteArray(), "session-v1".toByteArray()))
        val key = ByteArray(32)
        hkdf.generateBytes(key, 0, 32)
        sessionKey = key
        shared.fill(0)
    }

    fun setSessionKey(key: ByteArray) {
        sessionKey = key.copyOf()
    }

    fun sessionKeyBytes(): ByteArray? = sessionKey?.copyOf()

    fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val key = sessionKey ?: error("No session key")
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val n = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, n)
        return nonce to out
    }

    fun decrypt(nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        val key = sessionKey ?: error("No session key")
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce))
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        val n = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
        cipher.doFinal(out, n)
        return out
    }

    fun publicKeyB64(): String = Base64.encodeToString(publicKey, Base64.NO_WRAP)
    fun privateKeyB64(): String = Base64.encodeToString(privateKey, Base64.NO_WRAP)

    companion object {
        fun generate(): CryptoEngine {
            val gen = X25519KeyPairGenerator()
            gen.init(X25519KeyGenerationParameters(SecureRandom()))
            val kp = gen.generateKeyPair()
            val priv = (kp.private as X25519PrivateKeyParameters).encoded
            val pub = (kp.public as X25519PublicKeyParameters).encoded
            return CryptoEngine(priv, pub)
        }

        fun fromStored(privateB64: String, publicB64: String): CryptoEngine {
            return CryptoEngine(
                Base64.decode(privateB64, Base64.NO_WRAP),
                Base64.decode(publicB64, Base64.NO_WRAP)
            )
        }

        fun decodeB64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
        fun encodeB64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

        fun relayAuthToken(sessionKey: ByteArray): String =
            sessionKey.copyOfRange(0, minOf(16, sessionKey.size)).joinToString("") { "%02x".format(it) }
    }
}
