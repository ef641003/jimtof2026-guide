package jp.fairguide.jimtof2026.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import jp.fairguide.jimtof2026.MainActivity
import jp.fairguide.jimtof2026.R
import jp.fairguide.jimtof2026.data.FairRepository
import jp.fairguide.jimtof2026.data.ReminderStore
import jp.fairguide.jimtof2026.logic.hm

class ReminderReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) ?: return
        ReminderStore(context).remove(eventId)
        val event = FairRepository.get(context).events.firstOrNull { it.id == eventId } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Notifications.ensureChannel(context)

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, "schedule")
            putExtra(MainActivity.EXTRA_EVENT_DATE, event.date.toString())
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val startText = event.start?.hm() ?: ""
        val body = buildString {
            append(event.title)
            append('\n').append(event.place)
            if (event.speaker.isNotBlank()) append('\n').append(event.speaker)
        }
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("$startText から：${event.title}")
            .setContentText(event.place)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
    }
}
