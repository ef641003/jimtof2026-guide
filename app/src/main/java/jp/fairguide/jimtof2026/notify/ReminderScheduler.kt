package jp.fairguide.jimtof2026.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import jp.fairguide.jimtof2026.data.FairEvent
import jp.fairguide.jimtof2026.data.FairRepository
import jp.fairguide.jimtof2026.data.ReminderStore
import jp.fairguide.jimtof2026.logic.FAIR_ZONE
import java.time.ZonedDateTime

/** 予定の開始15分前に通知を出すアラームを管理する */
object ReminderScheduler {
    const val LEAD_MINUTES = 15L
    const val EXTRA_EVENT_ID = "event_id"
    private const val ACTION_REMIND = "jp.fairguide.jimtof2026.action.REMIND"

    // 正確なアラームが使えない端末では、10分の幅（開始20〜10分前）で通知する
    private const val WINDOW_MILLIS = 10 * 60 * 1000L

    enum class Result(val scheduled: Boolean) {
        EXACT(true),
        INEXACT(true),
        PASSED(false),
        NO_TIME(false),
    }

    fun schedule(context: Context, event: FairEvent, now: ZonedDateTime = ZonedDateTime.now(FAIR_ZONE)): Result {
        val start = event.startDateTime() ?: return Result.NO_TIME
        if (!start.isAfter(now)) return Result.PASSED
        val desired = start.minusMinutes(LEAD_MINUTES)
        val triggerAt = (if (desired.isAfter(now)) desired else now.plusSeconds(3)).toInstant().toEpochMilli()
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return Result.PASSED
        val operation = pendingIntent(context, event.id)

        if (canUseExact(alarmManager)) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
                return Result.EXACT
            } catch (e: SecurityException) {
                // 許可が取り消された直後など。下の近似アラームに切り替える
            }
        }
        val windowStart = maxOf(System.currentTimeMillis(), triggerAt - WINDOW_MILLIS / 2)
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, windowStart, WINDOW_MILLIS, operation)
        return Result.INEXACT
    }

    fun cancel(context: Context, eventId: String) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, eventId))
    }

    /** 起動時・再起動後・権限変更後に、保存済みのリマインダーを設定し直す */
    fun rescheduleAll(context: Context) {
        val store = ReminderStore(context)
        val ids = store.load()
        if (ids.isEmpty()) return
        val events = FairRepository.get(context).events.associateBy { it.id }
        val now = ZonedDateTime.now(FAIR_ZONE)
        val kept = ids.filterTo(HashSet()) { id ->
            val event = events[id] ?: return@filterTo false
            schedule(context, event, now).scheduled
        }
        if (kept != ids) store.save(kept)
    }

    fun canUseExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(context: Context, eventId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            data = Uri.parse("jimtofguide://reminder/$eventId")
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
