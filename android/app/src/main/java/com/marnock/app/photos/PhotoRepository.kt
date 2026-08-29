package com.marnock.app.photos

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import com.marnock.app.protocol.Envelope
import com.marnock.app.protocol.MessageTypes
import com.marnock.app.protocol.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

data class PhotoItem(val id: Long, val date: Long, val name: String, val pathOrUri: String)

class PhotoRepository(private val context: Context) {
    fun listRecent(limit: Int = 100): Envelope {
        val items = query(limit)
        return Envelope(
            MessageTypes.PHOTOS_LIST,
            payload = buildJsonObject {
                put("photos", buildJsonArray {
                    items.forEach { p ->
                        add(
                            buildJsonObject {
                                put("id", p.id.toString())
                                put("date", p.date)
                                put("name", p.name)
                                put("thumb", thumbB64(p.id))
                            }
                        )
                    }
                })
            }
        )
    }

    fun resolvePath(id: String): String? {
        val longId = id.toLongOrNull() ?: return null
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, longId)
        return if (Build.VERSION.SDK_INT >= 29) {
            // Copy to cache for transfer
            val name = "photo-$longId.jpg"
            val out = java.io.File(context.cacheDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            if (out.exists()) out.absolutePath else null
        } else {
            queryById(longId)
        }
    }

    private fun queryById(longId: Long): String? {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA
        )
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media._ID}=?",
            arrayOf(longId.toString()),
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                val dataCol = c.getColumnIndex(MediaStore.Images.Media.DATA)
                if (dataCol >= 0) {
                    val path = c.getString(dataCol)
                    if (!path.isNullOrEmpty()) return path
                }
            }
        }
        return null
    }

    private fun query(limit: Int): List<PhotoItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATA
        )
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val out = mutableListOf<PhotoItem>()
        context.contentResolver.query(collection, projection, null, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dataCol = c.getColumnIndex(MediaStore.Images.Media.DATA)
            while (c.moveToNext() && out.size < limit) {
                val id = c.getLong(idCol)
                out += PhotoItem(
                    id = id,
                    date = c.getLong(dateCol) * 1000,
                    name = c.getString(nameCol) ?: "photo-$id.jpg",
                    pathOrUri = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                )
            }
        }
        return out
    }

    private fun thumbB64(id: Long): String {
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val full = BitmapFactory.decodeStream(input) ?: return ""
                val w = 160
                val h = (full.height * w) / full.width.coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(full, w, h.coerceAtLeast(1), true)
                full.recycle()
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                scaled.recycle()
                val bytes = baos.toByteArray()
                if (bytes.size > 32_000) {
                    // already small enough usually
                }
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
