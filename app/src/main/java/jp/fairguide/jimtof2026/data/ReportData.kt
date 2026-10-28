package jp.fairguide.jimtof2026.data

import android.content.Context
import jp.fairguide.jimtof2026.logic.BoothCode
import org.json.JSONArray
import org.json.JSONObject

/** 見学レポートの項目（①競合、②省人化・無人化、⑤展示方法） */
data class ReportItem(
    val id: String,
    val short: String,
    val title: String,
    /** 検索タブの「見学レポート用の候補」と対応する ReportPreset の名前（無ければ null） */
    val presetName: String?,
    val intro: String,
    /** 会場で見るポイント */
    val guide: List<String>,
    val companies: List<ReportCompany>,
)

/** おすすめの会社1社ぶん。社名と最初の小間番号で出展社データに結びつく */
data class ReportCompany(
    val name: String,
    val booth: String,
    /** おすすめの理由（1行） */
    val why: String,
    /** 公開情報から分かった見どころ */
    val facts: List<String>,
    /** ブースで確かめること */
    val look: List<String>,
    val sourceIds: List<String>,
) {
    val key: String get() = NoveltyKey.of(name, booth)
}

data class ReportSource(val label: String, val url: String)

class ReportData(
    val retrievedOn: String,
    val deadline: String,
    val note: String,
    val sources: Map<String, ReportSource>,
    val items: List<ReportItem>,
) {
    fun item(id: String): ReportItem? = items.firstOrNull { it.id == id }
}

object ReportParser {
    fun parse(json: String): ReportData {
        val root = JSONObject(json)
        val src = root.getJSONObject("sources")
        val sources = src.keys().asSequence().associateWith { key ->
            val o = src.getJSONObject(key)
            ReportSource(o.getString("label"), o.getString("url"))
        }
        val items = root.getJSONArray("items")
        val parsed = (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            val cs = o.getJSONArray("companies")
            ReportItem(
                id = o.getString("id"),
                short = o.getString("short"),
                title = o.getString("title"),
                presetName = if (o.isNull("preset")) null else o.getString("preset"),
                intro = o.getString("intro"),
                guide = o.getJSONArray("guide").toStringList(),
                companies = (0 until cs.length()).map { j ->
                    val c = cs.getJSONObject(j)
                    ReportCompany(
                        name = c.getString("n"),
                        booth = c.getString("b"),
                        why = c.getString("why"),
                        facts = c.getJSONArray("facts").toStringList(),
                        look = c.getJSONArray("look").toStringList(),
                        sourceIds = c.getJSONArray("src").toStringList(),
                    )
                },
            )
        }
        return ReportData(
            retrievedOn = root.getString("retrievedOn"),
            deadline = root.getString("deadline"),
            note = root.getString("note"),
            sources = sources,
            items = parsed,
        )
    }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
}

object ReportRepository {
    private const val ASSET_NAME = "report.json"

    @Volatile
    private var cached: ReportData? = null

    fun get(context: Context): ReportData {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }
    }

    private fun load(context: Context): ReportData =
        context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { ReportParser.parse(it.readText()) }
}

/** 自分で項目に足した会社 */
data class ReportExtra(val itemId: String, val name: String, val booth: String) {
    val key: String get() = NoveltyKey.of(name, booth)
}

/** 会場で書いたメモ。すべてこの端末の中だけに保存する（外部送信なし） */
data class ReportNotes(
    /** "項目id|社名|小間" → メモ */
    val notes: Map<String, String> = emptyMap(),
    /** 項目id → まとめ */
    val summaries: Map<String, String> = emptyMap(),
    val extras: List<ReportExtra> = emptyList(),
) {
    fun note(itemId: String, key: String): String? = notes[noteKey(itemId, key)]

    companion object {
        fun noteKey(itemId: String, companyKey: String): String = "$itemId|$companyKey"
    }
}

class ReportNoteStore(context: Context) {
    private val prefs = context.getSharedPreferences("report_notes", Context.MODE_PRIVATE)

    fun load(): ReportNotes {
        val raw = prefs.getString(KEY, null) ?: return ReportNotes()
        return runCatching {
            val o = JSONObject(raw)
            val notes = o.optJSONObject("notes")?.let { n -> n.keys().asSequence().associateWith { n.optString(it, "") } }.orEmpty()
            val summaries = o.optJSONObject("summaries")?.let { s -> s.keys().asSequence().associateWith { s.optString(it, "") } }.orEmpty()
            val ex = o.optJSONArray("extras")
            val extras = if (ex == null) emptyList() else (0 until ex.length()).map { i ->
                val e = ex.getJSONObject(i)
                ReportExtra(e.getString("item"), e.getString("n"), e.getString("b"))
            }
            ReportNotes(notes, summaries, extras)
        }.getOrDefault(ReportNotes())
    }

    fun save(data: ReportNotes) {
        val notes = JSONObject()
        data.notes.forEach { (k, v) -> notes.put(k, v) }
        val summaries = JSONObject()
        data.summaries.forEach { (k, v) -> summaries.put(k, v) }
        val extras = JSONArray()
        data.extras.forEach { e ->
            extras.put(JSONObject().put("item", e.itemId).put("n", e.name).put("b", BoothCode.normalize(e.booth).ifEmpty { e.booth }))
        }
        prefs.edit()
            .putString(KEY, JSONObject().put("notes", notes).put("summaries", summaries).put("extras", extras).toString())
            .apply()
    }

    private companion object {
        const val KEY = "data"
    }
}
