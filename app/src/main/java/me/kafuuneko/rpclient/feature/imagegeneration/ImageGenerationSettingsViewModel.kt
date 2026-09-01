package me.kafuuneko.rpclient.feature.imagegeneration

import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImageGenerationSettingsUiIntent
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImageGenerationSettingsUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreViewModel
import me.kafuuneko.rpclient.libs.core.UiIntentObserver

/** Owns the persisted OpenAI-compatible image-generation settings. */
class ImageGenerationSettingsViewModel : CoreViewModel<
    ImageGenerationSettingsUiIntent,
    ImageGenerationSettingsUiState
>(ImageGenerationSettingsUiState.None) {
    @UiIntentObserver(ImageGenerationSettingsUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<ImageGenerationSettingsUiState.None>()) return

        ImageGenerationSettingsUiState.Normal(
            baseUrl = AppModel.imageGenerationBaseUrl,
            apiKey = AppModel.imageGenerationApiKey,
            model = AppModel.imageGenerationModel,
            size = AppModel.imageGenerationSize,
            stylePrompt = AppModel.imageGenerationStylePrompt
        ).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<ImageGenerationSettingsUiState.Finished>()) return
        ImageGenerationSettingsUiState.Finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.ChangeBaseUrl::class)
    private fun onChangeBaseUrl(intent: ImageGenerationSettingsUiIntent.ChangeBaseUrl) {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        AppModel.imageGenerationBaseUrl = intent.value
        state.copy(baseUrl = intent.value).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.ChangeApiKey::class)
    private fun onChangeApiKey(intent: ImageGenerationSettingsUiIntent.ChangeApiKey) {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        AppModel.imageGenerationApiKey = intent.value
        state.copy(apiKey = intent.value).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.ChangeModel::class)
    private fun onChangeModel(intent: ImageGenerationSettingsUiIntent.ChangeModel) {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        AppModel.imageGenerationModel = intent.value
        state.copy(model = intent.value).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.ChangeSize::class)
    private fun onChangeSize(intent: ImageGenerationSettingsUiIntent.ChangeSize) {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        AppModel.imageGenerationSize = intent.value
        state.copy(size = intent.value).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.ChangeStylePrompt::class)
    private fun onChangeStylePrompt(intent: ImageGenerationSettingsUiIntent.ChangeStylePrompt) {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        AppModel.imageGenerationStylePrompt = intent.value
        state.copy(stylePrompt = intent.value).setup()
    }
}
