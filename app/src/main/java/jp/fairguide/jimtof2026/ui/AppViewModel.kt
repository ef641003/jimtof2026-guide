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
            val index = withContext(Dispatchers.Default) { ExhibitorIndex(ExhibitorRepository.get(app)) }
            exhibitorIndex = index
            loadingIndex = false
            if (searchQuery.isNotBlank()) runSearch(searchQuery)
        }
    }

    fun updateSearch(query: String) {
        searchQuery = query
        ensureExhibitors()
        searchJob?.cancel()
        if (query.isBlank()) {
            searchHits = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(120) // 入力が落ち着くまで少し待つ
            runSearch(query)
        }
    }

    private suspend fun runSearch(query: String) {
        val index = exhibitorIndex ?: return
        val hits = withContext(Dispatchers.Default) { index.search(query, limit = Int.MAX_VALUE) }
        if (query == searchQuery) searchHits = hits
    }

    fun clearSearch() = updateSearch("")

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
