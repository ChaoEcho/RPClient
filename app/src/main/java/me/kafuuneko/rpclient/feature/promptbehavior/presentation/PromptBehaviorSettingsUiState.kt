package me.kafuuneko.rpclient.feature.promptbehavior.presentation

import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode

sealed class PromptBehaviorSettingsUiState {
    data object None : PromptBehaviorSettingsUiState()
    data class Finished(val previous: PromptBehaviorSettingsUiState) : PromptBehaviorSettingsUiState()
    data class Normal(
        val postProcessingMode: PromptPostProcessingMode,
        val exampleDialogueBehavior: ExampleDialogueBehavior,
        val includeThinkInContext: Boolean,
        val contextTrimmingAlert: Boolean,
        val streamEnabled: Boolean
    ) : PromptBehaviorSettingsUiState()
}
