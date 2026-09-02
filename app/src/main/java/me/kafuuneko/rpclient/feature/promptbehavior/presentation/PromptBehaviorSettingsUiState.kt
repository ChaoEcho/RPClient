package me.kafuuneko.rpclient.feature.promptbehavior.presentation

import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior

sealed class PromptBehaviorSettingsUiState {
    data object None : PromptBehaviorSettingsUiState()
    data class Finished(val previous: PromptBehaviorSettingsUiState) : PromptBehaviorSettingsUiState()
    data class Normal(
        val exampleDialogueBehavior: ExampleDialogueBehavior,
        val includeThinkInContext: Boolean,
        val contextTrimmingAlert: Boolean,
        val streamEnabled: Boolean,
        val worldInfoBudgetPercent: Int,
        val worldInfoBudgetCap: Int,
        val worldInfoOverflowAlert: Boolean
    ) : PromptBehaviorSettingsUiState()
}
