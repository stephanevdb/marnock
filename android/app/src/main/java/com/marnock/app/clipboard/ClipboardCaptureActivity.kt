package com.marnock.app.clipboard

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
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
    private var retries = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        main.postDelayed({ finishCapture(null) }, FAIL_AFTER_MS)
    }

    override fun onResume() {
        super.onResume()
        tryRead()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (finished) return
        retries = 0
        tryRead()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            retries = 0
            tryRead()
        }
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
        val value = text?.takeIf { it.isNotBlank() }
        if (value != null) {
            finishCapture(value)
            return
        }
        if (hasWindowFocus() && retries < MAX_RETRIES) {
            retries++
            main.postDelayed({ tryRead() }, RETRY_MS)
        }
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
        private const val FAIL_AFTER_MS = 2_000L
        private const val RETRY_MS = 50L
        private const val MAX_RETRIES = 10

        fun launchIntent(context: Context): Intent =
            Intent(context, ClipboardCaptureActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }

        fun pendingIntent(context: Context, requestCode: Int): PendingIntent {
            val app = context.applicationContext
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val launch = launchIntent(app)
            return if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                PendingIntent.getActivity(app, requestCode, launch, flags, options.toBundle())
            } else {
                PendingIntent.getActivity(app, requestCode, launch, flags)
            }
        }

        /** @return false if [startActivity] threw (BAL / security). BAL may also fail silently. */
        fun start(context: Context): Boolean {
            val i = launchIntent(context)
            return try {
                if (Build.VERSION.SDK_INT >= 34) {
                    val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    context.startActivity(i, options.toBundle())
                } else {
                    context.startActivity(i)
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
