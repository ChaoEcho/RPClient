package me.kafuuneko.rpclient.feature.imagegeneration.presentation

/** UI state for the OpenAI-compatible image-generation settings page. */
sealed class ImageGenerationSettingsUiState {
    data object None : ImageGenerationSettingsUiState()

    data class Normal(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val size: String,
        val stylePrompt: String,
        val selectedProviderId: Long = 0L,
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
