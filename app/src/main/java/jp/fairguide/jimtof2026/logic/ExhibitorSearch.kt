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
    /** 見学テーマ（ReportPreset）で候補に入った理由 */
    val presetReason: String? = null,
    /** 検索語と少し表記が違う（濁点・小さい文字の違いや、数文字の打ち間違い）が、近いので出した */
    val fuzzy: Boolean = false,
)

/**
 * 出展社の検索。社名（日本語・英語）、小間番号、出展製品分類、よく使う言い換えで探す。
 * 表記ゆれ（全角・半角、カタカナ・ひらがな、大文字・小文字、長音・中黒・空白）は吸収する。
 * 複数の語を空白で区切ると、すべての語に当てはまる会社だけを出す。
 * ぴったり合う会社が少ないときは、濁点・小さい文字の違いや数文字の打ち間違いまで許した「近い表記」の会社も、下のほうに出す
 * （いちばん近い表記のものだけ）。
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
    ) {
        /** あいまい検索用（濁点・小さい文字をそろえた形） */
        val looseCore: String = loosen(core)
        val looseEn: String? = en?.let { loosen(it) }
        val looseCategories: List<Pair<String, String>> = categoryText.map { (n, d) -> loosen(n) to d }
    }

    /** 検索語1つぶん。言い換えは語ごとに先に解決しておく */
    private class Token(val raw: String, val norm: String, val synonymCodes: List<String>) {
        val latin: Boolean = norm.all { it in 'a'..'z' || it in '0'..'9' }
        val shortLatin: Boolean = latin && norm.length <= 2
        val loose: String = loosen(norm)

        /** あいまい検索で許す文字の違いの数。-1 なら、この語はあいまい検索をしない（小間番号・短い語など） */
        val fuzzyLimit: Int = when {
            norm.length < 3 || norm.all { it in '0'..'9' } -> -1
            BOOTH_LIKE.matches(BoothCode.normalize(raw).lowercase()) || HALL_JP.matches(norm) -> -1
            else -> fuzzyLimitFor(loose.length)
        }
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
    fun search(
        query: String,
        limit: Int = 200,
        filter: ((Exhibitor) -> Boolean)? = null,
        preset: ReportPreset? = null,
    ): List<ExhibitorHit> {
        val tokens = query.trim().split(WHITESPACE).mapNotNull { raw ->
            val t = normalize(raw)
            if (t.isEmpty()) null else Token(raw, t, synonymCodes(t))
        }
        if (tokens.isEmpty() && filter == null && preset == null) return emptyList()
        val scored = ArrayList<Pair<Entry, ExhibitorHit>>()
        val distances = HashMap<Exhibitor, Int>() // 近い表記で当てた会社 → いちばん大きい文字の違い
        for (e in entries) {
            if (filter != null && !filter(e.exhibitor)) continue
            var total = 0
            var matched: String? = null
            var ok = true
            var fuzzy = false
            var fuzzyDistance = 0
            var presetReason: String? = null
            if (preset != null) {
                val m = preset.evaluate(e.exhibitor, data.categories)
                if (m == null) continue
                total += m.score
                presetReason = m.reason
            }
            for (t in tokens) {
                var (s, cat) = scoreToken(e, t)
                if (s == 0) {
                    // ぴったり合わなければ、近い表記で探す
                    val f = fuzzyToken(e, t)
                    if (f == null) {
                        ok = false
                        break
                    }
                    s = f.score
                    cat = f.category
                    fuzzy = true
                    if (f.distance > fuzzyDistance) fuzzyDistance = f.distance
                }
                total += s
                if (matched == null && cat != null) matched = cat
            }
            if (ok) {
                scored += e to ExhibitorHit(e.exhibitor, total, matched, presetReason, fuzzy)
                if (fuzzy) distances[e.exhibitor] = fuzzyDistance
            }
        }
        // 点数が同じなら、会場を歩く順（東→西→南、小間番号順）。同じ小間では本体の出展者を先に
        scored.sortWith(
            compareByDescending<Pair<Entry, ExhibitorHit>> { it.second.score }
                .thenBy { it.first.hallRank }
                .thenBy { it.first.booths.first() }
                .thenBy { it.first.exhibitor.mark }
                .thenBy { it.first.exhibitor.name },
        )
        var hits = scored.map { it.second }
        if (hits.count { !it.fuzzy } >= FUZZY_ONLY_IF_FEWER_THAN) {
            // ぴったり合う会社が十分あるなら、近い表記の会社は出さない（ノイズになる）
            hits = hits.filter { !it.fuzzy }
        } else if (distances.isNotEmpty()) {
            // 近い表記は、いちばん近いものだけ（1文字違いがあれば、2文字違いは出さない）
            val closest = distances.values.min()
            hits = hits.filter { !it.fuzzy || distances[it.exhibitor] == closest }
        }
        return if (hits.size > limit) hits.subList(0, limit) else hits
    }

    private class FuzzyMatch(val score: Int, val category: String?, val distance: Int)

    /**
     * 近い表記で1語ぶんの得点。0 なら不一致。ぴったり合う得点（35 以上）より必ず低くする。
     * 1) 濁点・小さい文字をそろえると含まれる（「やまさき まさく」→ ヤマザキマザック）
     * 2) 数文字の違い（置換・抜け・余分・隣どうしの入れ替え）で社名のどこかに合う
     */
    private fun fuzzyToken(e: Entry, token: Token): FuzzyMatch? {
        val k = token.fuzzyLimit
        if (k < 0) return null
        val t = token.loose
        if (e.looseCore.contains(t) || e.looseEn?.contains(t) == true) return FuzzyMatch(30, null, 0)
        if (!token.latin || t.length >= 4) {
            for ((norm, display) in e.looseCategories) {
                if (norm.contains(t)) return FuzzyMatch(25, display, 0)
            }
        }
        if (k == 0) return null
        var best = substringDistance(t, e.looseCore, k)
        if (best > 1 && e.looseEn != null) best = minOf(best, substringDistance(t, e.looseEn, k))
        return if (best <= k) FuzzyMatch(28 - 4 * best, null, best) else null
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

        /** ぴったり合う会社がこの数以上あれば、近い表記の会社は出さない */
        const val FUZZY_ONLY_IF_FEWER_THAN = 10

        private fun isShortLatin(t: String): Boolean =
            t.length <= 2 && t.all { it in 'a'..'z' || it in '0'..'9' }

        /** 語の長さ（ゆるい形）に応じて、あいまい検索で許す文字の違いの数。3文字は濁点・小さい文字の違いだけ */
        fun fuzzyLimitFor(length: Int): Int = when {
            length < 3 -> -1
            length == 3 -> 0
            length <= 6 -> 1
            length <= 10 -> 2
            else -> 3
        }

        private val LOOSE: Map<Char, Char> = buildMap {
            fun pairs(from: String, to: String) = from.forEachIndexed { i, c -> put(c, to[i]) }
            pairs("ぁぃぅぇぉっゃゅょゎゕゖ", "あいうえおつやゆよわかけ")
            pairs("がぎぐげござじずぜぞだぢづでどばびぶべぼ", "かきくけこさしすせそたちつてとはひふへほ")
            pairs("ぱぴぷぺぽ", "はひふへほ")
            put('ゔ', 'う')
            put('ゐ', 'い')
            put('ゑ', 'え')
        }

        /** あいまい検索用に、小さい文字を大きく、濁点・半濁点を外す（normalize したあとの文字列に使う） */
        fun loosen(normalized: String): String {
            val sb = StringBuilder(normalized.length)
            for (ch in normalized) sb.append(LOOSE[ch] ?: ch)
            return sb.toString()
        }

        /**
         * pattern が text のどこかの部分文字列と、何文字の違い（置換・抜け・余分・隣どうしの入れ替え）で合うか。
         * 違いが k を超えると分かった時点で k+1 を返す。
         */
        fun substringDistance(pattern: String, text: String, k: Int): Int {
            val m = pattern.length
            val n = text.length
            if (m == 0) return 0
            if (n == 0) return minOf(m, k + 1)
            var prev2 = IntArray(n + 1)
            var prev = IntArray(n + 1) // 0行目は全部 0（部分文字列はどこから始まってもよい）
            var cur = IntArray(n + 1)
            for (i in 1..m) {
                cur[0] = i
                var rowMin = i
                val pc = pattern[i - 1]
                for (j in 1..n) {
                    val tc = text[j - 1]
                    var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (pc == tc) 0 else 1)
                    if (i > 1 && j > 1 && pc == text[j - 2] && pattern[i - 2] == tc) v = minOf(v, prev2[j - 2] + 1)
                    cur[j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > k) return k + 1
                val t = prev2
                prev2 = prev
                prev = cur
                cur = t
            }
            var best = prev[0]
            for (j in 1..n) if (prev[j] < best) best = prev[j]
            return best
        }

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
