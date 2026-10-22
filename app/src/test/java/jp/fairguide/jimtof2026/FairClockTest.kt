package jp.fairguide.jimtof2026

import jp.fairguide.jimtof2026.data.FairDay
import jp.fairguide.jimtof2026.logic.FAIR_ZONE
import jp.fairguide.jimtof2026.logic.FairStatus
import jp.fairguide.jimtof2026.logic.fairStatus
import jp.fairguide.jimtof2026.logic.formatDuration
import jp.fairguide.jimtof2026.logic.hm
import jp.fairguide.jimtof2026.logic.jpShort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class FairClockTest {

    private val days = (26..31).map { d ->
        FairDay(
            date = LocalDate.of(2026, 10, d),
            open = LocalTime.of(10, 0),
            close = if (d == 31) LocalTime.of(16, 0) else LocalTime.of(17, 0),
        )
    }

    private fun at(month: Int, day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, FAIR_ZONE)

    @Test
    fun beforeFair() {
        val status = fairStatus(days, at(10, 22, 16, 0)) as FairStatus.Before
        assertEquals(4L, status.daysLeft)
        assertEquals(LocalDate.of(2026, 10, 26), status.firstDay.date)
    }

    @Test
    fun morningBeforeOpen() {
        val status = fairStatus(days, at(10, 27, 9, 30)) as FairStatus.BeforeOpen
        assertEquals(2, status.dayIndex)
        assertEquals(30L, status.minutesToOpen)
    }

    @Test
    fun openDuringTheDay() {
        val status = fairStatus(days, at(10, 26, 16, 0)) as FairStatus.Open
        assertEquals(1, status.dayIndex)
        assertEquals(60L, status.minutesToClose)
        assertEquals(false, status.isLastDay)
    }

    @Test
    fun lastDayClosesAt16() {
        val open = fairStatus(days, at(10, 31, 15, 59)) as FairStatus.Open
        assertTrue(open.isLastDay)
        assertTrue(fairStatus(days, at(10, 31, 16, 0)) is FairStatus.Ended)
    }

    @Test
    fun eveningBetweenDays() {
        val status = fairStatus(days, at(10, 28, 18, 0)) as FairStatus.ClosedForDay
        assertEquals(LocalDate.of(2026, 10, 29), status.next.date)
    }

    @Test
    fun afterFair() {
        assertTrue(fairStatus(days, at(11, 2, 9, 0)) is FairStatus.Ended)
    }

    @Test
    fun otherTimeZoneIsConvertedToJapanTime() {
        // UTC 10/26 0:30 は日本時間 10/26 9:30
        val utc = ZonedDateTime.of(2026, 10, 26, 0, 30, 0, 0, ZoneOffset.UTC)
        val status = fairStatus(days, utc) as FairStatus.BeforeOpen
        assertEquals(1, status.dayIndex)
    }

    @Test
    fun durationText() {
        assertEquals("30分", formatDuration(30))
        assertEquals("2時間", formatDuration(120))
        assertEquals("1時間5分", formatDuration(65))
    }

    @Test
    fun dateAndTimeText() {
        assertEquals("10/26（月）", LocalDate.of(2026, 10, 26).jpShort())
        assertEquals("10/31（土）", LocalDate.of(2026, 10, 31).jpShort())
        assertEquals("9:00", LocalTime.of(9, 0).hm())
        assertEquals("16:00", LocalTime.of(16, 0).hm())
    }
}
