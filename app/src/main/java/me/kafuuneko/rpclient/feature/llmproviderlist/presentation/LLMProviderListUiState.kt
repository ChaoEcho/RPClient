package me.kafuuneko.rpclient.feature.llmproviderlist.presentation

import me.kafuuneko.rpclient.feature.llmproviderlist.model.LLMProviderListItem

/** 模型配置列表页状态。 */
sealed class LLMProviderListUiState {
    data object None : LLMProviderListUiState()

    data class Normal(
        val providers: List<LLMProviderListItem>,
        val loadState: LLMProviderListLoadState = LLMProviderListLoadState.None,
        val dialogState: LLMProviderListDialogState = LLMProviderListDialogState.None
    ) : LLMProviderListUiState()

    data class Finished(val previous: LLMProviderListUiState) : LLMProviderListUiState()

    companion object {
        fun finished(previous: LLMProviderListUiState): LLMProviderListUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 模型配置列表页当前显示的业务对话框。 */
sealed class LLMProviderListDialogState {
    data object None : LLMProviderListDialogState()

    data class DeleteProvider(
        val providerId: Long,
        val providerName: String,
        val associatedCharacterCount: Int,
        val isDeleting: Boolean = false
    ) : LLMProviderListDialogState()
}

/** 模型配置加载或启停更新状态。 */
sealed class LLMProviderListLoadState {
    data object None : LLMProviderListLoadState()
    data object Loading : LLMProviderListLoadState()
}
