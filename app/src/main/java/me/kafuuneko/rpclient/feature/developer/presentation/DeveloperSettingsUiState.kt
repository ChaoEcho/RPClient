package me.kafuuneko.rpclient.feature.developer.presentation

sealed class DeveloperSettingsUiState {
    data object None : DeveloperSettingsUiState()

    data class Normal(
        val developerLoggingEnabled: Boolean,
        val debugModeEnabled: Boolean,
        val runtimeStatus: DeveloperRuntimeStatus
    ) : DeveloperSettingsUiState()

    data class Finished(
        val previous: DeveloperSettingsUiState
    ) : DeveloperSettingsUiState()
}

/**
 * 进程内正在进行的 AI 任务快照。
 *
 * 全部读自已有的内存结构，不引入任何新状态；调并发问题时这一屏最有价值。
 */
data class DeveloperRuntimeStatus(
    val activeGenerationSessionIds: List<Long>,
    val activeSummaryKeys: List<String>,
    val bufferedLogCount: Int
)
