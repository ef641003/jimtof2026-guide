package jp.fairguide.jimtof2026.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import jp.fairguide.jimtof2026.ui.components.Caption
import jp.fairguide.jimtof2026.ui.components.GuideCard
import jp.fairguide.jimtof2026.ui.components.LabeledRow
import jp.fairguide.jimtof2026.ui.components.ListRow
import jp.fairguide.jimtof2026.ui.components.SectionHeader
import jp.fairguide.jimtof2026.ui.components.placeColorForPlaceName
import jp.fairguide.jimtof2026.ui.theme.GuideTheme
import java.time.LocalDate
import java.time.ZonedDateTime

@Composable
fun OverviewScreen(
    data: FairData,
    now: ZonedDateTime,
    onOpenSchedule: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLinks: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val status = remember(now, data) { fairStatus(data.days, now) }
    val preview = remember(now, data) { previewEvents(data.events, status, now) }
    val notices = remember(now, data) { data.notices.filter { it.isActive(now) } }
    val today = remember(now) { now.withZoneSameInstant(FAIR_ZONE).toLocalDate() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeroCard(data.info, status, data.days.size) }
        item { QuickActions(status, onOpenSearch, onOpenSchedule) }
        item { AssignmentCard(onOpenReport) }
        if (notices.isNotEmpty()) {
            item { NoticeCard(notices) }
        }
        if (preview != null) {
            item { PreviewCard(preview, onOpenSchedule) }
        }
        item { DaysCard(data.days, today) }
        item {
            AdmissionCard(
                items = data.admission,
                onRegister = { onOpenUrl(data.info.registrationUrl) },
                onMyPage = { onOpenUrl(data.info.mypageUrl) },
            )
        }
        item {
            SectionHeader("来場者証と入場")
            GuideCard { data.visitorNotes.forEach { BulletText(it) } }
        }
        item {
            SectionHeader("アクセス・公式リンク")
            GuideCard(contentPadding = PaddingValues(0.dp)) {
                ListRow(
                    title = "アクセス・公式リンクを開く",
                    description = "交通アクセス、来場登録・マイページ、出展者検索、講演の公式ページ、問い合わせ先",
                    icon = Icons.Filled.Info,
                    onClick = onOpenLinks,
                )
            }
        }
        item {
            SectionHeader("開催データ")
            GuideCard {
                LabeledRow("会場", "${data.info.venue}（${data.info.address}）")
                LabeledRow("ホール", data.info.halls)
                LabeledRow("展示面積", data.info.area)
                LabeledRow("出展規模", data.info.scale)
                LabeledRow("主催", data.info.organizers)
            }
        }
        item { AboutText(data.info) }
    }
}

/** 担当エリア（西展示棟）と、今年の会場での実際の場所 */
@Composable
private fun AssignmentCard(onOpenReport: () -> Unit) {
    val west = GuideTheme.colors.west
    Column {
        SectionHeader("担当エリア：西展示棟")
        GuideCard(accent = west.strong) {
            Text("西展示棟（西1〜4ホール）", style = MaterialTheme.typography.titleMedium, color = west.strong)
            Text(
                text = "主に切削工具（チップ、エンドミル、タップなど）、保持具（チャック、ホルダー）、測定機器の展示エリア。" +
                    "ほかの展示棟も確認してください。",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "今年の出展社カタログでは、切削工具の主要メーカーは西1ホール、保持具は西2ホールに集まっています。" +
                    "測定機器の主要メーカー（ミツトヨ・東京精密・ニコン・レニショーなど）は東7ホールが中心で、キーエンスは南4です。",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onOpenReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(48.dp),
            ) { Text("テーマ別のおすすめ会社と回る順を見る") }
        }
    }
}

/** いちばん上：いま何をすべきかが一目でわかる状態表示 */
@Composable
private fun HeroCard(info: FairInfo, status: FairStatus, totalDays: Int) {
    val dark = isSystemInDarkTheme()
    val container = if (dark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val content = if (dark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
    val texts = statusTexts(status, totalDays)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status, content)
                Spacer(Modifier.width(8.dp))
                Text(text = texts.kicker, style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.9f))
            }
            Spacer(Modifier.height(6.dp))
            texts.lead?.let { Text(text = it, style = MaterialTheme.typography.titleMedium, color = content.copy(alpha = 0.92f)) }
            Text(
                text = texts.big,
                style = if (texts.numeric) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            )
            Text(text = texts.detail, style = MaterialTheme.typography.bodyLarge, color = content.copy(alpha = 0.92f))
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = content.copy(alpha = 0.25f))
            Spacer(Modifier.height(10.dp))
            Text(text = "${info.title}　${info.nameJa}", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${info.period}・${info.venue}",
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun StatusDot(status: FairStatus, fallback: Color) {
    val color = when (status) {
        is FairStatus.Open -> GuideTheme.colors.good.strong
        is FairStatus.BeforeOpen, is FairStatus.Before -> GuideTheme.colors.warn.strong
        else -> fallback.copy(alpha = 0.6f)
    }
    Box(
        Modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}

/** kicker＝状態、lead＋big＝いちばん伝えたい数字、detail＝補足 */
private class StatusTexts(val kicker: String, val lead: String?, val big: String, val numeric: Boolean, val detail: String)

private fun statusTexts(status: FairStatus, totalDays: Int): StatusTexts = when (status) {
    is FairStatus.Before -> {
        val detail = "初日 ${status.firstDay.date.jpShort()} ${status.firstDay.open.hm()} 開場"
        if (status.daysLeft <= 1L) StatusTexts("開幕前", null, "開幕は明日", false, detail)
        else StatusTexts("開幕前", "開幕まであと", "${status.daysLeft}日", true, detail)
    }
    is FairStatus.BeforeOpen -> StatusTexts(
        "本日 ${status.dayIndex}日目（全${totalDays}日）",
        "開場まであと",
        formatDuration(status.minutesToOpen),
        true,
        "${status.day.open.hm()} 開場・${status.day.close.hm()} 閉場",
    )
    is FairStatus.Open -> StatusTexts(
        "開催中・${status.dayIndex}日目（全${totalDays}日）",
        "閉場まであと",
        formatDuration(status.minutesToClose),
        true,
        (if (status.isLastDay) "最終日は" else "本日は") + "${status.day.close.hm()}まで",
    )
    is FairStatus.ClosedForDay -> StatusTexts(
        "本日 ${status.dayIndex}日目は終了",
        null,
        "本日の展示は終了しました",
        false,
        "次は ${status.next.date.jpShort()} ${status.next.open.hm()} 開場",
    )
    FairStatus.Ended -> StatusTexts("会期終了", null, "JIMTOF2026 は終了しました", false, "ご来場おつかれさまでした")
}

/** よく使う2つの操作を、親指の届くところに大きく置く（文字は1行に収まる長さにする） */
@Composable
private fun QuickActions(status: FairStatus, onOpenSearch: () -> Unit, onOpenSchedule: () -> Unit) {
    val duringFair = status is FairStatus.Open || status is FairStatus.BeforeOpen || status is FairStatus.ClosedForDay
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onOpenSearch,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("出展社検索", style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
        FilledTonalButton(
            onClick = onOpenSchedule,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (duringFair) "今日の予定" else "予定を見る", style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

@Composable
private fun NoticeCard(notices: List<Notice>) {
    val c = GuideTheme.colors.warn
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.container, contentColor = c.onContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = c.strong)
                Spacer(Modifier.width(6.dp))
                Text("お知らせ", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            notices.forEach { BulletText(it.text) }
        }
    }
}

@Composable
private fun PreviewCard(preview: EventPreview, onMore: () -> Unit) {
    Column {
        SectionHeader(preview.heading)
        GuideCard {
            preview.events.forEachIndexed { index, event ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                val place = placeColorForPlaceName(event.place)
                Row {
                    Text(
                        text = event.timeRange(),
                        modifier = Modifier.width(100.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(text = event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(place.strong, CircleShape),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = event.place,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onMore, modifier = Modifier.align(Alignment.End)) {
                Text("予定をすべて見る")
            }
        }
    }
}

@Composable
private fun DaysCard(days: List<FairDay>, today: LocalDate) {
    val lastDay = days.last()
    Column {
        SectionHeader("会期と開場時間")
        GuideCard(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
            days.forEachIndexed { index, day ->
                val isToday = day.date == today
                val shortDay = day.close != days.first().close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isToday) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isToday) "今日" else "${index + 1}日目",
                        modifier = Modifier.width(52.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isToday) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = day.date.jpShort(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    Text(
                        text = "${day.open.hm()}〜${day.close.hm()}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (shortDay || isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (shortDay) MaterialTheme.colorScheme.secondary else Color.Unspecified,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = "最終日（${lastDay.date.jpShort()}）は${lastDay.close.hm()}で終了します",
                modifier = Modifier.padding(start = 10.dp, top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun AdmissionCard(items: List<Admission>, onRegister: () -> Unit, onMyPage: () -> Unit) {
    Column {
        SectionHeader("入場料（税込）")
        GuideCard {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
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
                    Spacer(Modifier.width(12.dp))
                    Text(item.price, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRegister, modifier = Modifier.weight(1f).height(48.dp)) { Text("来場登録") }
                OutlinedButton(onClick = onMyPage, modifier = Modifier.weight(1f).height(48.dp)) { Text("マイページ") }
            }
        }
    }
}

@Composable
private fun AboutText(info: FairInfo) {
    Caption(
        "このアプリは主催者とは関係のない非公式ガイドです。情報は${info.verifiedOn}時点の" +
            "${info.sourceNote}。講演や会場の情報は変更されることがあるため、最新情報は公式サイトで確認してください。",
    )
}
