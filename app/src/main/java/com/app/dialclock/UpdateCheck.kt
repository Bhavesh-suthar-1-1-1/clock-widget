package com.app.dialclock

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * UpdateCheck.kt — in-app update check (v2.4, Atmo-style).
 *
 * On app open, queries the GitHub releases API for the clock-widget repo
 * and compares the latest tag with the installed version. Only when a
 * newer release exists does the app show an "Update available" dialog
 * (version, changelog bullets, download progress, Hide / Update).
 *
 * Rules: silent when up to date, offline, or on any error — no popup, no
 * error, no crash. Nothing is downloaded or installed without the user
 * tapping Update. Check runs at most once per app launch.
 */
object UpdateCheck {

    private const val LATEST_URL =
        "https://api.github.com/repos/Bhavesh-suthar-1-1-1/clock-widget/releases/latest"

    data class Release(
        val tag: String,         // e.g. "v2.5"
        val version: String,     // e.g. "2.5"
        val notes: List<String>, // changelog bullets from the release body
        val apkUrl: String       // browser_download_url of the .apk asset
    )

    /** The newer release, or null when up to date / offline / any failure. */
    suspend fun check(ctx: Context): Release? = withContext(Dispatchers.IO) {
        try {
            val current = installedVersion(ctx)
            val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Dial-App")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val version = tag.removePrefix("v").trim()
            if (version.isEmpty() || compareVersions(version, current) <= 0) return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url", "")
                    break
                }
            }
            if (apkUrl.isNullOrEmpty()) return@withContext null
            Release(tag, version, parseNotes(json.optString("body", "")), apkUrl)
        } catch (_: Exception) {
            null
        }
    }

    /** Downloads the APK to the cache dir, reporting 0..100 progress. Null on failure. */
    suspend fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "Dial-App")
                    instanceFollowRedirects = true
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val total = conn.contentLengthLong
                val outFile = File(ctx.cacheDir, "dial-update.apk")
                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { out ->
                        val buf = ByteArray(32 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            done += read
                            if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
                conn.disconnect()
                outFile
            } catch (_: Exception) {
                null
            }
        }

    /** Hands the downloaded APK to the system installer (user confirms the install). */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(intent)
    }

    private fun installedVersion(ctx: Context): String = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    /** "2.4" vs "2.5" etc. — positive when a is newer than b. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** Changelog bullets from the release body (up to 6 lines). */
    private fun parseNotes(body: String): List<String> {
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val bullets = lines
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { it.drop(2).trim() }
        val picked = if (bullets.isNotEmpty()) bullets else lines
        return picked.take(6)
    }
}
