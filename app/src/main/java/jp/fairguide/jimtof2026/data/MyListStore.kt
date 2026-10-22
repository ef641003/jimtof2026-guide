package jp.fairguide.jimtof2026.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** マイリストの1件（回りたいブース） */
data class BoothItem(
    val id: Long,
    val company: String,
    val booth: String,
    val memo: String,
    val visited: Boolean,
    val createdAt: Long,
)

/** マイリストはこの端末の中だけに保存する（外部送信なし） */
class MyListStore(context: Context) {
    private val prefs = context.getSharedPreferences("my_list", Context.MODE_PRIVATE)

    fun load(): List<BoothItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val id = o.getLong("id")
                BoothItem(
                    id = id,
                    company = o.optString("company", ""),
                    booth = o.optString("booth", ""),
                    memo = o.optString("memo", ""),
                    visited = o.optBoolean("visited", false),
                    createdAt = o.optLong("createdAt", id),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(items: List<BoothItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("company", item.company)
                    .put("booth", item.booth)
                    .put("memo", item.memo)
                    .put("visited", item.visited)
                    .put("createdAt", item.createdAt),
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object {
        const val KEY_ITEMS = "items"
    }
}
