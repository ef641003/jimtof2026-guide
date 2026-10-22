package jp.fairguide.jimtof2026.data

import jp.fairguide.jimtof2026.logic.FAIR_ZONE
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/** 開催概要 */
data class FairInfo(
    val title: String,
    val nameJa: String,
    val nameEn: String,
    val period: String,
    val venue: String,
    val address: String,
    val halls: String,
    val area: String,
    val scale: String,
    val organizers: String,
    val registrationUrl: String,
    val mypageUrl: String,
    val officialUrl: String,
    val verifiedOn: String,
    val sourceNote: String,
)

/** 会期の1日分（開場・閉場時刻） */
data class FairDay(
    val date: LocalDate,
    val open: LocalTime,
    val close: LocalTime,
)

data class Admission(
    val label: String,
    val price: String,
    val note: String,
)

/** 期限つきのお知らせ。until を過ぎると表示しない */
data class Notice(
    val text: String,
    val until: LocalDateTime?,
) {
    fun isActive(now: ZonedDateTime): Boolean {
        val limit = until ?: return true
        return now.withZoneSameInstant(FAIR_ZONE).toLocalDateTime().isBefore(limit)
    }
}

data class Hall(
    val id: String,
    val name: String,
    val location: String,
    val prefixes: List<String>,
    val categoryLabel: String,
    val categories: List<String>,
    val highlights: List<String>,
    val mapUrl: String?,
    val pdfUrl: String?,
)

enum class EventCategory(val key: String, val display: String) {
    TALK("talk", "講演・ステージ"),
    WORKSHOP("workshop", "出展者WS"),
    ASSOCIATION("assoc", "IMEC・団体"),
    STUDENT("student", "学生");

    companion object {
        fun fromKey(key: String): EventCategory =
            entries.firstOrNull { it.key == key } ?: TALK
    }
}

/** 講演・ワークショップなど、時刻つきの予定 */
data class FairEvent(
    val id: String,
    val date: LocalDate,
    val start: LocalTime?,
    val end: LocalTime?,
    val category: EventCategory,
    val label: String,
    val title: String,
    val speaker: String,
    val place: String,
    val entry: String,
    val url: String?,
) {
    fun startDateTime(): ZonedDateTime? = start?.let { date.atTime(it).atZone(FAIR_ZONE) }
}

data class InfoItem(val title: String, val body: String)

data class LinkItem(val title: String, val desc: String, val url: String)

data class LinkGroup(val title: String, val items: List<LinkItem>)

data class FairData(
    val info: FairInfo,
    val days: List<FairDay>,
    val admission: List<Admission>,
    val visitorNotes: List<String>,
    val notices: List<Notice>,
    val halls: List<Hall>,
    val hallNotes: List<String>,
    val events: List<FairEvent>,
    val scheduleNotes: List<String>,
    val access: List<InfoItem>,
    val linkGroups: List<LinkGroup>,
)
