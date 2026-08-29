package com.marnock.app.clipboard

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * BAL exemption so [ClipboardCaptureActivity] can start after a copy and read the clip.
 * Does not scrape the screen or inject input.
 */
class ClipboardAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile
        private var instance: ClipboardAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(context, ClipboardAccessibilityService::class.java)
            val flat = me.flattenToString()
            val shortFlat = me.flattenToShortString()
            return enabled.split(':').any {
                it.equals(flat, ignoreCase = true) || it.equals(shortFlat, ignoreCase = true)
            }
        }

        fun startCapture(): Boolean {
            val svc = instance ?: return false
            return ClipboardCaptureActivity.start(svc)
        }
    }
}
