package jp.fairguide.jimtof2026.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.fairguide.jimtof2026.data.BoothItem
import jp.fairguide.jimtof2026.data.Exhibitor
import jp.fairguide.jimtof2026.data.ExhibitorRepository
import jp.fairguide.jimtof2026.data.FairData
import jp.fairguide.jimtof2026.data.FairEvent
import jp.fairguide.jimtof2026.data.FairRepository
import jp.fairguide.jimtof2026.data.NoveltyData
import jp.fairguide.jimtof2026.data.NoveltyInfo
import jp.fairguide.jimtof2026.data.NoveltyKey
import jp.fairguide.jimtof2026.data.NoveltyNoteStore
import jp.fairguide.jimtof2026.data.NoveltyRepository
import jp.fairguide.jimtof2026.data.MyListStore
import jp.fairguide.jimtof2026.data.ReminderStore
import jp.fairguide.jimtof2026.data.ReportData
import jp.fairguide.jimtof2026.data.RouteSettings
import jp.fairguide.jimtof2026.logic.RouteStop
import jp.fairguide.jimtof2026.logic.BoothChecklist
import jp.fairguide.jimtof2026.logic.BoothCode
import jp.fairguide.jimtof2026.data.ReportExtra
import jp.fairguide.jimtof2026.data.ReportNoteStore
import jp.fairguide.jimtof2026.data.ReportNotes
import jp.fairguide.jimtof2026.data.ReportRepository
import jp.fairguide.jimtof2026.logic.ExhibitorHit
import jp.fairguide.jimtof2026.logic.ExhibitorIndex
import jp.fairguide.jimtof2026.logic.FAIR_ZONE
import jp.fairguide.jimtof2026.logic.ReportPreset
import jp.fairguide.jimtof2026.notify.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZonedDateTime

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    val data: FairData = FairRepository.get(application)

    private val listStore = MyListStore(application)
    private val reminderStore = ReminderStore(application)
    private val noveltyNoteStore = NoveltyNoteStore(application)
    private val reportNoteStore = ReportNoteStore(application)

    var tab by mutableStateOf(AppTab.OVERVIEW)
        private set

    var scheduleDate by mutableStateOf<LocalDate?>(null)
        private set

    var now by mutableStateOf(ZonedDateTime.now(FAIR_ZONE))
        private set

    var myList by mutableStateOf(listStore.load())
        private set

    var reminders by mutableStateOf(reminderStore.load())
        private set

    // ---- 出展社検索 ----

    /** 出展社データ（1,100社あまり）は最初に検索タブを開いたときに裏で読む */
    var exhibitorIndex by mutableStateOf<ExhibitorIndex?>(null)
        private set

    var searchQuery by mutableStateOf("")
        private set

    /** 「ノベルティあり」の絞り込み */
    var noveltyOnly by mutableStateOf(false)
        private set

    /** 見学レポートの項目に合わせた候補の絞り込み（なければ null） */
    var reportPreset by mutableStateOf<ReportPreset?>(null)
        private set

    /** 公開情報から確認できたノベルティ（出展社データと一緒に読む） */
    var noveltyData by mutableStateOf<NoveltyData?>(null)
        private set

    /** 自分で「ノベルティあり」と記録した会社（NoveltyKey → メモ）。この端末の中だけ */
    var userNovelty by mutableStateOf(noveltyNoteStore.load())
        private set

    var searchHits by mutableStateOf<List<ExhibitorHit>>(emptyList())
        private set

    /** 検索結果から「地図で見る」を押したホール（会場タブで強調する） */
    var focusHallId by mutableStateOf<String?>(null)
        private set

    private var searchJob: Job? = null
    private var loadingIndex = false

    init {
        // 「開幕まであと◯日」「閉場まであと◯分」を更新し続ける
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                now = ZonedDateTime.now(FAIR_ZONE)
            }
        }
    }

    fun selectTab(target: AppTab) {
        tab = target
        if (target == AppTab.VENUE) ensureExhibitors()
        if (target == AppTab.REPORT) ensureReport()
    }

    // ---- 見学レポート ----

    /** おすすめの会社と見どころ（report.json）。レポートタブを最初に開いたときに読む */
    var reportData by mutableStateOf<ReportData?>(null)
        private set

    /** 会場で書いたメモ・まとめ・自分で足した会社（端末内だけに保存） */
    var reportNotes by mutableStateOf(reportNoteStore.load())
        private set

    /** レポートタブで開いている項目 */
    var reportItemId by mutableStateOf("1")
        private set

    private var loadingReport = false

    fun ensureReport() {
        if (reportData != null || loadingReport) return
        loadingReport = true
        viewModelScope.launch {
            reportData = withContext(Dispatchers.Default) { ReportRepository.get(app) }
            loadingReport = false
        }
    }

    fun selectReportItem(id: String) {
        reportItemId = id
    }

    private fun saveReportNotes(next: ReportNotes) {
        reportNotes = next
        reportNoteStore.save(next)
    }

    /** 会社ごとのメモ。null か空なら消す */
    fun setReportNote(itemId: String, companyKey: String, note: String?) {
        val k = ReportNotes.noteKey(itemId, companyKey)
        val notes = reportNotes.notes.toMutableMap()
        if (note.isNullOrBlank()) notes.remove(k) else notes[k] = note.trim()
        saveReportNotes(reportNotes.copy(notes = notes))
    }

    fun setReportSummary(itemId: String, text: String) {
        val s = reportNotes.summaries.toMutableMap()
        if (text.isBlank()) s.remove(itemId) else s[itemId] = text
        saveReportNotes(reportNotes.copy(summaries = s))
    }

    /** 検索結果などから、項目に会社を足す。すでにあれば false */
    fun addReportCompany(itemId: String, exhibitor: Exhibitor): Boolean {
        val booth = exhibitor.booths.first()
        val key = NoveltyKey.of(exhibitor.name, booth)
        val recommended = reportData?.item(itemId)?.companies?.any { it.key == key } == true
        val exists = reportNotes.extras.any { it.itemId == itemId && it.key == key }
        if (recommended || exists) return false
        saveReportNotes(reportNotes.copy(extras = reportNotes.extras + ReportExtra(itemId, exhibitor.name, booth)))
        return true
    }

    fun removeReportCompany(itemId: String, companyKey: String) {
        val notes = reportNotes.notes.toMutableMap()
        notes.remove(ReportNotes.noteKey(itemId, companyKey))
        saveReportNotes(
            reportNotes.copy(
                notes = notes,
                extras = reportNotes.extras.filterNot { it.itemId == itemId && it.key == companyKey },
            ),
        )
    }

    // ---- 見学ルート ----

    fun updateRoute(transform: (RouteSettings) -> RouteSettings) {
        saveReportNotes(reportNotes.copy(route = transform(reportNotes.route)))
    }

    fun toggleRouteItem(itemId: String) {
        updateRoute { r ->
            val ids = r.itemIds.toMutableSet()
            if (!ids.remove(itemId)) ids += itemId
            r.copy(itemIds = ids)
        }
    }

    /** ルートから外す／戻す */
    fun toggleRouteSkip(key: String) {
        val skips = reportNotes.routeSkips.toMutableSet()
        if (!skips.remove(key)) skips += key
        saveReportNotes(reportNotes.copy(routeSkips = skips))
    }

    fun clearRouteSkips() {
        saveReportNotes(reportNotes.copy(routeSkips = emptySet()))
    }

    /** ルートの順にブースリストへ入れる（登録順＝ルート順になる）。戻り値は新しく入れた数 */
    fun addRouteToMyList(stops: List<RouteStop>): Int {
        var added = 0
        val list = myList.toMutableList()
        var id = maxOf(System.currentTimeMillis(), (list.maxOfOrNull { it.id } ?: 0L) + 1)
        for (s in stops) {
            val exists = list.any { it.company == s.name && BoothCode.normalize(it.booth) == BoothCode.normalize(s.booth) }
            if (exists) continue
            list += BoothItem(
                id = id, company = s.name, booth = s.booth, memo = "", visited = false, createdAt = id,
                checks = BoothChecklist.merge(emptyList(), s.checks),
            )
            id++
            added++
        }
        if (added > 0) {
            myList = list
            listStore.save(list)
        }
        return added
    }

    /** 検索タブを、その項目の候補（ReportPreset）で開く */
    fun openSearchWithPreset(preset: ReportPreset?) {
        resetSearch()
        if (preset != null) selectReportPreset(preset)
        tab = AppTab.VENUE
        ensureExhibitors()
    }

    fun ensureExhibitors() {
        if (exhibitorIndex != null || loadingIndex) return
        loadingIndex = true
        viewModelScope.launch {
            val (index, novelty) = withContext(Dispatchers.Default) {
                val nov = NoveltyRepository.get(app)
                ExhibitorIndex(ExhibitorRepository.get(app), nov) to nov
            }
            noveltyData = novelty
            exhibitorIndex = index
            loadingIndex = false
            if (hasCriteria()) runSearch(searchQuery)
        }
    }

    fun updateSearch(query: String) {
        searchQuery = query
        ensureExhibitors()
        searchJob?.cancel()
        if (query.isBlank() && !noveltyOnly && reportPreset == null) {
            searchHits = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(120) // 入力が落ち着くまで少し待つ
            runSearch(query)
        }
    }

    fun toggleNoveltyOnly(on: Boolean) {
        noveltyOnly = on
        ensureExhibitors()
        searchJob?.cancel()
        if (!hasCriteria()) {
            searchHits = emptyList()
            return
        }
        searchJob = viewModelScope.launch { runSearch(searchQuery) }
    }

    /** 見学テーマを選ぶ。同じものをもう一度押すと外れる */
    fun selectReportPreset(preset: ReportPreset?) {
        reportPreset = if (preset == reportPreset) null else preset
        ensureExhibitors()
        searchJob?.cancel()
        if (!hasCriteria()) {
            searchHits = emptyList()
            return
        }
        searchJob = viewModelScope.launch { runSearch(searchQuery) }
    }

    private fun hasCriteria(): Boolean = searchQuery.isNotBlank() || noveltyOnly || reportPreset != null

    private suspend fun runSearch(query: String) {
        val index = exhibitorIndex ?: return
        val only = noveltyOnly
        val preset = reportPreset
        // 検索は別スレッドで走るので、いまの状態を先に控えてから渡す
        val official = noveltyData?.byExhibitor.orEmpty()
        val mine = userNovelty
        val filter: ((Exhibitor) -> Boolean)? = if (only) {
            { ex -> NoveltyKey.of(ex).let { it in official || it in mine } }
        } else {
            null
        }
        val hits = withContext(Dispatchers.Default) { index.search(query, limit = Int.MAX_VALUE, filter = filter, preset = preset) }
        if (query == searchQuery && only == noveltyOnly && preset == reportPreset) searchHits = hits
    }

    /** 検索語だけ消す（絞り込みは残る） */
    fun clearSearch() = updateSearch("")

    /** 検索語も絞り込みも消して、会場マップの表示に戻す */
    fun resetSearch() {
        noveltyOnly = false
        reportPreset = null
        updateSearch("")
    }

    // ---- ノベルティ ----

    /** この会社のノベルティ（公開情報）。なければ空 */
    fun noveltyOf(exhibitor: Exhibitor): List<NoveltyInfo> = noveltyData?.infoFor(exhibitor).orEmpty()

    /** 自分用のメモ。記録していなければ null（空文字は「ノベルティあり」だけ記録した状態） */
    fun noveltyNoteOf(exhibitor: Exhibitor): String? = userNovelty[NoveltyKey.of(exhibitor)]

    fun hasNovelty(exhibitor: Exhibitor): Boolean =
        noveltyOf(exhibitor).isNotEmpty() || noveltyNoteOf(exhibitor) != null

    /** 会場で見つけたノベルティを記録する。note が null なら記録を外す */
    fun setNoveltyNote(exhibitor: Exhibitor, note: String?) {
        val key = NoveltyKey.of(exhibitor)
        val next = userNovelty.toMutableMap()
        if (note == null) next.remove(key) else next[key] = note.trim()
        userNovelty = next
        noveltyNoteStore.save(next)
        // 絞り込み中に記録を外した会社は、一覧からも外す
        if (noveltyOnly) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch(searchQuery) }
        }
    }

    fun focusHall(hallId: String?) {
        focusHallId = hallId
    }

    /** 検索結果をマイリストに入れる。同じ会社・小間がすでにあれば追加しない */
    fun addExhibitorToMyList(exhibitor: Exhibitor, booth: String): Boolean {
        val exists = myList.any { it.company == exhibitor.name && it.booth.equals(booth, ignoreCase = true) }
        if (exists) return false
        val now = nextItemId()
        saveItem(
            BoothItem(
                id = now,
                company = exhibitor.name,
                booth = booth,
                memo = "",
                visited = false,
                createdAt = now,
                checks = BoothChecklist.merge(emptyList(), reportChecksFor(exhibitor.name, booth)),
            ),
        )
        return true
    }

    /** 出展社データを読む前でも、社名と小間番号だけでブースリストに入れる */
    fun addToMyListByName(company: String, booth: String): Boolean {
        val exists = myList.any { it.company == company && it.booth.equals(booth, ignoreCase = true) }
        if (exists) return false
        val now = nextItemId()
        saveItem(
            BoothItem(
                id = now, company = company, booth = booth, memo = "", visited = false, createdAt = now,
                checks = BoothChecklist.merge(emptyList(), reportChecksFor(company, booth)),
            ),
        )
        return true
    }

    /** 同じ瞬間に2件入れても番号がぶつからないようにする */
    private fun nextItemId(): Long = maxOf(System.currentTimeMillis(), (myList.maxOfOrNull { it.id } ?: 0L) + 1)

    /** 画面の確認用：レポート項目の先頭から数社をブースリストに入れる */
    fun addFirstReportCompanies(itemId: String, count: Int) {
        val data = reportData ?: runCatching { ReportRepository.get(app) }.getOrNull() ?: return
        data.item(itemId)?.companies?.take(count)?.forEach { addToMyListByName(it.name, it.booth) }
    }

    /** 見学レポートのおすすめにある「ブースで確かめること」（その会社がおすすめに無ければ空） */
    fun reportChecksFor(company: String, booth: String): List<String> =
        BoothChecklist.fromReport(reportData ?: runCatching { ReportRepository.get(app) }.getOrNull(), company, booth)

    fun isInMyList(exhibitor: Exhibitor, booth: String): Boolean =
        myList.any { it.company == exhibitor.name && it.booth.equals(booth, ignoreCase = true) }

    // ---- 予定 ----

    fun pickScheduleDate(date: LocalDate) {
        scheduleDate = date
    }

    fun selectScheduleDate(iso: String) {
        scheduleDate = runCatching { LocalDate.parse(iso) }.getOrNull()
    }

    /** 画面に戻ってきたとき：時刻・リマインダーの状態を最新にする */
    fun refresh() {
        now = ZonedDateTime.now(FAIR_ZONE)
        ReminderScheduler.rescheduleAll(app)
        reminders = reminderStore.load()
    }

    // ---- マイリスト ----

    fun saveItem(item: BoothItem) {
        val list = myList.toMutableList()
        val index = list.indexOfFirst { it.id == item.id }
        if (index >= 0) list[index] = item else list.add(item)
        myList = list
        listStore.save(list)
    }

    fun deleteItem(id: Long) {
        myList = myList.filterNot { it.id == id }
        listStore.save(myList)
    }

    /** チェックリストの項目を1つ付ける／外す */
    fun toggleCheck(id: Long, index: Int) {
        myList = myList.map { if (it.id == id) it.copy(checks = BoothChecklist.toggle(it.checks, index)) else it }
        listStore.save(myList)
    }

    fun toggleVisited(id: Long) {
        myList = myList.map { if (it.id == id) it.copy(visited = !it.visited) else it }
        listStore.save(myList)
    }

    // ---- 通知 ----

    fun enableReminder(event: FairEvent): ReminderScheduler.Result {
        val result = ReminderScheduler.schedule(app, event)
        if (result.scheduled) {
            reminderStore.add(event.id)
            reminders = reminderStore.load()
        }
        return result
    }

    fun disableReminder(event: FairEvent) {
        ReminderScheduler.cancel(app, event.id)
        reminderStore.remove(event.id)
        reminders = reminderStore.load()
    }
}
