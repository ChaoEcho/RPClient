package me.kafuuneko.rpclient.feature.developer

import me.kafuuneko.rpclient.feature.developer.logviewer.AppLogViewerActivity
import me.kafuuneko.rpclient.feature.developer.presentation.DeveloperSettingsUiIntent
import me.kafuuneko.rpclient.feature.developer.presentation.DeveloperSettingsUiState
import me.kafuuneko.rpclient.feature.requestlog.RequestLogActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver

class DeveloperSettingsViewModel : CoreViewModelWithEvent<
    DeveloperSettingsUiIntent,
    DeveloperSettingsUiState
>(DeveloperSettingsUiState.None) {

    @UiIntentObserver(DeveloperSettingsUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<DeveloperSettingsUiState.None>()) return

        val state = DeveloperSettingsUiState.Normal(
            developerLoggingEnabled = AppModel.developerLoggingEnabled,
            debugModeEnabled = AppModel.debugModeEnabled
        )
        state.setup()
    }

    @UiIntentObserver(DeveloperSettingsUiIntent.Back::class)
    private fun onBack() {
        val state = uiStateFlow.value
        if (state is DeveloperSettingsUiState.Finished) return
        DeveloperSettingsUiState.Finished(state).setup()
    }

    @UiIntentObserver(DeveloperSettingsUiIntent.ToggleDeveloperLogging::class)
    private fun onToggleDeveloperLogging(intent: DeveloperSettingsUiIntent.ToggleDeveloperLogging) {
        val state = getOrNull<DeveloperSettingsUiState.Normal>() ?: return
        AppModel.developerLoggingEnabled = intent.enabled
        state.copy(developerLoggingEnabled = intent.enabled).setup()
    }

    @UiIntentObserver(DeveloperSettingsUiIntent.ToggleDebugMode::class)
    private fun onToggleDebugMode(intent: DeveloperSettingsUiIntent.ToggleDebugMode) {
        val state = getOrNull<DeveloperSettingsUiState.Normal>() ?: return
        AppModel.debugModeEnabled = intent.enabled
        state.copy(debugModeEnabled = intent.enabled).setup()
    }

    @UiIntentObserver(DeveloperSettingsUiIntent.OpenAppLogs::class)
    private fun onOpenAppLogs() {
        AppViewEvent.StartActivity(AppLogViewerActivity::class.java).tryEmit()
    }

    @UiIntentObserver(DeveloperSettingsUiIntent.OpenRequestLogs::class)
    private fun onOpenRequestLogs() {
        AppViewEvent.StartActivity(RequestLogActivity::class.java).tryEmit()
    }
}
