package jp.fairguide.jimtof2026

import jp.fairguide.jimtof2026.data.ExhibitorParser
import jp.fairguide.jimtof2026.data.NoveltyKey
import jp.fairguide.jimtof2026.data.ReportExtra
import jp.fairguide.jimtof2026.data.ReportNotes
import jp.fairguide.jimtof2026.data.ReportParser
import jp.fairguide.jimtof2026.logic.ReportDraft
import jp.fairguide.jimtof2026.logic.ReportPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 見学レポートのデータ（assets/report.json）と下書きのテスト */
class ReportTest {

    private val exhibitors by lazy {
        ExhibitorParser.parse(File("src/main/assets/exhibitors.json").readText(Charsets.UTF_8))
    }
    private val report by lazy {
        ReportParser.parse(File("src/main/assets/report.json").readText(Charsets.UTF_8))
    }

    @Test
    fun everyRecommendedCompanyMatchesAnExhibitor() {
        val keys = exhibitors.exhibitors.map { NoveltyKey.of(it) }.toSet()
        assertEquals(listOf("1", "2", "5"), report.items.map { it.id })
        report.items.forEach { item ->
            assertTrue(item.id, item.companies.size >= 10)
            assertTrue(item.guide.isNotEmpty() && item.intro.isNotBlank())
            item.presetName?.let { name -> assertNotNull(name, ReportPreset.entries.firstOrNull { it.name == name }) }
            val seen = HashSet<String>()
            item.companies.forEach { c ->
                assertTrue("出展社データに無い：${c.name} ${c.booth}", c.key in keys)
                assertTrue("同じ会社が2回：${item.id} ${c.name}", seen.add(c.key))
                assertTrue(c.why.isNotBlank() && c.facts.isNotEmpty() && c.look.isNotEmpty())
                c.sourceIds.forEach { s -> assertNotNull("出典が未定義：$s", report.sources[s]) }
            }
            // 自社は候補に入れない
            assertFalse(item.companies.any { it.name == "株式会社オーエム製作所" })
        }
        report.sources.values.forEach { assertTrue(it.url.startsWith("https://")) }
    }

    @Test
    fun rowsAppendUserAddedCompaniesAndOrderByHall() {
        val item = report.items.first()
        val extra = ReportExtra("1", "オークマ株式会社", "E3028") // すでにおすすめにある → 重複しない
        val extra2 = ReportExtra("1", "株式会社ツガミ", "S1004")
        val notes = ReportNotes(extras = listOf(extra, extra2, ReportExtra("2", "株式会社ツガミ", "S1004")))
        val rows = ReportDraft.rows(report, "1", notes)
        assertEquals(item.companies.size + 1, rows.size)
        assertEquals("株式会社ツガミ", rows.last().name)
        assertNotNull(rows.last().extra)
        val byHall = ReportDraft.rowsByHall(rows).map { it.booth }
        assertTrue(byHall.indexOf("E1001") < byHall.indexOf("W2014"))
        assertTrue(byHall.indexOf("W2014") < byHall.indexOf("S1004"))
    }

    @Test
    fun draftListsOnlyCompaniesWithNotesAndMarksEmptyItems() {
        val mazak = report.items.first().companies.first { it.booth == "E1001" }
        val notes = ReportNotes(
            notes = mapOf(
                ReportNotes.noteKey("1", mazak.key) to "MEGA TURN 900M は建機向けと説明\n付加機能は歯車",
                ReportNotes.noteKey("2", mazak.key) to "",
            ),
            summaries = mapOf("5" to "定時デモが有効"),
        )
        val text = ReportDraft.text(report, notes, "2026/10/28")
        assertTrue(text.startsWith("JIMTOF2026 見学レポート 下書き（2026/10/28 書き出し）"))
        assertTrue(text.contains("提出期限：2026/11/05"))
        assertTrue(text.contains("1．競合各社の重点業界、展示内容、付加機能の提案動向"))
        assertTrue(text.contains("・ヤマザキマザック（E1001）：MEGA TURN 900M は建機向けと説明\n　付加機能は歯車"))
        // 空のメモの項目②は未記入、項目⑤はまとめだけ
        assertTrue(text.contains("2．出展メーカーの省人化・無人化仕様の提案動向\n（未記入）"))
        assertTrue(text.contains("［まとめ］定時デモが有効"))
        assertFalse(text.contains("オークマ")) // メモの無い会社は出さない
        assertEquals("ヤマザキマザック", ReportDraft.shortName("ヤマザキマザック株式会社"))
        assertEquals("EMAG GmbH & Co. KG", ReportDraft.shortName("EMAG GmbH & Co. KG"))
    }
}
