package me.kafuuneko.rpclient.feature.promptbehavior.presentation

import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior

sealed class PromptBehaviorSettingsUiIntent {
    data object Init : PromptBehaviorSettingsUiIntent()
    data object Back : PromptBehaviorSettingsUiIntent()
    data class SelectExampleDialogueBehavior(val behavior: ExampleDialogueBehavior) : PromptBehaviorSettingsUiIntent()
    data class ToggleIncludeThinkInContext(val enabled: Boolean) : PromptBehaviorSettingsUiIntent()

    data class ToggleKeepSystemPromptInSpecialModes(
        val enabled: Boolean
    ) : PromptBehaviorSettingsUiIntent()
    data class ToggleContextTrimmingAlert(val enabled: Boolean) : PromptBehaviorSettingsUiIntent()
    data class ToggleStreamEnabled(val enabled: Boolean) : PromptBehaviorSettingsUiIntent()
    data class ChangeWorldInfoBudgetPercent(val percent: Int) : PromptBehaviorSettingsUiIntent()
    data class ChangeWorldInfoBudgetCap(val cap: String) : PromptBehaviorSettingsUiIntent()
    data class ToggleWorldInfoOverflowAlert(val enabled: Boolean) : PromptBehaviorSettingsUiIntent()
}
