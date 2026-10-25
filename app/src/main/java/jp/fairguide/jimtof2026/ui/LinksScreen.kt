package jp.fairguide.jimtof2026.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import jp.fairguide.jimtof2026.data.FairData
import jp.fairguide.jimtof2026.data.LinkGroup
import jp.fairguide.jimtof2026.data.LinkItem
import jp.fairguide.jimtof2026.ui.components.Caption
import jp.fairguide.jimtof2026.ui.components.GuideCard
import jp.fairguide.jimtof2026.ui.components.ListRow
import jp.fairguide.jimtof2026.ui.components.SectionHeader
import jp.fairguide.jimtof2026.ui.theme.GuideTheme

@Composable
fun LinksScreen(data: FairData, onOpenUrl: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "access") {
            SectionHeader("アクセス")
            GuideCard {
                data.access.forEachIndexed { index, info ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = info.title,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = info.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        data.linkGroups.forEach { group ->
            item(key = group.title) {
                SectionHeader(group.title)
                GuideCard(contentPadding = PaddingValues(0.dp)) {
                    group.items.forEachIndexed { index, link ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 64.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        ListRow(
                            title = link.title,
                            description = link.desc,
                            icon = iconFor(group, link),
                            iconTint = tintFor(link),
                            trailing = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            onClick = { onOpenUrl(link.url) },
                        )
                    }
                }
            }
        }
        item(key = "about") {
            Caption(
                "このアプリは主催者とは関係のない非公式ガイドです。情報は${data.info.verifiedOn}時点の" +
                    "${data.info.sourceNote}。最新情報は公式サイトで確認してください。",
            )
        }
    }
}

private fun iconFor(group: LinkGroup, link: LinkItem): ImageVector = when {
    link.url.startsWith("tel:") -> Icons.Filled.Phone
    link.url.startsWith("mailto:") -> Icons.Filled.Email
    group.title.contains("会場") || group.title.contains("アクセス") -> Icons.Filled.Place
    group.title.contains("出展者") -> Icons.Filled.Search
    group.title.contains("講演") -> Icons.Filled.DateRange
    group.title.contains("登録") -> Icons.Filled.Star
    else -> Icons.Filled.Info
}

@Composable
private fun tintFor(link: LinkItem) = when {
    link.url.startsWith("tel:") -> GuideTheme.colors.good.strong
    link.url.startsWith("mailto:") -> GuideTheme.colors.conference.strong
    else -> MaterialTheme.colorScheme.primary
}
