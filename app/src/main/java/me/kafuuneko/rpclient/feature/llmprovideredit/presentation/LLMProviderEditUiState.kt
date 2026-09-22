package me.kafuuneko.rpclient.feature.llmprovideredit.presentation

import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderEditForm
import me.kafuuneko.rpclient.libs.llm.catalog.ModelCatalogState
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel

/** 模型配置创建/编辑页面状态树。 */
sealed class LLMProviderEditUiState {
    data object None : LLMProviderEditUiState()

    /** 模型配置表单、连接测试和未保存确认的稳定页面状态。 */
    data class Normal(
        /** 当前流程采用的处理模式。 */
        val mode: LLMProviderEditMode,
        /** 当前页面正在编辑的表单数据。 */
        val form: LLMProviderEditForm,
        /** ViewModel 根据用户设置与可信目录解析，Compose 只控制显示。 */
        val showImageTokenEstimator: Boolean = true,
        /** 进入编辑页时保存的初始表单快照。 */
        val initialForm: LLMProviderEditForm = form,
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: LLMProviderEditLoadState = LLMProviderEditLoadState.None,
        /** 正则脚本编辑页测试区域的状态。 */
        val testState: LLMProviderEditTestState = LLMProviderEditTestState.None,
        /** 高级请求体和 OpenRouter 路由选项的编辑状态。 */
        val requestExtensionsState: LLMProviderEditRequestExtensionsState =
            LLMProviderEditRequestExtensionsState(),
        val modelCatalogState: ModelCatalogState = ModelCatalogState.Idle,
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
    /** 当前模型配置是否使用 OpenRouter 供应商能力。 */
    val isOpenRouter: Boolean = false,
    /** 当前高级请求配置是否包含首选供应商。 */
    val usesPreferredProvider: Boolean = false,
    /** OpenRouter 路由时优先选择的上游供应商。 */
    val preferredProvider: String = "",
    /** 首选上游不可用时是否允许 OpenRouter 回退。 */
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
    data class Success(val message: String?) : LLMProviderEditTestState()
    data object Failed : LLMProviderEditTestState()
}

/** 模型配置编辑页互斥显示的确认对话框。 */
sealed class LLMProviderEditDialogState {
    data object None : LLMProviderEditDialogState()
    data object UnsavedChangesConfirm : LLMProviderEditDialogState()
    data object ApiKeyEditor : LLMProviderEditDialogState()
    data class CustomHeadersEditor(val initialValue: String = "") : LLMProviderEditDialogState()
    data class RequestBodyPatchEditor(val initialValue: String) : LLMProviderEditDialogState()
    data class ModelPicker(
        /** 当前列表使用的搜索关键词。 */
        val searchQuery: String = "",
        /** 当前状态包含的列表项。 */
        val items: List<LLMAvailableModel>
    ) : LLMProviderEditDialogState()
}
