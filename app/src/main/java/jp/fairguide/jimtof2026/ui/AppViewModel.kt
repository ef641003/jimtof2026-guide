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
import jp.fairguide.jimtof2026.logic.ExhibitorHit
import jp.fairguide.jimtof2026.logic.ExhibitorIndex
import jp.fairguide.jimtof2026.logic.FAIR_ZONE
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
            if (searchQuery.isNotBlank() || noveltyOnly) runSearch(searchQuery)
        }
    }

    fun updateSearch(query: String) {
        searchQuery = query
        ensureExhibitors()
        searchJob?.cancel()
        if (query.isBlank() && !noveltyOnly) {
            searchHits = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(120) // 入力が落ち着くまで少し待つ
            runSearch(query)
        }
    }

    fun setNoveltyOnly(on: Boolean) {
        noveltyOnly = on
        ensureExhibitors()
        searchJob?.cancel()
        if (searchQuery.isBlank() && !on) {
            searchHits = emptyList()
            return
        }
        searchJob = viewModelScope.launch { runSearch(searchQuery) }
    }

    private suspend fun runSearch(query: String) {
        val index = exhibitorIndex ?: return
        val only = noveltyOnly
        // 検索は別スレッドで走るので、いまの状態を先に控えてから渡す
        val official = noveltyData?.byExhibitor.orEmpty()
        val mine = userNovelty
        val filter: ((Exhibitor) -> Boolean)? = if (only) {
            { ex -> NoveltyKey.of(ex).let { it in official || it in mine } }
        } else {
            null
        }
        val hits = withContext(Dispatchers.Default) { index.search(query, limit = Int.MAX_VALUE, filter = filter) }
        if (query == searchQuery && only == noveltyOnly) searchHits = hits
    }

    /** 検索語だけ消す（絞り込みは残る） */
    fun clearSearch() = updateSearch("")

    /** 検索語も絞り込みも消して、会場マップの表示に戻す */
    fun resetSearch() {
        noveltyOnly = false
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
        val now = System.currentTimeMillis()
        saveItem(
            BoothItem(
                id = now,
                company = exhibitor.name,
                booth = booth,
                memo = "",
                visited = false,
                createdAt = now,
            ),
        )
        return true
    }

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
