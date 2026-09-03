package me.kafuuneko.rpclient.feature.ttsprovideredit

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.feature.ttsprovideredit.presentation.AzureTtsSettingsState
import me.kafuuneko.rpclient.feature.ttsprovideredit.presentation.MimoTtsSettingsState
import me.kafuuneko.rpclient.feature.ttsprovideredit.presentation.SystemTtsSettingsState
import me.kafuuneko.rpclient.feature.ttsprovideredit.presentation.TtsProviderEditUiIntent
import me.kafuuneko.rpclient.feature.ttsprovideredit.presentation.TtsProviderEditUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.tts.TtsProviderType
import me.kafuuneko.rpclient.libs.tts.TtsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 单个语音服务的配置持有者。
 *
 * 沿用语音设置一贯的逐键即时写入语义：每次修改直接落 Kotpref，页面没有保存按钮，
 * 因此也不需要草稿态与未保存确认。
 */
class TtsProviderEditViewModel : CoreViewModelWithEvent<
    TtsProviderEditUiIntent,
    TtsProviderEditUiState
>(TtsProviderEditUiState.None), KoinComponent {
    private val mTtsService by inject<TtsService>()

    @UiIntentObserver(TtsProviderEditUiIntent.Init::class)
    private fun onInit(intent: TtsProviderEditUiIntent.Init) {
        if (!isStateOf<TtsProviderEditUiState.None>()) return

        TtsProviderEditUiState.Normal(
            provider = intent.provider,
            system = SystemTtsSettingsState(
                languageTag = AppModel.ttsSystemLanguageTag,
                voiceName = AppModel.ttsSystemVoiceName,
                speechRate = AppModel.ttsSystemSpeechRate,
                pitch = AppModel.ttsSystemPitch,
                voices = emptyList()
            ),
            mimo = MimoTtsSettingsState(
                baseUrl = AppModel.ttsMimoBaseUrl,
                apiKey = AppModel.ttsMimoApiKey,
                model = AppModel.ttsMimoModel,
                voice = AppModel.ttsMimoVoice,
                instructions = AppModel.ttsMimoInstructions,
                temperature = AppModel.ttsMimoTemperature,
                streaming = AppModel.ttsMimoStreaming
            ),
            azure = AzureTtsSettingsState(
                apiKey = AppModel.ttsAzureApiKey,
                region = AppModel.ttsAzureRegion,
                voice = AppModel.ttsAzureVoice,
                speechRate = AppModel.ttsAzureSpeechRate
            )
        ).setup()

        if (intent.provider != TtsProviderType.System) return
        // 系统发音人列表来自 TextToSpeech 引擎，只有系统服务详情页需要，异步补齐。
        viewModelScope.launch {
            val voices = runCatching { mTtsService.getSystemVoices() }.getOrElse { emptyList() }
            val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return@launch
            val system = normalizeSystemVoiceSelection(state.system.copy(voices = voices))
            persistSystemSelectionIfChanged(state.system, system)
            state.copy(system = system).setup()
        }
    }

    @UiIntentObserver(TtsProviderEditUiIntent.Back::class)
    private fun onBack() {
        val currentState = uiStateFlow.value
        if (currentState is TtsProviderEditUiState.Finished) return
        TtsProviderEditUiState.Finished(currentState).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.SelectSystemLanguage::class)
    private fun onSelectSystemLanguage(intent: TtsProviderEditUiIntent.SelectSystemLanguage) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        val selectedVoice = state.system.voices
            .filter { it.languageTag == intent.languageTag }
            .firstOrNull { it.name == state.system.voiceName }
            ?: state.system.voices.firstOrNull { it.languageTag == intent.languageTag }
        val voiceName = selectedVoice?.name.orEmpty()
        AppModel.ttsSystemLanguageTag = intent.languageTag
        AppModel.ttsSystemVoiceName = voiceName
        state.copy(
            system = state.system.copy(languageTag = intent.languageTag, voiceName = voiceName)
        ).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.SelectSystemVoice::class)
    private fun onSelectSystemVoice(intent: TtsProviderEditUiIntent.SelectSystemVoice) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        val voice = state.system.voices.firstOrNull { it.name == intent.voiceName } ?: return
        AppModel.ttsSystemLanguageTag = voice.languageTag
        AppModel.ttsSystemVoiceName = voice.name
        state.copy(
            system = state.system.copy(languageTag = voice.languageTag, voiceName = voice.name)
        ).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeSystemSpeechRate::class)
    private fun onChangeSystemSpeechRate(intent: TtsProviderEditUiIntent.ChangeSystemSpeechRate) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsSystemSpeechRate = intent.value
        state.copy(system = state.system.copy(speechRate = intent.value)).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeSystemPitch::class)
    private fun onChangeSystemPitch(intent: TtsProviderEditUiIntent.ChangeSystemPitch) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsSystemPitch = intent.value
        state.copy(system = state.system.copy(pitch = intent.value)).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeMimoBaseUrl::class)
    private fun onChangeMimoBaseUrl(intent: TtsProviderEditUiIntent.ChangeMimoBaseUrl) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsMimoBaseUrl = intent.value
        state.copy(mimo = state.mimo.copy(baseUrl = intent.value)).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeMimoApiKey::class)
    private fun onChangeMimoApiKey(intent: TtsProviderEditUiIntent.ChangeMimoApiKey) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsMimoApiKey = intent.value
        state.copy(mimo = state.mimo.copy(apiKey = intent.value)).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeMimoModel::class)
    private fun onChangeMimoModel(intent: TtsProviderEditUiIntent.ChangeMimoModel) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsMimoModel = intent.value
        state.copy(mimo = state.mimo.copy(model = intent.value)).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeMimoVoice::class)
    private fun onChangeMimoVoice(intent: TtsProviderEditUiIntent.ChangeMimoVoice) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsMimoVoice = intent.value
        state.copy(mimo = state.mimo.copy(voice = intent.value)).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeMimoInstructions::class)
    private fun onChangeMimoInstructions(intent: TtsProviderEditUiIntent.ChangeMimoInstructions) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsMimoInstructions = intent.value
        state.copy(mimo = state.mimo.copy(instructions = intent.value)).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeMimoTemperature::class)
    private fun onChangeMimoTemperature(intent: TtsProviderEditUiIntent.ChangeMimoTemperature) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsMimoTemperature = intent.value
        state.copy(mimo = state.mimo.copy(temperature = intent.value)).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeMimoStreaming::class)
    private fun onChangeMimoStreaming(intent: TtsProviderEditUiIntent.ChangeMimoStreaming) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsMimoStreaming = intent.value
        state.copy(mimo = state.mimo.copy(streaming = intent.value)).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeAzureApiKey::class)
    private fun onChangeAzureApiKey(intent: TtsProviderEditUiIntent.ChangeAzureApiKey) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsAzureApiKey = intent.value
        state.copy(azure = state.azure.copy(apiKey = intent.value)).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeAzureRegion::class)
    private fun onChangeAzureRegion(intent: TtsProviderEditUiIntent.ChangeAzureRegion) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsAzureRegion = intent.value
        state.copy(azure = state.azure.copy(region = intent.value)).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeAzureVoice::class)
    private fun onChangeAzureVoice(intent: TtsProviderEditUiIntent.ChangeAzureVoice) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsAzureVoice = intent.value
        state.copy(azure = state.azure.copy(voice = intent.value)).setup()
    }

    @UiIntentObserver(TtsProviderEditUiIntent.ChangeAzureSpeechRate::class)
    private fun onChangeAzureSpeechRate(intent: TtsProviderEditUiIntent.ChangeAzureSpeechRate) {
        val state = getOrNull<TtsProviderEditUiState.Normal>() ?: return
        AppModel.ttsAzureSpeechRate = intent.value
        state.copy(azure = state.azure.copy(speechRate = intent.value)).setup()
    }

    private fun normalizeSystemVoiceSelection(
        system: SystemTtsSettingsState
    ): SystemTtsSettingsState {
        if (system.voices.isEmpty()) return system
        val languageTag = system.voices
            .firstOrNull { it.languageTag.equals(system.languageTag, ignoreCase = true) }
            ?.languageTag
            ?: system.voices.first().languageTag
        val voiceName = system.voices
            .filter { it.languageTag.equals(languageTag, ignoreCase = true) }
            .firstOrNull { it.name == system.voiceName }
            ?.name
            ?: system.voices.first { it.languageTag.equals(languageTag, ignoreCase = true) }.name
        return system.copy(languageTag = languageTag, voiceName = voiceName)
    }

    private fun persistSystemSelectionIfChanged(
        previous: SystemTtsSettingsState,
        current: SystemTtsSettingsState
    ) {
        if (previous.languageTag != current.languageTag) {
            AppModel.ttsSystemLanguageTag = current.languageTag
        }
        if (previous.voiceName != current.voiceName) {
            AppModel.ttsSystemVoiceName = current.voiceName
        }
    }
}
