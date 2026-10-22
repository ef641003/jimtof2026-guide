package jp.fairguide.jimtof2026.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.fairguide.jimtof2026.data.BoothItem
import jp.fairguide.jimtof2026.data.FairData
import jp.fairguide.jimtof2026.data.FairEvent
import jp.fairguide.jimtof2026.data.FairRepository
import jp.fairguide.jimtof2026.data.MyListStore
import jp.fairguide.jimtof2026.data.ReminderStore
import jp.fairguide.jimtof2026.logic.FAIR_ZONE
import jp.fairguide.jimtof2026.notify.ReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    }

    fun setScheduleDate(date: LocalDate) {
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
