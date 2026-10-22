package jp.fairguide.jimtof2026.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_ID = "event_reminders"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "講演リマインダー",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "講演・ワークショップの開始15分前に通知します"
        }
        manager.createNotificationChannel(channel)
    }
}
