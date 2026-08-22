package me.kafuuneko.rpclient.feature.storyeditor

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryCharacterOptionItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryCharacterActivationMode
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditHistory
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorDocument
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorSnapshot
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditedTextRange
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryLorebookEntryItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryLorebookGroupItem
import me.kafuuneko.rpclient.feature.storyeditor.model.enableLorebook
import me.kafuuneko.rpclient.feature.storyeditor.model.restoreLorebookSelection
import me.kafuuneko.rpclient.feature.storyeditor.model.toggleLorebook
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryImportPreview
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryTextExportFormat
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryUndoEntry
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorContentState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorDialogState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorPageState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorReferenceState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorTopBarState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiIntent
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorViewEvent
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StorySaveState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationFailure
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.StoryCharacterSelection
import me.kafuuneko.rpclient.libs.room.repository.StoryRepository
import me.kafuuneko.rpclient.libs.room.repository.StoryGeneratedEdit
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.story.StoryArchiveRepository
import me.kafuuneko.rpclient.libs.story.StoryEditTarget
import me.kafuuneko.rpclient.libs.story.StoryOutputSanitizer
import me.kafuuneko.rpclient.libs.story.StoryPromptBuilder
import me.kafuuneko.rpclient.libs.story.StoryPromptContext
import me.kafuuneko.rpclient.libs.story.StoryPromptBudgetException
import me.kafuuneko.rpclient.libs.story.StorySummaryPromptBuilder
import me.kafuuneko.rpclient.libs.story.StoryImportDraft
import me.kafuuneko.rpclient.libs.story.prepareStoryContinuationText
import me.kafuuneko.rpclient.libs.story.storyTextHash
import me.kafuuneko.rpclient.libs.llm.GenerationFailure as LLMGenerationFailure
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.llm.NoEnabledLLMProviderException
import me.kafuuneko.rpclient.libs.llm.UnavailableLLMProviderSelectionException
import me.kafuuneko.rpclient.libs.llm.classifyGenerationFailure
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.prompt.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.summarySafeContent
import me.kafuuneko.rpclient.libs.AppModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 连续正文故事编辑器（Story Editor）的 ViewModel（状态持有者与业务控制器）。
 *
 * 核心设计与职责：
 * - 双轨草稿与防抖自动保存：
 *   - 内存草稿（`mDraftContent`）与持久化内容（`mPersistedContent`）解耦。
 *   - 自动保存采用乐观锁版本号（`mRevision`）机制，串行化保存防并发冲突；若发生冲突则保护内存草稿不被数据库旧数据覆盖。
 *   - 输入法组合态（`isComposing`）感知：在打字组合期间暂缓自动保存，待组合结束后按需触发。
 * - 连续文本 AI 续写（Continuation Generation）：
 *   - 支持流式与非流式调用、世界书递归扫描预算、角色卡上下文组装。
 *   - 异常与取消保障：支持“可恢复的局部生成内容”（Recoverable Partial），用户中断或生成失败时可保留或一键插入已生成的文本片段。
 * - 精细化撤销/重做（Undo/Redo）：
 *   - 通过 [StoryEditHistory] 记录手动编辑与 AI 生成编辑的差异及世界书时序快照，支持精准回退与重做。
 * - 故事设置与摘要：
 *   - 支持故事设定（Memory、Summary、Author Note）、角色绑定、世界书分配与顺序排序；
 *   - 支持针对长文本故事的增量/全量摘要生成与二次确认写入。
 * - 导入与导出：
 *   - 支持纯文本（TXT/Markdown）与 RPStory 打包文件（.rpstory.json）的双向导入导出。
 */
class StoryEditorViewModel : CoreViewModelWithEvent<StoryEditorUiIntent, StoryEditorUiState>(
    StoryEditorUiState.None
), KoinComponent {
    // 仓库与依赖注入
    private val mStoryRepository by inject<StoryRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mProviderSelectionResolver by inject<LLMProviderSelectionResolver>()
    private val mStoryPromptBuilder by inject<StoryPromptBuilder>()
    private val mStorySummaryPromptBuilder by inject<StorySummaryPromptBuilder>()
    private val mStoryOutputSanitizer by inject<StoryOutputSanitizer>()
    private val mStoryArchiveRepository by inject<StoryArchiveRepository>()

    /** 正文文档流，供 Compose 编辑器组件监听并双向同步文本内容与最新编辑范围。 */
    private val mDocumentFlow = MutableStateFlow<StoryEditorDocument?>(null)
    val documentFlow = mDocumentFlow.asStateFlow()

    /** 自动保存协程互斥锁，确保并发触发的保存操作串行执行。 */
    private val mSaveMutex = Mutex()
    /** 防抖自动保存任务 Job。 */
    private var mDebounceJob: Job? = null
    /** 当前故事数据库实体缓存。 */
    private var mStory: Story? = null
    /** 当前编辑器内存中的正文草稿。 */
    private var mDraftContent = ""
    /** 上一次成功持久化落库的正文内容。 */
    private var mPersistedContent = ""
    /** 当前正文的版本号（Revision），用于乐观锁冲突检测。 */
    private var mRevision = 0L
    /** 文档同步版本计数，用于通知 UI 强制刷新编辑器内容。 */
    private var mDocumentSyncVersion = 0L
    /** 当前是否处于输入法组合输入态（IME Composing）。 */
    private var mIsComposing = false
    /** 当前 AI 故事续写生成的协程 Job。 */
    private var mGenerationJob: Job? = null
    /** 当前故事摘要生成的协程 Job。 */
    private var mSummaryJob: Job? = null
    /** 正在进行中的活跃生成任务状态快照。 */
    private var mActiveGeneration: ActiveStoryGeneration? = null
    /** 被取消时的活跃生成快照缓存，便于收尾落库。 */
    private var mCancelledGeneration: ActiveStoryGeneration? = null
    /** 发生失败或中断时，可供用户手动挽救插入的局部生成内容快照。 */
    private var mRecoverableGeneration: ActiveStoryGeneration? = null
    /** 最近一次 Prompt 构建的检查分析报告。 */
    private var mLastPromptInspection: PromptInspection? = null
    /** 故事正文编辑历史记录器（支持 Undo/Redo）。 */
    private val mEditHistory = StoryEditHistory()

    /**
     * 初始化故事编辑器。
     *
     * 处理流程：
     * - 校验 storyId 有效性；
     * - 从数据库加载故事实体、关联角色数、世界书条目数；
     * - 初始化内存草稿、持久化镜像与版本号；
     * - 发布文档流并初始化 [StoryEditorUiState.Normal]。
     *
     * @param intent 包含 storyId 的初始化意图
     */
    @UiIntentObserver(StoryEditorUiIntent.Init::class)
    private suspend fun onInit(intent: StoryEditorUiIntent.Init) {
        if (!isStateOf<StoryEditorUiState.None>()) return
        // 校验故事 ID
        if (intent.storyId <= 0L) {
            finishMissingStory()
            return
        }
        // 异步从数据库加载故事基础数据与关联统计
        val loaded = withContext(Dispatchers.IO) {
            val story = mStoryRepository.getStory(intent.storyId) ?: return@withContext null
            val characterCount = mStoryRepository.getStoryCharacterCandidates(story.id).size
            val lorebookEntryCount = mStoryRepository.getLorebookEntryIds(story).size
            Triple(story, characterCount, lorebookEntryCount)
        } ?: run {
            finishMissingStory()
            return
        }
        val (story, characterCount, lorebookEntryCount) = loaded
        // 初始化本地状态快照与版本号
        mStory = story
        mDraftContent = story.content
        mPersistedContent = story.content
        mRevision = story.contentRevision
        // 向文档流发布初始文本
        publishDocument(story.content)
        // 建立初始 UI 状态
        StoryEditorUiState.Normal(
            storyId = story.id,
            topBarState = StoryEditorTopBarState(title = story.title),
            contentState = StoryEditorContentState(
                characterCount = story.content.length
            ),
            referenceState = StoryEditorReferenceState(
                hasMemory = story.memory.isNotBlank() || story.summary.isNotBlank(),
                hasAuthorNote = story.authorNote.isNotBlank(),
                characterCount = characterCount,
                lorebookEntryCount = lorebookEntryCount
            )
        ).setup()
    }

    /**
     * 响应编辑器文本与光标快照变化事件。
     *
     * @param intent 包含快照数据的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.EditorSnapshotChanged::class)
    private fun onEditorSnapshotChanged(intent: StoryEditorUiIntent.EditorSnapshotChanged) {
        acceptEditorSnapshot(intent.snapshot)
    }

    /**
     * 接收并处理编辑器组件同步上来的正文快照。
     *
     * 处理逻辑：
     * - 只读保护：若处于不可编辑态且内容被修改则直接忽略；
     * - 组合态监听：记录是否处于输入法组合输入中，若组合态刚结束且有未存草稿，则补发自动保存；
     * - 记录编辑历史：计算差异记录至 [StoryEditHistory] 用于撤销回退；
     * - 更新草稿状态：比对持久化镜像标记 [StorySaveState.Saved] 或 [StorySaveState.Dirty]；
     * - 防抖保存触发：若非组合态且内容脏，按防抖间隔触发自动保存。
     *
     * @param snapshot 来自 UI 编辑器的当前快照
     */
    private fun acceptEditorSnapshot(snapshot: StoryEditorSnapshot) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        // 编辑器处于禁用状态时不允许外部变更正文
        if (!uiState.contentState.editable && snapshot.content != mDraftContent) return
        val wasComposing = mIsComposing
        mIsComposing = snapshot.isComposing
        // 正文未变动（可能只是光标移动或组合态变化）
        if (snapshot.content == mDraftContent) {
            // 输入法输入完成后，若有之前延迟的草稿，立即安排自动保存
            if (
                shouldScheduleStoryAutoSaveAfterComposition(
                    wasComposing = wasComposing,
                    snapshot = snapshot,
                    draftContent = mDraftContent,
                    persistedContent = mPersistedContent
                )
            ) {
                scheduleAutoSave()
            }
            return
        }
        val story = mStory ?: return
        // 记录手动编辑历史（包含世界书时序快照）
        mEditHistory.recordManualEdit(
            previousContent = mDraftContent,
            currentContent = snapshot.content,
            worldInfoStateJson = story.worldInfoStateJson,
            worldInfoGenerationStep = story.worldInfoGenerationStep
        )
        mDraftContent = snapshot.content
        // 更新文档流内容与最新编辑高亮范围
        mDocumentFlow.value = mDocumentFlow.value?.copy(
            content = snapshot.content,
            latestEditedRange = mEditHistory.nextUndo()?.editedTextRange()
        )
        // 根据与已持久化内容的差异更新保存状态
        val saveState = if (snapshot.content == mPersistedContent) {
            mDebounceJob?.cancel()
            StorySaveState.Saved
        } else {
            StorySaveState.Dirty
        }
        // 更新 UI 状态
        uiState.copy(
            topBarState = uiState.topBarState.copy(saveState = saveState),
            contentState = uiState.contentState.copy(
                characterCount = snapshot.content.length
            ),
            canUndoEdit = mEditHistory.canUndo,
            canRedoEdit = mEditHistory.canRedo
        ).setup()
        // 非组合输入且正文有修改时调度防抖自动保存
        if (!snapshot.isComposing && saveState == StorySaveState.Dirty) scheduleAutoSave()
    }

    /**
     * 立即取消防抖等待，直接刷盘保存当前草稿。
     */
    @UiIntentObserver(StoryEditorUiIntent.FlushDraft::class)
    private suspend fun onFlushDraft() {
        if (!isStateOf<StoryEditorUiState.Normal>()) return
        mDebounceJob?.cancel()
        saveDraft()
    }

    /**
     * 重试保存正文草稿。
     */
    @UiIntentObserver(StoryEditorUiIntent.RetrySave::class)
    private fun onRetrySave() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.topBarState.saveState !in setOf(StorySaveState.Failed, StorySaveState.Dirty)) {
            return
        }
        scheduleAutoSave(delayMillis = 0L)
    }

    /**
     * 处理返回按键/退出事件。
     *
     * 分级拦截与保护逻辑：
     * - 若处于设置/加载子页面：取消摘要任务并切回正文编辑主页；
     * - 若展示对话框：关闭对话框；
     * - 若正在进行流式生成：中断生成并落库已接收到的局部文本；
     * - 若有未处理的可恢复生成失败内容：提示用户先处理局部生成；
     * - 退出前强制刷盘保存草稿，保存成功后结束页面。
     */
    @UiIntentObserver(StoryEditorUiIntent.Back::class)
    private suspend fun onBack() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        // 设置页返回拦截
        if (uiState.pageState != StoryEditorPageState.Editor) {
            cancelSummaryJob()
            val settings = uiState.pageState as? StoryEditorPageState.Settings
            if (settings?.isSaving == true) return
            getOrNull<StoryEditorUiState.Normal>()
                ?.copy(pageState = StoryEditorPageState.Editor)
                ?.setup()
            return
        }
        // 弹窗返回拦截
        if (uiState.dialogState != StoryEditorDialogState.None) {
            uiState.copy(dialogState = StoryEditorDialogState.None).setup()
            return
        }
        // 流式生成中返回：立即停止并保留生成内容
        if (uiState.generationState is StoryGenerationState.Streaming) {
            stopGeneration()
            return
        }
        // 有待挽救的生成失败文本时阻止意外退出
        val failedGeneration = uiState.generationState as? StoryGenerationState.Failed
        if (failedGeneration?.recoverablePartial?.isNotBlank() == true) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.story_resolve_partial_before_leaving
            ).tryEmit()
            return
        }
        // 退出前强制提交未保存草稿
        mDebounceJob?.cancel()
        if (!saveDraft()) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_leave_with_unsaved_draft).tryEmit()
            return
        }
        StoryEditorUiState.finished(uiStateFlow.value).setup()
    }

    /**
     * 发生保存冲突时，将当前内存中未落库的草稿复制到剪贴板。
     */
    @UiIntentObserver(StoryEditorUiIntent.CopyConflictDraft::class)
    private fun onCopyConflictDraft() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.topBarState.saveState != StorySaveState.Conflict) return
        StoryEditorViewEvent.CopyDraft(mDraftContent).tryEmit()
    }

    /**
     * 发生保存冲突后，丢弃本地未保存草稿，从数据库重新拉取最新版本。
     *
     * 处理步骤：
     * - 从数据库重读故事实体；
     * - 覆盖同步本地内存草稿、持久化镜像与版本号；
     * - 清空历史撤销栈并重新向文档流发布新正文；
     * - 恢复保存状态为 [StorySaveState.Saved]。
     */
    @UiIntentObserver(StoryEditorUiIntent.ReloadAfterConflict::class)
    private suspend fun onReloadAfterConflict() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.topBarState.saveState != StorySaveState.Conflict) return
        // 重新从数据库读取最新故事内容
        val story = withContext(Dispatchers.IO) {
            mStoryRepository.getStory(uiState.storyId)
        } ?: run {
            finishMissingStory()
            return
        }
        // 重置内存镜像与版本
        mStory = story
        mDraftContent = story.content
        mPersistedContent = story.content
        mRevision = story.contentRevision
        // 清理编辑撤销历史并发布到文档流
        clearEditHistory()
        publishDocument(story.content)
        // 恢复 UI 状态为已保存
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return
        current.copy(
            topBarState = current.topBarState.copy(saveState = StorySaveState.Saved),
            contentState = current.contentState.copy(characterCount = story.content.length),
            canUndoEdit = false,
            canRedoEdit = false
        ).setup()
    }

    /**
     * 打开故事设置页面。
     *
     * 校验前置状态（必须处于空闲且无弹窗状态），异步构建并加载设置页数据 [StoryEditorPageState.Settings]。
     */
    @UiIntentObserver(StoryEditorUiIntent.OpenStorySettings::class)
    private suspend fun onOpenStorySettings() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.generationState !is StoryGenerationState.Idle) return
        if (uiState.pageState != StoryEditorPageState.Editor) return
        if (uiState.dialogState != StoryEditorDialogState.None) return
        // 切换为加载中状态
        uiState.copy(pageState = StoryEditorPageState.LoadingSettings).setup()
        try {
            // 异步组装故事设置页面数据
            val settings = withContext(Dispatchers.IO) { buildSettingsState() }
            val current = getOrNull<StoryEditorUiState.Normal>() ?: return
            if (current.pageState != StoryEditorPageState.LoadingSettings) return
            current.copy(pageState = settings).setup()
        } catch (_: Exception) {
            val current = getOrNull<StoryEditorUiState.Normal>() ?: return
            current.copy(pageState = StoryEditorPageState.Editor).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.story_settings_load_failed).tryEmit()
        }
    }
    @UiIntentObserver(StoryEditorUiIntent.SelectSettingsSection::class)
    private fun onSelectSettingsSection(intent: StoryEditorUiIntent.SelectSettingsSection) {
        updateSettings { copy(selectedSection = intent.section) }
    }

    /**
     * 关闭故事设置页，切回正文编辑页。
     */
    @UiIntentObserver(StoryEditorUiIntent.CloseStorySettings::class)
    private suspend fun onCloseStorySettings() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val settings = uiState.pageState as? StoryEditorPageState.Settings
        if (settings?.isSaving == true) return
        cancelSummaryJob()
        if (uiState.pageState == StoryEditorPageState.Editor) return
        getOrNull<StoryEditorUiState.Normal>()
            ?.copy(pageState = StoryEditorPageState.Editor)
            ?.setup()
    }

    /**
     * 修改记忆设定（Memory）草稿。
     *
     * @param intent 包含设定文本的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ChangeMemory::class)
    private fun onChangeMemory(intent: StoryEditorUiIntent.ChangeMemory) {
        updateSettings { copy(memory = intent.value) }
    }

    /**
     * 修改摘要正文（Summary）草稿。
     *
     * @param intent 包含摘要文本的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ChangeSummary::class)
    private fun onChangeSummary(intent: StoryEditorUiIntent.ChangeSummary) {
        updateSettings { copy(summary = intent.value) }
    }

    /**
     * 修改作者注释（Author Note）草稿。
     *
     * @param intent 包含作者注释文本的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ChangeAuthorNote::class)
    private fun onChangeAuthorNote(intent: StoryEditorUiIntent.ChangeAuthorNote) {
        updateSettings { copy(authorNote = intent.value) }
    }

    /**
     * 触发故事正文自动总结。
     *
     * 处理步骤：
     * - 检查正文非空；
     * - 总结前先将当前正文草稿保存落库；
     * - 校验并获取当前可用的摘要服务 Provider；
     * - 弹出 [StoryEditorDialogState.SummarizingStory] 对话框并启动异步总结任务。
     */
    @UiIntentObserver(StoryEditorUiIntent.SummarizeStory::class)
    private suspend fun onSummarizeStory() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val settings = uiState.pageState as? StoryEditorPageState.Settings ?: return
        if (settings.isSaving || uiState.dialogState != StoryEditorDialogState.None) return
        // 校验正文是否为空
        if (mDraftContent.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_summary_empty_document).tryEmit()
            return
        }
        // 确保正文最新修改已保存
        if (!saveDraft()) return
        // 解析总结专用的 LLM Provider
        val provider = try {
            withContext(Dispatchers.IO) { mProviderSelectionResolver.requireSummaryProvider() }
        } catch (_: UnavailableLLMProviderSelectionException) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.generation_error_summary_provider_unavailable
            ).tryEmit()
            return
        } catch (_: NoEnabledLLMProviderException) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_error_no_provider).tryEmit()
            return
        }
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return
        val currentSettings = current.pageState as? StoryEditorPageState.Settings ?: return
        // 切换 UI 展示总结中弹窗
        current.copy(
            dialogState = StoryEditorDialogState.SummarizingStory
        ).setup()
        // 启动后台总结协程
        launchSummaryJob(
            storyId = current.storyId,
            memory = currentSettings.memory,
            currentSummary = currentSettings.summary,
            sourceContent = mDraftContent,
            sourceRevision = mRevision,
            provider = provider
        )
    }

    /**
     * 取消正在进行的故事总结任务。
     */
    @UiIntentObserver(StoryEditorUiIntent.CancelStorySummary::class)
    private suspend fun onCancelStorySummary() {
        cancelSummaryJob()
    }

    /**
     * 确认应用生成的总结文本。
     *
     * 处理步骤：
     * - 提取生成的总结预览内容与生成时的正文版本号；
     * - 调用数据库带乐观锁保存（`saveGeneratedSummary`），若版本不一致则提示冲突；
     * - 同步本地实体与设置页草稿状态，更新参考区红点标记。
     */
    @UiIntentObserver(StoryEditorUiIntent.ConfirmStorySummary::class)
    private suspend fun onConfirmStorySummary() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val preview = uiState.dialogState as? StoryEditorDialogState.StorySummaryPreview
            ?: return
        // 校验版本号并保存生成的摘要到数据库
        val saved = withContext(Dispatchers.IO) {
            mStoryRepository.saveGeneratedSummary(
                storyId = uiState.storyId,
                expectedContentRevision = preview.sourceContentRevision,
                content = preview.content
            )
        }
        if (!saved) {
            uiState.copy(dialogState = StoryEditorDialogState.None).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.story_summary_conflict).tryEmit()
            return
        }
        // 更新内存故事快照
        mStory = mStory?.copy(summary = preview.content)
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return
        val currentSettings = current.pageState as? StoryEditorPageState.Settings ?: return
        // 更新 UI 设置状态与参考栏红点
        current.copy(
            referenceState = current.referenceState.copy(
                hasMemory = currentSettings.memory.isNotBlank() || preview.content.isNotBlank()
            ),
            pageState = currentSettings.copy(summary = preview.content),
            dialogState = StoryEditorDialogState.None
        ).setup()
        AppViewEvent.PopupToastMessageByResId(R.string.story_summary_updated).tryEmit()
    }

    /**
     * 切换故事中某个角色的勾选参与状态。
     *
     * 自动联动逻辑：
     * - 新勾选角色时，将其排在已选角色末尾，并自动勾选其绑定的角色世界书；
     * - 取消勾选角色时，重置其排序序号并重新规范化剩余角色的排序。
     *
     * @param intent 包含角色 ID 的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ToggleStoryCharacter::class)
    private fun onToggleStoryCharacter(intent: StoryEditorUiIntent.ToggleStoryCharacter) {
        updateSettings {
            val target = characters.firstOrNull { it.id == intent.characterId } ?: return@updateSettings this
            val selecting = !target.selected
            // 计算新选中角色的下一个排序值
            val nextOrder = characters.filter { it.selected }.maxOfOrNull { it.sortOrder }
                ?.plus(1)
                ?: 0
            copy(
                // 重新规范化角色排序列表
                characters = normalizeCharacterOrder(
                    characters.map { item ->
                        if (item.id != target.id) item else item.copy(
                            selected = !item.selected,
                            sortOrder = if (item.selected) Int.MAX_VALUE else nextOrder
                        )
                    }
                ),
                // 若角色绑定了角色专属世界书，勾选角色时自动启用该世界书
                lorebookGroups = if (selecting && target.linkedLorebookId != null) {
                    lorebookGroups.enableLorebook(target.linkedLorebookId)
                } else {
                    lorebookGroups
                }
            )
        }
    }

    /**
     * 设置角色的世界书激活模式（始终激活 vs 关键字自动激活）。
     *
     * @param intent 包含角色 ID 与激活模式的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.SetCharacterActivationMode::class)
    private fun onSetCharacterActivationMode(
        intent: StoryEditorUiIntent.SetCharacterActivationMode
    ) {
        updateSettings {
            copy(
                characters = characters.map { item ->
                    if (item.id == intent.characterId && item.selected) {
                        item.copy(activationMode = intent.activationMode)
                    } else {
                        item
                    }
                }
            )
        }
    }

    /**
     * 修改角色的激活关键字草稿。
     *
     * @param intent 包含角色 ID 与关键字草稿的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ChangeCharacterActivationKeys::class)
    private fun onChangeCharacterActivationKeys(
        intent: StoryEditorUiIntent.ChangeCharacterActivationKeys
    ) {
        updateSettings {
            copy(
                characters = characters.map { item ->
                    if (item.id == intent.characterId && item.selected) {
                        item.copy(activationKeysDraft = intent.value)
                    } else {
                        item
                    }
                }
            )
        }
    }

    /**
     * 调整已选角色在故事上下文中的先后排序。
     *
     * 处理步骤：
     * - 提取所有已选角色按当前序号排列；
     * - 计算目标位移并完成数组元素移动；
     * - 重新建立排序映射并更新角色列表。
     *
     * @param intent 包含角色 ID 与位移偏移量 offset 的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.MoveStoryCharacter::class)
    private fun onMoveStoryCharacter(intent: StoryEditorUiIntent.MoveStoryCharacter) {
        updateSettings {
            // 提取已选角色列表
            val selected = characters.filter { it.selected }.sortedBy { it.sortOrder }.toMutableList()
            val from = selected.indexOfFirst { it.id == intent.characterId }
            if (from < 0) return@updateSettings this
            // 计算边界限制内的目标索引
            val to = (from + intent.offset).coerceIn(0, selected.lastIndex)
            if (from == to) return@updateSettings this
            // 移动元素
            val moved = selected.removeAt(from)
            selected.add(to, moved)
            // 重新映射各角色 ID 的新序号
            val selectedOrder = selected.mapIndexed { index, item -> item.id to index }.toMap()
            copy(
                characters = normalizeCharacterOrder(
                    characters.map { item ->
                        item.copy(sortOrder = selectedOrder[item.id] ?: Int.MAX_VALUE)
                    }
                )
            )
        }
    }

    /**
     * 批量切换某本世界书全部条目的启用/禁用状态。
     *
     * @param intent 包含世界书 ID 的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ToggleLorebook::class)
    private fun onToggleLorebook(intent: StoryEditorUiIntent.ToggleLorebook) {
        updateSettings {
            copy(lorebookGroups = lorebookGroups.toggleLorebook(intent.lorebookId))
        }
    }

    /**
     * 切换单个世界书条目的启用/禁用状态。
     *
     * @param intent 包含条目 ID 的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ToggleLorebookEntry::class)
    private fun onToggleLorebookEntry(intent: StoryEditorUiIntent.ToggleLorebookEntry) {
        updateSettings {
            copy(
                lorebookGroups = lorebookGroups.map { group ->
                    group.copy(
                        entries = group.entries.map { entry ->
                            if (entry.id == intent.entryId) {
                                entry.copy(selected = !entry.selected)
                            } else {
                                entry
                            }
                        }
                    )
                }
            )
        }
    }

    /**
     * 保存故事设置页面的全部设定。
     *
     * 持久化内容：
     * - 设定、摘要、作者注释；
     * - 启用的世界书条目 ID 列表；
     * - 关联角色列表（包含排序顺序、激活模式与解析后的激活关键字）；
     * - 重新拉取故事实体并更新参考区状态栏。
     */
    @UiIntentObserver(StoryEditorUiIntent.SaveStorySettings::class)
    private suspend fun onSaveStorySettings() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val settings = uiState.pageState as? StoryEditorPageState.Settings ?: return
        if (settings.isSaving || uiState.dialogState != StoryEditorDialogState.None) return
        // 标记为保存中
        uiState.copy(pageState = settings.copy(isSaving = true)).setup()
        try {
            // 提交持久化到数据库
            val refreshedStory = withContext(Dispatchers.IO) {
                mStoryRepository.updateStoryConfiguration(
                    storyId = uiState.storyId,
                    memory = settings.memory,
                    summary = settings.summary,
                    authorNote = settings.authorNote,
                    lorebookEntryIds = settings.lorebookGroups
                        .flatMap { it.entries }
                        .filter { it.selected }
                        .map { it.id },
                    characterSelections = settings.characters
                        .filter { it.selected }
                        .sortedBy { it.sortOrder }
                        .map { item ->
                            StoryCharacterSelection(
                                characterId = item.id,
                                activationMode = item.activationMode.toStorageValue(),
                                activationKeys = parseActivationKeys(item.activationKeysDraft)
                            )
                        }
                )
                mStoryRepository.getStory(uiState.storyId)
                    ?: error("Story does not exist")
            }
            mStory = refreshedStory
            val current = getOrNull<StoryEditorUiState.Normal>() ?: return
            // 切回编辑器页面并更新参考区统计
            current.copy(
                referenceState = StoryEditorReferenceState(
                    hasMemory = settings.memory.isNotBlank() || settings.summary.isNotBlank(),
                    hasAuthorNote = settings.authorNote.isNotBlank(),
                    characterCount = settings.characters.count { it.selected },
                    lorebookEntryCount = settings.lorebookGroups
                        .sumOf { group -> group.entries.count { it.selected } }
                ),
                pageState = StoryEditorPageState.Editor
            ).setup()
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_settings_save_failed).tryEmit()
            val current = getOrNull<StoryEditorUiState.Normal>() ?: return
            val currentSettings = current.pageState
                as? StoryEditorPageState.Settings
                ?: return
            current.copy(pageState = currentSettings.copy(isSaving = false)).setup()
        }
    }

    /**
     * 触发 AI 续写故事。
     *
     * @param intent 包含当前编辑器快照的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ContinueStory::class)
    private suspend fun onContinueStory(intent: StoryEditorUiIntent.ContinueStory) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.pageState != StoryEditorPageState.Editor) return
        if (uiState.generationState !is StoryGenerationState.Idle) return
        if (!uiState.contentState.editable) return
        acceptEditorSnapshot(intent.snapshot)
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return
        startGeneration(
            continuationGuidance = current.continuationInputState.guidanceDraft.trim()
        )
    }

    /**
     * 修改续写引导词（Continuation Guidance）草稿。
     *
     * @param intent 包含引导词文本的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ChangeContinuationGuidance::class)
    private fun onChangeContinuationGuidance(
        intent: StoryEditorUiIntent.ChangeContinuationGuidance
    ) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.pageState != StoryEditorPageState.Editor) return
        if (uiState.generationState !is StoryGenerationState.Idle) return
        uiState.copy(
            continuationInputState = uiState.continuationInputState.copy(
                guidanceDraft = intent.value
            )
        ).setup()
    }

    /**
     * 停止当前的 AI 续写生成。
     */
    @UiIntentObserver(StoryEditorUiIntent.StopGeneration::class)
    private suspend fun onStopGeneration() {
        stopGeneration()
    }

    /**
     * 将生成失败或中断时保存的局部文本（Recoverable Partial）插入正文末尾。
     */
    @UiIntentObserver(StoryEditorUiIntent.InsertRecoverablePartial::class)
    private suspend fun onInsertRecoverablePartial() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val failure = uiState.generationState as? StoryGenerationState.Failed ?: return
        val active = mRecoverableGeneration ?: return
        if (failure.recoverablePartial.isBlank()) return
        applyGeneratedResult(active, failure.recoverablePartial)
    }

    /**
     * 复制可挽救的局部生成文本到剪贴板。
     */
    @UiIntentObserver(StoryEditorUiIntent.CopyRecoverablePartial::class)
    private fun onCopyRecoverablePartial() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val failure = uiState.generationState as? StoryGenerationState.Failed ?: return
        if (failure.recoverablePartial.isNotBlank()) {
            StoryEditorViewEvent.CopyGeneratedText(failure.recoverablePartial).tryEmit()
        }
    }

    /**
     * 丢弃可挽救的局部生成文本，重置为空闲状态。
     */
    @UiIntentObserver(StoryEditorUiIntent.DiscardRecoverablePartial::class)
    private fun onDiscardRecoverablePartial() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.generationState !is StoryGenerationState.Failed) return
        mRecoverableGeneration = null
        uiState.copy(generationState = StoryGenerationState.Idle).setup()
    }

    /**
     * 撤销上一次正文编辑操作。
     */
    @UiIntentObserver(StoryEditorUiIntent.UndoLastEdit::class)
    private suspend fun onUndoLastEdit() {
        mDebounceJob?.cancel()
        if (!saveDraft()) return
        val entry = mEditHistory.nextUndo() ?: return
        undoEdit(entry)
    }

    /**
     * 重做上一次被撤销的正文编辑操作。
     */
    @UiIntentObserver(StoryEditorUiIntent.RedoLastEdit::class)
    private suspend fun onRedoLastEdit() {
        val entry = mEditHistory.nextRedo() ?: return
        redoEdit(entry)
    }

    /**
     * 打开 Prompt 检查器弹窗。
     */
    @UiIntentObserver(StoryEditorUiIntent.OpenPromptInspector::class)
    private fun onOpenPromptInspector() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val inspection = mLastPromptInspection ?: return
        uiState.copy(dialogState = StoryEditorDialogState.PromptInspector(inspection)).setup()
    }

    /**
     * 复制 Prompt 检查器中的文本项。
     *
     * @param intent 包含待复制文本的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.CopyPromptItem::class)
    private fun onCopyPromptItem(intent: StoryEditorUiIntent.CopyPromptItem) {
        if (!isStateOf<StoryEditorUiState.Normal>()) return
        StoryEditorViewEvent.CopyPromptText(intent.text).tryEmit()
    }

    /**
     * 打开文件操作对话框（导入/导出菜单）。
     */
    @UiIntentObserver(StoryEditorUiIntent.OpenFileActions::class)
    private fun onOpenFileActions() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.generationState !is StoryGenerationState.Idle) return
        uiState.copy(dialogState = StoryEditorDialogState.FileActions).setup()
    }

    /**
     * 点击导入纯文本，先保存草稿并触发系统文本文件选择器。
     */
    @UiIntentObserver(StoryEditorUiIntent.ImportTextClick::class)
    private suspend fun onImportTextClick() {
        if (!saveDraft()) return
        closeDialog()
        StoryEditorViewEvent.OpenTextImporter.tryEmit()
    }

    /**
     * 接收文本文件导入 URI 结果。
     *
     * @param intent 包含文件 URI 的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ImportTextResult::class)
    private suspend fun onImportTextResult(intent: StoryEditorUiIntent.ImportTextResult) {
        readImport { mStoryArchiveRepository.readTextImportFromUri(intent.uri) }
    }

    /**
     * 点击导入 RPStory 归档文件，先保存草稿并触发系统文件选择器。
     */
    @UiIntentObserver(StoryEditorUiIntent.ImportStoryClick::class)
    private suspend fun onImportStoryClick() {
        if (!saveDraft()) return
        closeDialog()
        StoryEditorViewEvent.OpenStoryImporter.tryEmit()
    }

    /**
     * 接收故事归档文件导入 URI 结果。
     *
     * @param intent 包含文件 URI 的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ImportStoryResult::class)
    private suspend fun onImportStoryResult(intent: StoryEditorUiIntent.ImportStoryResult) {
        readImport { mStoryArchiveRepository.readArchiveImportFromUri(intent.uri) }
    }

    /**
     * 修改导入预览弹窗中的故事标题草稿。
     *
     * @param intent 包含新标题的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ChangeImportTitle::class)
    private fun onChangeImportTitle(intent: StoryEditorUiIntent.ChangeImportTitle) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? StoryEditorDialogState.ImportPreview ?: return
        if (dialog.preview.isSaving) return
        uiState.copy(
            dialogState = dialog.copy(preview = dialog.preview.copy(title = intent.value))
        ).setup()
    }

    /**
     * 确认导入故事。
     *
     * 处理流程：
     * - 校验标题非空；
     * - 将导入草稿持久化为新故事实体；
     * - 发送事件导航跳转到新导入的故事页面。
     */
    @UiIntentObserver(StoryEditorUiIntent.ConfirmImport::class)
    private suspend fun onConfirmImport() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? StoryEditorDialogState.ImportPreview ?: return
        // 校验状态与标题
        if (dialog.preview.isSaving || dialog.preview.title.isBlank()) return
        uiState.copy(
            dialogState = dialog.copy(preview = dialog.preview.copy(isSaving = true))
        ).setup()
        try {
            // 异步保存导入草稿到数据库并获取新故事 ID
            val storyId = withContext(Dispatchers.IO) {
                mStoryArchiveRepository.saveImport(
                    draft = dialog.preview.draft,
                    title = dialog.preview.title
                )
            }
            // 跳转到新故事
            StoryEditorViewEvent.OpenStory(storyId).tryEmit()
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_import_failed).tryEmit()
            val current = getOrNull<StoryEditorUiState.Normal>() ?: return
            val currentDialog = current.dialogState as? StoryEditorDialogState.ImportPreview ?: return
            current.copy(
                dialogState = currentDialog.copy(
                    preview = currentDialog.preview.copy(isSaving = false)
                )
            ).setup()
        }
    }

    /**
     * 点击导出纯文本（TXT/Markdown），先保存草稿并触发系统保存文件选择器。
     *
     * @param intent 包含导出格式（TXT 或 Markdown）的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ExportTextClick::class)
    private suspend fun onExportTextClick(intent: StoryEditorUiIntent.ExportTextClick) {
        if (!saveDraft()) return
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        closeDialog()
        val extension = if (intent.format == StoryTextExportFormat.Markdown) ".md" else ".txt"
        StoryEditorViewEvent.OpenTextExporter(
            fileName = safeFileName(uiState.topBarState.title) + extension,
            markdown = intent.format == StoryTextExportFormat.Markdown
        ).tryEmit()
    }

    /**
     * 接收文本导出目标 URI 并执行文本文件写入。
     *
     * @param intent 包含导出目标 URI 的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ExportTextResult::class)
    private suspend fun onExportTextResult(intent: StoryEditorUiIntent.ExportTextResult) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        export { mStoryArchiveRepository.exportTextToUri(uiState.storyId, intent.uri) }
    }

    /**
     * 点击导出完整故事归档（RPStory），先保存草稿并触发文件保存选择器。
     */
    @UiIntentObserver(StoryEditorUiIntent.ExportStoryClick::class)
    private suspend fun onExportStoryClick() {
        if (!saveDraft()) return
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        closeDialog()
        StoryEditorViewEvent.OpenStoryExporter(
            safeFileName(uiState.topBarState.title) + ".rpstory.json"
        ).tryEmit()
    }

    /**
     * 接收归档导出目标 URI 并执行 RPStory JSON 压缩/打包写入。
     *
     * @param intent 包含导出目标 URI 的意图
     */
    @UiIntentObserver(StoryEditorUiIntent.ExportStoryResult::class)
    private suspend fun onExportStoryResult(intent: StoryEditorUiIntent.ExportStoryResult) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        export { mStoryArchiveRepository.exportArchiveToUri(uiState.storyId, intent.uri) }
    }

    /**
     * 关闭或丢弃当前展示的弹窗对话框。
     */
    @UiIntentObserver(StoryEditorUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val import = uiState.dialogState as? StoryEditorDialogState.ImportPreview
        if (import?.preview?.isSaving == true) return
        if (uiState.dialogState == StoryEditorDialogState.SummarizingStory) return
        uiState.copy(dialogState = StoryEditorDialogState.None).setup()
    }

    /**
     * 启动异步故事摘要生成任务。
     *
     * 取消上一个运行中的摘要任务，并在 viewModelScope 中启动新协程执行。
     *
     * @param storyId 故事 ID
     * @param memory 当前记忆设定
     * @param currentSummary 当前已有摘要
     * @param sourceContent 待总结正文源文本
     * @param sourceRevision 当前正文版本号
     * @param provider LLM 服务提供商配置
     */
    private fun launchSummaryJob(
        storyId: Long,
        memory: String,
        currentSummary: String,
        sourceContent: String,
        sourceRevision: Long,
        provider: LLMProvider
    ) {
        // 取消前序未完成任务
        mSummaryJob?.cancel()
        // 启动异步总结生成协程
        mSummaryJob = viewModelScope.launch {
            runStorySummary(
                storyId = storyId,
                memory = memory,
                currentSummary = currentSummary,
                sourceContent = sourceContent,
                sourceRevision = sourceRevision,
                provider = provider
            )
        }
    }

    /**
     * 异步构建并请求大模型生成故事摘要核心执行逻辑。
     *
     * 执行流程：
     * - 调用 [StorySummaryPromptBuilder.build] 构建结构化 Prompt；
     * - 调用 LLM 服务生成摘要正文，并做安全字符清洗（`summarySafeContent`）；
     * - 生成完成后将结果包装为预览状态 [StoryEditorDialogState.StorySummaryPreview] 供用户确认；
     * - 异常处理与状态重置。
     */
    private suspend fun runStorySummary(
        storyId: Long,
        memory: String,
        currentSummary: String,
        sourceContent: String,
        sourceRevision: Long,
        provider: LLMProvider
    ) {
        try {
            // 在后台线程构建摘要 Prompt 请求体
            val request = withContext(Dispatchers.Default) {
                mStorySummaryPromptBuilder.build(
                    memory = memory,
                    currentSummary = currentSummary,
                    content = sourceContent,
                    provider = provider
                )
            }
            // 调用模型生成摘要文本并清洗
            val summary = withContext(Dispatchers.IO) {
                mLLMRepository.generateWithProvider(
                    provider = provider,
                    request = request,
                    routingSessionKey = "story:$storyId"
                )
                    .content
                    .summarySafeContent()
                    .trim()
            }
            if (summary.isBlank()) error("Story summary response is empty")
            val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
            if (uiState.dialogState != StoryEditorDialogState.SummarizingStory) return
            // 弹窗展示生成的摘要预览，并记录关联的正文版本号
            uiState.copy(
                dialogState = StoryEditorDialogState.StorySummaryPreview(
                    content = summary,
                    sourceContentRevision = sourceRevision
                )
            ).setup()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 发生异常时关闭总结弹窗并提示错误
            val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
            if (uiState.dialogState != StoryEditorDialogState.SummarizingStory) return
            uiState.copy(dialogState = StoryEditorDialogState.None).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.story_summary_failed).tryEmit()
        } finally {
            mSummaryJob = null
        }
    }

    /**
     * 取消运行中的故事摘要任务并关闭弹窗。
     */
    private suspend fun cancelSummaryJob() {
        mSummaryJob?.cancelAndJoin()
        mSummaryJob = null
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (
            uiState.dialogState == StoryEditorDialogState.SummarizingStory ||
            uiState.dialogState is StoryEditorDialogState.StorySummaryPreview
        ) {
            uiState.copy(dialogState = StoryEditorDialogState.None).setup()
        }
    }

    /**
     * 启动 AI 故事续写生成的完整准备与调度流程。
     *
     * 关键步骤：
     * - 状态与前置检查：确保处于编辑器主页、空闲状态且可编辑；
     * - 冻结编辑态：将 UI 设为不可编辑（editable = false），标记为准备中（Preparing）；
     * - 强制刷盘草稿：确保生成前内存草稿与数据库版本完全同步；
     * - 上下文与 Prompt 构建：聚合故事设定、角色卡、世界书递归扫描，校验 Token 预算；
     * - 状态快照冻结：创建 [ActiveStoryGeneration] 并启动生成协程。
     *
     * @param continuationGuidance 用户输入的续写引导指示
     */
    private suspend fun startGeneration(
        continuationGuidance: String = ""
    ) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        // 校验前置状态
        if (uiState.generationState !is StoryGenerationState.Idle) return
        if (uiState.pageState != StoryEditorPageState.Editor) return
        if (!uiState.contentState.editable) return
        // 冻结编辑器并展示准备中状态
        uiState.copy(
            contentState = uiState.contentState.copy(editable = false),
            generationState = StoryGenerationState.Preparing,
            dialogState = StoryEditorDialogState.None
        ).setup()
        mDebounceJob?.cancel()
        // 续写插入目标位置（正文末尾）
        val target = StoryEditTarget(mDraftContent.length, mDraftContent.length)
        // 生成前强制持久化草稿
        if (!saveDraft()) {
            restoreEditorAfterPreparation()
            return
        }
        val story = mStory?.copy(
            content = mDraftContent,
            contentRevision = mRevision
        ) ?: run {
            restoreEditorAfterPreparation()
            return
        }
        // 构建 Prompt 上下文并组装请求体
        val buildResult = try {
            val provider = withContext(Dispatchers.IO) {
                mProviderSelectionResolver.requireDefaultProvider()
            }
            val promptContext = withContext(Dispatchers.IO) {
                buildPromptContext(
                    story = story,
                    target = target,
                    continuationGuidance = continuationGuidance,
                    provider = provider
                )
            }
            provider to withContext(Dispatchers.Default) {
                mStoryPromptBuilder.build(promptContext)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: StoryPromptBudgetException) {
            // Token 预算超限特殊处理（标明超限最大的角色）
            val current = getOrNull<StoryEditorUiState.Normal>() ?: return
            current.copy(
                contentState = current.contentState.copy(editable = true),
                generationState = StoryGenerationState.Failed(
                    reason = StoryGenerationFailure.ContextBudget,
                    detail = error.largestCharacterNames.joinToString(", ")
                ),
                dialogState = StoryEditorDialogState.None
            ).setup()
            return
        } catch (_: Exception) {
            // 常规构建异常处理
            AppViewEvent.PopupToastMessageByResId(R.string.story_generation_failed).tryEmit()
            val current = getOrNull<StoryEditorUiState.Normal>() ?: return
            current.copy(
                contentState = current.contentState.copy(editable = true),
                generationState = StoryGenerationState.Failed(StoryGenerationFailure.Setup),
                dialogState = StoryEditorDialogState.None
            ).setup()
            return
        }
        val (provider, promptBuildResult) = buildResult
        recordPromptInspection(promptBuildResult.inspection)
        // 4. 冻结本次生成参数快照
        val active = ActiveStoryGeneration(
            token = Any(),
            provider = provider,
            target = target,
            baseRevision = mRevision,
            sourceContent = mDraftContent,
            previousEditedRange = mDocumentFlow.value?.latestEditedRange,
            previousWorldInfoStateJson = story.worldInfoStateJson,
            previousWorldInfoGenerationStep = story.worldInfoGenerationStep,
            nextWorldInfoStateJson = promptBuildResult.nextWorldInfoStateJson
        )
        mRecoverableGeneration = null
        mActiveGeneration = active
        // 5. 切换 UI 为生成中流式状态
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return
        current.copy(
            generationState = StoryGenerationState.Streaming(""),
            dialogState = StoryEditorDialogState.None
        ).setup()
        // 6. 启动生成协程
        mGenerationJob = viewModelScope.launch {
            runGeneration(active, promptBuildResult.request)
        }
    }

    /**
     * 准备阶段若失败，恢复编辑器的可编辑状态。
     */
    private fun restoreEditorAfterPreparation() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.generationState !is StoryGenerationState.Preparing) return
        uiState.copy(
            contentState = uiState.contentState.copy(editable = true),
            generationState = StoryGenerationState.Idle
        ).setup()
    }

    /**
     * 执行大模型生成（流式或单次非流式）并实时推送增量或最终应用结果。
     *
     * @param initial 初始活跃生成快照
     * @param request 构造完毕的请求体
     */
    private suspend fun runGeneration(
        initial: ActiveStoryGeneration,
        request: LLMGenerationRequest
    ) {
        var active = initial
        var applyingResult = false
        try {
            // 分发流式与非流式调用
            if (AppModel.streamEnabled) {
                mLLMRepository.streamGenerateWithProvider(
                    provider = active.provider,
                    request = request,
                    routingSessionKey = mStory?.id?.let { "story:$it" }
                ).collect { event ->
                    if (event is LLMStreamEvent.Delta) {
                        // 累积局部文本并刷新编辑器预览
                        active = active.copy(partialText = active.partialText + event.content)
                        mActiveGeneration = active
                        updateStreamingState(active)
                    }
                }
            } else {
                val response = mLLMRepository.generateWithProvider(
                    provider = active.provider,
                    request = request,
                    routingSessionKey = mStory?.id?.let { "story:$it" }
                )
                active = active.copy(partialText = response.content)
                mActiveGeneration = active
                updateStreamingState(active)
            }
            // 标记进入结果落库应用阶段
            applyingResult = true
            applyGeneratedResult(active, active.partialText)
        } catch (error: CancellationException) {
            // 捕获取消信号，缓存被中断的快照以供收尾挽救
            mCancelledGeneration = active
            throw error
        } catch (error: Exception) {
            showGenerationFailure(
                active = active,
                reason = if (applyingResult) {
                    StoryGenerationFailure.ApplyResult
                } else {
                    error.toStoryGenerationFailure()
                }
            )
        } finally {
            if (mActiveGeneration?.token === initial.token) mActiveGeneration = null
        }
    }

    /**
     * 流式增量到达时，向文档流发布拼接了生成预览的完整正文，供 UI 实时展示。
     *
     * @param active 当前活跃生成快照
     */
    private fun updateStreamingState(active: ActiveStoryGeneration) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val previewContent = active.previewContent()
        publishDocument(previewContent, active.previousEditedRange)
        uiState.copy(
            contentState = uiState.contentState.copy(characterCount = previewContent.length),
            generationState = StoryGenerationState.Streaming(
                partialText = active.partialText
            )
        ).setup()
    }

    /**
     * 停止 AI 生成。
     *
     * 容错与优雅收尾逻辑：
     * - 取消生成协程并等待其 join 完毕；
     * - 若尚未生成任何文字，还原正文并恢复编辑器可编辑状态；
     * - 若已生成部分文字（partialText 非空），在 [NonCancellable] 上下文中调用 [applyGeneratedResult] 将已生成片段写入正文。
     */
    private suspend fun stopGeneration() {
        val active = mActiveGeneration ?: return
        mCancelledGeneration = null
        // 取消并等待生成任务终止
        mGenerationJob?.cancel()
        mGenerationJob?.join()
        mGenerationJob = null
        val stopped = mCancelledGeneration ?: active
        mCancelledGeneration = null
        // 尚未生成内容直接还原
        if (stopped.partialText.isBlank()) {
            publishDocument(stopped.sourceContent, stopped.previousEditedRange)
            val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
            uiState.copy(
                contentState = uiState.contentState.copy(
                    characterCount = stopped.sourceContent.length,
                    editable = true
                ),
                generationState = StoryGenerationState.Idle
            ).setup()
            return
        }
        // 在 NonCancellable 下安全提交已接收到的局部生成结果
        withContext(NonCancellable) {
            try {
                applyGeneratedResult(stopped, stopped.partialText)
            } catch (_: Exception) {
                showGenerationFailure(stopped, StoryGenerationFailure.ApplyResult)
            }
        }
    }

    /**
     * 展示生成失败状态，并保留已生成的局部文本供用户挽救。
     *
     * @param active 失败时的生成快照
     * @param reason 失败原因
     */
    private fun showGenerationFailure(
        active: ActiveStoryGeneration,
        reason: StoryGenerationFailure
    ) {
        publishDocument(active.sourceContent, active.previousEditedRange)
        // 缓存可挽救的部分
        mRecoverableGeneration = active.takeIf { it.partialText.isNotBlank() }
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        uiState.copy(
            contentState = uiState.contentState.copy(
                characterCount = active.sourceContent.length,
                editable = true
            ),
            generationState = StoryGenerationState.Failed(
                reason = reason,
                recoverablePartial = active.partialText
            )
        ).setup()
    }

    /**
     * 将大模型生成的原始文本清洗后应用并持久化写库。
     *
     * 核心步骤：
     * - 文本清洗与格式预处理（[StoryOutputSanitizer.sanitize] 及空白/续写衔接整理）；
     * - 在 [NonCancellable] 下调用数据库 [StoryRepository.applyGeneratedEdit]，带乐观锁校验 baseRevision 与原文本 hash；
     * - 若发生冲突，缓存为可挽救生成并在 UI 提示冲突；
     * - 若成功，构造 [StoryUndoEntry] 并记录入撤销历史 [StoryEditHistory]；
     * - 更新内存版本号、草稿镜像、实体字段及文档流。
     *
     * @param active 生成快照
     * @param rawResult 原始生成结果字符串
     */
    private suspend fun applyGeneratedResult(
        active: ActiveStoryGeneration,
        rawResult: String
    ) {
        // 清洗并格式化续写文本
        val sanitizedResult = mStoryOutputSanitizer.sanitize(rawResult)
        val result = prepareStoryContinuationText(active.sourceContent, sanitizedResult)
        // 空结果按失败处理
        if (result.isBlank()) {
            publishDocument(active.sourceContent, active.previousEditedRange)
            val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
            uiState.copy(
                contentState = uiState.contentState.copy(
                    characterCount = active.sourceContent.length,
                    editable = true
                ),
                generationState = StoryGenerationState.Failed(StoryGenerationFailure.EmptyResult)
            ).setup()
            return
        }
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        uiState.copy(generationState = StoryGenerationState.Applying).setup()
        // 在 NonCancellable 下向数据库提交编辑
        val applied = withContext(NonCancellable + Dispatchers.IO) {
            mStoryRepository.applyGeneratedEdit(
                StoryGeneratedEdit(
                    storyId = uiState.storyId,
                    baseRevision = active.baseRevision,
                    start = active.target.start,
                    end = active.target.end,
                    originalTextHash = storyTextHash(
                        active.target.originalText(active.sourceContent)
                    ),
                    result = result,
                    nextWorldInfoStateJson = active.nextWorldInfoStateJson
                )
            )
        }
        // 提交冲突处理
        if (applied == null) {
            publishDocument(active.sourceContent, active.previousEditedRange)
            mRecoverableGeneration = active.copy(partialText = result)
            val current = getOrNull<StoryEditorUiState.Normal>() ?: return
            current.copy(
                contentState = current.contentState.copy(
                    characterCount = active.sourceContent.length,
                    editable = true
                ),
                generationState = StoryGenerationState.Failed(
                    StoryGenerationFailure.Conflict,
                    result
                )
            ).setup()
            return
        }
        // 记录撤销条目（包含世界书时序步骤快照）
        val undoEntry = StoryUndoEntry(
            start = active.target.start,
            insertedText = result,
            replacedText = active.target.originalText(active.sourceContent),
            previousWorldInfoStateJson = active.previousWorldInfoStateJson,
            previousWorldInfoGenerationStep = active.previousWorldInfoGenerationStep,
            nextWorldInfoStateJson = active.nextWorldInfoStateJson,
            nextWorldInfoGenerationStep = applied.worldInfoGenerationStep
        )
        mEditHistory.record(undoEntry)
        // 更新本地镜像与版本
        mRecoverableGeneration = null
        mRevision = applied.revision
        mDraftContent = applied.content
        mPersistedContent = applied.content
        mStory = mStory?.copy(
            content = applied.content,
            contentRevision = applied.revision,
            worldInfoStateJson = active.nextWorldInfoStateJson,
            worldInfoGenerationStep = applied.worldInfoGenerationStep
        )
        // 发布新正文与高亮范围到文档流
        publishDocument(applied.content, active.resultTextRange(result))
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return
        // 恢复 UI 状态为已保存且可编辑
        current.copy(
            topBarState = current.topBarState.copy(saveState = StorySaveState.Saved),
            contentState = current.contentState.copy(
                characterCount = applied.content.length,
                editable = true
            ),
            continuationInputState = current.continuationInputState.copy(guidanceDraft = ""),
            generationState = StoryGenerationState.Idle,
            canUndoEdit = mEditHistory.canUndo,
            canRedoEdit = mEditHistory.canRedo
        ).setup()
    }

    /**
     * 撤销上一次正文编辑操作（AI 生成或手动批量编辑）。
     *
     * 核心步骤：
     * - 校验当前处于空闲状态；
     * - 调用数据库 [StoryRepository.revertGeneratedEdit] 带期望版本号执行逆向替换；
     * - 冲突处理：若数据库版本不一致则清空撤销栈并提示冲突；
     * - 成功后确认撤销（`mEditHistory.confirmUndo`），同步回滚世界书时序步骤与实体字段；
     * - 刷新文档流与 UI 撤销/重做可用状态。
     *
     * @param entry 待撤销的记录条目
     * @return 撤销是否成功执行
     */
    private suspend fun undoEdit(entry: StoryUndoEntry): Boolean {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return false
        if (uiState.generationState !is StoryGenerationState.Idle) return false
        // 调用数据库反向替换文本与世界书状态快照
        val reverted = withContext(Dispatchers.IO) {
            mStoryRepository.revertGeneratedEdit(
                storyId = uiState.storyId,
                expectedRevision = mRevision,
                start = entry.start,
                insertedText = entry.insertedText,
                replacedText = entry.replacedText,
                previousWorldInfoStateJson = entry.previousWorldInfoStateJson,
                previousWorldInfoGenerationStep = entry.previousWorldInfoGenerationStep
            )
        }
        // 撤销版本冲突处理
        if (reverted == null) {
            clearEditHistory()
            uiState.copy(
                generationState = StoryGenerationState.Failed(StoryGenerationFailure.Conflict),
                canUndoEdit = false,
                canRedoEdit = false
            ).setup()
            return false
        }
        // 更新版本号与草稿镜像
        mRevision = reverted.revision
        mDraftContent = reverted.content
        mPersistedContent = reverted.content
        mStory = mStory?.copy(
            content = reverted.content,
            contentRevision = reverted.revision,
            worldInfoStateJson = entry.previousWorldInfoStateJson,
            worldInfoGenerationStep = entry.previousWorldInfoGenerationStep
        )
        // 确认撤销并移动历史指针
        mEditHistory.confirmUndo(entry)
        // 向文档流发布回退后的正文并高亮范围
        publishDocument(
            reverted.content,
            mEditHistory.nextUndo()?.editedTextRange()
        )
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return true
        // 刷新 UI 状态
        current.copy(
            topBarState = current.topBarState.copy(saveState = StorySaveState.Saved),
            contentState = current.contentState.copy(
                characterCount = reverted.content.length,
                editable = true
            ),
            canUndoEdit = mEditHistory.canUndo,
            canRedoEdit = mEditHistory.canRedo
        ).setup()
        return true
    }

    /**
     * 重做上一次被撤销的编辑操作。
     *
     * 核心步骤：
     * - 校验处于空闲状态；
     * - 调用数据库 [StoryRepository.applyGeneratedEdit] 重新应用已记录的文本与世界书快照；
     * - 冲突校验：版本号不匹配时清空历史栈并报错；
     * - 成功后确认重做（`mEditHistory.confirmRedo`），更新版本号与本地缓存；
     * - 发布文档流并刷新 UI。
     *
     * @param entry 待重做的记录条目
     * @return 重做是否成功执行
     */
    private suspend fun redoEdit(entry: StoryUndoEntry): Boolean {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return false
        if (uiState.generationState !is StoryGenerationState.Idle) return false
        // 调用数据库重新应用编辑
        val applied = withContext(Dispatchers.IO) {
            mStoryRepository.applyGeneratedEdit(
                StoryGeneratedEdit(
                    storyId = uiState.storyId,
                    baseRevision = mRevision,
                    start = entry.start,
                    end = entry.start + entry.replacedText.length,
                    originalTextHash = storyTextHash(entry.replacedText),
                    result = entry.insertedText,
                    nextWorldInfoStateJson = entry.nextWorldInfoStateJson,
                    nextWorldInfoGenerationStep = entry.nextWorldInfoGenerationStep
                )
            )
        }
        // 冲突校验处理
        if (applied == null) {
            clearEditHistory()
            uiState.copy(
                generationState = StoryGenerationState.Failed(StoryGenerationFailure.Conflict),
                canUndoEdit = false,
                canRedoEdit = false
            ).setup()
            return false
        }
        // 同步本地版本与内容镜像
        mRevision = applied.revision
        mDraftContent = applied.content
        mPersistedContent = applied.content
        mStory = mStory?.copy(
            content = applied.content,
            contentRevision = applied.revision,
            worldInfoStateJson = entry.nextWorldInfoStateJson,
            worldInfoGenerationStep = applied.worldInfoGenerationStep
        )
        // 确认重做并移动历史指针
        mEditHistory.confirmRedo(entry)
        // 发布重做后的文档流与编辑高亮
        publishDocument(
            applied.content,
            entry.editedTextRange()
        )
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return true
        // 刷新 UI 状态
        current.copy(
            topBarState = current.topBarState.copy(saveState = StorySaveState.Saved),
            contentState = current.contentState.copy(
                characterCount = applied.content.length,
                editable = true
            ),
            canUndoEdit = mEditHistory.canUndo,
            canRedoEdit = mEditHistory.canRedo
        ).setup()
        return true
    }

    /**
     * 组装用于构建故事续写 Prompt 的上下文对象 [StoryPromptContext]。
     *
     * 包含内容：
     * - 故事基础实体（设定、正文、记忆、摘要、作者注释等）；
     * - 故事候选角色列表（按排序与激活条件准备）；
     * - 显式关联的世界书条目与全量世界书字典；
     * - 开启递归扫描的世界书 ID 集合；
     * - 用户输入的续写引导词。
     */
    private suspend fun buildPromptContext(
        story: Story,
        target: StoryEditTarget,
        continuationGuidance: String,
        provider: LLMProvider
    ): StoryPromptContext {
        // 加载故事候选角色
        val characterCandidates = mStoryRepository.getStoryCharacterCandidates(story.id)
        // 加载故事关联的世界书条目与世界书映射字典
        val explicitEntryIds = mStoryRepository.getLorebookEntryIds(story).toSet()
        val lorebooks = mLorebookRepository.getAllLorebooks()
        val entries = explicitEntryIds.mapNotNull { mLorebookRepository.getEntryById(it) }
        val candidateLorebookIds = entries.mapTo(mutableSetOf()) { it.lorebookId }
        val candidateLorebooks = lorebooks
            .filter { it.id in candidateLorebookIds }
            .associateBy { it.id }
        // 构造并返回结构化 Prompt 上下文
        return StoryPromptContext(
            story = story,
            characterCandidates = characterCandidates,
            target = target,
            sourceContent = story.content,
            provider = provider,
            candidateLorebookEntries = entries,
            candidateLorebooks = candidateLorebooks,
            recursiveScanningLorebookIds = candidateLorebooks.values
                .filter { it.recursiveScanning }
                .mapTo(mutableSetOf()) { it.id },
            continuationGuidance = continuationGuidance
        )
    }

    /**
     * 记录最新生成的 Prompt 检查快照，并在 UI 上点亮 Prompt 检查器入口。
     */
    private fun recordPromptInspection(inspection: PromptInspection) {
        mLastPromptInspection = inspection
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        uiState.copy(hasPromptInspection = true).setup()
    }

    /**
     * 执行导入文件流的异步解析，并弹出导入预览对话框。
     *
     * @param read 读取导入草稿的挂起 lambda
     */
    private suspend fun readImport(read: suspend () -> StoryImportDraft) {
        try {
            val draft = read()
            val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
            uiState.copy(
                dialogState = StoryEditorDialogState.ImportPreview(
                    StoryImportPreview(draft = draft, title = draft.title)
                )
            ).setup()
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_import_failed).tryEmit()
        }
    }

    /**
     * 统一包装执行导出写入任务，并弹出成功或失败 Toast。
     *
     * @param write 导出写入操作的挂起 lambda
     */
    private suspend fun export(write: suspend () -> Unit) {
        try {
            write()
            AppViewEvent.PopupToastMessageByResId(R.string.story_export_succeeded).tryEmit()
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_export_failed).tryEmit()
        }
    }

    /**
     * 关闭当前任意展示中的对话框弹窗。
     */
    private fun closeDialog() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        uiState.copy(dialogState = StoryEditorDialogState.None).setup()
    }

    /**
     * 清理故事标题中的非法文件名字符，生成安全的文件名。
     */
    private fun safeFileName(title: String): String {
        return title.replace(INVALID_FILE_NAME_CHARS, "_").trim().ifBlank { "story" }
    }

    /**
     * 清空当前故事的撤销/重做历史栈。
     */
    private fun clearEditHistory() {
        mEditHistory.clear()
    }

    /**
     * 调度防抖自动保存任务。
     *
     * 在延迟时间内若收到新的用户输入，会取消前一个等待中的延时任务；
     * 延时结束后启动独立子任务调用 [saveDraft] 执行持久化。
     *
     * @param delayMillis 防抖等待延迟毫秒数
     */
    private fun scheduleAutoSave(delayMillis: Long = AUTO_SAVE_DELAY_MILLIS) {
        mDebounceJob?.cancel()
        mDebounceJob = viewModelScope.launch {
            delay(delayMillis)
            // 独立子任务脱离 debounce Job，后续输入只取消等待，不中断已经开始的 Room 写入。
            viewModelScope.launch { saveDraft() }
        }
    }

    /**
     * 保存当前内存正文草稿到 Room 数据库。
     *
     * 并发与冲突控制机制：
     * - 使用 [Mutex] 串行化保存；
     * - 若草稿与持久化镜像一致，直接标记已保存（Saved）；
     * - 携带 `expectedRevision` 版本号执行乐观锁更新（`mStoryRepository.updateContent`）；
     * - 若返回 false 说明数据库版本被其他流程更新，标记为冲突状态（Conflict）以保护内存草稿；
     * - 保存成功后版本号自增 1，同步更新持久化镜像与状态栏。
     *
     * @return 保存是否成功
     */
    private suspend fun saveDraft(): Boolean = mSaveMutex.withLock {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return@withLock false
        // 若已处于冲突状态，拒绝盲目覆盖
        if (uiState.topBarState.saveState == StorySaveState.Conflict) return@withLock false
        // 内容未发生改动，直接确认为已保存
        if (mDraftContent == mPersistedContent) {
            updateSaveState(StorySaveState.Saved)
            return@withLock true
        }
        val contentToSave = mDraftContent
        val expectedRevision = mRevision
        updateSaveState(StorySaveState.Saving)
        // 异步向数据库提交带版本号的正文更新
        val saved = try {
            withContext(Dispatchers.IO) {
                mStoryRepository.updateContent(
                    storyId = uiState.storyId,
                    expectedRevision = expectedRevision,
                    content = contentToSave
                )
            }
        } catch (_: Exception) {
            updateSaveState(StorySaveState.Failed)
            return@withLock false
        }
        // 乐观锁版本冲突处理
        if (!saved) {
            updateSaveState(StorySaveState.Conflict)
            return@withLock false
        }
        // 保存成功，版本号自增并同步持久化镜像
        mRevision = expectedRevision + 1L
        mPersistedContent = contentToSave
        mStory = mStory?.copy(
            content = contentToSave,
            contentRevision = mRevision
        )
        // 检查在保存期间是否有新的输入到来
        if (mDraftContent == contentToSave) {
            updateSaveState(StorySaveState.Saved)
        } else {
            updateSaveState(StorySaveState.Dirty)
            if (!mIsComposing) scheduleAutoSave()
        }
        true
    }

    /**
     * 异步构建故事设置页面的完整 UI 状态 [StoryEditorPageState.Settings]。
     *
     * 加载内容：
     * - 故事记忆、摘要、作者注释；
     * - 全量角色列表及其在当前故事中的选中状态、激活模式、关键字草稿与排序序号；
     * - 全量世界书及其条目列表，还原故事的选中状态。
     */
    private suspend fun buildSettingsState(): StoryEditorPageState.Settings {
        val story = requireNotNull(mStory)
        // 读取角色关联关系与世界书
        val relations = mStoryRepository.getStoryCharacterCandidates(story.id)
            .associateBy { it.character.id }
        val lorebooks = mLorebookRepository.getAllLorebooks()
        val lorebookNames = lorebooks.associate { it.id to it.name }
        val selectedEntryIds = mStoryRepository.getLorebookEntryIds(story).toSet()
        // 映射角色选项条目
        val characters = mCharacterRepository.getAllCharacters().map { character ->
            val relation = relations[character.id]
            StoryCharacterOptionItem(
                id = character.id,
                name = character.name,
                description = character.description,
                selected = relation != null,
                activationMode = relation?.relation?.activationMode
                    .toStoryCharacterActivationMode(),
                activationKeysDraft = relation?.activationKeys.orEmpty().joinToString(", "),
                sortOrder = relation?.relation?.sortOrder ?: Int.MAX_VALUE,
                linkedLorebookId = character.characterLorebookId.takeIf { it > 0L },
                linkedLorebookName = lorebookNames[character.characterLorebookId]
            )
        }
        // 映射世界书与条目分组
        return StoryEditorPageState.Settings(
            memory = story.memory,
            summary = story.summary,
            authorNote = story.authorNote,
            characters = normalizeCharacterOrder(characters),
            lorebookGroups = lorebooks.map { lorebook ->
                StoryLorebookGroupItem(
                    id = lorebook.id,
                    name = lorebook.name,
                    entries = mLorebookRepository.getEntriesByLorebookId(lorebook.id).map { entry ->
                        StoryLorebookEntryItem(
                            id = entry.id,
                            name = entry.name,
                            contentPreview = entry.content,
                            keywords = entry.getKeywordList(),
                            constant = entry.constant,
                            selected = false
                        )
                    }
                )
            }.restoreLorebookSelection(selectedEntryIds)
        )
    }

    /**
     * 便捷方法：在非保存中状态下更新设置页面的状态。
     */
    private fun updateSettings(
        update: StoryEditorPageState.Settings.() -> StoryEditorPageState.Settings
    ) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val settings = uiState.pageState as? StoryEditorPageState.Settings ?: return
        if (settings.isSaving) return
        uiState.copy(pageState = settings.update()).setup()
    }

    /**
     * 规范化角色列表的排序：已选角色按 sortOrder 升序且紧密重新编号（0, 1, 2...），未选角色按名称字典序排在最后。
     */
    private fun normalizeCharacterOrder(
        characters: List<StoryCharacterOptionItem>
    ): List<StoryCharacterOptionItem> {
        val selected = characters.filter { it.selected }
            .sortedBy { it.sortOrder }
            .mapIndexed { index, item -> item.copy(sortOrder = index) }
        val unselected = characters.filterNot { it.selected }
            .sortedBy { it.name.lowercase() }
            .map { it.copy(sortOrder = Int.MAX_VALUE) }
        return selected + unselected
    }

    /**
     * 数据库存储的激活模式 Int 转 UI 枚举 [StoryCharacterActivationMode]。
     */
    private fun Int?.toStoryCharacterActivationMode(): StoryCharacterActivationMode {
        return if (this == StoryCharacter.ACTIVATION_ALWAYS) {
            StoryCharacterActivationMode.Always
        } else {
            StoryCharacterActivationMode.Auto
        }
    }

    /**
     * UI 激活模式枚举转数据库存储的 Int 值。
     */
    private fun StoryCharacterActivationMode.toStorageValue(): Int {
        return when (this) {
            StoryCharacterActivationMode.Always -> StoryCharacter.ACTIVATION_ALWAYS
            StoryCharacterActivationMode.Auto -> StoryCharacter.ACTIVATION_AUTO
        }
    }

    /**
     * 解析用户输入的角色激活关键字字符串（支持中英文逗号与换行分隔并去重）。
     */
    private fun parseActivationKeys(value: String): List<String> {
        return value.split(',', '，', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * 更新顶部状态栏的保存状态 [StorySaveState]。
     */
    private fun updateSaveState(saveState: StorySaveState) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        uiState.copy(
            topBarState = uiState.topBarState.copy(saveState = saveState)
        ).setup()
    }

    /**
     * 向正文文档流 [documentFlow] 发布最新文本，并携带最新编辑文本范围以支持 UI 高亮或光标同步。
     */
    private fun publishDocument(
        content: String,
        latestEditedRange: StoryEditedTextRange? = null
    ) {
        val story = mStory ?: return
        mDocumentFlow.value = StoryEditorDocument(
            storyId = story.id,
            content = content,
            syncVersion = ++mDocumentSyncVersion,
            latestEditedRange = latestEditedRange
        )
    }

    /**
     * 故事不存在或加载失败时的退出处理。
     */
    private fun finishMissingStory() {
        AppViewEvent.PopupToastMessageByResId(R.string.story_not_found).tryEmit()
        StoryEditorUiState.finished(uiStateFlow.value).setup()
    }

    private companion object {
        /** 防抖自动保存延时（毫秒） */
        const val AUTO_SAVE_DELAY_MILLIS = 650L
        /** 文件名非法字符正则过滤 */
        val INVALID_FILE_NAME_CHARS = Regex("[\\\\/:*?\"<>|]")
    }
}

/**
 * 活跃的故事 AI 续写任务快照，记录生成上下文与局部生成增量。
 */
private data class ActiveStoryGeneration(
    val token: Any,
    val provider: LLMProvider,
    val target: StoryEditTarget,
    val baseRevision: Long,
    val sourceContent: String,
    val previousEditedRange: StoryEditedTextRange?,
    val previousWorldInfoStateJson: String,
    val previousWorldInfoGenerationStep: Int,
    val nextWorldInfoStateJson: String,
    val partialText: String = ""
) {
    /** 生成拼接了增量续写预览文本的完整正文。 */
    fun previewContent(): String {
        val previewText = prepareStoryContinuationText(sourceContent, partialText)
        return sourceContent.replaceRange(target.start, target.end, previewText)
    }

    /** 计算当前生成结果在正文中的范围。 */
    fun resultTextRange(text: String = partialText): StoryEditedTextRange? {
        if (text.isEmpty()) return null
        return StoryEditedTextRange(
            start = target.start,
            end = target.start + text.length
        )
    }
}

/** 计算撤销条目中插入文本所覆盖的范围。 */
private fun StoryUndoEntry.editedTextRange(): StoryEditedTextRange? {
    if (insertedText.isEmpty()) return null
    return StoryEditedTextRange(
        start = start,
        end = start + insertedText.length
    )
}

/** 将 LLM 异常转换为故事生成专用的失败分类 [StoryGenerationFailure]。 */
internal fun Throwable.toStoryGenerationFailure(): StoryGenerationFailure {
    return when (classifyGenerationFailure(this)) {
        LLMGenerationFailure.EmptyResponse -> StoryGenerationFailure.EmptyResult
        is LLMGenerationFailure.PromptBudget -> StoryGenerationFailure.ContextBudget
        else -> StoryGenerationFailure.Provider
    }
}

/** 判断输入法组合态结束时，是否需要补发一次被延后的自动保存。 */
internal fun shouldScheduleStoryAutoSaveAfterComposition(
    wasComposing: Boolean,
    snapshot: StoryEditorSnapshot,
    draftContent: String,
    persistedContent: String
): Boolean {
    return wasComposing &&
        !snapshot.isComposing &&
        snapshot.content == draftContent &&
        draftContent != persistedContent
}
