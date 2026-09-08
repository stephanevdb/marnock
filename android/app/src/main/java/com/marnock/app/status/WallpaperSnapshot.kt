package com.marnock.app.status

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import com.marnock.app.protocol.Envelope
import com.marnock.app.protocol.MessageTypes
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import kotlin.math.max

class WallpaperSnapshot(private val context: Context) {
    private var lastSentId: Int = Int.MIN_VALUE
    private var pendingId: Int? = null

    fun invalidate() {
        lastSentId = Int.MIN_VALUE
    }

    fun pendingEnvelope(): Envelope? {
        val snap = capture() ?: return null
        if (snap.id == lastSentId) return null
        pendingId = snap.id
        return Envelope(
            MessageTypes.DEVICE_WALLPAPER,
            payload = buildJsonObject {
                put("id", snap.id)
                put("thumb", snap.thumb)
            }
        )
    }

    fun markSent() {
        lastSentId = pendingId ?: lastSentId
        pendingId = null
    }

    fun currentId(): Int = try {
        WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
    } catch (_: Throwable) {
        0
    }

    private data class Snap(val id: Int, val thumb: String)

    private fun capture(): Snap? {
        return try {
            val wm = WallpaperManager.getInstance(context)
            @Suppress("DEPRECATION")
            val drawable = wm.peekDrawable() ?: return null
            val tile = renderTile(drawable) ?: return null
            val baos = ByteArrayOutputStream()
            val ok = tile.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            tile.recycle()
            if (!ok) return null
            val bytes = baos.toByteArray()
            if (bytes.isEmpty()) return null
            val wmId = try {
                wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            } catch (_: Throwable) {
                0
            }
            val id = if (wmId > 0) wmId else bytes.contentHashCode()
            Snap(id, Base64.encodeToString(bytes, Base64.NO_WRAP))
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Draw into a tiny software bitmap. Never copy the wallpaper Bitmap: it is often
     * HARDWARE-config and Bitmap.createBitmap(src, …) aborts the process.
     */
    private fun renderTile(drawable: Drawable): Bitmap? {
        val tile = Bitmap.createBitmap(TILE_WIDTH, TILE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tile)
        val dw = drawable.intrinsicWidth.takeIf { it > 0 } ?: TILE_WIDTH
        val dh = drawable.intrinsicHeight.takeIf { it > 0 } ?: TILE_HEIGHT
        val scale = max(TILE_WIDTH.toFloat() / dw, TILE_HEIGHT.toFloat() / dh)
        val sw = (dw * scale).toInt().coerceAtLeast(1)
        val sh = (dh * scale).toInt().coerceAtLeast(1)
        val left = (TILE_WIDTH - sw) / 2
        val top = (TILE_HEIGHT - sh) / 2
        drawable.setBounds(left, top, left + sw, top + sh)
        drawable.draw(canvas)
        return tile
    }

    companion object {
        private const val TILE_WIDTH = 80
        private const val TILE_HEIGHT = 110
    }
}
