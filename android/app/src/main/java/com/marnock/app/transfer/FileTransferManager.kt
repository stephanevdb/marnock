package com.marnock.app.transfer

import android.content.Context
import android.os.Environment
import android.util.Base64
import com.marnock.app.protocol.Envelope
import com.marnock.app.protocol.MessageTypes
import com.marnock.app.protocol.bool
import com.marnock.app.protocol.long
import com.marnock.app.protocol.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TransferProgress(
    val id: String,
    val name: String,
    val direction: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val status: String
)

class FileTransferManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val send: (Envelope) -> Unit
) {
    private val incoming = ConcurrentHashMap<String, Incoming>()
    private val pendingIn = ConcurrentHashMap<String, PendingOffer>()
    private val pendingOut = ConcurrentHashMap<String, File>()
    private val _progress = MutableStateFlow<List<TransferProgress>>(emptyList())
    val progress: StateFlow<List<TransferProgress>> = _progress

    fun offerFile(path: String, mime: String = "application/octet-stream") {
        scope.launch(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists()) return@launch
            val id = UUID.randomUUID().toString()
            val sha = sha256(file)
            upsert(TransferProgress(id, file.name, "out", 0, file.length(), "offering"))
            pendingOut[id] = file
            send(
                Envelope(
                    MessageTypes.FILE_OFFER,
                    payload = buildJsonObject {
                        put("transferId", id)
                        put("name", file.name)
                        put("size", file.length())
                        put("mime", mime)
                        put("sha256", sha)
                    }
                )
            )
        }
    }

    fun acceptIncoming(id: String) {
        val offer = pendingIn.remove(id) ?: return
        val dest = File(downloadsDir(), sanitize(offer.name))
        incoming[id] = Incoming(dest, offer.size, offer.sha, FileOutputStream(dest))
        upsert(TransferProgress(id, dest.name, "in", 0, offer.size, "receiving"))
        send(
            Envelope(
                MessageTypes.FILE_ACCEPT,
                payload = buildJsonObject {
                    put("transferId", id)
                    put("ok", true)
                }
            )
        )
    }

    fun rejectIncoming(id: String) {
        val offer = pendingIn.remove(id) ?: return
        upsert(TransferProgress(id, offer.name, "in", 0, offer.size, "rejected"))
        send(
            Envelope(
                MessageTypes.FILE_ACCEPT,
                payload = buildJsonObject {
                    put("transferId", id)
                    put("ok", false)
                }
            )
        )
    }

    fun handle(env: Envelope) {
        when (env.type) {
            MessageTypes.FILE_OFFER -> {
                val id = env.payload.str("transferId")
                val name = env.payload.str("name")
                val size = env.payload.long("size")
                val sha = env.payload.str("sha256")
                pendingIn[id] = PendingOffer(sanitize(name), size, sha)
                upsert(TransferProgress(id, sanitize(name), "in", 0, size, "awaiting"))
            }
            MessageTypes.FILE_ACCEPT -> {
                val id = env.payload.str("transferId")
                val ok = env.payload.bool("ok")
                val file = pendingOut.remove(id) ?: return
                if (!ok) {
                    upsert(TransferProgress(id, file.name, "out", 0, file.length(), "rejected"))
                    return
                }
                scope.launch(Dispatchers.IO) { sendChunks(id, file) }
            }
            MessageTypes.FILE_CHUNK -> {
                val id = env.payload.str("transferId")
                val offset = env.payload.long("offset")
                val dataB64 = env.payload.str("data")
                val bytes = Base64.decode(dataB64, Base64.DEFAULT)
                val inc = incoming[id] ?: return
                synchronized(inc) {
                    inc.out.write(bytes)
                    inc.done = offset + bytes.size
                }
                upsert(
                    TransferProgress(
                        id, inc.file.name, "in", inc.done, inc.total,
                        if (inc.done >= inc.total) "finishing" else "receiving"
                    )
                )
            }
            MessageTypes.FILE_COMPLETE -> {
                val id = env.payload.str("transferId")
                val inc = incoming.remove(id) ?: return
                synchronized(inc) {
                    inc.out.flush()
                    inc.out.close()
                }
                upsert(TransferProgress(id, inc.file.name, "in", inc.total, inc.total, "complete"))
            }
            MessageTypes.FILE_CANCEL -> {
                val id = env.payload.str("transferId")
                incoming.remove(id)?.out?.close()
                pendingOut.remove(id)
                pendingIn.remove(id)
                upsert(TransferProgress(id, id, "?", 0, 0, "cancelled"))
            }
        }
    }

    private suspend fun sendChunks(id: String, file: File) = withContext(Dispatchers.IO) {
        FileInputStream(file).use { input ->
            val buf = ByteArray(CHUNK)
            var offset = 0L
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                val slice = if (n == buf.size) buf else buf.copyOf(n)
                send(
                    Envelope(
                        MessageTypes.FILE_CHUNK,
                        payload = buildJsonObject {
                            put("transferId", id)
                            put("offset", offset)
                            put("data", Base64.encodeToString(slice, Base64.NO_WRAP))
                        }
                    )
                )
                offset += n
                upsert(TransferProgress(id, file.name, "out", offset, file.length(), "sending"))
                delay(5)
            }
        }
        send(
            Envelope(
                MessageTypes.FILE_COMPLETE,
                payload = buildJsonObject {
                    put("transferId", id)
                    put("ok", true)
                }
            )
        )
        upsert(TransferProgress(id, file.name, "out", file.length(), file.length(), "complete"))
    }

    private fun downloadsDir(): File {
        val public = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Marnock"
        )
        if (public.exists() || public.mkdirs()) return public
        val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Marnock")
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
    }

    fun cancel(id: String) {
        incoming.remove(id)?.out?.close()
        pendingOut.remove(id)
        pendingIn.remove(id)
        send(
            Envelope(
                MessageTypes.FILE_CANCEL,
                payload = buildJsonObject { put("transferId", id) }
            )
        )
        upsert(TransferProgress(id, id, "?", 0, 0, "cancelled"))
    }

    private fun upsert(p: TransferProgress) {
        _progress.value = (_progress.value.filterNot { it.id == p.id } + p).takeLast(20)
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private data class PendingOffer(val name: String, val size: Long, val sha: String)

    private data class Incoming(
        val file: File,
        val total: Long,
        val sha: String,
        val out: FileOutputStream,
        var done: Long = 0
    )

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "file.bin" }

    companion object {
        private const val CHUNK = 48 * 1024
    }
}
