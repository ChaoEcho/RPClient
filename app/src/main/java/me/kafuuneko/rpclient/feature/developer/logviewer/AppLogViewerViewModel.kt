package me.kafuuneko.rpclient.feature.developer.logviewer

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.developer.logviewer.presentation.AppLogViewerUiIntent
import me.kafuuneko.rpclient.feature.developer.logviewer.presentation.AppLogViewerUiState
import me.kafuuneko.rpclient.feature.developer.logviewer.presentation.AppLogViewerViewEvent
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.debug.AppLogEntry
import me.kafuuneko.rpclient.libs.debug.AppLogLevel
import me.kafuuneko.rpclient.libs.debug.AppLogStore

@OptIn(FlowPreview::class)
class AppLogViewerViewModel : CoreViewModelWithEvent<
    AppLogViewerUiIntent,
    AppLogViewerUiState
>(AppLogViewerUiState.None) {

    @UiIntentObserver(AppLogViewerUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<AppLogViewerUiState.None>()) return

        val initialLogs = AppLogStore.snapshot()
        val state = AppLogViewerUiState.Normal(
            allLogs = initialLogs,
            filteredLogs = filterLogs(initialLogs, "", null),
            searchQuery = "",
            selectedLevel = null,
            isClearDialogOpen = false
        )
        state.setup()

        // 只订阅版本号，页面可见时才按需快照；写入路径不再为每条日志拷贝整个缓冲区。
        viewModelScope.launch {
            AppLogStore.revision
                .sample(LOG_REFRESH_INTERVAL_MS)
                .collectLatest {
                    val current = getOrNull<AppLogViewerUiState.Normal>() ?: return@collectLatest
                    val newLogs = AppLogStore.snapshot()
                    current.copy(
                        allLogs = newLogs,
                        filteredLogs = filterLogs(newLogs, current.searchQuery, current.selectedLevel)
                    ).setup()
                }
        }
    }

    @UiIntentObserver(AppLogViewerUiIntent.Back::class)
    private fun onBack() {
        val state = uiStateFlow.value
        if (state is AppLogViewerUiState.Finished) return
        AppLogViewerUiState.Finished(state).setup()
    }

    @UiIntentObserver(AppLogViewerUiIntent.ChangeSearchQuery::class)
    private fun onChangeSearchQuery(intent: AppLogViewerUiIntent.ChangeSearchQuery) {
        val state = getOrNull<AppLogViewerUiState.Normal>() ?: return
        val filtered = filterLogs(state.allLogs, intent.query, state.selectedLevel)
        state.copy(
            searchQuery = intent.query,
            filteredLogs = filtered
        ).setup()
    }

    @UiIntentObserver(AppLogViewerUiIntent.SelectLevelFilter::class)
    private fun onSelectLevelFilter(intent: AppLogViewerUiIntent.SelectLevelFilter) {
        val state = getOrNull<AppLogViewerUiState.Normal>() ?: return
        val filtered = filterLogs(state.allLogs, state.searchQuery, intent.level)
        state.copy(
            selectedLevel = intent.level,
            filteredLogs = filtered
        ).setup()
    }

    @UiIntentObserver(AppLogViewerUiIntent.RequestClearLogs::class)
    private fun onRequestClearLogs() {
        val state = getOrNull<AppLogViewerUiState.Normal>() ?: return
        state.copy(isClearDialogOpen = true).setup()
    }

    @UiIntentObserver(AppLogViewerUiIntent.DismissClearDialog::class)
    private fun onDismissClearDialog() {
        val state = getOrNull<AppLogViewerUiState.Normal>() ?: return
        state.copy(isClearDialogOpen = false).setup()
    }

    @UiIntentObserver(AppLogViewerUiIntent.ConfirmClearLogs::class)
    private fun onConfirmClearLogs() {
        val state = getOrNull<AppLogViewerUiState.Normal>() ?: return
        AppLogStore.clear()
        state.copy(
            allLogs = emptyList(),
            filteredLogs = emptyList(),
            isClearDialogOpen = false
        ).setup()
        AppViewEvent.PopupToastMessageByResId(R.string.logs_cleared).tryEmit()
    }

    @UiIntentObserver(AppLogViewerUiIntent.ExportLogs::class)
    private fun onExportLogs() {
        val formatted = AppLogStore.exportFormattedLogs()
        if (formatted.isNotBlank()) {
            AppLogViewerViewEvent.CopyText(formatted).tryEmit()
        } else {
            AppViewEvent.PopupToastMessageByResId(R.string.no_logs_title).tryEmit()
        }
    }

    private fun filterLogs(
        logs: List<AppLogEntry>,
        query: String,
        level: AppLogLevel?
    ): List<AppLogEntry> {
        val trimmedQuery = query.trim()
        return logs.filter { entry ->
            val matchesLevel = level == null || entry.level == level
            val matchesQuery = trimmedQuery.isEmpty() ||
                entry.message.contains(trimmedQuery, ignoreCase = true) ||
                entry.module.contains(trimmedQuery, ignoreCase = true) ||
                (entry.throwableSummary != null && entry.throwableSummary.contains(trimmedQuery, ignoreCase = true))
            matchesLevel && matchesQuery
        }.asReversed() // Newest logs first
    }

    private companion object {
        /** 日志刷新节流；生成过程中日志很密集，逐条重建列表没有意义。 */
        const val LOG_REFRESH_INTERVAL_MS = 300L
    }
}
