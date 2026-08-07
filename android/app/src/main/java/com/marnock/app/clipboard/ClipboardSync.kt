package com.marnock.app.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Watches local clipboard and applies remote updates with loop prevention.
 */
class ClipboardSync(context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var suppressUntil = 0L
    @Volatile private var lastApplied = ""
    @Volatile private var enabled = false

    private val _localChanges = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val localChanges: SharedFlow<String> = _localChanges

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!enabled) return@OnPrimaryClipChangedListener
        if (System.currentTimeMillis() < suppressUntil) return@OnPrimaryClipChangedListener
        val text = currentText() ?: return@OnPrimaryClipChangedListener
        if (text == lastApplied) return@OnPrimaryClipChangedListener
        lastApplied = text
        _localChanges.tryEmit(text)
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (value) {
            clipboard.addPrimaryClipChangedListener(listener)
        } else {
            clipboard.removePrimaryClipChangedListener(listener)
        }
    }

    fun currentText(): String? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(null)?.toString()
    }

    fun applyRemote(text: String) {
        if (!enabled) return
        if (text == lastApplied || text == currentText()) {
            lastApplied = text
            return
        }
        suppressUntil = System.currentTimeMillis() + 750
        lastApplied = text
        main.post {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Marnock", text))
        }
    }

    fun dispose() {
        clipboard.removePrimaryClipChangedListener(listener)
    }
}
