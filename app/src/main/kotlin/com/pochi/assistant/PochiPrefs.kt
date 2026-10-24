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

    private val _offsetXFlow = MutableStateFlow(0)
    val offsetXFlow: StateFlow<Int> = _offsetXFlow

    // Default Y = 0dp so the island sits flush ("nempel") inside the top status bar
    private val _offsetYFlow = MutableStateFlow(0)
    val offsetYFlow: StateFlow<Int> = _offsetYFlow

    private val _flushStatusBarFlow = MutableStateFlow(true)
    val flushStatusBarFlow: StateFlow<Boolean> = _flushStatusBarFlow

    private val _langFlow = MutableStateFlow(PochiLang.ID)
    val langFlow: StateFlow<PochiLang> = _langFlow

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

    private fun defaultSampleTasks(): List<TodayTask> = listOf(
        TodayTask(
            id = "sample_1",
            title = "Review PR #42 & merge branch",
            timeLabel = "10:30 AM",
            hour24 = 10,
            minute = 30,
            dayIndex = 0,
            isDone = false,
            isOverdue = true,
            isHighPriority = true
        ),
        TodayTask(
            id = "sample_2",
            title = "Team architecture sync",
            timeLabel = "2:00 PM",
            hour24 = 14,
            minute = 0,
            dayIndex = 0,
            isDone = false,
            isOverdue = false
        ),
        TodayTask(
            id = "sample_3",
            title = "Ship 120fps Dynamic Island build",
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
        prefs(context).edit().putString(KEY_CITY, city.trim()).apply()
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
