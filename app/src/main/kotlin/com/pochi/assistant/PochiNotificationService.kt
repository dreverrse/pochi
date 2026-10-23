package com.pochi.assistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PochiNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching {
            activeNotifications?.forEach { publish(it) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        publish(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        PochiSignals.removeNotification(sbn.key ?: return)
    }

    private fun publish(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val appLabel = runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)

        PochiSignals.addNotification(
            PochiNotification(
                key = sbn.key ?: "${sbn.packageName}-${sbn.postTime}",
                app = appLabel,
                title = title,
                text = text,
                postedAt = sbn.postTime
            )
        )
    }
}
