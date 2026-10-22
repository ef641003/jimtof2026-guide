package jp.fairguide.jimtof2026.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.fairguide.jimtof2026.data.FairData
import jp.fairguide.jimtof2026.data.LinkItem
import jp.fairguide.jimtof2026.ui.components.SectionCard

@Composable
fun LinksScreen(data: FairData, onOpenUrl: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("アクセス") {
                data.access.forEachIndexed { index, info ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = info.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        data.linkGroups.forEach { group ->
            item(key = group.title) {
                SectionCard(group.title) {
                    group.items.forEachIndexed { index, link ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        LinkRow(link = link, onClick = { onOpenUrl(link.url) })
                    }
                }
            }
        }
        item {
            Text(
                text = "このアプリは主催者とは関係のない非公式ガイドです。情報は${data.info.verifiedOn}時点の" +
                    "${data.info.sourceNote}。最新情報は公式サイトで確認してください。",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkRow(link: LinkItem, onClick: () -> Unit) {
    val icon: ImageVector = when {
        link.url.startsWith("tel:") -> Icons.Filled.Phone
        link.url.startsWith("mailto:") -> Icons.Filled.Email
        else -> Icons.AutoMirrored.Filled.KeyboardArrowRight
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = link.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (link.desc.isNotBlank()) {
                Text(
                    text = link.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
