package me.kafuuneko.rpclient.feature.imageproviderlist.presentation

import me.kafuuneko.rpclient.feature.imageproviderlist.model.ImageProviderListItem

/** 图片服务列表页状态。 */
sealed class ImageProviderListUiState {
    data object None : ImageProviderListUiState()

    data class Normal(
        val providers: List<ImageProviderListItem>,
        // 以下三项是与"用哪条服务出图"无关的全局设定，因此留在列表页而不是每条服务里。
        val promptProviderId: Long = 0L,
        val promptProviders: List<ImagePromptProviderItem> = emptyList(),
        val sceneStylePrompt: String = "",
        val avatarStylePrompt: String = "",
        val isLoading: Boolean = false,
        val dialogState: ImageProviderListDialogState = ImageProviderListDialogState.None
    ) : ImageProviderListUiState()

    data class Finished(val previous: ImageProviderListUiState) : ImageProviderListUiState()

    companion object {
        fun finished(previous: ImageProviderListUiState): ImageProviderListUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 图片服务列表页当前显示的业务对话框。 */
sealed class ImageProviderListDialogState {
    data object None : ImageProviderListDialogState()

    data class DeleteProvider(
        val providerId: Long,
        val providerName: String,
        val isDeleting: Boolean = false
    ) : ImageProviderListDialogState()
}

/** 场景提示词模型选择器使用的已启用对话模型摘要。 */
data class ImagePromptProviderItem(
    val id: Long,
    val name: String,
    val model: String
)
