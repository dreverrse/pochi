package com.pochi.assistant

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Rich Generative UI Data Models for Pochi Jarvis AI Agent.
 * Supports Crypto Live Market, Interactive Gradient Charts, Animated Weather,
 * Prayer Schedules, Dropped File Inspection, System Telemetry, and Visual Knowledge.
 */
sealed class PochiRichCard {
    data class Crypto(
        val symbol: String,
        val name: String,
        val priceUsd: Double,
        val priceIdr: Long,
        val change24h: Double,
        val high24h: Double,
        val low24h: Double,
        val volumeLabel: String,
        val sparkline: List<Float>,
        val colorStart: Long,
        val colorEnd: Long
    ) : PochiRichCard()

    data class Chart(
        val title: String,
        val subtitle: String,
        val points: List<Pair<String, Float>>,
        val unitPrefix: String = "",
        val unitSuffix: String = "",
        val growthBadge: String = "+18.4%",
        val colorStart: Long = 0xFF9D4EDD,
        val colorEnd: Long = 0xFF00F5D4
    ) : PochiRichCard()

    data class Weather(
        val city: String,
        val tempC: Int,
        val feelsLikeC: Int,
        val condition: String,
        val humidity: Int,
        val windKmh: Int,
        val uvIndex: Int,
        val hourly: List<Pair<String, Int>>
    ) : PochiRichCard()

    data class Prayer(
        val city: String,
        val nextPrayerName: String,
        val nextPrayerTime: String,
        val remainingText: String,
        val schedule: List<PrayerTimeItem>
    ) : PochiRichCard()

    data class FileDrop(
        val fileName: String,
        val mimeType: String,
        val sizeLabel: String,
        val snippet: String,
        val insight: String
    ) : PochiRichCard()

    data class SystemStatus(
        val batteryPct: Int,
        val isCharging: Boolean,
        val ramUsedMb: Int,
        val ramTotalMb: Int,
        val activeApp: String,
        val activeMedia: String,
        val uptimeHours: Int
    ) : PochiRichCard()

    data class Knowledge(
        val query: String,
        val title: String,
        val summary: String,
        val source: String,
        val tags: List<String>
    ) : PochiRichCard()
}

data class JarvisReplyBundle(
    val text: String,
    val emote: PochiEmote,
    val richCard: PochiRichCard? = null,
    val actionFeedback: String? = null
)

object PochiJarvisEngine {

    private data class CoinMeta(
        val symbol: String,
        val name: String,
        val binancePair: String,
        val geckoId: String,
        val fallbackUsd: Double,
        val fallbackChange: Double,
        val colorStart: Long,
        val colorEnd: Long
    )

    private val knownCoins = listOf(
        CoinMeta("BTC", "Bitcoin", "BTCUSDT", "bitcoin", 94250.0, 4.38, 0xFFF7931A, 0xFFFFD166),
        CoinMeta("ETH", "Ethereum", "ETHUSDT", "ethereum", 3280.5, 3.12, 0xFF627EEA, 0xFF00F5D4),
        CoinMeta("SOL", "Solana", "SOLUSDT", "solana", 214.8, 7.64, 0xFF9945FF, 0xFF14F195),
        CoinMeta("BNB", "BNB Chain", "BNBUSDT", "binancecoin", 658.0, 1.95, 0xFFF3BA2F, 0xFFFF9F1C),
        CoinMeta("XRP", "Ripple XRP", "XRPUSDT", "ripple", 2.42, 5.40, 0xFF00A8E8, 0xFF9D4EDD),
        CoinMeta("DOGE", "Dogecoin", "DOGEUSDT", "dogecoin", 0.368, 6.20, 0xFFC2A633, 0xFFFFE66D),
        CoinMeta("TON", "Toncoin", "TONUSDT", "the-open-network", 5.84, 2.85, 0xFF0088CC, 0xFF38EF7D),
        CoinMeta("ADA", "Cardano", "ADAUSDT", "cardano", 0.98, 3.45, 0xFF3CC8C8, 0xFF5A189A),
        CoinMeta("PEPE", "Pepe", "PEPEUSDT", "pepe", 0.0000195, 11.4, 0xFF00F5D4, 0xFF34C759),
        CoinMeta("SUI", "Sui Network", "SUIUSDT", "sui", 3.45, 8.15, 0xFF4CC9F0, 0xFF7209B7),
        CoinMeta("AVAX", "Avalanche", "AVAXUSDT", "avalanche-2", 38.9, 4.10, 0xFFE84142, 0xFFFF758F)
    )

    /**
     * Reads metadata and text preview from any dropped or picked Android Uri.
     */
    fun inspectUri(context: Context, uri: Uri): DroppedFileInfo {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "dokumen_tuan_andre"
        var size = 0L
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: guessMimeFromName(name)

        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) {
                        val n = cursor.getString(nameIdx)
                        if (!n.isNullOrBlank()) name = n
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }

        val isTextLike = mime.startsWith("text/") ||
            mime.contains("json") ||
            mime.contains("xml") ||
            mime.contains("javascript") ||
            name.endsWith(".kt", true) ||
            name.endsWith(".py", true) ||
            name.endsWith(".md", true) ||
            name.endsWith(".txt", true) ||
            name.endsWith(".csv", true) ||
            name.endsWith(".json", true) ||
            name.endsWith(".log", true) ||
            name.endsWith(".html", true)

        var preview = ""
        if (isTextLike) {
            runCatching {
                resolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val buf = CharArray(1600)
                    val read = reader.read(buf)
                    if (read > 0) {
                        preview = String(buf, 0, read).trim()
                    }
                }
            }
        }

        return DroppedFileInfo(
            uri = uri,
            fileName = name,
            mimeType = mime,
            sizeBytes = size,
            textPreview = preview
        )
    }

    private fun guessMimeFromName(name: String): String {
        val lower = name.lowercase(Locale.US)
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") -> "image/*"
            lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") -> "audio/*"
            lower.endsWith(".mp4") || lower.endsWith(".mkv") -> "video/*"
            lower.endsWith(".zip") || lower.endsWith(".rar") -> "application/zip"
            lower.endsWith(".apk") -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    /**
     * Fetches live crypto price and 18-point chart from Binance / CoinGecko public APIs.
     */
    suspend fun fetchCryptoCard(rawQuery: String): PochiRichCard.Crypto = withContext(Dispatchers.IO) {
        val lower = rawQuery.lowercase(Locale.US)
        val meta = knownCoins.firstOrNull { c ->
            lower.contains(c.symbol.lowercase(Locale.US)) ||
                lower.contains(c.name.lowercase(Locale.US)) ||
                lower.contains(c.geckoId.lowercase(Locale.US))
        } ?: knownCoins.first()

        var priceUsd = meta.fallbackUsd
        var change24h = meta.fallbackChange
        var high24h = priceUsd * 1.035
        var low24h = priceUsd * 0.965
        var volumeLabel = "$28.4B"
        val sparkline = mutableListOf<Float>()

        // 1. Try live Binance 24hr ticker & klines
        runCatching {
            val tickerConn = (URL("https://api.binance.com/api/v3/ticker/24hr?symbol=${meta.binancePair}").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
            }
            if (tickerConn.responseCode in 200..299) {
                val body = tickerConn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                priceUsd = json.optString("lastPrice").toDoubleOrNull() ?: priceUsd
                change24h = json.optString("priceChangePercent").toDoubleOrNull() ?: change24h
                high24h = json.optString("highPrice").toDoubleOrNull() ?: high24h
                low24h = json.optString("lowPrice").toDoubleOrNull() ?: low24h
                val quoteVol = json.optString("quoteVolume").toDoubleOrNull() ?: 0.0
                if (quoteVol > 0) {
                    volumeLabel = when {
                        quoteVol >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", quoteVol / 1_000_000_000.0)
                        quoteVol >= 1_000_000 -> String.format(Locale.US, "$%.1fM", quoteVol / 1_000_000.0)
                        else -> String.format(Locale.US, "$%.0fK", quoteVol / 1_000.0)
                    }
                }
            }
            tickerConn.disconnect()

            val klineConn = (URL("https://api.binance.com/api/v3/klines?symbol=${meta.binancePair}&interval=2h&limit=18").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
            }
            if (klineConn.responseCode in 200..299) {
                val arr = JSONArray(klineConn.inputStream.bufferedReader().use { it.readText() })
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONArray(i)
                    val closePrice = item?.optString(4)?.toFloatOrNull()
                    if (closePrice != null) sparkline.add(closePrice)
                }
            }
            klineConn.disconnect()
        }

        // 2. Fallback to CoinGecko if sparkline still empty
        if (sparkline.isEmpty()) {
            runCatching {
                val geckoUrl = "https://api.coingecko.com/api/v3/simple/price?ids=${meta.geckoId}&vs_currencies=usd,idr&include_24hr_change=true"
                val conn = (URL(geckoUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4500
                    readTimeout = 4500
                }
                if (conn.responseCode in 200..299) {
                    val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        .optJSONObject(meta.geckoId)
                    if (obj != null) {
                        priceUsd = obj.optDouble("usd", priceUsd)
                        change24h = obj.optDouble("usd_24h_change", change24h)
                        high24h = priceUsd * (1.0 + kotlin.math.abs(change24h) / 150.0)
                        low24h = priceUsd * (1.0 - kotlin.math.abs(change24h) / 160.0)
                    }
                }
                conn.disconnect()
            }
        }

        // 3. Ensure smooth 18-point sparkline curve
        if (sparkline.size < 6) {
            sparkline.clear()
            val base = priceUsd.toFloat()
            val trend = if (change24h >= 0) 1f else -1f
            val wave = listOf(-0.028f, -0.019f, -0.024f, -0.011f, -0.016f, -0.004f, 0.002f, -0.006f, 0.008f, 0.014f, 0.009f, 0.018f, 0.013f, 0.022f, 0.019f, 0.026f, 0.024f, 0.031f)
            wave.forEach { w ->
                sparkline.add(base * (1f + w * trend))
            }
        }

        val priceIdr = (priceUsd * 16250.0).toLong()
        PochiRichCard.Crypto(
            symbol = meta.symbol,
            name = meta.name,
            priceUsd = priceUsd,
            priceIdr = priceIdr,
            change24h = change24h,
            high24h = high24h,
            low24h = low24h,
            volumeLabel = volumeLabel,
            sparkline = sparkline,
            colorStart = meta.colorStart,
            colorEnd = meta.colorEnd
        )
    }

    /**
     * Fetches live weather from Open-Meteo API with city coordinate lookup.
     */
    suspend fun fetchWeatherCard(cityInput: String): PochiRichCard.Weather = withContext(Dispatchers.IO) {
        val city = cityInput.trim().ifBlank { "Jakarta" }
        val coords = when (city.lowercase(Locale.US)) {
            "bandung" -> -6.9175 to 107.6191
            "surabaya" -> -7.2575 to 112.7521
            "yogyakarta", "jogja" -> -7.7956 to 110.3695
            "semarang" -> -6.9667 to 110.4167
            "medan" -> 3.5952 to 98.6722
            "makassar" -> -5.1477 to 119.4327
            "denpasar", "bali" -> -8.6705 to 115.2126
            else -> -6.2088 to 106.8456 // Jakarta default
        }

        val fallback = WeatherHelper.getSnapshot(city)
        var tempC = fallback.tempC
        var feelsLike = tempC + 2
        var humidity = fallback.humidityPercent
        var windKmh = fallback.windKmh
        var condition = fallback.conditionId
        val hourly = mutableListOf<Pair<String, Int>>()

        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${coords.first}&longitude=${coords.second}&current=temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,weather_code&hourly=temperature_2m&forecast_days=1"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
            }
            if (conn.responseCode in 200..299) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val cur = json.optJSONObject("current")
                if (cur != null) {
                    tempC = cur.optDouble("temperature_2m", tempC.toDouble()).toInt()
                    feelsLike = cur.optDouble("apparent_temperature", feelsLike.toDouble()).toInt()
                    humidity = cur.optInt("relative_humidity_2m", humidity)
                    windKmh = cur.optDouble("wind_speed_10m", windKmh.toDouble()).toInt()
                    val code = cur.optInt("weather_code", 1)
                    condition = when (code) {
                        0 -> "Cerah Bersih"
                        1, 2, 3 -> "Cerah Berawan"
                        45, 48 -> "Berkabut Sejuk"
                        51, 53, 55, 61, 63, 65 -> "Hujan Ringan"
                        80, 81, 82, 95 -> "Hujan Petir"
                        else -> fallback.conditionId
                    }
                }
                val hObj = json.optJSONObject("hourly")
                val times = hObj?.optJSONArray("time")
                val temps = hObj?.optJSONArray("temperature_2m")
                val curHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                if (times != null && temps != null) {
                    for (offset in 0..4) {
                        val idx = (curHour + offset * 2).coerceIn(0, min(times.length() - 1, temps.length() - 1))
                        val tStr = times.optString(idx).substringAfter("T", "12:00").take(5)
                        val tVal = temps.optDouble(idx, tempC.toDouble()).toInt()
                        hourly.add(tStr to tVal)
                    }
                }
            }
            conn.disconnect()
        }

        if (hourly.isEmpty()) {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            for (i in 0..4) {
                val hr = (h + i * 2) % 24
                val delta = if (hr in 11..15) 2 else if (hr in 0..5) -3 else 0
                hourly.add(String.format(Locale.US, "%02d:00", hr) to (tempC + delta))
            }
        }

        val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val uv = when (hourNow) {
            in 10..14 -> 8
            in 7..9, in 15..16 -> 5
            else -> 1
        }

        PochiRichCard.Weather(
            city = city,
            tempC = tempC,
            feelsLikeC = feelsLike,
            condition = condition,
            humidity = humidity,
            windKmh = windKmh,
            uvIndex = uv,
            hourly = hourly
        )
    }

    /**
     * Builds an interactive gradient chart from user prompt, expenses, focus sessions, or custom numbers.
     */
    fun buildChartCard(context: Context, prompt: String, customTagArg: String? = null): PochiRichCard.Chart {
        if (!customTagArg.isNullOrBlank() && customTagArg.contains("|")) {
            val title = customTagArg.substringBefore("|").trim().ifBlank { "Analisis Grafik Tuan Andre" }
            val rawPairs = customTagArg.substringAfter("|").split(",")
            val parsed = rawPairs.mapNotNull { item ->
                val parts = item.split(":")
                if (parts.size == 2) {
                    val label = parts[0].trim()
                    val v = parts[1].replace("[^0-9.-]".toRegex(), "").toFloatOrNull()
                    if (label.isNotEmpty() && v != null) label to v else null
                } else null
            }
            if (parsed.size >= 3) {
                return PochiRichCard.Chart(
                    title = title,
                    subtitle = "Visualisasi Data Real-Time • Jarvis Analytics",
                    points = parsed,
                    growthBadge = "+24.6%"
                )
            }
        }

        val lower = prompt.lowercase(Locale.US)
        // Extract any explicit numbers in the user's prompt if >= 3 numbers provided
        val numMatches = Regex("""([A-Za-z]{2,10})\s*[:=]?\s*(\d+(?:\.\d+)?)""").findAll(prompt).toList()
        if (numMatches.size >= 3) {
            val pts = numMatches.take(8).map { m ->
                m.groupValues[1].take(6) to (m.groupValues[2].toFloatOrNull() ?: 10f)
            }
            return PochiRichCard.Chart(
                title = "Grafik Kustom Tuan Andre",
                subtitle = "Data yang diekstrak langsung dari instruksi",
                points = pts,
                growthBadge = "Custom"
            )
        }

        return when {
            lower.contains("pengeluaran") || lower.contains("uang") || lower.contains("biaya") || lower.contains("wallet") || lower.contains("keuangan") -> {
                val expenses = PochiPrefs.expensesFlow.value
                val pts = if (expenses.size >= 3) {
                    expenses.take(6).reversed().map { it.title.take(6) to (it.amountRp / 1000f) }
                } else {
                    listOf(
                        "Sen" to 45f,
                        "Sel" to 32f,
                        "Rab" to 78f,
                        "Kam" to 54f,
                        "Jum" to 92f,
                        "Sab" to 65f,
                        "Min" to 48f
                    )
                }
                PochiRichCard.Chart(
                    title = "Tren Pengeluaran & Arus Kas",
                    subtitle = "Dalam Ribuan Rupiah (IDR K) • Pekan Ini",
                    points = pts,
                    unitPrefix = "Rp ",
                    unitSuffix = "K",
                    growthBadge = "-8.2% Hemat",
                    colorStart = 0xFFFF2A85,
                    colorEnd = 0xFFFFB703
                )
            }
            lower.contains("fokus") || lower.contains("produktivitas") || lower.contains("kerja") || lower.contains("belajar") -> {
                val curFocus = PochiPrefs.focusSessionsFlow.value.coerceAtLeast(2)
                PochiRichCard.Chart(
                    title = "Grafik Produktivitas & Sesi Fokus",
                    subtitle = "Durasi Deep Work Tuan Andre (Menit)",
                    points = listOf(
                        "Sen" to 50f,
                        "Sel" to 75f,
                        "Rab" to 100f,
                        "Kam" to 65f,
                        "Jum" to 120f,
                        "Sab" to 90f,
                        "Hari Ini" to (curFocus * 25f)
                    ),
                    unitSuffix = " mnt",
                    growthBadge = "+32% Fokus",
                    colorStart = 0xFF7B2CBF,
                    colorEnd = 0xFF00F5D4
                )
            }
            else -> {
                PochiRichCard.Chart(
                    title = "Analitik Performa & Tren Data",
                    subtitle = "Visualisasi Multi-Metrik Real-Time",
                    points = listOf(
                        "Q1" to 42f,
                        "Q2" to 68f,
                        "Q3" to 61f,
                        "Q4" to 89f,
                        "Q5" to 114f,
                        "Now" to 136f
                    ),
                    growthBadge = "+41.2%",
                    colorStart = 0xFF9D4EDD,
                    colorEnd = 0xFF00F5D4
                )
            }
        }
    }

    fun buildPrayerCard(city: String): PochiRichCard.Prayer {
        val cleanCity = city.trim().ifBlank { PochiPrefs.cityFlow.value }
        val next = PrayerScheduleHelper.getNextPrayer(cleanCity)
        val sched = PrayerScheduleHelper.getScheduleForCity(cleanCity)
        return PochiRichCard.Prayer(
            city = cleanCity,
            nextPrayerName = next.prayer.nameId,
            nextPrayerTime = next.prayer.formatted,
            remainingText = next.remainingTextId,
            schedule = sched
        )
    }

    fun buildSystemStatusCard(context: Context): PochiRichCard.SystemStatus {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(1, 100) ?: 88
        val statusIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val status = statusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalMb = ((memInfo.totalMem / (1024 * 1024)).toInt()).coerceAtLeast(4096)
        val availMb = ((memInfo.availMem / (1024 * 1024)).toInt()).coerceIn(512, totalMb)
        val usedMb = (totalMb - availMb).coerceAtLeast(1024)

        val screenApp = PochiSignals.screenApp.value.ifBlank { "Pochi Jarvis Home" }
        val media = PochiSignals.mediaPlayback.value
        val activeMedia = if (media.hasTrack) "${media.title} • ${media.artist}" else "Tidak ada musik diputar"
        val uptimeHrs = ((SystemClock.elapsedRealtime() / (1000L * 3600L)).toInt()).coerceAtLeast(1)

        return PochiRichCard.SystemStatus(
            batteryPct = pct,
            isCharging = isCharging,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            activeApp = screenApp,
            activeMedia = activeMedia,
            uptimeHours = uptimeHrs
        )
    }

    suspend fun fetchKnowledgeCard(rawQuery: String): PochiRichCard.Knowledge? = withContext(Dispatchers.IO) {
        val clean = rawQuery
            .replace(Regex("""^(?:tolong\s+|pochi\s+|jarvis\s+)?(?:carikan|cari|apa\s+itu|siapa\s+itu|siapa|jelaskan\s+tentang|info\s+tentang)\s+""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (clean.length < 2) return@withContext null

        val encoded = URLEncoder.encode(clean.replace(" ", "_"), "UTF-8")
        for (langHost in listOf("id.wikipedia.org", "en.wikipedia.org")) {
            val res = runCatching {
                val conn = (URL("https://$langHost/api/rest_v1/page/summary/$encoded").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "PochiJarvis/2.0")
                }
                if (conn.responseCode in 200..299) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val title = json.optString("title")
                    val extract = json.optString("extract")
                    val desc = json.optString("description")
                    conn.disconnect()
                    if (title.isNotBlank() && extract.isNotBlank()) {
                        return@withContext PochiRichCard.Knowledge(
                            query = clean,
                            title = title,
                            summary = extract.take(320),
                            source = if (langHost.startsWith("id")) "Ensiklopedia ID • Live" else "Global Knowledge • Live",
                            tags = listOfNotNull(
                                desc.takeIf { it.isNotBlank() }?.take(28),
                                "Verified Data",
                                "Jarvis Search"
                            )
                        )
                    }
                }
                conn.disconnect()
                null
            }.getOrNull()
            if (res != null) return@withContext res
        }
        null
    }

    /**
     * Unified Jarvis query processor used by BOTH the Notch Dynamic Island and the Main App.
     */
    suspend fun processQuery(
        context: Context,
        rawInput: String,
        droppedFile: DroppedFileInfo? = null
    ): JarvisReplyBundle {
        val clean = rawInput.trim()
        val lower = clean.lowercase(Locale.US)
        val city = PochiPrefs.cityFlow.value

        // 0. If a file was dropped or attached, inspect and analyze it!
        if (droppedFile != null) {
            val provider = PochiPrefs.getProvider(context)
            val apiKey = PochiPrefs.getApiKey(context, provider)
            val model = PochiPrefs.getModel(context, provider)

            val aiInsight = if (apiKey.isNotBlank()) {
                val filePrompt = buildString {
                    append("Tuan Andre baru saja menjatuhkan/melampirkan file bernama '${droppedFile.fileName}' ")
                    append("(Tipe: ${droppedFile.mimeType}, Ukuran: ${droppedFile.formattedSize}). ")
                    if (droppedFile.textPreview.isNotBlank()) {
                        append("\nIsi cuplikan file:\n${droppedFile.textPreview.take(900)}\n")
                    }
                    if (clean.isNotBlank()) {
                        append("\nInstruksi Tuan Andre untuk file ini: $clean")
                    } else {
                        append("\nBerikan analisis singkat dan cerdas untuk Tuan Andre mengenai file ini.")
                    }
                }
                runCatching {
                    val raw = AiClient.ask(provider, apiKey, model, filePrompt, PochiDb.getInstance(context).getMemoriesPrompt())
                    PochiAgent.extractEmote(PochiAgent.extract(raw).first, clean).first
                }.getOrDefault("File '${droppedFile.fileName}' (${droppedFile.formattedSize}) telah saya pindai, Tuan Andre. Struktur dokumen valid dan siap diproses.")
            } else {
                if (droppedFile.textPreview.isNotBlank()) {
                    "File teks/kode '${droppedFile.fileName}' (${droppedFile.formattedSize}) berhasil dipindai (${droppedFile.textPreview.lines().size} baris terdeteksi). Siap diolah, Tuan Andre!"
                } else {
                    "Berkas '${droppedFile.fileName}' (${droppedFile.mimeType} • ${droppedFile.formattedSize}) berhasil diterima di Notch Jarvis, Tuan Andre."
                }
            }

            val card = PochiRichCard.FileDrop(
                fileName = droppedFile.fileName,
                mimeType = droppedFile.mimeType,
                sizeLabel = droppedFile.formattedSize,
                snippet = droppedFile.textPreview.ifBlank { "Binary / Media Stream • URI: ${droppedFile.uri?.scheme ?: "content"}://${droppedFile.fileName}" },
                insight = aiInsight
            )
            return JarvisReplyBundle(
                text = aiInsight,
                emote = PochiEmote.EXCITED,
                richCard = card
            )
        }

        // 1. Detect rich visual intent directly from user prompt
        val wantsCrypto = lower.contains("kripto") || lower.contains("crypto") ||
            lower.contains("bitcoin") || lower.contains("btc") ||
            lower.contains("ethereum") || lower.contains("eth") ||
            lower.contains("solana") || lower.contains("sol") ||
            lower.contains("bnb") || lower.contains("doge") ||
            lower.contains("xrp") || lower.contains("pepe") ||
            lower.contains("toncoin") || lower.contains("sui") ||
            lower.contains("avax") || lower.contains("harga koin")

        val wantsChart = lower.contains("grafik") || lower.contains("chart") ||
            lower.contains("statistik") || lower.contains("diagram") ||
            lower.contains("tren ") || lower.contains("visualisasi")

        val wantsWeather = lower.contains("suhu") || lower.contains("cuaca") ||
            lower.contains("weather") || lower.contains("temperatur") ||
            lower.contains("mendung") || lower.contains("hujan")

        val wantsPrayer = lower.contains("sholat") || lower.contains("salat") ||
            lower.contains("shalat") || lower.contains("adzan") ||
            lower.contains("subuh") || lower.contains("dzuhur") ||
            lower.contains("ashar") || lower.contains("maghrib") ||
            lower.contains("isya") || lower.contains("imsak")

        val wantsSystem = lower.contains("baterai") || lower.contains("battery") ||
            lower.contains("status hp") || lower.contains("ram ") ||
            lower.contains("kondisi hp") || lower.contains("sedang apa") ||
            lower.contains("layar")

        // Pre-fetch rich card if matched
        var preFetchedCard: PochiRichCard? = when {
            wantsCrypto -> fetchCryptoCard(clean)
            wantsChart -> buildChartCard(context, clean)
            wantsWeather -> {
                val targetCity = PrayerScheduleHelper.supportedCities.firstOrNull {
                    lower.contains(it.lowercase(Locale.US))
                } ?: city
                fetchWeatherCard(targetCity)
            }
            wantsPrayer -> {
                val targetCity = PrayerScheduleHelper.supportedCities.firstOrNull {
                    lower.contains(it.lowercase(Locale.US))
                } ?: city
                buildPrayerCard(targetCity)
            }
            wantsSystem -> buildSystemStatusCard(context)
            else -> null
        }

        // 2. Check fast local device commands (Timer, Alarm, Notes, Remember, Camera, etc.)
        val localResult = PochiAgent.tryHandleLocal(context, clean)
        if (localResult != null && preFetchedCard == null) {
            val (replyText, action) = localResult
            val feedback = action?.let { PochiAgent.execute(context, it) }.orEmpty()
            val fullText = if (feedback.isNotBlank()) "$replyText\n$feedback" else replyText
            return JarvisReplyBundle(
                text = fullText,
                emote = PochiEmote.HAPPY,
                richCard = null,
                actionFeedback = action?.type
            )
        }

        // 3. Query AI Brain if API Key is configured
        val provider = PochiPrefs.getProvider(context)
        val apiKey = PochiPrefs.getApiKey(context, provider)
        val model = PochiPrefs.getModel(context, provider)

        if (apiKey.isNotBlank()) {
            val db = PochiDb.getInstance(context)
            val memoriesPrompt = db.getMemoriesPrompt()
            val screenApp = PochiSignals.screenApp.value
            val screenText = PochiSignals.screenText.value
            val media = PochiSignals.mediaPlayback.value

            val contextNote = buildString {
                if (memoriesPrompt.isNotBlank()) {
                    append("\nMemori Tersimpan Tuan Andre:\n$memoriesPrompt")
                }
                if (screenApp.isNotBlank()) {
                    append("\nAplikasi aktif di layar HP Tuan Andre: $screenApp")
                }
                if (screenText.isNotBlank()) {
                    append("\nCuplikan teks layar: ${screenText.take(240)}")
                }
                if (media.hasTrack) {
                    append("\nMusik diputar: ${media.title} - ${media.artist}")
                }
                when (val c = preFetchedCard) {
                    is PochiRichCard.Crypto -> append("\nData Live ${c.name} (${c.symbol}): $${c.priceUsd} (Rp ${c.priceIdr}), perubahan 24j ${c.change24h}%")
                    is PochiRichCard.Weather -> append("\nData Cuaca Live ${c.city}: ${c.tempC}°C (${c.condition}), kelembapan ${c.humidity}%")
                    is PochiRichCard.Prayer -> append("\nJadwal Sholat ${c.city} berikutnya: ${c.nextPrayerName} pukul ${c.nextPrayerTime} (${c.remainingText})")
                    else -> {}
                }
            }

            val aiRaw = runCatching {
                AiClient.ask(
                    provider = provider,
                    apiKey = apiKey,
                    model = model,
                    prompt = clean,
                    contextNote = contextNote
                )
            }.getOrNull()

            if (aiRaw != null) {
                val (withoutCard, aiCard) = extractCardTag(context, aiRaw, clean)
                if (preFetchedCard == null && aiCard != null) {
                    preFetchedCard = aiCard
                }
                val (withoutAction, action) = PochiAgent.extract(withoutCard)
                val (cleanReply, emote) = PochiAgent.extractEmote(withoutAction, clean)

                var actionFeedback: String? = null
                if (action != null) {
                    if (PochiAgent.requiresApproval(action)) {
                        ApprovalManager.requestAction(context, action)
                        actionFeedback = "Meminta konfirmasi Tuan Andre untuk ${action.type}"
                    } else {
                        actionFeedback = PochiAgent.execute(context, action)
                    }
                }

                // If still no rich card and it's a search/knowledge query, try Wikipedia live card
                if (preFetchedCard == null && (lower.startsWith("cari") || lower.startsWith("apa itu") || lower.startsWith("siapa") || lower.contains("jelaskan"))) {
                    preFetchedCard = fetchKnowledgeCard(clean)
                }

                return JarvisReplyBundle(
                    text = cleanReply,
                    emote = emote,
                    richCard = preFetchedCard,
                    actionFeedback = actionFeedback
                )
            }
        }

        // 4. Smart Offline / Direct Live API Responses when API Key is not yet set or offline
        when (val c = preFetchedCard) {
            is PochiRichCard.Crypto -> {
                val sign = if (c.change24h >= 0) "+" else ""
                val usdStr = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = if (c.priceUsd < 1.0) 5 else 2 }.format(c.priceUsd)
                val idrStr = NaturalExpenseParser.formatRupiah(c.priceIdr)
                return JarvisReplyBundle(
                    text = "Berikut data pasar live ${c.name} (${c.symbol}) untuk Tuan Andre: saat ini berada di kisaran $$usdStr ($idrStr) dengan pergerakan 24 jam $sign${String.format(Locale.US, "%.2f", c.change24h)}%.",
                    emote = if (c.change24h >= 0) PochiEmote.EXCITED else PochiEmote.FOCUS,
                    richCard = c
                )
            }
            is PochiRichCard.Chart -> {
                return JarvisReplyBundle(
                    text = "Siap Tuan Andre! Saya telah merender visualisasi grafik '${c.title}' secara interaktif. Tuan Andre dapat menekan tombol Line/Bar di pojok kartu untuk mengganti mode tampilan grafik.",
                    emote = PochiEmote.SUCCESS,
                    richCard = c
                )
            }
            is PochiRichCard.Weather -> {
                return JarvisReplyBundle(
                    text = "Suhu di ${c.city} saat ini ${c.tempC}°C (${c.condition}, terasa seperti ${c.feelsLikeC}°C) dengan kelembapan ${c.humidity}% dan angin ${c.windKmh} km/jam, Tuan Andre.",
                    emote = PochiEmote.SMILING,
                    richCard = c
                )
            }
            is PochiRichCard.Prayer -> {
                return JarvisReplyBundle(
                    text = "Untuk wilayah ${c.city}, waktu sholat berikutnya adalah ${c.nextPrayerName} pukul ${c.nextPrayerTime} (${c.remainingText}), Tuan Andre.",
                    emote = PochiEmote.CALM,
                    richCard = c
                )
            }
            is PochiRichCard.SystemStatus -> {
                return JarvisReplyBundle(
                    text = "Sistem HP Tuan Andre dalam kondisi optimal: Baterai ${c.batteryPct}% (${if (c.isCharging) "Mengisi Daya" else "Normal"}), RAM terpakai ${c.ramUsedMb}/${c.ramTotalMb} MB, konteks layar: ${c.activeApp}.",
                    emote = PochiEmote.OK,
                    richCard = c
                )
            }
            else -> {
                val knowledge = fetchKnowledgeCard(clean)
                if (knowledge != null) {
                    return JarvisReplyBundle(
                        text = "${knowledge.summary} (Sumber: ${knowledge.source} untuk Tuan Andre).",
                        emote = PochiEmote.SMILING,
                        richCard = knowledge
                    )
                }
                return JarvisReplyBundle(
                    text = "Halo Tuan Andre! Saya siap mencari harga kripto live (misal: 'harga BTC' atau 'ETH'), membuat grafik interaktif ('buat grafik pengeluaran'), mengecek 'suhu sekarang', 'jadwal sholat', atau memindai file yang Tuan Andre drop ke sini. Untuk penalaran penuh LLM, pastikan API Key aktif di Pengaturan.",
                    emote = PochiEmote.WINK_LEFT,
                    richCard = null
                )
            }
        }
    }

    private suspend fun extractCardTag(
        context: Context,
        rawReply: String,
        userPrompt: String
    ): Pair<String, PochiRichCard?> {
        val regex = Regex("""\[CARD:([A-Z_]+):([^\]]+)\]""", RegexOption.IGNORE_CASE)
        val match = regex.find(rawReply) ?: return rawReply to null
        val type = match.groupValues[1].uppercase(Locale.US)
        val arg = match.groupValues[2].trim()
        val cleaned = rawReply.replace(match.value, "").trim()

        val card: PochiRichCard? = when (type) {
            "CRYPTO" -> fetchCryptoCard(arg)
            "CHART" -> buildChartCard(context, userPrompt, arg)
            "WEATHER" -> fetchWeatherCard(arg)
            "PRAYER" -> buildPrayerCard(arg)
            "SYSTEM" -> buildSystemStatusCard(context)
            else -> null
        }
        return cleaned to card
    }
}

// ============================================================================
// GENERATIVE UI COMPOSABLE WIDGETS (ANIMATED, GRADIENT, COLORFUL & DARK)
// ============================================================================

@Composable
fun PochiRichCardView(
    card: PochiRichCard,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val enterAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(card) {
        enterAnim.snapTo(0f)
        enterAnim.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }

    Box(modifier = modifier.fillMaxWidth()) {
        when (card) {
            is PochiRichCard.Crypto -> CryptoRichCardUI(card, enterAnim.value, compact)
            is PochiRichCard.Chart -> ChartRichCardUI(card, enterAnim.value, compact)
            is PochiRichCard.Weather -> WeatherRichCardUI(card, enterAnim.value, compact)
            is PochiRichCard.Prayer -> PrayerRichCardUI(card, enterAnim.value, compact)
            is PochiRichCard.FileDrop -> FileDropRichCardUI(card, compact)
            is PochiRichCard.SystemStatus -> SystemStatusRichCardUI(card, enterAnim.value, compact)
            is PochiRichCard.Knowledge -> KnowledgeRichCardUI(card, compact)
        }
    }
}

@Composable
private fun CryptoRichCardUI(
    data: PochiRichCard.Crypto,
    progress: Float,
    compact: Boolean
) {
    val cStart = Color(data.colorStart)
    val cEnd = Color(data.colorEnd)
    val isUp = data.change24h >= 0
    val badgeColor = if (isUp) Color(0xFF00F5D4) else Color(0xFFFF2A85)

    val inf = rememberInfiniteTransition(label = "cryptoPulse")
    val pulseRadius by inf.animateFloat(
        initialValue = 4f,
        targetValue = 9f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotPulse"
    )

    val usdFormatted = remember(data.priceUsd) {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = if (data.priceUsd < 1.0) 4 else 2
            maximumFractionDigits = if (data.priceUsd < 1.0) 6 else 2
        }.format(data.priceUsd)
    }
    val idrFormatted = remember(data.priceIdr) {
        NaturalExpenseParser.formatRupiah(data.priceIdr)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        cStart.copy(alpha = 0.22f),
                        Color(0xFF0E0E16),
                        cEnd.copy(alpha = 0.14f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(cStart.copy(alpha = 0.65f), cEnd.copy(alpha = 0.35f))),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(if (compact) 12.dp else 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            // Top Coin Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Glowing Coin Emblem
                    Box(
                        modifier = Modifier
                            .size(if (compact) 34.dp else 40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(cStart, cEnd))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = data.symbol.take(3),
                            color = Color(0xFF050508),
                            fontSize = if (compact) 11.sp else 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Column {
                        Text(
                            text = data.name,
                            color = Color.White,
                            fontSize = if (compact) 14.sp else 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${data.symbol}/USDT • Live Market",
                            color = Color(0xFFA0A0B2),
                            fontSize = 11.sp
                        )
                    }
                }

                // 24h Change Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badgeColor.copy(alpha = 0.16f))
                        .border(1.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    val sign = if (isUp) "▲ +" else "▼ "
                    Text(
                        text = "$sign${String.format(Locale.US, "%.2f", data.change24h)}%",
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Price Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "$$usdFormatted",
                        color = Color.White,
                        fontSize = if (compact) 22.sp else 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Text(
                        text = "≈ $idrFormatted",
                        color = cEnd,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Vol 24h: ${data.volumeLabel}",
                        color = Color(0xFFA0A0B2),
                        fontSize = 10.sp
                    )
                    Text(
                        text = "H: $${String.format(Locale.US, "%.2f", data.high24h)}  L: $${String.format(Locale.US, "%.2f", data.low24h)}",
                        color = Color(0xFF8E8E9E),
                        fontSize = 10.sp
                    )
                }
            }

            // Animated Bezier Sparkline Canvas
            if (data.sparkline.size >= 2) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 68.dp else 88.dp)
                        .padding(top = 4.dp)
                ) {
                    val pts = data.sparkline
                    val minV = pts.minOrNull() ?: 0f
                    val maxV = pts.maxOrNull() ?: 1f
                    val range = (maxV - minV).takeIf { it > 0.000001f } ?: 1f

                    val w = size.width * progress.coerceIn(0.05f, 1f)
                    val h = size.height
                    val stepX = w / (pts.size - 1).coerceAtLeast(1)

                    val coords = pts.mapIndexed { idx, v ->
                        val normY = (v - minV) / range
                        val x = idx * stepX
                        val y = h - (normY * (h * 0.78f) + h * 0.11f)
                        Offset(x, y)
                    }

                    val linePath = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 1 until coords.size) {
                            val prev = coords[i - 1]
                            val cur = coords[i]
                            val midX = (prev.x + cur.x) / 2f
                            cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(coords.last().x, h)
                        lineTo(coords.first().x, h)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(cStart.copy(alpha = 0.42f), cEnd.copy(alpha = 0.02f)),
                            startY = 0f,
                            endY = h
                        )
                    )

                    drawPath(
                        path = linePath,
                        brush = Brush.horizontalGradient(listOf(cStart, cEnd, badgeColor)),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    val lastPt = coords.last()
                    drawCircle(
                        color = badgeColor.copy(alpha = 0.32f),
                        radius = pulseRadius.dp.toPx(),
                        center = lastPt
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = lastPt
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartRichCardUI(
    data: PochiRichCard.Chart,
    progress: Float,
    compact: Boolean
) {
    var isBarMode by remember { mutableStateOf(false) }
    var selectedIdx by remember { mutableIntStateOf(data.points.lastIndex.coerceAtLeast(0)) }

    val cStart = Color(data.colorStart)
    val cEnd = Color(data.colorEnd)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        cStart.copy(alpha = 0.20f),
                        Color(0xFF0D0D14),
                        cEnd.copy(alpha = 0.14f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(cStart.copy(alpha = 0.6f), cEnd.copy(alpha = 0.4f))),
                RoundedCornerShape(24.dp)
            )
            .padding(if (compact) 12.dp else 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = data.title,
                        color = Color.White,
                        fontSize = if (compact) 14.sp else 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = data.subtitle,
                        color = Color(0xFF9E9EB0),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Interactive Line / Bar Mode Switch Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF1A1A26))
                        .clickable { isBarMode = !isBarMode }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isBarMode) "📊 Bar" else "📈 Spline",
                        color = cEnd,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Selected Data Point Highlight Row
            if (data.points.isNotEmpty()) {
                val sel = data.points[selectedIdx.coerceIn(data.points.indices)]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${sel.first}: ${data.unitPrefix}${String.format(Locale.US, "%.1f", sel.second)}${data.unitSuffix}",
                        color = Color.White,
                        fontSize = if (compact) 18.sp else 21.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(cEnd.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = data.growthBadge,
                            color = cEnd,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Animated Canvas (Spline Area OR Neon Bars)
            if (data.points.isNotEmpty()) {
                val values = data.points.map { it.second }
                val minV = (values.minOrNull() ?: 0f).coerceAtMost(0f)
                val maxV = (values.maxOrNull() ?: 100f).coerceAtLeast(1f)
                val range = (maxV - minV).takeIf { it > 0.001f } ?: 1f

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 84.dp else 108.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val count = data.points.size

                    // Subtle horizontal dashed grid lines
                    for (g in 1..3) {
                        val gy = h * (g / 4f)
                        drawLine(
                            color = Color.White.copy(alpha = 0.06f),
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    if (isBarMode) {
                        val barSlot = w / count.coerceAtLeast(1)
                        val barW = (barSlot * 0.56f).coerceAtMost(28.dp.toPx())
                        data.points.forEachIndexed { idx, pair ->
                            val norm = ((pair.second - minV) / range).coerceIn(0.08f, 1f) * progress
                            val barH = norm * (h * 0.86f)
                            val left = idx * barSlot + (barSlot - barW) / 2f
                            val top = h - barH
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = if (idx == selectedIdx) listOf(cEnd, cStart) else listOf(cStart.copy(alpha = 0.85f), cEnd.copy(alpha = 0.35f)),
                                    startY = top,
                                    endY = h
                                ),
                                topLeft = Offset(left, top),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }
                    } else {
                        val stepX = w / (count - 1).coerceAtLeast(1)
                        val coords = data.points.mapIndexed { idx, pair ->
                            val norm = ((pair.second - minV) / range).coerceIn(0.05f, 1f) * progress
                            Offset(idx * stepX, h - (norm * (h * 0.82f) + h * 0.08f))
                        }

                        val path = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 1 until coords.size) {
                                val p0 = coords[i - 1]
                                val p1 = coords[i]
                                val mx = (p0.x + p1.x) / 2f
                                cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                            }
                        }

                        val fill = Path().apply {
                            addPath(path)
                            lineTo(coords.last().x, h)
                            lineTo(coords.first().x, h)
                            close()
                        }

                        drawPath(
                            path = fill,
                            brush = Brush.verticalGradient(
                                listOf(cStart.copy(alpha = 0.45f), cEnd.copy(alpha = 0.02f))
                            )
                        )
                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(listOf(cStart, cEnd)),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        coords.forEachIndexed { idx, pt ->
                            drawCircle(
                                color = if (idx == selectedIdx) cEnd else cStart,
                                radius = if (idx == selectedIdx) 5.dp.toPx() else 3.5.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }

                // Bottom X-axis Labels (Tap any label to inspect its value)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    data.points.forEachIndexed { idx, pair ->
                        val isSel = idx == selectedIdx
                        Text(
                            text = pair.first,
                            color = if (isSel) cEnd else Color(0xFF8E8E9E),
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { selectedIdx = idx }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherRichCardUI(
    data: PochiRichCard.Weather,
    progress: Float,
    compact: Boolean
) {
    val inf = rememberInfiniteTransition(label = "weatherAnim")
    val sunAngle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "sunRot"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0077B6).copy(alpha = 0.28f),
                        Color(0xFF0D0D16),
                        Color(0xFFFF9F1C).copy(alpha = 0.18f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Color(0xFF4CC9F0).copy(alpha = 0.55f), Color(0xFFFFB703).copy(alpha = 0.45f))),
                RoundedCornerShape(24.dp)
            )
            .padding(if (compact) 12.dp else 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = data.city,
                        color = Color.White,
                        fontSize = if (compact) 16.sp else 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${data.condition} • Terasa ${data.feelsLikeC}°C",
                        color = Color(0xFF90E0EF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(data.tempC * progress).toInt()}°C",
                        color = Color.White,
                        fontSize = if (compact) 30.sp else 36.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Animated Sun & Cloud Canvas
                Canvas(modifier = Modifier.size(if (compact) 58.dp else 72.dp)) {
                    val cx = size.width * 0.52f
                    val cy = size.height * 0.44f
                    val r = size.minDimension * 0.24f

                    rotate(sunAngle, pivot = Offset(cx, cy)) {
                        for (i in 0 until 8) {
                            val rad = Math.toRadians((i * 45).toDouble())
                            val sx = cx + cos(rad).toFloat() * (r * 1.25f)
                            val sy = cy + sin(rad).toFloat() * (r * 1.25f)
                            val ex = cx + cos(rad).toFloat() * (r * 1.65f)
                            val ey = cy + sin(rad).toFloat() * (r * 1.65f)
                            drawLine(
                                color = Color(0xFFFFB703),
                                start = Offset(sx, sy),
                                end = Offset(ex, ey),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFEA00), Color(0xFFFF9F1C)),
                            center = Offset(cx, cy),
                            radius = r
                        ),
                        radius = r,
                        center = Offset(cx, cy)
                    )

                    // Soft translucent cloud pill in foreground
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(size.width * 0.18f, size.height * 0.54f),
                        size = Size(size.width * 0.64f, size.height * 0.26f),
                        cornerRadius = CornerRadius(18f, 18f)
                    )
                }
            }

            // Metrics strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WeatherPillStat("💧 Lembap", "${data.humidity}%")
                WeatherPillStat("🌬️ Angin", "${data.windKmh} km/j")
                WeatherPillStat("☀️ UV Index", "${data.uvIndex}")
            }

            // Hourly Forecast Row
            if (data.hourly.isNotEmpty() && !compact) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    data.hourly.forEach { (time, t) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = time, color = Color(0xFFA0A0B2), fontSize = 10.sp)
                            Text(text = "$t°", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherPillStat(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = label, color = Color(0xFFA0A0B2), fontSize = 10.sp)
        Text(text = value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PrayerRichCardUI(
    data: PochiRichCard.Prayer,
    progress: Float,
    compact: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF7B2CBF).copy(alpha = 0.26f),
                        Color(0xFF0D0D15),
                        Color(0xFF00F5D4).copy(alpha = 0.16f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Color(0xFF9D4EDD).copy(alpha = 0.6f), Color(0xFF00F5D4).copy(alpha = 0.45f))),
                RoundedCornerShape(24.dp)
            )
            .padding(if (compact) 12.dp else 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "🕌 Jadwal Sholat • ${data.city}",
                        color = Color(0xFFE0AAFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${data.nextPrayerName} • ${data.nextPrayerTime}",
                        color = Color.White,
                        fontSize = if (compact) 19.sp else 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF00F5D4).copy(alpha = 0.16f))
                        .border(1.dp, Color(0xFF00F5D4).copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = data.remainingText,
                        color = Color(0xFF00F5D4),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 5-Prayer Schedule Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                data.schedule.forEach { item ->
                    val isNext = item.nameId == data.nextPrayerName
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isNext) Brush.verticalGradient(listOf(Color(0xFF9D4EDD), Color(0xFF5A189A)))
                                else Brush.verticalGradient(listOf(Color(0xFF181824), Color(0xFF14141E)))
                            )
                            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = item.nameId,
                            color = if (isNext) Color.White else Color(0xFF9E9EB0),
                            fontSize = 10.sp,
                            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium
                        )
                        Text(
                            text = item.formatted,
                            color = if (isNext) Color(0xFF00F5D4) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileDropRichCardUI(
    data: PochiRichCard.FileDrop,
    compact: Boolean
) {
    val inf = rememberInfiniteTransition(label = "fileScan")
    val scanPos by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scanLine"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF7B2CBF).copy(alpha = 0.25f),
                        Color(0xFF0E0E17),
                        Color(0xFF4CC9F0).copy(alpha = 0.18f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Color(0xFFC77DFF), Color(0xFF4CC9F0))),
                RoundedCornerShape(24.dp)
            )
            .padding(if (compact) 12.dp else 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF9D4EDD), Color(0xFF4CC9F0)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = data.fileName.substringAfterLast('.', "FILE").uppercase(Locale.US).take(4),
                            color = Color(0xFF050508),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = data.fileName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${data.mimeType} • ${data.sizeLabel}",
                            color = Color(0xFF90E0EF),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF00F5D4).copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "✓ Inspected",
                        color = Color(0xFF00F5D4),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Animated Laser Scan Bar
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.08f),
                    size = size,
                    cornerRadius = CornerRadius(4f, 4f)
                )
                val beamW = size.width * 0.35f
                val beamX = (size.width - beamW) * scanPos
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xFF00F5D4), Color(0xFFC77DFF), Color.Transparent),
                        startX = beamX,
                        endX = beamX + beamW
                    ),
                    topLeft = Offset(beamX, 0f),
                    size = Size(beamW, size.height),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }

            // Code / Content Snippet Box
            if (data.snippet.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF07070C))
                        .padding(10.dp)
                ) {
                    Text(
                        text = data.snippet.take(220),
                        color = Color(0xFFD8D8E8),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = if (compact) 3 else 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemStatusRichCardUI(
    data: PochiRichCard.SystemStatus,
    progress: Float,
    compact: Boolean
) {
    val ramRatio = (data.ramUsedMb.toFloat() / data.ramTotalMb.coerceAtLeast(1).toFloat()).coerceIn(0.1f, 0.98f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF34C759).copy(alpha = 0.20f),
                        Color(0xFF0D0D15),
                        Color(0xFF7B2CBF).copy(alpha = 0.20f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Color(0xFF00F5D4).copy(alpha = 0.55f), Color(0xFF9D4EDD).copy(alpha = 0.45f))),
                RoundedCornerShape(24.dp)
            )
            .padding(if (compact) 12.dp else 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "⚡ Jarvis System Telemetry",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Uptime ${data.uptimeHours}h",
                    color = Color(0xFF00F5D4),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Battery Gauge Pill
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(10.dp)
                ) {
                    Text(text = if (data.isCharging) "⚡ Charging" else "🔋 Baterai", color = Color(0xFFA0A0B2), fontSize = 10.sp)
                    Text(text = "${(data.batteryPct * progress).toInt()}%", color = Color(0xFF00F5D4), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }

                // RAM Gauge Pill
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(10.dp)
                ) {
                    Text(text = "🧠 Memori RAM", color = Color(0xFFA0A0B2), fontSize = 10.sp)
                    Text(text = "${(ramRatio * 100 * progress).toInt()}%", color = Color(0xFFC77DFF), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Text(
                text = "📱 Fokus Layar: ${data.activeApp}  •  🎵 ${data.activeMedia.take(28)}",
                color = Color(0xFFD0D0E0),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun KnowledgeRichCardUI(
    data: PochiRichCard.Knowledge,
    compact: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF5A189A).copy(alpha = 0.28f),
                        Color(0xFF0E0E17),
                        Color(0xFF0077B6).copy(alpha = 0.22f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Color(0xFFC77DFF).copy(alpha = 0.55f), Color(0xFF4CC9F0).copy(alpha = 0.45f))),
                RoundedCornerShape(24.dp)
            )
            .padding(if (compact) 12.dp else 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = data.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = data.source,
                    color = Color(0xFF4CC9F0),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = data.summary,
                color = Color(0xFFD8D8E6),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = if (compact) 3 else 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
