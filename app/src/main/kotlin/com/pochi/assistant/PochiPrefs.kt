package com.pochi.assistant

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    private val _offsetXFlow = MutableStateFlow(0)
    val offsetXFlow: StateFlow<Int> = _offsetXFlow

    private val _offsetYFlow = MutableStateFlow(4)
    val offsetYFlow: StateFlow<Int> = _offsetYFlow

    private val _hiddenFlow = MutableStateFlow(false)
    val hiddenFlow: StateFlow<Boolean> = _hiddenFlow

    private val _screenAwarenessFlow = MutableStateFlow(false)
    val screenAwarenessFlow: StateFlow<Boolean> = _screenAwarenessFlow

    private val _darkModeFlow = MutableStateFlow(false)
    val darkModeFlow: StateFlow<Boolean> = _darkModeFlow

    private val _providerFlow = MutableStateFlow(AiProvider.GEMINI)
    val providerFlow: StateFlow<AiProvider> = _providerFlow

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _offsetXFlow.value = prefs.getInt(KEY_OFFSET_X, 0)
        _offsetYFlow.value = prefs.getInt(KEY_OFFSET_Y, 4)
        _darkModeFlow.value = prefs.getBoolean(KEY_DARK_MODE, false)
        _screenAwarenessFlow.value = prefs.getBoolean(KEY_SCREEN_AWARENESS, false)
        _hiddenFlow.value = false
        _providerFlow.value = prefs.getString(KEY_PROVIDER, AiProvider.GEMINI.name)
            ?.let { saved -> AiProvider.entries.firstOrNull { it.name == saved } }
            ?: AiProvider.GEMINI
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
        prefs(context).edit().putString(KEY_NOTES, notes).apply()
    }

    fun getNotes(context: Context): String {
        return prefs(context).getString(KEY_NOTES, "") ?: ""
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
