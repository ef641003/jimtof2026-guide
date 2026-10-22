package jp.fairguide.jimtof2026.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.fairguide.jimtof2026.data.Admission
import jp.fairguide.jimtof2026.data.FairData
import jp.fairguide.jimtof2026.data.FairDay
import jp.fairguide.jimtof2026.data.FairInfo
import jp.fairguide.jimtof2026.data.Notice
import jp.fairguide.jimtof2026.logic.EventPreview
import jp.fairguide.jimtof2026.logic.FAIR_ZONE
import jp.fairguide.jimtof2026.logic.FairStatus
import jp.fairguide.jimtof2026.logic.fairStatus
import jp.fairguide.jimtof2026.logic.formatDuration
import jp.fairguide.jimtof2026.logic.hm
import jp.fairguide.jimtof2026.logic.jpShort
import jp.fairguide.jimtof2026.logic.previewEvents
import jp.fairguide.jimtof2026.logic.timeRange
import jp.fairguide.jimtof2026.ui.components.BulletText
import jp.fairguide.jimtof2026.ui.components.LabeledRow
import jp.fairguide.jimtof2026.ui.components.SectionCard
import java.time.LocalDate
import java.time.ZonedDateTime

@Composable
fun OverviewScreen(
    data: FairData,
    now: ZonedDateTime,
    onOpenSchedule: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val status = remember(now, data) { fairStatus(data.days, now) }
    val preview = remember(now, data) { previewEvents(data.events, status, now) }
    val notices = remember(now, data) { data.notices.filter { it.isActive(now) } }
    val today = remember(now) { now.withZoneSameInstant(FAIR_ZONE).toLocalDate() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeroCard(data.info, status, data.days.size) }
        if (notices.isNotEmpty()) {
            item { NoticeCard(notices) }
        }
        if (preview != null) {
            item { PreviewCard(preview, onOpenSchedule) }
        }
        item { DaysCard(data.days, today) }
        item {
            SectionCard("開催データ") {
                LabeledRow("会場", "${data.info.venue}（${data.info.address}）")
                LabeledRow("ホール", data.info.halls)
                LabeledRow("展示面積", data.info.area)
                LabeledRow("出展規模", data.info.scale)
                LabeledRow("主催", data.info.organizers)
            }
        }
        item {
            AdmissionCard(
                items = data.admission,
                onRegister = { onOpenUrl(data.info.registrationUrl) },
                onMyPage = { onOpenUrl(data.info.mypageUrl) },
            )
        }
        item {
            SectionCard("来場者証と入場") {
                data.visitorNotes.forEach { BulletText(it) }
            }
        }
        item { AboutText(data.info) }
    }
}

@Composable
private fun HeroCard(info: FairInfo, status: FairStatus, totalDays: Int) {
    val dark = isSystemInDarkTheme()
    val container = if (dark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val content = if (dark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
    val (headline, detail) = statusTexts(status, totalDays)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(text = info.nameJa, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(text = "${info.period}・${info.venue}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = content.copy(alpha = 0.12f),
                contentColor = content,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(text = headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(text = detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun statusTexts(status: FairStatus, totalDays: Int): Pair<String, String> = when (status) {
    is FairStatus.Before -> Pair(
        if (status.daysLeft <= 1L) "開幕は明日です" else "開幕まであと${status.daysLeft}日",
        "初日 ${status.firstDay.date.jpShort()} ${status.firstDay.open.hm()} 開場",
    )
    is FairStatus.BeforeOpen -> Pair(
        "本日 ${status.dayIndex}日目（全${totalDays}日）",
        "${status.day.open.hm()} 開場まであと${formatDuration(status.minutesToOpen)}",
    )
    is FairStatus.Open -> Pair(
        "開催中・${status.dayIndex}日目（全${totalDays}日）",
        (if (status.isLastDay) "最終日は" else "本日は") +
            "${status.day.close.hm()}まで（あと${formatDuration(status.minutesToClose)}）",
    )
    is FairStatus.ClosedForDay -> Pair(
        "本日の展示は終了しました",
        "次は ${status.next.date.jpShort()} ${status.next.open.hm()} 開場",
    )
    FairStatus.Ended -> Pair("JIMTOF2026 は終了しました", "ご来場おつかれさまでした")
}

@Composable
private fun NoticeCard(notices: List<Notice>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("お知らせ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            notices.forEach { BulletText(it.text) }
        }
    }
}

@Composable
private fun PreviewCard(preview: EventPreview, onMore: () -> Unit) {
    SectionCard(preview.heading) {
        preview.events.forEachIndexed { index, event ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Row {
                Text(
                    text = event.timeRange(),
                    modifier = Modifier.width(96.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = event.place,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(onClick = onMore, modifier = Modifier.align(Alignment.End)) {
            Text("予定をすべて見る")
        }
    }
}

@Composable
private fun DaysCard(days: List<FairDay>, today: LocalDate) {
    val lastDay = days.last()
    SectionCard("会期と開場時間") {
        days.forEachIndexed { index, day ->
            val isToday = day.date == today
            val shortDay = day.close != days.first().close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isToday) "今日" else "${index + 1}日目",
                    modifier = Modifier.width(44.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = day.date.jpShort(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
                Text(
                    text = "${day.open.hm()}〜${day.close.hm()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (shortDay) FontWeight.Bold else FontWeight.Normal,
                    color = if (shortDay) MaterialTheme.colorScheme.secondary else Color.Unspecified,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "最終日（${lastDay.date.jpShort()}）は${lastDay.close.hm()}で終了します",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun AdmissionCard(items: List<Admission>, onRegister: () -> Unit, onMyPage: () -> Unit) {
    SectionCard("入場料（税込）") {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (item.note.isNotBlank()) {
                        Text(
                            text = item.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(item.price, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRegister, modifier = Modifier.weight(1f)) { Text("来場登録") }
            OutlinedButton(onClick = onMyPage, modifier = Modifier.weight(1f)) { Text("マイページ") }
        }
    }
}

@Composable
private fun AboutText(info: FairInfo) {
    Text(
        text = "このアプリは主催者とは関係のない非公式ガイドです。情報は${info.verifiedOn}時点の" +
            "${info.sourceNote}。講演や会場の情報は変更されることがあるため、最新情報は公式サイトで確認してください。",
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
