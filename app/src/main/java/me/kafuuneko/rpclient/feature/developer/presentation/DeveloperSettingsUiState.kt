package me.kafuuneko.rpclient.feature.developer.presentation

sealed class DeveloperSettingsUiState {
    data object None : DeveloperSettingsUiState()

    data class Normal(
        val developerLoggingEnabled: Boolean,
        val debugModeEnabled: Boolean
    ) : DeveloperSettingsUiState()

    data class Finished(
        val previous: DeveloperSettingsUiState
    ) : DeveloperSettingsUiState()
}
