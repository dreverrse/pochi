package com.pochi.assistant

import android.content.Context

object PochiPrefs {
    private const val PREF_NAME = "pochi_settings"
    private const val KEY_API = "gemini_api_key"
    private const val KEY_NOTES = "pochi_saved_notes"
    private const val KEY_CITY = "pochi_weather_city"

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
            .getString(KEY_CITY, "Jakarta") ?: "Jakarta"
    }
}
