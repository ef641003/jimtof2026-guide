package jp.fairguide.jimtof2026.logic

import jp.fairguide.jimtof2026.data.Exhibitor
import jp.fairguide.jimtof2026.data.ExhibitorData
import java.text.Normalizer

/** 検索結果1件。なぜ当たったかを matchedCategory で示す */
data class ExhibitorHit(
    val exhibitor: Exhibitor,
    val score: Int,
    val matchedCategory: String?,
)

/**
 * 出展社の検索。社名（日本語・英語）、小間番号、出展製品分類、よく使う言い換えで探す。
 * 表記ゆれ（全角・半角、カタカナ・ひらがな、大文字・小文字、長音・中黒・空白）は吸収する。
 */
class ExhibitorIndex(val data: ExhibitorData) {

    private class Entry(
        val exhibitor: Exhibitor,
        val name: String,
        val core: String,
        val en: String?,
        val booths: List<String>,
        /** 自分のコードと親のコード（s6 → s6, m1, g1） */
        val codes: Set<String>,
        /** コード → 正規化した分類名（親を含む） */
        val categoryText: List<Pair<String, String>>,
    )

    private val entries: List<Entry>
    private val synonymKeys: List<Pair<String, List<String>>>

    init {
        val cats = data.categories
        entries = data.exhibitors.map { ex ->
            val expanded = LinkedHashSet<String>()
            val texts = ArrayList<Pair<String, String>>()
            for (code in ex.codes) {
                expanded += code
                parentsOf(code, cats).forEach { expanded += it }
                for (n in cats.namesWithParents(code)) texts += normalize(n) to n
            }
            Entry(
                exhibitor = ex,
                name = normalize(ex.name),
                core = stripCorporate(normalize(ex.name)),
                en = ex.nameEn?.let { normalize(it) },
                booths = ex.booths.map { BoothCode.normalize(it) },
                codes = expanded,
                categoryText = texts,
            )
        }
        synonymKeys = data.synonyms.map { (k, v) -> normalize(k) to v }
    }

    private fun parentsOf(code: String, cats: jp.fairguide.jimtof2026.data.ProductCategories): List<String> {
        val out = ArrayList<String>(2)
        when (code.firstOrNull()) {
            's' -> {
                val m = cats.small[code.drop(1)]?.first ?: return out
                out += "m$m"
                cats.middle[m]?.first?.let { out += "g$it" }
            }
            'm' -> cats.middle[code.drop(1)]?.first?.let { out += "g$it" }
        }
        return out
    }

    fun search(query: String, limit: Int = 200): List<ExhibitorHit> {
        val tokens = query.trim().split(WHITESPACE).filter { normalize(it).isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val hits = ArrayList<ExhibitorHit>()
        for (e in entries) {
            var total = 0
            var matched: String? = null
            var ok = true
            for (t in tokens) {
                val (s, cat) = scoreToken(e, t)
                if (s == 0) {
                    ok = false
                    break
                }
                total += s
                if (matched == null && cat != null) matched = cat
            }
            if (ok) hits += ExhibitorHit(e.exhibitor, total, matched)
        }
        hits.sortWith(
            compareByDescending<ExhibitorHit> { it.score }
                .thenBy { it.exhibitor.mark }
                .thenBy { it.exhibitor.group == "海外" }
                .thenBy { it.exhibitor.name.length }
                .thenBy { it.exhibitor.name },
        )
        return if (hits.size > limit) hits.subList(0, limit) else hits
    }

    /** 1語ぶんの得点。0 なら不一致 */
    private fun scoreToken(e: Entry, raw: String): Pair<Int, String?> {
        val t = normalize(raw)
        // 小間番号（E2019、W3104、WA015、E1009-2、E7 など）
        val boothToken = BoothCode.normalize(raw)
        if (boothToken.isNotEmpty() && BOOTH_LIKE.matches(boothToken.lowercase())) {
            if (e.booths.any { it == boothToken }) return 120 to null
            if (e.booths.any { it.startsWith(boothToken) }) return 100 to null
        }
        // 「東1」「西3」「南4」のようなホール名
        HALL_JP.matchEntire(t)?.let { m ->
            val prefix = when (m.groupValues[1]) {
                "東" -> "E"
                "西" -> "W"
                else -> "S"
            } + m.groupValues[2]
            if (e.booths.any { it.startsWith(prefix) }) return 100 to null
        }
        // 社名
        if (e.core.startsWith(t) || e.name.startsWith(t)) return 90 to null
        if (e.core.contains(t)) return 70 to null
        e.en?.let { en ->
            if (en.startsWith(t)) return 80 to null
            if (en.contains(t)) return 60 to null
        }
        // 分類名（自分の分類と、その親の分類）。英数字2文字以下（MC、AM など）は言い換えの完全一致だけ
        val shortLatin = t.length <= 2 && t.all { it in 'a'..'z' || it in '0'..'9' }
        if (t.length >= 2 && !shortLatin) {
            for ((norm, display) in e.categoryText) {
                if (norm.contains(t)) return 40 to display
            }
        }
        if (t.length >= 2) {
            // 言い換え（立旋盤 → NC旋盤（立軸） など）
            for ((key, codes) in synonymKeys) {
                val match = if (shortLatin) key == t else (key == t || key.contains(t) || (t.length >= 3 && t.contains(key)))
                if (match) {
                    val hit = codes.firstOrNull { it in e.codes } ?: continue
                    return 45 to data.categories.name(hit)
                }
            }
        }
        return 0 to null
    }

    companion object {
        private val WHITESPACE = Regex("[\\s　]+")
        private val BOOTH_LIKE = Regex("^(wa\\d{0,3}|[ews]\\d{1,4}(-\\d{0,2})?)$")
        private val HALL_JP = Regex("^(東|西|南)(\\d)(ホール)?$")
        private val REMOVE = Regex("[\\s　・･、，,．.\\-－—–ー〜~（）()\\[\\]「」『』/／&＆'\"’“”!！?？:：;；|]")
        private val CORPORATE = Regex(
            "(株式会社|有限会社|合同会社|合資会社|一般社団法人|公益社団法人|一般財団法人|公益財団法人|学校法人|国立研究開発法人|\\(株\\)|\\(有\\))",
        )

        /** 検索キー用に表記をそろえる */
        fun normalize(input: String): String {
            val nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC).lowercase()
            val sb = StringBuilder(nfkc.length)
            for (ch in nfkc) {
                // カタカナ → ひらがな（ヴ・ヵ・ヶ も含む）
                sb.append(if (ch in 'ァ'..'ヶ') (ch - 0x60) else ch)
            }
            return REMOVE.replace(sb, "")
        }

        /** 会社の種類を表す語を取り除く（「株式会社オーエム製作所」→「おーえむ製作所」） */
        fun stripCorporate(normalized: String): String {
            val s = CORPORATE.replace(normalized, "")
            return s.ifEmpty { normalized }
        }
    }
}
