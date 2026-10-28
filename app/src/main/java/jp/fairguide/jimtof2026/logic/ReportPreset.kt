package jp.fairguide.jimtof2026.logic

import jp.fairguide.jimtof2026.data.Exhibitor
import jp.fairguide.jimtof2026.data.ProductCategories

/** 候補に入った理由と点数（点数が高いほど、その見学テーマに近い） */
class PresetMatch(val score: Int, val reason: String)

/**
 * 見学レポートの項目ごとに、見て回る候補の会社を絞り込むための設定。
 * 公式カタログに会社が自分で登録した「出展製品分類」と社名だけが根拠なので、
 * 分類に出ない提案（ロボットの組み込みなど）は拾えない。候補の入口として使い、実際の展示は会場で確かめる。
 * 項目③④は自社内のことなので候補は作らない。
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
    );

    fun evaluate(ex: Exhibitor, cats: ProductCategories): PresetMatch? = when (this) {
        COMPETITOR -> competitor(ex, cats)
        AUTOMATION -> automation(ex, cats)
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
    }
}
