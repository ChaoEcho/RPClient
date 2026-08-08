package me.kafuuneko.rpclient.feature.story.list.presentation

import me.kafuuneko.rpclient.feature.story.list.model.StoryListItem

/** Story 列表页状态树。 */
sealed class StoryListUiState {
    data object None : StoryListUiState()

    data class Normal(
        val contentState: StoryListContentState = StoryListContentState.Loading,
        val dialogState: StoryListDialogState = StoryListDialogState.None
    ) : StoryListUiState()

    data class Finished(val previous: StoryListUiState) : StoryListUiState()

    companion object {
        fun finished(previous: StoryListUiState): StoryListUiState {
            return if (previous is Finished) previous else Finished(previous)
        }
    }
}

/** Story 列表内容的加载、空列表和正常列表状态。 */
sealed class StoryListContentState {
    data object Loading : StoryListContentState()
    data object Empty : StoryListContentState()
    data class Content(val stories: List<StoryListItem>) : StoryListContentState()
}

/** Story 新建、重命名和删除确认对话框状态。 */
sealed class StoryListDialogState {
    data object None : StoryListDialogState()

    data class EditTitle(
        val storyId: Long?,
        val title: String,
        val isSaving: Boolean = false
    ) : StoryListDialogState()

    data class DeleteStory(
        val storyId: Long,
        val title: String,
        val isDeleting: Boolean = false
    ) : StoryListDialogState()
}
