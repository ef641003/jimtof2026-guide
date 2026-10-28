package jp.fairguide.jimtof2026.logic

import jp.fairguide.jimtof2026.data.ReportCompany
import jp.fairguide.jimtof2026.data.ReportData
import jp.fairguide.jimtof2026.data.ReportExtra
import jp.fairguide.jimtof2026.data.ReportNotes

/** 見学レポートの下書き（共有用のテキスト）を組み立てる */
object ReportDraft {

    /** 項目ごとの一覧に出す行。おすすめ（company）か、自分で足した会社（extra）のどちらか */
    class Row(val itemId: String, val name: String, val booth: String, val company: ReportCompany?, val extra: ReportExtra?) {
        val key: String get() = company?.key ?: extra!!.key
    }

    /** おすすめの会社に自分で足した会社をつなげ、会場を歩く順（東→西→南、小間番号順）で返す */
    fun rows(data: ReportData, itemId: String, notes: ReportNotes): List<Row> {
        val item = data.item(itemId) ?: return emptyList()
        val recommended = item.companies.map { Row(itemId, it.name, it.booth, it, null) }
        val known = recommended.map { it.key }.toSet()
        val extras = notes.extras.filter { it.itemId == itemId && it.key !in known }
            .map { Row(itemId, it.name, it.booth, null, it) }
        return recommended + extras
    }

    /** 会場を歩く順に並べ替える（メモがある会社を先に、は行わない。順路のまま） */
    fun rowsByHall(rows: List<Row>): List<Row> =
        rows.sortedWith(compareBy({ BoothCode.hallRank(it.booth) }, { BoothCode.normalize(it.booth) }, { it.name }))

    /** 下書きテキスト。メモを書いた会社とまとめだけを出し、何も書いていない項目は「未記入」にする */
    fun text(data: ReportData, notes: ReportNotes, today: String): String {
        val sb = StringBuilder()
        sb.append("JIMTOF2026 見学レポート 下書き（").append(today).append(" 書き出し）\n")
        sb.append("提出期限：").append(data.deadline.replace('-', '/')).append('\n')
        for (item in data.items) {
            sb.append('\n').append(item.id).append("．").append(item.title).append('\n')
            val rows = rowsByHall(rows(data, item.id, notes))
            var written = 0
            for (row in rows) {
                val note = notes.note(item.id, row.key)?.trim().orEmpty()
                if (note.isEmpty()) continue
                written++
                sb.append("・").append(shortName(row.name))
                val code = BoothCode.normalize(row.booth).ifEmpty { row.booth }
                sb.append("（").append(code).append("）：").append(note.replace("\n", "\n　")).append('\n')
            }
            val summary = notes.summaries[item.id]?.trim().orEmpty()
            if (summary.isNotEmpty()) {
                sb.append("［まとめ］").append(summary.replace("\n", "\n　")).append('\n')
            }
            if (written == 0 && summary.isEmpty()) sb.append("（未記入）\n")
        }
        return sb.toString().trimEnd()
    }

    /** 「株式会社」などを外して短くする（下書きの行を読みやすくする） */
    fun shortName(name: String): String =
        name.replace(Regex("(株式会社|有限会社|合同会社)"), "").trim().ifEmpty { name }
}
