package jp.fairguide.jimtof2026.logic

import jp.fairguide.jimtof2026.data.Exhibitor
import jp.fairguide.jimtof2026.data.ProductCategories

/** 候補に入った理由と点数（点数が高いほど、その見学テーマに近い） */
class PresetMatch(val score: Int, val reason: String)

/**
 * 見学レポートの項目ごとに、見て回る候補の会社を絞り込むための設定。
 * 公式カタログに会社が自分で登録した「出展製品分類」と社名だけが根拠なので、
 * 分類に出ない提案（ロボットの組み込みなど）は拾えない。候補の入口として使い、実際の展示は会場で確かめる。
 */
enum class ReportPreset(
    val label: String,
    val shortLabel: String,
    val description: String,
) {
    /** 項目1：競合各社の重点業界・展示内容・付加機能 */
    COMPETITOR(
        label = "①競合",
        shortLabel = "競合",
        description = "NC旋盤（立軸）・ターニングセンタ・融合機として出展を登録している会社です（自社は除く）。" +
            "立旋盤の登録があり、登録分野が絞られている（専業に近い）会社ほど上に並べています。商社・代理店も混ざります。",
    ),

    /** 項目2：省人化・無人化仕様の提案動向 */
    AUTOMATION(
        label = "②省人化・無人化",
        shortLabel = "省人化・無人化",
        description = "生産管理・NC支援システム、自動工具交換、切屑処理、ツールプリセッタの出展者と、" +
            "社名にロボット・自動化などを含む会社です。分類に出ない提案もあるので、候補の入口として使ってください。",
    ),

    /** 項目3：出展機と研削機能 */
    GRINDING(
        label = "③研削",
        shortLabel = "研削",
        description = "研削盤・仕上げ盤・砥石の出展者です。旋盤と研削盤の両方を出している会社（旋削と研削をつなぐ提案が見られそう）を先に並べています。",
    );

    fun evaluate(ex: Exhibitor, cats: ProductCategories): PresetMatch? = when (this) {
        COMPETITOR -> competitor(ex, cats)
        AUTOMATION -> automation(ex, cats)
        GRINDING -> grinding(ex, cats)
    }

    private fun competitor(ex: Exhibitor, cats: ProductCategories): PresetMatch? {
        // 自社（オーエム製作所 E2019）は競合に入れない
        if (ex.booths.any { BoothCode.normalize(it) == OWN_BOOTH }) return null
        val m = weighted(ex, cats, COMPETITOR_WEIGHTS) ?: return null
        // 登録している分野が少ない会社（専業に近い）ほど上に。商社・代理店は分野が広いので下がる
        val focus = maxOf(0, 60 - 4 * ex.codes.size)
        return PresetMatch(m.score + focus, m.reason)
    }

    private fun automation(ex: Exhibitor, cats: ProductCategories): PresetMatch? {
        val byCategory = weighted(ex, cats, AUTOMATION_WEIGHTS)
        val byName = AUTOMATION_NAME.containsMatchIn(ex.name)
        return when {
            byName && byCategory != null ->
                PresetMatch(byCategory.score + NAME_WEIGHT, "社名にロボット・自動化など／" + byCategory.reason)
            byName -> PresetMatch(NAME_WEIGHT, "社名にロボット・自動化など")
            else -> byCategory
        }
    }

    private fun grinding(ex: Exhibitor, cats: ProductCategories): PresetMatch? {
        val grinders = ex.codes.filter { it in GRINDER_CODES }
        val finishing = ex.codes.filter { it in FINISHING_CODES }
        val wheels = ex.codes.any { it == "m33" || it == "g7" }
        val lathe = ex.codes.any { it in LATHE_CODES }
        if (grinders.isEmpty() && finishing.isEmpty() && !wheels) return null
        var score = 0
        val reasons = ArrayList<String>()
        if (grinders.isNotEmpty()) {
            score += 100
            reasons += grinders.mapNotNull { cats.name(it) }.take(2).joinToString("・").removeSuffix("（NC含む）")
        } else if (finishing.isNotEmpty()) {
            score += 30
            reasons += finishing.mapNotNull { cats.name(it) }.take(2).joinToString("・")
        }
        if (wheels) {
            score += 40
            reasons += "砥石・研磨材"
        }
        if (lathe && (grinders.isNotEmpty() || finishing.isNotEmpty())) {
            score += 80
            reasons += "旋盤・複合機も出展"
        }
        return PresetMatch(score, reasons.filter { it.isNotBlank() }.joinToString("／"))
    }

    private fun weighted(ex: Exhibitor, cats: ProductCategories, weights: Map<String, Int>): PresetMatch? {
        val matched = ex.codes.filter { it in weights }
        if (matched.isEmpty()) return null
        val score = matched.sumOf { weights.getValue(it) }
        val reason = matched.sortedByDescending { weights.getValue(it) }
            .mapNotNull { cats.name(it)?.removeSuffix("（NC含む）") }
            .take(3)
            .joinToString("・")
        return PresetMatch(score, reason)
    }

    companion object {
        /** 自社の小間（株式会社オーエム製作所） */
        const val OWN_BOOTH = "E2019"

        private const val NAME_WEIGHT = 100

        private val COMPETITOR_WEIGHTS = mapOf("s6" to 100, "s7" to 50, "s29" to 30)

        private val AUTOMATION_WEIGHTS = mapOf(
            "s261" to 40, // 生産管理システム
            "s258" to 40, // NC支援システム
            "s166" to 30, // 自動工具交換装置
            "s168" to 30, // 切屑処理装置
            "s167" to 20, // ツールプリセッタ
        )

        private val AUTOMATION_NAME = Regex("ロボ|オートメ|自動化|ハンドリング|搬送|ローダ|ファナック|安川|川崎重工|デンソー")

        /** 円筒・内面・平面・心なし・ならい・工具・ねじ・ジグ・立形・万能・カム・その他研削盤、歯車研削盤 */
        private val GRINDER_CODES = setOf(
            "s30", "s31", "s32", "s33", "s34", "s35", "s36", "s38", "s39", "s40", "s41", "s42", "s50",
        )

        /** ホーニング盤・超仕上げ盤・ラップ盤・その他の仕上げ盤 */
        private val FINISHING_CODES = setOf("s43", "s44", "s45", "s46")

        /** 旋盤系（普通・自動・NC横軸・NC立軸・ターニングセンタ・その他・融合機・超精密旋削） */
        private val LATHE_CODES = setOf("s1", "s3", "s5", "s6", "s7", "s8", "s29", "s62")
    }
}
