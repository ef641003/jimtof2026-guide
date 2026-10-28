package jp.fairguide.jimtof2026.logic

import jp.fairguide.jimtof2026.data.CheckItem
import jp.fairguide.jimtof2026.data.NoveltyKey
import jp.fairguide.jimtof2026.data.ReportData

/** 見学ルート（ブースリスト）の、ブースごとのチェックリストを組み立てる */
object BoothChecklist {

    /** どのブースでも使える定番の確認項目 */
    val STANDARD: List<String> = listOf(
        "実機の動きを見る",
        "担当者に話を聞く",
        "資料・カタログをもらう",
        "写真を撮ってよいか確認する",
        "気づいたことをメモする",
    )

    /** 見学レポートのおすすめにある「ブースで確かめること」を、同じ会社・小間のぶんだけ集める（重複は除く） */
    fun fromReport(data: ReportData?, company: String, booth: String): List<String> {
        if (data == null || company.isBlank()) return emptyList()
        val key = NoveltyKey.of(company, booth)
        return data.items.flatMap { item -> item.companies.filter { it.key == key }.flatMap { it.look } }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /** すでにある項目の後ろに、まだ無い項目だけを足す */
    fun merge(existing: List<CheckItem>, add: List<String>): List<CheckItem> {
        val have = existing.map { it.text }.toSet()
        return existing + add.map { it.trim() }.filter { it.isNotEmpty() && it !in have }.distinct().map { CheckItem(it) }
    }

    /** 「2/4」のような進み具合。項目が無ければ空 */
    fun progress(checks: List<CheckItem>): String =
        if (checks.isEmpty()) "" else "${checks.count { it.done }}/${checks.size}"

    fun toggle(checks: List<CheckItem>, index: Int): List<CheckItem> =
        checks.mapIndexed { i, c -> if (i == index) c.copy(done = !c.done) else c }
}
