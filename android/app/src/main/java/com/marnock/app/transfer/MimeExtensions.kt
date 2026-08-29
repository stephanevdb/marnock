package com.marnock.app.transfer

import android.webkit.MimeTypeMap

object MimeExtensions {
    fun ensureExtension(name: String, mime: String): String {
        val cleaned = name.trim().ifBlank { "shared" }
        if (cleaned.substringAfterLast('/', cleaned).contains('.')) return cleaned
        val ext = extensionForMime(mime) ?: return cleaned
        return "$cleaned.$ext"
    }

    fun extensionForMime(mime: String): String? {
        val type = mime.substringBefore(';').trim().lowercase()
        return when (type) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/avif" -> "avif"
            "image/bmp" -> "bmp"
            "video/mp4" -> "mp4"
            "video/quicktime" -> "mov"
            "audio/mpeg" -> "mp3"
            "text/plain" -> "txt"
            "application/pdf" -> "pdf"
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(type)?.takeIf { it.isNotBlank() }
        }
    }
}
