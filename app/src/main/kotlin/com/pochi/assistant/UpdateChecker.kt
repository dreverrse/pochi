package com.pochi.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val REPO = "dreverrse/pochi"

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val downloadUrl: String,
        val tag: String
    )

    suspend fun check(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val connection = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode != 200) throw Exception("HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            val assets = json.optJSONArray("assets") ?: JSONArray()
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
            UpdateInfo(
                hasUpdate = tag.isNotBlank() && compareVersions(tag, current) > 0,
                latestVersion = tag,
                downloadUrl = apkUrl,
                tag = json.optString("tag_name")
            )
        } catch (_: Exception) {
            UpdateInfo(hasUpdate = false, latestVersion = "", downloadUrl = "", tag = "")
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        url: String,
        version: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = context.externalCacheDir ?: context.cacheDir
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("Pochi-") && file.name.endsWith(".apk")) {
                    runCatching { file.delete() }
                }
            }
            val targetFile = File(dir, "Pochi-$version.apk")

            var currentUrl = url
            var redirects = 0
            var connection: HttpURLConnection
            while (true) {
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "Pochi-Android-Updater")
                }
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location.isNullOrBlank() || ++redirects > 5) {
                        error("Terlalu banyak pengalihan unduhan")
                    }
                    currentUrl = location
                } else if (code in 200..299) {
                    break
                } else {
                    connection.disconnect()
                    error("Gagal mengunduh APK (HTTP $code)")
                }
            }

            try {
                val totalBytes = connection.contentLengthLong
                connection.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var downloaded = 0L
                        var lastReported = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                                if (pct != lastReported) {
                                    lastReported = pct
                                    withContext(Dispatchers.Main) { onProgress(pct) }
                                }
                            }
                        }
                        output.flush()
                    }
                }
            } finally {
                connection.disconnect()
            }

            withContext(Dispatchers.Main) {
                onProgress(100)
                installApk(context, targetFile)
            }
            targetFile
        }
    }

    fun openReleasePage(context: Context) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/$REPO/releases/latest")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            openReleasePage(context)
            return
        }

        runCatching {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        }.onFailure {
            openReleasePage(context)
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").mapNotNull { it.toIntOrNull() }
        val pb = b.split(".").mapNotNull { it.toIntOrNull() }
        val size = maxOf(pa.size, pb.size)
        for (i in 0 until size) {
            val va = pa.getOrNull(i) ?: 0
            val vb = pb.getOrNull(i) ?: 0
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }
}
