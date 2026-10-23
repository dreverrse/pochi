package com.pochi.assistant

import android.app.Application
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PochiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val report = buildString {
                    append("Waktu: ")
                    append(stamp)
                    append("\nVersi: ")
                    append(packageManager.getPackageInfo(packageName, 0).versionName)
                    append("\nThread: ")
                    append(thread.name)
                    append("\n\n")
                    append(throwable.stackTraceToString())
                }
                File(filesDir, "last_crash.txt").writeText(report)
                runCatching {
                    getExternalFilesDir(null)?.let { dir ->
                        File(dir, "last_crash.txt").writeText(report)
                    }
                }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
