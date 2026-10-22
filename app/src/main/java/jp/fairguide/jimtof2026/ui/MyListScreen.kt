package jp.fairguide.jimtof2026.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import jp.fairguide.jimtof2026.data.BoothItem
import jp.fairguide.jimtof2026.logic.BoothCode
import jp.fairguide.jimtof2026.ui.components.GuideCard
import jp.fairguide.jimtof2026.ui.components.Tag
import jp.fairguide.jimtof2026.ui.components.cardContainerColor

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
        }
        return sb.toString().trimEnd()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BoothItem?>(null) }
    var sortByHall by rememberSaveable { mutableStateOf(true) }

    val items = vm.myList
    val sorted = remember(items, sortByHall) {
        if (sortByHall) items.sortedWith(MyListOrder.forScreen) else items.sortedBy { it.createdAt }
    }
    val visitedCount = items.count { it.visited }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                GuideCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "訪問済み $visitedCount / ${items.size}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, MyListOrder.shareText(items))
                                }
                                context.startActivity(Intent.createChooser(send, "マイリストを共有"))
                            },
                            enabled = items.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "共有")
                        }
                    }
                    if (items.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { visitedCount.toFloat() / items.size },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sortByHall,
                            onClick = { sortByHall = true },
                            label = { Text("ホール順") },
                        )
                        FilterChip(
                            selected = !sortByHall,
                            onClick = { sortByHall = false },
                            label = { Text("登録順") },
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    GuideCard {
                        Text(
                            text = "回りたいブースを登録しましょう",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "右下の「追加」から会社名と小間番号（例：E1024）を入れると、ホールが自動で表示され、東→西→南の順に並びます。" +
                                "見終わったらチェックを入れてください。共有ボタンでテキストとして送れます。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(sorted, key = { it.id }) { item ->
                BoothRow(
                    item = item,
                    onToggle = { vm.toggleVisited(item.id) },
                    onClick = {
                        editing = item
                        showEditor = true
                    },
                )
            }
            item {
                Text(
                    text = "マイリストはこの端末の中だけに保存され、外部には送信されません。",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ExtendedFloatingActionButton(
            text = { Text("追加") },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            onClick = {
                editing = null
                showEditor = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (showEditor) {
        BoothEditorDialog(
            initial = editing,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoothRow(item: BoothItem, onToggle: () -> Unit, onClick: () -> Unit) {
    val location = remember(item.booth) { BoothCode.locate(item.booth) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor()),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.visited, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.company,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.visited) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.visited) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val code = BoothCode.normalize(item.booth)
                    if (code.isNotEmpty()) {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    if (location != null) {
                        Tag(location.label, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    } else if (code.isNotEmpty()) {
                        Text(
                            text = "ホール不明",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (item.memo.isNotBlank()) {
                    Text(
                        text = item.memo,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoothEditorDialog(
    initial: BoothItem?,
    onDismiss: () -> Unit,
    onSave: (BoothItem) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var company by rememberSaveable { mutableStateOf(initial?.company ?: "") }
    var booth by rememberSaveable { mutableStateOf(initial?.booth ?: "") }
    var memo by rememberSaveable { mutableStateOf(initial?.memo ?: "") }
    val location = remember(booth) { BoothCode.locate(booth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "ブースを追加" else "ブースを編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("会社名（必須）") },
                    singleLine = true,
                )
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
            TextButton(
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
