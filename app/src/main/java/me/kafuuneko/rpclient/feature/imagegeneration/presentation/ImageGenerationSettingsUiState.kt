package me.kafuuneko.rpclient.feature.imagegeneration.presentation

/** Editable draft form for OpenAI-compatible image-generation settings. */
data class ImageGenerationSettingsForm(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val size: String = "",
    val sceneStylePrompt: String = "",
    val avatarStylePrompt: String = "",
    val promptProviderId: Long = 0L,
    val maxConcurrentRequests: String = ""
)

/** UI state for the OpenAI-compatible image-generation settings page. */
sealed class ImageGenerationSettingsUiState {
    data object None : ImageGenerationSettingsUiState()

    data class Normal(
        val form: ImageGenerationSettingsForm,
        val providers: List<ImagePromptProviderItem> = emptyList()
    ) : ImageGenerationSettingsUiState()

    data class Finished(val previous: ImageGenerationSettingsUiState) : ImageGenerationSettingsUiState()
}

/** Minimal enabled LLM provider summary shown by the image scene prompt selector. */
data class ImagePromptProviderItem(
    val id: Long,
    val name: String,
    val model: String
)
