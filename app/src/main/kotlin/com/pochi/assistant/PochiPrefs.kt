package com.pochi.assistant

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

enum class PochiLang(val code: String, val label: String, val shortBadge: String) {
    ID("id", "Bahasa Indonesia", "ID"),
    EN("en", "English", "EN")
}

data class TodayTask(
    val id: String,
    val title: String,
    val timeLabel: String = "",
    val hour24: Int = -1,
    val minute: Int = -1,
    val dayIndex: Int = 0, // 0 = Today, -3..+3 in weekly strip
    val isDone: Boolean = false,
    val isOverdue: Boolean = false,
    val isHighPriority: Boolean = false
) {
    fun isCurrentlyOverdue(): Boolean {
        if (isDone) return false
        if (dayIndex < 0) return true
        if (dayIndex > 0) return false
        if (hour24 in 0..23 && minute in 0..59) {
            val now = Calendar.getInstance()
            val nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val taskMins = hour24 * 60 + minute
            return nowMins > taskMins
        }
        return isOverdue
    }
}

object NaturalTaskParser {
    // Splits multi-clause input in both English and Indonesian:
    // e.g. "review PR, then 2pm team meeting" or "cek PR, lalu jam 2 siang rapat tim"
    private val splitClauseRegex = Regex(
        """(?:\s*,\s*then\s+|\s+then\s+|\s*,\s*and\s+|\s*,\s*lalu\s+|\s+lalu\s+|\s*,\s*terus\s+|\s+terus\s+|\s*,\s*kemudian\s+|\s+kemudian\s+|\s*;\s*|\s*,\s*(?=(?:jam\s+|pukul\s+|at\s+)?\d{1,2}(?:[:.]\d{2})?\s*(?:am|pm|pagi|siang|sore|malam)\b))""",
        RegexOption.IGNORE_CASE
    )

    // Matches relative time: "in 30m", "in 2 hours", "30 menit lagi", "2 jam lagi"
    private val relativeTimeRegex = Regex(
        """\b(?:in\s+(\d{1,3})\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours)|(\d{1,3})\s*(menit|mnt|jam)\s+lagi)\b""",
        RegexOption.IGNORE_CASE
    )

    // Matches clock time: "2pm", "14:30", "at 9:15am", "jam 2 siang", "pukul 19.30 malam"
    private val clockTimeRegex = Regex(
        """\b(?:at\s+|jam\s+|pukul\s+)?(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm|pagi|siang|sore|malam)?\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses natural language input (Indonesian or English) into one or more scheduled [TodayTask]s.
     */
    fun parse(rawInput: String, activeDayIndex: Int = 0): List<TodayTask> {
        val cleaned = rawInput.trim()
        if (cleaned.isEmpty()) return emptyList()

        val clauses = cleaned.split(splitClauseRegex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val baseId = System.currentTimeMillis()
        return clauses.mapIndexed { idx, clause ->
            var timeFormatted = ""
            var parsedHour24 = -1
            var parsedMinute = -1
            var workingTitle = clause

            // Detect priority markers ("!", "urgent", "penting")
            val isPriority = workingTitle.contains("!") ||
                workingTitle.contains("urgent", ignoreCase = true) ||
                workingTitle.contains("penting", ignoreCase = true)

            // 1. Check relative time ("in 30m", "30 menit lagi")
            val relMatch = relativeTimeRegex.find(workingTitle)
            if (relMatch != null) {
                val amountEn = relMatch.groupValues[1].toIntOrNull()
                val unitEn = relMatch.groupValues[2].lowercase(Locale.US)
                val amountId = relMatch.groupValues[3].toIntOrNull()
                val unitId = relMatch.groupValues[4].lowercase(Locale.US)

                val deltaMinutes = when {
                    amountEn != null -> if (unitEn.startsWith("h")) amountEn * 60 else amountEn
                    amountId != null -> if (unitId.startsWith("j")) amountId * 60 else amountId
                    else -> 0
                }
                if (deltaMinutes > 0) {
                    val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, deltaMinutes) }
                    parsedHour24 = cal.get(Calendar.HOUR_OF_DAY)
                    parsedMinute = cal.get(Calendar.MINUTE)
                    timeFormatted = format12Hour(parsedHour24, parsedMinute)
                    workingTitle = workingTitle.removeRange(relMatch.range)
                }
            } else {
                // 2. Check clock time ("2pm", "jam 2 siang", "14:30")
                val clockMatch = clockTimeRegex.find(workingTitle)
                if (clockMatch != null) {
                    val rawHour = clockMatch.groupValues[1].toIntOrNull()
                    val rawMin = clockMatch.groupValues[2].toIntOrNull() ?: 0
                    val meridiem = clockMatch.groupValues[3].lowercase(Locale.US)
                    val matchedLower = clockMatch.value.lowercase(Locale.US)
                    val hasExplicitMarker = meridiem.isNotEmpty() ||
                        clockMatch.groupValues[2].isNotEmpty() ||
                        matchedLower.startsWith("at ") ||
                        matchedLower.startsWith("jam ") ||
                        matchedLower.startsWith("pukul ")

                    if (rawHour != null && rawHour in 0..23 && rawMin in 0..59 && hasExplicitMarker) {
                        val h24 = when {
                            meridiem in setOf("pm", "siang", "sore") && rawHour in 1..11 -> rawHour + 12
                            meridiem == "malam" && rawHour in 1..11 -> if (rawHour == 12) 0 else rawHour + 12
                            meridiem in setOf("am", "pagi") && rawHour == 12 -> 0
                            else -> rawHour
                        }
                        parsedHour24 = h24
                        parsedMinute = rawMin
                        timeFormatted = format12Hour(h24, rawMin)
                        workingTitle = workingTitle.removeRange(clockMatch.range)
                    }
                }
            }

            workingTitle = workingTitle
                .replace("!", "")
                .replace(Regex("""\s+"""), " ")
                .trim(' ', ',', '-', ':')

            if (workingTitle.isBlank()) {
                workingTitle = clause
            } else {
                workingTitle = workingTitle.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            }

            val tempTask = TodayTask(
                id = "${baseId}_$idx",
                title = workingTitle,
                timeLabel = timeFormatted,
                hour24 = parsedHour24,
                minute = parsedMinute,
                dayIndex = activeDayIndex,
                isDone = false,
                isOverdue = false,
                isHighPriority = isPriority
            )
            tempTask.copy(isOverdue = tempTask.isCurrentlyOverdue())
        }
    }

    private fun format12Hour(hour24: Int, minute: Int): String {
        val amPm = if (hour24 >= 12) "PM" else "AM"
        val h12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return String.format(Locale.US, "%d:%02d %s", h12, minute, amPm)
    }
}

data class ExpenseEntry(
    val id: String,
    val title: String,
    val amountRp: Long,
    val category: String,
    val timeLabel: String
)

object NaturalExpenseParser {
    private val amountRegex = Regex(
        """(\d+(?:[.,]\d{3})*|\d+(?:[.,]\d+)?)\s*(rb|ribu|k|jt|juta)?\b""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawInput: String): ExpenseEntry? {
        val cleaned = rawInput.trim()
        if (cleaned.isEmpty()) return null
        val matches = amountRegex.findAll(cleaned).toList()
        val match = matches.lastOrNull() ?: return null

        val rawNum = match.groupValues[1].replace(".", "").replace(",", ".")
        val suffix = match.groupValues[2].lowercase(Locale.US)
        val baseVal = rawNum.toDoubleOrNull() ?: return null

        val multiplier = when (suffix) {
            "rb", "ribu", "k" -> 1_000.0
            "jt", "juta" -> 1_000_000.0
            else -> if (baseVal < 500) 1_000.0 else 1.0
        }
        val finalAmount = (baseVal * multiplier).toLong().coerceAtLeast(100L)

        var title = cleaned.removeRange(match.range)
            .replace(Regex("""^(?:beli|bayar|buat|untuk|catat|spend|pay)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', ',', ':')

        if (title.isBlank()) {
            title = "Pengeluaran"
        } else {
            title = title.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }

        val lower = title.lowercase(Locale.getDefault())
        val category = when {
            lower.contains("makan") || lower.contains("sarapan") || lower.contains("nasi") ||
                lower.contains("ayam") || lower.contains("bakso") || lower.contains("lunch") ||
                lower.contains("dinner") -> "Makan"
            lower.contains("kopi") || lower.contains("es ") || lower.contains("jajan") ||
                lower.contains("snack") || lower.contains("teh") || lower.contains("coffee") -> "Jajan"
            lower.contains("bensin") || lower.contains("parkir") || lower.contains("gojek") ||
                lower.contains("grab") || lower.contains("tol") || lower.contains("kereta") -> "Transport"
            lower.contains("listrik") || lower.contains("pulsa") || lower.contains("kuota") ||
                lower.contains("wifi") || lower.contains("air") || lower.contains("tagihan") -> "Tagihan"
            else -> "Belanja"
        }

        val now = Calendar.getInstance()
        val timeStr = String.format(
            Locale.US,
            "%02d:%02d",
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE)
        )

        return ExpenseEntry(
            id = System.currentTimeMillis().toString(),
            title = title,
            amountRp = finalAmount,
            category = category,
            timeLabel = timeStr
        )
    }

    fun formatRupiah(amount: Long): String {
        val str = amount.coerceAtLeast(0L).toString()
        val sb = StringBuilder()
        for (i in str.indices) {
            if (i > 0 && (str.length - i) % 3 == 0) {
                sb.append('.')
            }
            sb.append(str[i])
        }
        return "Rp $sb"
    }
}

data class PrayerTimeItem(
    val nameId: String,
    val nameEn: String,
    val hour: Int,
    val minute: Int
) {
    val formatted: String
        get() = String.format(Locale.US, "%02d:%02d", hour, minute)
    val totalMinutes: Int
        get() = hour * 60 + minute
}

data class NextPrayerSummary(
    val prayer: PrayerTimeItem,
    val remainingTextId: String,
    val remainingTextEn: String
)

object PrayerScheduleHelper {
    private val cityMinuteOffsets = mapOf(
        "Jakarta" to 0,
        "Bandung" to -3,
        "Semarang" to -14,
        "Yogyakarta" to -14,
        "Surabaya" to -24,
        "Medan" to +33,
        "Makassar" to -50,
        "Denpasar" to -33
    )

    val supportedCities = listOf(
        "Jakarta", "Bandung", "Surabaya", "Yogyakarta", "Semarang", "Medan", "Makassar", "Denpasar"
    )

    fun getScheduleForCity(city: String): List<PrayerTimeItem> {
        val shift = cityMinuteOffsets[city] ?: 0
        fun shifted(nameId: String, nameEn: String, baseH: Int, baseM: Int): PrayerTimeItem {
            val total = (baseH * 60 + baseM + shift + 1440) % 1440
            return PrayerTimeItem(nameId, nameEn, total / 60, total % 60)
        }
        return listOf(
            shifted("Subuh", "Fajr", 4, 38),
            shifted("Dzuhur", "Dhuhr", 11, 56),
            shifted("Ashar", "Asr", 15, 14),
            shifted("Maghrib", "Maghrib", 17, 57),
            shifted("Isya", "Isha", 19, 8)
        )
    }

    fun getNextPrayer(city: String): NextPrayerSummary {
        val schedule = getScheduleForCity(city)
        val now = Calendar.getInstance()
        val nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val upcoming = schedule.firstOrNull { it.totalMinutes > nowMins } ?: schedule.first()
        val diffMins = if (upcoming.totalMinutes > nowMins) {
            upcoming.totalMinutes - nowMins
        } else {
            (1440 - nowMins) + upcoming.totalMinutes
        }
        val h = diffMins / 60
        val m = diffMins % 60
        val remId = if (h > 0) "${h}j ${m}m lagi" else "${m}m lagi"
        val remEn = if (h > 0) "in ${h}h ${m}m" else "in ${m}m"

        return NextPrayerSummary(
            prayer = upcoming,
            remainingTextId = remId,
            remainingTextEn = remEn
        )
    }
}

data class WeatherSnapshot(
    val city: String,
    val tempC: Int,
    val conditionId: String,
    val conditionEn: String,
    val humidityPercent: Int,
    val windKmh: Int,
    val adviceId: String,
    val adviceEn: String
)

object WeatherHelper {
    fun getSnapshot(city: String, customTempC: Int? = null): WeatherSnapshot {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val baseCityTemp = when (city) {
            "Bandung" -> 24
            "Surabaya" -> 32
            "Yogyakarta" -> 28
            "Medan" -> 30
            "Makassar" -> 31
            "Denpasar" -> 29
            else -> 30
        }
        val timeDelta = when (hour) {
            in 0..5 -> -4
            in 6..9 -> -2
            in 10..15 -> +2
            in 16..18 -> 0
            else -> -3
        }
        val temp = customTempC ?: (baseCityTemp + timeDelta)
        val (condId, condEn) = when (hour) {
            in 5..10 -> "Cerah Pagi" to "Sunny Morning"
            in 11..15 -> "Cerah Berawan" to "Partly Cloudy"
            in 16..18 -> "Berawan Sore" to "Cloudy Breeze"
            else -> "Sejuk Malam" to "Clear Night"
        }
        val (advId, advEn) = when {
            temp >= 31 -> "Cuaca cukup terik, perbanyak minum air putih & gunakan topi/payung." to "Warm weather today, stay hydrated & wear sun protection."
            hour in 15..19 -> "Sore agak berawan, siapkan jas hujan/payung bila bepergian." to "Breezy afternoon, keep an umbrella handy if commuting."
            hour >= 21 || hour < 5 -> "Udara malam sejuk, waktu yang pas untuk istirahat lebih awal." to "Cool night air, great time to wind down and rest."
            else -> "Cuaca nyaman untuk beraktivitas dan menyelesaikan rencana hari ini." to "Comfortable weather for outdoor activities and productivity."
        }
        return WeatherSnapshot(
            city = city,
            tempC = temp,
            conditionId = condId,
            conditionEn = condEn,
            humidityPercent = if (hour in 11..15) 64 else 76,
            windKmh = 12,
            adviceId = advId,
            adviceEn = advEn
        )
    }
}

data class SmartAssistantBanner(
    val title: String,
    val subtitle: String,
    val shortPillText: String,
    val emote: PochiEmote
)

object SmartLifeAssistant {
    fun currentBanner(
        lang: PochiLang,
        city: String,
        waterGlasses: Int,
        remainingTasks: Int
    ): SmartAssistantBanner {
        val isId = lang == PochiLang.ID
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val nextPrayer = PrayerScheduleHelper.getNextPrayer(city)
        val weather = WeatherHelper.getSnapshot(city)

        return when (hour) {
            in 4..5 -> SmartAssistantBanner(
                title = if (isId) "Subuh ${nextPrayer.prayer.formatted} • Awali Hari" else "Fajr ${nextPrayer.prayer.formatted} • Fresh Start",
                subtitle = if (isId) "Minum segelas air hangat & cek rencana hari ini." else "Drink a glass of water & review today's plan.",
                shortPillText = if (isId) "Subuh ${nextPrayer.prayer.formatted}" else "Fajr ${nextPrayer.prayer.formatted}",
                emote = PochiEmote.CALM
            )
            in 6..9 -> SmartAssistantBanner(
                title = if (isId) "Jangan Lupa Sarapan Pagi 🥣" else "Don't Forget Breakfast 🥣",
                subtitle = if (isId) "${weather.city} ${weather.tempC}°C • Isi energi dulu sebelum mulai aktivitas!" else "${weather.city} ${weather.tempC}°C • Fuel up before starting your day!",
                shortPillText = if (isId) "Ingat Sarapan ☀️" else "Breakfast ☀️",
                emote = PochiEmote.GOOD_MORNING
            )
            in 10..11 -> SmartAssistantBanner(
                title = if (isId) "Waktunya Fokus Produktif 💪" else "Peak Productivity Time 💪",
                subtitle = if (isId) "Ada $remainingTasks tugas aktif • Hidrasi: $waterGlasses/8 gelas air." else "$remainingTasks active tasks • Hydration: $waterGlasses/8 glasses.",
                shortPillText = if (isId) "$remainingTasks Tugas • ${weather.tempC}°C" else "$remainingTasks Tasks • ${weather.tempC}°C",
                emote = PochiEmote.SMILING
            )
            in 12..13 -> SmartAssistantBanner(
                title = if (isId) "Makan Siang & Sholat Dzuhur 🕌" else "Lunch & Dhuhr Break 🕌",
                subtitle = if (isId) "Istirahatkan mata sejenak, makan siang & jaga hidrasi ($waterGlasses/8)." else "Rest your eyes, grab lunch & stay hydrated ($waterGlasses/8).",
                shortPillText = if (isId) "Rehat & Dzuhur" else "Lunch & Dhuhr",
                emote = PochiEmote.HAPPY
            )
            in 14..16 -> SmartAssistantBanner(
                title = if (isId) "Sholat Ashar & Minum Air 💧" else "Asr Time & Hydration Check 💧",
                subtitle = if (isId) "${nextPrayer.prayer.nameId} ${nextPrayer.prayer.formatted} (${nextPrayer.remainingTextId}) • Air: $waterGlasses/8 gelas." else "${nextPrayer.prayer.nameEn} ${nextPrayer.prayer.formatted} (${nextPrayer.remainingTextEn}) • Water: $waterGlasses/8.",
                shortPillText = "${nextPrayer.prayer.nameId} ${nextPrayer.prayer.formatted}",
                emote = PochiEmote.HMM
            )
            in 17..19 -> SmartAssistantBanner(
                title = if (isId) "Waktu Maghrib & Makan Malam 🌇" else "Maghrib & Dinner Time 🌇",
                subtitle = if (isId) "Jangan lupa catat pengeluaran hari ini di menu Dompet." else "Don't forget to log today's expenses in Wallet.",
                shortPillText = "${nextPrayer.prayer.nameId} ${nextPrayer.prayer.formatted}",
                emote = PochiEmote.SMILING
            )
            in 20..22 -> SmartAssistantBanner(
                title = if (isId) "Waktu Santai & Evaluasi Hari Ini 🌙" else "Evening Wind Down & Review 🌙",
                subtitle = if (isId) "Cek sisa tugas besok & pastikan target air putih ($waterGlasses/8) terpenuhi." else "Review tomorrow's tasks & finish your hydration goal ($waterGlasses/8).",
                shortPillText = if (isId) "Malam • ${weather.tempC}°C" else "Night • ${weather.tempC}°C",
                emote = PochiEmote.CALM
            )
            else -> SmartAssistantBanner(
                title = if (isId) "Waktunya Istirahat Malam 💤" else "Time to Sleep & Recharge 💤",
                subtitle = if (isId) "Sudah larut malam, kurangi layar HP agar bangun segar besok pagi." else "It's late—rest your eyes so you wake up refreshed tomorrow.",
                shortPillText = if (isId) "Istirahat 💤" else "Sleep 💤",
                emote = PochiEmote.SLEEPY
            )
        }
    }
}

object PochiPrefs {
    private const val PREF_NAME = "pochi_settings"
    private const val KEY_API = "gemini_api_key"
    private const val KEY_OPENROUTER = "openrouter_api_key"
    private const val KEY_OPENCODE = "opencode_api_key"
    private const val KEY_NOTES = "pochi_saved_notes"
    private const val KEY_CITY = "pochi_weather_city"
    private const val KEY_OFFSET_X = "pochi_offset_x"
    private const val KEY_OFFSET_Y = "pochi_offset_y"
    private const val KEY_DARK_MODE = "pochi_dark_mode"
    private const val KEY_PROVIDER = "pochi_ai_provider"
    private const val KEY_SCREEN_AWARENESS = "pochi_screen_awareness"
    private const val KEY_TASKS_JSON = "pochi_today_tasks_json"
    private const val KEY_PINNED_APPS = "pochi_pinned_apps"
    private const val KEY_MCP_URL = "pochi_mcp_endpoint_url"
    private const val KEY_LANG = "pochi_language_code"
    private const val KEY_FLUSH_STATUSBAR = "pochi_flush_statusbar"
    private const val KEY_FOCUS_SESSIONS = "pochi_focus_completed_sessions"
    private const val KEY_EXPENSES_JSON = "pochi_expenses_json"
    private const val KEY_DAILY_BUDGET = "pochi_daily_budget_rp"
    private const val KEY_WATER_GLASSES = "pochi_water_glasses_today"

    private val _offsetXFlow = MutableStateFlow(0)
    val offsetXFlow: StateFlow<Int> = _offsetXFlow

    // Default Y = 0dp so the island sits flush ("nempel") inside/under the top status bar
    private val _offsetYFlow = MutableStateFlow(0)
    val offsetYFlow: StateFlow<Int> = _offsetYFlow

    private val _flushStatusBarFlow = MutableStateFlow(true)
    val flushStatusBarFlow: StateFlow<Boolean> = _flushStatusBarFlow

    private val _langFlow = MutableStateFlow(PochiLang.ID)
    val langFlow: StateFlow<PochiLang> = _langFlow

    private val _cityFlow = MutableStateFlow("Jakarta")
    val cityFlow: StateFlow<String> = _cityFlow

    private val _waterGlassesFlow = MutableStateFlow(3)
    val waterGlassesFlow: StateFlow<Int> = _waterGlassesFlow

    private val _dailyBudgetFlow = MutableStateFlow(100_000L)
    val dailyBudgetFlow: StateFlow<Long> = _dailyBudgetFlow

    private val _expensesFlow = MutableStateFlow<List<ExpenseEntry>>(defaultSampleExpenses())
    val expensesFlow: StateFlow<List<ExpenseEntry>> = _expensesFlow

    private val _screenAwarenessFlow = MutableStateFlow(false)
    val screenAwarenessFlow: StateFlow<Boolean> = _screenAwarenessFlow

    private val _darkModeFlow = MutableStateFlow(true)
    val darkModeFlow: StateFlow<Boolean> = _darkModeFlow

    private val _providerFlow = MutableStateFlow(AiProvider.GEMINI)
    val providerFlow: StateFlow<AiProvider> = _providerFlow

    private val _notesFlow = MutableStateFlow("")
    val notesFlow: StateFlow<String> = _notesFlow

    private val _tasksFlow = MutableStateFlow<List<TodayTask>>(defaultSampleTasks())
    val tasksFlow: StateFlow<List<TodayTask>> = _tasksFlow

    private val _pinnedAppsFlow = MutableStateFlow<Set<String>>(emptySet())
    val pinnedAppsFlow: StateFlow<Set<String>> = _pinnedAppsFlow

    private val _mcpUrlFlow = MutableStateFlow("ws://100.64.0.1:8765/mcp")
    val mcpUrlFlow: StateFlow<String> = _mcpUrlFlow

    private val _focusSessionsFlow = MutableStateFlow(2)
    val focusSessionsFlow: StateFlow<Int> = _focusSessionsFlow

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _offsetXFlow.value = prefs.getInt(KEY_OFFSET_X, 0)
        _offsetYFlow.value = prefs.getInt(KEY_OFFSET_Y, 0)
        _flushStatusBarFlow.value = prefs.getBoolean(KEY_FLUSH_STATUSBAR, true)
        _darkModeFlow.value = prefs.getBoolean(KEY_DARK_MODE, true)
        _screenAwarenessFlow.value = prefs.getBoolean(KEY_SCREEN_AWARENESS, false)
        _notesFlow.value = prefs.getString(KEY_NOTES, "") ?: ""
        _focusSessionsFlow.value = prefs.getInt(KEY_FOCUS_SESSIONS, 2)
        _cityFlow.value = prefs.getString(KEY_CITY, "Jakarta") ?: "Jakarta"
        _waterGlassesFlow.value = prefs.getInt(KEY_WATER_GLASSES, 3)
        _dailyBudgetFlow.value = prefs.getLong(KEY_DAILY_BUDGET, 100_000L)

        val savedLang = prefs.getString(KEY_LANG, PochiLang.ID.code)
        _langFlow.value = PochiLang.entries.firstOrNull { it.code == savedLang } ?: PochiLang.ID

        _providerFlow.value = prefs.getString(KEY_PROVIDER, AiProvider.GEMINI.name)
            ?.let { saved -> AiProvider.entries.firstOrNull { it.name == saved } }
            ?: AiProvider.GEMINI
        _mcpUrlFlow.value = prefs.getString(KEY_MCP_URL, "ws://100.64.0.1:8765/mcp")
            ?: "ws://100.64.0.1:8765/mcp"

        val savedTasksJson = prefs.getString(KEY_TASKS_JSON, null)
        if (!savedTasksJson.isNullOrBlank()) {
            _tasksFlow.value = decodeTasks(savedTasksJson)
        } else {
            _tasksFlow.value = defaultSampleTasks()
        }

        val savedExpensesJson = prefs.getString(KEY_EXPENSES_JSON, null)
        if (!savedExpensesJson.isNullOrBlank()) {
            _expensesFlow.value = decodeExpenses(savedExpensesJson)
        } else {
            _expensesFlow.value = defaultSampleExpenses()
        }

        val savedPinned = prefs.getStringSet(KEY_PINNED_APPS, null)
        if (savedPinned != null) {
            _pinnedAppsFlow.value = savedPinned
        }
    }

    fun setLanguage(context: Context, lang: PochiLang) {
        _langFlow.value = lang
        prefs(context).edit().putString(KEY_LANG, lang.code).apply()
    }

    fun toggleLanguage(context: Context) {
        val next = if (_langFlow.value == PochiLang.ID) PochiLang.EN else PochiLang.ID
        setLanguage(context, next)
    }

    fun setFlushStatusBar(context: Context, flush: Boolean) {
        _flushStatusBarFlow.value = flush
        val newY = if (flush) 0 else 6
        _offsetYFlow.value = newY
        prefs(context).edit()
            .putBoolean(KEY_FLUSH_STATUSBAR, flush)
            .putInt(KEY_OFFSET_Y, newY)
            .apply()
    }

    fun incrementFocusSessions(context: Context) {
        val next = _focusSessionsFlow.value + 1
        _focusSessionsFlow.value = next
        prefs(context).edit().putInt(KEY_FOCUS_SESSIONS, next).apply()
    }

    fun addWaterGlass(context: Context) {
        val next = (_waterGlassesFlow.value + 1).coerceIn(0, 16)
        _waterGlassesFlow.value = next
        prefs(context).edit().putInt(KEY_WATER_GLASSES, next).apply()
    }

    fun resetWaterGlasses(context: Context) {
        _waterGlassesFlow.value = 0
        prefs(context).edit().putInt(KEY_WATER_GLASSES, 0).apply()
    }

    fun setDailyBudget(context: Context, budgetRp: Long) {
        val clean = budgetRp.coerceAtLeast(10_000L)
        _dailyBudgetFlow.value = clean
        prefs(context).edit().putLong(KEY_DAILY_BUDGET, clean).apply()
    }

    fun saveExpenses(context: Context, list: List<ExpenseEntry>) {
        _expensesFlow.value = list
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("title", e.title)
                    put("amountRp", e.amountRp)
                    put("category", e.category)
                    put("timeLabel", e.timeLabel)
                }
            )
        }
        prefs(context).edit().putString(KEY_EXPENSES_JSON, arr.toString()).apply()
    }

    private fun decodeExpenses(json: String): List<ExpenseEntry> = runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    ExpenseEntry(
                        id = obj.optString("id", "exp_$i"),
                        title = obj.optString("title", "Pengeluaran"),
                        amountRp = obj.optLong("amountRp", 0L),
                        category = obj.optString("category", "Lainnya"),
                        timeLabel = obj.optString("timeLabel", "12:00")
                    )
                )
            }
        }
    }.getOrDefault(defaultSampleExpenses())

    private fun defaultSampleExpenses(): List<ExpenseEntry> = listOf(
        ExpenseEntry(
            id = "exp_1",
            title = "Sarapan & Teh Hangat",
            amountRp = 18_000L,
            category = "Makan",
            timeLabel = "07:30"
        ),
        ExpenseEntry(
            id = "exp_2",
            title = "Kopi Susu Dingin",
            amountRp = 15_000L,
            category = "Jajan",
            timeLabel = "13:15"
        )
    )

    private fun defaultSampleTasks(): List<TodayTask> = listOf(
        TodayTask(
            id = "sample_1",
            title = "Sarapan pagi & minum air putih",
            timeLabel = "7:30 AM",
            hour24 = 7,
            minute = 30,
            dayIndex = 0,
            isDone = false,
            isOverdue = false,
            isHighPriority = true
        ),
        TodayTask(
            id = "sample_2",
            title = "Fokus kerja / tugas utama hari ini",
            timeLabel = "10:00 AM",
            hour24 = 10,
            minute = 0,
            dayIndex = 0,
            isDone = false,
            isOverdue = false
        ),
        TodayTask(
            id = "sample_3",
            title = "Olahraga ringan / jalan sore",
            timeLabel = "4:30 PM",
            hour24 = 16,
            minute = 30,
            dayIndex = 0,
            isDone = true,
            isOverdue = false
        )
    )

    fun saveTasks(context: Context, tasks: List<TodayTask>) {
        _tasksFlow.value = tasks
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("timeLabel", t.timeLabel)
                    put("hour24", t.hour24)
                    put("minute", t.minute)
                    put("dayIndex", t.dayIndex)
                    put("isDone", t.isDone)
                    put("isOverdue", t.isOverdue)
                    put("isHighPriority", t.isHighPriority)
                }
            )
        }
        prefs(context).edit().putString(KEY_TASKS_JSON, arr.toString()).apply()
    }

    private fun decodeTasks(json: String): List<TodayTask> = runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    TodayTask(
                        id = obj.optString("id", "task_$i"),
                        title = obj.optString("title", ""),
                        timeLabel = obj.optString("timeLabel", ""),
                        hour24 = obj.optInt("hour24", -1),
                        minute = obj.optInt("minute", -1),
                        dayIndex = obj.optInt("dayIndex", 0),
                        isDone = obj.optBoolean("isDone", false),
                        isOverdue = obj.optBoolean("isOverdue", false),
                        isHighPriority = obj.optBoolean("isHighPriority", false)
                    )
                )
            }
        }
    }.getOrDefault(defaultSampleTasks())

    fun togglePinnedApp(context: Context, packageName: String) {
        val current = _pinnedAppsFlow.value.toMutableSet()
        if (!current.add(packageName)) {
            current.remove(packageName)
        }
        _pinnedAppsFlow.value = current
        prefs(context).edit().putStringSet(KEY_PINNED_APPS, current).apply()
    }

    fun saveMcpUrl(context: Context, url: String) {
        val trimmed = url.trim().ifBlank { "ws://100.64.0.1:8765/mcp" }
        _mcpUrlFlow.value = trimmed
        prefs(context).edit().putString(KEY_MCP_URL, trimmed).apply()
    }

    fun setScreenAwareness(context: Context, enabled: Boolean) {
        _screenAwarenessFlow.value = enabled
        prefs(context).edit().putBoolean(KEY_SCREEN_AWARENESS, enabled).apply()
        if (!enabled) PochiSignals.clear()
    }

    fun isScreenAwarenessEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SCREEN_AWARENESS, false)
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        _darkModeFlow.value = isDark
        prefs(context).edit().putBoolean(KEY_DARK_MODE, isDark).apply()
    }

    fun saveOffsets(context: Context, xDp: Int, yDp: Int) {
        _offsetXFlow.value = xDp
        _offsetYFlow.value = yDp
        prefs(context).edit()
            .putInt(KEY_OFFSET_X, xDp)
            .putInt(KEY_OFFSET_Y, yDp)
            .apply()
    }

    fun getProvider(context: Context): AiProvider {
        val saved = prefs(context).getString(KEY_PROVIDER, AiProvider.GEMINI.name)
        return AiProvider.entries.firstOrNull { it.name == saved } ?: AiProvider.GEMINI
    }

    fun setProvider(context: Context, provider: AiProvider) {
        _providerFlow.value = provider
        prefs(context).edit().putString(KEY_PROVIDER, provider.name).apply()
    }

    fun getApiKey(context: Context): String = getApiKey(context, getProvider(context))

    fun getApiKey(context: Context, provider: AiProvider): String {
        return prefs(context).getString(keyName(provider), "") ?: ""
    }

    fun saveApiKey(context: Context, apiKey: String) {
        saveApiKey(context, getProvider(context), apiKey)
    }

    fun saveApiKey(context: Context, provider: AiProvider, apiKey: String) {
        prefs(context).edit().putString(keyName(provider), apiKey.trim()).apply()
    }

    fun getModel(context: Context, provider: AiProvider): String {
        return prefs(context).getString(modelName(provider), provider.defaultModel)
            ?.takeIf { it.isNotBlank() }
            ?: provider.defaultModel
    }

    fun saveModel(context: Context, provider: AiProvider, model: String) {
        prefs(context).edit().putString(modelName(provider), model.trim()).apply()
    }

    fun saveNotes(context: Context, notes: String) {
        _notesFlow.value = notes
        prefs(context).edit().putString(KEY_NOTES, notes).apply()
    }

    fun getNotes(context: Context): String {
        val saved = prefs(context).getString(KEY_NOTES, "") ?: ""
        _notesFlow.value = saved
        return saved
    }

    fun saveCity(context: Context, city: String) {
        val clean = city.trim().ifBlank { "Jakarta" }
        _cityFlow.value = clean
        prefs(context).edit().putString(KEY_CITY, clean).apply()
    }

    fun getCity(context: Context): String {
        return prefs(context).getString(KEY_CITY, "Jakarta") ?: "Jakarta"
    }

    private fun keyName(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> KEY_API
        AiProvider.OPENROUTER -> KEY_OPENROUTER
        AiProvider.OPENCODE -> KEY_OPENCODE
    }

    private fun modelName(provider: AiProvider): String {
        return "pochi_model_${provider.name.lowercase()}"
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
