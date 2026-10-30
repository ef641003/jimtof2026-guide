package jp.fairguide.jimtof2026.logic

import jp.fairguide.jimtof2026.data.BoothItem
import jp.fairguide.jimtof2026.data.NoveltyKey
import jp.fairguide.jimtof2026.data.ReportData
import jp.fairguide.jimtof2026.data.ReportNotes
import jp.fairguide.jimtof2026.data.RouteSettings

/** ルートに入れる会社1社ぶん。同じ会社が複数の項目に載っていても1回で回る */
data class RouteStop(
    val name: String,
    val booth: String,
    /** 載っている項目の id。ブースリストだけの会社は空 */
    val itemIds: List<String>,
    /** 項目の印（①②③）。itemIds と同じ並び */
    val marks: List<String>,
    /** 項目id → おすすめの理由 */
    val whys: Map<String, String>,
    /** ブースで確かめること（全項目ぶん、重複なし） */
    val checks: List<String>,
    val fromMyList: Boolean,
    val visited: Boolean,
    /** おすすめの順位（0 が1番目）。複数の項目に載っていれば、いちばん上の順位。ブースリストだけの会社は大きい値 */
    val rank: Int = 0,
) {
    val key: String get() = NoveltyKey.of(name, booth)
    val hallId: String? get() = BoothCode.locate(booth)?.hallId
    val code: String get() = BoothCode.normalize(booth).ifEmpty { booth }
}

sealed interface RouteEntry {
    /** 「◯日目」の見出し（2日以上に分かれるときだけ） */
    data class Day(val day: Int, val minutes: Int, val stops: Int) : RouteEntry

    /** ホールの見出し。walkMinutes は前の場所からここまで歩く目安 */
    data class Hall(val hallId: String, val hallName: String, val floor: String, val walkMinutes: Int, val fromLabel: String, val arriveMinutes: Int, val stops: Int, val day: Int) : RouteEntry

    /** 会社。arriveMinutes はその日の出発からの経過（分） */
    data class Stop(val number: Int, val stop: RouteStop, val arriveMinutes: Int, val stayMinutes: Int, val day: Int) : RouteEntry

    /** 小間番号からホールが分からない会社（最後にまとめる） */
    data class Unknown(val stop: RouteStop) : RouteEntry
}

data class RoutePlan(
    val entries: List<RouteEntry>,
    val stops: Int,
    val halls: Int,
    val days: Int,
    /** 全体の所要（分、日をまたぐ分も合計） */
    val totalMinutes: Int,
    val walkMinutes: Int,
    /** 訪問済みで外した数 */
    val skippedVisited: Int,
    /** 自分で外した会社 */
    val skipped: List<RouteStop>,
    val unknown: Int,
    /** 日数を決めたとき、時間内に入らなかった会社（優先度の高い順） */
    val dropped: List<RouteStop> = emptyList(),
    /** 日数を決めたときの対象の会社数（入らなかった会社も含む） */
    val candidates: Int = 0,
)

/** 見学レポートの項目に合わせて、歩く距離が短くなる順に回る順番と所要時間の目安を組み立てる */
object RoutePlanner {

    /** 複数の項目に載っている会社は、項目が1つ増えるごとに滞在を少し長く見る */
    const val EXTRA_ITEM_MINUTES = 5

    /** 項目のおすすめ・自分で足した会社・（設定により）ブースリストから、回る会社を集める */
    fun collect(data: ReportData, notes: ReportNotes, myList: List<BoothItem>): List<RouteStop> {
        val settings = notes.route
        val order = ArrayList<String>()
        val byKey = LinkedHashMap<String, MutableStop>()
        fun get(name: String, booth: String): MutableStop {
            val key = NoveltyKey.of(name, booth)
            return byKey.getOrPut(key) {
                order += key
                MutableStop(name, booth)
            }
        }
        for (item in data.items) {
            if (item.id !in settings.itemIds) continue
            ReportDraft.rows(data, item.id, notes).forEachIndexed { index, row ->
                val s = get(row.name, row.booth)
                if (index < s.rank) s.rank = index
                if (s.itemIds.add(item.id)) s.marks += item.mark
                row.company?.let { c ->
                    s.whys[item.id] = c.why
                    c.look.forEach { l -> if (l.isNotBlank() && s.checks.none { it == l.trim() }) s.checks += l.trim() }
                }
            }
        }
        if (settings.includeMyList) {
            for (b in myList) {
                // 小間番号の無い登録は回る順番に入れようがないので、ブースリストの側だけに置く
                if (b.company.isBlank() || b.booth.isBlank()) continue
                val s = get(b.company, b.booth)
                s.fromMyList = true
                if (s.itemIds.isEmpty()) s.rank = MY_LIST_RANK
            }
        }
        return order.map { key ->
            val s = byKey.getValue(key)
            val mine = myList.firstOrNull { it.company == s.name && BoothCode.normalize(it.booth) == BoothCode.normalize(s.booth) }
            RouteStop(
                name = s.name,
                booth = s.booth,
                itemIds = s.itemIds.toList(),
                marks = s.marks.toList(),
                whys = s.whys,
                checks = s.checks,
                fromMyList = s.fromMyList,
                visited = mine?.visited == true,
                rank = s.rank,
            )
        }
    }

    private class MutableStop(val name: String, val booth: String) {
        val itemIds = LinkedHashSet<String>()
        val marks = ArrayList<String>()
        val whys = LinkedHashMap<String, String>()
        val checks = ArrayList<String>()
        var fromMyList = false
        var rank = Int.MAX_VALUE
    }

    /** ブースリストだけの会社の順位（おすすめの会社のあと） */
    private const val MY_LIST_RANK = 1000

    /**
     * 日数が足りないときに先に入れる順。各項目のおすすめ1番目どうし→2番目どうし…と交互に入れ、
     * 同じ順位なら複数の項目に載っている会社（1社で何項目も見られる）を先にする。
     */
    val PRIORITY: Comparator<RouteStop> = compareBy<RouteStop> { it.rank }
        .thenByDescending { it.itemIds.size }
        .thenBy { it.itemIds.firstOrNull() ?: "~" }
        .thenBy { it.name }

    /** 滞在の目安（分） */
    fun stayMinutes(stop: RouteStop, settings: RouteSettings): Int =
        settings.minutesPerStop + EXTRA_ITEM_MINUTES * maxOf(0, stop.itemIds.size - 1)

    fun plan(all: List<RouteStop>, settings: RouteSettings, skips: Set<String>): RoutePlan {
        val skippedVisited = if (settings.skipVisited) all.count { it.visited && it.key !in skips } else 0
        val skipped = all.filter { it.key in skips }
        val active = all.filter { it.key !in skips && !(settings.skipVisited && it.visited) }
        val known = active.filter { VenueWalk.knows(it.hallId) }
        val unknown = active.filter { !VenueWalk.knows(it.hallId) }
        if (settings.days <= 0) return layout(known, unknown, settings, skippedVisited, skipped)

        // 日数が決まっているとき：優先度の高い会社から順に入れ、決めた日数に収まらなくなる会社は外す
        // （外したあとも、短い時間で回れる会社は続けて入れていく）
        val chosen = ArrayList<RouteStop>()
        val dropped = ArrayList<RouteStop>()
        for (s in known.sortedWith(PRIORITY)) {
            chosen += s
            if (layout(chosen, emptyList(), settings, 0, emptyList()).days > settings.days) {
                chosen.removeAt(chosen.lastIndex)
                dropped += s
            }
        }
        return layout(chosen, unknown, settings, skippedVisited, skipped)
            .copy(dropped = dropped, candidates = known.size + unknown.size)
    }

    /** 回る会社が決まったあと、ホールの順・日分け・時刻を組み立てる */
    private fun layout(
        known: List<RouteStop>,
        unknown: List<RouteStop>,
        settings: RouteSettings,
        skippedVisited: Int,
        skipped: List<RouteStop>,
    ): RoutePlan {

        val start = VenueWalk.Start.of(settings.start)
        val byHall = known.groupBy { it.hallId!! }
        val hallOrder = VenueWalk.order(start, byHall.keys)

        val entries = ArrayList<RouteEntry>()
        val dayMarks = ArrayList<Int>() // Day エントリの位置（あとで所要を埋める）
        var day = 1
        var elapsed = 0 // その日の経過（分）
        var dayStops = 0
        var number = 0
        var total = 0
        var walk = 0
        var prevHall: String? = null
        var prevLabel = start.label
        val capacity = maxOf(30, settings.minutesPerDay)

        dayMarks += entries.size
        entries += RouteEntry.Day(1, 0, 0)

        for (hallId in hallOrder) {
            val stops = byHall.getValue(hallId).sortedWith(compareBy({ BoothCode.normalize(it.booth) }, { it.name }))
            val loc = BoothCode.locate(stops.first().booth)!!
            var walkHere = if (prevHall == null) VenueWalk.minutesFromStart(start, hallId) else VenueWalk.minutesBetweenHalls(prevHall, hallId)
            var fromLabel = prevLabel
            var hallEntryIndex = -1
            stops.forEachIndexed { i, stop ->
                val stay = stayMinutes(stop, settings)
                val moveIfContinue = if (i == 0) walkHere else VenueWalk.SAME_HALL_MINUTES
                // その日の持ち時間を超えるなら、次の日にまわす（その日に1社もなければ超えても入れる）
                if (dayStops > 0 && elapsed + moveIfContinue + stay > capacity) {
                    // ここまでを1日ぶんとして確定
                    (entries[dayMarks.last()] as RouteEntry.Day).let { entries[dayMarks.last()] = it.copy(minutes = elapsed, stops = dayStops) }
                    day++
                    elapsed = 0
                    dayStops = 0
                    dayMarks += entries.size
                    entries += RouteEntry.Day(day, 0, 0)
                    // 新しい日は出発地点から歩き直す
                    walkHere = VenueWalk.minutesFromStart(start, hallId)
                    fromLabel = start.label
                    hallEntryIndex = -1
                }
                val move = if (hallEntryIndex < 0) walkHere else VenueWalk.SAME_HALL_MINUTES
                walk += move
                elapsed += move
                if (hallEntryIndex < 0) {
                    hallEntryIndex = entries.size
                    entries += RouteEntry.Hall(hallId, loc.hallName, loc.floor, walkHere, fromLabel, elapsed, 0, day)
                }
                number++
                entries += RouteEntry.Stop(number, stop, elapsed, stay, day)
                (entries[hallEntryIndex] as RouteEntry.Hall).let { entries[hallEntryIndex] = it.copy(stops = it.stops + 1) }
                elapsed += stay
                total += move + stay
                dayStops++
            }
            prevHall = hallId
            prevLabel = loc.hallName
        }
        (entries[dayMarks.last()] as RouteEntry.Day).let { entries[dayMarks.last()] = it.copy(minutes = elapsed, stops = dayStops) }
        if (known.isEmpty()) entries.removeAt(dayMarks.last())
        unknown.sortedBy { it.name }.forEach { entries += RouteEntry.Unknown(it) }

        return RoutePlan(
            entries = entries,
            stops = known.size + unknown.size,
            halls = hallOrder.size,
            days = if (known.isEmpty()) 0 else day,
            totalMinutes = total,
            walkMinutes = walk,
            skippedVisited = skippedVisited,
            skipped = skipped,
            unknown = unknown.size,
        )
    }

    fun elapsedLabel(minutes: Int): String = "+%d:%02d".format(minutes / 60, minutes % 60)

    /** 共有用のテキスト */
    fun text(plan: RoutePlan, data: ReportData, settings: RouteSettings, today: String): String {
        val sb = StringBuilder()
        sb.append("JIMTOF2026 見学ルート（").append(today).append(" 作成）\n")
        val items = data.items.filter { it.id in settings.itemIds }.joinToString("・") { it.short }
        sb.append("対象：").append(items.ifEmpty { "（項目なし）" })
        if (settings.includeMyList) sb.append("＋ブースリスト")
        sb.append("／出発：").append(VenueWalk.Start.of(settings.start).label)
        sb.append("／1社 約").append(settings.minutesPerStop).append("分\n")
        if (settings.days > 0) {
            sb.append("日数：").append(settings.days).append("日（1日 ")
                .append(if (settings.minutesPerDay >= 100_000) "時間の上限なし" else "約" + formatDuration(settings.minutesPerDay.toLong()))
                .append("）で ").append(plan.candidates).append("社中 ").append(plan.stops).append("社を回るプラン\n")
        }
        sb.append("全 ").append(plan.stops).append("社・").append(plan.halls).append("ホール／約")
            .append(formatDuration(plan.totalMinutes.toLong())).append("（移動 約").append(plan.walkMinutes).append("分）\n")
        for (e in plan.entries) {
            when (e) {
                is RouteEntry.Day -> if (plan.days > 1) {
                    sb.append("\n■ ").append(e.day).append("日目（").append(e.stops).append("社・約").append(formatDuration(e.minutes.toLong())).append("）\n")
                }
                is RouteEntry.Hall -> sb.append("▼ ").append(e.hallName).append("・").append(e.floor)
                    .append("（").append(e.fromLabel).append("から徒歩 約").append(e.walkMinutes).append("分）\n")
                is RouteEntry.Stop -> {
                    sb.append(' ').append(e.number).append(". ").append(elapsedLabel(e.arriveMinutes)).append(' ')
                        .append(e.stop.code).append(' ').append(ReportDraft.shortName(e.stop.name))
                    val marks = e.stop.marks.joinToString("")
                    if (marks.isNotEmpty()) sb.append(' ').append(marks)
                    if (e.stop.fromMyList && e.stop.itemIds.isEmpty()) sb.append(" ★")
                    sb.append('\n')
                }
                is RouteEntry.Unknown -> sb.append(" ・").append(e.stop.code).append(' ').append(e.stop.name).append("（ホール不明）\n")
            }
        }
        if (plan.dropped.isNotEmpty()) {
            sb.append("\n◇ 時間内に入らなかった会社（").append(plan.dropped.size).append("社）\n")
            plan.dropped.forEach { s ->
                sb.append(" ・").append(s.code).append(' ').append(ReportDraft.shortName(s.name))
                val marks = s.marks.joinToString("")
                if (marks.isNotEmpty()) sb.append(' ').append(marks)
                sb.append('\n')
            }
        }
        sb.append("※ 位置関係・所要時間は目安です。小間の場所は公式の小間割図で確認してください。")
        return sb.toString()
    }

}
