package jp.fairguide.jimtof2026.data

import android.content.Context

/** 通知を設定した予定のIDを保存する */
class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun load(): Set<String> = prefs.getStringSet(KEY_IDS, null)?.toSet() ?: emptySet()

    fun save(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_IDS, HashSet(ids)).apply()
    }

    fun add(id: String) = save(load() + id)

    fun remove(id: String) = save(load() - id)

    private companion object {
        const val KEY_IDS = "ids"
    }
}
