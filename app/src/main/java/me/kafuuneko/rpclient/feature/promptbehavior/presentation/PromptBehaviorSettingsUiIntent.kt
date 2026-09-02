package me.kafuuneko.rpclient.feature.promptbehavior.presentation

import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode

sealed class PromptBehaviorSettingsUiIntent {
    data object Init : PromptBehaviorSettingsUiIntent()
    data object Back : PromptBehaviorSettingsUiIntent()
    data class SelectPostProcessingMode(val mode: PromptPostProcessingMode) : PromptBehaviorSettingsUiIntent()
    data class SelectExampleDialogueBehavior(val behavior: ExampleDialogueBehavior) : PromptBehaviorSettingsUiIntent()
    data class ToggleIncludeThinkInContext(val enabled: Boolean) : PromptBehaviorSettingsUiIntent()
    data class ToggleContextTrimmingAlert(val enabled: Boolean) : PromptBehaviorSettingsUiIntent()
    data class ToggleStreamEnabled(val enabled: Boolean) : PromptBehaviorSettingsUiIntent()
}
