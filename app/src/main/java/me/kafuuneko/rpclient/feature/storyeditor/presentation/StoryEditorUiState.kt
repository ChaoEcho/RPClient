package me.kafuuneko.rpclient.feature.storyeditor.presentation

import me.kafuuneko.rpclient.feature.storyeditor.model.StoryCharacterOptionItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterDestination
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterOutlineItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryLorebookGroupItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryImportPreview
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryStructureTitleTarget
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryVolumeOutlineItem
import me.kafuuneko.rpclient.libs.prompt.PromptInspection

/** 章节编辑器页面状态树；当前章节正文由独立文档状态桥接，不复制到此处。 */
sealed class StoryEditorUiState {
    data object None : StoryEditorUiState()

    data class Normal(
        val storyId: Long,
        val topBarState: StoryEditorTopBarState,
        val contentState: StoryEditorContentState,
        val structureState: StoryEditorStructureState,
        val referenceState: StoryEditorReferenceState,
        val continuationInputState: StoryContinuationInputState = StoryContinuationInputState(),
        val generationState: StoryGenerationState = StoryGenerationState.Idle,
        val canUndoEdit: Boolean = false,
        val canRedoEdit: Boolean = false,
        val hasPromptInspection: Boolean = false,
        val hasAvailableProvider: Boolean = true,
        val pageState: StoryEditorPageState = StoryEditorPageState.Editor,
        val dialogState: StoryEditorDialogState = StoryEditorDialogState.None
    ) : StoryEditorUiState()

    data class Finished(val previous: StoryEditorUiState) : StoryEditorUiState()

    companion object {
        fun finished(previous: StoryEditorUiState): StoryEditorUiState {
            return previous as? Finished ?: Finished(previous)
        }
    }
}

/** 编辑器顶部栏标题与保存状态。 */
data class StoryEditorTopBarState(
    val title: String,
    val saveState: StorySaveState = StorySaveState.Saved
)

/** 正文区域的轻量渲染状态；正文内容不存入 UiState。 */
data class StoryEditorContentState(
    val characterCount: Int,
    val editable: Boolean = true
)

/** 当前 Story 的轻量分卷/章节结构与编辑定位，不包含任何章节正文。 */
data class StoryEditorStructureState(
    val currentChapterId: Long,
    val currentChapterTitle: String,
    val currentVolumeId: Long? = null,
    val currentVolumeTitle: String? = null,
    val ungroupedChapters: List<StoryChapterOutlineItem> = emptyList(),
    val volumes: List<StoryVolumeOutlineItem> = emptyList(),
    val isUpdating: Boolean = false
)

/** 当前故事已配置的上下文来源摘要。 */
data class StoryEditorReferenceState(
    val hasMemory: Boolean,
    val hasAuthorNote: Boolean,
    val characterCount: Int,
    val lorebookEntryCount: Int
)

/** 编辑器底栏中归属于当前章节并持续用于普通续写的引导草稿。 */
data class StoryContinuationInputState(
    val guidanceDraft: String = ""
)

/** 正文自动保存状态。冲突草稿只保留在 ViewModel，不进入可重放 UiState。 */
sealed class StorySaveState {
    data object Saved : StorySaveState()
    data object Dirty : StorySaveState()
    data object Saving : StorySaveState()
    data object Failed : StorySaveState()
    data object Conflict : StorySaveState()
}

/** 一轮续写从准备、流式接收到应用结果的互斥状态。 */
sealed class StoryGenerationState {
    data object Idle : StoryGenerationState()
    data object Preparing : StoryGenerationState()
    data class Streaming(
        val partialText: String,
        val phase: StoryGenerationPhase,
        /** 生成开始时的单调时钟值，仅用于计算当前任务耗时。 */
        val startedAtElapsedRealtime: Long,
        val reasoningPreview: String = "",
        val reasoningDetail: String = "",
        val isReasoningExpanded: Boolean = false
    ) : StoryGenerationState()
    data object Applying : StoryGenerationState()
    data class Failed(
        val reason: StoryGenerationFailure,
        val recoverablePartial: String = "",
        val detail: String = ""
    ) : StoryGenerationState()
}

/** 流式故事生成中由真实协议事件驱动的当前阶段。 */
enum class StoryGenerationPhase {
    AwaitingResponse,
    Connected,
    Reasoning,
    Writing
}

/** 用户可恢复或重试的续写失败类型。 */
enum class StoryGenerationFailure {
    Setup,
    Provider,
    ApplyResult,
    Conflict,
    EmptyResult,
    ContextBudget
}

/** 故事设置页的可选分区。 */
enum class StorySettingsSection {
    Context,
    Characters,
    Lorebook
}

/** 编辑器与全屏设置之间的页面状态。 */
sealed class StoryEditorPageState {
    data object Editor : StoryEditorPageState()
    data object Outline : StoryEditorPageState()
    data object LoadingSettings : StoryEditorPageState()

    data class Settings(
        val selectedSection: StorySettingsSection = StorySettingsSection.Context,
        val memory: String,
        val summary: String,
        val authorNote: String,
        val includeUserPersona: Boolean,
        val characters: List<StoryCharacterOptionItem>,
        val lorebookGroups: List<StoryLorebookGroupItem>,
        val isSaving: Boolean = false
    ) : StoryEditorPageState()
}

/** 编辑器业务对话框状态。 */
sealed class StoryEditorDialogState {
    data object None : StoryEditorDialogState()
    data object NoProviderGuide : StoryEditorDialogState()
    data class PromptInspector(val inspection: PromptInspection) : StoryEditorDialogState()
    data object FileActions : StoryEditorDialogState()
    data class ImportPreview(val preview: StoryImportPreview) : StoryEditorDialogState()
    data class StructureTitleEditor(
        val target: StoryStructureTitleTarget,
        val title: String,
        val isSaving: Boolean = false
    ) : StoryEditorDialogState()
    data class DeleteVolume(
        val volumeId: Long,
        val title: String,
        val isSaving: Boolean = false
    ) : StoryEditorDialogState()
    data class DeleteChapter(
        val chapterId: Long,
        val title: String,
        val isSaving: Boolean = false
    ) : StoryEditorDialogState()
    data class MoveChapter(
        val chapterId: Long,
        val title: String,
        val selectedDestination: StoryChapterDestination,
        val isSaving: Boolean = false
    ) : StoryEditorDialogState()
    data object SummarizingStory : StoryEditorDialogState()
    data class StorySummaryPreview(
        val content: String,
        val sourceStoryRevision: Long,
        val sourceChapterId: Long,
        val sourceChapterRevision: Long
    ) : StoryEditorDialogState()
}
