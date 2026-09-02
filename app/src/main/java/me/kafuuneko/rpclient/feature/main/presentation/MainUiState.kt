package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainHomeItemSelection

/** 应用首页状态树，组合最近内容、全局设置和批量操作对话框。 */
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
        settingsState = settingsState
    )
}

internal fun MainUiState.Normal.canOpenDialog(): Boolean = dialogState == MainDialogState.None

/** 首页互斥显示的确认对话框。 */
sealed class MainDialogState {
    data object None : MainDialogState()

    data class DeleteSelectedItems(
        val count: Int,
        val isDeleting: Boolean = false
    ) : MainDialogState()

    data class RenameItem(
        val item: MainHomeItemSelection,
        val title: String,
        val isSaving: Boolean = false
    ) : MainDialogState()

    data class EditUserDescription(
        val draftText: String
    ) : MainDialogState()

}

/** 首页底部导航对应的一级页面。 */
enum class MainPage {
    Home,
    Settings
}
