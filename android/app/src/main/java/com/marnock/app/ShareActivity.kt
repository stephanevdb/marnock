package com.marnock.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
                // Treat as small text file
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
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "shared.bin"
        val out = File(cacheDir, "share-${System.currentTimeMillis()}-$name")
        contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        if (out.exists()) {
            app.agent.sendFileToPeer(out.absolutePath, mime)
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
