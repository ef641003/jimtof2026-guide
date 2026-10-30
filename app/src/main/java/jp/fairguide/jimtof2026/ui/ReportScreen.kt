package jp.fairguide.jimtof2026.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import jp.fairguide.jimtof2026.data.ReportData
import jp.fairguide.jimtof2026.data.ReportItem
import jp.fairguide.jimtof2026.data.ReportNotes
import jp.fairguide.jimtof2026.data.RouteSettings
import jp.fairguide.jimtof2026.logic.BoothCode
import jp.fairguide.jimtof2026.logic.FAIR_ZONE
import jp.fairguide.jimtof2026.logic.ReportDraft
import jp.fairguide.jimtof2026.logic.ReportPreset
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
 * 見学レポートのタブ。項目（①②⑤）ごとに「おすすめの会社」と見どころを出し、
 * ブースで見たことをその場でメモして、下書きとして共有する。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(vm: AppViewModel, snackbarHostState: SnackbarHostState, onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val data = vm.reportData
    val notes = vm.reportNotes
    var byHall by rememberSaveable { mutableStateOf(false) }
    var noteTarget by remember { mutableStateOf<ReportDraft.Row?>(null) }

    LaunchedEffect(Unit) { vm.ensureReport() }
    LaunchedEffect(vm.reportItemId) { listState.scrollToItem(0) }

    if (data == null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("レポートの候補を読み込んでいます…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    if (vm.reportItemId == RouteSettings.ID) {
        RouteScreen(vm = vm, data = data, snackbarHostState = snackbarHostState) {
            ReportItemChips(data = data, notes = notes, selectedId = RouteSettings.ID, onSelect = { vm.selectReportItem(it) })
        }
        return
    }

    val item = data.item(vm.reportItemId) ?: data.items.first()
    val rows = remember(data, item.id, notes.extras, byHall) {
        val base = ReportDraft.rows(data, item.id, notes)
        if (byHall) ReportDraft.rowsByHall(base) else base
    }
    val noteCount = remember(notes) { notes.notes.count { it.value.isNotBlank() } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "items") {
            Column {
                ReportItemChips(data = data, notes = notes, selectedId = item.id, onSelect = { vm.selectReportItem(it) })
                Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = {
                            val today = LocalDate.now(FAIR_ZONE).toString().replace('-', '/')
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "JIMTOF2026 見学レポート 下書き")
                                putExtra(Intent.EXTRA_TEXT, ReportDraft.text(data, notes, today))
                            }
                            context.startActivity(Intent.createChooser(send, "下書きを共有"))
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("下書きを共有")
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (noteCount == 0) "メモはまだありません" else "メモ ${noteCount}件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "head-" + item.id) {
            ItemHeadCard(
                item = item,
                byHall = byHall,
                onToggleOrder = { byHall = it },
                onOpenSearch = if (item.presetName != null) {
                    { vm.openSearchWithPreset(ReportPreset.entries.firstOrNull { it.name == item.presetName }) }
                } else {
                    null
                },
            )
        }

        item(key = "summary-" + item.id) {
            SummaryCard(
                itemId = item.id,
                text = notes.summaries[item.id].orEmpty(),
                onChange = { vm.setReportSummary(item.id, it) },
            )
        }

        item(key = "list-header-" + item.id) {
            SectionHeader("おすすめの会社（${rows.size}社）")
        }

        items(rows, key = { item.id + "|" + it.key }) { row ->
            CompanyCard(
                row = row,
                data = data,
                note = notes.note(item.id, row.key),
                inMyList = vm.myList.any { it.company == row.name && it.booth.equals(row.booth, ignoreCase = true) },
                onNote = { noteTarget = row },
                onAddToMyList = {
                    val ex = vm.exhibitorIndex?.data?.exhibitors?.firstOrNull { it.name == row.name && it.booths.first() == row.booth }
                    val added = if (ex != null) {
                        vm.addExhibitorToMyList(ex, row.booth)
                    } else {
                        vm.addToMyListByName(row.name, row.booth)
                    }
                    scope.launch {
                        snackbarHostState.showSnackbar(if (added) "ブースリストに追加しました" else "すでにブースリストにあります")
                    }
                },
                onOpenUrl = onOpenUrl,
            )
        }

        item(key = "foot") {
            Caption(
                data.note + "（${data.retrievedOn} 時点の公開情報）。メモ・まとめ・足した会社は、この端末の中だけに保存されます。",
            )
        }
    }

    noteTarget?.let { row ->
        NoteDialog(
            row = row,
            initial = notes.note(item.id, row.key).orEmpty(),
            onSave = {
                vm.setReportNote(item.id, row.key, it)
                noteTarget = null
            },
            onRemove = if (row.extra != null) {
                {
                    vm.removeReportCompany(item.id, row.key)
                    noteTarget = null
                }
            } else {
                null
            },
            onDismiss = { noteTarget = null },
        )
    }
}

/** 項目（①②⑤）と「ルート」を切り替えるチップ。メモの数も添える */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReportItemChips(data: ReportData, notes: ReportNotes, selectedId: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        data.items.forEach { ri ->
            val count = notes.notes.keys.count { k -> k.startsWith(ri.id + "|") }
            FilterChip(
                selected = ri.id == selectedId,
                onClick = { onSelect(ri.id) },
                label = { Text(if (count > 0) "${ri.short}（$count）" else ri.short) },
            )
        }
        FilterChip(
            selected = selectedId == RouteSettings.ID,
            onClick = { onSelect(RouteSettings.ID) },
            label = { Text("ルート") },
            leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun ItemHeadCard(
    item: ReportItem,
    byHall: Boolean,
    onToggleOrder: (Boolean) -> Unit,
    onOpenSearch: (() -> Unit)?,
) {
    GuideCard(accent = MaterialTheme.colorScheme.primary) {
        Text(text = "${item.mark} ${item.title}", style = MaterialTheme.typography.titleMedium)
        Text(
            text = item.intro,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "会場で見るポイント",
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        item.guide.forEach { BulletText(it) }
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("並び順", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = !byHall, onClick = { onToggleOrder(false) }, label = { Text("おすすめ順") })
            Spacer(Modifier.width(6.dp))
            FilterChip(selected = byHall, onClick = { onToggleOrder(true) }, label = { Text("順路順") })
        }
        if (onOpenSearch != null) {
            TextButton(onClick = onOpenSearch, contentPadding = PaddingValues(0.dp)) {
                Text("検索タブで、ほかの候補（分類からの機械的な絞り込み）を見る")
            }
        }
    }
}

@Composable
private fun SummaryCard(itemId: String, text: String, onChange: (String) -> Unit) {
    // 入力中の文字は画面側で持ち、保存はそのつど行う（項目を切り替えると保存済みの文字に戻る）
    var value by rememberSaveable(itemId) { mutableStateOf(text) }
    Column {
        SectionHeader("この項目のまとめ")
        GuideCard {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it.take(2000)
                    onChange(value)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("全体の傾向、気づき、持ち帰ること。下書きの［まとめ］に入ります") },
                minLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanyCard(
    row: ReportDraft.Row,
    data: ReportData,
    note: String?,
    inMyList: Boolean,
    onNote: () -> Unit,
    onAddToMyList: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val location = remember(row.booth) { BoothCode.locate(row.booth) }
    val color = placeColorFor(location?.hallId)
    var expanded by rememberSaveable(row.key) { mutableStateOf(false) }
    val company = row.company

    GuideCard(accent = color.strong) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoothBadge(row.booth, large = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = location?.let { "${it.hallName}・${it.floor}" } ?: "ホール不明",
                    style = MaterialTheme.typography.labelLarge,
                    color = color.strong,
                    fontWeight = FontWeight.Bold,
                )
                if (row.extra != null) {
                    Text("自分で追加", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!note.isNullOrBlank()) Tag("メモあり", GuideTheme.colors.good, bold = true)
        }
        Spacer(Modifier.height(8.dp))
        Text(text = row.name, style = MaterialTheme.typography.titleMedium)
        if (company != null) {
            Text(
                text = company.why,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (!note.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = GuideTheme.colors.good.container,
                contentColor = GuideTheme.colors.good.onContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = note,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (company != null && expanded) {
            Text(
                text = "見どころ（公開情報）",
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            company.facts.forEach { BulletText(it) }
            Text(
                text = "ブースで確かめること",
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            company.look.forEach { BulletText(it) }
            company.sourceIds.mapNotNull { data.sources[it] }.distinct().forEach { src ->
                TextButton(onClick = { onOpenUrl(src.url) }, contentPadding = PaddingValues(0.dp)) {
                    Text("出典：${src.label}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            FilledTonalButton(onClick = onNote) {
                Icon(Icons.Filled.Create, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (note.isNullOrBlank()) "メモを書く" else "メモを編集")
            }
            if (company != null) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "見どころを閉じる" else "見どころを見る")
                }
            }
            TextButton(onClick = onAddToMyList, enabled = !inMyList) {
                Icon(if (inMyList) Icons.Filled.Check else Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (inMyList) "リスト済み" else "ブースリストへ")
            }
        }
    }
}

@Composable
private fun NoteDialog(
    row: ReportDraft.Row,
    initial: String,
    onSave: (String) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var memo by rememberSaveable(row.key) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ブースで見たこと") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${BoothCode.normalize(row.booth).ifEmpty { row.booth }} ${row.name}", style = MaterialTheme.typography.bodyMedium)
                row.company?.let { c ->
                    Text(
                        text = c.look.joinToString("／"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it.take(1500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("業界・展示機・付加機能・説明員の話など") },
                    minLines = 4,
                )
                Text(
                    "下書きには、メモを書いた会社だけが出ます。端末の中だけに保存します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(memo) }) { Text("保存") } },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onClick = onRemove) { Text("項目から外す") }
                TextButton(onClick = onDismiss) { Text("閉じる") }
            }
        },
    )
}
