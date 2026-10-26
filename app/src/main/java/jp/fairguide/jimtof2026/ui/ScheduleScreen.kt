package jp.fairguide.jimtof2026.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import jp.fairguide.jimtof2026.data.EventCategory
import jp.fairguide.jimtof2026.data.FairEvent
import jp.fairguide.jimtof2026.logic.FAIR_ZONE
import jp.fairguide.jimtof2026.logic.hm
import jp.fairguide.jimtof2026.logic.jpShort
import jp.fairguide.jimtof2026.notify.ReminderScheduler
import jp.fairguide.jimtof2026.ui.components.BulletText
import jp.fairguide.jimtof2026.ui.components.EmptyState
import jp.fairguide.jimtof2026.ui.components.GuideCard
import jp.fairguide.jimtof2026.ui.components.Tag
import jp.fairguide.jimtof2026.ui.components.placeColorForPlaceName
import jp.fairguide.jimtof2026.ui.theme.GuideTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ScheduleScreen(vm: AppViewModel, snackbarHostState: SnackbarHostState) {
    val data = vm.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dates = remember(data) { data.events.map { it.date }.distinct().sorted() }
    val today = vm.now.withZoneSameInstant(FAIR_ZONE).toLocalDate()
    val nowTime = vm.now.withZoneSameInstant(FAIR_ZONE).toLocalTime()
    val selectedDate = vm.scheduleDate?.takeIf { it in dates } ?: (if (today in dates) today else dates.first())
    var categoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var notesExpanded by rememberSaveable { mutableStateOf(false) }

    val events = remember(data, selectedDate, categoryKey) {
        data.events
            .filter { it.date == selectedDate && (categoryKey == null || it.category.key == categoryKey) }
            .sortedWith(compareBy<FairEvent>({ it.start == null }, { it.start }, { it.category.ordinal }, { it.place }))
    }

    var pendingEvent by remember { mutableStateOf<FairEvent?>(null) }

    fun announce(result: ReminderScheduler.Result, event: FairEvent) {
        scope.launch {
            when (result) {
                ReminderScheduler.Result.EXACT -> snackbarHostState.showSnackbar(
                    "${event.start?.hm() ?: ""} 開始の${ReminderScheduler.LEAD_MINUTES}分前に通知します",
                )
                ReminderScheduler.Result.INEXACT -> {
                    val r = snackbarHostState.showSnackbar(
                        message = "開始${ReminderScheduler.LEAD_MINUTES}分前ごろに通知します（数分ずれることがあります）",
                        actionLabel = "正確にする",
                        duration = SnackbarDuration.Long,
                    )
                    if (r == SnackbarResult.ActionPerformed) openExactAlarmSettings(context)
                }
                ReminderScheduler.Result.PASSED -> snackbarHostState.showSnackbar("開始時刻を過ぎています")
                ReminderScheduler.Result.NO_TIME -> snackbarHostState.showSnackbar("開始時刻が未定のため通知できません")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val event = pendingEvent ?: return@rememberLauncherForActivityResult
        pendingEvent = null
        val result = vm.enableReminder(event)
        if (granted) {
            announce(result, event)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("通知が許可されていないため、通知は表示されません")
            }
        }
    }

    val onBell: (FairEvent) -> Unit = { event ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        when {
            event.id in vm.reminders -> {
                vm.disableReminder(event)
                scope.launch { snackbarHostState.showSnackbar("通知を解除しました") }
            }
            needsPermission -> {
                pendingEvent = event
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> announce(vm.enableReminder(event), event)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "dates") {
            DaySelector(
                dates = dates,
                selected = selectedDate,
                today = today,
                lastFairDay = data.days.last().date,
                onSelect = vm::pickScheduleDate,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = categoryKey == null,
                        onClick = { categoryKey = null },
                        label = { Text("すべて") },
                    )
                }
                items(EventCategory.entries) { category ->
                    FilterChip(
                        selected = categoryKey == category.key,
                        onClick = { categoryKey = category.key },
                        label = { Text(category.display) },
                    )
                }
            }
        }
        item(key = "notes") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${selectedDate.jpShort()}の予定",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${events.size}件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selectedDate.isAfter(data.days.last().date)) {
                    Text(
                        text = "会期の翌日の予定です",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { notesExpanded = !notesExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ベルを押すと開始${ReminderScheduler.LEAD_MINUTES}分前に通知。講演は事前申込制です",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (notesExpanded) "閉じる" else "注意事項",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (notesExpanded) {
                    GuideCard(Modifier.padding(top = 4.dp)) {
                        data.scheduleNotes.forEach { BulletText(it) }
                    }
                }
            }
        }
        if (events.isEmpty()) {
            item(key = "empty") {
                EmptyState("この日の予定はありません", "別の日か、ほかの種類を選んでください")
            }
        }
        items(events, key = { it.id }) { event ->
            val startAt = event.startDateTime()
            val upcoming = startAt != null && startAt.isAfter(vm.now)
            val isToday = selectedDate == today
            val ended = isToday && event.start != null && (event.end ?: event.start.plusMinutes(60)).isBefore(nowTime)
            val ongoing = isToday && event.start != null && !ended && !event.start.isAfter(nowTime)
            EventCard(
                event = event,
                reminderOn = event.id in vm.reminders,
                canRemind = upcoming,
                ended = ended,
                ongoing = ongoing,
                onBell = { onBell(event) },
                onOpen = { event.url?.let { openLink(context, it) } },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
            )
        }
    }
}

/** 会期の各日を1列に並べる。日付の数字を大きく、曜日を添える */
@Composable
private fun DaySelector(
    dates: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    lastFairDay: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        dates.forEach { date ->
            val isSelected = date == selected
            val isToday = date == today
            val outside = date.isAfter(lastFairDay)
            val container = when {
                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                else -> GuideTheme.colors.card
            }
            val content = when {
                isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                outside -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }
            Surface(
                onClick = { onSelect(date) },
                modifier = Modifier
                    .weight(1f)
                    .height(66.dp),
                color = container,
                contentColor = content,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    if (isToday) 2.dp else 1.dp,
                    if (isToday) MaterialTheme.colorScheme.secondary else GuideTheme.colors.cardBorder,
                ),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPANESE),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Text(
                        text = "${date.dayOfMonth}",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    val note = when {
                        isToday -> "今日"
                        date.monthValue != dates.first().monthValue -> "${date.monthValue}月"
                        else -> null
                    }
                    if (note != null) {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: FairEvent,
    reminderOn: Boolean,
    canRemind: Boolean,
    ended: Boolean,
    ongoing: Boolean,
    onBell: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (tagContainer, tagContent) = categoryColors(event.category)
    val place = placeColorForPlaceName(event.place)
    GuideCard(
        modifier = modifier.alpha(if (ended) 0.55f else 1f),
        onClick = onOpen,
        accent = place.strong,
        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
    ) {
        Row {
            // 時刻の列：縦にそろえて、上から下へ流し読みできるようにする
            Column(Modifier.width(64.dp)) {
                Text(
                    text = event.start?.hm() ?: "未定",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                event.end?.let {
                    Text(
                        text = "〜${it.hm()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ongoing) {
                    Tag("開催中", GuideTheme.colors.good, modifier = Modifier.padding(top = 4.dp), bold = true)
                } else if (ended) {
                    Text("終了", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f)) {
                Tag(event.label, tagContainer, tagContent)
                Spacer(Modifier.height(4.dp))
                Text(text = event.title, style = MaterialTheme.typography.titleSmall)
                if (event.speaker.isNotBlank()) {
                    Text(
                        text = event.speaker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(place.strong, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = event.place,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (event.entry.isNotBlank()) {
                    Text(
                        text = event.entry,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (event.start != null) {
                IconButton(onClick = onBell, enabled = canRemind || reminderOn, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (reminderOn) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = if (reminderOn) "通知を解除" else "開始15分前に通知",
                        tint = if (reminderOn) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun categoryColors(category: EventCategory): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (category) {
        EventCategory.TALK -> cs.primaryContainer to cs.onPrimaryContainer
        EventCategory.WORKSHOP -> cs.secondaryContainer to cs.onSecondaryContainer
        EventCategory.ASSOCIATION -> cs.tertiaryContainer to cs.onTertiaryContainer
        EventCategory.STUDENT -> cs.surfaceContainerHigh to cs.onSurface
    }
}
