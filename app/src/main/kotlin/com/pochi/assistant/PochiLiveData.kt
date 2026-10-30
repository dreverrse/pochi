package com.pochi.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * PochiLiveData — data sungguhan untuk cuaca & jadwal salat
 * (Tahap 12: semua fungsi bekerja).
 *
 * Sebelumnya cuaca dan salat hanya simulasi lokal (rumus jam &
 * offset menit per kota). Kini:
 * - Cuaca: Open-Meteo (gratis, tanpa API key) per koordinat kota.
 * - Salat: Aladhan timingsByCity, metode 20 (Kemenag RI).
 * Hasil disimpan di StateFlow + cache SharedPreferences agar tetap
 * tampil saat offline. Kegagalan jaringan tidak pernah fatal —
 * pemanggil memakai data cache/simulasi sebagai cadangan.
 */
data class LiveWeather(
    val city: String,
    val tempC: Int,
    val conditionId: String,
    val conditionEn: String,
    val humidityPercent: Int,
    val windKmh: Int,
    val uvIndex: Int,
    val hourlyTemps: List<Float>,
    val dailyMax: List<Int>,
    val dailyMin: List<Int>,
    val fetchedAtMs: Long
)

object PochiLiveData {

    private const val PREFS_NAME = "pochi_live_data"
    private const val KEY_WEATHER_JSON = "weather_json"
    private const val KEY_PRAYER_JSON = "prayer_json"
    private const val KEY_PRAYER_CITY = "prayer_city"
    private const val KEY_PRAYER_DATE = "prayer_date"

    val cityCoords: Map<String, Pair<Double, Double>> = mapOf(
        "Jakarta" to (-6.2088 to 106.8456),
        "Bandung" to (-6.9175 to 107.6191),
        "Semarang" to (-6.9667 to 110.4167),
        "Yogyakarta" to (-7.7956 to 110.3695),
        "Surabaya" to (-7.2575 to 112.7521),
        "Medan" to (3.5952 to 98.6722),
        "Makassar" to (-5.1477 to 119.4327),
        "Denpasar" to (-8.6705 to 115.2126)
    )

    private val _weather = MutableStateFlow<LiveWeather?>(null)
    val weatherFlow: StateFlow<LiveWeather?> = _weather

    private val _prayer = MutableStateFlow<List<PrayerTimeItem>?>(null)
    val prayerFlow: StateFlow<List<PrayerTimeItem>?> = _prayer

    @Volatile
    private var prayerCity: String = ""

    @Volatile
    private var prayerDate: String = ""

    @Volatile
    private var weatherCity: String = ""

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Jadwal salat live untuk [city] bila sudah terambil hari ini. */
    fun prayerFor(city: String): List<PrayerTimeItem>? {
        return if (prayerCity == city && prayerDate == todayKey()) {
            _prayer.value
        } else {
            null
        }
    }

    /** Muat cache dari disk (sekali, saat island pertama dibuat). */
    fun loadCache(context: Context) {
        if (_weather.value != null || _prayer.value != null) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_WEATHER_JSON, null)?.let { json ->
            parseWeatherJson(json)?.let {
                _weather.value = it
                weatherCity = it.city
            }
        }
        val cachedCity = prefs.getString(KEY_PRAYER_CITY, "") ?: ""
        val cachedDate = prefs.getString(KEY_PRAYER_DATE, "") ?: ""
        prefs.getString(KEY_PRAYER_JSON, null)?.let { json ->
            parsePrayerJson(json)?.let { list ->
                _prayer.value = list
                prayerCity = cachedCity
                prayerDate = cachedDate
            }
        }
    }

    /**
     * Segarkan cuaca (bila basi >30 mnt / kota berganti) dan jadwal
     * salat (bila hari/kota berganti). Aman dipanggil berulang.
     */
    suspend fun refresh(context: Context, city: String, force: Boolean = false) {
        loadCache(context)
        val weatherStale = force || _weather.value.let {
            it == null || it.city != city ||
                System.currentTimeMillis() - it.fetchedAtMs > 30 * 60 * 1000L
        }
        val prayerStale = prayerFor(city) == null
        if (!weatherStale && !prayerStale) return
        withContext(Dispatchers.IO) {
            if (weatherStale) fetchWeather(context, city)
            if (prayerStale) fetchPrayer(context, city)
        }
    }

    private fun httpGet(urlText: String): String? = runCatching {
        val conn = URL(urlText).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "PochiAssistant/1.0")
        try {
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    // ---------------- Cuaca (Open-Meteo) ----------------

    private fun fetchWeather(context: Context, city: String) {
        val (lat, lon) = cityCoords[city] ?: cityCoords["Jakarta"]!!
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&hourly=temperature_2m" +
            "&daily=temperature_2m_max,temperature_2m_min,uv_index_max" +
            "&timezone=Asia%2FJakarta&forecast_days=7"
        val body = httpGet(url) ?: return
        val live = parseWeatherJson(body, city) ?: return
        _weather.value = live
        weatherCity = city
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_WEATHER_JSON, body).apply()
    }

    private fun parseWeatherJson(
        body: String,
        cityOverride: String? = null
    ): LiveWeather? = runCatching {
        val root = JSONObject(body)
        val current = root.getJSONObject("current")
        val code = current.optInt("weather_code", 1)
        val (condId, condEn) = wmoCondition(code)
        val hourlyArr = root.getJSONObject("hourly").getJSONArray("temperature_2m")
        val hourly = buildList {
            val n = minOf(hourlyArr.length(), 24)
            for (i in 0 until n) add(hourlyArr.getDouble(i).toFloat())
        }
        val daily = root.getJSONObject("daily")
        val maxArr = daily.getJSONArray("temperature_2m_max")
        val minArr = daily.getJSONArray("temperature_2m_min")
        val uvArr = daily.optJSONArray("uv_index_max")
        val dailyMax = buildList {
            for (i in 0 until maxArr.length()) add(maxArr.getDouble(i).toInt())
        }
        val dailyMin = buildList {
            for (i in 0 until minArr.length()) add(minArr.getDouble(i).toInt())
        }
        LiveWeather(
            city = cityOverride ?: weatherCity.ifBlank { "Jakarta" },
            tempC = current.getDouble("temperature_2m").toInt(),
            conditionId = condId,
            conditionEn = condEn,
            humidityPercent = current.optInt("relative_humidity_2m", 0),
            windKmh = current.optDouble("wind_speed_10m", 0.0).toInt(),
            uvIndex = if (uvArr != null && uvArr.length() > 0) {
                uvArr.getDouble(0).toInt()
            } else {
                0
            },
            hourlyTemps = hourly,
            dailyMax = dailyMax,
            dailyMin = dailyMin,
            fetchedAtMs = System.currentTimeMillis()
        )
    }.getOrNull()

    private fun wmoCondition(code: Int): Pair<String, String> = when (code) {
        0 -> "Cerah" to "Clear"
        1 -> "Cerah Berawan" to "Partly Cloudy"
        2 -> "Berawan" to "Cloudy"
        3 -> "Mendung" to "Overcast"
        45, 48 -> "Berkabut" to "Foggy"
        51, 53, 55 -> "Gerimis" to "Drizzle"
        56, 57 -> "Gerimis Beku" to "Freezing Drizzle"
        61 -> "Hujan Ringan" to "Light Rain"
        63 -> "Hujan" to "Rain"
        65 -> "Hujan Lebat" to "Heavy Rain"
        66, 67 -> "Hujan Beku" to "Freezing Rain"
        71, 73, 75, 77 -> "Salju" to "Snow"
        80 -> "Hujan Lokal" to "Rain Showers"
        81 -> "Hujan Lokal" to "Rain Showers"
        82 -> "Hujan Badai Lokal" to "Violent Showers"
        95 -> "Badai Petir" to "Thunderstorm"
        96, 99 -> "Badai Petir & Hujan Es" to "Thunderstorm & Hail"
        else -> "Berawan" to "Cloudy"
    }

    // ---------------- Salat (Aladhan, metode Kemenag) ----------------

    private fun fetchPrayer(context: Context, city: String) {
        val datePath = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
        val cityQ = URLEncoder.encode(city, "UTF-8")
        val url = "https://api.aladhan.com/v1/timingsByCity/$datePath" +
            "?city=$cityQ&country=Indonesia&method=20"
        val body = httpGet(url) ?: return
        val list = parsePrayerJson(body) ?: return
        _prayer.value = list
        prayerCity = city
        prayerDate = todayKey()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PRAYER_JSON, body)
            .putString(KEY_PRAYER_CITY, city)
            .putString(KEY_PRAYER_DATE, prayerDate)
            .apply()
    }

    private fun parsePrayerJson(body: String): List<PrayerTimeItem>? = runCatching {
        val timings = JSONObject(body)
            .getJSONObject("data")
            .getJSONObject("timings")
        fun item(nameId: String, nameEn: String, key: String): PrayerTimeItem {
            val raw = timings.getString(key).substringBefore(" ").trim()
            val parts = raw.split(":")
            return PrayerTimeItem(
                nameId = nameId,
                nameEn = nameEn,
                hour = parts[0].toInt(),
                minute = parts[1].toInt()
            )
        }
        listOf(
            item("Subuh", "Fajr", "Fajr"),
            item("Dzuhur", "Dhuhr", "Dhuhr"),
            item("Ashar", "Asr", "Asr"),
            item("Maghrib", "Maghrib", "Maghrib"),
            item("Isya", "Isha", "Isha")
        )
    }.getOrNull()

    /** Jam saat ini sebagai total menit (untuk hitung salat berikut). */
    fun nowMinutes(): Int {
        val now = Calendar.getInstance()
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }
}
