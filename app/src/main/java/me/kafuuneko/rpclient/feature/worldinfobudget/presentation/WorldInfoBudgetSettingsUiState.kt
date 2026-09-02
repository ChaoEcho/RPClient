package me.kafuuneko.rpclient.feature.worldinfobudget.presentation

sealed class WorldInfoBudgetSettingsUiState {
    data object None : WorldInfoBudgetSettingsUiState()

    data class Normal(
        val budgetPercent: Int,
        val budgetCap: Int,
        val overflowAlert: Boolean
    ) : WorldInfoBudgetSettingsUiState()

    data class Finished(
        val previous: WorldInfoBudgetSettingsUiState
    ) : WorldInfoBudgetSettingsUiState()
}
