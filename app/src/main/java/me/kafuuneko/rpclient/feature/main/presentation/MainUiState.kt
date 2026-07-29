package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainGenerationParameter
import me.kafuuneko.rpclient.feature.main.model.MainImportCharacterItem

/** 应用首页状态树，组合最近会话、全局设置和批量操作对话框。 */
sealed class MainUiState {
    data object None : MainUiState()

    data class Normal(
        val selectedPage: MainPage = MainPage.Home,
        val homeState: MainHomeState,
        val settingsState: MainSettingsState,
        val dialogState: MainDialogState = MainDialogState.None
    ) : MainUiState()

    data class Finished(val previous: MainUiState) : MainUiState()

    companion object {
        fun finished(previous: MainUiState): MainUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

internal fun MainUiState.Normal.mergeResumeRefresh(
    homeState: MainHomeState,
    settingsState: MainSettingsState
): MainUiState.Normal {
    return copy(
        homeState = homeState.preserveCollapsedGroupsFrom(this.homeState),
        settingsState = settingsState.copy(
            chatDataManagementState = this.settingsState.chatDataManagementState
        )
    )
}

internal fun MainUiState.Normal.canOpenDialog(): Boolean {
    return dialogState == MainDialogState.None &&
        settingsState.chatDataManagementState == MainChatDataManagementState.Idle
}

/** 首页互斥显示的确认对话框。 */
sealed class MainDialogState {
    data object None : MainDialogState()
    data class DeleteSelectedSessions(
        val count: Int
    ) : MainDialogState()

    data class EditGenerationParameter(
        val parameter: MainGenerationParameter,
        val draftValue: String
    ) : MainDialogState()

    data class ImportChatCharacterSelection(
        val title: String,
        val sourceCharacterName: String,
        val messageCount: Int,
        val query: String,
        val characters: List<MainImportCharacterItem>,
        val visibleCharacters: List<MainImportCharacterItem>,
        val selectedCharacterId: Long?,
        val isImporting: Boolean = false
    ) : MainDialogState()
}

/** 首页底部导航对应的一级页面。 */
enum class MainPage {
    Home,
    Settings
}
