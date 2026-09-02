package me.kafuuneko.rpclient.feature.summarymemory.presentation

import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole

data class SummaryProviderItem(
    val id: Long,
    val name: String,
    val isEnabled: Boolean
)

sealed class SummaryMemorySettingsUiState {
    data object None : SummaryMemorySettingsUiState()

    data class Normal(
        val providers: List<SummaryProviderItem> = emptyList(),
        val selectedProviderId: Long = 0L,
        val wordsLimit: Int = 500,
        val responseTokens: Int = 1000,
        val maxMessagesPerRequest: Int = 0,
        val autoSummaryEnabled: Boolean = true,
        val triggerMessageCount: Int = 20,
        val injectionPosition: SummaryInjectionPosition = SummaryInjectionPosition.default,
        val injectionDepth: Int = 2,
        val injectionRole: SummaryInjectionRole = SummaryInjectionRole.System
    ) : SummaryMemorySettingsUiState()

    data class Finished(
        val previous: SummaryMemorySettingsUiState
    ) : SummaryMemorySettingsUiState()
}
