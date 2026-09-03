package me.kafuuneko.rpclient.feature.imageprovideredit.presentation

/** 图片服务编辑页可响应的用户操作。 */
sealed class ImageProviderEditUiIntent {
    data class Init(val providerId: Long?) : ImageProviderEditUiIntent()

    data object Back : ImageProviderEditUiIntent()

    data object DiscardChanges : ImageProviderEditUiIntent()

    data object DismissDialog : ImageProviderEditUiIntent()

    data class ChangeName(val value: String) : ImageProviderEditUiIntent()

    data class ChangeBaseUrl(val value: String) : ImageProviderEditUiIntent()

    data class ChangeApiKey(val value: String) : ImageProviderEditUiIntent()

    data class ChangeModel(val value: String) : ImageProviderEditUiIntent()

    data class ChangeSize(val value: String) : ImageProviderEditUiIntent()

    data class ChangeMaxConcurrentRequests(val value: String) : ImageProviderEditUiIntent()

    data object QueryModels : ImageProviderEditUiIntent()

    data object CancelModelQuery : ImageProviderEditUiIntent()

    data object ShowModelPicker : ImageProviderEditUiIntent()

    data class SelectAvailableModel(val modelId: String) : ImageProviderEditUiIntent()

    data object SaveClick : ImageProviderEditUiIntent()
}
