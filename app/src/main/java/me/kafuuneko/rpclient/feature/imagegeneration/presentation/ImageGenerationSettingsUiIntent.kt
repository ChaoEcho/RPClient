package me.kafuuneko.rpclient.feature.imagegeneration.presentation

/** User intents for the OpenAI-compatible image-generation settings page. */
sealed class ImageGenerationSettingsUiIntent {
    data object Init : ImageGenerationSettingsUiIntent()
    data object Back : ImageGenerationSettingsUiIntent()
    data object Save : ImageGenerationSettingsUiIntent()

    data class ChangeBaseUrl(val value: String) : ImageGenerationSettingsUiIntent()
    data class ChangeApiKey(val value: String) : ImageGenerationSettingsUiIntent()
    data class ChangeModel(val value: String) : ImageGenerationSettingsUiIntent()
    data class ChangeSize(val value: String) : ImageGenerationSettingsUiIntent()
    data class ChangeSceneStylePrompt(val value: String) : ImageGenerationSettingsUiIntent()
    data class ChangeAvatarStylePrompt(val value: String) : ImageGenerationSettingsUiIntent()
    data class ChangeMaxConcurrentRequests(val value: String) : ImageGenerationSettingsUiIntent()
    data class ChangePromptProvider(val providerId: Long) : ImageGenerationSettingsUiIntent()
}
