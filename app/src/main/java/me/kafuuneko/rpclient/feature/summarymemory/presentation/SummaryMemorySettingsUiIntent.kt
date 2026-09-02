package me.kafuuneko.rpclient.feature.summarymemory.presentation

import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole

sealed class SummaryMemorySettingsUiIntent {
    data object Init : SummaryMemorySettingsUiIntent()
    data object Back : SummaryMemorySettingsUiIntent()
    data class SelectProvider(val providerId: Long) : SummaryMemorySettingsUiIntent()
    data class ChangeWordsLimit(val words: String) : SummaryMemorySettingsUiIntent()
    data class ChangeResponseTokens(val tokens: String) : SummaryMemorySettingsUiIntent()
    data class ChangeMaxMessagesPerRequest(val messages: String) : SummaryMemorySettingsUiIntent()
    data class ToggleAutoSummary(val enabled: Boolean) : SummaryMemorySettingsUiIntent()
    data class ChangeTriggerMessageCount(val count: String) : SummaryMemorySettingsUiIntent()
    data class SelectInjectionPosition(val position: SummaryInjectionPosition) : SummaryMemorySettingsUiIntent()
    data class ChangeInjectionDepth(val depth: String) : SummaryMemorySettingsUiIntent()
    data class SelectInjectionRole(val role: SummaryInjectionRole) : SummaryMemorySettingsUiIntent()
    data object OpenSummaryPromptTemplates : SummaryMemorySettingsUiIntent()
}
