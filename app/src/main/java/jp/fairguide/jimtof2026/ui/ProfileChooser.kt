package jp.fairguide.jimtof2026.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.fairguide.jimtof2026.data.ViewerProfile

/** 担当を選ぶ画面。初回は必ず出し、あとからは会期・入場の画面やレポートタブから開く */
@Composable
fun ProfileChooser(current: ViewerProfile?, onChoose: (ViewerProfile) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "担当を選んでください" else "担当を切り替える") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "レポートの項目・おすすめの会社・検索の候補が変わります。書いたメモは担当ごとに残ります。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ViewerProfile.entries.forEach { p ->
                    val selected = p == current
                    OutlinedCard(
                        onClick = { onChoose(p) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                text = p.label + if (selected) "（いまの担当）" else "",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(p.items, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = p.description,
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (current != null) TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}
