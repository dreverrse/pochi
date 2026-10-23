package com.pochi.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PochiNotification(
    val key: String,
    val app: String,
    val title: String,
    val text: String,
    val postedAt: Long
)

object PochiSignals {
    private const val MAX_NOTIFICATIONS = 25

    private val _screenApp = MutableStateFlow("")
    val screenApp: StateFlow<String> = _screenApp

    private val _screenText = MutableStateFlow("")
    val screenText: StateFlow<String> = _screenText

    private val _screenUpdatedAt = MutableStateFlow(0L)
    val screenUpdatedAt: StateFlow<Long> = _screenUpdatedAt

    private val _notifications = MutableStateFlow<List<PochiNotification>>(emptyList())
    val notifications: StateFlow<List<PochiNotification>> = _notifications

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

    fun addNotification(notification: PochiNotification) {
        val current = _notifications.value
            .filterNot { it.key == notification.key }
        _notifications.value = (listOf(notification) + current)
            .take(MAX_NOTIFICATIONS)
    }

    fun removeNotification(key: String) {
        _notifications.value = _notifications.value.filterNot { it.key == key }
    }

    fun clearNotifications() {
        _notifications.value = emptyList()
    }
}
