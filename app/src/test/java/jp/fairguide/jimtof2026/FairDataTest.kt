package jp.fairguide.jimtof2026

import jp.fairguide.jimtof2026.data.FairDataParser
import jp.fairguide.jimtof2026.logic.BoothCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** 同梱データ（assets/jimtof2026.json）の整合性チェック */
class FairDataTest {

    private val data by lazy {
        FairDataParser.parse(File("src/main/assets/jimtof2026.json").readText(Charsets.UTF_8))
    }

    @Test
    fun sixFairDaysAndShortLastDay() {
        assertEquals(6, data.days.size)
        assertEquals(LocalDate.of(2026, 10, 26), data.days.first().date)
        assertEquals(LocalTime.of(16, 0), data.days.last().close)
    }

    @Test
    fun eventIdsAreUnique() {
        val ids = data.events.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun eventsAreWithinTheFairPeriod() {
        val first = LocalDate.of(2026, 10, 26)
        val last = LocalDate.of(2026, 11, 1)
        data.events.forEach { e ->
            assertTrue(e.id, !e.date.isBefore(first) && !e.date.isAfter(last))
            val s = e.start
            val end = e.end
            if (s != null && end != null) assertTrue(e.id, end.isAfter(s))
            assertTrue(e.id, e.title.isNotBlank() && e.place.isNotBlank())
        }
    }

    @Test
    fun linksAreWellFormed() {
        val urls = data.linkGroups.flatMap { g -> g.items.map { it.url } } +
            data.halls.mapNotNull { it.mapUrl } + data.halls.mapNotNull { it.pdfUrl }
        urls.forEach { url ->
            assertTrue(url, url.startsWith("https://") || url.startsWith("mailto:") || url.startsWith("tel:"))
        }
    }

    @Test
    fun everyBoothPrefixHasAHallCard() {
        listOf("E1024", "E2012", "E7002", "WA001", "W1009", "W2032", "W3104", "W4090", "S1023", "S3061", "S4029")
            .forEach { code ->
                val loc = BoothCode.locate(code)!!
                assertTrue(code, data.halls.any { loc.hallId in it.prefixes })
            }
    }
}
