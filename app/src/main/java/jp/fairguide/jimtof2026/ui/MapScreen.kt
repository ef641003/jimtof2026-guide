package jp.fairguide.jimtof2026.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.fairguide.jimtof2026.data.Exhibitor
import jp.fairguide.jimtof2026.data.HallMap
import jp.fairguide.jimtof2026.data.MapBooth
import jp.fairguide.jimtof2026.data.ReportData
import jp.fairguide.jimtof2026.logic.BoothCode
import jp.fairguide.jimtof2026.logic.MapMark
import jp.fairguide.jimtof2026.logic.MapMarks
import jp.fairguide.jimtof2026.logic.MarkEntry
import jp.fairguide.jimtof2026.ui.components.BoothBadge
import jp.fairguide.jimtof2026.ui.components.BulletText
import jp.fairguide.jimtof2026.ui.components.Caption
import jp.fairguide.jimtof2026.ui.components.GuideCard
import jp.fairguide.jimtof2026.ui.components.SectionHeader
import jp.fairguide.jimtof2026.ui.components.Tag
import jp.fairguide.jimtof2026.ui.components.placeColorFor
import jp.fairguide.jimtof2026.ui.theme.GuideTheme
import kotlinx.coroutines.launch

/**
 * 会場マップ。公式の小間割図から写した小間の形を描き、レポートのおすすめとブースリストの会社に印をつける。
 * 折りたたみの画面では図の下に一覧、広げた画面（横幅 600dp 以上）では図の横に一覧を出す。
 */
@Composable
fun MapScreen(vm: AppViewModel, snackbarHostState: SnackbarHostState, onOpenUrl: (String) -> Unit) {
    LaunchedEffect(Unit) {
        vm.ensureBoothMap()
        vm.ensureReport()
        vm.ensureExhibitors()
    }
    val data = vm.boothMap
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val report = vm.reportData
    val notes = vm.reportNotes
    val myList = vm.myList
    val marksByMap = remember(data, report, notes.extras, vm.mapItemFilter, myList) {
        MapMarks.build(data, report, notes, vm.mapItemFilter, myList)
    }
    val map = data.map(vm.mapId ?: "") ?: data.maps.first()
    val marks = marksByMap[map.id].orEmpty()
    val selected = vm.mapBooth?.let { code -> map.booths.firstOrNull { it.code == code } }

    // 図の上の小間 → その小間の出展社（パビリオンは本体に寄せる）
    val index = vm.exhibitorIndex
    val exhibitorsByBooth = remember(index, data) {
        val out = HashMap<String, MutableList<Exhibitor>>()
        index?.data?.exhibitors?.forEach { ex ->
            ex.booths.forEach { b ->
                data.find(b)?.let { (_, booth) -> out.getOrPut(booth.code) { ArrayList() }.let { if (ex !in it) it += ex } }
            }
        }
        out
    }

    val itemColors = itemColorsOf(report)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val mapHeight = maxHeight * 0.5f
        val header: @Composable () -> Unit = {
            MapHeader(
                maps = data.maps,
                current = map,
                counts = data.maps.associate { m -> m.id to (marksByMap[m.id]?.size ?: 0) },
                report = report,
                itemFilter = vm.mapItemFilter,
                itemColors = itemColors,
                onSelectMap = { vm.selectMap(it) },
                onSelectItem = { vm.setMapItemFilter(it) },
            )
        }
        val side: @Composable (Modifier) -> Unit = { modifier ->
            SidePane(
                modifier = modifier,
                map = map,
                marks = marks,
                selected = selected,
                exhibitors = selected?.let { exhibitorsByBooth[it.code] }.orEmpty(),
                report = report,
                itemColors = itemColors,
                isInList = { name, booth -> myList.any { it.company == name && BoothCode.normalize(it.booth) == BoothCode.normalize(booth) } },
                onSelect = { code ->
                    vm.selectMapBooth(code)
                    vm.focusMapBooth()
                },
                onClear = { vm.selectMapBooth(null) },
                onAddToList = { name, booth ->
                    val added = vm.addToMyListByName(name, booth)
                    scope.launch { snackbarHostState.showSnackbar(if (added) "ブースリストに追加しました" else "すでにブースリストにあります") }
                },
                onOpenReport = { itemId ->
                    vm.selectReportItem(itemId)
                    vm.selectTab(AppTab.REPORT)
                },
                onOpenOfficial = { code -> onOpenUrl(map.pageFor(code)) },
            )
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1.35f).fillMaxHeight()) {
                    header()
                    MapCanvas(
                        map = map,
                        marks = marks,
                        selected = selected,
                        itemColors = itemColors,
                        focusTick = vm.mapFocusTick,
                        onTapBooth = { vm.selectMapBooth(it?.code) },
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 6.dp, bottom = 12.dp),
                    )
                }
                side(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                header()
                MapCanvas(
                    map = map,
                    marks = marks,
                    selected = selected,
                    itemColors = itemColors,
                    focusTick = vm.mapFocusTick,
                    onTapBooth = { vm.selectMapBooth(it?.code) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapHeight)
                        .padding(horizontal = 12.dp),
                )
                side(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** 項目ごとの印の色（①②③の順） */
@Composable
private fun itemColorsOf(report: ReportData?): Map<String, Color> {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        GuideTheme.colors.south.strong,
    )
    return report?.items?.mapIndexed { i, it -> it.id to palette[i % palette.size] }?.toMap().orEmpty()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapHeader(
    maps: List<HallMap>,
    current: HallMap,
    counts: Map<String, Int>,
    report: ReportData?,
    itemFilter: String?,
    itemColors: Map<String, Color>,
    onSelectMap: (String) -> Unit,
    onSelectItem: (String?) -> Unit,
) {
    Column(Modifier.padding(top = 4.dp, bottom = 6.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(maps, key = { it.id }) { m ->
                val n = counts[m.id] ?: 0
                FilterChip(
                    selected = m.id == current.id,
                    onClick = { onSelectMap(m.id) },
                    label = { Text(m.label.replace("ホール", "") + "・" + m.floor + if (n > 0) "（$n）" else "") },
                )
            }
        }
        if (report != null) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = itemFilter == null, onClick = { onSelectItem(null) }, label = { Text("全項目") })
                }
                items(report.items, key = { it.id }) { item ->
                    FilterChip(
                        selected = itemFilter == item.id,
                        onClick = { onSelectItem(if (itemFilter == item.id) null else item.id) },
                        label = { Text(item.short) },
                        leadingIcon = {
                            Box(Modifier.size(10.dp).background(itemColors[item.id] ?: Color.Gray, CircleShape))
                        },
                    )
                }
            }
        }
    }
}

/** 図の表示（拡大・移動・小間のタップ） */
@Composable
private fun MapCanvas(
    map: HallMap,
    marks: Map<String, MapMark>,
    selected: MapBooth?,
    itemColors: Map<String, Color>,
    focusTick: Int,
    onTapBooth: (MapBooth?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val guide = GuideTheme.colors
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(map.id) { mutableFloatStateOf(1f) }
    var pan by remember(map.id) { mutableStateOf(Offset.Zero) }
    // ホールの色（描画の中では Composable を呼べないので先に決める）
    val hallColors = map.halls.associate { it.hallId to placeColorFor(it.hallId) }
    val paths = remember(map) {
        map.booths.associate { b ->
            b.code to Path().apply {
                moveTo(b.points[0], b.points[1])
                var i = 2
                while (i < b.points.size) {
                    lineTo(b.points[i], b.points[i + 1])
                    i += 2
                }
                close()
            }
        }
    }
    val mapW = map.right - map.left
    val mapH = map.bottom - map.top
    val cx = (map.left + map.right) / 2f
    val cy = (map.top + map.bottom) / 2f
    fun base(): Float = if (size.width == 0) 1f else minOf(size.width / mapW, size.height / mapH) * 0.94f
    fun toScreen(x: Float, y: Float): Offset {
        val k = base() * zoom
        return Offset((x - cx) * k + size.width / 2f + pan.x, (y - cy) * k + size.height / 2f + pan.y)
    }
    fun toMap(p: Offset): Offset {
        val k = base() * zoom
        return Offset((p.x - size.width / 2f - pan.x) / k + cx, (p.y - size.height / 2f - pan.y) / k + cy)
    }
    fun focusOn(b: MapBooth) {
        zoom = maxOf(zoom, 3f)
        val k = base() * zoom
        pan = Offset(-(b.centerX - cx) * k, -(b.centerY - cy) * k)
    }
    // 「マップで見る」で来たときや、一覧から選んだときは、その小間へ寄せる
    LaunchedEffect(focusTick, map.id, size) {
        if (size.width > 0 && focusTick > 0) selected?.let { focusOn(it) }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size = it }
                .pointerInput(map.id) {
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val nz = (zoom * zoomChange).coerceIn(1f, 14f)
                        val c = centroid - Offset(size.width / 2f, size.height / 2f)
                        pan = c - (c - pan) * (nz / zoom) + panChange
                        zoom = nz
                    }
                }
                .pointerInput(map.id, marks) {
                    detectTapGestures(
                        onTap = { p ->
                            val m = toMap(p)
                            onTapBooth(map.boothAt(m.x, m.y))
                        },
                        onDoubleTap = { p ->
                            val nz = (zoom * 2f).coerceAtMost(14f)
                            val c = p - Offset(size.width / 2f, size.height / 2f)
                            pan = c - (c - pan) * (nz / zoom)
                            zoom = nz
                        },
                    )
                },
        ) {
            val k = base() * zoom
            // ホールの区画（色と名前）
            for (h in map.halls) {
                val pc = hallColors.getValue(h.hallId)
                val tl = toScreen(h.left - 40f, h.top - 120f)
                val br = toScreen(h.right + 40f, h.bottom + 40f)
                drawRoundRect(pc.container.copy(alpha = 0.35f), tl, Size(br.x - tl.x, br.y - tl.y), CornerRadius(10f, 10f))
            }
            // 小間（変換をかけて描く）
            withTransform({
                translate(size.width / 2f + pan.x - cx * k, size.height / 2f + pan.y - cy * k)
                scale(k, k, pivot = Offset.Zero)
            }) {
                val hair = 1.2f / k
                for (b in map.booths) {
                    val path = paths.getValue(b.code)
                    val mark = marks[b.code]
                    val fill = when {
                        b == selected -> scheme.secondary
                        mark == null -> scheme.surface
                        mark.visited -> scheme.surfaceVariant
                        mark.fromReport -> (itemColors[mark.entries.first().itemIds.firstOrNull()] ?: scheme.primary).copy(alpha = 0.30f)
                        else -> guide.warn.container
                    }
                    drawPath(path, fill)
                    drawPath(path, if (mark != null || b == selected) scheme.outline else scheme.outlineVariant, style = Stroke(if (b == selected) 3f * hair else hair))
                }
            }
            // ホール名
            for (h in map.halls) {
                val p = toScreen(h.left, h.top - 110f)
                val t = measurer.measure(h.name, TextStyle(color = hallColors.getValue(h.hallId).strong, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                drawText(t, topLeft = p)
            }
            // 印（項目の①②…と★）と、拡大したときの小間番号
            for (b in map.booths) {
                val mark = marks[b.code]
                val w = (b.right - b.left) * k
                val c = toScreen(b.centerX, b.centerY)
                if (c.x < -40 || c.y < -40 || c.x > size.width + 40 || c.y > size.height + 40) continue
                if (mark != null) {
                    val label = mark.marks.joinToString("") + if (mark.inMyList) "★" else ""
                    val color = itemColors[mark.entries.firstNotNullOfOrNull { it.itemIds.firstOrNull() }] ?: guide.warn.strong
                    val r = 9.dp.toPx()
                    drawCircle(scheme.surface, r + 2f, c)
                    drawCircle(if (mark.visited) scheme.outline else color, r, c)
                    if (label.isNotEmpty()) {
                        val t = measurer.measure(label, TextStyle(color = scheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        drawRoundRect(scheme.surface.copy(alpha = 0.9f), Offset(c.x + r, c.y - t.size.height / 2f), Size(t.size.width + 6f, t.size.height.toFloat()), CornerRadius(6f, 6f))
                        drawText(t, topLeft = Offset(c.x + r + 3f, c.y - t.size.height / 2f))
                    }
                } else if (w > 46.dp.toPx() / 1.6f && zoom >= 2.2f) {
                    val t = measurer.measure(b.code, TextStyle(color = scheme.onSurfaceVariant, fontSize = 9.sp))
                    if (t.size.width < w) drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
                }
            }
            selected?.let { b ->
                val c = toScreen(b.centerX, b.centerY)
                val t = measurer.measure(b.code, TextStyle(color = scheme.onSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                drawRoundRect(scheme.secondary, Offset(c.x - t.size.width / 2f - 6f, c.y - t.size.height - 14f), Size(t.size.width + 12f, t.size.height + 4f), CornerRadius(8f, 8f))
                drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height - 12f))
            }
        }
        // 拡大・縮小・全体表示（指2本でも操作できる）
        Column(
            Modifier.align(Alignment.BottomEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SmallFloatingActionButton(onClick = {
                val nz = (zoom * 1.6f).coerceAtMost(14f)
                pan = pan * (nz / zoom)
                zoom = nz
            }) { Icon(Icons.Filled.Add, contentDescription = "拡大") }
            SmallFloatingActionButton(onClick = {
                val nz = (zoom / 1.6f).coerceAtLeast(1f)
                pan = if (nz == 1f) Offset.Zero else pan * (nz / zoom)
                zoom = nz
            }) { Text("－", fontWeight = FontWeight.Bold) }
            SmallFloatingActionButton(onClick = {
                zoom = 1f
                pan = Offset.Zero
            }) { Text("全体", style = MaterialTheme.typography.labelSmall) }
        }
        Text(
            text = map.label + "・" + map.floor,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(scheme.surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SidePane(
    modifier: Modifier,
    map: HallMap,
    marks: Map<String, MapMark>,
    selected: MapBooth?,
    exhibitors: List<Exhibitor>,
    report: ReportData?,
    itemColors: Map<String, Color>,
    isInList: (String, String) -> Boolean,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    onAddToList: (String, String) -> Unit,
    onOpenReport: (String) -> Unit,
    onOpenOfficial: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    // 印のある小間を、小間番号の順に
    val rows = remember(marks, map) {
        marks.values.sortedBy { BoothCode.normalize(it.boothCode) }
    }
    LaunchedEffect(selected?.code) { if (selected != null) listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected != null) {
            item(key = "selected") {
                SelectedBoothCard(
                    booth = selected,
                    mark = marks[selected.code],
                    exhibitors = exhibitors,
                    report = report,
                    itemColors = itemColors,
                    isInList = isInList,
                    onClear = onClear,
                    onAddToList = onAddToList,
                    onOpenReport = onOpenReport,
                    onOpenOfficial = { onOpenOfficial(selected.code) },
                )
            }
        }
        item(key = "head") {
            SectionHeader(
                if (rows.isEmpty()) "この図に印の会社はありません" else "この図のおすすめ・ブースリスト（${rows.size}）",
            )
        }
        items(rows, key = { "row-" + it.boothCode }) { m ->
            MarkRow(m, itemColors, isSelected = m.boothCode == selected?.code, onClick = { onSelect(m.boothCode) })
        }
        item(key = "foot") {
            Caption(
                "小間の位置と形は、公式の小間割図（出展者ガイド）から写したものです。小間をタップすると、その小間の出展社が出ます。" +
                    "指2本で拡大・移動、ダブルタップで拡大できます。①②…はレポートの項目、★はブースリストに入れた会社です。",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkRow(m: MapMark, itemColors: Map<String, Color>, isSelected: Boolean, onClick: () -> Unit) {
    GuideCard(
        onClick = onClick,
        accent = if (isSelected) MaterialTheme.colorScheme.secondary else null,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoothBadge(m.boothCode)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                m.entries.forEach { e ->
                    Text(
                        text = e.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (e.visited) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            m.entries.flatMap { it.itemIds }.distinct().forEach { id ->
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(10.dp)
                        .background(itemColors[id] ?: Color.Gray, CircleShape),
                )
            }
            Text(
                text = m.marks.joinToString("") + if (m.inMyList) "★" else "",
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedBoothCard(
    booth: MapBooth,
    mark: MapMark?,
    exhibitors: List<Exhibitor>,
    report: ReportData?,
    itemColors: Map<String, Color>,
    isInList: (String, String) -> Boolean,
    onClear: () -> Unit,
    onAddToList: (String, String) -> Unit,
    onOpenReport: (String) -> Unit,
    onOpenOfficial: () -> Unit,
) {
    val loc = BoothCode.locate(booth.code)
    GuideCard(accent = MaterialTheme.colorScheme.secondary) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoothBadge(booth.code, large = true)
            Spacer(Modifier.width(10.dp))
            Text(
                text = loc?.let { "${it.hallName}・${it.floor}" } ?: "",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = placeColorFor(loc?.hallId).strong,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "選択を外す") }
        }
        // 印の会社（レポートのおすすめ）を先に、ほかの出展社をあとに
        val markNames = mark?.entries?.map { it.name }.orEmpty()
        mark?.entries?.forEach { e -> MarkEntryBlock(e, report, itemColors, isInList, onAddToList, onOpenReport) }
        val others = exhibitors.filter { it.name !in markNames }
        if (others.isNotEmpty()) {
            Text(
                text = if (mark == null) "この小間の出展社" else "同じ小間のほかの出展社",
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            others.take(12).forEach { ex ->
                val inList = isInList(ex.name, ex.booths.first())
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ex.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onAddToList(ex.name, ex.booths.first()) }, enabled = !inList) {
                        Text(if (inList) "リスト済み" else "リストへ")
                    }
                }
            }
            if (others.size > 12) Text("ほか ${others.size - 12}社", style = MaterialTheme.typography.bodySmall)
        }
        if (mark == null && exhibitors.isEmpty()) {
            Text(
                "出展社データを読み込み中か、この小間の出展社が見つかりません",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onOpenOfficial) { Text("公式の小間割図で見る（通信あり）") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkEntryBlock(
    e: MarkEntry,
    report: ReportData?,
    itemColors: Map<String, Color>,
    isInList: (String, String) -> Boolean,
    onAddToList: (String, String) -> Unit,
    onOpenReport: (String) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            e.itemIds.forEachIndexed { i, id ->
                val c = itemColors[id] ?: MaterialTheme.colorScheme.primary
                Tag(
                    text = (report?.item(id)?.short ?: e.marks.getOrNull(i) ?: id),
                    container = c.copy(alpha = 0.18f),
                    content = MaterialTheme.colorScheme.onSurface,
                    bold = true,
                )
            }
            if (e.inMyList) Tag(if (e.visited) "訪問済み" else "ブースリスト", GuideTheme.colors.warn, bold = true)
        }
        Text(e.name, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleSmall)
        val company = e.companies.values.firstOrNull()
        company?.let { c ->
            Text(c.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            if (c.look.isNotEmpty()) {
                Text(
                    "ブースで確かめること",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                c.look.take(4).forEach { BulletText(it) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val inList = isInList(e.name, e.booth)
            FilledTonalButton(onClick = { onAddToList(e.name, e.booth) }, enabled = !inList) {
                Icon(if (inList) Icons.Filled.Check else Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (inList) "リスト済み" else "ブースリストへ")
            }
            e.itemIds.firstOrNull()?.let { id ->
                TextButton(onClick = { onOpenReport(id) }) { Text("レポートで見る") }
            }
        }
    }
}
