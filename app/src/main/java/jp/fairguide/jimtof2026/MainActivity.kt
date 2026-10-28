package jp.fairguide.jimtof2026

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import jp.fairguide.jimtof2026.notify.Notifications
import jp.fairguide.jimtof2026.logic.ReportPreset
import jp.fairguide.jimtof2026.ui.AppTab
import jp.fairguide.jimtof2026.ui.AppViewModel
import jp.fairguide.jimtof2026.ui.GuideApp
import jp.fairguide.jimtof2026.ui.theme.GuideTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Notifications.ensureChannel(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            GuideTheme {
                GuideApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    /** 通知から開いたときは、その日の予定タブを表示する */
    private fun handleIntent(intent: Intent?) {
        AppTab.fromKey(intent?.getStringExtra(EXTRA_OPEN_TAB))?.let { viewModel.selectTab(it) }
        intent?.getStringExtra(EXTRA_EVENT_DATE)?.let { viewModel.selectScheduleDate(it) }
        intent?.getStringExtra(EXTRA_SEARCH)?.let {
            viewModel.selectTab(AppTab.VENUE)
            viewModel.resetSearch()
            viewModel.updateSearch(it)
        }
        // 画面の確認用：絞り込みを開いた状態で起動する
        intent?.getStringExtra(EXTRA_NOVELTY)?.let {
            viewModel.selectTab(AppTab.VENUE)
            viewModel.resetSearch()
            viewModel.toggleNoveltyOnly(true)
        }
        intent?.getStringExtra(EXTRA_REPORT_ITEM)?.let { id ->
            viewModel.selectTab(AppTab.REPORT)
            viewModel.selectReportItem(id)
        }
        intent?.getStringExtra(EXTRA_PRESET)?.let { name ->
            ReportPreset.entries.firstOrNull { it.name == name }?.let { preset ->
                viewModel.selectTab(AppTab.VENUE)
                viewModel.resetSearch()
                viewModel.selectReportPreset(preset)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_EVENT_DATE = "event_date"
        const val EXTRA_SEARCH = "search_query"
        const val EXTRA_NOVELTY = "novelty_only"
        const val EXTRA_PRESET = "report_preset"
        const val EXTRA_REPORT_ITEM = "report_item"
    }
}
