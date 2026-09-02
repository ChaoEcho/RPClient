package me.kafuuneko.rpclient.feature.worldinfobudget.presentation

sealed class WorldInfoBudgetSettingsUiIntent {
    data object Init : WorldInfoBudgetSettingsUiIntent()
    data object Back : WorldInfoBudgetSettingsUiIntent()
    data class ChangeBudgetPercent(val percent: Int) : WorldInfoBudgetSettingsUiIntent()
    data class ChangeBudgetCap(val cap: String) : WorldInfoBudgetSettingsUiIntent()
    data class ToggleOverflowAlert(val enabled: Boolean) : WorldInfoBudgetSettingsUiIntent()
}
