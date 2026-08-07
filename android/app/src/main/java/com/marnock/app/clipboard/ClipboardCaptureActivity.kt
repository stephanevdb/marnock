package com.marnock.app.clipboard

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Briefly takes window focus so Android 10+ allows reading [ClipboardManager.primaryClip]
 * from the background (the clip-changed listener alone often gets null).
 */
class ClipboardCaptureActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Empty content; we only need window focus.
        main.postDelayed({ finishCapture(null) }, 900)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || finished) return
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = try {
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this)?.toString()
            } else null
        } catch (_: SecurityException) {
            null
        }
        finishCapture(text?.takeIf { it.isNotBlank() })
    }

    private fun finishCapture(text: String?) {
        if (finished) return
        finished = true
        main.removeCallbacksAndMessages(null)
        if (!text.isNullOrEmpty()) {
            ClipboardSync.emitCaptured(text)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        fun start(context: Context) {
            val i = Intent(context, ClipboardCaptureActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            try {
                context.startActivity(i)
            } catch (_: Exception) {
                // Background activity starts may be blocked on some OS versions.
            }
        }
    }
}
