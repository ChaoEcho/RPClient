package me.kafuuneko.rpclient.feature.developer.logviewer.presentation

import me.kafuuneko.rpclient.libs.debug.AppLogEntry
import me.kafuuneko.rpclient.libs.debug.AppLogLevel

sealed class AppLogViewerUiState {
    data object None : AppLogViewerUiState()

    data class Normal(
        val allLogs: List<AppLogEntry> = emptyList(),
        val filteredLogs: List<AppLogEntry> = emptyList(),
        val searchQuery: String = "",
        val selectedLevel: AppLogLevel? = null,
        val isClearDialogOpen: Boolean = false
    ) : AppLogViewerUiState()

    data class Finished(
        val previous: AppLogViewerUiState
    ) : AppLogViewerUiState()
}
