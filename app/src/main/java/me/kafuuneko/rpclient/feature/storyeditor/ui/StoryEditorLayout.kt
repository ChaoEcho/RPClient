package me.kafuuneko.rpclient.feature.storyeditor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import me.kafuuneko.rpclient.utils.rememberPromptMacroVisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryCharacterOptionItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryCharacterActivationMode
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorDocument
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorSnapshot
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryLorebookEntryItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryLorebookGroupItem
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorDialogState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorContentState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryContinuationInputState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorPageState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorReferenceState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorTopBarState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiIntent
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StorySaveState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationFailure
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StorySettingsSection
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryTextExportFormat
import me.kafuuneko.rpclient.ui.dialog.AppActionItem
import me.kafuuneko.rpclient.ui.dialog.AppActionListDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import me.kafuuneko.rpclient.ui.dialog.LoadingDialog
import me.kafuuneko.rpclient.ui.dialog.PromptInspectorDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.ui.widgets.StoryUserPersonaCard

/** 连续正文编辑器及 Story 设置的 Compose 入口。 */
@Composable
fun StoryEditorLayout(
    uiState: StoryEditorUiState,
    document: StoryEditorDocument?,
    emit: StoryEditorUiIntent.() -> Unit
) {
    when (uiState) {
        StoryEditorUiState.None -> EditorLoading()
        is StoryEditorUiState.Normal -> StoryEditorNormal(uiState, document, emit)
        is StoryEditorUiState.Finished -> StoryEditorLayout(uiState.previous, document) {}
    }
}

@Composable
private fun StoryEditorNormal(
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val showingSettings = state.pageState != StoryEditorPageState.Editor
    BackHandler {
        if (showingSettings) {
            StoryEditorUiIntent.CloseStorySettings.emit()
        } else {
            StoryEditorUiIntent.Back.emit()
        }
    }
    when (val pageState = state.pageState) {
        StoryEditorPageState.Editor -> StoryEditorPage(state, document, emit)
        StoryEditorPageState.LoadingSettings -> StorySettingsLoadingPage(emit)
        is StoryEditorPageState.Settings -> StorySettingsPage(pageState, emit)
    }
    EditorDialogSwitch(state, emit)
}

@Composable
private fun StoryEditorPage(
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val editorState = remember(document?.storyId) {
        TextFieldState(document?.content.orEmpty())
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = state.topBarState.title,
                onBack = { StoryEditorUiIntent.Back.emit() },
                actions = {
                    SaveStatus(state.topBarState.saveState, emit)
                    IconButton(
                        onClick = { StoryEditorUiIntent.OpenPromptInspector.emit() },
                        enabled = state.hasPromptInspection
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = stringResource(R.string.prompt_inspector_title)
                        )
                    }
                    IconButton(
                        onClick = { StoryEditorUiIntent.OpenFileActions.emit() },
                        enabled = state.generationState is StoryGenerationState.Idle
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = stringResource(R.string.story_file_actions)
                        )
                    }
                    IconButton(
                        onClick = { StoryEditorUiIntent.OpenStorySettings.emit() },
                        enabled = state.generationState is StoryGenerationState.Idle
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.story_settings)
                        )
                    }
                }
            )
        },
        bottomBar = {
            EditorBottomBar(
                characterCount = state.contentState.characterCount,
                referenceState = state.referenceState,
                contentState = state.contentState,
                continuationInputState = state.continuationInputState,
                generationState = state.generationState,
                canUndoEdit = state.canUndoEdit,
                canRedoEdit = state.canRedoEdit,
                onContinue = document?.let {
                    {
                        StoryEditorUiIntent.ContinueStory(
                            StoryEditorSnapshot(
                                content = editorState.text.toString(),
                                isComposing = editorState.composition != null
                            )
                        ).emit()
                    }
                },
                emit = emit
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SaveProblemBanner(state.topBarState.saveState, emit)
            GenerationProblemBanner(state.generationState, emit)
            if (document == null) {
                EditorLoading()
            } else {
                StoryTextEditor(
                    modifier = Modifier.weight(1f),
                    document = document,
                    editorState = editorState,
                    editable = state.contentState.editable,
                    generationState = state.generationState,
                    emit = emit
                )
            }
        }
    }
}

@Composable
private fun StoryTextEditor(
    modifier: Modifier,
    document: StoryEditorDocument,
    editorState: TextFieldState,
    editable: Boolean,
    generationState: StoryGenerationState,
    emit: StoryEditorUiIntent.() -> Unit
) {
    // 正文不使用 rememberSaveable，避免长文进入 Activity Bundle；Room 承担进程恢复。
    val editorScrollState = rememberScrollState()
    val followStreamingOutput = generationState is StoryGenerationState.Streaming
    val generatedTextColor = MaterialTheme.colorScheme.primary
    val displayedEditedRange = document.latestEditedRange.takeUnless {
        generationState is StoryGenerationState.Streaming ||
            generationState is StoryGenerationState.Applying
    }
    val currentEditedRange by rememberUpdatedState(displayedEditedRange)
    val currentEditedStyle by rememberUpdatedState(
        SpanStyle(
            color = generatedTextColor,
            background = generatedTextColor.copy(alpha = 0.12f),
            fontWeight = FontWeight.Medium
        )
    )
    // 转换实例必须跨流式分片保持稳定，否则 BasicTextField 会反复重建布局状态并闪烁。
    val latestEditedOutputTransformation = remember {
        OutputTransformation {
            val editedRange = currentEditedRange
            if (
                editedRange != null &&
                editedRange.start < editedRange.end &&
                editedRange.end <= length
            ) {
                addStyle(
                    spanStyle = currentEditedStyle,
                    start = editedRange.start,
                    end = editedRange.end
                )
            }
        }
    }
    LaunchedEffect(document.syncVersion) {
        if (editorState.text.toString() != document.content) {
            editorState.setTextAndPlaceCursorAtEnd(document.content)
        }
    }
    LaunchedEffect(followStreamingOutput, editorScrollState) {
        if (!followStreamingOutput) return@LaunchedEffect
        // 流式文本布局完成后 maxValue 会持续增长，跟随它才能稳定滚到最新输出。
        snapshotFlow { editorScrollState.maxValue }.collect { bottom ->
            editorScrollState.scrollTo(bottom)
        }
    }
    LaunchedEffect(editorState) {
        snapshotFlow {
            StoryEditorSnapshot(
                content = editorState.text.toString(),
                isComposing = editorState.composition != null
            )
        }.distinctUntilChanged().collect { snapshot ->
            StoryEditorUiIntent.EditorSnapshotChanged(snapshot).emit()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        color = MaterialTheme.colorScheme.surface
    ) {
        BasicTextField(
            state = editorState,
            modifier = Modifier.fillMaxSize(),
            enabled = editable,
            textStyle = MaterialTheme.typography.bodyLarge.merge(
                TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 28.sp
                )
            ),
            scrollState = editorScrollState,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            outputTransformation = latestEditedOutputTransformation,
            decorator = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    if (editorState.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.story_editor_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun SaveStatus(
    saveState: StorySaveState,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val clickable = saveState == StorySaveState.Failed
    Surface(
        modifier = Modifier.clickable(enabled = clickable) {
            StoryEditorUiIntent.RetrySave.emit()
        },
        shape = RoundedCornerShape(50),
        color = when (saveState) {
            StorySaveState.Failed,
            StorySaveState.Conflict -> MaterialTheme.colorScheme.errorContainer
            StorySaveState.Dirty,
            StorySaveState.Saving -> MaterialTheme.colorScheme.secondaryContainer
            StorySaveState.Saved -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (saveState) {
                    StorySaveState.Saved -> Icons.Rounded.Check
                    StorySaveState.Dirty -> Icons.Rounded.Save
                    StorySaveState.Saving -> Icons.Rounded.HourglassTop
                    StorySaveState.Failed,
                    StorySaveState.Conflict -> Icons.Rounded.ErrorOutline
                },
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = stringResource(
                    when (saveState) {
                        StorySaveState.Saved -> R.string.story_saved
                        StorySaveState.Dirty -> R.string.story_unsaved
                        StorySaveState.Saving -> R.string.story_saving
                        StorySaveState.Failed -> R.string.story_save_failed_short
                        StorySaveState.Conflict -> R.string.story_save_conflict_short
                    }
                ),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SaveProblemBanner(
    saveState: StorySaveState,
    emit: StoryEditorUiIntent.() -> Unit
) {
    when (saveState) {
        StorySaveState.Failed -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.story_save_failed_message),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { StoryEditorUiIntent.RetrySave.emit() }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
        StorySaveState.Conflict -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.story_save_conflict_message),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { StoryEditorUiIntent.CopyConflictDraft.emit() }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.story_copy_draft))
                    }
                    TextButton(onClick = { StoryEditorUiIntent.ReloadAfterConflict.emit() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.story_reload_saved))
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun GenerationProblemBanner(
    generationState: StoryGenerationState,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val failure = generationState as? StoryGenerationState.Failed ?: return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    when (failure.reason) {
                        StoryGenerationFailure.Setup -> R.string.story_generation_failed
                        StoryGenerationFailure.Provider -> {
                            if (failure.recoverablePartial.isBlank()) {
                                R.string.story_generation_provider_failed_without_partial
                            } else {
                                R.string.story_generation_provider_failed
                            }
                        }
                        StoryGenerationFailure.ApplyResult -> {
                            R.string.story_generation_apply_failed
                        }
                        StoryGenerationFailure.Conflict -> R.string.story_generation_conflict
                        StoryGenerationFailure.EmptyResult -> R.string.story_generation_empty
                        StoryGenerationFailure.ContextBudget -> R.string.story_generation_budget
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (failure.detail.isNotBlank()) {
                Text(
                    text = stringResource(R.string.story_generation_largest_characters, failure.detail),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (failure.recoverablePartial.isNotBlank()) {
                    TextButton(onClick = { StoryEditorUiIntent.CopyRecoverablePartial.emit() }) {
                        Text(stringResource(R.string.copy))
                    }
                    TextButton(onClick = { StoryEditorUiIntent.InsertRecoverablePartial.emit() }) {
                        Text(stringResource(R.string.story_insert_partial))
                    }
                }
                TextButton(onClick = { StoryEditorUiIntent.DiscardRecoverablePartial.emit() }) {
                    Text(
                        stringResource(
                            if (failure.recoverablePartial.isBlank()) {
                                R.string.close
                            } else {
                                R.string.story_discard
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorBottomBar(
    characterCount: Int,
    referenceState: StoryEditorReferenceState,
    contentState: StoryEditorContentState,
    continuationInputState: StoryContinuationInputState,
    generationState: StoryGenerationState,
    canUndoEdit: Boolean,
    canRedoEdit: Boolean,
    onContinue: (() -> Unit)?,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                value = continuationInputState.guidanceDraft,
                onValueChange = { StoryEditorUiIntent.ChangeContinuationGuidance(it).emit() },
                enabled = contentState.editable && generationState is StoryGenerationState.Idle,
                minLines = 1,
                maxLines = 4,
                label = { Text(stringResource(R.string.story_continuation_guidance)) },
                placeholder = { Text(stringResource(R.string.story_continuation_guidance_placeholder)) },
                shape = RoundedCornerShape(16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = stringResource(R.string.story_character_count, characterCount),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${stringResource(R.string.story_character_references_count, referenceState.characterCount)} · ${stringResource(R.string.story_lorebook_entries_count, referenceState.lorebookEntryCount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (generationState) {
                    is StoryGenerationState.Streaming -> Button(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            StoryEditorUiIntent.StopGeneration.emit()
                        }
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.story_stop_generation))
                    }
                    StoryGenerationState.Preparing,
                    StoryGenerationState.Applying -> CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                    else -> {
                        val historyEnabled = contentState.editable &&
                            generationState is StoryGenerationState.Idle
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                StoryEditorUiIntent.UndoLastEdit.emit()
                            },
                            enabled = historyEnabled && canUndoEdit
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = stringResource(R.string.story_undo)
                            )
                        }
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                StoryEditorUiIntent.RedoLastEdit.emit()
                            },
                            enabled = historyEnabled && canRedoEdit
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Redo,
                                contentDescription = stringResource(R.string.story_redo)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onContinue?.invoke()
                            },
                            enabled = contentState.editable &&
                                generationState is StoryGenerationState.Idle &&
                                onContinue != null
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.story_continue))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorDialogSwitch(
    state: StoryEditorUiState.Normal,
    emit: StoryEditorUiIntent.() -> Unit
) {
    when (val dialogState = state.dialogState) {
        StoryEditorDialogState.None -> Unit
        is StoryEditorDialogState.PromptInspector -> PromptInspectorDialog(
            inspection = dialogState.inspection,
            onDismissRequest = { StoryEditorUiIntent.DismissDialog.emit() },
            onCopyRequest = { StoryEditorUiIntent.CopyPromptItem(it).emit() }
        )
        StoryEditorDialogState.FileActions -> FileActionsDialog(emit)
        is StoryEditorDialogState.ImportPreview -> ImportPreviewDialog(dialogState, emit)
        StoryEditorDialogState.SummarizingStory -> LoadingDialog(
            title = stringResource(R.string.story_summarizing),
            description = stringResource(R.string.story_summary_desc),
            onCancel = { StoryEditorUiIntent.CancelStorySummary.emit() }
        )
        is StoryEditorDialogState.StorySummaryPreview -> StorySummaryPreviewDialog(
            dialogState,
            emit
        )
    }
}

@Composable
private fun StorySummaryPreviewDialog(
    state: StoryEditorDialogState.StorySummaryPreview,
    emit: StoryEditorUiIntent.() -> Unit
) {
    AppDialogScaffold(
        onDismissRequest = { StoryEditorUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.story_summary_preview),
        badgeIcon = Icons.Rounded.Description,
        badgeTone = DialogBadgeTone.Primary,
        compactHeader = true,
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = { StoryEditorUiIntent.ConfirmStorySummary.emit() }
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.content,
            onValueChange = {},
            minLines = 5,
            maxLines = 12,
            readOnly = true,
            visualTransformation = rememberPromptMacroVisualTransformation(),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun FileActionsDialog(emit: StoryEditorUiIntent.() -> Unit) {
    AppActionListDialog(
        onDismissRequest = { StoryEditorUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.story_file_actions),
        badgeIcon = Icons.Rounded.FolderOpen,
        badgeTone = DialogBadgeTone.Primary,
        actions = listOf(
            AppActionItem(
                icon = Icons.Rounded.FileDownload,
                title = stringResource(R.string.story_import_text),
                onClick = { StoryEditorUiIntent.ImportTextClick.emit() }
            ),
            AppActionItem(
                icon = Icons.Rounded.FileDownload,
                title = stringResource(R.string.story_import_archive),
                onClick = { StoryEditorUiIntent.ImportStoryClick.emit() }
            ),
            AppActionItem(
                icon = Icons.Rounded.FileUpload,
                title = stringResource(R.string.story_export_txt),
                onClick = { StoryEditorUiIntent.ExportTextClick(StoryTextExportFormat.Text).emit() }
            ),
            AppActionItem(
                icon = Icons.Rounded.FileUpload,
                title = stringResource(R.string.story_export_markdown),
                onClick = { StoryEditorUiIntent.ExportTextClick(StoryTextExportFormat.Markdown).emit() }
            ),
            AppActionItem(
                icon = Icons.Rounded.FileUpload,
                title = stringResource(R.string.story_export_archive),
                onClick = { StoryEditorUiIntent.ExportStoryClick.emit() }
            )
        )
    )
}

@Composable
private fun ImportPreviewDialog(
    state: StoryEditorDialogState.ImportPreview,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val preview = state.preview
    AppDialogScaffold(
        onDismissRequest = {
            if (!preview.isSaving) StoryEditorUiIntent.DismissDialog.emit()
        },
        title = stringResource(R.string.story_import_preview),
        badgeIcon = Icons.Rounded.FileDownload,
        badgeTone = DialogBadgeTone.Primary,
        confirmText = stringResource(R.string.story_import_create),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = preview.title.isNotBlank() && !preview.isSaving,
        isConfirmLoading = preview.isSaving,
        onConfirm = { StoryEditorUiIntent.ConfirmImport.emit() },
        onDismiss = {
            if (!preview.isSaving) StoryEditorUiIntent.DismissDialog.emit()
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = preview.title,
                onValueChange = { StoryEditorUiIntent.ChangeImportTitle(it).emit() },
                label = { Text(stringResource(R.string.story_title)) },
                singleLine = true,
                enabled = !preview.isSaving,
                shape = RoundedCornerShape(14.dp)
            )
            Text(
                text = stringResource(
                    R.string.story_import_summary,
                    preview.draft.content.length,
                    preview.draft.characterHints.size,
                    preview.draft.lorebookHints.size
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorySettingsPage(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.story_settings),
                onBack = { StoryEditorUiIntent.CloseStorySettings.emit() },
                actions = {
                    TextButton(
                        onClick = { StoryEditorUiIntent.SaveStorySettings.emit() },
                        enabled = !state.isSaving
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SettingsSectionTabs(state.selectedSection, emit)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (state.selectedSection) {
                    StorySettingsSection.Context -> ContextSettings(state, emit)
                    StorySettingsSection.Characters -> CharacterSettings(state, emit)
                    StorySettingsSection.Lorebook -> LorebookSettings(state, emit)
                }
            }
        }
    }
}

@Composable
private fun StorySettingsLoadingPage(emit: StoryEditorUiIntent.() -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.story_settings),
                onBack = { StoryEditorUiIntent.CloseStorySettings.emit() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.story_loading_settings))
            }
        }
    }
}

@Composable
private fun SettingsSectionTabs(
    selected: StorySettingsSection,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsTab(
            selected = selected == StorySettingsSection.Context,
            label = stringResource(R.string.story_context_settings)
        ) { StoryEditorUiIntent.SelectSettingsSection(StorySettingsSection.Context).emit() }
        SettingsTab(
            selected = selected == StorySettingsSection.Characters,
            label = stringResource(R.string.story_character_references)
        ) { StoryEditorUiIntent.SelectSettingsSection(StorySettingsSection.Characters).emit() }
        SettingsTab(
            selected = selected == StorySettingsSection.Lorebook,
            label = stringResource(R.string.world_book)
        ) { StoryEditorUiIntent.SelectSettingsSection(StorySettingsSection.Lorebook).emit() }
    }
}

@Composable
private fun SettingsTab(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else {
            null
        }
    )
}

@Composable
private fun ContextSettings(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val controlsEnabled = !state.isSaving
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        UserPersonaSetting(state, controlsEnabled, emit)
        SettingIntro(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            title = stringResource(R.string.story_memory),
            subtitle = stringResource(R.string.story_memory_desc)
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.memory,
            onValueChange = { StoryEditorUiIntent.ChangeMemory(it).emit() },
            label = { Text(stringResource(R.string.story_memory)) },
            minLines = 6,
            enabled = controlsEnabled,
            shape = RoundedCornerShape(16.dp)
        )
        SettingIntro(
            icon = Icons.Rounded.AutoAwesome,
            title = stringResource(R.string.story_summary),
            subtitle = stringResource(R.string.story_summary_desc)
        )
        StorySummarySettings(state, emit)
        SettingIntro(
            icon = Icons.Rounded.Edit,
            title = stringResource(R.string.story_author_note),
            subtitle = stringResource(R.string.story_author_note_desc)
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.authorNote,
            onValueChange = { StoryEditorUiIntent.ChangeAuthorNote(it).emit() },
            label = { Text(stringResource(R.string.story_author_note)) },
            minLines = 4,
            enabled = controlsEnabled,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun UserPersonaSetting(
    state: StoryEditorPageState.Settings,
    enabled: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    StoryUserPersonaCard(
        checked = state.includeUserPersona,
        onCheckedChange = {
            StoryEditorUiIntent.SetIncludeUserPersona(it).emit()
        },
        enabled = enabled
    )
}

@Composable
private fun StorySummarySettings(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.summary,
        onValueChange = { StoryEditorUiIntent.ChangeSummary(it).emit() },
        label = { Text(stringResource(R.string.story_summary)) },
        minLines = 6,
        enabled = !state.isSaving,
        shape = RoundedCornerShape(16.dp)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(
            onClick = { StoryEditorUiIntent.SummarizeStory.emit() },
            enabled = !state.isSaving
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.story_summarize_now))
        }
    }
}

@Composable
private fun CharacterSettings(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    if (state.characters.isEmpty()) {
        EmptySettingsMessage(R.string.story_no_characters)
        return
    }
    val selectedCount = state.characters.count { it.selected }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.story_character_references_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(state.characters, key = { it.id }) { character ->
            val selectedIndex = state.characters
                .filter { it.selected }
                .indexOfFirst { it.id == character.id }
            CharacterSettingCard(
                character = character,
                canMoveUp = character.selected && selectedIndex > 0,
                canMoveDown = character.selected && selectedIndex in 0 until selectedCount - 1,
                emit = emit
            )
        }
    }
}

@Composable
private fun CharacterSettingCard(
    character: StoryCharacterOptionItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (character.selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (character.selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RpIconBubble(Icons.Rounded.Group)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = character.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = character.selected,
                    onCheckedChange = {
                        StoryEditorUiIntent.ToggleStoryCharacter(character.id).emit()
                    }
                )
            }
            if (character.selected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = character.activationMode == StoryCharacterActivationMode.Primary,
                        onClick = {
                            StoryEditorUiIntent.SetCharacterActivationMode(
                                character.id,
                                StoryCharacterActivationMode.Primary
                            ).emit()
                        },
                        label = { Text(stringResource(R.string.story_character_primary)) }
                    )
                    FilterChip(
                        selected = character.activationMode == StoryCharacterActivationMode.Always,
                        onClick = {
                            StoryEditorUiIntent.SetCharacterActivationMode(
                                character.id,
                                StoryCharacterActivationMode.Always
                            ).emit()
                        },
                        label = { Text(stringResource(R.string.story_character_always)) }
                    )
                    FilterChip(
                        selected = character.activationMode == StoryCharacterActivationMode.Auto,
                        onClick = {
                            StoryEditorUiIntent.SetCharacterActivationMode(
                                character.id,
                                StoryCharacterActivationMode.Auto
                            ).emit()
                        },
                        label = { Text(stringResource(R.string.story_character_auto)) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            StoryEditorUiIntent.MoveStoryCharacter(character.id, -1).emit()
                        },
                        enabled = canMoveUp
                    ) {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(R.string.move_up)
                        )
                    }
                    IconButton(
                        onClick = {
                            StoryEditorUiIntent.MoveStoryCharacter(character.id, 1).emit()
                        },
                        enabled = canMoveDown
                    ) {
                        Icon(
                            Icons.Rounded.ArrowDownward,
                            contentDescription = stringResource(R.string.move_down)
                        )
                    }
                }
                character.linkedLorebookName?.let { lorebookName ->
                    RpTagRow(
                        tags = listOf(
                            stringResource(R.string.story_linked_lorebook, lorebookName)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LorebookSettings(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    if (state.lorebookGroups.isEmpty()) {
        EmptySettingsMessage(R.string.no_world_books)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.story_lorebook_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(state.lorebookGroups, key = { it.id }) { group ->
            LorebookGroupCard(
                group = group,
                emit = emit
            )
        }
    }
}

@Composable
private fun LorebookGroupCard(
    group: StoryLorebookGroupItem,
    emit: StoryEditorUiIntent.() -> Unit
) {
    var expanded by remember(group.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (group.selectedCount > 0) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        color = if (group.selectedCount > 0) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                RpIconBubble(Icons.Rounded.Book)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(
                            R.string.enabled_entries_count,
                            group.selectedCount,
                            group.entries.size
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = group.isAllSelected,
                    enabled = group.entries.isNotEmpty(),
                    onCheckedChange = {
                        StoryEditorUiIntent.ToggleLorebook(group.id).emit()
                    }
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (group.entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.story_lorebook_empty),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        group.entries.forEach { entry ->
                            LorebookEntryRow(entry, emit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LorebookEntryRow(
    entry: StoryLorebookEntryItem,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { StoryEditorUiIntent.ToggleLorebookEntry(entry.id).emit() },
        shape = RoundedCornerShape(14.dp),
        color = if (entry.selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            RpIconBubble(Icons.Rounded.Book)
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.name.ifBlank { stringResource(R.string.unnamed_entry) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val constantLabel = if (entry.constant) {
                    stringResource(R.string.entry_constant)
                } else {
                    null
                }
                val tags = listOfNotNull(constantLabel) + entry.keywords
                if (tags.isNotEmpty()) {
                    RpTagRow(tags = tags, maxCount = 3)
                } else {
                    Text(
                        text = entry.contentPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Switch(
                checked = entry.selected,
                onCheckedChange = {
                    StoryEditorUiIntent.ToggleLorebookEntry(entry.id).emit()
                }
            )
            }
        }
    }
}

@Composable
private fun SettingIntro(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RpIconBubble(icon)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptySettingsMessage(messageRes: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(messageRes),
            modifier = Modifier.padding(28.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditorLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun StoryEditorLayoutPreview() {
    AppTheme(dynamicColor = false) {
        StoryEditorLayout(
            uiState = StoryEditorUiState.Normal(
                storyId = 1L,
                topBarState = StoryEditorTopBarState(
                    title = "Rain over the old city"
                ),
                contentState = StoryEditorContentState(
                    characterCount = 128
                ),
                referenceState = StoryEditorReferenceState(
                    hasMemory = true,
                    hasAuthorNote = true,
                    characterCount = 2,
                    lorebookEntryCount = 4
                )
            ),
            document = StoryEditorDocument(
                storyId = 1L,
                content = "# Chapter One\n\nRain tapped softly against the station windows.",
                syncVersion = 1L
            ),
            emit = {}
        )
    }
}
