package me.kafuuneko.rpclient.feature.summarymemory

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.summarymemory.presentation.SummaryMemorySettingsUiIntent
import me.kafuuneko.rpclient.feature.summarymemory.presentation.SummaryMemorySettingsUiState
import me.kafuuneko.rpclient.feature.summarymemory.presentation.SummaryProviderItem
import me.kafuuneko.rpclient.feature.promptpreset.PromptPresetActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SummaryMemorySettingsViewModel : CoreViewModelWithEvent<
    SummaryMemorySettingsUiIntent,
    SummaryMemorySettingsUiState
>(SummaryMemorySettingsUiState.None), KoinComponent {

    private val mLLMRepository by inject<LLMRepository>()

    @UiIntentObserver(SummaryMemorySettingsUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<SummaryMemorySettingsUiState.None>()) return

        val injectionPos = SummaryInjectionPosition.fromPersistedValue(
            runCatching { AppModel.summaryInjectionPosition }
                .getOrDefault(SummaryInjectionPosition.default.persistedValue)
        )
        val injectionRole = SummaryInjectionRole.fromPersistedValue(
            runCatching { AppModel.summaryInjectionRole }
                .getOrDefault(SummaryInjectionRole.System.persistedValue)
        )

        val initialState = SummaryMemorySettingsUiState.Normal(
            selectedProviderId = AppModel.summaryLLMProvider,
            wordsLimit = AppModel.summaryWordsLimit,
            responseTokens = AppModel.summaryResponseTokens,
            maxMessagesPerRequest = AppModel.summaryMaxMessagesPerRequest,
            autoSummaryEnabled = AppModel.autoSummaryEnabled,
            triggerMessageCount = AppModel.summaryTriggerMessageCount,
            injectionPosition = injectionPos,
            injectionDepth = AppModel.summaryInjectionDepth,
            injectionRole = injectionRole
        )
        initialState.setup()

        viewModelScope.launch(Dispatchers.IO) {
            val allProviders = mLLMRepository.getAllProviders()
            val providerItems = allProviders.map {
                SummaryProviderItem(
                    id = it.id,
                    name = it.name,
                    isEnabled = it.isEnabled
                )
            }
            withContext(Dispatchers.Main) {
                val current = getOrNull<SummaryMemorySettingsUiState.Normal>() ?: return@withContext
                current.copy(providers = providerItems).setup()
            }
        }
    }

    /**
     * 摘要提示词模板与其它模板同住提示词预设页；这里只提供一个入口，
     * 避免把同一份模板拆到两个页面各编辑一次。
     */
    @UiIntentObserver(SummaryMemorySettingsUiIntent.OpenSummaryPromptTemplates::class)
    private fun onOpenSummaryPromptTemplates() {
        AppViewEvent.StartActivity(PromptPresetActivity::class.java).tryEmit()
    }

    @UiIntentObserver(SummaryMemorySettingsUiIntent.Back::class)
    private fun onBack() {
        val state = uiStateFlow.value
        if (state is SummaryMemorySettingsUiState.Finished) return
        SummaryMemorySettingsUiState.Finished(state).setup()
    }

    @UiIntentObserver(SummaryMemorySettingsUiIntent.SelectProvider::class)
    private fun onSelectProvider(intent: SummaryMemorySettingsUiIntent.SelectProvider) {
        val state = getOrNull<SummaryMemorySettingsUiState.Normal>() ?: return
        AppModel.summaryLLMProvider = intent.providerId
        state.copy(selectedProviderId = intent.providerId).setup()
    }

    @UiIntentObserver(SummaryMemorySettingsUiIntent.ChangeWordsLimit::class)
    private fun onChangeWordsLimit(intent: SummaryMemorySettingsUiIntent.ChangeWordsLimit) {
        val state = getOrNull<SummaryMemorySettingsUiState.Normal>() ?: return
        val words = intent.words.toIntOrNull()?.coerceIn(50, 5000) ?: 500
        AppModel.summaryWordsLimit = words
        state.copy(wordsLimit = words).setup()
    }

    @UiIntentObserver(SummaryMemorySettingsUiIntent.ChangeResponseTokens::class)
    private fun onChangeResponseTokens(intent: SummaryMemorySettingsUiIntent.ChangeResponseTokens) {
        val state = getOrNull<SummaryMemorySettingsUiState.Normal>() ?: return
        val tokens = intent.tokens.toIntOrNull()?.coerceIn(100, 8192) ?: 1000
        AppModel.summaryResponseTokens = tokens
        state.copy(responseTokens = tokens).setup()
    }

    @UiIntentObserver(SummaryMemorySettingsUiIntent.ChangeMaxMessagesPerRequest::class)
    private fun onChangeMaxMessagesPerRequest(intent: SummaryMemorySettingsUiIntent.ChangeMaxMessagesPerRequest) {
        val state = getOrNull<SummaryMemorySettingsUiState.Normal>() ?: return
        val messages = intent.messages.toIntOrNull()?.coerceAtLeast(0) ?: 0
        AppModel.summaryMaxMessagesPerRequest = messages
        state.copy(maxMessagesPerRequest = messages).setup()
    }

    @UiIntentObserver(SummaryMemorySettingsUiIntent.ToggleAutoSummary::class)
    private fun onToggleAutoSummary(intent: SummaryMemorySettingsUiIntent.ToggleAutoSummary) {
        val state = getOrNull<SummaryMemorySettingsUiState.Normal>() ?: return
        AppModel.autoSummaryEnabled = intent.enabled
        state.copy(autoSummaryEnabled = intent.enabled).setup()
    }

    @UiIntentObserver(SummaryMemorySettingsUiIntent.ChangeTriggerMessageCount::class)
    private fun onChangeTriggerMessageCount(intent: SummaryMemorySettingsUiIntent.ChangeTriggerMessageCount) {
        val state = getOrNull<SummaryMemorySettingsUiState.Normal>() ?: return
        val count = intent.count.toIntOrNull()?.coerceIn(2, 200) ?: 20
        AppModel.summaryTriggerMessageCount = count
        state.copy(triggerMessageCount = count).setup()
    }

    @UiIntentObserver(SummaryMemorySettingsUiIntent.SelectInjectionPosition::class)
    private fun onSelectInjectionPosition(intent: SummaryMemorySettingsUiIntent.SelectInjectionPosition) {
        val state = getOrNull<SummaryMemorySettingsUiState.Normal>() ?: return
        AppModel.summaryInjectionPosition = intent.position.persistedValue
        state.copy(injectionPosition = intent.position).setup()
    }

    @UiIntentObserver(SummaryMemorySettingsUiIntent.ChangeInjectionDepth::class)
    private fun onChangeInjectionDepth(intent: SummaryMemorySettingsUiIntent.ChangeInjectionDepth) {
        val state = getOrNull<SummaryMemorySettingsUiState.Normal>() ?: return
        val depth = intent.depth.toIntOrNull()?.coerceIn(0, 100) ?: 2
        AppModel.summaryInjectionDepth = depth
        state.copy(injectionDepth = depth).setup()
    }

    @UiIntentObserver(SummaryMemorySettingsUiIntent.SelectInjectionRole::class)
    private fun onSelectInjectionRole(intent: SummaryMemorySettingsUiIntent.SelectInjectionRole) {
        val state = getOrNull<SummaryMemorySettingsUiState.Normal>() ?: return
        AppModel.summaryInjectionRole = intent.role.persistedValue
        state.copy(injectionRole = intent.role).setup()
    }
}
