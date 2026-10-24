package com.pochi.assistant

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class TodayTask(
    val id: String,
    val title: String,
    val timeLabel: String = "",
    val dayIndex: Int = 0, // 0 = Today, -3..+3 in weekly strip
    val isDone: Boolean = false,
    val isOverdue: Boolean = false
)

object NaturalTaskParser {
    private val splitClauseRegex = Regex(
        """(?:\s*,\s*then\s+|\s+then\s+|\s*,\s*and\s+|\s*;\s*|\s*,\s*(?=\d{1,2}(?::\d{2})?\s*(?:am|pm)?\b)|\s+lalu\s+)""",
        RegexOption.IGNORE_CASE
    )
    private val timeTokenRegex = Regex(
        """\b(?:at\s+|jam\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm|pagi|siang|sore|malam)?\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses natural language input such as `"review PR, then 2pm team meeting"`
     * into one or more distinct scheduled [TodayTask] items with extracted timestamps.
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
            var titleText = clause

            val match = timeTokenRegex.find(clause)
            if (match != null) {
                val rawHour = match.groupValues[1].toIntOrNull()
                val rawMin = match.groupValues[2].toIntOrNull() ?: 0
                val meridiem = match.groupValues[3].lowercase(Locale.US)
                val hasExplicitMarker = meridiem.isNotEmpty() ||
                    match.groupValues[2].isNotEmpty() ||
                    match.value.lowercase(Locale.US).startsWith("at ") ||
                    match.value.lowercase(Locale.US).startsWith("jam ")

                if (rawHour != null && rawHour in 0..23 && rawMin in 0..59 && hasExplicitMarker) {
                    val hour24 = when {
                        meridiem in setOf("pm", "siang", "sore", "malam") && rawHour in 1..11 -> rawHour + 12
                        meridiem in setOf("am", "pagi") && rawHour == 12 -> 0
                        else -> rawHour
                    }
                    val displayAmPm = if (hour24 >= 12) "PM" else "AM"
                    val hour12 = when {
                        hour24 == 0 -> 12
                        hour24 > 12 -> hour24 - 12
                        else -> hour24
                    }
                    timeFormatted = if (rawMin == 0) {
                        "$hour12:00 $displayAmPm"
                    } else {
                        String.format(Locale.US, "%d:%02d %s", hour12, rawMin, displayAmPm)
                    }
                    titleText = clause.removeRange(match.range)
                        .replace(Regex("""\s+"""), " ")
                        .trim(' ', ',', '-', ':')
                }
            }

            if (titleText.isBlank()) {
                titleText = clause
            } else {
                titleText = titleText.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            }

            TodayTask(
                id = "${baseId}_$idx",
                title = titleText,
                timeLabel = timeFormatted,
                dayIndex = activeDayIndex,
                isDone = false,
                isOverdue = false
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

    private val _offsetXFlow = MutableStateFlow(0)
    val offsetXFlow: StateFlow<Int> = _offsetXFlow

    private val _offsetYFlow = MutableStateFlow(4)
    val offsetYFlow: StateFlow<Int> = _offsetYFlow

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

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _offsetXFlow.value = prefs.getInt(KEY_OFFSET_X, 0)
        _offsetYFlow.value = prefs.getInt(KEY_OFFSET_Y, 4)
        _darkModeFlow.value = prefs.getBoolean(KEY_DARK_MODE, true)
        _screenAwarenessFlow.value = prefs.getBoolean(KEY_SCREEN_AWARENESS, false)
        _notesFlow.value = prefs.getString(KEY_NOTES, "") ?: ""
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

    private fun defaultSampleTasks(): List<TodayTask> = listOf(
        TodayTask(
            id = "sample_1",
            title = "Review PR #42 & merge branch",
            timeLabel = "10:30 AM",
            dayIndex = 0,
            isDone = false,
            isOverdue = true
        ),
        TodayTask(
            id = "sample_2",
            title = "Team architecture sync",
            timeLabel = "2:00 PM",
            dayIndex = 0,
            isDone = false,
            isOverdue = false
        ),
        TodayTask(
            id = "sample_3",
            title = "Ship 120fps Dynamic Island build",
            timeLabel = "4:30 PM",
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
                    put("dayIndex", t.dayIndex)
                    put("isDone", t.isDone)
                    put("isOverdue", t.isOverdue)
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
                        dayIndex = obj.optInt("dayIndex", 0),
                        isDone = obj.optBoolean("isDone", false),
                        isOverdue = obj.optBoolean("isOverdue", false)
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
