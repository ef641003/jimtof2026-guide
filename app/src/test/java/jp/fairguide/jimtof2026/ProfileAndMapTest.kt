package jp.fairguide.jimtof2026

import jp.fairguide.jimtof2026.data.BoothItem
import jp.fairguide.jimtof2026.data.BoothMapParser
import jp.fairguide.jimtof2026.data.ExhibitorParser
import jp.fairguide.jimtof2026.data.NoveltyKey
import jp.fairguide.jimtof2026.data.ReportExtra
import jp.fairguide.jimtof2026.data.ReportNotes
import jp.fairguide.jimtof2026.data.ReportParser
import jp.fairguide.jimtof2026.data.ViewerProfile
import jp.fairguide.jimtof2026.logic.BoothCode
import jp.fairguide.jimtof2026.logic.MapMarks
import jp.fairguide.jimtof2026.logic.ReportPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 担当の切り替え（ViewerProfile）と会場マップ（小間の位置・印）のテスト */
class ProfileAndMapTest {

    private val exhibitors by lazy {
        ExhibitorParser.parse(File("src/main/assets/exhibitors.json").readText(Charsets.UTF_8))
    }
    private val map by lazy {
        BoothMapParser.parse(File("src/main/assets/booth_map.json").readText(Charsets.UTF_8))
    }

    private fun report(profile: ViewerProfile) =
        ReportParser.parse(File("src/main/assets/" + profile.reportAsset).readText(Charsets.UTF_8))

    // ---- 担当 ----

    @Test
    fun everyProfileHasValidReportAndPresets() {
        val keys = exhibitors.exhibitors.map { NoveltyKey.of(it) }.toSet()
        val allPresets = HashSet<ReportPreset>()
        ViewerProfile.entries.forEach { p ->
            val r = report(p)
            assertTrue(p.id, r.items.size >= 3)
            assertTrue(p.presets.isNotEmpty())
            allPresets += p.presets
            assertEquals(p, ViewerProfile.of(p.id))
            r.items.forEach { item ->
                assertTrue(item.mark.isNotBlank())
                item.presetName?.let { name -> assertTrue("${p.id} ${item.id} $name", p.presets.any { it.name == name }) }
                item.companies.forEach { c -> assertTrue("${p.id}：出展社データに無い ${c.name} ${c.booth}", c.key in keys) }
            }
        }
        // どの候補もどれかの担当で使う
        assertEquals(ReportPreset.entries.toSet(), allPresets)
        // 項目 id は担当どうしで重ならない（メモが混ざらない）
        val ids = ViewerProfile.entries.flatMap { p -> report(p).items.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
        assertNull(ViewerProfile.of("unknown"))
        assertEquals(listOf("1", "2", "5"), report(ViewerProfile.MAKER).items.map { it.id })
        assertEquals(listOf("①", "②", "⑤"), report(ViewerProfile.MAKER).items.map { it.mark })
    }

    // ---- 会場マップ ----

    @Test
    fun mapCoversEveryExhibitorBooth() {
        assertEquals(listOf("e1-3", "e7-8", "w1-2", "w3-4", "s1-2", "s3-4"), map.maps.map { it.id })
        val total = map.maps.sumOf { it.booths.size }
        assertTrue(total.toString(), total in 900..1000)
        val missing = exhibitors.exhibitors.flatMap { it.booths }.filter { map.find(it) == null }
        assertTrue(missing.toString(), missing.isEmpty())
        map.maps.forEach { m ->
            assertTrue(m.label.isNotBlank() && m.floor.isNotBlank())
            assertTrue(m.pageUrl.startsWith("https://www.jimtof.org/") && "{code}" in m.pageUrl)
            m.booths.forEach { b ->
                assertTrue(b.code, b.points.size >= 6 && b.points.size % 2 == 0)
                assertTrue(b.right > b.left && b.bottom > b.top)
                // 小間の中心は小間の中にある（四角い小間）
                if (b.points.size == 8) assertTrue(b.code, b.contains(b.centerX, b.centerY))
                // 小間はその図のホールのもの
                assertEquals(b.code, m.id, map.find(b.code)!!.first.id)
            }
            // 図のホールが、小間番号のホールと合っている
            m.halls.forEach { h -> assertTrue(h.hallId, h.hallId in BoothCode.HALL_ORDER) }
        }
    }

    @Test
    fun findHandlesNotationAndPavilions() {
        assertEquals("E1001", map.find("e-1001")!!.second.code)
        assertEquals("E1001", map.find("Ｅ１００１")!!.second.code)
        // パビリオンの小区画は本体の区画に寄せる
        assertEquals("E1009", map.find("E1009-3")!!.second.code)
        assertEquals("W4010", map.find("W4010-12")!!.second.code)
        assertEquals("w1-2", map.find("WA001")!!.first.id)
        assertEquals("w3-4", map.find("W3001")!!.first.id)
        assertNull(map.find("E9999"))
        assertNull(map.find(""))
        // 小間の中心をタップすると、その小間
        val (hall, booth) = map.find("W2055")!!
        assertEquals("W2055", hall.boothAt(booth.centerX, booth.centerY)?.code)
        assertNull(hall.boothAt(hall.left - 100f, hall.top - 100f))
        assertEquals("https://www.jimtof.org/map/jp/w1-2_booth/w1-2.html?no=W2055", hall.pageFor("W2055"))
        assertNotNull(map.mapForHall("S4"))
    }

    @Test
    fun marksCoverReportCompaniesAndMyList() {
        val r = report(ViewerProfile.WEST)
        val notes = ReportNotes(extras = listOf(ReportExtra("w1", "株式会社ツガミ", "S1004")))
        val myList = listOf(
            BoothItem(1, "三菱マテリアル株式会社", "W1065", "", visited = true, createdAt = 1),
            BoothItem(2, "ヤマザキマザック株式会社", "E1001", "", visited = false, createdAt = 2),
            BoothItem(3, "小間なし", "", "", visited = false, createdAt = 3),
        )
        val marks = MapMarks.build(map, r, notes, null, myList)
        val count = marks.values.sumOf { m -> m.values.sumOf { it.entries.size } }
        val companies = r.items.flatMap { it.companies }.map { it.key }.toSet()
        // おすすめ（重複なし）＋自分で足した会社＋ブースリストだけの会社
        assertEquals(companies.size + 1 + 1, count)
        val mmc = marks.getValue("w1-2").getValue("W1065")
        assertTrue(mmc.inMyList && mmc.visited && mmc.fromReport)
        assertEquals(listOf("①"), mmc.marks)
        val mazak = marks.getValue("e1-3").getValue("E1001")
        assertTrue(mazak.inMyList && !mazak.fromReport)
        assertTrue(marks.getValue("s1-2").getValue("S1004").fromReport)
        // 項目で絞り込むと、その項目とブースリストだけ
        val only3 = MapMarks.build(map, r, notes, "w3", myList)
        val names = only3.values.flatMap { m -> m.values.flatMap { it.entries } }
        assertTrue(names.all { it.itemIds.isEmpty() || it.itemIds == listOf("w3") })
        assertTrue(names.any { it.name == "株式会社ミツトヨ" })
        assertFalse(names.any { it.name == "株式会社ツガミ" })
        // ブースリストを入れない設定
        val noList = MapMarks.build(map, r, ReportNotes(), null, myList, includeMyList = false)
        assertNull(noList["e1-3"]?.get("E1001"))
        // レポートが無くても、ブースリストの印は出る
        val listOnly = MapMarks.build(map, null, ReportNotes(), null, myList)
        assertEquals(2, listOnly.values.sumOf { it.size })
    }
}
