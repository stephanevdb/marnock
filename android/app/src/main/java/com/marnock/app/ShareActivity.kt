package com.marnock.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.marnock.app.transfer.MimeExtensions
import java.io.File

/**
 * Android share target — sends files/text/URLs to the paired Mac over Marnock.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MarnockApp
        app.ensureAgent()
        when (intent?.action) {
            Intent.ACTION_SEND -> handleSend(app, intent)
            Intent.ACTION_SEND_MULTIPLE -> handleSendMultiple(app, intent)
            else -> finish()
        }
    }

    private fun handleSend(app: MarnockApp, intent: Intent) {
        val type = intent.type.orEmpty()
        if (type.startsWith("text/") || intent.hasExtra(Intent.EXTRA_TEXT)) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (text.startsWith("http://") || text.startsWith("https://")) {
                app.agent.openLinkOnPeer(text)
                toast("Opening link on Mac")
            } else {
                val f = File(cacheDir, "shared-text.txt")
                f.writeText(text)
                app.agent.sendFileToPeer(f.absolutePath, "text/plain")
                toast("Sending text to Mac")
            }
        } else {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                copyAndSend(app, uri, type)
            }
        }
        finish()
    }

    private fun handleSendMultiple(app: MarnockApp, intent: Intent) {
        @Suppress("DEPRECATION")
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        uris.forEach { copyAndSend(app, it, intent.type ?: "application/octet-stream") }
        toast("Sending ${uris.size} file(s) to Mac")
        finish()
    }

    private fun copyAndSend(app: MarnockApp, uri: Uri, mime: String) {
        val resolvedMime = mime.ifBlank { contentResolver.getType(uri).orEmpty() }
        val name = uniqueCacheName(displayName(uri, resolvedMime))
        val out = File(cacheDir, name)
        contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        if (out.exists()) {
            app.agent.sendFileToPeer(
                out.absolutePath,
                resolvedMime.ifBlank { "application/octet-stream" }
            )
        }
    }

    private fun displayName(uri: Uri, mime: String): String {
        var name: String? = null
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx)
                }
            }
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }
        return MimeExtensions.ensureExtension(name?.substringAfterLast('/') ?: "shared", mime)
    }

    private fun uniqueCacheName(name: String): String {
        val dest = File(cacheDir, name)
        if (!dest.exists()) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        return "$stem-${System.currentTimeMillis()}$ext"
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

}
