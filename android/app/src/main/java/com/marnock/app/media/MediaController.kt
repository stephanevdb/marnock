package com.marnock.app.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.marnock.app.notifications.MirrorNotificationListener
import com.marnock.app.protocol.Envelope
import com.marnock.app.protocol.MessageTypes
import com.marnock.app.protocol.str
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MediaControllerHelper(private val context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun handleCommand(env: Envelope) {
        val cmd = env.payload.str("command")
        val ctrl = activeController()
        when (cmd) {
            "play" -> ctrl?.transportControls?.play()
            "pause" -> ctrl?.transportControls?.pause()
            "next" -> ctrl?.transportControls?.skipToNext()
            "previous" -> ctrl?.transportControls?.skipToPrevious()
            "volume" -> {
                val level = env.payload.str("level").toIntOrNull()
                if (level != null) {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val v = (level.coerceIn(0, 100) * max) / 100
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                }
            }
        }
    }

    fun currentStateEnvelope(): Envelope {
        val ctrl = activeController()
        val meta = ctrl?.metadata
        val playing = ctrl?.playbackState?.state == PlaybackState.STATE_PLAYING
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val vol = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100) / max
        return Envelope(
            MessageTypes.MEDIA_STATE,
            payload = buildJsonObject {
                put("title", meta?.description?.title?.toString() ?: "")
                put("artist", meta?.description?.subtitle?.toString() ?: "")
                put("playing", playing)
                put("volume", vol)
            }
        )
    }

    private fun activeController(): MediaController? {
        return try {
            val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(context, MirrorNotificationListener::class.java)
            mgr.getActiveSessions(listener).firstOrNull()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
