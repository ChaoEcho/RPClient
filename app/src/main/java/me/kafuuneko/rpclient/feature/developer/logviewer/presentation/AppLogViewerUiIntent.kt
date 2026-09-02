package me.kafuuneko.rpclient.feature.developer.logviewer.presentation

import me.kafuuneko.rpclient.libs.debug.AppLogLevel

sealed class AppLogViewerUiIntent {
    data object Init : AppLogViewerUiIntent()
    data object Back : AppLogViewerUiIntent()
    data class ChangeSearchQuery(val query: String) : AppLogViewerUiIntent()
    data class SelectLevelFilter(val level: AppLogLevel?) : AppLogViewerUiIntent()
    data object RequestClearLogs : AppLogViewerUiIntent()
    data object ConfirmClearLogs : AppLogViewerUiIntent()
    data object DismissClearDialog : AppLogViewerUiIntent()
    data object ExportLogs : AppLogViewerUiIntent()
}
