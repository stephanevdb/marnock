package com.marnock.app.status

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
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
    } catch (_: Exception) {
        0
    }

    private data class Snap(val id: Int, val thumb: String)

    private fun capture(): Snap? {
        return try {
            val wm = WallpaperManager.getInstance(context)
            @Suppress("DEPRECATION")
            val drawable = wm.peekDrawable() ?: wm.drawable ?: return null
            val src = drawableToBitmap(drawable) ?: return null
            val tile = cropToTile(src, TILE_WIDTH, TILE_HEIGHT)
            if (src !== tile && src !== (drawable as? BitmapDrawable)?.bitmap) {
                src.recycle()
            }
            val baos = ByteArrayOutputStream()
            tile.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            if (tile !== (drawable as? BitmapDrawable)?.bitmap) {
                tile.recycle()
            }
            val bytes = baos.toByteArray()
            if (bytes.isEmpty()) return null
            val wmId = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            val id = if (wmId > 0) wmId else bytes.contentHashCode()
            Snap(id, Base64.encodeToString(bytes, Base64.NO_WRAP))
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            return drawable.bitmap
        }
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun cropToTile(src: Bitmap, tw: Int, th: Int): Bitmap {
        val scale = max(tw.toFloat() / src.width, th.toFloat() / src.height)
        val sw = (tw / scale).toInt().coerceAtLeast(1).coerceAtMost(src.width)
        val sh = (th / scale).toInt().coerceAtLeast(1).coerceAtMost(src.height)
        val x = ((src.width - sw) / 2).coerceAtLeast(0)
        val y = ((src.height - sh) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(src, x, y, sw, sh)
        if (cropped.width == tw && cropped.height == th) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, tw, th, true)
        if (cropped !== src && cropped !== scaled) cropped.recycle()
        return scaled
    }

    companion object {
        private const val TILE_WIDTH = 80
        private const val TILE_HEIGHT = 110
    }
}
