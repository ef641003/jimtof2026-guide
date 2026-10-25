package jp.fairguide.jimtof2026.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.fairguide.jimtof2026.logic.BoothCode
import jp.fairguide.jimtof2026.ui.theme.GuideTheme
import jp.fairguide.jimtof2026.ui.theme.PlaceColor

/* 余白の基準：画面の左右 16dp、カード内 16dp、カード同士 12dp、要素同士 8dp／4dp */

/** 白いカード。情報の「まとまり」を1枚にする */
@Composable
fun GuideCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = GuideTheme.colors.card)
    val border = BorderStroke(1.dp, GuideTheme.colors.cardBorder)
    val body: @Composable () -> Unit = {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (accent != null) {
                Box(
                    Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(accent),
                )
            }
            Column(Modifier.weight(1f).padding(contentPadding), content = content)
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, border = border) { body() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, border = border) { body() }
    }
}

/** 画面内の見出し。カードの外に置き、左の短い棒で区切りを示す */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(18.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        trailing?.invoke(this)
    }
}

/** 見出しつきカード（見出しは外に出す） */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        SectionHeader(title, trailing = trailing)
        GuideCard(content = content)
    }
}

@Composable
fun BulletText(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 3.dp)) {
        Text(
            text = "•",
            modifier = Modifier.width(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LabeledRow(label: String, value: String, labelWidth: Int = 80) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(labelWidth.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 小さなラベル（分類など） */
@Composable
fun Tag(text: String, container: Color, content: Color, modifier: Modifier = Modifier, bold: Boolean = false) {
    Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun Tag(text: String, place: PlaceColor, modifier: Modifier = Modifier, bold: Boolean = false) =
    Tag(text, place.container, place.onContainer, modifier, bold)

/** 展示棟ごとの色。E＝東、W／WA＝西、S＝南、それ以外＝会議棟・不明 */
@Composable
fun placeColorFor(hallId: String?): PlaceColor {
    val c = GuideTheme.colors
    return when (hallId?.firstOrNull()) {
        'E' -> c.east
        'W' -> c.west
        'S' -> c.south
        'C' -> c.conference
        else -> c.neutral
    }
}

/** 会場名（「会議棟7F 国際会議場」「南4ホール INSIGHTSステージ」など）から棟の色を決める */
@Composable
fun placeColorForPlaceName(place: String): PlaceColor {
    val c = GuideTheme.colors
    return when {
        place.startsWith("会議棟") -> c.conference
        place.startsWith("東") -> c.east
        place.startsWith("西") -> c.west
        place.startsWith("南") -> c.south
        else -> c.neutral
    }
}

/** 小間番号のバッジ。棟の色＋太字の等幅で、遠目にも読めるようにする */
@Composable
fun BoothBadge(code: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val normalized = BoothCode.normalize(code)
    val location = BoothCode.locate(code)
    val color = placeColorFor(location?.hallId)
    Surface(
        modifier = modifier,
        color = color.container,
        contentColor = color.onContainer,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.strong.copy(alpha = 0.5f)),
    ) {
        Text(
            text = normalized.ifEmpty { code },
            modifier = Modifier.padding(horizontal = if (large) 10.dp else 7.dp, vertical = if (large) 4.dp else 2.dp),
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

/** 一覧の1行（リンクなど）。高さは最低 56dp で、指で押しやすくする */
@Composable
fun ListRow(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailing: ImageVector? = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = trailing,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 補足説明（小さく、色を落として） */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 何もないときの案内 */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
