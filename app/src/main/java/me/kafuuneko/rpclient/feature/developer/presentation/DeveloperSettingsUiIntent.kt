package me.kafuuneko.rpclient.feature.developer.presentation

sealed class DeveloperSettingsUiIntent {
    data object Init : DeveloperSettingsUiIntent()
    data object Resume : DeveloperSettingsUiIntent()
    data object Back : DeveloperSettingsUiIntent()
    data class ToggleDeveloperLogging(val enabled: Boolean) : DeveloperSettingsUiIntent()
    data class ToggleDebugMode(val enabled: Boolean) : DeveloperSettingsUiIntent()
    data object OpenAppLogs : DeveloperSettingsUiIntent()
    data object OpenRequestLogs : DeveloperSettingsUiIntent()
}
