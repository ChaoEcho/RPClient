package me.kafuuneko.rpclient.feature.imageproviderlist

import android.os.Bundle
import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.imageprovideredit.ImageProviderEditActivity
import me.kafuuneko.rpclient.feature.imageproviderlist.model.ImageProviderListItem
import me.kafuuneko.rpclient.feature.imageproviderlist.presentation.ImagePromptProviderItem
import me.kafuuneko.rpclient.feature.imageproviderlist.presentation.ImageProviderListDialogState
import me.kafuuneko.rpclient.feature.imageproviderlist.presentation.ImageProviderListUiIntent
import me.kafuuneko.rpclient.feature.imageproviderlist.presentation.ImageProviderListUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.ImageProviderRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 图片服务列表页状态持有者。
 *
 * 除了服务列表本身，还承载两项与"用哪条服务出图"无关的全局设定：
 * 场景提示词模型与风格提示词。它们按 TTS 页的做法逐键即时写入，页面上没有保存按钮。
 */
class ImageProviderListViewModel : CoreViewModelWithEvent<
    ImageProviderListUiIntent,
    ImageProviderListUiState
>(ImageProviderListUiState.None), KoinComponent {
    private val mImageProviderRepository by inject<ImageProviderRepository>()
    private val mLLMRepository by inject<LLMRepository>()

    @UiIntentObserver(ImageProviderListUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<ImageProviderListUiState.None>()) return
        ImageProviderListUiState.Normal(
            providers = emptyList(),
            promptProviderId = AppModel.imagePromptLLMProvider,
            sceneStylePrompt = AppModel.imageGenerationStylePrompt,
            avatarStylePrompt = AppModel.imageGenerationAvatarStylePrompt,
            isLoading = true
        ).setup()
        refreshPromptProviders()
        refreshProviders()
    }

    @UiIntentObserver(ImageProviderListUiIntent.Resume::class)
    private suspend fun onResume() {
        if (!isStateOf<ImageProviderListUiState.Normal>()) return
        refreshProviders()
    }

    @UiIntentObserver(ImageProviderListUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<ImageProviderListUiState.Finished>()) return
        ImageProviderListUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(ImageProviderListUiIntent.CreateProvider::class)
    private fun onCreateProvider() {
        if (!isStateOf<ImageProviderListUiState.Normal>()) return
        AppViewEvent.StartActivity(ImageProviderEditActivity::class.java).tryEmit()
    }

    @UiIntentObserver(ImageProviderListUiIntent.EditProvider::class)
    private fun onEditProvider(intent: ImageProviderListUiIntent.EditProvider) {
        if (!isStateOf<ImageProviderListUiState.Normal>()) return
        AppViewEvent.StartActivity(
            activity = ImageProviderEditActivity::class.java,
            extras = Bundle().apply {
                putLong(ImageProviderEditActivity.EXTRA_PROVIDER_ID, intent.providerId)
            }
        ).tryEmit()
    }

    @UiIntentObserver(ImageProviderListUiIntent.SelectCurrentProvider::class)
    private suspend fun onSelectCurrentProvider(intent: ImageProviderListUiIntent.SelectCurrentProvider) {
        if (!isStateOf<ImageProviderListUiState.Normal>()) return
        mImageProviderRepository.updateCurrentProvider(intent.providerId)
        refreshProviders()
    }

    @UiIntentObserver(ImageProviderListUiIntent.ShowDeleteProviderDialog::class)
    private fun onShowDeleteProviderDialog(intent: ImageProviderListUiIntent.ShowDeleteProviderDialog) {
        val uiState = getOrNull<ImageProviderListUiState.Normal>() ?: return
        val provider = uiState.providers.firstOrNull { it.id == intent.providerId } ?: return
        uiState.copy(
            dialogState = ImageProviderListDialogState.DeleteProvider(
                providerId = provider.id,
                providerName = provider.name
            )
        ).setup()
    }

    @UiIntentObserver(ImageProviderListUiIntent.ConfirmDeleteProvider::class)
    private suspend fun onConfirmDeleteProvider() {
        val uiState = getOrNull<ImageProviderListUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? ImageProviderListDialogState.DeleteProvider ?: return
        if (dialogState.isDeleting) return
        uiState.copy(dialogState = dialogState.copy(isDeleting = true)).setup()
        try {
            mImageProviderRepository.deleteProvider(dialogState.providerId)
            getOrNull<ImageProviderListUiState.Normal>()
                ?.copy(dialogState = ImageProviderListDialogState.None)
                ?.setup()
            refreshProviders()
            AppViewEvent.PopupToastMessageByResId(R.string.image_provider_deleted).tryEmit()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.image_provider_delete_failed).tryEmit()
            val current = getOrNull<ImageProviderListUiState.Normal>() ?: return
            val currentDialog = current.dialogState as? ImageProviderListDialogState.DeleteProvider
                ?: return
            current.copy(dialogState = currentDialog.copy(isDeleting = false)).setup()
        }
    }

    @UiIntentObserver(ImageProviderListUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<ImageProviderListUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? ImageProviderListDialogState.DeleteProvider ?: return
        if (dialogState.isDeleting) return
        uiState.copy(dialogState = ImageProviderListDialogState.None).setup()
    }

    @UiIntentObserver(ImageProviderListUiIntent.ChangePromptProvider::class)
    private fun onChangePromptProvider(intent: ImageProviderListUiIntent.ChangePromptProvider) {
        val uiState = getOrNull<ImageProviderListUiState.Normal>() ?: return
        AppModel.imagePromptLLMProvider = intent.providerId
        uiState.copy(promptProviderId = intent.providerId).setup()
    }

    @UiIntentObserver(ImageProviderListUiIntent.ChangeSceneStylePrompt::class)
    private fun onChangeSceneStylePrompt(intent: ImageProviderListUiIntent.ChangeSceneStylePrompt) {
        val uiState = getOrNull<ImageProviderListUiState.Normal>() ?: return
        AppModel.imageGenerationStylePrompt = intent.value
        uiState.copy(sceneStylePrompt = intent.value).setup()
    }

    @UiIntentObserver(ImageProviderListUiIntent.ChangeAvatarStylePrompt::class)
    private fun onChangeAvatarStylePrompt(intent: ImageProviderListUiIntent.ChangeAvatarStylePrompt) {
        val uiState = getOrNull<ImageProviderListUiState.Normal>() ?: return
        AppModel.imageGenerationAvatarStylePrompt = intent.value
        uiState.copy(avatarStylePrompt = intent.value).setup()
    }

    /** 拉取可用于场景提炼的已启用对话模型，并修正指向已删除模型的旧选择。 */
    private suspend fun refreshPromptProviders() {
        val providers = try {
            mLLMRepository.getEnabledProviders()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        val uiState = getOrNull<ImageProviderListUiState.Normal>() ?: return
        val enabledIds = providers.map { it.id }.toSet()
        val validatedId = uiState.promptProviderId.takeIf { it == 0L || it in enabledIds } ?: 0L
        uiState.copy(
            promptProviderId = validatedId,
            promptProviders = providers.map {
                ImagePromptProviderItem(id = it.id, name = it.name, model = it.model)
            }
        ).setup()
    }

    private suspend fun refreshProviders() {
        val uiState = getOrNull<ImageProviderListUiState.Normal>() ?: return
        val providers = mImageProviderRepository.getAllProviders()
        val currentId = AppModel.currentImageProvider
        uiState.copy(
            providers = providers.map { provider ->
                ImageProviderListItem(
                    id = provider.id,
                    name = provider.name,
                    baseUrl = provider.baseUrl,
                    model = provider.model,
                    maxConcurrentRequests = provider.maxConcurrentRequests,
                    isCurrent = provider.id == currentId
                )
            },
            isLoading = false
        ).setup()
    }
}
