package com.pochi.assistant

import android.content.Context

object PochiPrefs {
    private const val PREF_NAME = "pochi_settings"
    private const val KEY_API = "gemini_api_key"

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
}
