package me.kafuuneko.rpclient.feature.tts.presentation

import me.kafuuneko.rpclient.libs.tts.TtsProviderType
import me.kafuuneko.rpclient.libs.tts.TtsVoice

/** UI state for global text-to-speech settings. */
sealed class TtsSettingsUiState {
    data object None : TtsSettingsUiState()

    data class Normal(
        val selectedProvider: TtsProviderType,
        val system: SystemTtsSettingsState,
        val mimo: MimoTtsSettingsState,
        val azure: AzureTtsSettingsState,
        val previewState: TtsPreviewState = TtsPreviewState.Idle
    ) : TtsSettingsUiState()

    data class Finished(val previous: TtsSettingsUiState) : TtsSettingsUiState()
}

data class SystemTtsSettingsState(
    val languageTag: String,
    val voiceName: String,
    val speechRate: Float,
    val pitch: Float,
    val voices: List<TtsVoice>
)

data class MimoTtsSettingsState(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val voice: String,
    val instructions: String,
    val temperature: Float
)

data class AzureTtsSettingsState(
    val apiKey: String,
    val region: String,
    val voice: String,
    val speechRate: Float
)

sealed class TtsPreviewState {
    data object Idle : TtsPreviewState()
    data object Loading : TtsPreviewState()
    data object Playing : TtsPreviewState()
}
