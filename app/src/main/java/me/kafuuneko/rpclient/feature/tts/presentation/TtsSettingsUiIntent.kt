package me.kafuuneko.rpclient.feature.tts.presentation

import me.kafuuneko.rpclient.libs.tts.TtsProviderType

/** User intents for the global text-to-speech settings page. */
sealed class TtsSettingsUiIntent {
    data object Init : TtsSettingsUiIntent()
    data object Back : TtsSettingsUiIntent()
    data class SelectProvider(val provider: TtsProviderType) : TtsSettingsUiIntent()

    data class SelectSystemLanguage(val languageTag: String) : TtsSettingsUiIntent()
    data class SelectSystemVoice(val voiceName: String) : TtsSettingsUiIntent()
    data class ChangeSystemSpeechRate(val value: Float) : TtsSettingsUiIntent()
    data class ChangeSystemPitch(val value: Float) : TtsSettingsUiIntent()

    data class ChangeMimoBaseUrl(val value: String) : TtsSettingsUiIntent()
    data class ChangeMimoApiKey(val value: String) : TtsSettingsUiIntent()
    data class ChangeMimoModel(val value: String) : TtsSettingsUiIntent()
    data class ChangeMimoVoice(val value: String) : TtsSettingsUiIntent()
    data class ChangeMimoInstructions(val value: String) : TtsSettingsUiIntent()
    data class ChangeMimoTemperature(val value: Float) : TtsSettingsUiIntent()

    data class ChangeAzureApiKey(val value: String) : TtsSettingsUiIntent()
    data class ChangeAzureRegion(val value: String) : TtsSettingsUiIntent()
    data class ChangeAzureVoice(val value: String) : TtsSettingsUiIntent()
    data class ChangeAzureSpeechRate(val value: Float) : TtsSettingsUiIntent()

    data class PreviewSpeech(val text: String) : TtsSettingsUiIntent()
    data object StopPreview : TtsSettingsUiIntent()
}
