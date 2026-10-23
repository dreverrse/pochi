package com.pochi.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast

data class AgentAction(val type: String, val argument: String)

object PochiAgent {
    private val actionPattern = Regex("""\[ACTION:([A-Z_]+)(?::([^\]]*))?]""")

    fun extract(reply: String): Pair<String, AgentAction?> {
        val match = actionPattern.find(reply) ?: return reply.trim() to null
        val cleaned = reply.replace(match.value, "").trim()
        val type = match.groupValues[1]
        val argument = match.groupValues[2].trim()
        return cleaned.ifBlank { "Siap, Tuan Andre." } to AgentAction(type, argument)
    }

    fun describe(context: Context, action: AgentAction): String {
        return when (action.type) {
            "OPEN_APP" -> {
                val label = runCatching {
                    val info = context.packageManager.getApplicationInfo(action.argument, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(action.argument)
                "Pochi ingin membuka $label"
            }
            "OPEN_URL" -> "Pochi ingin membuka tautan ${action.argument}"
            "SET_TIMER" -> "Pochi ingin memasang timer ${action.argument} detik"
            "SET_ALARM" -> "Pochi ingin memasang alarm jam ${action.argument}"
            else -> "Pochi ingin menjalankan aksi ${action.type}"
        }
    }

    fun execute(context: Context, action: AgentAction): String {
        return try {
            when (action.type) {
                "OPEN_APP" -> {
                    val intent = context.packageManager
                        .getLaunchIntentForPackage(action.argument)
                    if (intent == null) {
                        "Aplikasi tidak ditemukan."
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Aplikasi dibuka."
                    }
                }
                "OPEN_URL" -> {
                    val uri = Uri.parse(action.argument)
                    if (uri.scheme != "https" && uri.scheme != "http") {
                        "Tautan ditolak (hanya http/https)."
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Tautan dibuka."
                    }
                }
                "SET_TIMER" -> {
                    val seconds = action.argument.toIntOrNull()
                    if (seconds == null || seconds <= 0) {
                        "Durasi timer tidak valid."
                    } else {
                        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, "Pochi")
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Timer dipasang."
                    }
                }
                "SET_ALARM" -> {
                    val parts = action.argument.split(":")
                    val hour = parts.getOrNull(0)?.toIntOrNull()
                    val minute = parts.getOrNull(1)?.toIntOrNull()
                    if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
                        "Waktu alarm tidak valid."
                    } else {
                        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, hour)
                            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Alarm dipasang."
                    }
                }
                else -> "Aksi tidak dikenal."
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Aksi gagal: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            "Aksi gagal: ${e.localizedMessage}"
        }
    }
}
