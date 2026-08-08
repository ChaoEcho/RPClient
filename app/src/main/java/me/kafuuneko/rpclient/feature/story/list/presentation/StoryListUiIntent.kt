package me.kafuuneko.rpclient.feature.story.list.presentation

/** Story 列表页可接收的用户行为。 */
sealed class StoryListUiIntent {
    data object Init : StoryListUiIntent()
    data object Resume : StoryListUiIntent()
    data object Back : StoryListUiIntent()
    data class OpenStory(val storyId: Long) : StoryListUiIntent()
    data object ShowCreateStoryDialog : StoryListUiIntent()
    data class ShowRenameStoryDialog(val storyId: Long) : StoryListUiIntent()
    data class ChangeTitleDraft(val value: String) : StoryListUiIntent()
    data object ConfirmTitle : StoryListUiIntent()
    data class ShowDeleteStoryDialog(val storyId: Long) : StoryListUiIntent()
    data object ConfirmDeleteStory : StoryListUiIntent()
    data object DismissDialog : StoryListUiIntent()
}
