package com.pochi.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PochiSignals {
    private val _screenApp = MutableStateFlow("")
    val screenApp: StateFlow<String> = _screenApp

    private val _screenText = MutableStateFlow("")
    val screenText: StateFlow<String> = _screenText

    private val _screenUpdatedAt = MutableStateFlow(0L)
    val screenUpdatedAt: StateFlow<Long> = _screenUpdatedAt

    fun updateScreen(app: String, text: String) {
        _screenApp.value = app
        _screenText.value = text
        _screenUpdatedAt.value = System.currentTimeMillis()
    }

    fun clear() {
        _screenApp.value = ""
        _screenText.value = ""
        _screenUpdatedAt.value = 0L
    }
}
