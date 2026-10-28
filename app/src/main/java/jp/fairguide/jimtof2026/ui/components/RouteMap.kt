package jp.fairguide.jimtof2026.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.fairguide.jimtof2026.ui.theme.GuideTheme
import jp.fairguide.jimtof2026.ui.theme.PlaceColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * ルート用の略図。ホールを1つずつ描き、回る順に矢印でつなぐ。
 * 位置・形・縮尺は目安で、実際とは異なる（4F のホールは、その棟の 1F の下に並べて描く）。
 */
object RouteLayout {
    data class Zone(
        val id: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val label: String,
        val floor: String,
    ) {
        val tappable: Boolean get() = !id.startsWith("-") && id != "CT"
        val cx: Float get() = (left + right) / 2f
        val cy: Float get() = (top + bottom) / 2f
        fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    }

    val zones: List<Zone> = listOf(
        Zone("-rinkai", 0.02f, 0.03f, 0.30f, 0.15f, "りんかい線\n国際展示場駅", "ALL"),
        Zone("CT", 0.33f, 0.03f, 0.67f, 0.15f, "会議棟\n（正面入口）", "ALL"),
        Zone("-yurikamome", 0.70f, 0.03f, 0.98f, 0.15f, "ゆりかもめ\nビッグサイト", "ALL"),
        // 西展示棟
        Zone("W1", 0.03f, 0.21f, 0.16f, 0.36f, "西1", "1F"),
        Zone("W2", 0.17f, 0.21f, 0.30f, 0.36f, "西2", "1F"),
        Zone("WA", 0.31f, 0.21f, 0.42f, 0.36f, "アトリ\nウム", "1F"),
        Zone("W3", 0.03f, 0.39f, 0.16f, 0.52f, "西3", "4F"),
        Zone("W4", 0.17f, 0.39f, 0.30f, 0.52f, "西4", "4F"),
        // 南展示棟
        Zone("S1", 0.03f, 0.60f, 0.20f, 0.75f, "南1", "1F"),
        Zone("S2", 0.21f, 0.60f, 0.38f, 0.75f, "南2", "1F"),
        Zone("S3", 0.03f, 0.78f, 0.20f, 0.93f, "南3", "4F"),
        Zone("S4", 0.21f, 0.78f, 0.38f, 0.93f, "南4", "4F"),
        // 東展示棟
        Zone("E1", 0.52f, 0.21f, 0.66f, 0.36f, "東1", "1F"),
        Zone("E2", 0.67f, 0.21f, 0.81f, 0.36f, "東2", "1F"),
        Zone("E3", 0.82f, 0.21f, 0.96f, 0.36f, "東3", "1F"),
        Zone("-e46", 0.52f, 0.39f, 0.96f, 0.47f, "東4〜6（休館）", "1F"),
        Zone("E7", 0.52f, 0.50f, 0.73f, 0.65f, "東7", "1F"),
        Zone("E8", 0.75f, 0.50f, 0.96f, 0.65f, "東8", "1F"),
    )

    /** 出発地点（VenueWalk.Start の id）→ 略図上の位置 */
    val starts: Map<String, Pair<Float, Float>> = mapOf(
        "main" to (0.50f to 0.175f),
        "east" to (0.49f to 0.285f),
        "east78" to (0.49f to 0.575f),
        "west" to (0.44f to 0.285f),
        "south" to (0.20f to 0.565f),
    )

    fun zone(id: String): Zone? = zones.firstOrNull { it.id == id }
}

/** 1日ぶんの道順：出発地点 → ホール（回る順） */
data class RouteDayPath(val day: Int, val hallIds: List<String>)

@Composable
fun RouteMap(
    days: List<RouteDayPath>,
    startId: String,
    dayColors: List<Color>,
    onSelectHall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val guide = GuideTheme.colors
    val onRoute: Set<String> = days.flatMap { it.hallIds }.toSet()

    fun placeColor(id: String): PlaceColor = when {
        id == "CT" -> guide.conference
        id.startsWith("E") -> guide.east
        id.startsWith("W") -> guide.west
        id.startsWith("S") -> guide.south
        else -> guide.neutral
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(onRoute) {
                detectTapGestures { pos ->
                    val x = pos.x / size.width
                    val y = pos.y / size.height
                    RouteLayout.zones.firstOrNull { it.tappable && it.id in onRoute && it.contains(x, y) }
                        ?.let { onSelectHall(it.id) }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

        fun drawZone(z: RouteLayout.Zone) {
            val tl = Offset(z.left * w, z.top * h)
            val sz = Size((z.right - z.left) * w, (z.bottom - z.top) * h)
            val c = placeColor(z.id)
            val station = z.id.startsWith("-rin") || z.id.startsWith("-yuri")
            val closed = z.id == "-e46"
            val active = z.id in onRoute
            val fill = when {
                station -> scheme.surfaceContainerHigh
                closed -> scheme.surfaceContainer
                z.id == "CT" -> c.container.copy(alpha = 0.6f)
                active -> c.container
                else -> c.container.copy(alpha = 0.3f)
            }
            val border = when {
                station -> scheme.outline
                closed -> scheme.outlineVariant
                active -> c.strong
                else -> c.strong.copy(alpha = 0.35f)
            }
            val textColor = when {
                station || closed -> scheme.onSurfaceVariant
                active || z.id == "CT" -> c.onContainer
                else -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
            }
            drawRoundRect(fill, tl, sz, CornerRadius(12f, 12f))
            drawRoundRect(
                border, tl, sz, CornerRadius(12f, 12f),
                style = if (z.floor == "4F") Stroke(if (active) 4f else 2f, pathEffect = dash) else Stroke(if (active) 4f else 2f),
            )
            val layout = measurer.measure(
                text = z.label,
                style = TextStyle(
                    color = textColor,
                    fontSize = if (z.id == "WA" || station || z.id == "CT") 11.sp else 13.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                constraints = Constraints(maxWidth = (sz.width - 6f).toInt().coerceAtLeast(1)),
                maxLines = 2,
            )
            // 数字の丸と重ならないよう、ホールの名前は少し上に置く
            val dy = if (z.tappable) -sz.height * 0.22f else 0f
            drawText(layout, topLeft = Offset(tl.x + (sz.width - layout.size.width) / 2f, tl.y + (sz.height - layout.size.height) / 2f + dy))
            if (z.floor == "4F") {
                val t = measurer.measure("4F", TextStyle(color = textColor, fontSize = 9.sp, fontWeight = FontWeight.Bold))
                drawText(t, topLeft = Offset(tl.x + sz.width - t.size.width - 5f, tl.y + 3f))
            }
        }

        RouteLayout.zones.forEach { drawZone(it) }

        // 出発地点
        val (sx, sy) = RouteLayout.starts[startId] ?: RouteLayout.starts.getValue("main")
        val startPt = Offset(sx * w, sy * h)

        fun center(id: String): Offset? = RouteLayout.zone(id)?.let { Offset(it.cx * w, it.cy * h + (it.bottom - it.top) * h * 0.14f) }

        val stroke = 3.dp.toPx()
        val radius = 11.dp.toPx()
        val visits = HashMap<String, Int>() // 同じホールを別の日にも回るときの丸のずらし

        days.forEachIndexed { di, day ->
            val color = dayColors[di % dayColors.size]
            val points = ArrayList<Offset>()
            points += startPt
            day.hallIds.forEach { id -> center(id)?.let { points += it } }
            // 線（日ごとに少しずらして、重なっても見分けられるように）
            val shift = (di % 3 - 1) * 4f
            val path = Path()
            points.forEachIndexed { i, p ->
                val q = Offset(p.x + shift, p.y + shift)
                if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
            }
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = 0.85f)
            // 矢印（各区間の中ほど）
            for (i in 1 until points.size) {
                val a = Offset(points[i - 1].x + shift, points[i - 1].y + shift)
                val b = Offset(points[i].x + shift, points[i].y + shift)
                val len = hypot(b.x - a.x, b.y - a.y)
                if (len < 24f) continue
                val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                val ang = atan2(b.y - a.y, b.x - a.x)
                val s = 6.dp.toPx()
                val tip = Offset(mid.x + cos(ang) * s, mid.y + sin(ang) * s)
                val l = Offset(mid.x + cos(ang + 2.5f) * s, mid.y + sin(ang + 2.5f) * s)
                val r = Offset(mid.x + cos(ang - 2.5f) * s, mid.y + sin(ang - 2.5f) * s)
                val tri = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(l.x, l.y)
                    lineTo(r.x, r.y)
                    close()
                }
                drawPath(tri, color)
            }
            // ホールに「その日に回る順」の数字
            day.hallIds.forEachIndexed { i, id ->
                val c = center(id) ?: return@forEachIndexed
                val n = visits.getOrDefault(id, 0)
                visits[id] = n + 1
                val pos = Offset(c.x + n * (radius * 2.1f), c.y)
                drawCircle(scheme.surface, radius + 2f, pos)
                drawCircle(color, radius, pos)
                val t = measurer.measure(
                    (i + 1).toString(),
                    TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                drawText(t, topLeft = Offset(pos.x - t.size.width / 2f, pos.y - t.size.height / 2f))
            }
        }

        // 出発地点の印
        drawCircle(scheme.surface, 7.dp.toPx(), startPt)
        drawCircle(scheme.onSurface, 5.dp.toPx(), startPt)
        drawCircle(scheme.surface, 2.dp.toPx(), startPt)
        val st = measurer.measure("出発", TextStyle(color = scheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold))
        // 東西のあいだの通路に置く出発地点は、ラベルを左に出す（ホールの箱と重ならないように）
        val labelX = if (startId == "east" || startId == "west" || startId == "east78") {
            startPt.x - 9.dp.toPx() - st.size.width
        } else {
            startPt.x + 9.dp.toPx()
        }
        drawText(st, topLeft = Offset(labelX, startPt.y - st.size.height / 2f))
    }
}
