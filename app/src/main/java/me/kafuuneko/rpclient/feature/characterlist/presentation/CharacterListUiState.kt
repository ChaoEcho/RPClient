package me.kafuuneko.rpclient.feature.characterlist.presentation

import me.kafuuneko.rpclient.feature.characterlist.model.CharacterListItem

/** 角色列表页状态；Normal 只强引用当前可见项的缩略图。 */
sealed class CharacterListUiState {
    data object None : CharacterListUiState()

    data class Normal(
        val loadState: CharacterListLoadState = CharacterListLoadState.None,
        val searchText: String = "",
        val selectedCharacterId: Long? = null,
        val characters: List<CharacterListItem> = emptyList(),
        val dialogState: CharacterListDialogState = CharacterListDialogState.None
    ) : CharacterListUiState()

    data class Finished(val previous: CharacterListUiState) : CharacterListUiState()

    companion object {
        fun finished(previous: CharacterListUiState): CharacterListUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 角色列表页对话框状态。 */
sealed class CharacterListDialogState {
    data object None : CharacterListDialogState()

    data class LowEmbeddedLorebookBudgetConfirm(
        val importedTokenBudget: Int,
        val affectedCharacterCount: Int
    ) : CharacterListDialogState()

    data class BatchImportResult(
        val successCount: Int,
        val failureCount: Int
    ) : CharacterListDialogState()

}

/** 角色列表读取或导入期间的阻塞状态。 */
sealed class CharacterListLoadState {
    data object None : CharacterListLoadState()
    data object Loading : CharacterListLoadState()
    data class Importing(
        val stage: CharacterImportStage,
        val completedCount: Int,
        val totalCount: Int
    ) : CharacterListLoadState()
}

/** 批量角色卡导入的当前处理阶段。 */
enum class CharacterImportStage {
    Reading,
    Saving
}
