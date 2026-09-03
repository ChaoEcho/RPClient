package me.kafuuneko.rpclient.feature.imageprovideredit.presentation

import me.kafuuneko.rpclient.libs.llm.catalog.ModelCatalogState
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel

/** 图片服务创建/编辑页面状态树。 */
sealed class ImageProviderEditUiState {
    data object None : ImageProviderEditUiState()

    data class Normal(
        val isCreateMode: Boolean,
        val form: ImageProviderEditForm,
        val initialForm: ImageProviderEditForm = form,
        val isSaving: Boolean = false,
        val modelCatalogState: ModelCatalogState = ModelCatalogState.Idle,
        val dialogState: ImageProviderEditDialogState = ImageProviderEditDialogState.None
    ) : ImageProviderEditUiState()

    data class Finished(val previous: ImageProviderEditUiState) : ImageProviderEditUiState()

    companion object {
        fun finished(previous: ImageProviderEditUiState): ImageProviderEditUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 图片服务表单。并发数按字符串保存，校验推迟到保存时统一提示。 */
data class ImageProviderEditForm(
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val size: String = "",
    val maxConcurrentRequests: String = ""
)

/** 图片服务编辑页互斥显示的弹窗。 */
sealed class ImageProviderEditDialogState {
    data object None : ImageProviderEditDialogState()
    data object UnsavedChangesConfirm : ImageProviderEditDialogState()
    data class ModelPicker(val items: List<LLMAvailableModel>) : ImageProviderEditDialogState()
}
