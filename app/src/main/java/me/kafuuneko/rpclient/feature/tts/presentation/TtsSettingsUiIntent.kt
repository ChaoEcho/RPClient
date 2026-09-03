package me.kafuuneko.rpclient.feature.tts.presentation

import me.kafuuneko.rpclient.libs.tts.TtsProviderType

/** 语音服务列表页的用户操作。具体参数改在详情页，这里只管选用与试听。 */
sealed class TtsSettingsUiIntent {
    data object Init : TtsSettingsUiIntent()
    data object Resume : TtsSettingsUiIntent()
    data object Back : TtsSettingsUiIntent()
    data class SelectProvider(val provider: TtsProviderType) : TtsSettingsUiIntent()
    data class OpenProviderEdit(val provider: TtsProviderType) : TtsSettingsUiIntent()
    data class PreviewSpeech(val text: String) : TtsSettingsUiIntent()
    data object StopPreview : TtsSettingsUiIntent()
}
