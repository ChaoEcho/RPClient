package me.kafuuneko.rpclient.feature.imagegeneration.presentation

/** UI state for the OpenAI-compatible image-generation settings page. */
sealed class ImageGenerationSettingsUiState {
    data object None : ImageGenerationSettingsUiState()

    data class Normal(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val size: String,
        val stylePrompt: String
    ) : ImageGenerationSettingsUiState()

    data class Finished(val previous: ImageGenerationSettingsUiState) : ImageGenerationSettingsUiState()
}
