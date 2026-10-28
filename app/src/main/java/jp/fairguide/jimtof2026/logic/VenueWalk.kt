package jp.fairguide.jimtof2026.logic

import kotlin.math.ceil
import kotlin.math.hypot

/**
 * 会場（東京ビッグサイト）を歩く時間の目安。ホール単位の位置関係だけを持つ。
 * 位置・距離・時間はすべて目安で、実際の通路や混み具合では前後する。
 * 東4〜6ホールは JIMTOF2026 では休館なので入れていない。
 */
object VenueWalk {

    /** 位置（x：東が正、y：北が正、単位は m の目安）、階、棟 */
    data class Point(val x: Double, val y: Double, val floor: Int, val building: Char)

    /** 出発地点 */
    enum class Start(val id: String, val label: String, val point: Point) {
        MAIN("main", "正面入口（会議棟）", Point(0.0, 0.0, 1, 'C')),
        EAST("east", "東展示棟", Point(170.0, 0.0, 1, 'E')),
        EAST78("east78", "東7・8", Point(590.0, 0.0, 1, 'N')),
        WEST("west", "西展示棟", Point(-140.0, 0.0, 1, 'W')),
        SOUTH("south", "南展示棟", Point(-230.0, -140.0, 1, 'S'));

        companion object {
            fun of(id: String?): Start = entries.firstOrNull { it.id == id } ?: MAIN
        }
    }

    /** 歩く速さの目安（会場は混むので遅め） */
    private const val METERS_PER_MINUTE = 60.0

    /** 1F⇔4F の移動（エスカレーター待ちを含む） */
    private const val FLOOR_CHANGE_MINUTES = 4.0

    /** 棟をまたぐとき（連絡通路・入口の分） */
    private const val BUILDING_CHANGE_MINUTES = 3.0

    /** 同じホールの中で次の小間へ移る目安 */
    const val SAME_HALL_MINUTES = 2

    /** ホール id（BoothCode.locate の hallId）→ 位置 */
    private val halls: Map<String, Point> = mapOf(
        "E1" to Point(240.0, -50.0, 1, 'E'),
        "E2" to Point(335.0, -50.0, 1, 'E'),
        "E3" to Point(430.0, -50.0, 1, 'E'),
        "E7" to Point(640.0, 0.0, 1, 'N'),
        "E8" to Point(740.0, 0.0, 1, 'N'),
        "WA" to Point(-180.0, 0.0, 1, 'W'),
        "W1" to Point(-270.0, 55.0, 1, 'W'),
        "W2" to Point(-270.0, -55.0, 1, 'W'),
        "W3" to Point(-270.0, 55.0, 4, 'W'),
        "W4" to Point(-270.0, -55.0, 4, 'W'),
        "S1" to Point(-240.0, -200.0, 1, 'S'),
        "S2" to Point(-350.0, -200.0, 1, 'S'),
        "S3" to Point(-240.0, -200.0, 4, 'S'),
        "S4" to Point(-350.0, -200.0, 4, 'S'),
    )

    fun hallPoint(hallId: String): Point? = halls[hallId]

    fun knows(hallId: String?): Boolean = hallId != null && hallId in halls

    /** 2地点のあいだを歩く時間（分、切り上げ、最低1分） */
    fun minutes(a: Point, b: Point): Int {
        var m = hypot(a.x - b.x, a.y - b.y) / METERS_PER_MINUTE
        if (a.floor != b.floor) m += FLOOR_CHANGE_MINUTES
        if (a.building != b.building) m += BUILDING_CHANGE_MINUTES
        return maxOf(1, ceil(m - 1e-9).toInt())
    }

    fun minutesBetweenHalls(a: String, b: String): Int {
        if (a == b) return 0
        val pa = halls[a] ?: return 0
        val pb = halls[b] ?: return 0
        return minutes(pa, pb)
    }

    fun minutesFromStart(start: Start, hallId: String): Int {
        val p = halls[hallId] ?: return 0
        return minutes(start.point, p)
    }

    /**
     * 出発地点から全ホールを1回ずつ回る、いちばん歩く時間の短い順番（戻ってこなくてよい）。
     * ホールは最大でも14なので、部分集合ごとの最短を全部計算して厳密に解く。
     */
    fun order(start: Start, hallIds: Collection<String>): List<String> {
        val ids = hallIds.filter { it in halls }.distinct()
        val n = ids.size
        if (n <= 1) return ids
        if (n > MAX_EXACT) return greedy(start, ids)
        val cost = Array(n) { i -> IntArray(n) { j -> minutesBetweenHalls(ids[i], ids[j]) } }
        val first = IntArray(n) { minutesFromStart(start, ids[it]) }
        val full = (1 shl n) - 1
        val inf = Int.MAX_VALUE / 4
        val best = Array(1 shl n) { IntArray(n) { inf } }
        val parent = Array(1 shl n) { IntArray(n) { -1 } }
        for (j in 0 until n) best[1 shl j][j] = first[j]
        for (mask in 1..full) {
            for (j in 0 until n) {
                val cur = best[mask][j]
                if (cur >= inf || mask and (1 shl j) == 0) continue
                for (k in 0 until n) {
                    if (mask and (1 shl k) != 0) continue
                    val next = mask or (1 shl k)
                    val c = cur + cost[j][k]
                    if (c < best[next][k]) {
                        best[next][k] = c
                        parent[next][k] = j
                    }
                }
            }
        }
        var end = 0
        for (j in 1 until n) if (best[full][j] < best[full][end]) end = j
        val out = ArrayList<String>(n)
        var mask = full
        var j = end
        while (j >= 0) {
            out += ids[j]
            val p = parent[mask][j]
            mask = mask and (1 shl j).inv()
            j = p
        }
        return out.reversed()
    }

    /** 近いホールから順に（厳密に解くには多すぎるときの保険） */
    private fun greedy(start: Start, ids: List<String>): List<String> {
        val left = ids.toMutableList()
        val out = ArrayList<String>(ids.size)
        var here: Point = start.point
        while (left.isNotEmpty()) {
            val next = left.minByOrNull { minutes(here, halls.getValue(it)) }!!
            left.remove(next)
            out += next
            here = halls.getValue(next)
        }
        return out
    }

    private const val MAX_EXACT = 16
}
