package com.marnock.app.clipboard

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/** KDE-style Quick Settings tile: user tap is a BAL-allowed start of the capture activity. */
class ClipboardTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(ClipboardCaptureActivity.pendingIntent(this, REQUEST_CODE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(ClipboardCaptureActivity.launchIntent(this).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    companion object {
        private const val REQUEST_CODE = 71003
    }
}
