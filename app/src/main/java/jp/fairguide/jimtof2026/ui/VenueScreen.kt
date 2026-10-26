package jp.fairguide.jimtof2026.ui

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.fairguide.jimtof2026.data.Exhibitor
import jp.fairguide.jimtof2026.data.ExhibitorData
import jp.fairguide.jimtof2026.data.Hall
import jp.fairguide.jimtof2026.data.NoveltyInfo
import jp.fairguide.jimtof2026.data.NoveltySource
import jp.fairguide.jimtof2026.logic.BoothCode
import jp.fairguide.jimtof2026.logic.ExhibitorHit
import jp.fairguide.jimtof2026.ui.components.BoothBadge
import jp.fairguide.jimtof2026.ui.components.BulletText
import jp.fairguide.jimtof2026.ui.components.Caption
import jp.fairguide.jimtof2026.ui.components.EmptyState
import jp.fairguide.jimtof2026.ui.components.GuideCard
import jp.fairguide.jimtof2026.ui.components.SectionHeader
import jp.fairguide.jimtof2026.ui.components.Tag
import jp.fairguide.jimtof2026.ui.components.VenueLayout
import jp.fairguide.jimtof2026.ui.components.VenueMap
import jp.fairguide.jimtof2026.ui.components.placeColorFor
import jp.fairguide.jimtof2026.ui.theme.GuideTheme
import kotlinx.coroutines.launch

/** 検索の入口になる言葉。押すとその語で検索する */
private val QUICK_KEYWORDS = listOf("旋盤", "マシニングセンタ", "研削盤", "放電加工", "切削工具", "チャック", "測定", "NC装置", "3Dプリンタ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VenueScreen(vm: AppViewModel, snackbarHostState: SnackbarHostState, onOpenUrl: (String) -> Unit) {
    val data = vm.data
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var floor by rememberSaveable { mutableStateOf("1F") }

    // 検索結果の「地図で見る」から来たとき
    LaunchedEffect(vm.focusHallId) {
        vm.focusHallId?.let {
            selectedId = it
            floor = VenueLayout.floorOf(it)
            vm.focusHall(null)
            listState.scrollToItem(0)
        }
    }

    val counts = remember(vm.myList, data) {
        vm.myList.mapNotNull { item ->
            val loc = BoothCode.locate(item.booth) ?: return@mapNotNull null
            data.halls.firstOrNull { loc.hallId in it.prefixes }?.id
        }.groupingBy { it }.eachCount()
    }
    val selectedHall = data.halls.firstOrNull { it.id == selectedId }
    val searching = vm.searchQuery.isNotBlank() || vm.noveltyOnly
    val index = vm.exhibitorIndex
    var noteTarget by remember { mutableStateOf<Exhibitor?>(null) }
    val noveltyCount = remember(index, vm.noveltyData, vm.userNovelty) {
        index?.data?.exhibitors?.count { vm.hasNovelty(it) }
    }
    val noveltyUi = NoveltyUi(
        infosOf = vm::noveltyOf,
        noteOf = vm::noveltyNoteOf,
        sources = vm.noveltyData?.sources.orEmpty(),
        onEdit = { noteTarget = it },
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "search") {
            SearchField(
                query = vm.searchQuery,
                onChange = vm::updateSearch,
                onClear = vm::clearSearch,
                focusManager = focusManager,
            )
        }

        item(key = "filters") {
            Column {
                FilterChip(
                    selected = vm.noveltyOnly,
                    onClick = { vm.setNoveltyOnly(!vm.noveltyOnly) },
                    label = {
                        Text(if (noveltyCount != null && noveltyCount > 0) "ノベルティあり（${noveltyCount}社）" else "ノベルティあり")
                    },
                    leadingIcon = if (vm.noveltyOnly) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
                if (vm.noveltyOnly) {
                    Caption(
                        "公開情報で確認できた会社と、自分で記録した会社だけを出しています。" +
                            "ここに出ない会社にノベルティが無いとは限りません。",
                    )
                }
            }
        }

        if (searching) {
            searchResults(
                hits = vm.searchHits,
                loaded = index != null,
                query = vm.searchQuery,
                noveltyOnly = vm.noveltyOnly,
                novelty = noveltyUi,
                data = index?.data,
                isInList = { ex, booth -> vm.isInMyList(ex, booth) },
                onAdd = { ex, booth ->
                    val added = vm.addExhibitorToMyList(ex, booth)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (added) "「${ex.name}」をブースリストに追加しました" else "すでにブースリストにあります",
                        )
                    }
                },
                onShowOnMap = { hallId ->
                    focusManager.clearFocus()
                    vm.resetSearch()
                    vm.focusHall(hallId)
                },
                onOpenUrl = onOpenUrl,
            )
        } else {
            item(key = "quick") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    QUICK_KEYWORDS.forEach { word ->
                        AssistChip(onClick = { vm.updateSearch(word) }, label = { Text(word) })
                    }
                }
            }
            item(key = "map-header") {
                SectionHeader("会場マップ（略図）") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1F", "4F").forEach { f ->
                            FilterChip(selected = floor == f, onClick = { floor = f }, label = { Text(f) })
                        }
                    }
                }
            }
            item(key = "map") {
                GuideCard(contentPadding = PaddingValues(12.dp)) {
                    VenueMap(
                        floor = floor,
                        selectedId = selectedId,
                        counts = counts,
                        onSelect = { id ->
                            selectedId = id
                            floor = if (id == "CT") floor else VenueLayout.floorOf(id)
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    MapLegend()
                    Text(
                        text = "ホールを押すと詳細が出ます。数字はブースリストに入れた件数。位置と形は目安です。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (selectedHall != null) {
                item(key = "selected") {
                    HallCard(hall = selectedHall, selected = true, count = counts[selectedHall.id] ?: 0, onOpenUrl = onOpenUrl)
                }
            }
            item(key = "halls-header") { SectionHeader("ホールと出展分野") }
            items(data.halls.filter { it.id != selectedId }, key = { it.id }) { hall ->
                HallCard(
                    hall = hall,
                    selected = false,
                    count = counts[hall.id] ?: 0,
                    onOpenUrl = onOpenUrl,
                    onClick = {
                        selectedId = hall.id
                        floor = if (hall.id == "CT") floor else VenueLayout.floorOf(hall.id)
                        scope.launch { listState.animateScrollToItem(1) }
                    },
                )
            }
            item(key = "notes") {
                SectionHeader("ホールの見かた")
                GuideCard { data.hallNotes.forEach { BulletText(it) } }
            }
            item(key = "source") {
                Caption(
                    "出展社の社名・小間番号・出展製品分類は、公式サイトのオンラインカタログを${index?.data?.retrievedOn ?: "会期直前"}に書き写したものです。" +
                        "会期中の変更は反映されていないことがあります。",
                )
            }
        }
    }

    noteTarget?.let { target ->
        NoveltyNoteDialog(
            exhibitor = target,
            initial = vm.noveltyNoteOf(target),
            onSave = {
                vm.setNoveltyNote(target, it)
                noteTarget = null
            },
            onRemove = {
                vm.setNoveltyNote(target, null)
                noteTarget = null
            },
            onDismiss = { noteTarget = null },
        )
    }
}

/** 検索結果のカードに出すノベルティの情報と操作 */
private class NoveltyUi(
    val infosOf: (Exhibitor) -> List<NoveltyInfo>,
    val noteOf: (Exhibitor) -> String?,
    val sources: Map<String, NoveltySource>,
    val onEdit: (Exhibitor) -> Unit,
)

@Composable
private fun NoveltyNoteDialog(
    exhibitor: Exhibitor,
    initial: String?,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var memo by rememberSaveable(exhibitor.name) { mutableStateOf(initial ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ノベルティを記録") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exhibitor.name, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("何がもらえた？（空でもOK）") },
                    minLines = 2,
                )
                Text(
                    "この端末の中だけに保存します。「ノベルティあり」の絞り込みに出るようになります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(memo) }) { Text("保存") } },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = onRemove) { Text("記録を外す") }
                TextButton(onClick = onDismiss) { Text("閉じる") }
            }
        },
    )
}

@Composable
private fun NoveltyBlock(
    infos: List<NoveltyInfo>,
    note: String?,
    sources: Map<String, NoveltySource>,
    onOpenUrl: (String) -> Unit,
) {
    val c = GuideTheme.colors.warn
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = c.container,
        contentColor = c.onContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("ノベルティ", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            infos.forEach { info ->
                Spacer(Modifier.height(6.dp))
                Text(info.item, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("条件：${info.condition}", style = MaterialTheme.typography.bodySmall)
                Text("数量：${info.quantity}", style = MaterialTheme.typography.bodySmall)
            }
            if (infos.isNotEmpty()) {
                Text(
                    text = "先着・なくなり次第終了のものが多く、すでに配り終えていることもあります。",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                infos.map { it.sourceId }.distinct().mapNotNull { sources[it] }.forEach { src ->
                    Text("出典：${src.label}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onOpenUrl(src.url) }, contentPadding = PaddingValues(0.dp)) {
                        Text("出典ページを開く（外部サイト）")
                    }
                }
            }
            note?.let {
                Text(
                    text = if (it.isBlank()) "自分の記録：ノベルティあり" else "自分のメモ：$it",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onClear: () -> Unit, focusManager: FocusManager) {
    OutlinedTextField(
        value = query,
        onValueChange = { onChange(it.take(40)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text("社名・製品・小間番号", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Filled.Clear, contentDescription = "クリア") }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GuideTheme.colors.card,
            unfocusedContainerColor = GuideTheme.colors.card,
        ),
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun MapLegend() {
    val c = GuideTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Tag("東 E", c.east, bold = true)
        Tag("西 W", c.west, bold = true)
        Tag("南 S", c.south, bold = true)
        Tag("会議棟", c.conference, bold = true)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.searchResults(
    hits: List<ExhibitorHit>,
    loaded: Boolean,
    query: String,
    noveltyOnly: Boolean,
    novelty: NoveltyUi,
    data: ExhibitorData?,
    isInList: (Exhibitor, String) -> Boolean,
    onAdd: (Exhibitor, String) -> Unit,
    onShowOnMap: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    item(key = "count") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!loaded) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("出展社データを読み込んでいます…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    text = if (hits.isEmpty()) "該当なし" else "${hits.size}件",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        query.isBlank() -> "ノベルティあり"
                        noveltyOnly -> "「$query」・ノベルティあり"
                        else -> "「$query」"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (loaded && hits.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                title = "見つかりませんでした",
                body = if (noveltyOnly) {
                    "ノベルティありの会社には当てはまりませんでした。「ノベルティあり」を外すと、ほかの会社も探せます。" +
                        "会場でノベルティを見つけたら、会社のカードの「ノベルティを記録」で残せます。"
                } else {
                    "社名の一部（例：森精機、mazak）、製品名（例：立旋盤、エンドミル）、小間番号（例：E2019）で探せます。" +
                        "共同出展・内部出展の会社は親会社の小間に出ています。"
                },
            )
        }
    }
    items(hits, key = { it.exhibitor.name + it.exhibitor.booths.joinToString() + it.exhibitor.mark }) { hit ->
        ExhibitorCard(
            hit = hit,
            data = data,
            novelty = novelty,
            isInList = isInList,
            onAdd = onAdd,
            onShowOnMap = onShowOnMap,
            onOpenUrl = onOpenUrl,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExhibitorCard(
    hit: ExhibitorHit,
    data: ExhibitorData?,
    novelty: NoveltyUi,
    isInList: (Exhibitor, String) -> Boolean,
    onAdd: (Exhibitor, String) -> Unit,
    onShowOnMap: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val ex = hit.exhibitor
    val noveltyInfos = novelty.infosOf(ex)
    val noveltyNote = novelty.noteOf(ex)
    val primaryBooth = ex.booths.first()
    val location = remember(primaryBooth) { BoothCode.locate(primaryBooth) }
    val color = placeColorFor(location?.hallId)
    val categories = remember(ex, data) { categorySummary(ex, data) }

    GuideCard(accent = color.strong) {
        // 1行目：小間番号（大きく）とホール名。会場で探すときに最初に見る情報
        Row(verticalAlignment = Alignment.CenterVertically) {
            ex.booths.forEachIndexed { i, booth ->
                if (i > 0) Spacer(Modifier.width(6.dp))
                BoothBadge(booth, large = true)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = location?.let { "${it.hallName}・${it.floor}" } ?: "ホール不明",
                    style = MaterialTheme.typography.labelLarge,
                    color = color.strong,
                    fontWeight = FontWeight.Bold,
                )
                ex.markLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = ex.name, style = MaterialTheme.typography.titleMedium)
        ex.nameEn?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (categories.isNotBlank()) {
            Text(
                text = categories,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        hit.matchedCategory?.let { matched ->
            Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag("分類で一致：$matched", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
            }
        }
        if (noveltyInfos.isNotEmpty() || noveltyNote != null) {
            Spacer(Modifier.height(8.dp))
            NoveltyBlock(noveltyInfos, noveltyNote, novelty.sources, onOpenUrl)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            val inList = isInList(ex, primaryBooth)
            FilledTonalButton(onClick = { onAdd(ex, primaryBooth) }, enabled = !inList) {
                Icon(if (inList) Icons.Filled.Check else Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (inList) "追加済み" else "リストに追加")
            }
            TextButton(onClick = { novelty.onEdit(ex) }) {
                Text(if (noveltyNote != null) "ノベルティのメモを編集" else "ノベルティを記録")
            }
            location?.let { loc ->
                val hallId = hallIdFor(loc.hallId)
                TextButton(onClick = { onShowOnMap(hallId) }) { Text("地図で見る") }
                TextButton(onClick = { onOpenUrl(officialBoothMapUrl(loc.hallId, BoothCode.normalize(primaryBooth))) }) {
                    Text("公式小間割図")
                }
            }
        }
    }
}

/** 分類の要約（小分類を最大4つ、残りは「ほか」） */
private fun categorySummary(ex: Exhibitor, data: ExhibitorData?): String {
    if (data == null || ex.codes.isEmpty()) return ""
    val names = ex.codes.mapNotNull { data.categories.name(it) }.distinct()
    val shown = names.take(4)
    val rest = names.size - shown.size
    return shown.joinToString("・") + if (rest > 0) " ほか$rest" else ""
}

/** BoothCode の hallId（E1、WA など）→ 略図・ホール一覧の id（E1-3 など） */
private fun hallIdFor(code: String): String = when (code) {
    "E1", "E2", "E3" -> "E1-3"
    "E7", "E8" -> "E7-8"
    "W1", "W2", "WA" -> "W1-2"
    "W3", "W4" -> "W3-4"
    "S1", "S2" -> "S1-2"
    else -> "S3-4"
}

/** 公式の小間割図。?no= で該当の小間が強調表示される */
private fun officialBoothMapUrl(hallCode: String, booth: String): String {
    val page = when (hallIdFor(hallCode)) {
        "E1-3" -> "e1-3"
        "E7-8" -> "e7-8"
        "W1-2" -> "w1-2"
        "W3-4" -> "w3-4"
        "S1-2" -> "s1-2"
        else -> "s3-4"
    }
    return "https://www.jimtof.org/map/jp/${page}_booth/$page.html?no=$booth"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HallCard(
    hall: Hall,
    selected: Boolean,
    count: Int,
    onOpenUrl: (String) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val color = placeColorFor(if (hall.id == "CT") "C" else hall.id)
    GuideCard(accent = color.strong, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hall.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Tag(hall.location, color, bold = true)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = hall.categoryLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            hall.categories.forEach { category ->
                Tag(category, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
            }
        }
        if (selected && hall.highlights.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            hall.highlights.forEach { BulletText(it) }
        }
        if (count > 0) {
            Text(
                text = "ブースリスト：${count}件",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (selected && hall.mapUrl != null) {
            TextButton(onClick = { onOpenUrl(hall.mapUrl) }, contentPadding = PaddingValues(0.dp)) {
                Text("公式の小間割図を開く（外部サイト）")
            }
        }
    }
}
