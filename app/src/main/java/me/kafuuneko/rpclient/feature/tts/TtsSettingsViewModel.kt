package me.kafuuneko.rpclient.feature.tts

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.tts.presentation.AzureTtsSettingsState
import me.kafuuneko.rpclient.feature.tts.presentation.MimoTtsSettingsState
import me.kafuuneko.rpclient.feature.tts.presentation.SystemTtsSettingsState
import me.kafuuneko.rpclient.feature.tts.presentation.TtsPreviewState
import me.kafuuneko.rpclient.feature.tts.presentation.TtsSettingsUiIntent
import me.kafuuneko.rpclient.feature.tts.presentation.TtsSettingsUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.tts.TtsProviderType
import me.kafuuneko.rpclient.libs.tts.TtsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Owns global TTS preferences and coordinates voice preview playback. */
class TtsSettingsViewModel : CoreViewModelWithEvent<TtsSettingsUiIntent, TtsSettingsUiState>(
    TtsSettingsUiState.None
), KoinComponent {
    private val mTtsService by inject<TtsService>()
    private var mPreviewJob: Job? = null
    private var mPreviewGeneration = 0L

    @UiIntentObserver(TtsSettingsUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<TtsSettingsUiState.None>()) return

        val initialState = TtsSettingsUiState.Normal(
            selectedProvider = TtsProviderType.fromPersistedValue(AppModel.ttsProvider),
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
        )
        initialState.setup()

        viewModelScope.launch {
            val voices = runCatching { mTtsService.getSystemVoices() }.getOrElse { emptyList() }
            val state = getOrNull<TtsSettingsUiState.Normal>() ?: return@launch
            val system = normalizeSystemVoiceSelection(state.system.copy(voices = voices))
            persistSystemSelectionIfChanged(state.system, system)
            state.copy(system = system).setup()
        }
    }

    @UiIntentObserver(TtsSettingsUiIntent.Back::class)
    private fun onBack() {
        stopPreviewInternal()
        val currentState = uiStateFlow.value
        if (currentState is TtsSettingsUiState.Finished) return
        TtsSettingsUiState.Finished(currentState).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.SelectProvider::class)
    private fun onSelectProvider(intent: TtsSettingsUiIntent.SelectProvider) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsProvider = intent.provider.persistedValue
        state.copy(selectedProvider = intent.provider).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.SelectSystemLanguage::class)
    private fun onSelectSystemLanguage(intent: TtsSettingsUiIntent.SelectSystemLanguage) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
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

    @UiIntentObserver(TtsSettingsUiIntent.SelectSystemVoice::class)
    private fun onSelectSystemVoice(intent: TtsSettingsUiIntent.SelectSystemVoice) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        val voice = state.system.voices.firstOrNull { it.name == intent.voiceName } ?: return
        AppModel.ttsSystemLanguageTag = voice.languageTag
        AppModel.ttsSystemVoiceName = voice.name
        state.copy(
            system = state.system.copy(languageTag = voice.languageTag, voiceName = voice.name)
        ).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeSystemSpeechRate::class)
    private fun onChangeSystemSpeechRate(intent: TtsSettingsUiIntent.ChangeSystemSpeechRate) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsSystemSpeechRate = intent.value
        state.copy(system = state.system.copy(speechRate = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeSystemPitch::class)
    private fun onChangeSystemPitch(intent: TtsSettingsUiIntent.ChangeSystemPitch) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsSystemPitch = intent.value
        state.copy(system = state.system.copy(pitch = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeMimoBaseUrl::class)
    private fun onChangeMimoBaseUrl(intent: TtsSettingsUiIntent.ChangeMimoBaseUrl) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsMimoBaseUrl = intent.value
        state.copy(mimo = state.mimo.copy(baseUrl = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeMimoApiKey::class)
    private fun onChangeMimoApiKey(intent: TtsSettingsUiIntent.ChangeMimoApiKey) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsMimoApiKey = intent.value
        state.copy(mimo = state.mimo.copy(apiKey = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeMimoModel::class)
    private fun onChangeMimoModel(intent: TtsSettingsUiIntent.ChangeMimoModel) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsMimoModel = intent.value
        state.copy(mimo = state.mimo.copy(model = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeMimoVoice::class)
    private fun onChangeMimoVoice(intent: TtsSettingsUiIntent.ChangeMimoVoice) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsMimoVoice = intent.value
        state.copy(mimo = state.mimo.copy(voice = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeMimoInstructions::class)
    private fun onChangeMimoInstructions(intent: TtsSettingsUiIntent.ChangeMimoInstructions) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsMimoInstructions = intent.value
        state.copy(mimo = state.mimo.copy(instructions = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeMimoTemperature::class)
    private fun onChangeMimoTemperature(intent: TtsSettingsUiIntent.ChangeMimoTemperature) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsMimoTemperature = intent.value
        state.copy(mimo = state.mimo.copy(temperature = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeMimoStreaming::class)
    private fun onChangeMimoStreaming(intent: TtsSettingsUiIntent.ChangeMimoStreaming) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsMimoStreaming = intent.value
        state.copy(mimo = state.mimo.copy(streaming = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeAzureApiKey::class)
    private fun onChangeAzureApiKey(intent: TtsSettingsUiIntent.ChangeAzureApiKey) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsAzureApiKey = intent.value
        state.copy(azure = state.azure.copy(apiKey = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeAzureRegion::class)
    private fun onChangeAzureRegion(intent: TtsSettingsUiIntent.ChangeAzureRegion) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsAzureRegion = intent.value
        state.copy(azure = state.azure.copy(region = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeAzureVoice::class)
    private fun onChangeAzureVoice(intent: TtsSettingsUiIntent.ChangeAzureVoice) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsAzureVoice = intent.value
        state.copy(azure = state.azure.copy(voice = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.ChangeAzureSpeechRate::class)
    private fun onChangeAzureSpeechRate(intent: TtsSettingsUiIntent.ChangeAzureSpeechRate) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsAzureSpeechRate = intent.value
        state.copy(azure = state.azure.copy(speechRate = intent.value)).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.PreviewSpeech::class)
    private fun onPreviewSpeech(intent: TtsSettingsUiIntent.PreviewSpeech) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        if (intent.text.isBlank()) return

        stopPreviewInternal()
        mPreviewGeneration += 1L
        val generation = mPreviewGeneration
        val job = viewModelScope.launch {
            state.copy(previewState = TtsPreviewState.Loading).setup()
            try {
                mTtsService.speak(intent.text) {
                    if (mPreviewGeneration == generation) {
                        val current = getOrNull<TtsSettingsUiState.Normal>() ?: return@speak
                        current.copy(previewState = TtsPreviewState.Playing).setup()
                    }
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (error: Throwable) {
                if (mPreviewGeneration == generation) {
                    val message = error.message?.takeIf { it.isNotBlank() }
                    if (message != null) {
                        AppViewEvent.PopupToastMessage(message).tryEmit()
                    } else {
                        AppViewEvent.PopupToastMessageByResId(R.string.tts_preview_failed).tryEmit()
                    }
                }
            } finally {
                if (mPreviewGeneration == generation) {
                    val current = getOrNull<TtsSettingsUiState.Normal>()
                    current?.copy(previewState = TtsPreviewState.Idle)?.setup()
                }
            }
        }
        mPreviewJob = job
    }

    @UiIntentObserver(TtsSettingsUiIntent.StopPreview::class)
    private fun onStopPreview() {
        stopPreviewInternal()
    }

    override fun onCleared() {
        stopPreviewInternal()
        super.onCleared()
    }

    private fun stopPreviewInternal() {
        mPreviewGeneration += 1L
        mPreviewJob?.cancel()
        mPreviewJob = null
        mTtsService.stop()
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        if (state.previewState != TtsPreviewState.Idle) {
            state.copy(previewState = TtsPreviewState.Idle).setup()
        }
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
