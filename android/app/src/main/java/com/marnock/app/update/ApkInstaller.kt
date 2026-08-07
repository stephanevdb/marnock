package com.marnock.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class ApkInstaller(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val _progress = MutableStateFlow(-1f)
    val progress: StateFlow<Float> = _progress

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    suspend fun downloadAndPromptInstall(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _progress.value = 0f
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Marnock-Android")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Download failed: HTTP ${resp.code}"))
                }
                val body = resp.body ?: return@withContext Result.failure(IllegalStateException("Empty body"))
                val total = body.contentLength().coerceAtLeast(1L)
                val out = File(context.cacheDir, "Marnock-update.apk")
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            done += n
                            _progress.value = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                }
                _progress.value = 1f
                withContext(Dispatchers.Main) {
                    promptInstall(out)
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            _progress.value = -1f
            Result.failure(e)
        }
    }

    private fun promptInstall(apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
