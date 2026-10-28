package jp.fairguide.jimtof2026.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import jp.fairguide.jimtof2026.data.ReportData
import jp.fairguide.jimtof2026.data.RouteSettings
import jp.fairguide.jimtof2026.logic.FAIR_ZONE
import jp.fairguide.jimtof2026.logic.FairStatus
import jp.fairguide.jimtof2026.logic.RouteEntry
import jp.fairguide.jimtof2026.logic.RoutePlan
import jp.fairguide.jimtof2026.logic.RoutePlanner
import jp.fairguide.jimtof2026.logic.RouteStop
import jp.fairguide.jimtof2026.logic.VenueWalk
import jp.fairguide.jimtof2026.logic.fairStatus
import jp.fairguide.jimtof2026.logic.formatDuration
import jp.fairguide.jimtof2026.logic.hm
import jp.fairguide.jimtof2026.ui.components.BoothBadge
import jp.fairguide.jimtof2026.ui.components.BulletText
import jp.fairguide.jimtof2026.ui.components.Caption
import jp.fairguide.jimtof2026.ui.components.GuideCard
import jp.fairguide.jimtof2026.ui.components.SectionHeader
import jp.fairguide.jimtof2026.ui.components.Tag
import jp.fairguide.jimtof2026.ui.components.placeColorFor
import jp.fairguide.jimtof2026.ui.theme.GuideTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * レポートタブの「ルート」。選んだ項目のおすすめ会社を、出発地点から歩く時間が短くなる順に並べ、
 * 所要時間の目安と「何日に分けるか」を示す。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteScreen(
    vm: AppViewModel,
    data: ReportData,
    snackbarHostState: SnackbarHostState,
    chips: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val notes = vm.reportNotes
    val settings = notes.route
    val myList = vm.myList
    val stops = remember(data, notes.extras, settings.itemIds, settings.includeMyList, myList) {
        RoutePlanner.collect(data, notes, myList)
    }
    val plan = remember(stops, settings, notes.routeSkips) { RoutePlanner.plan(stops, settings, notes.routeSkips) }
    val status = remember(vm.now, vm.data) { fairStatus(vm.data.days, vm.now) }
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "items") { chips() }

        item(key = "settings") {
            SettingsCard(data = data, settings = settings, vm = vm)
        }

        item(key = "summary") {
            SummaryCard(
                plan = plan,
                settings = settings,
                status = status,
                onAddAll = {
                    val targets = plan.entries.mapNotNull { (it as? RouteEntry.Stop)?.stop } +
                        plan.entries.mapNotNull { (it as? RouteEntry.Unknown)?.stop }
                    val added = vm.addRouteToMyList(targets)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (added == 0) "すべてブースリストにあります" else "${added}社をブースリストに入れました（登録順で並べると、この順になります）",
                        )
                    }
                },
                onShare = {
                    val today = LocalDate.now(FAIR_ZONE).toString().replace('-', '/')
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "JIMTOF2026 見学ルート")
                        putExtra(Intent.EXTRA_TEXT, RoutePlanner.text(plan, data, settings, today))
                    }
                    context.startActivity(Intent.createChooser(send, "ルートを共有"))
                },
            )
        }

        if (plan.stops == 0) {
            item(key = "empty") {
                GuideCard {
                    Text("ルートに入る会社がありません", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "上の「回る対象」で項目を選ぶか、外した会社を戻してください。",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(
            plan.entries,
            key = { e ->
                when (e) {
                    is RouteEntry.Day -> "day-${e.day}"
                    is RouteEntry.Hall -> "hall-${e.day}-${e.hallId}"
                    is RouteEntry.Stop -> "stop-${e.stop.key}"
                    is RouteEntry.Unknown -> "unknown-${e.stop.key}"
                }
            },
        ) { e ->
            when (e) {
                is RouteEntry.Day -> if (plan.days > 1) {
                    SectionHeader("${e.day}日目　${e.stops}社・約${formatDuration(e.minutes.toLong())}")
                }
                is RouteEntry.Hall -> HallHeader(e)
                is RouteEntry.Stop -> StopCard(
                    entry = e,
                    expanded = expandedKey == e.stop.key,
                    inMyList = myList.any { it.company == e.stop.name && it.booth.equals(e.stop.booth, ignoreCase = true) },
                    onToggle = { expandedKey = if (expandedKey == e.stop.key) null else e.stop.key },
                    onSkip = { vm.toggleRouteSkip(e.stop.key) },
                    onAddToMyList = {
                        val added = vm.addRouteToMyList(listOf(e.stop))
                        scope.launch { snackbarHostState.showSnackbar(if (added > 0) "ブースリストに追加しました" else "すでにブースリストにあります") }
                    },
                )
                is RouteEntry.Unknown -> UnknownCard(e.stop, onSkip = { vm.toggleRouteSkip(e.stop.key) })
            }
        }

        if (plan.skipped.isNotEmpty()) {
            item(key = "skipped-header") {
                SectionHeader("ルートから外した会社（${plan.skipped.size}）") {
                    TextButton(onClick = { vm.clearRouteSkips() }) { Text("すべて戻す") }
                }
            }
            item(key = "skipped") {
                GuideCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    plan.skipped.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${s.code}　${s.name}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { vm.toggleRouteSkip(s.key) }) { Text("戻す") }
                        }
                    }
                }
            }
        }

        item(key = "foot") {
            Caption(
                "ホールの位置関係・距離・所要時間は目安で、実際の通路や混み具合で前後します。" +
                    "同じホールの中は小間番号の順です（近い番号は近くにあることが多いですが、小間の場所は公式の小間割図で確認してください）。" +
                    "複数の項目に載っている会社は、項目が1つ増えるごとに＋${RoutePlanner.EXTRA_ITEM_MINUTES}分で見ています。設定はこの端末の中だけに保存されます。",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsCard(data: ReportData, settings: RouteSettings, vm: AppViewModel) {
    GuideCard(accent = MaterialTheme.colorScheme.primary) {
        Text("効率よく回るルート", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "選んだ項目のおすすめの会社（自分で足した会社も含む）を、出発地点から歩く時間が短くなる順に並べます。" +
                "ホールごとにまとめ、東⇔西のような長い移動を減らします。",
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingLabel("回る対象")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data.items.forEach { ri ->
                FilterChip(
                    selected = ri.id in settings.itemIds,
                    onClick = { vm.toggleRouteItem(ri.id) },
                    label = { Text(ri.short) },
                )
            }
            FilterChip(
                selected = settings.includeMyList,
                onClick = { vm.updateRoute { it.copy(includeMyList = !it.includeMyList) } },
                label = { Text("ブースリストの会社") },
            )
            FilterChip(
                selected = settings.skipVisited,
                onClick = { vm.updateRoute { it.copy(skipVisited = !it.skipVisited) } },
                label = { Text("訪問済みを外す") },
            )
        }

        SettingLabel("出発地点")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VenueWalk.Start.entries.forEach { st ->
                FilterChip(
                    selected = settings.start == st.id,
                    onClick = { vm.updateRoute { it.copy(start = st.id) } },
                    label = { Text(st.label) },
                )
            }
        }

        SettingLabel("1社あたりの滞在")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 15, 20, 30).forEach { m ->
                FilterChip(
                    selected = settings.minutesPerStop == m,
                    onClick = { vm.updateRoute { it.copy(minutesPerStop = m) } },
                    label = { Text("${m}分") },
                )
            }
        }

        SettingLabel("1日に回れる時間")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(180 to "3時間", 300 to "5時間", 420 to "7時間", NO_SPLIT to "分けない").forEach { (m, label) ->
                FilterChip(
                    selected = settings.minutesPerDay == m,
                    onClick = { vm.updateRoute { it.copy(minutesPerDay = m) } },
                    label = { Text(label) },
                )
            }
        }
    }
}

/** 「分けない」のときの1日の持ち時間（会期中に収まらない大きさ） */
private const val NO_SPLIT = 100_000

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(
    plan: RoutePlan,
    settings: RouteSettings,
    status: FairStatus,
    onAddAll: () -> Unit,
    onShare: () -> Unit,
) {
    GuideCard {
        Text(
            text = if (plan.stops == 0) "0社" else "全 ${plan.stops}社・${plan.halls}ホール",
            style = MaterialTheme.typography.titleMedium,
        )
        if (plan.stops > 0) {
            Text(
                text = "約${formatDuration(plan.totalMinutes.toLong())}（うち移動 約${plan.walkMinutes}分、1社 約${settings.minutesPerStop}分として）",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (plan.days > 1) {
            Text(
                text = "1日 約${formatDuration(settings.minutesPerDay.toLong())}として ${plan.days}日に分けています",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (plan.skippedVisited > 0) {
            Text(
                text = "訪問済みの ${plan.skippedVisited}社は外しています",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (status is FairStatus.Open) {
            val fits = plan.stops == 0 || status.minutesToClose >= firstDayMinutes(plan)
            Text(
                text = "いま会場なら：閉場（${status.day.close.hm()}）まであと${formatDuration(status.minutesToClose)}" +
                    if (fits) "" else "。今日の分（約${formatDuration(firstDayMinutes(plan).toLong())}）は収まらないので、上の時間を短くするか会社を外してください",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (fits) MaterialTheme.colorScheme.onSurfaceVariant else GuideTheme.colors.warn.onContainer,
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = onAddAll, enabled = plan.stops > 0) {
                Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("この順でブースリストへ")
            }
            TextButton(onClick = onShare, enabled = plan.stops > 0) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ルートを共有")
            }
        }
    }
}

private fun firstDayMinutes(plan: RoutePlan): Int =
    (plan.entries.firstOrNull { it is RouteEntry.Day } as? RouteEntry.Day)?.minutes ?: plan.totalMinutes

@Composable
private fun HallHeader(e: RouteEntry.Hall) {
    val color = placeColorFor(e.hallId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(34.dp)
                .background(color.strong, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${e.hallName}・${e.floor}　${e.stops}社",
                style = MaterialTheme.typography.titleSmall,
                color = color.strong,
            )
            Text(
                text = "${e.fromLabel}から徒歩 約${e.walkMinutes}分",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = RoutePlanner.elapsedLabel(e.arriveMinutes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopCard(
    entry: RouteEntry.Stop,
    expanded: Boolean,
    inMyList: Boolean,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onAddToMyList: () -> Unit,
) {
    val stop = entry.stop
    val color = placeColorFor(stop.hallId)
    GuideCard(onClick = onToggle, accent = color.strong, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BoothBadge(stop.booth)
                    stop.itemIds.forEach { Tag(RoutePlanner.itemMark(it), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, bold = true) }
                    if (stop.fromMyList && stop.itemIds.isEmpty()) Tag("ブースリスト", color, bold = false)
                    if (stop.visited) Tag("訪問済み", GuideTheme.colors.good, bold = true)
                }
                Text(
                    text = stop.name,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (stop.visited) TextDecoration.LineThrough else TextDecoration.None,
                )
                val firstWhy = stop.whys.values.firstOrNull()
                if (firstWhy != null && !expanded) {
                    Text(
                        text = firstWhy,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = RoutePlanner.elapsedLabel(entry.arriveMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "約${entry.stayMinutes}分",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            stop.whys.forEach { (id, why) ->
                Text(
                    text = "${RoutePlanner.itemMark(id)} $why",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (stop.checks.isNotEmpty()) {
                Text(
                    text = "ブースで確かめること",
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                stop.checks.forEach { BulletText(it) }
            }
            FlowRow(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onAddToMyList, enabled = !inMyList) {
                    Icon(if (inMyList) Icons.Filled.Check else Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (inMyList) "リスト済み" else "ブースリストへ")
                }
                TextButton(onClick = onSkip) { Text("ルートから外す") }
            }
        }
    }
}

@Composable
private fun UnknownCard(stop: RouteStop, onSkip: () -> Unit) {
    GuideCard(contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stop.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${stop.code}：ホールが分からないので順番に入れていません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onSkip) { Text("外す") }
        }
    }
}
