package jp.fairguide.jimtof2026.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 端末の再起動やアプリ更新でアラームが消えるため、設定し直す */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> ReminderScheduler.rescheduleAll(context)
        }
    }
}
