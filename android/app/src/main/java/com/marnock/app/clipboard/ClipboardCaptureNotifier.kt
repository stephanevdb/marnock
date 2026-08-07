package com.marnock.app.clipboard

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.marnock.app.R

/** Tap-to-sync fallback when background activity starts are blocked (BAL). */
object ClipboardCaptureNotifier {
    private const val CHANNEL_ID = "marnock_clipboard"
    private const val NOTIF_ID = 71002

    fun notify(context: Context) {
        val app = context.applicationContext
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Clipboard sync",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Tap to send copied text to your Mac when silent capture is blocked"
                }
            )
        }

        val launch = Intent(app, ClipboardCaptureActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = if (Build.VERSION.SDK_INT >= 34) {
            val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            PendingIntent.getActivity(app, NOTIF_ID, launch, flags, options.toBundle())
        } else {
            PendingIntent.getActivity(app, NOTIF_ID, launch, flags)
        }

        val notif = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Send clipboard to Mac")
            .setContentText("Tap to sync the text you just copied")
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .build()
        nm.notify(NOTIF_ID, notif)
    }

    fun cancel(context: Context) {
        val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
    }
}
