package jp.fairguide.jimtof2026.logic

import jp.fairguide.jimtof2026.data.EventCategory
import jp.fairguide.jimtof2026.data.FairDay
import jp.fairguide.jimtof2026.data.FairEvent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 会期の時刻はすべて日本時間で扱う（端末のタイムゾーンが違っても表示がずれないように） */
val FAIR_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

sealed interface FairStatus {
    data class Before(val daysLeft: Long, val firstDay: FairDay) : FairStatus
    data class BeforeOpen(val dayIndex: Int, val day: FairDay, val minutesToOpen: Long) : FairStatus
    data class Open(val dayIndex: Int, val day: FairDay, val minutesToClose: Long, val isLastDay: Boolean) : FairStatus
    data class ClosedForDay(val dayIndex: Int, val next: FairDay) : FairStatus
    data object Ended : FairStatus
}

fun fairStatus(days: List<FairDay>, now: ZonedDateTime): FairStatus {
    if (days.isEmpty()) return FairStatus.Ended
    val local = now.withZoneSameInstant(FAIR_ZONE)
    val today = local.toLocalDate()
    val time = local.toLocalTime()
    val index = days.indexOfFirst { it.date == today }
    if (index >= 0) {
        val day = days[index]
        return when {
            time.isBefore(day.open) ->
                FairStatus.BeforeOpen(index + 1, day, minutesUntil(time, day.open))
            time.isBefore(day.close) ->
                FairStatus.Open(index + 1, day, minutesUntil(time, day.close), index == days.lastIndex)
            index < days.lastIndex -> FairStatus.ClosedForDay(index + 1, days[index + 1])
            else -> FairStatus.Ended
        }
    }
    val next = days.firstOrNull { it.date.isAfter(today) } ?: return FairStatus.Ended
    return FairStatus.Before(ChronoUnit.DAYS.between(today, next.date), next)
}

/** 端数は切り上げ（開場前に「あと0分」と出ないように） */
private fun minutesUntil(from: LocalTime, to: LocalTime): Long {
    val seconds = Duration.between(from, to).seconds
    return (seconds + 59) / 60
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d（E）", Locale.JAPANESE)
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.JAPANESE)

fun LocalDate.jpShort(): String = format(DATE_FORMAT)

fun LocalTime.hm(): String = format(TIME_FORMAT)

fun formatDuration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "${m}分"
        m == 0L -> "${h}時間"
        else -> "${h}時間${m}分"
    }
}

fun FairEvent.timeRange(): String {
    val s = start ?: return "時間未定"
    val e = end
    return if (e != null) "${s.hm()}〜${e.hm()}" else "${s.hm()}〜"
}

data class EventPreview(val heading: String, val events: List<FairEvent>)

/** 概要タブに出す「このあとの予定」。出展者ワークショップは数が多いので除く */
fun previewEvents(events: List<FairEvent>, status: FairStatus, now: ZonedDateTime, limit: Int = 3): EventPreview? {
    val local = now.withZoneSameInstant(FAIR_ZONE)
    val main = events.filter { it.start != null && it.category != EventCategory.WORKSHOP }
    return when (status) {
        is FairStatus.Before ->
            pick("初日 ${status.firstDay.date.jpShort()} の主な予定", main.filter { it.date == status.firstDay.date }, limit)
        is FairStatus.BeforeOpen, is FairStatus.Open -> {
            val today = local.toLocalDate()
            val time = local.toLocalTime()
            pick(
                "このあとの主な予定（本日）",
                main.filter { ev ->
                    val start = ev.start ?: return@filter false
                    ev.date == today && (ev.end ?: start.plusMinutes(45)).isAfter(time)
                },
                limit,
            )
        }
        is FairStatus.ClosedForDay ->
            pick("次の開催日 ${status.next.date.jpShort()} の主な予定", main.filter { it.date == status.next.date }, limit)
        FairStatus.Ended -> null
    }
}

private fun pick(heading: String, list: List<FairEvent>, limit: Int): EventPreview? {
    val chosen = list.sortedWith(compareBy<FairEvent>({ it.start }, { it.category.ordinal })).take(limit)
    return if (chosen.isEmpty()) null else EventPreview(heading, chosen)
}
