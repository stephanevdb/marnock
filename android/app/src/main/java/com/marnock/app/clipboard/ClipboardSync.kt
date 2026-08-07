package com.marnock.app.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Watches local clipboard and applies remote updates with loop prevention.
 * On Android 10+, background reads return null — we briefly open
 * [ClipboardCaptureActivity] to obtain focus and re-read.
 */
class ClipboardSync(private val context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var suppressUntil = 0L
    @Volatile private var lastApplied = ""
    @Volatile private var enabled = false
    @Volatile private var listenerRegistered = false
    @Volatile private var lastCaptureAt = 0L

    private val _localChanges = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val localChanges: SharedFlow<String> = _localChanges

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!enabled) return@OnPrimaryClipChangedListener
        if (System.currentTimeMillis() < suppressUntil) return@OnPrimaryClipChangedListener
        val text = currentText()
        if (text != null) {
            emitLocal(text)
            return@OnPrimaryClipChangedListener
        }
        // Background: Android strips primaryClip — capture with a focused activity.
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureAt < 1_200L) return@OnPrimaryClipChangedListener
        lastCaptureAt = now
        main.post { ClipboardCaptureActivity.start(context.applicationContext) }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (value) {
            if (!listenerRegistered) {
                clipboard.addPrimaryClipChangedListener(listener)
                listenerRegistered = true
            }
        } else if (listenerRegistered) {
            clipboard.removePrimaryClipChangedListener(listener)
            listenerRegistered = false
        }
    }

    fun currentText(): String? {
        return try {
            val clip = clipboard.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            null
        }
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
        if (listenerRegistered) {
            clipboard.removePrimaryClipChangedListener(listener)
            listenerRegistered = false
        }
        active = null
    }

    private fun emitLocal(text: String) {
        if (text == lastApplied) return
        lastApplied = text
        _localChanges.tryEmit(text)
    }

    companion object {
        @Volatile
        private var active: ClipboardSync? = null

        fun bind(sync: ClipboardSync) {
            active = sync
        }

        /** Called from [ClipboardCaptureActivity] after a focused read. */
        fun emitCaptured(text: String) {
            val sync = active ?: return
            if (!sync.enabled) return
            if (System.currentTimeMillis() < sync.suppressUntil) return
            sync.emitLocal(text)
        }
    }

    init {
        bind(this)
    }
}
