package jp.fairguide.jimtof2026

import jp.fairguide.jimtof2026.data.BoothItem
import jp.fairguide.jimtof2026.data.ReportExtra
import jp.fairguide.jimtof2026.data.ReportNotes
import jp.fairguide.jimtof2026.data.ReportParser
import jp.fairguide.jimtof2026.data.RouteSettings
import jp.fairguide.jimtof2026.logic.BoothCode
import jp.fairguide.jimtof2026.logic.RouteEntry
import jp.fairguide.jimtof2026.logic.RoutePlanner
import jp.fairguide.jimtof2026.logic.RouteStop
import jp.fairguide.jimtof2026.logic.VenueWalk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 見学ルート（歩く順番と所要時間の目安）のテスト */
class RoutePlannerTest {

    private val report by lazy {
        ReportParser.parse(File("src/main/assets/report.json").readText(Charsets.UTF_8))
    }

    private fun stop(name: String, booth: String, items: List<String> = listOf("1"), visited: Boolean = false) =
        RouteStop(name, booth, items, items.associateWith { "理由" }, listOf("確かめる"), fromMyList = false, visited = visited)

    private fun stopsOf(plan: List<RouteEntry>) = plan.filterIsInstance<RouteEntry.Stop>()

    // ---- VenueWalk ----

    @Test
    fun walkTimesAreSymmetricAndPositive() {
        val ids = listOf("E1", "E2", "E3", "E7", "E8", "WA", "W1", "W2", "W3", "W4", "S1", "S2", "S3", "S4")
        for (a in ids) for (b in ids) {
            assertEquals(VenueWalk.minutesBetweenHalls(a, b), VenueWalk.minutesBetweenHalls(b, a))
            if (a != b) assertTrue("$a→$b", VenueWalk.minutesBetweenHalls(a, b) >= 1)
        }
        assertEquals(0, VenueWalk.minutesBetweenHalls("E1", "E1"))
        // 隣のホールは近く、東⇔西は遠い。階が違えば余分にかかる
        assertTrue(VenueWalk.minutesBetweenHalls("E1", "E2") < VenueWalk.minutesBetweenHalls("E1", "E3"))
        assertTrue(VenueWalk.minutesBetweenHalls("E1", "S4") > VenueWalk.minutesBetweenHalls("E1", "E8"))
        assertTrue(VenueWalk.minutesBetweenHalls("E1", "W1") > VenueWalk.minutesBetweenHalls("E1", "E3"))
        assertTrue(VenueWalk.minutesBetweenHalls("W1", "W3") >= 4)
        assertTrue(VenueWalk.minutesBetweenHalls("E8", "S4") > 20)
    }

    @Test
    fun everyKnownHallHasAPosition() {
        BoothCode.HALL_ORDER.forEach { assertTrue(it, VenueWalk.knows(it)) }
        assertFalse(VenueWalk.knows("E4"))
        assertFalse(VenueWalk.knows(null))
    }

    @Test
    fun orderIsOptimalForSmallSets() {
        // 全部の並びを試した最短と一致する
        val halls = listOf("E1", "E3", "E8", "W2", "S4", "WA")
        for (start in VenueWalk.Start.entries) {
            val best = permutations(halls).minOf { cost(start, it) }
            val got = VenueWalk.order(start, halls)
            assertEquals(halls.toSet(), got.toSet())
            assertEquals(start.name, best, cost(start, got))
        }
    }

    @Test
    fun orderFromMainEntranceDoesNotBounceBetweenBuildings() {
        val got = VenueWalk.order(VenueWalk.Start.MAIN, listOf("E1", "W1", "E3", "W2", "S1", "E8"))
        // 東の塊と西・南の塊が分かれている（東→西→東のような往復がない）
        val sides = got.map { if (it.startsWith("E")) 'E' else 'W' }
        var switches = 0
        for (i in 1 until sides.size) if (sides[i] != sides[i - 1]) switches++
        assertEquals(got.toString(), 1, switches)
    }

    @Test
    fun orderFromEast78StartsNearby() {
        val got = VenueWalk.order(VenueWalk.Start.EAST78, listOf("W1", "E1", "E8", "E2"))
        assertEquals("E8", got.first())
        assertEquals("W1", got.last())
    }

    private fun cost(start: VenueWalk.Start, order: List<String>): Int {
        var c = VenueWalk.minutesFromStart(start, order.first())
        for (i in 1 until order.size) c += VenueWalk.minutesBetweenHalls(order[i - 1], order[i])
        return c
    }

    private fun <T> permutations(list: List<T>): List<List<T>> {
        if (list.size <= 1) return listOf(list)
        return list.flatMap { head -> permutations(list - head).map { listOf(head) + it } }
    }

    // ---- collect ----

    @Test
    fun collectMergesCompaniesAcrossItemsAndAddsExtrasAndMyList() {
        // ①だけに載っている会社を、②にも自分で足した
        val a = report.item("1")!!.companies.first { c -> report.items.count { it.companies.any { x -> x.key == c.key } } == 1 }
        val extra = ReportExtra("2", a.name, a.booth)
        val notes = ReportNotes(
            extras = listOf(extra, ReportExtra("5", "株式会社テスト", "W1001")),
            route = RouteSettings(itemIds = setOf("1", "2", "5"), includeMyList = true),
        )
        val myList = listOf(
            BoothItem(1, a.name, a.booth, "", visited = true, createdAt = 1),
            BoothItem(2, "マイリストだけの会社", "S3010", "", visited = false, createdAt = 2),
            BoothItem(3, "小間なし", "", "", visited = false, createdAt = 3),
        )
        val stops = RoutePlanner.collect(report, notes, myList)
        val keys = stops.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        val first = stops.first { it.name == a.name }
        assertEquals(listOf("1", "2"), first.itemIds)
        assertTrue(first.visited)
        assertEquals(a.why, first.whys["1"])
        assertTrue(a.look.all { it in first.checks })
        val test = stops.first { it.name == "株式会社テスト" }
        assertEquals(listOf("5"), test.itemIds)
        assertTrue(test.whys.isEmpty())
        val mine = stops.first { it.name == "マイリストだけの会社" }
        assertTrue(mine.fromMyList && mine.itemIds.isEmpty())
        assertTrue(stops.none { it.name == "小間なし" })
        // ブースリストを入れない設定なら、その会社は出ない
        val without = RoutePlanner.collect(report, notes.copy(route = notes.route.copy(includeMyList = false)), myList)
        assertTrue(without.none { it.name == "マイリストだけの会社" })
        // 項目①だけなら、①の会社の数と同じ
        val only1 = RoutePlanner.collect(report, ReportNotes(route = RouteSettings(itemIds = setOf("1"))), emptyList())
        assertEquals(report.item("1")!!.companies.size, only1.size)
    }

    // ---- plan ----

    @Test
    fun planGroupsByHallInWalkingOrderAndSortsByBooth() {
        val stops = listOf(
            stop("c", "E1024"), stop("a", "E1001"), stop("w", "W2010"), stop("s", "S1005"), stop("e8", "E8001"), stop("e3", "E3031"),
        )
        val settings = RouteSettings(start = "main", minutesPerStop = 15, minutesPerDay = 100_000)
        val plan = RoutePlanner.plan(stops, settings, emptySet())
        assertEquals(6, plan.stops)
        assertEquals(5, plan.halls)
        assertEquals(1, plan.days)
        val halls = plan.entries.filterIsInstance<RouteEntry.Hall>().map { it.hallId }
        assertEquals(VenueWalk.order(VenueWalk.Start.MAIN, halls), halls)
        val ordered = stopsOf(plan.entries)
        assertEquals((1..6).toList(), ordered.map { it.number })
        // 同じホールの中は小間番号順
        val e1 = ordered.filter { it.stop.hallId == "E1" }.map { it.stop.code }
        assertEquals(listOf("E1001", "E1024"), e1)
        // 到着時刻は単調に増え、最初のホールは出発地点からの徒歩ぶん
        assertTrue(ordered.zipWithNext().all { (x, y) -> y.arriveMinutes > x.arriveMinutes })
        assertEquals(VenueWalk.minutesFromStart(VenueWalk.Start.MAIN, halls.first()), ordered.first().arriveMinutes)
        // 所要 ＝ 滞在の合計 ＋ 移動
        assertEquals(6 * 15 + plan.walkMinutes, plan.totalMinutes)
        // 同じホールの2社目は +2分
        val hallE1 = plan.entries.first { it is RouteEntry.Hall && it.hallId == "E1" } as RouteEntry.Hall
        assertEquals(2, hallE1.stops)
        assertEquals(ordered[e1Index(ordered, "E1001")].arriveMinutes + 15 + VenueWalk.SAME_HALL_MINUTES, ordered[e1Index(ordered, "E1024")].arriveMinutes)
    }

    private fun e1Index(list: List<RouteEntry.Stop>, code: String) = list.indexOfFirst { it.stop.code == code }

    @Test
    fun planSplitsIntoDaysWhenOverCapacity() {
        val stops = (1..8).map { stop("c$it", "E1%03d".format(it)) } + (1..4).map { stop("w$it", "W1%03d".format(it)) }
        val settings = RouteSettings(start = "main", minutesPerStop = 20, minutesPerDay = 120)
        val plan = RoutePlanner.plan(stops, settings, emptySet())
        assertTrue(plan.days > 1)
        val days = plan.entries.filterIsInstance<RouteEntry.Day>()
        assertEquals(plan.days, days.size)
        assertEquals(12, days.sumOf { it.stops })
        days.forEach { assertTrue("${it.day}日目 ${it.minutes}分", it.minutes <= 120) }
        // 日が変わったら、その日の最初の会社は出発地点から歩いた時刻で始まる
        val byDay = stopsOf(plan.entries).groupBy { it.day }
        byDay.values.forEach { list ->
            val first = list.first()
            assertEquals(VenueWalk.minutesFromStart(VenueWalk.Start.MAIN, first.stop.hallId!!), first.arriveMinutes)
        }
        // 通しの番号は続く
        assertEquals((1..12).toList(), stopsOf(plan.entries).map { it.number })
        // 「分けない」なら1日
        assertEquals(1, RoutePlanner.plan(stops, settings.copy(minutesPerDay = 100_000), emptySet()).days)
    }

    @Test
    fun planHandlesSkipsVisitedAndUnknownHalls() {
        val stops = listOf(
            stop("a", "E1001"), stop("b", "E1002", visited = true), stop("c", "W1001"), stop("x", "Z9999"), stop("y", ""),
        )
        val skips = setOf(stops[2].key)
        val plan = RoutePlanner.plan(stops, RouteSettings(skipVisited = true), skips)
        assertEquals(1, plan.skippedVisited)
        assertEquals(listOf("c"), plan.skipped.map { it.name })
        assertEquals(listOf("a"), stopsOf(plan.entries).map { it.stop.name })
        assertEquals(2, plan.unknown)
        val unknown = plan.entries.filterIsInstance<RouteEntry.Unknown>().map { it.stop.name }
        assertEquals(listOf("x", "y"), unknown)
        assertEquals(3, plan.stops)
        // 訪問済みを外さない設定なら b も入る（訪問済みの印つき）
        val keep = RoutePlanner.plan(stops, RouteSettings(skipVisited = false), skips)
        assertEquals(listOf("a", "b"), stopsOf(keep.entries).map { it.stop.name })
        assertEquals(0, keep.skippedVisited)
        // 何も残らなければ日も無い
        val empty = RoutePlanner.plan(emptyList(), RouteSettings(), emptySet())
        assertEquals(0, empty.days)
        assertTrue(empty.entries.isEmpty())
        assertNull(empty.entries.firstOrNull())
    }

    @Test
    fun companiesInSeveralItemsStayLonger() {
        val s = RouteSettings(minutesPerStop = 15)
        assertEquals(15, RoutePlanner.stayMinutes(stop("a", "E1001", listOf("1")), s))
        assertEquals(15 + RoutePlanner.EXTRA_ITEM_MINUTES, RoutePlanner.stayMinutes(stop("a", "E1001", listOf("1", "2")), s))
        assertEquals(15, RoutePlanner.stayMinutes(stop("a", "E1001", emptyList()), s))
    }

    @Test
    fun realReportRouteCoversEveryRecommendedCompanyOnce() {
        val notes = ReportNotes(route = RouteSettings(itemIds = setOf("1", "2", "5"), minutesPerDay = 300))
        val stops = RoutePlanner.collect(report, notes, emptyList())
        val expected = report.items.flatMap { it.companies }.map { it.key }.toSet()
        assertEquals(expected, stops.map { it.key }.toSet())
        val plan = RoutePlanner.plan(stops, notes.route, emptySet())
        assertEquals(0, plan.unknown)
        assertEquals(stops.size, stopsOf(plan.entries).size)
        assertTrue(plan.days >= 2) // 50社前後なので1日には収まらない
        // ホールは1回ずつ（日をまたいで同じホールに戻ることはある）
        val hallsPerDay = plan.entries.filterIsInstance<RouteEntry.Hall>().groupBy { it.day }
        hallsPerDay.values.forEach { list -> assertEquals(list.size, list.map { it.hallId }.toSet().size) }
    }

    @Test
    fun shareTextListsEveryStopWithMarks() {
        val notes = ReportNotes(route = RouteSettings(itemIds = setOf("1", "2"), minutesPerDay = 240, start = "east"))
        val stops = RoutePlanner.collect(report, notes, emptyList())
        val plan = RoutePlanner.plan(stops, notes.route, emptySet())
        val text = RoutePlanner.text(plan, report, notes.route, "2026/10/29")
        assertTrue(text.startsWith("JIMTOF2026 見学ルート（2026/10/29 作成）"))
        assertTrue(text.contains("対象：①競合・②省人化・無人化／出発：東展示棟／1社 約15分"))
        assertTrue(text.contains("■ 1日目"))
        assertTrue(text.contains("▼ "))
        stopsOf(plan.entries).forEach { e ->
            assertTrue(e.stop.name, text.contains(" ${e.number}. ${RoutePlanner.elapsedLabel(e.arriveMinutes)} ${e.stop.code} "))
        }
        val both = stops.first { it.itemIds.size >= 2 }
        assertTrue(text.contains(both.code + " ") && text.contains(" ①②"))
        assertEquals("+1:05", RoutePlanner.elapsedLabel(65))
        assertEquals("+0:00", RoutePlanner.elapsedLabel(0))
    }
}
