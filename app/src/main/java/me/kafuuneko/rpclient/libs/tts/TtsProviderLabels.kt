package me.kafuuneko.rpclient.libs.tts

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.R

/** 服务名文案，语音列表页与详情页共用。 */
@StringRes
fun TtsProviderType.titleRes(): Int = when (this) {
    TtsProviderType.System -> R.string.tts_provider_system
    TtsProviderType.Mimo -> R.string.tts_provider_mimo
    TtsProviderType.Azure -> R.string.tts_provider_azure
}

/** 服务的一句话说明。 */
@StringRes
fun TtsProviderType.descriptionRes(): Int = when (this) {
    TtsProviderType.System -> R.string.tts_provider_system_description
    TtsProviderType.Mimo -> R.string.tts_provider_mimo_description
    TtsProviderType.Azure -> R.string.tts_provider_azure_description
}
