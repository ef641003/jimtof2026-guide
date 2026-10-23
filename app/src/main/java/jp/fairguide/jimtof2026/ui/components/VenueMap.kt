package jp.fairguide.jimtof2026.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp

/** 略図上の1区画。座標は地図全体を 0〜1 に正規化した値 */
data class MapZone(
    val id: String,          // FairData.halls の id（CT は会議棟）。タップ不可の区画は "-" で始める
    val floor: String,       // "1F" / "4F" / "ALL"
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String,
) {
    val tappable: Boolean get() = !id.startsWith("-")
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/**
 * 会場の略図。位置関係・形・縮尺は目安で、実際とは異なる。
 * ホール単位まで（ブース単位の位置は公式の小間割図で確認する）。
 */
object VenueLayout {
    val zones: List<MapZone> = listOf(
        MapZone("CT", "ALL", 0.34f, 0.05f, 0.66f, 0.20f, "会議棟"),
        MapZone("-rinkai", "ALL", 0.01f, 0.03f, 0.32f, 0.20f, "りんかい線\n国際展示場駅"),
        MapZone("-yurikamome", "ALL", 0.68f, 0.03f, 0.99f, 0.20f, "ゆりかもめ\nビッグサイト"),
        // 1F
        MapZone("W1-2", "1F", 0.04f, 0.26f, 0.34f, 0.60f, "西1・2\nアトリウム"),
        MapZone("E1-3", "1F", 0.52f, 0.26f, 0.96f, 0.40f, "東1〜3"),
        MapZone("-e46", "1F", 0.52f, 0.42f, 0.96f, 0.52f, "東4〜6（休館）"),
        MapZone("E7-8", "1F", 0.52f, 0.54f, 0.96f, 0.68f, "東7・8"),
        MapZone("S1-2", "1F", 0.04f, 0.66f, 0.46f, 0.84f, "南1・2"),
        // 4F（西・南の上階）
        MapZone("W3-4", "4F", 0.04f, 0.26f, 0.34f, 0.60f, "西3・4\n（屋上展示場）"),
        MapZone("S3-4", "4F", 0.04f, 0.66f, 0.46f, 0.84f, "南3・4"),
    )

    fun floorOf(hallId: String?): String = when (hallId) {
        "W3-4", "S3-4" -> "4F"
        else -> "1F"
    }
}

@Composable
fun VenueMap(
    floor: String,
    selectedId: String?,
    counts: Map<String, Int>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val active = VenueLayout.zones.filter { it.floor == floor || it.floor == "ALL" }
    val ghosts = VenueLayout.zones.filter { it.floor != floor && it.floor != "ALL" && !(it.id == "-e46") }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(floor) {
                detectTapGestures { pos ->
                    val x = pos.x / size.width
                    val y = pos.y / size.height
                    val hit = active.firstOrNull { it.tappable && it.contains(x, y) }
                        ?: ghosts.firstOrNull { it.tappable && it.contains(x, y) }
                    hit?.let { onSelect(it.id) }
                }
            },
    ) {
        val w = size.width
        val h = size.height

        fun drawZone(z: MapZone, fill: Color, border: Color, textColor: Color, borderWidth: Float, bold: Boolean) {
            val tl = Offset(z.left * w, z.top * h)
            val sz = Size((z.right - z.left) * w, (z.bottom - z.top) * h)
            drawRoundRect(fill, tl, sz, CornerRadius(14f, 14f))
            drawRoundRect(border, tl, sz, CornerRadius(14f, 14f), style = Stroke(borderWidth))
            val layout = measurer.measure(
                text = z.label,
                style = TextStyle(
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                constraints = Constraints(maxWidth = (sz.width - 8f).toInt().coerceAtLeast(1)),
                maxLines = 3,
            )
            drawText(
                layout,
                topLeft = Offset(
                    tl.x + (sz.width - layout.size.width) / 2f,
                    tl.y + (sz.height - layout.size.height) / 2f,
                ),
            )
        }

        // 別の階にあるホールは薄く見せる（タップすると階が切り替わる）
        ghosts.forEach { z ->
            drawZone(z, scheme.surfaceVariant.copy(alpha = 0.35f), scheme.outlineVariant, scheme.onSurfaceVariant.copy(alpha = 0.45f), 2f, false)
        }
        active.forEach { z ->
            when {
                z.id.startsWith("-rin") || z.id.startsWith("-yuri") ->
                    drawZone(z, scheme.tertiaryContainer, scheme.tertiary, scheme.onTertiaryContainer, 2f, false)
                !z.tappable ->
                    drawZone(z, scheme.surfaceVariant.copy(alpha = 0.6f), scheme.outline, scheme.onSurfaceVariant, 2f, false)
                z.id == selectedId ->
                    drawZone(z, scheme.secondaryContainer, scheme.secondary, scheme.onSecondaryContainer, 6f, true)
                else ->
                    drawZone(z, scheme.primaryContainer, scheme.primary, scheme.onPrimaryContainer, 2f, true)
            }
        }
        // マイリストの件数バッジ
        (active + ghosts).forEach { z ->
            val n = counts[z.id] ?: 0
            if (n > 0 && z.tappable) {
                val c = Offset(z.right * w - 6f, z.top * h + 6f)
                val alpha = if (z.floor == floor || z.floor == "ALL") 1f else 0.5f
                drawCircle(scheme.error.copy(alpha = alpha), radius = 22f, center = c)
                val t = measurer.measure(
                    text = n.toString(),
                    style = TextStyle(color = scheme.onError, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
            }
        }
    }
}
