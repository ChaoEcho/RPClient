package me.kafuuneko.rpclient.feature.imageproviderlist.presentation

/** 图片服务列表页的用户意图。 */
sealed class ImageProviderListUiIntent {
    data object Init : ImageProviderListUiIntent()

    data object Resume : ImageProviderListUiIntent()

    data object Back : ImageProviderListUiIntent()

    data object CreateProvider : ImageProviderListUiIntent()

    data class EditProvider(val providerId: Long) : ImageProviderListUiIntent()

    data class SelectCurrentProvider(val providerId: Long) : ImageProviderListUiIntent()

    data class ShowDeleteProviderDialog(val providerId: Long) : ImageProviderListUiIntent()

    data object ConfirmDeleteProvider : ImageProviderListUiIntent()

    data object DismissDialog : ImageProviderListUiIntent()

    data class ChangePromptProvider(val providerId: Long) : ImageProviderListUiIntent()

    data class ChangeSceneStylePrompt(val value: String) : ImageProviderListUiIntent()

    data class ChangeAvatarStylePrompt(val value: String) : ImageProviderListUiIntent()
}
