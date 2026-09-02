package me.kafuuneko.rpclient.feature.worldinfobudget

import me.kafuuneko.rpclient.feature.worldinfobudget.presentation.WorldInfoBudgetSettingsUiIntent
import me.kafuuneko.rpclient.feature.worldinfobudget.presentation.WorldInfoBudgetSettingsUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver

class WorldInfoBudgetSettingsViewModel : CoreViewModelWithEvent<
    WorldInfoBudgetSettingsUiIntent,
    WorldInfoBudgetSettingsUiState
>(WorldInfoBudgetSettingsUiState.None) {

    @UiIntentObserver(WorldInfoBudgetSettingsUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<WorldInfoBudgetSettingsUiState.None>()) return

        val state = WorldInfoBudgetSettingsUiState.Normal(
            budgetPercent = AppModel.worldInfoBudgetPercent,
            budgetCap = AppModel.worldInfoBudgetCap,
            overflowAlert = AppModel.worldInfoOverflowAlert
        )
        state.setup()
    }

    @UiIntentObserver(WorldInfoBudgetSettingsUiIntent.Back::class)
    private fun onBack() {
        val state = uiStateFlow.value
        if (state is WorldInfoBudgetSettingsUiState.Finished) return
        WorldInfoBudgetSettingsUiState.Finished(state).setup()
    }

    @UiIntentObserver(WorldInfoBudgetSettingsUiIntent.ChangeBudgetPercent::class)
    private fun onChangeBudgetPercent(intent: WorldInfoBudgetSettingsUiIntent.ChangeBudgetPercent) {
        val state = getOrNull<WorldInfoBudgetSettingsUiState.Normal>() ?: return
        val clamped = intent.percent.coerceIn(5, 80)
        AppModel.worldInfoBudgetPercent = clamped
        state.copy(budgetPercent = clamped).setup()
    }

    @UiIntentObserver(WorldInfoBudgetSettingsUiIntent.ChangeBudgetCap::class)
    private fun onChangeBudgetCap(intent: WorldInfoBudgetSettingsUiIntent.ChangeBudgetCap) {
        val state = getOrNull<WorldInfoBudgetSettingsUiState.Normal>() ?: return
        val cap = intent.cap.toIntOrNull()?.coerceAtLeast(0) ?: 0
        AppModel.worldInfoBudgetCap = cap
        state.copy(budgetCap = cap).setup()
    }

    @UiIntentObserver(WorldInfoBudgetSettingsUiIntent.ToggleOverflowAlert::class)
    private fun onToggleOverflowAlert(intent: WorldInfoBudgetSettingsUiIntent.ToggleOverflowAlert) {
        val state = getOrNull<WorldInfoBudgetSettingsUiState.Normal>() ?: return
        AppModel.worldInfoOverflowAlert = intent.enabled
        state.copy(overflowAlert = intent.enabled).setup()
    }
}
