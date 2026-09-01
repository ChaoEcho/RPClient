package me.kafuuneko.rpclient.feature.imagegeneration

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImageGenerationSettingsForm
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImageGenerationSettingsUiIntent
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImageGenerationSettingsUiState
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImagePromptProviderItem
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Owns the draft editing and persisted state of OpenAI-compatible image-generation settings. */
class ImageGenerationSettingsViewModel : CoreViewModelWithEvent<
    ImageGenerationSettingsUiIntent,
    ImageGenerationSettingsUiState
>(ImageGenerationSettingsUiState.None), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()

    @UiIntentObserver(ImageGenerationSettingsUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<ImageGenerationSettingsUiState.None>()) return

        val initialForm = ImageGenerationSettingsForm(
            baseUrl = AppModel.imageGenerationBaseUrl,
            apiKey = AppModel.imageGenerationApiKey,
            model = AppModel.imageGenerationModel,
            size = AppModel.imageGenerationSize,
            sceneStylePrompt = AppModel.imageGenerationStylePrompt,
            avatarStylePrompt = AppModel.imageGenerationAvatarStylePrompt,
            promptProviderId = AppModel.imagePromptLLMProvider
        )

        ImageGenerationSettingsUiState.Normal(form = initialForm).setup()

        viewModelScope.launch {
            val providers = try {
                withContext(Dispatchers.IO) { mLLMRepository.getEnabledProviders() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return@launch
            val enabledProviderIds = providers.map { it.id }.toSet()
            val validatedProviderId = state.form.promptProviderId
                .takeIf { it == 0L || it in enabledProviderIds }
                ?: 0L
            state.copy(
                form = state.form.copy(promptProviderId = validatedProviderId),
                providers = providers.map { provider ->
                    ImagePromptProviderItem(
                        id = provider.id,
                        name = provider.name,
                        model = provider.model
                    )
                }
            ).setup()
        }
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<ImageGenerationSettingsUiState.Finished>()) return
        ImageGenerationSettingsUiState.Finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.ChangePromptProvider::class)
    private fun onChangePromptProvider(intent: ImageGenerationSettingsUiIntent.ChangePromptProvider) {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        state.copy(form = state.form.copy(promptProviderId = intent.providerId)).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.ChangeBaseUrl::class)
    private fun onChangeBaseUrl(intent: ImageGenerationSettingsUiIntent.ChangeBaseUrl) {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        state.copy(form = state.form.copy(baseUrl = intent.value)).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.ChangeApiKey::class)
    private fun onChangeApiKey(intent: ImageGenerationSettingsUiIntent.ChangeApiKey) {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        state.copy(form = state.form.copy(apiKey = intent.value)).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.ChangeModel::class)
    private fun onChangeModel(intent: ImageGenerationSettingsUiIntent.ChangeModel) {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        state.copy(form = state.form.copy(model = intent.value)).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.ChangeSize::class)
    private fun onChangeSize(intent: ImageGenerationSettingsUiIntent.ChangeSize) {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        state.copy(form = state.form.copy(size = intent.value)).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.ChangeSceneStylePrompt::class)
    private fun onChangeSceneStylePrompt(intent: ImageGenerationSettingsUiIntent.ChangeSceneStylePrompt) {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        state.copy(form = state.form.copy(sceneStylePrompt = intent.value)).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.ChangeAvatarStylePrompt::class)
    private fun onChangeAvatarStylePrompt(intent: ImageGenerationSettingsUiIntent.ChangeAvatarStylePrompt) {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        state.copy(form = state.form.copy(avatarStylePrompt = intent.value)).setup()
    }

    @UiIntentObserver(ImageGenerationSettingsUiIntent.Save::class)
    private fun onSave() {
        val state = getOrNull<ImageGenerationSettingsUiState.Normal>() ?: return
        val form = state.form
        AppModel.imageGenerationBaseUrl = form.baseUrl.trim()
        AppModel.imageGenerationApiKey = form.apiKey.trim()
        AppModel.imageGenerationModel = form.model.trim()
        AppModel.imageGenerationSize = form.size.trim()
        AppModel.imageGenerationStylePrompt = form.sceneStylePrompt
        AppModel.imageGenerationAvatarStylePrompt = form.avatarStylePrompt
        AppModel.imagePromptLLMProvider = form.promptProviderId

        AppViewEvent.PopupToastMessageByResId(R.string.image_generation_saved).tryEmit()
    }
}
