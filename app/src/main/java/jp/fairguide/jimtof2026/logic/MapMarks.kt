package jp.fairguide.jimtof2026.logic

import jp.fairguide.jimtof2026.data.BoothItem
import jp.fairguide.jimtof2026.data.BoothMapData
import jp.fairguide.jimtof2026.data.ReportCompany
import jp.fairguide.jimtof2026.data.ReportData
import jp.fairguide.jimtof2026.data.ReportNotes

/** マップに印をつける会社1社ぶん（レポートのおすすめ・自分で足した会社・ブースリスト） */
data class MarkEntry(
    val name: String,
    val booth: String,
    /** 載っているレポートの項目 id と印（①②…） */
    val itemIds: List<String>,
    val marks: List<String>,
    /** 項目 id → おすすめの会社（自分で足した会社は無い） */
    val companies: Map<String, ReportCompany>,
    val inMyList: Boolean,
    val visited: Boolean,
)

/** 図の上の小間1つにつける印 */
data class MapMark(
    val mapId: String,
    /** 図の小間番号（パビリオンなら本体の番号） */
    val boothCode: String,
    val entries: List<MarkEntry>,
) {
    val marks: List<String> get() = entries.flatMap { it.marks }.distinct()
    val inMyList: Boolean get() = entries.any { it.inMyList }
    val visited: Boolean get() = entries.isNotEmpty() && entries.all { it.visited }
    val fromReport: Boolean get() = entries.any { it.itemIds.isNotEmpty() }
}

object MapMarks {
    /**
     * レポートの項目（itemFilter が null なら全項目）とブースリストの会社に印をつける。
     * 戻り値：図の id → （図の小間番号 → 印）
     */
    fun build(
        map: BoothMapData,
        report: ReportData?,
        notes: ReportNotes,
        itemFilter: String?,
        myList: List<BoothItem>,
        includeMyList: Boolean = true,
    ): Map<String, Map<String, MapMark>> {
        class Acc(val name: String, val booth: String) {
            val itemIds = ArrayList<String>()
            val marks = ArrayList<String>()
            val companies = LinkedHashMap<String, ReportCompany>()
            var inMyList = false
            var visited = false
        }
        val accs = LinkedHashMap<String, Acc>()
        fun key(name: String, booth: String) = name + "|" + BoothCode.normalize(booth)

        if (report != null) {
            for (item in report.items) {
                if (itemFilter != null && item.id != itemFilter) continue
                for (row in ReportDraft.rows(report, item.id, notes)) {
                    val a = accs.getOrPut(key(row.name, row.booth)) { Acc(row.name, row.booth) }
                    if (item.id !in a.itemIds) {
                        a.itemIds += item.id
                        a.marks += item.mark
                    }
                    row.company?.let { a.companies[item.id] = it }
                }
            }
        }
        for (b in myList) {
            if (b.booth.isBlank()) continue
            val k = key(b.company, b.booth)
            val a = accs[k] ?: if (includeMyList) Acc(b.company, b.booth).also { accs[k] = it } else null
            if (a != null) {
                a.inMyList = true
                a.visited = b.visited
            }
        }

        val out = HashMap<String, HashMap<String, MutableList<MarkEntry>>>()
        for (a in accs.values) {
            val (hall, booth) = map.find(a.booth) ?: continue
            out.getOrPut(hall.id) { HashMap() }.getOrPut(booth.code) { ArrayList() } +=
                MarkEntry(a.name, a.booth, a.itemIds.toList(), a.marks.toList(), a.companies.toMap(), a.inMyList, a.visited)
        }
        return out.mapValues { (mapId, booths) -> booths.mapValues { (code, list) -> MapMark(mapId, code, list) } }
    }
}
