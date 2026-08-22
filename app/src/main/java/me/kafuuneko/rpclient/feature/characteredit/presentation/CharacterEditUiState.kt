package me.kafuuneko.rpclient.feature.characteredit.presentation

import me.kafuuneko.rpclient.feature.characteredit.model.CharacterEditForm
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterLorebookItem
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterProviderItem
import androidx.compose.ui.graphics.ImageBitmap

/** 角色创建/编辑页面状态树。 */
sealed class CharacterEditUiState {
    data object None : CharacterEditUiState()

    /**
     * 角色表单稳定状态。
     *
     * [initialForm] 用于离开页面时判断未保存变更；世界书和模型配置列表只提供绑定选择，
     * 具体配置内容仍由各自的独立管理页面维护。
     */
    data class Normal(
        val mode: CharacterEditMode,
        val form: CharacterEditForm,
        val initialForm: CharacterEditForm = form,
        val loadState: CharacterEditLoadState = CharacterEditLoadState.None,
        val dialogState: CharacterEditDialogState = CharacterEditDialogState.None,
        val avatarImage: ImageBitmap? = null,
        val availableLorebooks: List<CharacterLorebookItem> = emptyList(),
        val availableProviders: List<CharacterProviderItem> = emptyList()
    ) : CharacterEditUiState()

    data class Finished(val previous: CharacterEditUiState) : CharacterEditUiState()

    companion object {
        fun finished(previous: CharacterEditUiState): CharacterEditUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 角色页面当前是新增还是编辑已有角色。 */
enum class CharacterEditMode {
    Create,
    Edit
}

/** 角色加载、保存与删除操作状态。 */
sealed class CharacterEditLoadState {
    data object None : CharacterEditLoadState()
    data object Loading : CharacterEditLoadState()
    data object Saving : CharacterEditLoadState()
    data object Deleting : CharacterEditLoadState()
}

/** 角色编辑页当前显示的业务对话框。 */
sealed class CharacterEditDialogState {
    data object None : CharacterEditDialogState()

    data class DeleteConfirm(
        val characterName: String
    ) : CharacterEditDialogState()

    data class DeleteWithLorebookConfirm(
        val characterName: String,
        val lorebookId: Long,
        val lorebookName: String
    ) : CharacterEditDialogState()

    data class PromptEditor(
        val field: CharacterPromptField,
        val draftText: String
    ) : CharacterEditDialogState()

    data object UnsavedChangesConfirm : CharacterEditDialogState()
}

/** 可通过全屏 Prompt 编辑器修改的角色字段。 */
sealed class CharacterPromptField {
    data object Description : CharacterPromptField()
    data object Personality : CharacterPromptField()
    data object Scenario : CharacterPromptField()
    data class FirstMessage(val index: Int) : CharacterPromptField()
    data object DialogueExamples : CharacterPromptField()
    data object SystemPrompt : CharacterPromptField()
    data object PostHistoryInstructions : CharacterPromptField()
    data object DepthPrompt : CharacterPromptField()
    data class AlternateGreeting(val index: Int) : CharacterPromptField()
}
