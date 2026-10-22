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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import jp.fairguide.jimtof2026.data.FairData
import jp.fairguide.jimtof2026.data.Hall
import jp.fairguide.jimtof2026.logic.BoothCode
import jp.fairguide.jimtof2026.ui.components.BulletText
import jp.fairguide.jimtof2026.ui.components.GuideCard
import jp.fairguide.jimtof2026.ui.components.SectionCard
import jp.fairguide.jimtof2026.ui.components.Tag

@Composable
fun HallsScreen(data: FairData, onOpenUrl: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val location = remember(query) { BoothCode.locate(query) }
    val matchedHall = remember(location, data) {
        location?.let { loc -> data.halls.firstOrNull { loc.hallId in it.prefixes } }
    }

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
        items(data.halls, key = { it.id }) { hall ->
            HallCard(hall = hall, highlighted = hall == matchedHall, onOpenUrl = onOpenUrl)
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
private fun HallCard(hall: Hall, highlighted: Boolean, onOpenUrl: (String) -> Unit) {
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
        if (hall.mapUrl != null || hall.pdfUrl != null) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hall.mapUrl?.let { url ->
                    OutlinedButton(onClick = { onOpenUrl(url) }) { Text("小間割図を開く") }
                }
                hall.pdfUrl?.let { url ->
                    TextButton(onClick = { onOpenUrl(url) }) { Text("PDF") }
                }
            }
        }
    }
}
