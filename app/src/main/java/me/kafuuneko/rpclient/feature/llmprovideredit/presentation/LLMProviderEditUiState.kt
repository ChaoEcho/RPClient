package me.kafuuneko.rpclient.feature.llmprovideredit.presentation

import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderEditForm
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogFailure
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel

/** 模型配置创建/编辑页面状态树。 */
sealed class LLMProviderEditUiState {
    data object None : LLMProviderEditUiState()

    /** 模型配置表单、连接测试和未保存确认的稳定页面状态。 */
    data class Normal(
        val mode: LLMProviderEditMode,
        val form: LLMProviderEditForm,
        val initialForm: LLMProviderEditForm = form,
        val loadState: LLMProviderEditLoadState = LLMProviderEditLoadState.None,
        val testState: LLMProviderEditTestState = LLMProviderEditTestState.None,
        val requestExtensionsState: LLMProviderEditRequestExtensionsState =
            LLMProviderEditRequestExtensionsState(),
        val modelCatalogState: LLMProviderEditModelCatalogState =
            LLMProviderEditModelCatalogState.Idle,
        val dialogState: LLMProviderEditDialogState = LLMProviderEditDialogState.None
    ) : LLMProviderEditUiState()

    data class Finished(val previous: LLMProviderEditUiState) : LLMProviderEditUiState()

    companion object {
        fun finished(previous: LLMProviderEditUiState): LLMProviderEditUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 请求扩展面板的可渲染状态，避免 Compose 直接解析模型配置 JSON。 */
data class LLMProviderEditRequestExtensionsState(
    val isOpenRouter: Boolean = false,
    val usesPreferredProvider: Boolean = false,
    val preferredProvider: String = "",
    val allowFallbacks: Boolean = true
)

/** 模型配置页面当前是新增还是编辑。 */
enum class LLMProviderEditMode {
    Create,
    Edit
}

/** 模型配置保存操作状态。 */
sealed class LLMProviderEditLoadState {
    data object None : LLMProviderEditLoadState()
    data object Saving : LLMProviderEditLoadState()
}

/** 最小生成请求连接测试的生命周期与结果。 */
sealed class LLMProviderEditTestState {
    data object None : LLMProviderEditTestState()
    data object Testing : LLMProviderEditTestState()
    data class Success(val message: String) : LLMProviderEditTestState()
    data object Failed : LLMProviderEditTestState()
}

/** 模型目录查询的生命周期与可渲染结果。 */
sealed class LLMProviderEditModelCatalogState {
    data object Idle : LLMProviderEditModelCatalogState()
    data object Loading : LLMProviderEditModelCatalogState()
    data class Loaded(
        val models: List<LLMAvailableModel>
    ) : LLMProviderEditModelCatalogState()
    data class Failed(
        val failure: LLMModelCatalogFailure
    ) : LLMProviderEditModelCatalogState()
}

/** 模型配置编辑页互斥显示的确认对话框。 */
sealed class LLMProviderEditDialogState {
    data object None : LLMProviderEditDialogState()
    data object UnsavedChangesConfirm : LLMProviderEditDialogState()
    data object ApiKeyEditor : LLMProviderEditDialogState()
    data class CustomHeadersEditor(val initialValue: String = "") : LLMProviderEditDialogState()
    data class RequestBodyPatchEditor(val initialValue: String) : LLMProviderEditDialogState()
    data class ModelPicker(
        val searchQuery: String,
        val items: List<LLMAvailableModel>
    ) : LLMProviderEditDialogState()
}
