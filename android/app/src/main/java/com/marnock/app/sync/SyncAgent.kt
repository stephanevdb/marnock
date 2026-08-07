package com.marnock.app.sync

import android.content.Context
import android.util.Log
import android.content.Intent
import android.net.Uri
import com.marnock.app.calls.CallController
import com.marnock.app.clipboard.ClipboardSync
import com.marnock.app.crypto.CryptoEngine
import com.marnock.app.data.AppSettings
import com.marnock.app.discovery.DiscoveredPeer
import com.marnock.app.discovery.NsdBrowser
import com.marnock.app.find.FindPhoneController
import com.marnock.app.media.MediaControllerHelper
import com.marnock.app.photos.PhotoRepository
import com.marnock.app.protocol.Envelope
import com.marnock.app.protocol.MessageTypes
import com.marnock.app.protocol.bool
import com.marnock.app.protocol.str
import com.marnock.app.sms.SmsRepository
import com.marnock.app.status.DeviceStatusPublisher
import com.marnock.app.transfer.FileTransferManager
import com.marnock.app.transport.WebSocketSession
import com.marnock.app.wifi.WifiInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject

enum class ConnectionPath { Offline, Lan, Relay }

class SyncAgent(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: AppSettings
) {
    private val nsd = NsdBrowser(context)
    private val clipboard = ClipboardSync(context)
    private val sms = SmsRepository(context)
    private val calls = CallController(context)
    private val wifi = WifiInfoProvider(context)
    private val media = MediaControllerHelper(context)
    private val findPhone = FindPhoneController(context)
    private val photos = PhotoRepository(context)
    private lateinit var files: FileTransferManager
    private lateinit var deviceStatus: DeviceStatusPublisher

    private lateinit var identity: AppSettings.Identity
    private lateinit var crypto: CryptoEngine
    private var session: WebSocketSession? = null

    private val _path = MutableStateFlow(ConnectionPath.Offline)
    val path: StateFlow<ConnectionPath> = _path

    private val _status = MutableStateFlow("Starting…")
    val status: StateFlow<String> = _status

    private val _lastClipboard = MutableStateFlow("")
    val lastClipboard: StateFlow<String> = _lastClipboard

    private val _findRinging = MutableStateFlow(false)
    val findRinging: StateFlow<Boolean> = _findRinging

    private var peerDeviceId: String? = null
    private var useRelay = false
    private var jobs = mutableListOf<Job>()
    private var sessionJobs = mutableListOf<Job>()
    private var started = false
    private var currentUrl: String? = null
    private var connecting = false
    private var backoffMs = 2_000L

    fun start() {
        if (started) return
        started = true
        files = FileTransferManager(context, scope) { sendAppLanOnly(it) }
        deviceStatus = DeviceStatusPublisher(context, scope, wifi) { sendApp(it) }
        scope.launch {
            identity = settings.ensureIdentity()
            crypto = CryptoEngine.fromStored(identity.privateKeyB64, identity.publicKeyB64)
            settings.pairing()?.let { p ->
                peerDeviceId = p.peerDeviceId
                crypto.setSessionKey(CryptoEngine.decodeB64(p.sessionKeyB64))
            }
            nsd.start()
            sms.startWatching()
            calls.start()
            deviceStatus.start()
            clipboard.setEnabled(settings.isClipboardEnabled())
            wireLocalEmitters()
            scope.launch {
                while (isActive) {
                    if (session?.isOpen() == true) {
                        sendApp(media.currentStateEnvelope())
                    }
                    delay(10_000)
                }
            }
            connectLoop()
        }
        scope.launch {
            settings.clipboardEnabledFlow.collectLatest { clipboard.setEnabled(it) }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        sessionJobs.forEach { it.cancel() }
        sessionJobs.clear()
        session?.close()
        session = null
        currentUrl = null
        connecting = false
        nsd.stop()
        sms.stopWatching()
        clipboard.dispose()
        started = false
        _path.value = ConnectionPath.Offline
    }

    /** Pair by scanning Mac QR JSON. */
    fun pairFromQr(qrJson: String) {
        scope.launch {
            try {
                val obj = JSONObject(qrJson)
                val host = obj.getString("host")
                val port = obj.getInt("port")
                val peerId = obj.getString("deviceId")
                val peerPub = obj.getString("publicKey")
                val code = obj.getString("pairingCode")
                identity = settings.ensureIdentity()
                crypto = CryptoEngine.fromStored(identity.privateKeyB64, identity.publicKeyB64)
                crypto.deriveSession(CryptoEngine.decodeB64(peerPub))
                peerDeviceId = peerId
                openLan("ws://$host:$port/", force = true)
                delay(300)
                sendPlain(
                    Envelope(
                        type = MessageTypes.PAIR_HELLO,
                        payload = buildJsonObject {
                            put("deviceId", identity.deviceId)
                            put("publicKey", identity.publicKeyB64)
                            put("pairingCode", code)
                            put("displayName", identity.displayName)
                            put("platform", "android")
                        }
                    )
                )
                settings.savePairing(
                    peerId,
                    peerPub,
                    CryptoEngine.encodeB64(crypto.sessionKeyBytes()!!)
                )
                _status.value = "Paired with $peerId"
            } catch (e: Exception) {
                _status.value = "Pairing failed: ${e.message}"
                Log.e(TAG, "pair", e)
            }
        }
    }

    fun connectToPeer(peer: DiscoveredPeer) {
        scope.launch {
            openLan("ws://${peer.host}:${peer.port}/", force = true)
        }
    }

    private suspend fun connectLoop() {
        while (scope.isActive) {
            val paired = settings.pairing()
            if (paired == null) {
                _status.value = "Not paired — scan Mac QR"
                _path.value = ConnectionPath.Offline
                delay(2000)
                continue
            }
            peerDeviceId = paired.peerDeviceId
            crypto.setSessionKey(CryptoEngine.decodeB64(paired.sessionKeyB64))

            // Healthy session — leave it alone
            if (session?.isOpen() == true) {
                backoffMs = 2_000L
                if (_path.value == ConnectionPath.Offline) {
                    _path.value = if (useRelay) ConnectionPath.Relay else ConnectionPath.Lan
                    _status.value = if (useRelay) "Connected (Relay)" else "Connected (LAN)"
                }
                delay(5_000)
                continue
            }

            if (connecting) {
                delay(500)
                continue
            }

            val lanPeer = nsd.peers.value.firstOrNull { it.deviceId == paired.peerDeviceId }
                ?: nsd.peers.value.firstOrNull()
            if (lanPeer != null) {
                val url = "ws://${lanPeer.host}:${lanPeer.port}/"
                _status.value = "Connecting LAN ${lanPeer.host}:${lanPeer.port}"
                openLan(url, force = false)
                waitForOpen(6_000)
                if (session?.isOpen() == true) {
                    useRelay = false
                    _path.value = ConnectionPath.Lan
                    _status.value = "Connected (LAN)"
                    backoffMs = 2_000L
                    continue
                }
            }

            if (!settings.isLocalOnly()) {
                _status.value = "LAN unavailable — trying relay"
                openRelay()
                waitForOpen(6_000)
                if (session?.isOpen() == true) {
                    useRelay = true
                    _path.value = ConnectionPath.Relay
                    _status.value = "Connected (Relay)"
                    backoffMs = 2_000L
                    continue
                }
            }

            _path.value = ConnectionPath.Offline
            _status.value = "Offline — retrying"
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
        }
    }

    private suspend fun waitForOpen(timeoutMs: Long) {
        val steps = (timeoutMs / 200).toInt().coerceAtLeast(1)
        repeat(steps) {
            if (session?.isOpen() == true) return
            delay(200)
        }
    }

    private fun openLan(url: String, force: Boolean) {
        if (!force && session?.isOpen() == true && currentUrl == url) return
        if (!force && connecting && currentUrl == url) return
        useRelay = false
        replaceSession(url) { it.connect(url) }
    }

    private suspend fun openRelay() {
        useRelay = true
        val url = settings.relayUrl()
        replaceSession(url) { it.connect(url) }
        delay(400)
        if (session?.isOpen() != true) return
        val key = crypto.sessionKeyBytes() ?: return
        sendPlain(
            Envelope(
                type = MessageTypes.RELAY_REGISTER,
                payload = buildJsonObject {
                    put("deviceId", identity.deviceId)
                    put("authToken", CryptoEngine.relayAuthToken(key))
                }
            )
        )
    }

    private fun replaceSession(url: String, connect: (WebSocketSession) -> Unit) {
        connecting = true
        currentUrl = url
        // Drop collectors for the previous socket so a late "closed" can't mark us offline
        sessionJobs.forEach { it.cancel() }
        sessionJobs.clear()
        session?.close()
        val ws = WebSocketSession(scope, crypto)
        session = ws
        listen(ws)
        connect(ws)
    }

    private fun listen(ws: WebSocketSession) {
        val generation = ws
        sessionJobs += scope.launch {
            ws.incoming.collect { env -> handleIncoming(env) }
        }
        sessionJobs += scope.launch {
            ws.connectionState.collect { up ->
                // Ignore events from a socket that is no longer current
                if (session !== generation) return@collect
                if (up) {
                    connecting = false
                    _path.value = if (useRelay) ConnectionPath.Relay else ConnectionPath.Lan
                    _status.value = if (useRelay) "Connected (Relay)" else "Connected (LAN)"
                } else if (!connecting) {
                    _path.value = ConnectionPath.Offline
                    if (_status.value.startsWith("Connected")) {
                        _status.value = "Disconnected — reconnecting"
                    }
                }
            }
        }
    }

    private fun wireLocalEmitters() {
        jobs += scope.launch {
            clipboard.localChanges.collect { text ->
                _lastClipboard.value = text.take(200)
                sendApp(
                    Envelope(
                        type = MessageTypes.CLIPBOARD_CHANGED,
                        payload = buildJsonObject {
                            put("text", text)
                            put("originDeviceId", identity.deviceId)
                            put("ts", System.currentTimeMillis())
                        }
                    )
                )
            }
        }
        jobs += scope.launch {
            SyncBus.notifPosted.collect { n ->
                if (SyncBus.quietHours) return@collect
                sendApp(Envelope(MessageTypes.NOTIFICATION_POSTED, payload = SyncBus.notifPayload(n)))
            }
        }
        jobs += scope.launch {
            SyncBus.notifRemoved.collect { key ->
                sendApp(
                    Envelope(
                        MessageTypes.NOTIFICATION_REMOVED,
                        payload = buildJsonObject { put("key", key) }
                    )
                )
            }
        }
        jobs += scope.launch {
            SyncBus.smsIncoming.collect { m ->
                sendApp(
                    Envelope(
                        MessageTypes.SMS_RECEIVED,
                        payload = buildJsonObject {
                            put("id", "live-${m.date}")
                            put("threadId", "")
                            put("address", m.address)
                            put("body", m.body)
                            put("date", m.date)
                            put("type", "inbox")
                        }
                    )
                )
            }
        }
        jobs += scope.launch {
            calls.state.collect { st ->
                sendApp(Envelope(MessageTypes.CALL_STATE, payload = st.toJson()))
            }
        }
        jobs += scope.launch {
            sms.changes.collect {
                // Mac can re-request threads; optionally push a lightweight ping
            }
        }
    }

    private fun handleIncoming(env: Envelope) {
        when (env.type) {
            MessageTypes.PAIR_COMPLETE -> {
                _status.value = "Pairing complete"
                _path.value = if (useRelay) ConnectionPath.Relay else ConnectionPath.Lan
            }
            MessageTypes.PING -> sendPlain(Envelope(MessageTypes.PONG, id = env.id))
            MessageTypes.CLIPBOARD_SET, MessageTypes.CLIPBOARD_CHANGED -> {
                val text = env.payload.str("text")
                val origin = env.payload.str("originDeviceId")
                if (origin != identity.deviceId && text.isNotEmpty()) {
                    clipboard.applyRemote(text)
                    _lastClipboard.value = text.take(200)
                }
            }
            MessageTypes.NOTIFICATION_ACTION -> {
                SyncBus.notificationListener?.performAction(
                    env.payload.str("key"),
                    env.payload.str("actionId"),
                    env.payload["replyText"]?.let { env.payload.str("replyText") }
                )
            }
            MessageTypes.SMS_THREADS_REQUEST -> {
                val threads = sms.threads()
                sendApp(
                    Envelope(
                        MessageTypes.SMS_THREADS,
                        payload = buildJsonObject {
                            put("threads", buildJsonArray { threads.forEach { add(it.toJson()) } })
                        }
                    )
                )
            }
            MessageTypes.SMS_MESSAGES_REQUEST -> {
                val threadId = env.payload.str("threadId")
                val messages = sms.messages(threadId)
                sendApp(
                    Envelope(
                        MessageTypes.SMS_MESSAGES,
                        payload = buildJsonObject {
                            put("threadId", threadId)
                            put("messages", buildJsonArray { messages.forEach { add(it.toJson()) } })
                        }
                    )
                )
            }
            MessageTypes.SMS_SEND -> {
                val address = env.payload.str("address")
                val body = env.payload.str("body")
                runCatching { sms.send(address, body) }
            }
            MessageTypes.CALL_HISTORY_REQUEST -> {
                val entries = calls.history()
                sendApp(
                    Envelope(
                        MessageTypes.CALL_HISTORY,
                        payload = buildJsonObject {
                            put("entries", buildJsonArray { entries.forEach { add(it.toJson()) } })
                        }
                    )
                )
            }
            MessageTypes.CALL_DIAL -> calls.dial(env.payload.str("number"))
            MessageTypes.CALL_ANSWER -> calls.answer()
            MessageTypes.CALL_REJECT -> calls.reject()

            MessageTypes.FILE_OFFER, MessageTypes.FILE_ACCEPT, MessageTypes.FILE_CHUNK,
            MessageTypes.FILE_COMPLETE, MessageTypes.FILE_CANCEL -> {
                if (::files.isInitialized) files.handle(env)
            }
            MessageTypes.MEDIA_COMMAND -> {
                media.handleCommand(env)
                sendApp(media.currentStateEnvelope())
            }
            MessageTypes.FIND_RING -> {
                findPhone.startRing()
                _findRinging.value = true
            }
            MessageTypes.FIND_STOP -> {
                findPhone.stopRing()
                _findRinging.value = false
            }
            MessageTypes.LINK_OPEN -> {
                val url = env.payload.str("url")
                if (url.isNotEmpty()) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
            MessageTypes.WIFI_REQUEST -> sendApp(wifi.infoEnvelope())
            MessageTypes.PHOTOS_LIST_REQUEST -> {
                val limit = env.payload.str("limit").toIntOrNull()?.coerceIn(1, 100) ?: 100
                sendApp(photos.listRecent(limit))
            }
            MessageTypes.PHOTOS_GET -> {
                val id = env.payload.str("id")
                val path = photos.resolvePath(id)
                if (path != null && ::files.isInitialized) {
                    files.offerFile(path, "image/jpeg")
                }
            }
            MessageTypes.PREFS_QUIET -> {
                SyncBus.quietHours = env.payload.bool("enabled")
            }
        }
    }

    private fun sendPlain(env: Envelope) {
        session?.sendPlain(env)
    }

    private fun sendApp(env: Envelope) {
        val ws = session ?: return
        if (!ws.isOpen()) return
        if (useRelay) {
            val peer = peerDeviceId ?: return
            ws.sendViaRelay(peer, identity.deviceId, env)
        } else {
            ws.sendEncrypted(env)
        }
    }

    /** Binary transfers stay on LAN only. */
    private fun sendAppLanOnly(env: Envelope) {
        if (useRelay) return
        sendApp(env)
    }

    fun discoveredPeers() = nsd.peers

    private val emptyTransfers = MutableStateFlow(emptyList<com.marnock.app.transfer.TransferProgress>())
    fun transferProgress() = if (::files.isInitialized) files.progress else emptyTransfers

    fun sendFileToPeer(path: String, mime: String = "application/octet-stream") {
        if (::files.isInitialized) files.offerFile(path, mime)
    }

    fun openLinkOnPeer(url: String) {
        if (!::identity.isInitialized) return
        sendApp(
            Envelope(
                MessageTypes.LINK_OPEN,
                payload = buildJsonObject {
                    put("url", url)
                    put("originDeviceId", identity.deviceId)
                }
            )
        )
    }

    fun stopFindRing() {
        findPhone.stopRing()
        _findRinging.value = false
        sendApp(Envelope(MessageTypes.FIND_STOP))
    }

    companion object {
        private const val TAG = "SyncAgent"
    }
}
