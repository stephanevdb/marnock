package com.marnock.app.find

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class FindPhoneController(private val context: Context) {
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringtone = RingtoneManager.getRingtone(
        context,
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    )
    private var savedVolume = -1
    @Volatile var ringing = false
        private set

    fun startRing() {
        if (ringing) return
        ringing = true
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        savedVolume = audio.getStreamVolume(AudioManager.STREAM_RING)
        audio.setStreamVolume(
            AudioManager.STREAM_RING,
            audio.getStreamMaxVolume(AudioManager.STREAM_RING),
            0
        )
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "marnock:find"
        ).also { it.acquire(60_000) }

        context.startActivity(
            Intent(context, FindRingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        ringtone?.play()
        vibrate(true)
    }

    fun stopRing() {
        ringing = false
        ringtone?.stop()
        vibrate(false)
        if (savedVolume >= 0) {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.setStreamVolume(AudioManager.STREAM_RING, savedVolume, 0)
            savedVolume = -1
        }
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun vibrate(on: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(VibratorManager::class.java)
                val v = vm.defaultVibrator
                if (on) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
                else v.cancel()
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (on) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(longArrayOf(0, 500, 200, 500), 0)
                    }
                } else v.cancel()
            }
        } catch (_: Exception) {
        }
    }
}
