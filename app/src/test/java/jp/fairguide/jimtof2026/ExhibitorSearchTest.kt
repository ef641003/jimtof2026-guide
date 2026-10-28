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
        assertEquals("おえむ製作所", ExhibitorIndex.stripCorporate(ExhibitorIndex.normalize("株式会社オーエム製作所")))
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
        assertEquals(index.search("E7").size, index.search("東7").size)
        assertEquals(index.search("E7").size, index.search("東7ホール").size)
        // ホール内は小間番号順に並ぶ
        val booths = index.search("E7").map { it.exhibitor.booths.first() }
        assertEquals(booths.sorted(), booths)
    }

    @Test
    fun findsByCategoryAndSynonym() {
        val vtl = index.search("立旋盤")
        assertTrue(vtl.any { it.exhibitor.name == "株式会社オーエム製作所" })
        assertTrue(vtl.all { it.matchedCategory == "NC旋盤（立軸）" })
        // 「旋盤」だけの会社（立旋盤なし）は混ざらない
        assertTrue(vtl.size < index.search("旋盤").size)
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

    // ---- あいまい検索 ----

    @Test
    fun substringDistanceCountsEdits() {
        assertEquals(0, ExhibitorIndex.substringDistance("まさく", "やまさきまさく", 2))
        assertEquals(1, ExhibitorIndex.substringDistance("おおくま", "おくま", 2)) // 1文字余分
        assertEquals(0, ExhibitorIndex.substringDistance("まきの", "まきのふらいす", 1))
        assertEquals(1, ExhibitorIndex.substringDistance("まきお", "まきの", 1)) // 置換
        assertEquals(1, ExhibitorIndex.substringDistance("まざか", "まかざ", 1)) // 隣の入れ替え
        assertEquals(3, ExhibitorIndex.substringDistance("abcdef", "xyz", 2)) // k を超えたら k+1
        assertEquals(0, ExhibitorIndex.substringDistance("", "abc", 1))
        assertEquals(2, ExhibitorIndex.substringDistance("ab", "", 1))
    }

    @Test
    fun loosenDropsDakutenAndSmallKana() {
        assertEquals("やまさきまさつく", ExhibitorIndex.loosen(ExhibitorIndex.normalize("ヤマザキマザック")))
        assertEquals("はなそにつく", ExhibitorIndex.loosen("ぱなそにっく"))
        assertEquals("mazak", ExhibitorIndex.loosen("mazak"))
        assertEquals(-1, ExhibitorIndex.fuzzyLimitFor(2))
        assertEquals(0, ExhibitorIndex.fuzzyLimitFor(3))
        assertEquals(1, ExhibitorIndex.fuzzyLimitFor(5))
        assertEquals(2, ExhibitorIndex.fuzzyLimitFor(8))
        assertEquals(3, ExhibitorIndex.fuzzyLimitFor(12))
    }

    @Test
    fun fuzzySearchFindsNearSpellings() {
        // 濁点なし・小さい「ッ」なし
        val a = index.search("やまさきまさく")
        assertTrue(a.isNotEmpty())
        assertEquals("ヤマザキマザック株式会社", a.first().exhibitor.name)
        assertTrue(a.first().fuzzy)
        // 「オオクマ」→ オークマ（長音の代わりに「オ」）
        val b = index.search("オオクマ")
        assertTrue(b.any { it.exhibitor.name == "オークマ株式会社" && it.fuzzy })
        // 英語名の打ち間違い。いちばん近い（1文字違い）会社だけが出る
        val c = index.search("makimo")
        assertTrue(c.isNotEmpty() && c.size <= 5)
        assertTrue(c.all { it.fuzzy })
        assertEquals("株式会社牧野フライス製作所", c.first().exhibitor.name)
        assertEquals(2, c.count { it.exhibitor.name.contains("牧野フライス") })
        // ぴったり合うものは fuzzy にならず、先に並ぶ
        val d = index.search("マザック")
        assertEquals("ヤマザキマザック株式会社", d.first().exhibitor.name)
        assertTrue(d.none { it.fuzzy } || d.indexOfFirst { it.fuzzy } > d.indexOfLast { !it.fuzzy })
    }

    @Test
    fun fuzzySearchStaysQuiet() {
        // 小間番号・短い語はあいまい検索しない
        assertTrue(index.search("E2019").none { it.fuzzy })
        assertTrue(index.search("E9999").isEmpty())
        assertTrue(index.search("ab").none { it.fuzzy })
        // ぴったり合う会社が十分あれば、近い表記は混ぜない
        val many = index.search("製作所")
        assertTrue(many.size >= ExhibitorIndex.FUZZY_ONLY_IF_FEWER_THAN)
        assertTrue(many.none { it.fuzzy })
        // まったく違う語は何も出ない
        assertTrue(index.search("ぞろぞろぞろぞろ").isEmpty())
        // 複数語は全部に当たる会社だけ（近い表記を含めて）
        val two = index.search("やまさき まざっく")
        assertTrue(two.isNotEmpty())
        assertTrue(two.all { it.exhibitor.name.contains("ヤマザキマザック") && it.fuzzy })
    }
}
