package me.kafuuneko.rpclient.feature.promptbehavior

import me.kafuuneko.rpclient.feature.promptbehavior.presentation.PromptBehaviorSettingsUiIntent
import me.kafuuneko.rpclient.feature.promptbehavior.presentation.PromptBehaviorSettingsUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PromptBehaviorSettingsViewModel : CoreViewModelWithEvent<
    PromptBehaviorSettingsUiIntent,
    PromptBehaviorSettingsUiState
>(PromptBehaviorSettingsUiState.None), KoinComponent {

    @UiIntentObserver(PromptBehaviorSettingsUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<PromptBehaviorSettingsUiState.None>()) return

        val exampleBehavior = ExampleDialogueBehavior.fromPersistedValue(
            runCatching { AppModel.exampleDialogueBehavior }
                .getOrDefault(ExampleDialogueBehavior.default.persistedValue)
        )

        PromptBehaviorSettingsUiState.Normal(
            exampleDialogueBehavior = exampleBehavior,
            includeThinkInContext = AppModel.includeThinkInContext,
            contextTrimmingAlert = AppModel.contextTrimmingAlert,
            streamEnabled = AppModel.streamEnabled,
            worldInfoBudgetPercent = AppModel.worldInfoBudgetPercent,
            worldInfoBudgetCap = AppModel.worldInfoBudgetCap,
            worldInfoOverflowAlert = AppModel.worldInfoOverflowAlert
        ).setup()
    }

    @UiIntentObserver(PromptBehaviorSettingsUiIntent.Back::class)
    private fun onBack() {
        val state = uiStateFlow.value
        if (state is PromptBehaviorSettingsUiState.Finished) return
        PromptBehaviorSettingsUiState.Finished(state).setup()
    }

    @UiIntentObserver(PromptBehaviorSettingsUiIntent.SelectExampleDialogueBehavior::class)
    private fun onSelectExampleDialogueBehavior(intent: PromptBehaviorSettingsUiIntent.SelectExampleDialogueBehavior) {
        val state = getOrNull<PromptBehaviorSettingsUiState.Normal>() ?: return
        AppModel.exampleDialogueBehavior = intent.behavior.persistedValue
        state.copy(exampleDialogueBehavior = intent.behavior).setup()
    }

    @UiIntentObserver(PromptBehaviorSettingsUiIntent.ToggleIncludeThinkInContext::class)
    private fun onToggleIncludeThinkInContext(intent: PromptBehaviorSettingsUiIntent.ToggleIncludeThinkInContext) {
        val state = getOrNull<PromptBehaviorSettingsUiState.Normal>() ?: return
        AppModel.includeThinkInContext = intent.enabled
        state.copy(includeThinkInContext = intent.enabled).setup()
    }

    @UiIntentObserver(PromptBehaviorSettingsUiIntent.ToggleContextTrimmingAlert::class)
    private fun onToggleContextTrimmingAlert(intent: PromptBehaviorSettingsUiIntent.ToggleContextTrimmingAlert) {
        val state = getOrNull<PromptBehaviorSettingsUiState.Normal>() ?: return
        AppModel.contextTrimmingAlert = intent.enabled
        state.copy(contextTrimmingAlert = intent.enabled).setup()
    }

    @UiIntentObserver(PromptBehaviorSettingsUiIntent.ToggleStreamEnabled::class)
    private fun onToggleStreamEnabled(intent: PromptBehaviorSettingsUiIntent.ToggleStreamEnabled) {
        val state = getOrNull<PromptBehaviorSettingsUiState.Normal>() ?: return
        AppModel.streamEnabled = intent.enabled
        state.copy(streamEnabled = intent.enabled).setup()
    }

    @UiIntentObserver(PromptBehaviorSettingsUiIntent.ChangeWorldInfoBudgetPercent::class)
    private fun onChangeWorldInfoBudgetPercent(
        intent: PromptBehaviorSettingsUiIntent.ChangeWorldInfoBudgetPercent
    ) {
        val state = getOrNull<PromptBehaviorSettingsUiState.Normal>() ?: return
        val clamped = intent.percent.coerceIn(
            WORLD_INFO_BUDGET_MIN_PERCENT,
            WORLD_INFO_BUDGET_MAX_PERCENT
        )
        AppModel.worldInfoBudgetPercent = clamped
        state.copy(worldInfoBudgetPercent = clamped).setup()
    }

    @UiIntentObserver(PromptBehaviorSettingsUiIntent.ChangeWorldInfoBudgetCap::class)
    private fun onChangeWorldInfoBudgetCap(
        intent: PromptBehaviorSettingsUiIntent.ChangeWorldInfoBudgetCap
    ) {
        val state = getOrNull<PromptBehaviorSettingsUiState.Normal>() ?: return
        val cap = intent.cap.toIntOrNull()?.coerceAtLeast(0) ?: 0
        AppModel.worldInfoBudgetCap = cap
        state.copy(worldInfoBudgetCap = cap).setup()
    }

    @UiIntentObserver(PromptBehaviorSettingsUiIntent.ToggleWorldInfoOverflowAlert::class)
    private fun onToggleWorldInfoOverflowAlert(
        intent: PromptBehaviorSettingsUiIntent.ToggleWorldInfoOverflowAlert
    ) {
        val state = getOrNull<PromptBehaviorSettingsUiState.Normal>() ?: return
        AppModel.worldInfoOverflowAlert = intent.enabled
        state.copy(worldInfoOverflowAlert = intent.enabled).setup()
    }
}

/** 世界书预算占上下文的比例范围；低于下限世界书基本失效，高于上限会挤掉对话历史。 */
private const val WORLD_INFO_BUDGET_MIN_PERCENT = 5
private const val WORLD_INFO_BUDGET_MAX_PERCENT = 80
