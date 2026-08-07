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
        // Empty content; we only need window focus.
        main.postDelayed({ finishCapture(null) }, 1_200)
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
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
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
