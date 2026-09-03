package me.kafuuneko.rpclient.feature.tts.presentation

import me.kafuuneko.rpclient.libs.tts.TtsProviderType

/** 语音服务列表页状态。 */
sealed class TtsSettingsUiState {
    data object None : TtsSettingsUiState()

    data class Normal(
        val providers: List<TtsProviderListItem>,
        val previewState: TtsPreviewState = TtsPreviewState.Idle
    ) : TtsSettingsUiState()

    data class Finished(val previous: TtsSettingsUiState) : TtsSettingsUiState()
}

/**
 * 列表项。
 *
 * [isConfigured] 只回答"这条现在能不能用"：系统朗读永远可用，另外两条要有密钥。
 */
data class TtsProviderListItem(
    val provider: TtsProviderType,
    val isCurrent: Boolean,
    val isConfigured: Boolean
)

sealed class TtsPreviewState {
    data object Idle : TtsPreviewState()
    data object Loading : TtsPreviewState()
    data object Playing : TtsPreviewState()
}
