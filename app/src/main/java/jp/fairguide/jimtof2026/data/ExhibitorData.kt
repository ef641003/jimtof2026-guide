package jp.fairguide.jimtof2026.data

import android.content.Context
import org.json.JSONObject

/** 出展社1社。公式オンラインカタログの「社名・小間番号・出展製品分類」だけを持つ */
data class Exhibitor(
    val name: String,
    /** 英語名（公式英語版に同じ小間で1社だけ載っていたときのみ） */
    val nameEn: String?,
    val booths: List<String>,
    /** 50音の行（ア〜ワ）または「海外」 */
    val group: String,
    /** 0＝通常、1＝共同出展者、2＝内部出展者 */
    val mark: Int,
    /** 出展製品分類のコード（g＝大分類、m＝中分類、s＝小分類） */
    val codes: List<String>,
) {
    val markLabel: String?
        get() = when (mark) {
            1 -> "共同出展"
            2 -> "内部出展"
            else -> null
        }
}

/** 出展製品分類（大・中・小の3階層） */
class ProductCategories(
    val large: Map<String, String>,
    val middle: Map<String, Pair<String, String>>,   // code -> (parent g, name)
    val small: Map<String, Pair<String, String>>,    // code -> (parent m, name)
) {
    fun name(code: String): String? = when (code.firstOrNull()) {
        'g' -> large[code.drop(1)]
        'm' -> middle[code.drop(1)]?.second
        's' -> small[code.drop(1)]?.second
        else -> null
    }

    /** 大分類の名前（s/m コードから親をたどる） */
    fun largeName(code: String): String? = when (code.firstOrNull()) {
        'g' -> large[code.drop(1)]
        'm' -> middle[code.drop(1)]?.first?.let { large[it] }
        's' -> small[code.drop(1)]?.first?.let { m -> middle[m]?.first }?.let { large[it] }
        else -> null
    }

    /** 中分類の名前（s コードから親をたどる。g には無い） */
    fun middleName(code: String): String? = when (code.firstOrNull()) {
        'm' -> middle[code.drop(1)]?.second
        's' -> small[code.drop(1)]?.first?.let { middle[it]?.second }
        else -> null
    }

    /** 検索用：このコードと、その親の名前をすべて返す */
    fun namesWithParents(code: String): List<String> =
        listOfNotNull(name(code), middleName(code), largeName(code)).distinct()
}

class ExhibitorData(
    val retrievedOn: String,
    val source: String,
    val categories: ProductCategories,
    val synonyms: Map<String, List<String>>,
    val exhibitors: List<Exhibitor>,
)

object ExhibitorParser {
    fun parse(json: String): ExhibitorData {
        val root = JSONObject(json)
        val cats = root.getJSONObject("cats")
        val g = cats.getJSONObject("g")
        val m = cats.getJSONObject("m")
        val s = cats.getJSONObject("s")
        val large = g.keys().asSequence().associateWith { g.getString(it) }
        val middle = m.keys().asSequence().associateWith { key ->
            val o = m.getJSONObject(key)
            o.getString("g") to o.getString("t")
        }
        val small = s.keys().asSequence().associateWith { key ->
            val o = s.getJSONObject(key)
            o.getString("m") to o.getString("t")
        }
        val syn = root.getJSONObject("synonyms")
        val synonyms = syn.keys().asSequence().associateWith { key ->
            val arr = syn.getJSONArray(key)
            (0 until arr.length()).map { arr.getString(it) }
        }
        val list = root.getJSONArray("exhibitors")
        val exhibitors = (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            val booths = o.getJSONArray("b")
            val codes = o.optJSONArray("c")
            Exhibitor(
                name = o.getString("n"),
                nameEn = if (o.has("e")) o.getString("e") else null,
                booths = (0 until booths.length()).map { booths.getString(it) },
                group = o.getString("k"),
                mark = o.optInt("j", 0),
                codes = if (codes == null) emptyList() else (0 until codes.length()).map { codes.getString(it) },
            )
        }
        return ExhibitorData(
            retrievedOn = root.getString("retrievedOn"),
            source = root.getString("source"),
            categories = ProductCategories(large, middle, small),
            synonyms = synonyms,
            exhibitors = exhibitors,
        )
    }
}

/** 出展社データは起動時に読まず、最初に必要になったときに1回だけ読む */
object ExhibitorRepository {
    private const val ASSET_NAME = "exhibitors.json"

    @Volatile
    private var cached: ExhibitorData? = null

    fun get(context: Context): ExhibitorData {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }
    }

    private fun load(context: Context): ExhibitorData =
        context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
            ExhibitorParser.parse(reader.readText())
        }
}
