package jp.fairguide.jimtof2026

import jp.fairguide.jimtof2026.data.CheckCodec
import jp.fairguide.jimtof2026.data.CheckItem
import jp.fairguide.jimtof2026.data.ReportParser
import jp.fairguide.jimtof2026.logic.BoothChecklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 見学ルートのチェックリスト（ブースごとの確かめること）のテスト */
class BoothChecklistTest {

    private val report by lazy {
        ReportParser.parse(File("src/main/assets/report.json").readText(Charsets.UTF_8))
    }

    @Test
    fun recommendedCompanyGetsItsReportChecks() {
        val c = report.item("1")!!.companies.first()
        val checks = BoothChecklist.fromReport(report, c.name, c.booth)
        assertTrue(checks.isNotEmpty())
        assertTrue(c.look.all { it.trim() in checks })
        assertEquals(checks.size, checks.toSet().size)
    }

    @Test
    fun companyInSeveralItemsGetsEachItemsChecksWithoutDuplicates() {
        // 複数の項目に載っている会社は、項目ごとの確認事項を重複なくまとめる
        val byKey = report.items.flatMap { it.companies }.groupBy { it.key }.filter { it.value.size > 1 }
        assertTrue(byKey.isNotEmpty())
        byKey.values.forEach { list ->
            val c = list.first()
            val checks = BoothChecklist.fromReport(report, c.name, c.booth)
            list.forEach { x -> assertTrue(x.look.all { it.trim() in checks }) }
            assertEquals(checks.size, checks.toSet().size)
        }
    }

    @Test
    fun unknownCompanyHasNoReportChecks() {
        assertTrue(BoothChecklist.fromReport(report, "存在しない会社", "E9999").isEmpty())
        assertTrue(BoothChecklist.fromReport(null, "何か", "E1").isEmpty())
        assertTrue(BoothChecklist.fromReport(report, "", "E1").isEmpty())
    }

    @Test
    fun mergeAddsOnlyNewItemsAndKeepsProgress() {
        val existing = listOf(CheckItem("実機の動きを見る", true), CheckItem("自分で足した項目"))
        val merged = BoothChecklist.merge(existing, BoothChecklist.STANDARD + listOf("  ", "自分で足した項目"))
        assertEquals(existing, merged.take(2))
        assertEquals(merged.size, merged.map { it.text }.toSet().size)
        assertEquals(1 + BoothChecklist.STANDARD.size, merged.size)
        assertEquals(1, merged.count { it.done })
    }

    @Test
    fun toggleAndProgress() {
        var checks = BoothChecklist.merge(emptyList(), listOf("a", "b", "c"))
        assertEquals("0/3", BoothChecklist.progress(checks))
        checks = BoothChecklist.toggle(checks, 1)
        assertEquals("1/3", BoothChecklist.progress(checks))
        assertTrue(checks[1].done && !checks[0].done)
        checks = BoothChecklist.toggle(checks, 1)
        assertEquals("0/3", BoothChecklist.progress(checks))
        assertEquals("", BoothChecklist.progress(emptyList()))
    }

    @Test
    fun codecRoundTrips() {
        val checks = listOf(CheckItem("説明員に聞く：納期・保守", true), CheckItem("写真（OKなら）"), CheckItem("1行\n目と2行目"))
        val expected = listOf(checks[0], checks[1], CheckItem("1行 目と2行目"))
        assertEquals(expected, CheckCodec.unpack(CheckCodec.pack(checks)))
        assertEquals(checks, CheckCodec.fromJson(CheckCodec.toJson(checks)))
        assertEquals(emptyList<CheckItem>(), CheckCodec.fromJson(null))
        assertEquals(emptyList<CheckItem>(), CheckCodec.unpack(""))
    }
}
