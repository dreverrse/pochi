package com.pochi.assistant

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
            val body = connection.inputStream.bufferedReader().readText()
            val json = org.json.JSONObject(body)
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
        } catch (e: Exception) {
            UpdateInfo(hasUpdate = false, latestVersion = "", downloadUrl = "", tag = "")
        }
    }

    fun download(context: Context, url: String, version: String) {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Pochi $version")
            setDescription("Mengunduh pembaruan Pochi")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Pochi-$version.apk")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
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
