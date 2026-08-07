package com.marnock.app.update

import com.marnock.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdate(
    val version: String,
    val downloadUrl: String,
    val notes: String,
    val htmlUrl: String
)

class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val repo: String = BuildConfig.UPDATE_REPO,
    private val assetName: String = BuildConfig.UPDATE_ASSET_ANDROID,
    private val currentVersion: String = BuildConfig.VERSION_NAME
) {
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Marnock-Android")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            if (!SemVer.isNewer(tag, currentVersion)) return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var url = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == assetName) {
                    url = a.optString("browser_download_url")
                    break
                }
            }
            if (url.isEmpty()) return@withContext null
            AppUpdate(
                version = tag,
                downloadUrl = url,
                notes = json.optString("body").orEmpty(),
                htmlUrl = json.optString("html_url").orEmpty()
            )
        }
    }
}
