package me.kafuuneko.rpclient.feature.imagegeneration.presentation

/** User intents for the OpenAI-compatible image-generation settings page. */
sealed class ImageGenerationSettingsUiIntent {
    data object Init : ImageGenerationSettingsUiIntent()
    data object Back : ImageGenerationSettingsUiIntent()

    data class ChangeBaseUrl(val value: String) : ImageGenerationSettingsUiIntent()
    data class ChangeApiKey(val value: String) : ImageGenerationSettingsUiIntent()
    data class ChangeModel(val value: String) : ImageGenerationSettingsUiIntent()
    data class ChangeSize(val value: String) : ImageGenerationSettingsUiIntent()
    data class ChangeStylePrompt(val value: String) : ImageGenerationSettingsUiIntent()
}
