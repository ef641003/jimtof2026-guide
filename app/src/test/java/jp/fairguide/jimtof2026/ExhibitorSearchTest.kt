package jp.fairguide.jimtof2026

import jp.fairguide.jimtof2026.data.ExhibitorParser
import jp.fairguide.jimtof2026.logic.BoothCode
import jp.fairguide.jimtof2026.logic.ExhibitorIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 出展社データ（assets/exhibitors.json）と検索のテスト */
class ExhibitorSearchTest {

    private val data by lazy {
        ExhibitorParser.parse(File("src/main/assets/exhibitors.json").readText(Charsets.UTF_8))
    }
    private val index by lazy { ExhibitorIndex(data) }

    @Test
    fun dataIsConsistent() {
        assertTrue(data.exhibitors.size > 1100)
        assertEquals(11, data.categories.large.size)
        assertEquals(37, data.categories.middle.size)
        assertEquals(268, data.categories.small.size)
        data.exhibitors.forEach { ex ->
            assertTrue(ex.name, ex.booths.isNotEmpty())
            ex.booths.forEach { b -> assertNotNull("${ex.name} $b", BoothCode.locate(b)) }
            ex.codes.forEach { c -> assertNotNull("${ex.name} $c", data.categories.name(c)) }
        }
        data.synonyms.values.flatten().forEach { c -> assertNotNull(c, data.categories.name(c)) }
    }

    @Test
    fun normalizeAbsorbsNotationDifferences() {
        assertEquals(ExhibitorIndex.normalize("ＤＭＧ森精機"), ExhibitorIndex.normalize("dmg森精機"))
        assertEquals(ExhibitorIndex.normalize("ミツトヨ"), ExhibitorIndex.normalize("みつとよ"))
        assertEquals(ExhibitorIndex.normalize("レーザー"), ExhibitorIndex.normalize("レーザ"))
        assertEquals("おーえむ製作所", ExhibitorIndex.stripCorporate(ExhibitorIndex.normalize("株式会社オーエム製作所")))
    }

    @Test
    fun findsByJapaneseName() {
        val hits = index.search("オーエム")
        assertEquals("株式会社オーエム製作所", hits.first().exhibitor.name)
        assertEquals(listOf("E2019"), hits.first().exhibitor.booths)
    }

    @Test
    fun findsByHiraganaAndEnglishName() {
        assertEquals("ヤマザキマザック株式会社", index.search("まざっく").first().exhibitor.name)
        assertEquals("ヤマザキマザック株式会社", index.search("mazak").first().exhibitor.name)
        assertEquals("オークマ株式会社", index.search("OKUMA").first().exhibitor.name)
        assertEquals("DMG森精機株式会社", index.search("dmg").first().exhibitor.name)
    }

    @Test
    fun findsByBoothCode() {
        val hits = index.search("e2019")
        assertEquals("株式会社オーエム製作所", hits.first().exhibitor.name)
        assertTrue(index.search("E1009-2").first().exhibitor.booths.contains("E1009-2"))
        // ホールだけ指定すると、そのホールの出展社が全部出る
        assertTrue(index.search("E7").size > 50)
        assertTrue(index.search("東7").size > 50)
    }

    @Test
    fun findsByCategoryAndSynonym() {
        val vtl = index.search("立旋盤")
        assertTrue(vtl.any { it.exhibitor.name == "株式会社オーエム製作所" })
        assertTrue(vtl.all { it.matchedCategory != null })
        val cmm = index.search("三次元測定")
        assertTrue(cmm.any { it.exhibitor.name == "株式会社ミツトヨ" })
        assertTrue(index.search("エンドミル").size > 30)
    }

    @Test
    fun multipleWordsNarrowDown() {
        val hits = index.search("研削盤 岡本")
        assertEquals(1, hits.size)
        assertEquals("株式会社岡本工作機械製作所", hits.first().exhibitor.name)
        assertTrue(index.search("存在しない会社名xyz").isEmpty())
    }
}
