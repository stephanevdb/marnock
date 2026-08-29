package com.marnock.app.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Watches local clipboard and applies remote updates with loop prevention.
 * On Android 10+, background reads return null — we briefly open
 * [ClipboardCaptureActivity], then fall back to a tap notification if BAL blocks it.
 */
class ClipboardSync(private val context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var suppressUntil = 0L
    @Volatile private var lastApplied = ""
    @Volatile private var enabled = false
    @Volatile private var listenerRegistered = false
    @Volatile private var awaitingCapture = false
    @Volatile private var captureGeneration = 0
    @Volatile private var lastNotifAt = 0L

    private val _localChanges = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val localChanges: SharedFlow<String> = _localChanges

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!enabled) return@OnPrimaryClipChangedListener
        if (System.currentTimeMillis() < suppressUntil) return@OnPrimaryClipChangedListener
        val text = currentText()
        if (text != null) {
            emitLocal(text)
            return@OnPrimaryClipChangedListener
        }
        requestBackgroundCapture()
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (value) {
            if (!listenerRegistered) {
                clipboard.addPrimaryClipChangedListener(listener)
                listenerRegistered = true
            }
            pollIfReadable()
        } else if (listenerRegistered) {
            clipboard.removePrimaryClipChangedListener(listener)
            listenerRegistered = false
            awaitingCapture = false
            ClipboardCaptureNotifier.cancel(context)
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

    /** Call when a Marnock activity has focus — reads without needing capture UI. */
    fun pollIfReadable() {
        if (!enabled) return
        if (System.currentTimeMillis() < suppressUntil) return
        val text = currentText() ?: return
        emitLocal(text)
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
        awaitingCapture = false
        ClipboardCaptureNotifier.cancel(context)
        active = null
    }

    private fun requestBackgroundCapture() {
        val gen = ++captureGeneration
        awaitingCapture = true
        val started = ClipboardCaptureActivity.start(context.applicationContext)
        if (!started) {
            awaitingCapture = false
            offerCaptureNotification()
            return
        }
        // BAL often fails silently (no exception) — fall back if we never emitted.
        main.postDelayed({
            if (awaitingCapture && captureGeneration == gen) {
                awaitingCapture = false
                offerCaptureNotification()
            }
        }, 800)
    }

    private fun offerCaptureNotification() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifAt < 2_500L) return
        lastNotifAt = now
        ClipboardCaptureNotifier.notify(context)
    }

    private fun emitLocal(text: String) {
        if (text == lastApplied) return
        lastApplied = text
        awaitingCapture = false
        ClipboardCaptureNotifier.cancel(context)
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

        /** Capture activity finished without text — allow notification fallback. */
        fun onCaptureFailed() {
            val sync = active ?: return
            if (!sync.awaitingCapture) return
            sync.awaitingCapture = false
            sync.offerCaptureNotification()
        }
    }

    init {
        bind(this)
    }
}
