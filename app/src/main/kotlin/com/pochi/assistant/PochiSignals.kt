package com.pochi.assistant

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PochiNotification(
    val key: String,
    val app: String,
    val title: String,
    val text: String,
    val postedAt: Long
)

data class MediaPlaybackInfo(
    val title: String = "",
    val artist: String = "",
    val appName: String = "",
    val isPlaying: Boolean = false
) {
    val hasTrack: Boolean get() = title.isNotBlank()
}

data class DownloadStatusInfo(
    val key: String,
    val title: String,
    val progress: Int,
    val maxProgress: Int = 100,
    val indeterminate: Boolean = false
) {
    val fraction: Float
        get() = if (maxProgress > 0) (progress.toFloat() / maxProgress.toFloat()).coerceIn(0f, 1f) else 0f
}

data class ScreenshotPreviewInfo(
    val uri: Uri,
    val displayName: String,
    val capturedAt: Long = System.currentTimeMillis()
)

data class AgentChoicePrompt(
    val id: String,
    val question: String,
    val options: List<String>
)

data class AgentSessionState(
    val providerName: String = "Claude Code",
    val isConnected: Boolean = true,
    val endpointUrl: String = "ws://100.64.0.1:8765/mcp",
    val compactStatus: String = "Idle • Ready",
    val isBusy: Boolean = false,
    val currentFileOrTool: String = "",
    val contextTokensUsed: Int = 48_200,
    val contextTokensMax: Int = 200_000,
    val sessionQuotaPercent: Float = 0.76f,
    val recentLogs: List<String> = listOf(
        "✓ Connected to MCP session (claude-code-v2)",
        "✓ Indexed 42 workspace files",
        "• Watching git working tree for changes"
    ),
    val activeChoicePrompt: AgentChoicePrompt? = null
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

    private val _mediaPlayback = MutableStateFlow(MediaPlaybackInfo())
    val mediaPlayback: StateFlow<MediaPlaybackInfo> = _mediaPlayback

    private val _activeDownload = MutableStateFlow<DownloadStatusInfo?>(null)
    val activeDownload: StateFlow<DownloadStatusInfo?> = _activeDownload

    private val _latestScreenshot = MutableStateFlow<ScreenshotPreviewInfo?>(null)
    val latestScreenshot: StateFlow<ScreenshotPreviewInfo?> = _latestScreenshot

    private val _agentSession = MutableStateFlow(AgentSessionState())
    val agentSession: StateFlow<AgentSessionState> = _agentSession

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
        if (_activeDownload.value?.key == key) {
            _activeDownload.value = null
        }
    }

    fun clearNotifications() {
        _notifications.value = emptyList()
    }

    fun updateMediaPlayback(info: MediaPlaybackInfo) {
        _mediaPlayback.value = info
    }

    fun updateDownload(info: DownloadStatusInfo?) {
        _activeDownload.value = info
    }

    fun publishScreenshot(info: ScreenshotPreviewInfo) {
        _latestScreenshot.value = info
    }

    fun dismissScreenshot() {
        _latestScreenshot.value = null
    }

    fun updateAgentSession(transform: (AgentSessionState) -> AgentSessionState) {
        _agentSession.value = transform(_agentSession.value)
    }

    fun playTapHaptic(context: android.content.Context) {
        runCatching {
            val v = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                v?.vibrate(android.os.VibrationEffect.createOneShot(18, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(18)
            }
        }
    }

    fun playSuccessHaptic(context: android.content.Context) {
        runCatching {
            val v = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                v?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 20, 60, 30), -1))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(40)
            }
        }
    }
}
