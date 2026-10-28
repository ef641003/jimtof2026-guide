package jp.fairguide.jimtof2026.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** ブースで確かめる項目1つ（チェックリストの1行） */
data class CheckItem(val text: String, val done: Boolean = false)

/** マイリストの1件（回りたいブース） */
data class BoothItem(
    val id: Long,
    val company: String,
    val booth: String,
    val memo: String,
    val visited: Boolean,
    val createdAt: Long,
    /** ブースで確かめる項目（見学ルートのチェックリスト） */
    val checks: List<CheckItem> = emptyList(),
)

/** チェックリストの保存・復元（画面の回転で編集中の内容が消えないよう、文字列にも変換できる） */
object CheckCodec {
    fun toJson(checks: List<CheckItem>): JSONArray {
        val array = JSONArray()
        checks.forEach { array.put(JSONObject().put("t", it.text).put("d", it.done)) }
        return array
    }

    fun fromJson(array: JSONArray?): List<CheckItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optString("t", "").trim()
            if (text.isEmpty()) null else CheckItem(text, o.optBoolean("d", false))
        }
    }

    /** 1行1項目：先頭の1文字が「1」ならチェック済み、そのあとに項目 */
    fun pack(checks: List<CheckItem>): String =
        checks.joinToString("\n") { (if (it.done) "1" else "0") + it.text.replace('\n', ' ') }

    fun unpack(raw: String): List<CheckItem> =
        raw.lines().filter { it.length > 1 }.map { CheckItem(it.substring(1).trim(), it[0] == '1') }
            .filter { it.text.isNotEmpty() }
}

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
                    checks = CheckCodec.fromJson(o.optJSONArray("checks")),
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
                    .put("createdAt", item.createdAt)
                    .put("checks", CheckCodec.toJson(item.checks)),
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object {
        const val KEY_ITEMS = "items"
    }
}
