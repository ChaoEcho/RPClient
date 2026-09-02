package me.kafuuneko.rpclient.feature.developer

import me.kafuuneko.rpclient.feature.developer.logviewer.AppLogViewerActivity
import me.kafuuneko.rpclient.feature.developer.presentation.DeveloperSettingsUiIntent
import me.kafuuneko.rpclient.feature.developer.presentation.DeveloperRuntimeStatus
import me.kafuuneko.rpclient.feature.developer.presentation.DeveloperSettingsUiState
import me.kafuuneko.rpclient.feature.requestlog.RequestLogActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.chat.generation.ChatGenerationCoordinator
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.debug.AppLogStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DeveloperSettingsViewModel : CoreViewModelWithEvent<
    DeveloperSettingsUiIntent,
    DeveloperSettingsUiState
>(DeveloperSettingsUiState.None), KoinComponent {

    private val mGenerationCoordinator by inject<ChatGenerationCoordinator>()

    @UiIntentObserver(DeveloperSettingsUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<DeveloperSettingsUiState.None>()) return

        DeveloperSettingsUiState.Normal(
            developerLoggingEnabled = AppModel.developerLoggingEnabled,
            debugModeEnabled = AppModel.debugModeEnabled,
            runtimeStatus = readRuntimeStatus()
        ).setup()
    }

    /** 回到页面时重新采样，让运行状态反映刚才那次生成。 */
    @UiIntentObserver(DeveloperSettingsUiIntent.Resume::class)
    private fun onResume() {
        val state = getOrNull<DeveloperSettingsUiState.Normal>() ?: return
        state.copy(runtimeStatus = readRuntimeStatus()).setup()
    }

    private fun readRuntimeStatus() = DeveloperRuntimeStatus(
        activeGenerationSessionIds = mGenerationCoordinator.activeSessionIds().sorted(),
        activeSummaryKeys = mGenerationCoordinator.activeSummaryKeys().sorted(),
        bufferedLogCount = AppLogStore.snapshot().size
    )

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
