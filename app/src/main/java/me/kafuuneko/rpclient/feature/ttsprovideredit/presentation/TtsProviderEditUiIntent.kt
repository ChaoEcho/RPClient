package me.kafuuneko.rpclient.feature.ttsprovideredit.presentation

import me.kafuuneko.rpclient.libs.tts.TtsProviderType

/** 单个语音服务详情页的用户操作。写入即生效，没有保存态。 */
sealed class TtsProviderEditUiIntent {
    data class Init(val provider: TtsProviderType) : TtsProviderEditUiIntent()
    data object Back : TtsProviderEditUiIntent()

    data class SelectSystemLanguage(val languageTag: String) : TtsProviderEditUiIntent()
    data class SelectSystemVoice(val voiceName: String) : TtsProviderEditUiIntent()
    data class ChangeSystemSpeechRate(val value: Float) : TtsProviderEditUiIntent()
    data class ChangeSystemPitch(val value: Float) : TtsProviderEditUiIntent()

    data class ChangeMimoBaseUrl(val value: String) : TtsProviderEditUiIntent()
    data class ChangeMimoApiKey(val value: String) : TtsProviderEditUiIntent()
    data class ChangeMimoModel(val value: String) : TtsProviderEditUiIntent()
    data class ChangeMimoVoice(val value: String) : TtsProviderEditUiIntent()
    data class ChangeMimoInstructions(val value: String) : TtsProviderEditUiIntent()
    data class ChangeMimoTemperature(val value: Float) : TtsProviderEditUiIntent()
    data class ChangeMimoStreaming(val value: Boolean) : TtsProviderEditUiIntent()

    data class ChangeAzureApiKey(val value: String) : TtsProviderEditUiIntent()
    data class ChangeAzureRegion(val value: String) : TtsProviderEditUiIntent()
    data class ChangeAzureVoice(val value: String) : TtsProviderEditUiIntent()
    data class ChangeAzureSpeechRate(val value: Float) : TtsProviderEditUiIntent()
}
