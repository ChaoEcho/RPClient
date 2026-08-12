package me.kafuuneko.rpclient.feature.story.editor

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.story.editor.model.StoryCharacterOptionItem
import me.kafuuneko.rpclient.feature.story.editor.model.StoryCharacterActivationMode
import me.kafuuneko.rpclient.feature.story.editor.model.StoryEditHistory
import me.kafuuneko.rpclient.feature.story.editor.model.StoryEditorDocument
import me.kafuuneko.rpclient.feature.story.editor.model.StoryEditorSnapshot
import me.kafuuneko.rpclient.feature.story.editor.model.StoryEditedTextRange
import me.kafuuneko.rpclient.feature.story.editor.model.StoryLorebookEntryItem
import me.kafuuneko.rpclient.feature.story.editor.model.StoryLorebookGroupItem
import me.kafuuneko.rpclient.feature.story.editor.model.enableLorebook
import me.kafuuneko.rpclient.feature.story.editor.model.restoreLorebookSelection
import me.kafuuneko.rpclient.feature.story.editor.model.toggleLorebook
import me.kafuuneko.rpclient.feature.story.editor.model.StoryImportPreview
import me.kafuuneko.rpclient.feature.story.editor.model.StoryTextExportFormat
import me.kafuuneko.rpclient.feature.story.editor.model.StoryUndoEntry
import me.kafuuneko.rpclient.feature.story.editor.presentation.StoryEditorContentState
import me.kafuuneko.rpclient.feature.story.editor.presentation.StoryEditorDialogState
import me.kafuuneko.rpclient.feature.story.editor.presentation.StoryEditorPageState
import me.kafuuneko.rpclient.feature.story.editor.presentation.StoryEditorReferenceState
import me.kafuuneko.rpclient.feature.story.editor.presentation.StoryEditorTopBarState
import me.kafuuneko.rpclient.feature.story.editor.presentation.StoryEditorUiIntent
import me.kafuuneko.rpclient.feature.story.editor.presentation.StoryEditorUiState
import me.kafuuneko.rpclient.feature.story.editor.presentation.StoryEditorViewEvent
import me.kafuuneko.rpclient.feature.story.editor.presentation.StorySaveState
import me.kafuuneko.rpclient.feature.story.editor.presentation.StorySettingsSection
import me.kafuuneko.rpclient.feature.story.editor.presentation.StoryGenerationFailure
import me.kafuuneko.rpclient.feature.story.editor.presentation.StoryGenerationState
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
import me.kafuuneko.rpclient.libs.llm.classifyGenerationFailure
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.prompt.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.summarySafeContent
import me.kafuuneko.rpclient.libs.AppModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 连续正文编辑器状态持有者。
 *
 * 正文草稿保留在专用文档流和私有快照中；自动保存通过 revision 乐观锁串行提交，
 * 冲突时保留内存草稿且不覆盖数据库正文。
 */
class StoryEditorViewModel : CoreViewModelWithEvent<StoryEditorUiIntent, StoryEditorUiState>(
    StoryEditorUiState.None
), KoinComponent {
    private val mStoryRepository by inject<StoryRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mStoryPromptBuilder by inject<StoryPromptBuilder>()
    private val mStorySummaryPromptBuilder by inject<StorySummaryPromptBuilder>()
    private val mStoryOutputSanitizer by inject<StoryOutputSanitizer>()
    private val mStoryArchiveRepository by inject<StoryArchiveRepository>()

    private val mDocumentFlow = MutableStateFlow<StoryEditorDocument?>(null)
    val documentFlow = mDocumentFlow.asStateFlow()

    private val mSaveMutex = Mutex()
    private var mDebounceJob: Job? = null
    private var mStory: Story? = null
    private var mDraftContent = ""
    private var mPersistedContent = ""
    private var mRevision = 0L
    private var mDocumentSyncVersion = 0L
    private var mIsComposing = false
    private var mGenerationJob: Job? = null
    private var mSummaryJob: Job? = null
    private var mActiveGeneration: ActiveStoryGeneration? = null
    private var mCancelledGeneration: ActiveStoryGeneration? = null
    private var mRecoverableGeneration: ActiveStoryGeneration? = null
    private var mLastPromptInspection: PromptInspection? = null
    private val mEditHistory = StoryEditHistory()

    @UiIntentObserver(StoryEditorUiIntent.Init::class)
    private suspend fun onInit(intent: StoryEditorUiIntent.Init) {
        if (!isStateOf<StoryEditorUiState.None>()) return
        if (intent.storyId <= 0L) {
            finishMissingStory()
            return
        }
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
        mStory = story
        mDraftContent = story.content
        mPersistedContent = story.content
        mRevision = story.contentRevision
        publishDocument(story.content)
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

    @UiIntentObserver(StoryEditorUiIntent.EditorSnapshotChanged::class)
    private fun onEditorSnapshotChanged(intent: StoryEditorUiIntent.EditorSnapshotChanged) {
        acceptEditorSnapshot(intent.snapshot)
    }

    private fun acceptEditorSnapshot(snapshot: StoryEditorSnapshot) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (!uiState.contentState.editable && snapshot.content != mDraftContent) return
        val wasComposing = mIsComposing
        mIsComposing = snapshot.isComposing
        if (snapshot.content == mDraftContent) {
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
        mEditHistory.recordManualEdit(
            previousContent = mDraftContent,
            currentContent = snapshot.content,
            worldInfoStateJson = story.worldInfoStateJson,
            worldInfoGenerationStep = story.worldInfoGenerationStep
        )
        mDraftContent = snapshot.content
        mDocumentFlow.value = mDocumentFlow.value?.copy(
            content = snapshot.content,
            latestEditedRange = mEditHistory.nextUndo()?.editedTextRange()
        )
        val saveState = if (snapshot.content == mPersistedContent) {
            mDebounceJob?.cancel()
            StorySaveState.Saved
        } else {
            StorySaveState.Dirty
        }
        uiState.copy(
            topBarState = uiState.topBarState.copy(saveState = saveState),
            contentState = uiState.contentState.copy(
                characterCount = snapshot.content.length
            ),
            canUndoEdit = mEditHistory.canUndo,
            canRedoEdit = mEditHistory.canRedo
        ).setup()
        if (!snapshot.isComposing && saveState == StorySaveState.Dirty) scheduleAutoSave()
    }

    @UiIntentObserver(StoryEditorUiIntent.FlushDraft::class)
    private suspend fun onFlushDraft() {
        if (!isStateOf<StoryEditorUiState.Normal>()) return
        mDebounceJob?.cancel()
        saveDraft()
    }

    @UiIntentObserver(StoryEditorUiIntent.RetrySave::class)
    private fun onRetrySave() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.topBarState.saveState !in setOf(StorySaveState.Failed, StorySaveState.Dirty)) {
            return
        }
        scheduleAutoSave(delayMillis = 0L)
    }

    @UiIntentObserver(StoryEditorUiIntent.Back::class)
    private suspend fun onBack() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.pageState != StoryEditorPageState.Editor) {
            cancelSummaryJob()
            val settings = uiState.pageState as? StoryEditorPageState.Settings
            if (settings?.isSaving == true) return
            getOrNull<StoryEditorUiState.Normal>()
                ?.copy(pageState = StoryEditorPageState.Editor)
                ?.setup()
            return
        }
        if (uiState.dialogState != StoryEditorDialogState.None) {
            uiState.copy(dialogState = StoryEditorDialogState.None).setup()
            return
        }
        if (uiState.generationState is StoryGenerationState.Streaming) {
            stopGeneration()
            return
        }
        val failedGeneration = uiState.generationState as? StoryGenerationState.Failed
        if (failedGeneration?.recoverablePartial?.isNotBlank() == true) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.story_resolve_partial_before_leaving
            ).tryEmit()
            return
        }
        mDebounceJob?.cancel()
        if (!saveDraft()) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_leave_with_unsaved_draft).tryEmit()
            return
        }
        StoryEditorUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(StoryEditorUiIntent.CopyConflictDraft::class)
    private fun onCopyConflictDraft() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.topBarState.saveState != StorySaveState.Conflict) return
        StoryEditorViewEvent.CopyDraft(mDraftContent).tryEmit()
    }

    @UiIntentObserver(StoryEditorUiIntent.ReloadAfterConflict::class)
    private suspend fun onReloadAfterConflict() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.topBarState.saveState != StorySaveState.Conflict) return
        val story = withContext(Dispatchers.IO) {
            mStoryRepository.getStory(uiState.storyId)
        } ?: run {
            finishMissingStory()
            return
        }
        mStory = story
        mDraftContent = story.content
        mPersistedContent = story.content
        mRevision = story.contentRevision
        clearEditHistory()
        publishDocument(story.content)
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return
        current.copy(
            topBarState = current.topBarState.copy(saveState = StorySaveState.Saved),
            contentState = current.contentState.copy(characterCount = story.content.length),
            canUndoEdit = false,
            canRedoEdit = false
        ).setup()
    }

    @UiIntentObserver(StoryEditorUiIntent.OpenStorySettings::class)
    private suspend fun onOpenStorySettings() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.generationState !is StoryGenerationState.Idle) return
        if (uiState.pageState != StoryEditorPageState.Editor) return
        if (uiState.dialogState != StoryEditorDialogState.None) return
        uiState.copy(pageState = StoryEditorPageState.LoadingSettings).setup()
        try {
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

    @UiIntentObserver(StoryEditorUiIntent.ChangeMemory::class)
    private fun onChangeMemory(intent: StoryEditorUiIntent.ChangeMemory) {
        updateSettings { copy(memory = intent.value) }
    }

    @UiIntentObserver(StoryEditorUiIntent.ChangeSummary::class)
    private fun onChangeSummary(intent: StoryEditorUiIntent.ChangeSummary) {
        updateSettings { copy(summary = intent.value) }
    }

    @UiIntentObserver(StoryEditorUiIntent.ChangeAuthorNote::class)
    private fun onChangeAuthorNote(intent: StoryEditorUiIntent.ChangeAuthorNote) {
        updateSettings { copy(authorNote = intent.value) }
    }

    @UiIntentObserver(StoryEditorUiIntent.SummarizeStory::class)
    private suspend fun onSummarizeStory() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val settings = uiState.pageState as? StoryEditorPageState.Settings ?: return
        if (settings.isSaving || uiState.dialogState != StoryEditorDialogState.None) return
        if (mDraftContent.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_summary_empty_document).tryEmit()
            return
        }
        if (!saveDraft()) return
        val provider = withContext(Dispatchers.IO) { mLLMRepository.getSelectedProvider() }
        if (provider == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_summary_failed).tryEmit()
            return
        }
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return
        val currentSettings = current.pageState as? StoryEditorPageState.Settings ?: return
        current.copy(
            dialogState = StoryEditorDialogState.SummarizingStory
        ).setup()
        launchSummaryJob(
            storyId = current.storyId,
            memory = currentSettings.memory,
            currentSummary = currentSettings.summary,
            sourceContent = mDraftContent,
            sourceRevision = mRevision,
            provider = provider
        )
    }

    @UiIntentObserver(StoryEditorUiIntent.CancelStorySummary::class)
    private suspend fun onCancelStorySummary() {
        cancelSummaryJob()
    }

    @UiIntentObserver(StoryEditorUiIntent.ConfirmStorySummary::class)
    private suspend fun onConfirmStorySummary() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val preview = uiState.dialogState as? StoryEditorDialogState.StorySummaryPreview
            ?: return
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
        mStory = mStory?.copy(summary = preview.content)
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return
        val currentSettings = current.pageState as? StoryEditorPageState.Settings ?: return
        current.copy(
            referenceState = current.referenceState.copy(
                hasMemory = currentSettings.memory.isNotBlank() || preview.content.isNotBlank()
            ),
            pageState = currentSettings.copy(summary = preview.content),
            dialogState = StoryEditorDialogState.None
        ).setup()
        AppViewEvent.PopupToastMessageByResId(R.string.story_summary_updated).tryEmit()
    }

    @UiIntentObserver(StoryEditorUiIntent.ToggleStoryCharacter::class)
    private fun onToggleStoryCharacter(intent: StoryEditorUiIntent.ToggleStoryCharacter) {
        updateSettings {
            val target = characters.firstOrNull { it.id == intent.characterId } ?: return@updateSettings this
            val selecting = !target.selected
            val nextOrder = characters.filter { it.selected }.maxOfOrNull { it.sortOrder }
                ?.plus(1)
                ?: 0
            copy(
                characters = normalizeCharacterOrder(
                    characters.map { item ->
                        if (item.id != target.id) item else item.copy(
                            selected = !item.selected,
                            sortOrder = if (item.selected) Int.MAX_VALUE else nextOrder
                        )
                    }
                ),
                lorebookGroups = if (selecting && target.linkedLorebookId != null) {
                    lorebookGroups.enableLorebook(target.linkedLorebookId)
                } else {
                    lorebookGroups
                }
            )
        }
    }

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

    @UiIntentObserver(StoryEditorUiIntent.MoveStoryCharacter::class)
    private fun onMoveStoryCharacter(intent: StoryEditorUiIntent.MoveStoryCharacter) {
        updateSettings {
            val selected = characters.filter { it.selected }.sortedBy { it.sortOrder }.toMutableList()
            val from = selected.indexOfFirst { it.id == intent.characterId }
            if (from < 0) return@updateSettings this
            val to = (from + intent.offset).coerceIn(0, selected.lastIndex)
            if (from == to) return@updateSettings this
            val moved = selected.removeAt(from)
            selected.add(to, moved)
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

    @UiIntentObserver(StoryEditorUiIntent.ToggleLorebook::class)
    private fun onToggleLorebook(intent: StoryEditorUiIntent.ToggleLorebook) {
        updateSettings {
            copy(lorebookGroups = lorebookGroups.toggleLorebook(intent.lorebookId))
        }
    }

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

    @UiIntentObserver(StoryEditorUiIntent.SaveStorySettings::class)
    private suspend fun onSaveStorySettings() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val settings = uiState.pageState as? StoryEditorPageState.Settings ?: return
        if (settings.isSaving || uiState.dialogState != StoryEditorDialogState.None) return
        uiState.copy(pageState = settings.copy(isSaving = true)).setup()
        try {
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

    @UiIntentObserver(StoryEditorUiIntent.StopGeneration::class)
    private suspend fun onStopGeneration() {
        stopGeneration()
    }

    @UiIntentObserver(StoryEditorUiIntent.InsertRecoverablePartial::class)
    private suspend fun onInsertRecoverablePartial() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val failure = uiState.generationState as? StoryGenerationState.Failed ?: return
        val active = mRecoverableGeneration ?: return
        if (failure.recoverablePartial.isBlank()) return
        applyGeneratedResult(active, failure.recoverablePartial)
    }

    @UiIntentObserver(StoryEditorUiIntent.CopyRecoverablePartial::class)
    private fun onCopyRecoverablePartial() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val failure = uiState.generationState as? StoryGenerationState.Failed ?: return
        if (failure.recoverablePartial.isNotBlank()) {
            StoryEditorViewEvent.CopyGeneratedText(failure.recoverablePartial).tryEmit()
        }
    }

    @UiIntentObserver(StoryEditorUiIntent.DiscardRecoverablePartial::class)
    private fun onDiscardRecoverablePartial() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.generationState !is StoryGenerationState.Failed) return
        mRecoverableGeneration = null
        uiState.copy(generationState = StoryGenerationState.Idle).setup()
    }

    @UiIntentObserver(StoryEditorUiIntent.UndoLastEdit::class)
    private suspend fun onUndoLastEdit() {
        mDebounceJob?.cancel()
        if (!saveDraft()) return
        val entry = mEditHistory.nextUndo() ?: return
        undoEdit(entry)
    }

    @UiIntentObserver(StoryEditorUiIntent.RedoLastEdit::class)
    private suspend fun onRedoLastEdit() {
        val entry = mEditHistory.nextRedo() ?: return
        redoEdit(entry)
    }

    @UiIntentObserver(StoryEditorUiIntent.OpenPromptInspector::class)
    private fun onOpenPromptInspector() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val inspection = mLastPromptInspection ?: return
        uiState.copy(dialogState = StoryEditorDialogState.PromptInspector(inspection)).setup()
    }

    @UiIntentObserver(StoryEditorUiIntent.OpenFileActions::class)
    private fun onOpenFileActions() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.generationState !is StoryGenerationState.Idle) return
        uiState.copy(dialogState = StoryEditorDialogState.FileActions).setup()
    }

    @UiIntentObserver(StoryEditorUiIntent.ImportTextClick::class)
    private suspend fun onImportTextClick() {
        if (!saveDraft()) return
        closeDialog()
        StoryEditorViewEvent.OpenTextImporter.tryEmit()
    }

    @UiIntentObserver(StoryEditorUiIntent.ImportTextResult::class)
    private suspend fun onImportTextResult(intent: StoryEditorUiIntent.ImportTextResult) {
        readImport { mStoryArchiveRepository.readTextImportFromUri(intent.uri) }
    }

    @UiIntentObserver(StoryEditorUiIntent.ImportStoryClick::class)
    private suspend fun onImportStoryClick() {
        if (!saveDraft()) return
        closeDialog()
        StoryEditorViewEvent.OpenStoryImporter.tryEmit()
    }

    @UiIntentObserver(StoryEditorUiIntent.ImportStoryResult::class)
    private suspend fun onImportStoryResult(intent: StoryEditorUiIntent.ImportStoryResult) {
        readImport { mStoryArchiveRepository.readArchiveImportFromUri(intent.uri) }
    }

    @UiIntentObserver(StoryEditorUiIntent.ChangeImportTitle::class)
    private fun onChangeImportTitle(intent: StoryEditorUiIntent.ChangeImportTitle) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? StoryEditorDialogState.ImportPreview ?: return
        if (dialog.preview.isSaving) return
        uiState.copy(
            dialogState = dialog.copy(preview = dialog.preview.copy(title = intent.value))
        ).setup()
    }

    @UiIntentObserver(StoryEditorUiIntent.ConfirmImport::class)
    private suspend fun onConfirmImport() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? StoryEditorDialogState.ImportPreview ?: return
        if (dialog.preview.isSaving || dialog.preview.title.isBlank()) return
        uiState.copy(
            dialogState = dialog.copy(preview = dialog.preview.copy(isSaving = true))
        ).setup()
        try {
            val storyId = withContext(Dispatchers.IO) {
                mStoryArchiveRepository.saveImport(
                    draft = dialog.preview.draft,
                    title = dialog.preview.title
                )
            }
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

    @UiIntentObserver(StoryEditorUiIntent.ExportTextResult::class)
    private suspend fun onExportTextResult(intent: StoryEditorUiIntent.ExportTextResult) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        export { mStoryArchiveRepository.exportTextToUri(uiState.storyId, intent.uri) }
    }

    @UiIntentObserver(StoryEditorUiIntent.ExportStoryClick::class)
    private suspend fun onExportStoryClick() {
        if (!saveDraft()) return
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        closeDialog()
        StoryEditorViewEvent.OpenStoryExporter(
            safeFileName(uiState.topBarState.title) + ".rpstory.json"
        ).tryEmit()
    }

    @UiIntentObserver(StoryEditorUiIntent.ExportStoryResult::class)
    private suspend fun onExportStoryResult(intent: StoryEditorUiIntent.ExportStoryResult) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        export { mStoryArchiveRepository.exportArchiveToUri(uiState.storyId, intent.uri) }
    }

    @UiIntentObserver(StoryEditorUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val import = uiState.dialogState as? StoryEditorDialogState.ImportPreview
        if (import?.preview?.isSaving == true) return
        if (uiState.dialogState == StoryEditorDialogState.SummarizingStory) return
        uiState.copy(dialogState = StoryEditorDialogState.None).setup()
    }

    private fun launchSummaryJob(
        storyId: Long,
        memory: String,
        currentSummary: String,
        sourceContent: String,
        sourceRevision: Long,
        provider: LLMProvider
    ) {
        mSummaryJob?.cancel()
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

    private suspend fun runStorySummary(
        storyId: Long,
        memory: String,
        currentSummary: String,
        sourceContent: String,
        sourceRevision: Long,
        provider: LLMProvider
    ) {
        try {
            val request = withContext(Dispatchers.Default) {
                mStorySummaryPromptBuilder.build(
                    memory = memory,
                    currentSummary = currentSummary,
                    content = sourceContent,
                    provider = provider
                )
            }
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
            uiState.copy(
                dialogState = StoryEditorDialogState.StorySummaryPreview(
                    content = summary,
                    sourceContentRevision = sourceRevision
                )
            ).setup()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
            if (uiState.dialogState != StoryEditorDialogState.SummarizingStory) return
            uiState.copy(dialogState = StoryEditorDialogState.None).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.story_summary_failed).tryEmit()
        } finally {
            mSummaryJob = null
        }
    }

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

    private suspend fun startGeneration(
        continuationGuidance: String = ""
    ) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.generationState !is StoryGenerationState.Idle) return
        if (uiState.pageState != StoryEditorPageState.Editor) return
        if (!uiState.contentState.editable) return
        uiState.copy(
            contentState = uiState.contentState.copy(editable = false),
            generationState = StoryGenerationState.Preparing,
            dialogState = StoryEditorDialogState.None
        ).setup()
        mDebounceJob?.cancel()
        val target = StoryEditTarget(mDraftContent.length, mDraftContent.length)
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
        val buildResult = try {
            val promptContext = withContext(Dispatchers.IO) {
                buildPromptContext(
                    story = story,
                    target = target,
                    continuationGuidance = continuationGuidance
                )
            }
            withContext(Dispatchers.Default) { mStoryPromptBuilder.build(promptContext) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: StoryPromptBudgetException) {
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
            AppViewEvent.PopupToastMessageByResId(R.string.story_generation_failed).tryEmit()
            val current = getOrNull<StoryEditorUiState.Normal>() ?: return
            current.copy(
                contentState = current.contentState.copy(editable = true),
                generationState = StoryGenerationState.Failed(StoryGenerationFailure.Setup),
                dialogState = StoryEditorDialogState.None
            ).setup()
            return
        }
        recordPromptInspection(buildResult.inspection)
        val active = ActiveStoryGeneration(
            token = Any(),
            target = target,
            baseRevision = mRevision,
            sourceContent = mDraftContent,
            previousEditedRange = mDocumentFlow.value?.latestEditedRange,
            previousWorldInfoStateJson = story.worldInfoStateJson,
            previousWorldInfoGenerationStep = story.worldInfoGenerationStep,
            nextWorldInfoStateJson = buildResult.nextWorldInfoStateJson
        )
        mRecoverableGeneration = null
        mActiveGeneration = active
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return
        current.copy(
            generationState = StoryGenerationState.Streaming(""),
            dialogState = StoryEditorDialogState.None
        ).setup()
        mGenerationJob = viewModelScope.launch {
            runGeneration(active, buildResult.request)
        }
    }

    private fun restoreEditorAfterPreparation() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        if (uiState.generationState !is StoryGenerationState.Preparing) return
        uiState.copy(
            contentState = uiState.contentState.copy(editable = true),
            generationState = StoryGenerationState.Idle
        ).setup()
    }

    private suspend fun runGeneration(
        initial: ActiveStoryGeneration,
        request: LLMGenerationRequest
    ) {
        var active = initial
        var applyingResult = false
        try {
            if (AppModel.streamEnabled) {
                mLLMRepository.streamGenerateWithSelectedProvider(
                    request,
                    routingSessionKey = mStory?.id?.let { "story:$it" }
                ).collect { event ->
                    if (event is LLMStreamEvent.Delta) {
                        active = active.copy(partialText = active.partialText + event.content)
                        mActiveGeneration = active
                        updateStreamingState(active)
                    }
                }
            } else {
                val response = mLLMRepository.generateWithSelectedProvider(
                    request,
                    routingSessionKey = mStory?.id?.let { "story:$it" }
                )
                active = active.copy(partialText = response.content)
                mActiveGeneration = active
                updateStreamingState(active)
            }
            applyingResult = true
            applyGeneratedResult(active, active.partialText)
        } catch (error: CancellationException) {
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

    private suspend fun stopGeneration() {
        val active = mActiveGeneration ?: return
        mCancelledGeneration = null
        mGenerationJob?.cancel()
        mGenerationJob?.join()
        mGenerationJob = null
        val stopped = mCancelledGeneration ?: active
        mCancelledGeneration = null
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
        withContext(NonCancellable) {
            try {
                applyGeneratedResult(stopped, stopped.partialText)
            } catch (_: Exception) {
                showGenerationFailure(stopped, StoryGenerationFailure.ApplyResult)
            }
        }
    }

    private fun showGenerationFailure(
        active: ActiveStoryGeneration,
        reason: StoryGenerationFailure
    ) {
        publishDocument(active.sourceContent, active.previousEditedRange)
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

    private suspend fun applyGeneratedResult(
        active: ActiveStoryGeneration,
        rawResult: String
    ) {
        val sanitizedResult = mStoryOutputSanitizer.sanitize(rawResult)
        val result = prepareStoryContinuationText(active.sourceContent, sanitizedResult)
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
        publishDocument(applied.content, active.resultTextRange(result))
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return
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

    private suspend fun undoEdit(entry: StoryUndoEntry): Boolean {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return false
        if (uiState.generationState !is StoryGenerationState.Idle) return false
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
        if (reverted == null) {
            clearEditHistory()
            uiState.copy(
                generationState = StoryGenerationState.Failed(StoryGenerationFailure.Conflict),
                canUndoEdit = false,
                canRedoEdit = false
            ).setup()
            return false
        }
        mRevision = reverted.revision
        mDraftContent = reverted.content
        mPersistedContent = reverted.content
        mStory = mStory?.copy(
            content = reverted.content,
            contentRevision = reverted.revision,
            worldInfoStateJson = entry.previousWorldInfoStateJson,
            worldInfoGenerationStep = entry.previousWorldInfoGenerationStep
        )
        mEditHistory.confirmUndo(entry)
        publishDocument(
            reverted.content,
            mEditHistory.nextUndo()?.editedTextRange()
        )
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return true
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

    private suspend fun redoEdit(entry: StoryUndoEntry): Boolean {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return false
        if (uiState.generationState !is StoryGenerationState.Idle) return false
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
        if (applied == null) {
            clearEditHistory()
            uiState.copy(
                generationState = StoryGenerationState.Failed(StoryGenerationFailure.Conflict),
                canUndoEdit = false,
                canRedoEdit = false
            ).setup()
            return false
        }
        mRevision = applied.revision
        mDraftContent = applied.content
        mPersistedContent = applied.content
        mStory = mStory?.copy(
            content = applied.content,
            contentRevision = applied.revision,
            worldInfoStateJson = entry.nextWorldInfoStateJson,
            worldInfoGenerationStep = applied.worldInfoGenerationStep
        )
        mEditHistory.confirmRedo(entry)
        publishDocument(
            applied.content,
            entry.editedTextRange()
        )
        val current = getOrNull<StoryEditorUiState.Normal>() ?: return true
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

    private suspend fun buildPromptContext(
        story: Story,
        target: StoryEditTarget,
        continuationGuidance: String
    ): StoryPromptContext {
        val provider = mLLMRepository.getSelectedProvider() ?: error("No enabled provider")
        val characterCandidates = mStoryRepository.getStoryCharacterCandidates(story.id)
        val explicitEntryIds = mStoryRepository.getLorebookEntryIds(story).toSet()
        val lorebooks = mLorebookRepository.getAllLorebooks()
        val entries = explicitEntryIds.mapNotNull { mLorebookRepository.getEntryById(it) }
        val candidateLorebookIds = entries.mapTo(mutableSetOf()) { it.lorebookId }
        val candidateLorebooks = lorebooks
            .filter { it.id in candidateLorebookIds }
            .associateBy { it.id }
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

    private fun recordPromptInspection(inspection: PromptInspection) {
        mLastPromptInspection = inspection
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        uiState.copy(hasPromptInspection = true).setup()
    }

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

    private suspend fun export(write: suspend () -> Unit) {
        try {
            write()
            AppViewEvent.PopupToastMessageByResId(R.string.story_export_succeeded).tryEmit()
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_export_failed).tryEmit()
        }
    }

    private fun closeDialog() {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        uiState.copy(dialogState = StoryEditorDialogState.None).setup()
    }

    private fun safeFileName(title: String): String {
        return title.replace(INVALID_FILE_NAME_CHARS, "_").trim().ifBlank { "story" }
    }

    private fun clearEditHistory() {
        mEditHistory.clear()
    }

    private fun scheduleAutoSave(delayMillis: Long = AUTO_SAVE_DELAY_MILLIS) {
        mDebounceJob?.cancel()
        mDebounceJob = viewModelScope.launch {
            delay(delayMillis)
            // 独立子任务脱离 debounce Job，后续输入只取消等待，不中断已经开始的 Room 写入。
            viewModelScope.launch { saveDraft() }
        }
    }

    private suspend fun saveDraft(): Boolean = mSaveMutex.withLock {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return@withLock false
        if (uiState.topBarState.saveState == StorySaveState.Conflict) return@withLock false
        if (mDraftContent == mPersistedContent) {
            updateSaveState(StorySaveState.Saved)
            return@withLock true
        }
        val contentToSave = mDraftContent
        val expectedRevision = mRevision
        updateSaveState(StorySaveState.Saving)
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
        if (!saved) {
            updateSaveState(StorySaveState.Conflict)
            return@withLock false
        }
        mRevision = expectedRevision + 1L
        mPersistedContent = contentToSave
        mStory = mStory?.copy(
            content = contentToSave,
            contentRevision = mRevision
        )
        if (mDraftContent == contentToSave) {
            updateSaveState(StorySaveState.Saved)
        } else {
            updateSaveState(StorySaveState.Dirty)
            if (!mIsComposing) scheduleAutoSave()
        }
        true
    }

    private suspend fun buildSettingsState(): StoryEditorPageState.Settings {
        val story = requireNotNull(mStory)
        val relations = mStoryRepository.getStoryCharacterCandidates(story.id)
            .associateBy { it.character.id }
        val lorebooks = mLorebookRepository.getAllLorebooks()
        val lorebookNames = lorebooks.associate { it.id to it.name }
        val selectedEntryIds = mStoryRepository.getLorebookEntryIds(story).toSet()
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

    private fun updateSettings(
        update: StoryEditorPageState.Settings.() -> StoryEditorPageState.Settings
    ) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        val settings = uiState.pageState as? StoryEditorPageState.Settings ?: return
        if (settings.isSaving) return
        uiState.copy(pageState = settings.update()).setup()
    }

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

    private fun Int?.toStoryCharacterActivationMode(): StoryCharacterActivationMode {
        return if (this == StoryCharacter.ACTIVATION_ALWAYS) {
            StoryCharacterActivationMode.Always
        } else {
            StoryCharacterActivationMode.Auto
        }
    }

    private fun StoryCharacterActivationMode.toStorageValue(): Int {
        return when (this) {
            StoryCharacterActivationMode.Always -> StoryCharacter.ACTIVATION_ALWAYS
            StoryCharacterActivationMode.Auto -> StoryCharacter.ACTIVATION_AUTO
        }
    }

    private fun parseActivationKeys(value: String): List<String> {
        return value.split(',', '，', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun updateSaveState(saveState: StorySaveState) {
        val uiState = getOrNull<StoryEditorUiState.Normal>() ?: return
        uiState.copy(
            topBarState = uiState.topBarState.copy(saveState = saveState)
        ).setup()
    }

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

    private fun finishMissingStory() {
        AppViewEvent.PopupToastMessageByResId(R.string.story_not_found).tryEmit()
        StoryEditorUiState.finished(uiStateFlow.value).setup()
    }

    private companion object {
        const val AUTO_SAVE_DELAY_MILLIS = 650L
        val INVALID_FILE_NAME_CHARS = Regex("[\\\\/:*?\"<>|]")
    }
}

private data class ActiveStoryGeneration(
    val token: Any,
    val target: StoryEditTarget,
    val baseRevision: Long,
    val sourceContent: String,
    val previousEditedRange: StoryEditedTextRange?,
    val previousWorldInfoStateJson: String,
    val previousWorldInfoGenerationStep: Int,
    val nextWorldInfoStateJson: String,
    val partialText: String = ""
) {
    fun previewContent(): String {
        val previewText = prepareStoryContinuationText(sourceContent, partialText)
        return sourceContent.replaceRange(target.start, target.end, previewText)
    }

    fun resultTextRange(text: String = partialText): StoryEditedTextRange? {
        if (text.isEmpty()) return null
        return StoryEditedTextRange(
            start = target.start,
            end = target.start + text.length
        )
    }
}

private fun StoryUndoEntry.editedTextRange(): StoryEditedTextRange? {
    if (insertedText.isEmpty()) return null
    return StoryEditedTextRange(
        start = start,
        end = start + insertedText.length
    )
}

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
