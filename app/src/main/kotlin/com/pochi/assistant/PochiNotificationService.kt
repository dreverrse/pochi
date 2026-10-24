package com.pochi.assistant

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PochiNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching {
            activeNotifications?.forEach { publish(it) }
            refreshMediaSessions()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        publish(sbn)
        refreshMediaSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val key = sbn.key ?: return
        PochiSignals.removeNotification(key)
        refreshMediaSessions()
    }

    private fun refreshMediaSessions() {
        runCatching {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val component = ComponentName(this, PochiNotificationService::class.java)
            val controllers: List<MediaController> = msm.getActiveSessions(component)
            val active = controllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: controllers.firstOrNull()

            if (active != null) {
                val meta = active.metadata
                val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                    ?: ""
                val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: ""
                val isPlaying = active.playbackState?.state == PlaybackState.STATE_PLAYING
                val appLabel = runCatching {
                    val info = packageManager.getApplicationInfo(active.packageName, 0)
                    packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(active.packageName)

                if (title.isNotBlank()) {
                    PochiSignals.updateMediaPlayback(
                        MediaPlaybackInfo(
                            title = title,
                            artist = artist,
                            appName = appLabel,
                            isPlaying = isPlaying
                        )
                    )
                    return
                }
            }
        }
    }

    private fun publish(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val key = sbn.key ?: "${sbn.packageName}-${sbn.postTime}"

        // 1. Detect active file download progress notifications
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, -1)
        val maxProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        if (maxProgress > 0 && progress in 0..maxProgress) {
            if (progress >= maxProgress) {
                PochiSignals.updateDownload(null)
            } else {
                PochiSignals.updateDownload(
                    DownloadStatusInfo(
                        key = key,
                        title = title.ifBlank { text.ifBlank { "Downloading file..." } },
                        progress = progress,
                        maxProgress = maxProgress,
                        indeterminate = indeterminate
                    )
                )
            }
            return
        }

        // 2. Detect media style notifications as fallback if MediaSessionManager is restricted
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION) && title.isNotBlank()) {
            val appLabel = runCatching {
                val info = packageManager.getApplicationInfo(sbn.packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(sbn.packageName)
            PochiSignals.updateMediaPlayback(
                MediaPlaybackInfo(
                    title = title,
                    artist = text,
                    appName = appLabel,
                    isPlaying = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
                )
            )
            return
        }

        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (title.isBlank() && text.isBlank()) return

        val appLabel = runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)

        PochiSignals.addNotification(
            PochiNotification(
                key = key,
                app = appLabel,
                title = title,
                text = text,
                postedAt = sbn.postTime
            )
        )
    }
}
