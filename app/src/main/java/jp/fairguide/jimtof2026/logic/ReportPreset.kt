package jp.fairguide.jimtof2026.logic

import jp.fairguide.jimtof2026.data.Exhibitor
import jp.fairguide.jimtof2026.data.ProductCategories

/** 候補に入った理由と点数（点数が高いほど、その見学テーマに近い） */
class PresetMatch(val score: Int, val reason: String)

/**
 * 見学テーマ（西展示棟担当：切削工具・保持具・測定機器）ごとに、見て回る候補の会社を絞り込むための設定。
 * 公式カタログに会社が自分で登録した「出展製品分類」だけが根拠なので、分類に出ない提案は拾えない。
 * 候補の入口として使い、実際の展示は会場で確かめる。
 */
enum class ReportPreset(
    val label: String,
    val shortLabel: String,
    val description: String,
) {
    /** 項目①：切削工具（チップ・エンドミル・タップなど） */
    CUTTING(
        label = "①切削工具",
        shortLabel = "切削工具",
        description = "刃先交換工具・エンドミル・ドリル・タップ・バイトと、コーティングチップ・CBN／ダイヤ工具を出展登録している会社です。" +
            "登録している工具の分類が多い会社ほど上に並べています。工具の商社も混ざります。",
    ),

    /** 項目②：保持具（チャック・ホルダー） */
    HOLDER(
        label = "②保持具",
        shortLabel = "保持具",
        description = "ツールホルダ（ミーリングチャック、アーバ、タップホルダ、ボーリング）と、ワーク保持具（パワーチャック、コレット、磁力チャック、センタ）を" +
            "出展登録している会社です。治具・クランプは分類に出ないことがあるので、候補の入口として使ってください。",
    ),

    /** 項目③：測定機器 */
    MEASURING(
        label = "③測定機器",
        shortLabel = "測定機器",
        description = "三次元測定機・形状測定機・測長機・ゲージ類などを出展登録している会社です。" +
            "試験機だけの会社は下に並びます。機上計測（プローブ）は分類に出ないことがあります。",
    );

    fun evaluate(ex: Exhibitor, cats: ProductCategories): PresetMatch? = when (this) {
        CUTTING -> weighted(ex, cats, CUTTING_WEIGHTS)
        HOLDER -> weighted(ex, cats, HOLDER_WEIGHTS)
        MEASURING -> weighted(ex, cats, MEASURING_WEIGHTS)
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
        private val CUTTING_WEIGHTS = mapOf(
            "s198" to 100, // 刃先交換工具
            "s201" to 80, // フライス、エンドミル（超硬）
            "s200" to 80, // ドリル、リーマ（超硬）
            "s184" to 60, // コーティングチップ
            "s199" to 50, // バイト（超硬）
            "s192" to 40, // エンドミル（高速度鋼）
            "s190" to 40, // ドリル（高速度鋼）
            "s195" to 40, // タップ、ダイス
            "s211" to 40, // ダイヤモンド切削工具
            "s213" to 40, // CBN およびWBN工具
            "s191" to 30, // フライス、リーマ
            "s193" to 30, // ミーリングカッタ
            "s194" to 30, // ギヤーカッタ、ブローチ
            "s196" to 30, // バイト（高速度鋼）
            "s214" to 30, // サーメット工具
            "s215" to 30, // セラミック工具
            "s183" to 20, // 超硬合金
            "s185" to 10, // サーメット
            "s186" to 10, // セラミックス
            "s187" to 10, // ダイヤモンド（天然・焼結）
            "s188" to 10, // CBN, WBN
            "s202" to 10, // 耐摩耗工具
        )

        private val HOLDER_WEIGHTS = mapOf(
            "s145" to 100, // ミーリングチャックおよび関連附属品
            "s149" to 100, // アーバ・ホルダ類
            "s139" to 100, // パワーチャック
            "s140" to 80, // コレットチャック
            "s146" to 60, // タップホルダ
            "s147" to 60, // ボーリングバー・ボーリングヘッド
            "s151" to 60, // 旋盤用工具保持具
            "s138" to 50, // ハンドチャック
            "s141" to 50, // 磁力チャック
            "s150" to 40, // ドリルチャック
            "s148" to 30, // スリーブ・ソケット類
            "s142" to 30, // レースセンタ
            "s143" to 30, // 回転センタ
            "s152" to 20, // その他（工具保持具）
            "s144" to 20, // その他（工作物保持具）
        )

        private val MEASURING_WEIGHTS = mapOf(
            "s233" to 100, // 三次元座標測定機器
            "s238" to 80, // 真円度、真直度および形状測定機器
            "s237" to 70, // 表面あらさ測定機器
            "s232" to 60, // 測長機
            "s239" to 60, // 自動精密測定機器
            "s241" to 50, // 万能測定顕微鏡
            "s244" to 50, // 投影機
            "s236" to 50, // 歯車測定機器
            "s235" to 40, // ねじ測定機器
            "s234" to 40, // 角度測定機器
            "s242" to 40, // 工具顕微鏡
            "s227" to 40, // ダイヤルゲージ
            "s226" to 40, // マイクロメータ
            "s225" to 40, // ノギス
            "s229" to 40, // ゲージ
            "s228" to 30, // ブロックゲージ
            "s230" to 30, // 空気マイクロメータ
            "s231" to 30, // 電気マイクロメータ
            "s240" to 30, // デジタル・スケール
            "s246" to 30, // その他の測定機
            "s243" to 20, // 金属顕微鏡
            "s248" to 20, // 硬さ試験機
            "s245" to 10, // 回転計
            "s247" to 10, // 材料試験機
            "s249" to 10, // 万能試験機
            "s254" to 10, // つり合い試験機
            "s256" to 10, // その他の試験機器
        )
    }
}
