package com.marnock.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    private val secrets: SharedPreferences by lazy { encryptedPrefs(context) }

    val clipboardEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { it[clipboardEnabled] ?: false }

    val localOnlyFlow: Flow<Boolean> =
        context.dataStore.data.map { it[localOnly] ?: true }

    val pairedFlow: Flow<Boolean> =
        context.dataStore.data.map { it[paired] ?: false }

    suspend fun ensureIdentity(): Identity {
        migrateSecretsFromDataStore()
        val prefs = context.dataStore.data.first()
        var id = prefs[deviceIdKey]
        var priv = secrets.getString(SECRET_PRIVATE_KEY, null)
        var pub = prefs[pubKey]
        if (id == null || priv == null || pub == null) {
            val engine = com.marnock.app.crypto.CryptoEngine.generate()
            id = UUID.randomUUID().toString()
            priv = engine.privateKeyB64()
            pub = engine.publicKeyB64()
            secrets.edit().putString(SECRET_PRIVATE_KEY, priv).apply()
            context.dataStore.edit {
                it[deviceIdKey] = id!!
                it[pubKey] = pub!!
                it[displayName] = android.os.Build.MODEL
                it[relayUrl] = DEFAULT_RELAY_URL
                it.remove(privKey)
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
        secrets.edit()
            .putString(SECRET_SESSION_KEY, sessionKeyB64)
            .apply()
        context.dataStore.edit {
            it[peerDeviceIdKey] = peerDeviceId
            it[peerPubKey] = peerPublicKeyB64
            it.remove(sessionKey)
            it[paired] = true
        }
    }

    suspend fun clearPairing() {
        secrets.edit().remove(SECRET_SESSION_KEY).apply()
        context.dataStore.edit {
            it.remove(peerDeviceIdKey)
            it.remove(peerPubKey)
            it.remove(sessionKey)
            it[paired] = false
        }
    }

    suspend fun pairing(): PairingState? {
        migrateSecretsFromDataStore()
        val p = context.dataStore.data.first()
        val peer = p[peerDeviceIdKey] ?: return null
        val peerPub = p[peerPubKey] ?: return null
        val sess = secrets.getString(SECRET_SESSION_KEY, null) ?: return null
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
        context.dataStore.data.first()[relayUrl] ?: DEFAULT_RELAY_URL

    suspend fun isLocalOnly(): Boolean =
        context.dataStore.data.first()[localOnly] ?: true

    suspend fun isClipboardEnabled(): Boolean =
        context.dataStore.data.first()[clipboardEnabled] ?: false

    private suspend fun migrateSecretsFromDataStore() {
        val prefs = context.dataStore.data.first()
        val editor = secrets.edit()
        var changed = false
        prefs[privKey]?.let { plaintext ->
            if (secrets.getString(SECRET_PRIVATE_KEY, null).isNullOrEmpty()) {
                editor.putString(SECRET_PRIVATE_KEY, plaintext)
                changed = true
            }
        }
        prefs[sessionKey]?.let { plaintext ->
            if (secrets.getString(SECRET_SESSION_KEY, null).isNullOrEmpty()) {
                editor.putString(SECRET_SESSION_KEY, plaintext)
                changed = true
            }
        }
        if (changed) editor.apply()
        if (prefs[privKey] != null || prefs[sessionKey] != null) {
            context.dataStore.edit {
                it.remove(privKey)
                it.remove(sessionKey)
            }
        }
    }

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

    companion object {
        const val DEFAULT_RELAY_URL = "wss://marnock.stephanevdb.com/ws"
        private const val SECRET_PRIVATE_KEY = "private_key"
        private const val SECRET_SESSION_KEY = "session_key"

        private fun encryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "marnock_secrets",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
