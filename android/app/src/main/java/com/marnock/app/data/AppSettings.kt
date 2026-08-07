package com.marnock.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore("marnock_settings")

class AppSettings(private val context: Context) {
    private val deviceIdKey = stringPreferencesKey("device_id")
    private val privKey = stringPreferencesKey("private_key")
    private val pubKey = stringPreferencesKey("public_key")
    private val peerDeviceIdKey = stringPreferencesKey("peer_device_id")
    private val peerPubKey = stringPreferencesKey("peer_public_key")
    private val sessionKey = stringPreferencesKey("session_key")
    private val clipboardEnabled = booleanPreferencesKey("clipboard_enabled")
    private val localOnly = booleanPreferencesKey("local_only")
    private val relayUrl = stringPreferencesKey("relay_url")
    private val displayName = stringPreferencesKey("display_name")
    private val paired = booleanPreferencesKey("paired")

    val clipboardEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { it[clipboardEnabled] ?: false }

    val localOnlyFlow: Flow<Boolean> =
        context.dataStore.data.map { it[localOnly] ?: true }

    val pairedFlow: Flow<Boolean> =
        context.dataStore.data.map { it[paired] ?: false }

    suspend fun ensureIdentity(): Identity {
        val prefs = context.dataStore.data.first()
        var id = prefs[deviceIdKey]
        var priv = prefs[privKey]
        var pub = prefs[pubKey]
        if (id == null || priv == null || pub == null) {
            val engine = com.marnock.app.crypto.CryptoEngine.generate()
            id = UUID.randomUUID().toString()
            priv = engine.privateKeyB64()
            pub = engine.publicKeyB64()
            context.dataStore.edit {
                it[deviceIdKey] = id!!
                it[privKey] = priv!!
                it[pubKey] = pub!!
                it[displayName] = android.os.Build.MODEL
                it[relayUrl] = "ws://127.0.0.1:8787/ws"
            }
        }
        return Identity(
            deviceId = id!!,
            privateKeyB64 = priv!!,
            publicKeyB64 = pub!!,
            displayName = prefs[displayName] ?: android.os.Build.MODEL
        )
    }

    suspend fun savePairing(peerDeviceId: String, peerPublicKeyB64: String, sessionKeyB64: String) {
        context.dataStore.edit {
            it[peerDeviceIdKey] = peerDeviceId
            it[peerPubKey] = peerPublicKeyB64
            it[sessionKey] = sessionKeyB64
            it[paired] = true
        }
    }

    suspend fun clearPairing() {
        context.dataStore.edit {
            it.remove(peerDeviceIdKey)
            it.remove(peerPubKey)
            it.remove(sessionKey)
            it[paired] = false
        }
    }

    suspend fun pairing(): PairingState? {
        val p = context.dataStore.data.first()
        val peer = p[peerDeviceIdKey] ?: return null
        val peerPub = p[peerPubKey] ?: return null
        val sess = p[sessionKey] ?: return null
        if (p[paired] != true) return null
        return PairingState(peer, peerPub, sess)
    }

    suspend fun setClipboardEnabled(enabled: Boolean) {
        context.dataStore.edit { it[clipboardEnabled] = enabled }
    }

    suspend fun setLocalOnly(enabled: Boolean) {
        context.dataStore.edit { it[localOnly] = enabled }
    }

    suspend fun setRelayUrl(url: String) {
        context.dataStore.edit { it[relayUrl] = url }
    }

    suspend fun relayUrl(): String =
        context.dataStore.data.first()[relayUrl] ?: "ws://127.0.0.1:8787/ws"

    suspend fun isLocalOnly(): Boolean =
        context.dataStore.data.first()[localOnly] ?: true

    suspend fun isClipboardEnabled(): Boolean =
        context.dataStore.data.first()[clipboardEnabled] ?: false

    data class Identity(
        val deviceId: String,
        val privateKeyB64: String,
        val publicKeyB64: String,
        val displayName: String
    )

    data class PairingState(
        val peerDeviceId: String,
        val peerPublicKeyB64: String,
        val sessionKeyB64: String
    )
}
