package me.kafuuneko.rpclient.feature.ttsprovideredit.presentation

import me.kafuuneko.rpclient.libs.tts.TtsProviderType
import me.kafuuneko.rpclient.libs.tts.TtsVoice

/** 语音服务详情页状态树。三种服务共用一个页面，按 [Normal.provider] 渲染其中一份配置。 */
sealed class TtsProviderEditUiState {
    data object None : TtsProviderEditUiState()

    data class Normal(
        val provider: TtsProviderType,
        val system: SystemTtsSettingsState,
        val mimo: MimoTtsSettingsState,
        val azure: AzureTtsSettingsState
    ) : TtsProviderEditUiState()

    data class Finished(val previous: TtsProviderEditUiState) : TtsProviderEditUiState()
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
    val temperature: Float,
    val streaming: Boolean
)

data class AzureTtsSettingsState(
    val apiKey: String,
    val region: String,
    val voice: String,
    val speechRate: Float
)
