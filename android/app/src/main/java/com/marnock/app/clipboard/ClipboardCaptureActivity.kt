package com.marnock.app.clipboard

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

/**
 * Briefly takes window focus so Android 10+ allows reading [ClipboardManager.primaryClip]
 * from the background (the clip-changed listener alone often gets null).
 */
class ClipboardCaptureActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        main.postDelayed({ finishCapture(null) }, 800)
    }

    override fun onResume() {
        super.onResume()
        tryRead()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finished = false
        tryRead()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) tryRead()
    }

    private fun tryRead() {
        if (finished) return
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = try {
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this)?.toString()
            } else null
        } catch (_: SecurityException) {
            null
        }
        val value = text?.takeIf { it.isNotBlank() } ?: return
        finishCapture(value)
    }

    private fun finishCapture(text: String?) {
        if (finished) return
        finished = true
        main.removeCallbacksAndMessages(null)
        if (!text.isNullOrEmpty()) {
            ClipboardSync.emitCaptured(text)
        } else {
            ClipboardSync.onCaptureFailed()
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        /** @return false if [startActivity] threw (BAL / security). BAL may also fail silently. */
        fun start(context: Context): Boolean {
            val i = Intent(context, ClipboardCaptureActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            return try {
                context.startActivity(i)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
