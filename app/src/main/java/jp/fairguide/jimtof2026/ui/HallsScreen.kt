package jp.fairguide.jimtof2026.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import jp.fairguide.jimtof2026.data.BoothItem
import jp.fairguide.jimtof2026.data.FairData
import jp.fairguide.jimtof2026.data.Hall
import jp.fairguide.jimtof2026.logic.BoothCode
import jp.fairguide.jimtof2026.ui.components.BulletText
import jp.fairguide.jimtof2026.ui.components.GuideCard
import jp.fairguide.jimtof2026.ui.components.SectionCard
import jp.fairguide.jimtof2026.ui.components.Tag
import jp.fairguide.jimtof2026.ui.components.VenueLayout
import jp.fairguide.jimtof2026.ui.components.VenueMap

@Composable
fun HallsScreen(data: FairData, myList: List<BoothItem>, onOpenUrl: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var floor by rememberSaveable { mutableStateOf("1F") }
    val counts = remember(myList, data) {
        myList.mapNotNull { item ->
            val loc = BoothCode.locate(item.booth) ?: return@mapNotNull null
            data.halls.firstOrNull { loc.hallId in it.prefixes }?.id
        }.groupingBy { it }.eachCount()
    }
    val location = remember(query) { BoothCode.locate(query) }
    val matchedHall = remember(location, data) {
        location?.let { loc -> data.halls.firstOrNull { loc.hallId in it.prefixes } }
    }
    LaunchedEffect(matchedHall) {
        matchedHall?.let {
            selectedId = it.id
            floor = VenueLayout.floorOf(it.id)
        }
    }
    val selectedHall = data.halls.firstOrNull { it.id == selectedId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("小間番号からホールを調べる") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("例：E1024、WA015") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "クリア")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                )
                if (query.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    if (location == null) {
                        Text(
                            text = "該当するホールが見つかりません。英字＋数字で入力してください（例：E1024、W3104、S4029、WA015）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            text = location.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        matchedHall?.let { hall ->
                            Text(
                                text = "${hall.categoryLabel}：${hall.categories.joinToString("、")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item {
            SectionCard("会場マップ（略図）") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1F", "4F").forEach { f ->
                        FilterChip(selected = floor == f, onClick = { floor = f }, label = { Text(f) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                VenueMap(
                    floor = floor,
                    selectedId = selectedId,
                    counts = counts,
                    onSelect = { id ->
                        selectedId = id
                        floor = if (id == "CT") floor else VenueLayout.floorOf(id)
                    },
                )
                Text(
                    text = "ホールをタップすると詳細が出ます。赤い数字はマイリストに入れたブースの数です。位置関係・形は目安で、実際とは異なります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        selectedHall?.let { hall ->
            item(key = "selected") {
                HallCard(hall = hall, highlighted = true, onOpenUrl = onOpenUrl, count = counts[hall.id] ?: 0)
            }
        }
        items(data.halls.filter { it.id != selectedId }, key = { it.id }) { hall ->
            HallCard(hall = hall, highlighted = false, onOpenUrl = onOpenUrl, count = counts[hall.id] ?: 0)
        }
        item {
            SectionCard("ホールの見かた") {
                data.hallNotes.forEach { BulletText(it) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HallCard(hall: Hall, highlighted: Boolean, onOpenUrl: (String) -> Unit, count: Int) {
    val border = if (highlighted) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    GuideCard(border = border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hall.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Tag(hall.location, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
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
                Tag(category, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (hall.highlights.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            hall.highlights.forEach { BulletText(it) }
        }
        if (count > 0) {
            Text(
                text = "マイリストのブース：${count}件",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (highlighted && hall.mapUrl != null) {
            TextButton(onClick = { onOpenUrl(hall.mapUrl) }) { Text("ブース単位の詳細は公式の小間割図（外部サイト）") }
        }
    }
}
