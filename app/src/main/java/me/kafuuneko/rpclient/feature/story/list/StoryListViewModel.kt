package me.kafuuneko.rpclient.feature.story.list

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.story.editor.StoryEditorActivity
import me.kafuuneko.rpclient.feature.story.list.model.StoryListItem
import me.kafuuneko.rpclient.feature.story.list.presentation.StoryListContentState
import me.kafuuneko.rpclient.feature.story.list.presentation.StoryListDialogState
import me.kafuuneko.rpclient.feature.story.list.presentation.StoryListUiIntent
import me.kafuuneko.rpclient.feature.story.list.presentation.StoryListUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.StoryRepository
import me.kafuuneko.rpclient.libs.utils.formatTimestamp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Story 列表状态持有者，协调 CRUD 和编辑器导航。 */
class StoryListViewModel : CoreViewModelWithEvent<StoryListUiIntent, StoryListUiState>(
    StoryListUiState.None
), KoinComponent {
    private val mStoryRepository by inject<StoryRepository>()

    @UiIntentObserver(StoryListUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<StoryListUiState.None>()) return
        StoryListUiState.Normal().setup()
        refreshStories()
    }

    @UiIntentObserver(StoryListUiIntent.Resume::class)
    private suspend fun onResume() {
        if (!isStateOf<StoryListUiState.Normal>()) return
        refreshStories()
    }

    @UiIntentObserver(StoryListUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<StoryListUiState.Finished>()) return
        StoryListUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(StoryListUiIntent.OpenStory::class)
    private fun onOpenStory(intent: StoryListUiIntent.OpenStory) {
        val uiState = getOrNull<StoryListUiState.Normal>() ?: return
        if (uiState.findStory(intent.storyId) == null) return
        openStory(intent.storyId)
    }

    @UiIntentObserver(StoryListUiIntent.ShowCreateStoryDialog::class)
    private fun onShowCreateStoryDialog() {
        val uiState = getOrNull<StoryListUiState.Normal>() ?: return
        if (uiState.dialogState != StoryListDialogState.None) return
        uiState.copy(dialogState = StoryListDialogState.EditTitle(null, "")).setup()
    }

    @UiIntentObserver(StoryListUiIntent.ShowRenameStoryDialog::class)
    private fun onShowRenameStoryDialog(intent: StoryListUiIntent.ShowRenameStoryDialog) {
        val uiState = getOrNull<StoryListUiState.Normal>() ?: return
        if (uiState.dialogState != StoryListDialogState.None) return
        val story = uiState.findStory(intent.storyId) ?: return
        uiState.copy(
            dialogState = StoryListDialogState.EditTitle(story.id, story.title)
        ).setup()
    }

    @UiIntentObserver(StoryListUiIntent.ChangeTitleDraft::class)
    private fun onChangeTitleDraft(intent: StoryListUiIntent.ChangeTitleDraft) {
        val uiState = getOrNull<StoryListUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? StoryListDialogState.EditTitle ?: return
        if (dialog.isSaving) return
        uiState.copy(dialogState = dialog.copy(title = intent.value)).setup()
    }

    @UiIntentObserver(StoryListUiIntent.ConfirmTitle::class)
    private suspend fun onConfirmTitle() {
        val uiState = getOrNull<StoryListUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? StoryListDialogState.EditTitle ?: return
        val title = dialog.title.trim()
        if (title.isEmpty() || dialog.isSaving) return
        uiState.copy(dialogState = dialog.copy(isSaving = true)).setup()
        try {
            val storyId = withContext(Dispatchers.IO) {
                if (dialog.storyId == null) {
                    mStoryRepository.createStory(title)
                } else {
                    mStoryRepository.renameStory(dialog.storyId, title)
                    dialog.storyId
                }
            }
            val current = getOrNull<StoryListUiState.Normal>() ?: return
            current.copy(dialogState = StoryListDialogState.None).setup()
            refreshStories()
            if (dialog.storyId == null) openStory(storyId)
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_save_failed).tryEmit()
            val current = getOrNull<StoryListUiState.Normal>() ?: return
            val currentDialog = current.dialogState as? StoryListDialogState.EditTitle ?: return
            current.copy(dialogState = currentDialog.copy(isSaving = false)).setup()
        }
    }

    @UiIntentObserver(StoryListUiIntent.ShowDeleteStoryDialog::class)
    private fun onShowDeleteStoryDialog(intent: StoryListUiIntent.ShowDeleteStoryDialog) {
        val uiState = getOrNull<StoryListUiState.Normal>() ?: return
        if (uiState.dialogState != StoryListDialogState.None) return
        val story = uiState.findStory(intent.storyId) ?: return
        uiState.copy(
            dialogState = StoryListDialogState.DeleteStory(story.id, story.title)
        ).setup()
    }

    @UiIntentObserver(StoryListUiIntent.ConfirmDeleteStory::class)
    private suspend fun onConfirmDeleteStory() {
        val uiState = getOrNull<StoryListUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? StoryListDialogState.DeleteStory ?: return
        if (dialog.isDeleting) return
        uiState.copy(dialogState = dialog.copy(isDeleting = true)).setup()
        try {
            withContext(Dispatchers.IO) {
                mStoryRepository.deleteStory(dialog.storyId)
            }
            val current = getOrNull<StoryListUiState.Normal>() ?: return
            current.copy(dialogState = StoryListDialogState.None).setup()
            refreshStories()
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_delete_failed).tryEmit()
            val current = getOrNull<StoryListUiState.Normal>() ?: return
            val currentDialog = current.dialogState as? StoryListDialogState.DeleteStory ?: return
            current.copy(dialogState = currentDialog.copy(isDeleting = false)).setup()
        }
    }

    @UiIntentObserver(StoryListUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<StoryListUiState.Normal>() ?: return
        val busy = when (val dialog = uiState.dialogState) {
            is StoryListDialogState.EditTitle -> dialog.isSaving
            is StoryListDialogState.DeleteStory -> dialog.isDeleting
            StoryListDialogState.None -> false
        }
        if (busy) return
        uiState.copy(dialogState = StoryListDialogState.None).setup()
    }

    private suspend fun refreshStories() {
        val uiState = getOrNull<StoryListUiState.Normal>() ?: return
        val items = withContext(Dispatchers.IO) {
            mStoryRepository.getStoryOverviews().map { story ->
                StoryListItem(
                    id = story.id,
                    title = story.title,
                    preview = story.preview.replace(Regex("\\s+"), " ").trim(),
                    characterCount = story.contentCharacterCount,
                    updatedAt = story.latestTime.formatTimestamp("MM-dd HH:mm")
                )
            }
        }
        val current = getOrNull<StoryListUiState.Normal>() ?: return
        current.copy(
            contentState = if (items.isEmpty()) {
                StoryListContentState.Empty
            } else {
                StoryListContentState.Content(items)
            }
        ).setup()
    }

    private fun openStory(storyId: Long) {
        AppViewEvent.StartActivity(
            activity = StoryEditorActivity::class.java,
            extras = Bundle().apply {
                putLong(StoryEditorActivity.EXTRA_STORY_ID, storyId)
            }
        ).tryEmit()
    }

    private fun StoryListUiState.Normal.findStory(storyId: Long): StoryListItem? {
        val content = contentState as? StoryListContentState.Content ?: return null
        return content.stories.firstOrNull { it.id == storyId }
    }
}
