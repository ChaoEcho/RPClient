package me.kafuuneko.rpclient.feature.storyeditor.presentation

import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorSnapshot
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryCharacterActivationMode
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryTextExportFormat
import android.net.Uri

/** 连续正文编辑器可接收的用户行为和生命周期意图。 */
sealed class StoryEditorUiIntent {
    data class Init(val storyId: Long) : StoryEditorUiIntent()
    data object Back : StoryEditorUiIntent()
    data class EditorSnapshotChanged(val snapshot: StoryEditorSnapshot) : StoryEditorUiIntent()
    data object FlushDraft : StoryEditorUiIntent()
    data object RetrySave : StoryEditorUiIntent()
    data object CopyConflictDraft : StoryEditorUiIntent()
    data object ReloadAfterConflict : StoryEditorUiIntent()
    data object OpenStorySettings : StoryEditorUiIntent()
    data object CloseStorySettings : StoryEditorUiIntent()
    data class SelectSettingsSection(val section: StorySettingsSection) : StoryEditorUiIntent()
    data class ChangeMemory(val value: String) : StoryEditorUiIntent()
    data class ChangeSummary(val value: String) : StoryEditorUiIntent()
    data class ChangeAuthorNote(val value: String) : StoryEditorUiIntent()
    data class SetIncludeUserPersona(val enabled: Boolean) : StoryEditorUiIntent()
    data object SummarizeStory : StoryEditorUiIntent()
    data object CancelStorySummary : StoryEditorUiIntent()
    data object ConfirmStorySummary : StoryEditorUiIntent()
    data class ToggleStoryCharacter(val characterId: Long) : StoryEditorUiIntent()
    data class SetCharacterActivationMode(
        val characterId: Long,
        val activationMode: StoryCharacterActivationMode
    ) : StoryEditorUiIntent()
    data class MoveStoryCharacter(val characterId: Long, val offset: Int) : StoryEditorUiIntent()
    data class ToggleLorebook(val lorebookId: Long) : StoryEditorUiIntent()
    data class ToggleLorebookEntry(val entryId: Long) : StoryEditorUiIntent()
    data object SaveStorySettings : StoryEditorUiIntent()
    data class ChangeContinuationGuidance(val value: String) : StoryEditorUiIntent()
    data class ContinueStory(val snapshot: StoryEditorSnapshot) : StoryEditorUiIntent()
    data object StopGeneration : StoryEditorUiIntent()
    data object InsertRecoverablePartial : StoryEditorUiIntent()
    data object CopyRecoverablePartial : StoryEditorUiIntent()
    data object DiscardRecoverablePartial : StoryEditorUiIntent()
    data object UndoLastEdit : StoryEditorUiIntent()
    data object RedoLastEdit : StoryEditorUiIntent()
    data object OpenPromptInspector : StoryEditorUiIntent()
    data class CopyPromptItem(val text: String) : StoryEditorUiIntent()
    data object OpenFileActions : StoryEditorUiIntent()
    data object ImportTextClick : StoryEditorUiIntent()
    data class ImportTextResult(val uri: Uri) : StoryEditorUiIntent()
    data object ImportStoryClick : StoryEditorUiIntent()
    data class ImportStoryResult(val uri: Uri) : StoryEditorUiIntent()
    data class ChangeImportTitle(val value: String) : StoryEditorUiIntent()
    data object ConfirmImport : StoryEditorUiIntent()
    data class ExportTextClick(val format: StoryTextExportFormat) : StoryEditorUiIntent()
    data class ExportTextResult(val uri: Uri) : StoryEditorUiIntent()
    data object ExportStoryClick : StoryEditorUiIntent()
    data class ExportStoryResult(val uri: Uri) : StoryEditorUiIntent()
    data object DismissDialog : StoryEditorUiIntent()
}
