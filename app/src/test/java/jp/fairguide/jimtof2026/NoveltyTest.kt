package jp.fairguide.jimtof2026

import jp.fairguide.jimtof2026.data.ExhibitorParser
import jp.fairguide.jimtof2026.data.NoveltyKey
import jp.fairguide.jimtof2026.data.NoveltyParser
import jp.fairguide.jimtof2026.logic.ExhibitorIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** ノベルティのデータ（assets/novelty.json）と、「ノベルティあり」の絞り込みのテスト */
class NoveltyTest {

    private val data by lazy {
        ExhibitorParser.parse(File("src/main/assets/exhibitors.json").readText(Charsets.UTF_8))
    }
    private val novelty by lazy {
        NoveltyParser.parse(File("src/main/assets/novelty.json").readText(Charsets.UTF_8))
    }
    private val index by lazy { ExhibitorIndex(data, novelty) }

    @Test
    fun everyNoveltyEntryMatchesAnExhibitor() {
        val keys = data.exhibitors.map { NoveltyKey.of(it) }.toSet()
        novelty.byExhibitor.forEach { (key, infos) ->
            assertTrue("出展社データに無い：$key", key in keys)
            infos.forEach { info ->
                assertNotNull("出典が未定義：$key ${info.sourceId}", novelty.sources[info.sourceId])
                assertTrue(info.item.isNotBlank() && info.condition.isNotBlank() && info.quantity.isNotBlank())
            }
        }
        novelty.sources.values.forEach { assertTrue(it.url.startsWith("https://")) }
    }

    @Test
    fun onlyFilterWithoutQueryListsExactlyTheNoveltyCompanies() {
        val hits = index.search("", filter = { NoveltyKey.of(it) in novelty.byExhibitor })
        assertEquals(novelty.byExhibitor.size, hits.size)
        // 会場を歩く順（東→西→南、小間番号順）
        val ranks = hits.map { it.exhibitor.booths.first() }
        assertEquals("E1001", ranks.first())
        assertTrue(ranks.indexOf("W1039") < ranks.indexOf("S1059"))
    }

    @Test
    fun noFilterAndNoQueryStillReturnsNothing() {
        assertTrue(index.search("").isEmpty())
    }

    @Test
    fun filterCombinesWithQuery() {
        val only = { ex: jp.fairguide.jimtof2026.data.Exhibitor -> NoveltyKey.of(ex) in novelty.byExhibitor }
        // 切削工具メーカーのうち、ノベルティがある会社だけに絞れる
        val all = index.search("切削工具", filter = null)
        val withNovelty = index.search("切削工具", filter = only)
        assertTrue(withNovelty.isNotEmpty())
        assertTrue(withNovelty.size < all.size)
        assertTrue(withNovelty.all { only(it.exhibitor) })
        assertEquals("京セラ株式会社", index.search("京セラ", filter = only).first().exhibitor.name)
        assertTrue(index.search("オークマ", filter = only).isEmpty())
    }

    @Test
    fun findsByNoveltyItemName() {
        val names = index.search("トートバッグ").map { it.exhibitor.name }
        assertTrue(names.contains("日進工具株式会社"))
        assertTrue(names.contains("株式会社ナカニシ"))
    }

    @Test
    fun companyWithSeveralNoveltiesKeepsAllOfThem() {
        val tungaloy = data.exhibitors.first { it.name == "株式会社タンガロイ" }
        assertEquals(3, novelty.infoFor(tungaloy).size)
    }
}
