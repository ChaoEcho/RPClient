package me.kafuuneko.rpclient.feature.promptbehavior

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.promptbehavior.presentation.PromptBehaviorSettingsUiIntent
import me.kafuuneko.rpclient.feature.promptbehavior.presentation.PromptBehaviorSettingsUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PromptBehaviorSettingsViewModel : CoreViewModelWithEvent<
    PromptBehaviorSettingsUiIntent,
    PromptBehaviorSettingsUiState
>(PromptBehaviorSettingsUiState.None), KoinComponent {

    private val mLLMRepository by inject<LLMRepository>()

    @UiIntentObserver(PromptBehaviorSettingsUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<PromptBehaviorSettingsUiState.None>()) return

        val exampleBehavior = ExampleDialogueBehavior.fromPersistedValue(
            runCatching { AppModel.exampleDialogueBehavior }
                .getOrDefault(ExampleDialogueBehavior.default.persistedValue)
        )

        val initialState = PromptBehaviorSettingsUiState.Normal(
            postProcessingMode = PromptPostProcessingMode.Strict,
            exampleDialogueBehavior = exampleBehavior,
            includeThinkInContext = AppModel.includeThinkInContext,
            contextTrimmingAlert = AppModel.contextTrimmingAlert,
            streamEnabled = AppModel.streamEnabled
        )
        initialState.setup()

        viewModelScope.launch(Dispatchers.IO) {
            val currentProviderId = AppModel.currentLLMProvider
            val provider = mLLMRepository.getProviderById(currentProviderId)
                ?: mLLMRepository.getEnabledProviders().firstOrNull()
            val mode = provider?.let {
                PromptPostProcessingMode.fromOrdinal(it.promptPostProcessingMode)
            } ?: PromptPostProcessingMode.Strict

            withContext(Dispatchers.Main) {
                val current = getOrNull<PromptBehaviorSettingsUiState.Normal>() ?: return@withContext
                current.copy(postProcessingMode = mode).setup()
            }
        }
    }

    @UiIntentObserver(PromptBehaviorSettingsUiIntent.Back::class)
    private fun onBack() {
        val state = uiStateFlow.value
        if (state is PromptBehaviorSettingsUiState.Finished) return
        PromptBehaviorSettingsUiState.Finished(state).setup()
    }

    @UiIntentObserver(PromptBehaviorSettingsUiIntent.SelectPostProcessingMode::class)
    private fun onSelectPostProcessingMode(intent: PromptBehaviorSettingsUiIntent.SelectPostProcessingMode) {
        val state = getOrNull<PromptBehaviorSettingsUiState.Normal>() ?: return
        state.copy(postProcessingMode = intent.mode).setup()

        viewModelScope.launch(Dispatchers.IO) {
            val currentProviderId = AppModel.currentLLMProvider
            val provider = mLLMRepository.getProviderById(currentProviderId)
                ?: mLLMRepository.getEnabledProviders().firstOrNull()
            if (provider != null) {
                val updated = provider.copy(promptPostProcessingMode = intent.mode.ordinal)
                mLLMRepository.saveProvider(updated)
            }
        }
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
}
