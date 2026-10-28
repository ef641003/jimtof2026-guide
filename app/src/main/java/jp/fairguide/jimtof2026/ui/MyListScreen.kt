package jp.fairguide.jimtof2026.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.fairguide.jimtof2026.data.BoothItem
import jp.fairguide.jimtof2026.data.CheckCodec
import jp.fairguide.jimtof2026.data.Exhibitor
import jp.fairguide.jimtof2026.logic.BoothChecklist
import jp.fairguide.jimtof2026.logic.BoothCode
import jp.fairguide.jimtof2026.logic.ExhibitorIndex
import jp.fairguide.jimtof2026.ui.components.BoothBadge
import jp.fairguide.jimtof2026.ui.components.Caption
import jp.fairguide.jimtof2026.ui.components.GuideCard
import jp.fairguide.jimtof2026.ui.components.Tag
import jp.fairguide.jimtof2026.ui.components.placeColorFor

/** マイリストの並び順 */
object MyListOrder {
    /** 東→西→南のホール順、同じホールの中は小間番号順 */
    val byHall: Comparator<BoothItem> = compareBy<BoothItem>(
        { BoothCode.hallRank(it.booth) },
        { BoothCode.normalize(it.booth) },
        { it.createdAt },
    )

    /** 画面表示用：未訪問を先に、そのうえでホール順 */
    val forScreen: Comparator<BoothItem> = compareBy<BoothItem> { it.visited }.then(byHall)

    fun shareText(items: List<BoothItem>): String {
        val sb = StringBuilder()
        sb.append("JIMTOF2026 回りたいブース（訪問済み ")
            .append(items.count { it.visited }).append('/').append(items.size).append("）\n")
        var lastHall: String? = null
        for (item in items.sortedWith(byHall)) {
            val hall = BoothCode.locate(item.booth)?.hallName ?: "小間番号なし"
            if (hall != lastHall) {
                sb.append("\n■ ").append(hall).append('\n')
                lastHall = hall
            }
            sb.append(if (item.visited) "☑ " else "☐ ")
            val code = BoothCode.normalize(item.booth)
            if (code.isNotEmpty()) sb.append(code).append(' ')
            sb.append(item.company)
            if (item.memo.isNotBlank()) sb.append(" … ").append(item.memo.replace('\n', ' '))
            sb.append('\n')
            for (c in item.checks) sb.append("　").append(if (c.done) "☑ " else "☐ ").append(c.text).append('\n')
        }
        return sb.toString().trimEnd()
    }
}

/** 一覧の見出しに使う棟の名前 */
private fun buildingLabel(item: BoothItem): String =
    BoothCode.locate(item.booth)?.let { "${it.building}（${it.floor}）" } ?: "小間番号なし・不明"

private sealed interface MyRow {
    data class Header(val label: String, val hallId: String?, val key: String) : MyRow
    data class Item(val item: BoothItem) : MyRow
}

@Composable
fun MyListScreen(vm: AppViewModel, onOpenSearch: () -> Unit) {
    val context = LocalContext.current
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BoothItem?>(null) }
    var sortByHall by rememberSaveable { mutableStateOf(true) }

    val items = vm.myList
    val rows: List<MyRow> = remember(items, sortByHall) {
        if (!sortByHall) {
            items.sortedBy { it.createdAt }.map { MyRow.Item(it) }
        } else {
            val out = ArrayList<MyRow>()
            var last: String? = null
            for (item in items.sortedWith(MyListOrder.forScreen)) {
                val label = if (item.visited) "訪問済み" else buildingLabel(item)
                if (label != last) {
                    out += MyRow.Header(label, BoothCode.locate(item.booth)?.hallId?.takeIf { !item.visited }, "h-$label")
                    last = label
                }
                out += MyRow.Item(item)
            }
            out
        }
    }
    val visitedCount = items.count { it.visited }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") {
                GuideCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (items.isEmpty()) "まだ登録がありません" else "訪問済み $visitedCount / ${items.size}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (items.isNotEmpty()) {
                                Text(
                                    text = "残り ${items.size - visitedCount} 件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, MyListOrder.shareText(items))
                                }
                                context.startActivity(Intent.createChooser(send, "ブースリストを共有"))
                            },
                            enabled = items.isNotEmpty(),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "共有")
                        }
                    }
                    if (items.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { visitedCount.toFloat() / items.size },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 10.dp),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = sortByHall, onClick = { sortByHall = true }, label = { Text("ホール順") })
                            FilterChip(selected = !sortByHall, onClick = { sortByHall = false }, label = { Text("登録順") })
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                item(key = "empty") {
                    GuideCard {
                        Text("回りたいブースを集めておく場所です", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "「検索」タブで出展社を探して「ブースリストに追加」を押すと、ここに並びます。" +
                                "東→西→南のホール順に並ぶので、回る順番がすぐに決まります。見終わったらチェックを入れてください。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onOpenSearch, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("出展社をさがして追加")
                        }
                    }
                }
            }
            items(
                rows,
                key = { row ->
                    when (row) {
                        is MyRow.Header -> row.key
                        is MyRow.Item -> row.item.id
                    }
                },
            ) { row ->
                when (row) {
                    is MyRow.Header -> GroupHeader(row.label, row.hallId)
                    is MyRow.Item -> BoothRow(
                        item = row.item,
                        onToggle = { vm.toggleVisited(row.item.id) },
                        onToggleCheck = { vm.toggleCheck(row.item.id, it) },
                        onClick = {
                            editing = row.item
                            showEditor = true
                        },
                    )
                }
            }
            item(key = "note") {
                Caption("ブースリストはこの端末の中だけに保存され、外部には送信されません。")
            }
        }

        ExtendedFloatingActionButton(
            text = { Text("追加") },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            onClick = {
                editing = null
                showEditor = true
            },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    LaunchedEffect(showEditor) { if (showEditor) vm.ensureExhibitors() }
    if (showEditor) {
        BoothEditorDialog(
            initial = editing,
            index = vm.exhibitorIndex,
            reportChecks = { c, b -> vm.reportChecksFor(c, b) },
            onDismiss = { showEditor = false },
            onSave = {
                vm.saveItem(it)
                showEditor = false
            },
            onDelete = {
                vm.deleteItem(it)
                showEditor = false
            },
        )
    }
}

@Composable
private fun GroupHeader(label: String, hallId: String?) {
    val color = placeColorFor(hallId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(18.dp)
                .background(color.strong, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun BoothRow(item: BoothItem, onToggle: () -> Unit, onToggleCheck: (Int) -> Unit, onClick: () -> Unit) {
    val location = remember(item.booth) { BoothCode.locate(item.booth) }
    val code = BoothCode.normalize(item.booth)
    GuideCard(
        onClick = onClick,
        accent = if (item.visited) null else placeColorFor(location?.hallId).strong,
        contentPadding = PaddingValues(start = 0.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = item.visited,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(48.dp),
                colors = androidx.compose.material3.CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.tertiary,
                ),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.company,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (item.visited) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.visited) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (code.isNotEmpty()) {
                        BoothBadge(code)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (location != null) {
                        Text(
                            text = "${location.hallName}・${location.floor}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (code.isNotEmpty()) {
                        Text(
                            text = "ホール不明",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            text = "小間番号なし",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.memo.isNotBlank()) {
                    Text(
                        text = item.memo,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.checks.isNotEmpty()) {
                    Text(
                        text = "確かめること ${BoothChecklist.progress(item.checks)}",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 見終わったブースは進み具合だけにして、一覧を短く保つ
                    if (!item.visited) {
                        item.checks.forEachIndexed { i, c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .toggleable(value = c.done, role = Role.Checkbox, onValueChange = { onToggleCheck(i) }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = c.done, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = c.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (c.done) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (c.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoothEditorDialog(
    initial: BoothItem?,
    index: ExhibitorIndex?,
    reportChecks: (String, String) -> List<String>,
    onDismiss: () -> Unit,
    onSave: (BoothItem) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var company by rememberSaveable { mutableStateOf(initial?.company ?: "") }
    var booth by rememberSaveable { mutableStateOf(initial?.booth ?: "") }
    var memo by rememberSaveable { mutableStateOf(initial?.memo ?: "") }
    var suggestionsOpen by remember { mutableStateOf(false) }
    val location = remember(booth) { BoothCode.locate(booth) }
    // チェックリスト：画面を回しても消えないよう文字列で持つ
    var checksRaw by rememberSaveable { mutableStateOf(CheckCodec.pack(initial?.checks ?: emptyList())) }
    var newCheck by rememberSaveable { mutableStateOf("") }
    val checks = remember(checksRaw) { CheckCodec.unpack(checksRaw) }
    val fromReport = remember(company, booth) { reportChecks(company, booth) }

    // 社名を入れると出展社データから候補を出し、選べば小間番号も入る
    val suggestions: List<Exhibitor> = remember(company, index, suggestionsOpen) {
        if (!suggestionsOpen || index == null || company.length < 1) emptyList()
        else index.search(company, limit = 6).map { it.exhibitor }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "ブースを追加" else "ブースを編集") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = company,
                    onValueChange = {
                        company = it
                        suggestionsOpen = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("会社名（必須）") },
                    singleLine = true,
                )
                if (suggestions.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column {
                            suggestions.forEach { ex ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            company = ex.name
                                            booth = ex.booths.first()
                                            suggestionsOpen = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = ex.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Tag(ex.booths.first(), placeColorFor(BoothCode.locate(ex.booths.first())?.hallId), bold = true)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = booth,
                    onValueChange = { booth = it.take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("小間番号") },
                    placeholder = { Text("例：E1024") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    supportingText = {
                        Text(
                            when {
                                booth.isBlank() -> "わかれば入力（ホールを自動表示）"
                                location != null -> "→ ${location.label}"
                                else -> "ホールを判別できません"
                            },
                        )
                    },
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("メモ") },
                    minLines = 2,
                    maxLines = 4,
                )
                Text("確かめること（チェックリスト）", style = MaterialTheme.typography.titleSmall)
                checks.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = c.done,
                            onCheckedChange = { checksRaw = CheckCodec.pack(BoothChecklist.toggle(checks, i)) },
                        )
                        Text(c.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(
                            onClick = { checksRaw = CheckCodec.pack(checks.filterIndexed { j, _ -> j != i }) },
                            modifier = Modifier.size(48.dp),
                        ) { Icon(Icons.Filled.Close, contentDescription = "「${c.text}」を消す") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCheck,
                        onValueChange = { newCheck = it.take(60) },
                        modifier = Modifier.weight(1f),
                        label = { Text("項目を足す") },
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            checksRaw = CheckCodec.pack(BoothChecklist.merge(checks, listOf(newCheck)))
                            newCheck = ""
                        },
                        enabled = newCheck.isNotBlank(),
                        modifier = Modifier.size(48.dp),
                    ) { Icon(Icons.Filled.Add, contentDescription = "項目を追加") }
                }
                val reportNew = fromReport.filter { r -> checks.none { it.text == r } }
                if (reportNew.isNotEmpty()) {
                    TextButton(onClick = { checksRaw = CheckCodec.pack(BoothChecklist.merge(checks, fromReport)) }) {
                        Text("レポートの確認事項を入れる（${reportNew.size}件）")
                    }
                }
                if (BoothChecklist.STANDARD.any { s -> checks.none { it.text == s } }) {
                    TextButton(onClick = { checksRaw = CheckCodec.pack(BoothChecklist.merge(checks, BoothChecklist.STANDARD)) }) {
                        Text("定番の項目を入れる")
                    }
                }
                if (initial != null) {
                    TextButton(
                        onClick = { onDelete(initial.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("このブースを削除")
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                enabled = company.isNotBlank(),
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        BoothItem(
                            id = initial?.id ?: now,
                            company = company.trim(),
                            booth = booth.trim(),
                            memo = memo.trim(),
                            visited = initial?.visited ?: false,
                            createdAt = initial?.createdAt ?: now,
                            // 入力欄に書きかけの項目があれば、それも入れて保存する
                            checks = BoothChecklist.merge(checks, listOf(newCheck)),
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
