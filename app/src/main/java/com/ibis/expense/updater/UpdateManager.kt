package com.ibis.expense.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String
)

object UpdateManager {
    private const val CDN_UPDATE_JSON = "https://cdn.jsdelivr.net/gh/Ibis919/ExpenseTracker@main/update.json"
    private const val GITHUB_LATEST_API = "https://api.github.com/repos/Ibis919/ExpenseTracker/releases/latest"
    private const val UA = "ExpenseTracker-Android"

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"

    private suspend fun httpGet(url: String, timeoutMs: Int): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", UA)
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun fetchCdnUpdate(): UpdateInfo? {
        val json = JSONObject(httpGet(CDN_UPDATE_JSON, 8000))
        val version = json.optString("version")
        val apkUrl = json.optString("apkUrl")
        if (version.isEmpty() || apkUrl.isEmpty()) return null
        return UpdateInfo(version, json.optString("notes").take(600), apkUrl)
    }

    private suspend fun fetchGithubUpdate(): UpdateInfo? {
        val json = JSONObject(httpGet(GITHUB_LATEST_API, 10_000))
        val tag = json.optString("tag_name").removePrefix("v")
        if (tag.isEmpty()) return null
        val assets = json.optJSONArray("assets") ?: JSONArray()
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                apkUrl = a.optString("browser_download_url")
                break
            }
        }
        if (apkUrl.isEmpty()) return null
        return UpdateInfo(tag, json.optString("body").take(600), apkUrl)
    }

    suspend fun checkLatest(current: String): UpdateInfo? {
        val cdn = runCatching { fetchCdnUpdate() }.getOrNull()
        if (cdn != null) return if (cdn.version == current) null else cdn
        val gh = runCatching { fetchGithubUpdate() }.getOrNull() ?: return null
        return if (gh.version == current) null else gh
    }

    suspend fun downloadApk(context: Context, url: String, onProgress: (Int) -> Unit): File {
        return try {
            downloadFrom(context, url, onProgress)
        } catch (e: Exception) {
            if (url.startsWith("https://cdn.jsdelivr.net/")) {
                val gh = runCatching { fetchGithubUpdate() }.getOrNull()
                if (gh != null && gh.apkUrl != url) downloadFrom(context, gh.apkUrl, onProgress) else throw e
            } else {
                throw e
            }
        }
    }

    private suspend fun downloadFrom(context: Context, url: String, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", UA)
            try {
                if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, "update.apk")
                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) withContext(Dispatchers.Main) { onProgress((read * 100 / total).toInt()) }
                        }
                    }
                }
                file
            } finally {
                conn.disconnect()
            }
        }

    fun canRequestInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "com.ibis.expense.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
