package com.pochi.assistant

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PochiPrefs {
    private const val PREF_NAME = "pochi_settings"
    private const val KEY_API = "gemini_api_key"
    private const val KEY_NOTES = "pochi_saved_notes"
    private const val KEY_CITY = "pochi_weather_city"
    private const val KEY_OFFSET_X = "pochi_offset_x"
    private const val KEY_OFFSET_Y = "pochi_offset_y"
    private const val KEY_DARK_MODE = "pochi_dark_mode"

    private val _offsetXFlow = MutableStateFlow(0)
    val offsetXFlow: StateFlow<Int> = _offsetXFlow

    private val _offsetYFlow = MutableStateFlow(4)
    val offsetYFlow: StateFlow<Int> = _offsetYFlow

    private val _darkModeFlow = MutableStateFlow(false)
    val darkModeFlow: StateFlow<Boolean> = _darkModeFlow

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _offsetXFlow.value = prefs.getInt(KEY_OFFSET_X, 0)
        _offsetYFlow.value = prefs.getInt(KEY_OFFSET_Y, 4)
        _darkModeFlow.value = prefs.getBoolean(KEY_DARK_MODE, false)
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        _darkModeFlow.value = isDark
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, isDark)
            .apply()
    }

    fun saveOffsets(context: Context, xDp: Int, yDp: Int) {
        _offsetXFlow.value = xDp
        _offsetYFlow.value = yDp
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OFFSET_X, xDp)
            .putInt(KEY_OFFSET_Y, yDp)
            .apply()
    }

    fun saveApiKey(context: Context, apiKey: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API, apiKey.trim())
            .apply()
    }

    fun getApiKey(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API, "") ?: ""
    }

    fun saveNotes(context: Context, notes: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOTES, notes)
            .apply()
    }

    fun getNotes(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NOTES, "") ?: ""
    }

    fun saveCity(context: Context, city: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CITY, city.trim())
            .apply()
    }

    fun getCity(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CITY, "Semarang") ?: "Semarang"
    }
}
