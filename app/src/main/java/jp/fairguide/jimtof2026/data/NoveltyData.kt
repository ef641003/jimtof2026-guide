package jp.fairguide.jimtof2026.data

import android.content.Context
import jp.fairguide.jimtof2026.logic.BoothCode
import org.json.JSONObject

/** 公開情報から確認できたノベルティ1件。社名・小間番号・品名・条件・数量だけを持つ */
data class NoveltyInfo(
    val item: String,
    val condition: String,
    val quantity: String,
    val sourceId: String,
)

data class NoveltySource(val label: String, val url: String)

/** ノベルティの出展社を引くキー（社名＋最初の小間番号） */
object NoveltyKey {
    fun of(name: String, booth: String): String = name + "|" + BoothCode.normalize(booth)
    fun of(exhibitor: Exhibitor): String = of(exhibitor.name, exhibitor.booths.first())
}

class NoveltyData(
    val retrievedOn: String,
    val sources: Map<String, NoveltySource>,
    /** NoveltyKey → その会社のノベルティ */
    val byExhibitor: Map<String, List<NoveltyInfo>>,
) {
    fun infoFor(exhibitor: Exhibitor): List<NoveltyInfo> = byExhibitor[NoveltyKey.of(exhibitor)].orEmpty()
}

object NoveltyParser {
    fun parse(json: String): NoveltyData {
        val root = JSONObject(json)
        val src = root.getJSONObject("sources")
        val sources = src.keys().asSequence().associateWith { key ->
            val o = src.getJSONObject(key)
            NoveltySource(o.getString("label"), o.getString("url"))
        }
        val items = root.getJSONArray("items")
        val map = LinkedHashMap<String, MutableList<NoveltyInfo>>()
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val key = NoveltyKey.of(o.getString("n"), o.getString("b"))
            map.getOrPut(key) { ArrayList() } += NoveltyInfo(
                item = o.getString("item"),
                condition = o.getString("cond"),
                quantity = o.getString("qty"),
                sourceId = o.getString("s"),
            )
        }
        return NoveltyData(root.getString("retrievedOn"), sources, map)
    }
}

/** ノベルティのデータも、最初に必要になったときに1回だけ読む */
object NoveltyRepository {
    private const val ASSET_NAME = "novelty.json"

    @Volatile
    private var cached: NoveltyData? = null

    fun get(context: Context): NoveltyData {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }
    }

    private fun load(context: Context): NoveltyData =
        context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { NoveltyParser.parse(it.readText()) }
}

/** 会場で見つけたノベルティの自分用メモ。この端末の中だけに保存する（外部送信なし） */
class NoveltyNoteStore(context: Context) {
    private val prefs = context.getSharedPreferences("novelty_notes", Context.MODE_PRIVATE)

    /** NoveltyKey → メモ（空でも「ノベルティあり」の印として残す） */
    fun load(): Map<String, String> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.optString(it, "") }
        }.getOrDefault(emptyMap())
    }

    fun save(notes: Map<String, String>) {
        val o = JSONObject()
        notes.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private companion object {
        const val KEY = "notes"
    }
}
