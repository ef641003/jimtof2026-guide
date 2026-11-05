package jp.fairguide.jimtof2026.data

import android.content.Context
import jp.fairguide.jimtof2026.logic.ReportPreset

/**
 * 見学の担当（だれの視点で回るか）。レポートの項目・おすすめの会社・検索の候補・ルートの出発地点が変わる。
 * 次の展示会では、ここに担当を足し、report_*.json を差し替えれば使い回せる。
 */
enum class ViewerProfile(
    val id: String,
    val label: String,
    /** 項目の一覧（選ぶ画面に出す） */
    val items: String,
    val description: String,
    /** おすすめの会社と見どころ（assets のファイル名） */
    val reportAsset: String,
    /** 検索タブに出す見学テーマの候補 */
    val presets: List<ReportPreset>,
    /** ルートの出発地点の初期値（VenueWalk.Start の id） */
    val routeStart: String,
) {
    MAKER(
        id = "maker",
        label = "立旋盤メーカー視点",
        items = "①競合 ②省人化・無人化 ⑤展示方法",
        description = "競合各社の重点業界・展示内容・付加機能、省人化・無人化の提案、次回に取り入れたい展示方法を見て回る",
        reportAsset = "report_oshima.json",
        presets = listOf(ReportPreset.COMPETITOR, ReportPreset.AUTOMATION),
        routeStart = "main",
    ),
    WEST(
        id = "west",
        label = "西展示棟担当",
        items = "①切削工具 ②保持具 ③測定機器",
        description = "切削工具（チップ・エンドミル・タップ）、保持具（チャック・ホルダー）、測定機器の展示を見て回る",
        reportAsset = "report_west.json",
        presets = listOf(ReportPreset.CUTTING, ReportPreset.HOLDER, ReportPreset.MEASURING),
        routeStart = "west",
    );

    companion object {
        fun of(id: String?): ViewerProfile? = entries.firstOrNull { it.id == id }
    }
}

/** 選んだ担当（端末の中だけに保存） */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): ViewerProfile? = ViewerProfile.of(prefs.getString(KEY, null))

    fun save(profile: ViewerProfile) {
        prefs.edit().putString(KEY, profile.id).apply()
    }

    private companion object {
        const val KEY = "profile"
    }
}
