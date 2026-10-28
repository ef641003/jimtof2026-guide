package jp.fairguide.jimtof2026.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

enum class AppTab(
    val key: String,
    val label: String,
    val title: String,
    val icon: ImageVector,
    /** 下のタブバーに出すか（リンクは概要タブの中から開く） */
    val inNavBar: Boolean = true,
) {
    OVERVIEW("overview", "概要", "会期・入場", Icons.Filled.Home),
    VENUE("halls", "検索", "出展社をさがす・会場マップ", Icons.Filled.Search),
    SCHEDULE("schedule", "予定", "講演・ワークショップ", Icons.Filled.DateRange),
    MY_LIST("mylist", "ブース", "回りたいブース", Icons.Filled.Star),
    REPORT("report", "レポート", "見学レポート（11/5 提出）", Icons.Filled.Create),
    LINKS("links", "リンク", "アクセス・公式リンク", Icons.Filled.Info, inNavBar = false);

    companion object {
        fun fromKey(key: String?): AppTab? = entries.firstOrNull { it.key == key }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideApp(vm: AppViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val openUrl: (String) -> Unit = { url -> openLink(context, url) }

    // 戻るボタンは、まず概要タブへ戻す
    BackHandler(enabled = vm.tab != AppTab.OVERVIEW) {
        vm.selectTab(AppTab.OVERVIEW)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text(
                            text = vm.tab.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "JIMTOF2026 非公式ガイド",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                AppTab.entries.filter { it.inNavBar }.forEach { item ->
                    NavigationBarItem(
                        selected = vm.tab == item,
                        onClick = { vm.selectTab(item) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (vm.tab) {
                AppTab.OVERVIEW -> OverviewScreen(
                    data = vm.data,
                    now = vm.now,
                    onOpenSchedule = { vm.selectTab(AppTab.SCHEDULE) },
                    onOpenSearch = { vm.selectTab(AppTab.VENUE) },
                    onOpenLinks = { vm.selectTab(AppTab.LINKS) },
                    onOpenUrl = openUrl,
                )
                AppTab.VENUE -> VenueScreen(vm = vm, snackbarHostState = snackbarHostState, onOpenUrl = openUrl)
                AppTab.SCHEDULE -> ScheduleScreen(vm = vm, snackbarHostState = snackbarHostState)
                AppTab.MY_LIST -> MyListScreen(vm = vm, onOpenSearch = { vm.selectTab(AppTab.VENUE) })
                AppTab.REPORT -> ReportScreen(vm = vm, snackbarHostState = snackbarHostState, onOpenUrl = openUrl)
                AppTab.LINKS -> LinksScreen(data = vm.data, onOpenUrl = openUrl)
            }
        }
    }
}

/** URL・電話・メールを、それぞれ対応するアプリで開く */
fun openLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    val intent = when (uri.scheme) {
        "tel" -> Intent(Intent.ACTION_DIAL, uri)
        "mailto" -> Intent(Intent.ACTION_SENDTO, uri)
        else -> Intent(Intent.ACTION_VIEW, uri)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "開けるアプリが見つかりません", Toast.LENGTH_SHORT).show()
    }
}

/** 「アラームとリマインダー」の許可画面（Android 12以降）を開く */
fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}"),
        )
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "設定画面を開けませんでした", Toast.LENGTH_SHORT).show()
        }
    }
}
