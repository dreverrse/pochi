package com.pochi.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import java.net.URLEncoder
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AgentAction(val type: String, val argument: String)

object PochiAgent {
    private val actionPattern = Regex("""\[ACTION:([A-Z_]+)(?::([^\]]*))?]""")

    private val appAliases = mapOf(
        "wa" to "com.whatsapp",
        "whatsapp" to "com.whatsapp",
        "ig" to "com.instagram.android",
        "instagram" to "com.instagram.android",
        "yt" to "com.google.android.youtube",
        "youtube" to "com.google.android.youtube",
        "spotify" to "com.spotify.music",
        "maps" to "com.google.android.apps.maps",
        "gmaps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "chrome" to "com.android.chrome",
        "telegram" to "org.telegram.messenger",
        "tiktok" to "com.zhiliaoapp.musically",
        "gojek" to "com.gojek.app",
        "grab" to "com.grabtaxi.passenger",
        "shopee" to "com.shopee.id",
        "tokopedia" to "com.tokopedia.tkpd",
        "dana" to "id.dana"
    )

    fun requiresApproval(action: AgentAction): Boolean {
        return when (action.type) {
            "CALL", "SMS", "WHATSAPP", "OPEN_URL" -> true
            else -> false
        }
    }

    fun extract(reply: String): Pair<String, AgentAction?> {
        val match = actionPattern.find(reply) ?: return reply.trim() to null
        val cleaned = reply.replace(match.value, "").trim()
        val type = match.groupValues[1]
        val argument = match.groupValues[2].trim()
        return cleaned.ifBlank { "Oke, langsung disiapkan." } to AgentAction(type, argument)
    }

    /**
     * Menangani perintah harian secara instan (0ms, tanpa perlu API) untuk kalkulator,
     * catatan cepat, timer, alarm, senter, buka aplikasi, dan pengaturan.
     */
    fun tryHandleLocal(context: Context, rawInput: String): Pair<String, AgentAction?>? {
        val input = rawInput.trim()
        if (input.isEmpty()) return null
        val lower = input.lowercase(Locale.getDefault())

        // 1. Kalkulator & Persentase Instan (mis: "45rb * 3", "= 125000 + 11%", "hitung 350000 / 4")
        val mathCandidate = when {
            lower.startsWith("=") -> input.substring(1).trim()
            lower.startsWith("hitung ") -> input.substring(7).trim()
            lower.startsWith(" berapa ") -> input.substring(8).trim()
            else -> input.removeSuffix("=").removeSuffix("?").trim()
        }
        evalQuickMath(mathCandidate)?.let { resultStr ->
            return "Hasilnya: $resultStr" to null
        }

        // 2. Catatan Cepat ("catat ...", "note ...", "ingat ...")
        val notePrefixes = listOf("catat:", "catat ", "note:", "note ", "ingat:", "ingat ")
        for (prefix in notePrefixes) {
            if (lower.startsWith(prefix)) {
                val content = input.substring(prefix.length).trim()
                if (content.isNotEmpty()) {
                    return "Sudah masuk ke catatan: \"$content\"" to AgentAction("SAVE_NOTE", content)
                }
            }
        }

        // 3. Senter ("senter", "nyalakan senter", "matikan senter")
        if (lower == "senter" || lower == "nyalakan senter" || lower == "hidupkan senter" || lower == "senter on") {
            return "Senter dinyalakan." to AgentAction("TORCH", "ON")
        }
        if (lower == "matikan senter" || lower == "senter mati" || lower == "senter off") {
            return "Senter dimatikan." to AgentAction("TORCH", "OFF")
        }

        // 4. Timer Cepat ("timer 5 menit", "timer 30 detik", "pasang timer 10 menit")
        val timerRegex = Regex("""^(?:pasang\s+)?timer\s+(\d+)\s*(detik|dtk|s|menit|mnt|m|jam|j)?$""", RegexOption.IGNORE_CASE)
        timerRegex.matchEntire(lower)?.let { match ->
            val amount = match.groupValues[1].toIntOrNull() ?: return null
            val unit = match.groupValues[2].lowercase()
            val seconds = when (unit) {
                "detik", "dtk", "s" -> amount
                "jam", "j" -> amount * 3600
                else -> amount * 60
            }
            val labelUnit = when (unit) {
                "detik", "dtk", "s" -> "$amount detik"
                "jam", "j" -> "$amount jam"
                else -> "$amount menit"
            }
            return "Timer $labelUnit dipasang." to AgentAction("SET_TIMER", seconds.toString())
        }

        // 5. Alarm Cepat ("alarm 06:30", "pasang alarm 05.45")
        val alarmRegex = Regex("""^(?:pasang\s+)?alarm\s+(?:jam\s+)?(\d{1,2})[:.](\d{2})$""", RegexOption.IGNORE_CASE)
        alarmRegex.matchEntire(lower)?.let { match ->
            val h = match.groupValues[1].toIntOrNull() ?: return null
            val m = match.groupValues[2].toIntOrNull() ?: return null
            if (h in 0..23 && m in 0..59) {
                val timeFormatted = String.format(Locale.US, "%02d:%02d", h, m)
                return "Alarm dipasang untuk jam $timeFormatted." to AgentAction("SET_ALARM", timeFormatted)
            }
        }

        // 6. Buka Aplikasi ("buka whatsapp", "buka spotify", "open ig")
        val openPrefixes = listOf("buka aplikasi ", "buka app ", "buka ", "open ")
        for (prefix in openPrefixes) {
            if (lower.startsWith(prefix)) {
                val target = input.substring(prefix.length).trim()
                if (target.isNotEmpty() && !target.contains(" ")) {
                    return "Membuka $target..." to AgentAction("OPEN_APP", target)
                } else if (target.isNotEmpty()) {
                    val resolved = resolveAppPackage(context, target)
                    if (resolved != null) {
                        return "Membuka $target..." to AgentAction("OPEN_APP", resolved)
                    }
                }
            }
        }

        // 7. Cari di Maps ("rute ke ...", "lokasi ...", "maps ...")
        val mapPrefixes = listOf("rute ke ", "navigasi ke ", "maps ", "cari lokasi ")
        for (prefix in mapPrefixes) {
            if (lower.startsWith(prefix)) {
                val place = input.substring(prefix.length).trim()
                if (place.isNotEmpty()) {
                    return "Membuka peta untuk $place." to AgentAction("MAPS", place)
                }
            }
        }

        // 8. Pengaturan Cepat ("pengaturan wifi", "setelan bluetooth")
        if (lower in listOf("wifi", "setelan wifi", "pengaturan wifi")) {
            return "Membuka pengaturan Wi-Fi." to AgentAction("SETTINGS", "WIFI")
        }
        if (lower in listOf("bluetooth", "setelan bluetooth", "pengaturan bluetooth")) {
            return "Membuka pengaturan Bluetooth." to AgentAction("SETTINGS", "BLUETOOTH")
        }

        return null
    }

    fun describe(context: Context, action: AgentAction): String {
        return when (action.type) {
            "OPEN_APP" -> {
                val pkg = resolveAppPackage(context, action.argument) ?: action.argument
                val label = runCatching {
                    val info = context.packageManager.getApplicationInfo(pkg, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(action.argument)
                "Membuka aplikasi $label"
            }
            "OPEN_URL" -> "Membuka tautan ${action.argument}"
            "SEARCH_WEB" -> "Mencari \"${action.argument}\" di browser"
            "YOUTUBE" -> "Mencari \"${action.argument}\" di YouTube"
            "SET_TIMER" -> "Memasang timer ${action.argument} detik"
            "SET_ALARM" -> "Memasang alarm jam ${action.argument.substringBefore("|")}"
            "CALL" -> "Menelepon ${action.argument}"
            "SMS" -> {
                val target = action.argument.substringBefore("|")
                "Mengirim SMS ke $target"
            }
            "WHATSAPP" -> {
                val target = action.argument.substringBefore("|")
                "Membuka chat WhatsApp ke $target"
            }
            "CALENDAR" -> {
                val title = action.argument.substringBefore("|")
                "Menambah jadwal \"$title\""
            }
            "MAPS" -> "Membuka peta ke ${action.argument}"
            "CLIPBOARD" -> "Menyalin teks ke clipboard"
            "SAVE_NOTE" -> "Menyimpan ke catatan"
            "TORCH" -> if (action.argument.equals("OFF", true)) "Mematikan senter" else "Menyalakan senter"
            "SETTINGS" -> "Membuka pengaturan ${action.argument}"
            else -> "Menjalankan ${action.type}"
        }
    }

    fun execute(context: Context, action: AgentAction): String {
        return try {
            when (action.type) {
                "OPEN_APP" -> {
                    val pkg = resolveAppPackage(context, action.argument)
                    val intent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
                    if (intent == null) {
                        "Aplikasi \"${action.argument}\" tidak ditemukan di HP."
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Aplikasi dibuka."
                    }
                }
                "OPEN_URL" -> {
                    val raw = action.argument.trim()
                    val normalized = if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
                        "https://$raw"
                    } else raw
                    val uri = Uri.parse(normalized)
                    if (uri.scheme != "https" && uri.scheme != "http") {
                        "Tautan ditolak (hanya http/https)."
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Tautan dibuka."
                    }
                }
                "SEARCH_WEB" -> {
                    val q = URLEncoder.encode(action.argument, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "Pencarian dibuka."
                }
                "YOUTUBE" -> {
                    val q = URLEncoder.encode(action.argument, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "YouTube dibuka."
                }
                "SET_TIMER" -> {
                    val seconds = action.argument.trim().toIntOrNull()
                    if (seconds == null || seconds <= 0) {
                        "Durasi timer tidak valid."
                    } else {
                        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, "Pochi Timer")
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Timer dipasang."
                    }
                }
                "SET_ALARM" -> {
                    val timePart = action.argument.substringBefore("|").trim()
                    val labelPart = action.argument.substringAfter("|", "Pochi").trim()
                    val parts = timePart.split(":", ".")
                    val hour = parts.getOrNull(0)?.toIntOrNull()
                    val minute = parts.getOrNull(1)?.toIntOrNull()
                    if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
                        "Format waktu alarm tidak valid."
                    } else {
                        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, hour)
                            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, labelPart)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Alarm jam ${String.format(Locale.US, "%02d:%02d", hour, minute)} dipasang."
                    }
                }
                "CALL" -> {
                    val number = PochiContacts.resolveNumber(context, action.argument)
                    if (number.isBlank()) {
                        "Nomor tidak ditemukan."
                    } else {
                        val intent = Intent(
                            Intent.ACTION_DIAL,
                            Uri.fromParts("tel", number, null)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Dialer dibuka untuk $number."
                    }
                }
                "SMS" -> {
                    val target = action.argument.substringBefore("|").trim()
                    val message = action.argument.substringAfter("|", "")
                    val number = PochiContacts.resolveNumber(context, target)
                    if (number.isBlank()) {
                        "Nomor tidak ditemukan."
                    } else {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
                            .putExtra("sms_body", message)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Aplikasi pesan dibuka untuk $number."
                    }
                }
                "WHATSAPP" -> {
                    val target = action.argument.substringBefore("|").trim()
                    val message = action.argument.substringAfter("|", "").trim()
                    val rawNumber = PochiContacts.resolveNumber(context, target)
                        .replace(Regex("[^0-9+]"), "")
                    val waNumber = when {
                        rawNumber.startsWith("+") -> rawNumber.substring(1)
                        rawNumber.startsWith("0") -> "62" + rawNumber.substring(1)
                        else -> rawNumber
                    }
                    val encodedMsg = URLEncoder.encode(message, "UTF-8")
                    val uri = if (waNumber.length >= 8) {
                        Uri.parse("https://wa.me/$waNumber?text=$encodedMsg")
                    } else {
                        Uri.parse("https://wa.me/?text=$encodedMsg")
                    }
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "WhatsApp dibuka."
                }
                "CALENDAR" -> {
                    val parts = action.argument.split("|")
                    val title = parts.getOrNull(0)?.trim().orEmpty().ifBlank { "Agenda" }
                    val minutesFromNow = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 60
                    val durationMinutes = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 30
                    val start = System.currentTimeMillis() + minutesFromNow * 60_000L
                    val end = start + durationMinutes * 60_000L
                    val intent = Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, title)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "Kalender dibuka."
                }
                "MAPS" -> {
                    val query = URLEncoder.encode(action.argument, "UTF-8")
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("geo:0,0?q=$query")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "Peta dibuka."
                }
                "CLIPBOARD" -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Pochi", action.argument))
                    Toast.makeText(context, "Disalin ke clipboard", Toast.LENGTH_SHORT).show()
                    "Teks disalin ke clipboard."
                }
                "SAVE_NOTE" -> {
                    val current = PochiPrefs.getNotes(context).trim()
                    val stamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    val item = "• ${action.argument.trim()} ($stamp)"
                    val updated = if (current.isEmpty()) item else "$current\n$item"
                    PochiPrefs.saveNotes(context, updated)
                    "Disimpan ke catatan."
                }
                "TORCH" -> {
                    val turnOn = !action.argument.equals("OFF", ignoreCase = true)
                    val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val camId = camManager.cameraIdList.firstOrNull()
                    if (camId != null) {
                        camManager.setTorchMode(camId, turnOn)
                        if (turnOn) "Senter menyala." else "Senter mati."
                    } else {
                        "Senter tidak tersedia."
                    }
                }
                "SETTINGS" -> {
                    val actionIntent = when (action.argument.uppercase(Locale.US)) {
                        "WIFI" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                        "BLUETOOTH" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        "SOUND" -> Intent(Settings.ACTION_SOUND_SETTINGS)
                        "DISPLAY" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                        else -> Intent(Settings.ACTION_SETTINGS)
                    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(actionIntent)
                    "Pengaturan dibuka."
                }
                else -> "Aksi tidak dikenal."
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal menjalankan aksi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            "Gagal menjalankan aksi: ${e.localizedMessage}"
        }
    }

    private fun resolveAppPackage(context: Context, query: String): String? {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return null
        val lower = cleaned.lowercase(Locale.getDefault())

        // 1. Cek alias populer
        appAliases[lower]?.let { pkg ->
            if (context.packageManager.getLaunchIntentForPackage(pkg) != null) return pkg
        }

        // 2. Cek apakah query sudah berupa package name yang valid
        if (cleaned.contains(".") && context.packageManager.getLaunchIntentForPackage(cleaned) != null) {
            return cleaned
        }

        // 3. Cari berdasarkan nama aplikasi yang terinstal di launcher
        runCatching {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = context.packageManager.queryIntentActivities(mainIntent, 0)
            val exact = apps.firstOrNull { info ->
                info.loadLabel(context.packageManager).toString().equals(cleaned, ignoreCase = true)
            }
            if (exact != null) return exact.activityInfo.packageName

            val partial = apps.firstOrNull { info ->
                info.loadLabel(context.packageManager).toString().lowercase(Locale.getDefault()).contains(lower) ||
                    info.activityInfo.packageName.lowercase(Locale.getDefault()).contains(lower)
            }
            if (partial != null) return partial.activityInfo.packageName
        }

        return appAliases[lower]
    }

    /**
     * Evaluator ekspresi matematika sederhana termasuk "rb"/"ribu", "jt"/"juta", dan persen "%".
     */
    private fun evalQuickMath(raw: String): String? {
        val s = raw.lowercase(Locale.getDefault())
            .replace("rp", "")
            .replace("ribu", "000")
            .replace("rb", "000")
            .replace("juta", "000000")
            .replace("jt", "000000")
            .replace("x", "*")
            .replace("×", "*")
            .replace("÷", "/")
            .replace(":", "/")
            .replace(" ", "")

        // Harus punya minimal satu operator aritmatika dan hanya terdiri dari karakter angka/operator
        if (s.length < 3) return null
        if (!s.any { it in charArrayOf('+', '-', '*', '/') }) return null
        if (!s.all { it.isDigit() || it in charArrayOf('+', '-', '*', '/', '.', ',', '%', '(', ')') }) return null

        // Normalisasi titik ribuan vs desimal (jika seperti 150.000 -> 150000)
        val normalized = s.replace(Regex("""\.(\d{3})(?!\d)"""), "$1").replace(',', '.')

        return runCatching {
            // Tangani pola A + B% atau A - B% (diskon/pajak)
            val percentAddSub = Regex("""^(\d+(?:\.\d+)?)([+-])(\d+(?:\.\d+)?)%$""").matchEntire(normalized)
            val value = if (percentAddSub != null) {
                val base = percentAddSub.groupValues[1].toDouble()
                val op = percentAddSub.groupValues[2]
                val pct = percentAddSub.groupValues[3].toDouble()
                if (op == "+") base * (1.0 + pct / 100.0) else base * (1.0 - pct / 100.0)
            } else {
                val expr = normalized.replace(Regex("""(\d+(?:\.\d+)?)%"""), "($1/100.0)")
                parseAndEval(expr)
            }
            if (value == null || value.isNaN() || value.isInfinite()) return null
            val symbols = DecimalFormatSymbols(Locale("id", "ID")).apply {
                groupingSeparator = '.'
                decimalSeparator = ','
            }
            DecimalFormat("#,##0.##", symbols).format(value)
        }.getOrNull()
    }

    private fun parseAndEval(expr: String): Double? {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < expr.length) expr[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val d = parseFactor()
                        if (d == 0.0) return Double.NaN
                        x /= d
                    }
                    else -> return x
                }
            }
        }

        fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()
            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException()
            } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                x = expr.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException()
            }
            return x
        }

        nextChar()
        val x = parseExpression()
        if (pos < expr.length) return null
        return x
    }
}
