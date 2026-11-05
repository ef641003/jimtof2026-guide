package jp.fairguide.jimtof2026

import jp.fairguide.jimtof2026.data.ExhibitorParser
import jp.fairguide.jimtof2026.logic.ExhibitorIndex
import jp.fairguide.jimtof2026.logic.ReportPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 見学レポート用の候補（ReportPreset）のテスト */
class ReportPresetTest {

    private val data by lazy {
        ExhibitorParser.parse(File("src/main/assets/exhibitors.json").readText(Charsets.UTF_8))
    }
    private val index by lazy { ExhibitorIndex(data) }

    private fun names(preset: ReportPreset, query: String = "") =
        index.search(query, limit = Int.MAX_VALUE, preset = preset).map { it.exhibitor.name }

    @Test
    fun competitorsListVerticalLatheMakersButNotOurselves() {
        val names = names(ReportPreset.COMPETITOR)
        assertTrue(names.size in 40..120)
        listOf("株式会社TAKISAWA", "新日本工機株式会社", "芝浦機械株式会社", "株式会社池貝").forEach {
            assertTrue(it, it in names)
        }
        assertFalse(names.contains("株式会社オーエム製作所"))
        // 専業に近い会社が上位に来る
        val top = names.take(10)
        assertTrue(top.contains("株式会社TAKISAWA") || top.contains("新日本工機株式会社"))
    }

    @Test
    fun automationCoversSystemsAndNamedRobotCompanies() {
        val hits = index.search("", limit = Int.MAX_VALUE, preset = ReportPreset.AUTOMATION)
        val names = hits.map { it.exhibitor.name }
        assertTrue(names.contains("ファナック株式会社"))
        assertTrue(names.size > 60)
        assertTrue(hits.all { !it.presetReason.isNullOrBlank() })
        // 社名にロボット・自動化を含む会社が先頭グループ
        assertTrue(hits.first().presetReason!!.contains("社名"))
    }

    @Test
    fun cuttingToolsListMajorMakersFirst() {
        val hits = index.search("", limit = Int.MAX_VALUE, preset = ReportPreset.CUTTING)
        val names = hits.map { it.exhibitor.name }
        assertTrue(names.size.toString(), names.size in 150..450)
        listOf("三菱マテリアル株式会社", "株式会社タンガロイ", "オーエスジー株式会社", "京セラ株式会社", "株式会社彌満和製作所").forEach {
            assertTrue(it, it in names)
        }
        assertTrue(hits.all { !it.presetReason.isNullOrBlank() })
        // 登録分類の多い総合メーカーが上位
        val top = names.take(25)
        assertTrue(top.toString(), top.contains("三菱マテリアル株式会社") || top.contains("株式会社タンガロイ") || top.contains("京セラ株式会社"))
    }

    @Test
    fun holdersCoverToolHoldersAndChucks() {
        val names = names(ReportPreset.HOLDER)
        assertTrue(names.size.toString(), names.size in 40..160)
        listOf("大昭和精機株式会社", "株式会社日研工作所", "株式会社北川鉄工所", "ユキワ精工株式会社").forEach {
            assertTrue(it, it in names)
        }
    }

    @Test
    fun measuringCoversMetrologyMakers() {
        val names = names(ReportPreset.MEASURING)
        assertTrue(names.size.toString(), names.size in 60..260)
        listOf("株式会社ミツトヨ", "株式会社東京精密", "レニショー株式会社", "株式会社ニコン").forEach {
            assertTrue(it, it in names)
        }
        // 三次元測定機の会社が、試験機だけの会社より上
        assertTrue(names.indexOf("株式会社ミツトヨ") < names.indexOf("株式会社山本科学工具研究社"))
    }

    @Test
    fun presetCombinesWithQueryAndNeverReturnsEverythingWithoutIt() {
        assertTrue(index.search("").isEmpty())
        val narrowed = names(ReportPreset.CUTTING, "タンガロイ")
        assertEquals(listOf("株式会社タンガロイ"), narrowed)
        assertNotNull(ReportPreset.entries.map { it.description }.firstOrNull())
    }
}
