package jp.fairguide.jimtof2026.logic

import jp.fairguide.jimtof2026.data.Exhibitor
import jp.fairguide.jimtof2026.data.ExhibitorData
import jp.fairguide.jimtof2026.data.NoveltyData
import jp.fairguide.jimtof2026.data.ProductCategories
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
 * 複数の語を空白で区切ると、すべての語に当てはまる会社だけを出す。
 */
class ExhibitorIndex(val data: ExhibitorData, novelty: NoveltyData? = null) {

    private class Entry(
        val exhibitor: Exhibitor,
        val name: String,
        val core: String,
        val en: String?,
        /** 社名を空白・記号で区切った語（英語名の「MAZAK」などで前方一致させる） */
        val words: List<String>,
        val booths: List<String>,
        val hallRank: Int,
        /** 自分のコードと親のコード（s6 → s6, m1, g1） */
        val codes: Set<String>,
        /** 正規化した分類名と表示用の分類名（親を含む） */
        val categoryText: List<Pair<String, String>>,
        /** ノベルティの品名（正規化済み）。「トート」「キーホルダー」などで探せる */
        val noveltyText: List<String>,
    )

    /** 検索語1つぶん。言い換えは語ごとに先に解決しておく */
    private class Token(val raw: String, val norm: String, val synonymCodes: List<String>) {
        val latin: Boolean = norm.all { it in 'a'..'z' || it in '0'..'9' }
        val shortLatin: Boolean = latin && norm.length <= 2
    }

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
            val name = normalize(ex.name)
            val first = BoothCode.normalize(ex.booths.first())
            Entry(
                exhibitor = ex,
                name = name,
                core = stripCorporate(name),
                en = ex.nameEn?.let { normalize(it) },
                words = (listOf(ex.name) + listOfNotNull(ex.nameEn))
                    .flatMap { WORD_SPLIT.split(Normalizer.normalize(it, Normalizer.Form.NFKC).lowercase()) }
                    .map { normalize(it) }
                    .filter { it.isNotEmpty() },
                booths = ex.booths.map { BoothCode.normalize(it) },
                hallRank = BoothCode.hallRank(first),
                codes = expanded,
                categoryText = texts,
                noveltyText = novelty?.infoFor(ex).orEmpty().map { normalize(it.item) },
            )
        }
        synonymKeys = data.synonyms.map { (k, v) -> normalize(k) to v }
    }

    private fun parentsOf(code: String, cats: ProductCategories): List<String> {
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

    /**
     * filter を渡すと、それに当てはまる会社だけを対象にする（ノベルティありの絞り込みなど）。
     * 検索語が空でも filter があれば、当てはまる会社をすべて会場の歩く順に返す。
     */
    fun search(query: String, limit: Int = 200, filter: ((Exhibitor) -> Boolean)? = null): List<ExhibitorHit> {
        val tokens = query.trim().split(WHITESPACE).mapNotNull { raw ->
            val t = normalize(raw)
            if (t.isEmpty()) null else Token(raw, t, synonymCodes(t))
        }
        if (tokens.isEmpty() && filter == null) return emptyList()
        val scored = ArrayList<Pair<Entry, ExhibitorHit>>()
        for (e in entries) {
            if (filter != null && !filter(e.exhibitor)) continue
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
            if (ok) scored += e to ExhibitorHit(e.exhibitor, total, matched)
        }
        // 点数が同じなら、会場を歩く順（東→西→南、小間番号順）。同じ小間では本体の出展者を先に
        scored.sortWith(
            compareByDescending<Pair<Entry, ExhibitorHit>> { it.second.score }
                .thenBy { it.first.hallRank }
                .thenBy { it.first.booths.first() }
                .thenBy { it.first.exhibitor.mark }
                .thenBy { it.first.exhibitor.name },
        )
        val hits = scored.map { it.second }
        return if (hits.size > limit) hits.subList(0, limit) else hits
    }

    /**
     * 言い換えの解決。完全一致 → 入力が言い換え語の一部 → 入力が言い換え語を含む（いちばん長い語だけ）の順。
     * 「立旋盤」で「旋盤」まで拾って、立旋盤以外の旋盤メーカーが混ざるのを防ぐ。
     */
    private fun synonymCodes(t: String): List<String> {
        if (t.length < 2) return emptyList()
        val exact = synonymKeys.filter { it.first == t }
        if (exact.isNotEmpty() || isShortLatin(t)) return exact.flatMap { it.second }.distinct()
        val partial = synonymKeys.filter { it.first.contains(t) }
        if (partial.isNotEmpty()) return partial.flatMap { it.second }.distinct()
        if (t.length < 3) return emptyList()
        val contained = synonymKeys.filter { t.contains(it.first) }
        val longest = contained.maxOfOrNull { it.first.length } ?: return emptyList()
        return contained.filter { it.first.length == longest }.flatMap { it.second }.distinct()
    }

    /** 1語ぶんの得点。0 なら不一致 */
    private fun scoreToken(e: Entry, token: Token): Pair<Int, String?> {
        val t = token.norm
        // 小間番号（E2019、W3104、WA015、E1009-2、E7 など）
        val boothToken = BoothCode.normalize(token.raw)
        if (boothToken.isNotEmpty() && BOOTH_LIKE.matches(boothToken.lowercase())) {
            if (e.booths.any { it == boothToken }) return 120 to null
            if (e.booths.any { it.startsWith(boothToken) }) return 100 to null
        }
        // 「東1」「西3」「南4ホール」のようなホール名（正規化で「ホール」は「ほる」になる）
        HALL_JP.matchEntire(t)?.let { m ->
            val prefix = when (m.groupValues[1]) {
                "東" -> "E"
                "西" -> "W"
                else -> "S"
            } + m.groupValues[2]
            if (e.booths.any { it.startsWith(prefix) }) return 100 to null
        }
        // 社名。英数字2文字以下（MC、AM など）は語の頭だけで比べる（「SYSTEM CO」の「MC」に当たらないように）
        if (e.core.startsWith(t) || e.name.startsWith(t)) return 90 to null
        if (e.en?.startsWith(t) == true) return 80 to null
        if (e.words.any { it.startsWith(t) }) return 75 to null
        if (!token.shortLatin) {
            // 英数字だけの語は、空白を詰めた社名ではなく語の中で探す（「UNITED MACHINING」の「EDM」を防ぐ）
            if (!token.latin && e.core.contains(t)) return 70 to null
            if (e.words.any { it.contains(t) }) return 60 to null
        }
        // 分類名（自分の分類と、その親の分類）
        if (t.length >= 2 && !token.shortLatin) {
            for ((norm, display) in e.categoryText) {
                if (norm.contains(t)) return 40 to display
            }
        }
        // ノベルティの品名（トートバッグ、キーホルダーなど）
        if (t.length >= 2 && !token.shortLatin && e.noveltyText.any { it.contains(t) }) return 38 to null
        // 言い換え（立旋盤 → NC旋盤（立軸） など）。分類名に直接当たるものより下に並べる
        if (token.synonymCodes.isNotEmpty()) {
            val hit = token.synonymCodes.firstOrNull { it in e.codes }
            if (hit != null) return 35 to data.categories.name(hit)
        }
        return 0 to null
    }

    companion object {
        private val WHITESPACE = Regex("[\\s　]+")
        private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")
        private val BOOTH_LIKE = Regex("^(wa\\d{0,3}|[ews]\\d{1,4}(-\\d{1,2})?)$")
        private val HALL_JP = Regex("^(東|西|南)(\\d)(ほる)?$")
        private val REMOVE = Regex("[\\s　・･、，,．.\\-－—–ー〜~（）()\\[\\]「」『』/／&＆'\"’“”!！?？:：;；|]")
        private val CORPORATE = Regex(
            "(株式会社|有限会社|合同会社|合資会社|一般社団法人|公益社団法人|一般財団法人|公益財団法人|学校法人|国立研究開発法人)",
        )

        private fun isShortLatin(t: String): Boolean =
            t.length <= 2 && t.all { it in 'a'..'z' || it in '0'..'9' }

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

        /** 会社の種類を表す語を取り除く（「株式会社オーエム製作所」→「おえむ製作所」） */
        fun stripCorporate(normalized: String): String {
            val s = CORPORATE.replace(normalized, "")
            return s.ifEmpty { normalized }
        }
    }
}
