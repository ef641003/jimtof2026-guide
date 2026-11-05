package jp.fairguide.jimtof2026.data

import android.content.Context
import jp.fairguide.jimtof2026.logic.BoothCode
import org.json.JSONObject

/**
 * 小間1つの形と印の位置（公式の小間割図から写したもの。tools/fetch_booth_map.py が作る）。
 * 座標は公式図の 1/8、y は下向き。
 */
class MapBooth(
    val code: String,
    /** x0, y0, x1, y1, … の並び */
    val points: FloatArray,
    val markerX: Float,
    val markerY: Float,
    /** パビリオン（1区画に複数社）の小間番号。ふつうの小間は空 */
    val members: List<String>,
) {
    val left: Float = points.filterIndexed { i, _ -> i % 2 == 0 }.min()
    val right: Float = points.filterIndexed { i, _ -> i % 2 == 0 }.max()
    val top: Float = points.filterIndexed { i, _ -> i % 2 == 1 }.min()
    val bottom: Float = points.filterIndexed { i, _ -> i % 2 == 1 }.max()
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** 点が小間の中か（多角形の内外判定） */
    fun contains(x: Float, y: Float): Boolean {
        if (x < left || x > right || y < top || y > bottom) return false
        var inside = false
        val n = points.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = points[i * 2]
            val yi = points[i * 2 + 1]
            val xj = points[j * 2]
            val yj = points[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}

/** ホールのまとまり（公式の小間割図1枚ぶん。例：東1〜3ホール） */
class HallMap(
    val id: String,
    val label: String,
    val floor: String,
    /** 公式の小間割図（Web）。{code} を小間番号に置き換える */
    val pageUrl: String,
    val booths: List<MapBooth>,
) {
    val left: Float = booths.minOf { it.left }
    val right: Float = booths.maxOf { it.right }
    val top: Float = booths.minOf { it.top }
    val bottom: Float = booths.maxOf { it.bottom }

    /** ホール（E1・E2・WA など）ごとの外枠。小間の広がりから求める */
    val halls: List<HallArea> = booths.groupBy { hallIdOf(it.code) }
        .filterKeys { it != null }
        .map { (hall, list) ->
            HallArea(hall!!, list.minOf { it.left }, list.minOf { it.top }, list.maxOf { it.right }, list.maxOf { it.bottom })
        }
        .sortedBy { BoothCode.HALL_ORDER.indexOf(it.hallId) }

    fun boothAt(x: Float, y: Float): MapBooth? = booths.firstOrNull { it.contains(x, y) }

    fun pageFor(code: String): String = pageUrl.replace("{code}", code)

    companion object {
        fun hallIdOf(code: String): String? = BoothCode.locate(code)?.hallId
    }
}

class HallArea(val hallId: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val name: String get() = BoothCode.locate(if (hallId == "WA") "WA001" else "${hallId}001")?.hallName ?: hallId
}

class BoothMapData(val maps: List<HallMap>) {
    private val byCode: Map<String, Pair<HallMap, MapBooth>> = buildMap {
        for (m in maps) for (b in m.booths) {
            put(b.code, m to b)
            for (member in b.members) putIfAbsent(member, m to b)
        }
    }

    /** 小間番号（表記ゆれ可）から、その小間が載っている図と小間 */
    fun find(booth: String): Pair<HallMap, MapBooth>? {
        val code = BoothCode.normalize(booth)
        byCode[code]?.let { return it }
        // パビリオンの小区画（E1009-12 など）が図に無ければ、本体（E1009）に寄せる
        val base = code.substringBefore('-')
        return byCode[base]
    }

    /** ホール（E1 など）が載っている図 */
    fun mapForHall(hallId: String): HallMap? = maps.firstOrNull { m -> m.halls.any { it.hallId == hallId } }

    fun map(id: String): HallMap? = maps.firstOrNull { it.id == id }
}

object BoothMapParser {
    private val LABELS = mapOf(
        "e1-3" to ("東1〜3ホール" to "1F"),
        "e7-8" to ("東7・8ホール" to "1F"),
        "w1-2" to ("西1・2ホール・アトリウム" to "1F"),
        "w3-4" to ("西3・4ホール" to "4F"),
        "s1-2" to ("南1・2ホール" to "1F"),
        "s3-4" to ("南3・4ホール" to "4F"),
    )

    /** 会場を歩く順（東→西→南）に並べる */
    private val ORDER = listOf("e1-3", "e7-8", "w1-2", "w3-4", "s1-2", "s3-4")

    fun parse(json: String): BoothMapData {
        val root = JSONObject(json)
        val arr = root.getJSONArray("maps")
        val maps = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val bs = o.getJSONArray("booths")
            val booths = (0 until bs.length()).map { j ->
                val b = bs.getJSONObject(j)
                val p = b.getJSONArray("p")
                val m = b.getJSONArray("m")
                val g = b.optJSONArray("g")
                MapBooth(
                    code = b.getString("c"),
                    points = FloatArray(p.length()) { k -> p.getDouble(k).toFloat() },
                    markerX = m.getDouble(0).toFloat(),
                    markerY = m.getDouble(1).toFloat(),
                    members = if (g == null) emptyList() else (0 until g.length()).map { k -> g.getString(k) },
                )
            }
            val (label, floor) = LABELS[id] ?: (id to "")
            HallMap(id, label, floor, o.getString("page"), booths)
        }
        return BoothMapData(maps.sortedBy { ORDER.indexOf(it.id).let { x -> if (x < 0) 99 else x } })
    }
}

object BoothMapRepository {
    @Volatile
    private var cached: BoothMapData? = null

    fun get(context: Context): BoothMapData {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: context.applicationContext.assets.open("booth_map.json").bufferedReader(Charsets.UTF_8)
                .use { BoothMapParser.parse(it.readText()) }
                .also { cached = it }
        }
    }
}
