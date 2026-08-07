package com.marnock.app.transport

import com.marnock.app.crypto.CryptoEngine
import com.marnock.app.protocol.Envelope
import com.marnock.app.protocol.MessageTypes
import com.marnock.app.protocol.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WebSocketSession(
    private val scope: CoroutineScope,
    private val crypto: CryptoEngine,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null
    private val open = AtomicBoolean(false)
    private var readerJob: Job? = null

    private val _incoming = MutableSharedFlow<Envelope>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incoming: SharedFlow<Envelope> = _incoming

    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)
    val connectionState: SharedFlow<Boolean> = _connectionState

    fun connect(url: String) {
        close()
        val req = Request.Builder().url(url).build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                open.set(true)
                _connectionState.tryEmit(true)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBytes(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleBytes(text.toByteArray(Charsets.UTF_8))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                open.set(false)
                _connectionState.tryEmit(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                open.set(false)
                _connectionState.tryEmit(false)
            }
        })
    }

    private fun handleBytes(raw: ByteArray) {
        scope.launch(Dispatchers.Default) {
            try {
                val jsonBytes = Framing.decode(raw)
                val env = json.decodeFromString<Envelope>(jsonBytes.toString(Charsets.UTF_8))
                when (env.type) {
                    MessageTypes.SESSION_FRAME -> {
                        val nonce = CryptoEngine.decodeB64(env.payload.str("nonce"))
                        val ct = CryptoEngine.decodeB64(env.payload.str("ciphertext"))
                        val plain = crypto.decrypt(nonce, ct)
                        val inner = json.decodeFromString<Envelope>(plain.toString(Charsets.UTF_8))
                        _incoming.emit(inner)
                    }
                    MessageTypes.RELAY_FORWARD -> {
                        // Outer relay envelope: ciphertext is a length-prefixed encrypted session frame JSON
                        val blob = CryptoEngine.decodeB64(env.payload.str("ciphertext"))
                        val innerJson = Framing.decode(blob)
                        val wrapped = json.decodeFromString<Envelope>(innerJson.toString(Charsets.UTF_8))
                        if (wrapped.type == MessageTypes.SESSION_FRAME) {
                            val nonce = CryptoEngine.decodeB64(wrapped.payload.str("nonce"))
                            val ct = CryptoEngine.decodeB64(wrapped.payload.str("ciphertext"))
                            val plain = crypto.decrypt(nonce, ct)
                            _incoming.emit(json.decodeFromString(plain.toString(Charsets.UTF_8)))
                        } else {
                            _incoming.emit(wrapped)
                        }
                    }
                    else -> _incoming.emit(env)
                }
            } catch (_: Exception) {
                // drop malformed
            }
        }
    }

    fun sendPlain(envelope: Envelope) {
        val bytes = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        socket?.send(Framing.encode(bytes).toByteString())
    }

    fun sendEncrypted(envelope: Envelope) {
        val plain = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        val (nonce, ct) = crypto.encrypt(plain)
        val frame = Envelope(
            type = MessageTypes.SESSION_FRAME,
            payload = buildJsonObject {
                put("nonce", CryptoEngine.encodeB64(nonce))
                put("ciphertext", CryptoEngine.encodeB64(ct))
            }
        )
        sendPlain(frame)
    }

    /** Send encrypted payload via relay as opaque blob. */
    fun sendViaRelay(toDeviceId: String, fromDeviceId: String, envelope: Envelope) {
        val plain = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        val (nonce, ct) = crypto.encrypt(plain)
        val sessionEnv = Envelope(
            type = MessageTypes.SESSION_FRAME,
            payload = buildJsonObject {
                put("nonce", CryptoEngine.encodeB64(nonce))
                put("ciphertext", CryptoEngine.encodeB64(ct))
            }
        )
        val blob = Framing.encode(json.encodeToString(sessionEnv).toByteArray(Charsets.UTF_8))
        sendPlain(
            Envelope(
                type = MessageTypes.RELAY_FORWARD,
                payload = buildJsonObject {
                    put("toDeviceId", toDeviceId)
                    put("fromDeviceId", fromDeviceId)
                    put("ciphertext", CryptoEngine.encodeB64(blob))
                }
            )
        )
    }

    fun isOpen(): Boolean = open.get()

    fun close() {
        readerJob?.cancel()
        socket?.close(1000, "bye")
        socket = null
        open.set(false)
        _connectionState.tryEmit(false)
    }
}
