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

/** 見学ルートの設定（レポートタブの「ルート」） */
data class RouteSettings(
    /** ルートに入れる項目 */
    val itemIds: Set<String> = setOf("1", "2", "5"),
    /** ブースリストの会社も入れる */
    val includeMyList: Boolean = false,
    /** ブースリストで訪問済みにした会社を外す */
    val skipVisited: Boolean = true,
    /** 出発地点（VenueWalk.Start の id） */
    val start: String = "main",
    /** 1社あたりの滞在の目安（分） */
    val minutesPerStop: Int = 15,
    /** 1日に回れる時間の目安（分） */
    val minutesPerDay: Int = 300,
) {
    companion object {
        /** レポートタブの項目チップで「ルート」を選んだときの id */
        const val ID = "route"
    }
}

/** 会場で書いたメモ。すべてこの端末の中だけに保存する（外部送信なし） */
data class ReportNotes(
    /** "項目id|社名|小間" → メモ */
    val notes: Map<String, String> = emptyMap(),
    /** 項目id → まとめ */
    val summaries: Map<String, String> = emptyMap(),
    val extras: List<ReportExtra> = emptyList(),
    val route: RouteSettings = RouteSettings(),
    /** ルートから外した会社（NoveltyKey） */
    val routeSkips: Set<String> = emptySet(),
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
            val r = o.optJSONObject("route")
            val defaults = RouteSettings()
            val route = if (r == null) defaults else RouteSettings(
                itemIds = r.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: defaults.itemIds,
                includeMyList = r.optBoolean("myList", defaults.includeMyList),
                skipVisited = r.optBoolean("skipVisited", defaults.skipVisited),
                start = r.optString("start", defaults.start),
                minutesPerStop = r.optInt("perStop", defaults.minutesPerStop),
                minutesPerDay = r.optInt("perDay", defaults.minutesPerDay),
            )
            val sk = o.optJSONArray("skips")
            val skips = if (sk == null) emptySet() else (0 until sk.length()).map { sk.getString(it) }.toSet()
            ReportNotes(notes, summaries, extras, route, skips)
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
        val route = JSONObject()
            .put("items", JSONArray(data.route.itemIds.sorted()))
            .put("myList", data.route.includeMyList)
            .put("skipVisited", data.route.skipVisited)
            .put("start", data.route.start)
            .put("perStop", data.route.minutesPerStop)
            .put("perDay", data.route.minutesPerDay)
        val root = JSONObject()
            .put("notes", notes)
            .put("summaries", summaries)
            .put("extras", extras)
            .put("route", route)
            .put("skips", JSONArray(data.routeSkips.sorted()))
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    private companion object {
        const val KEY = "data"
    }
}
