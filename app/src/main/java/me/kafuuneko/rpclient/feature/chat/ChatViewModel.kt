package me.kafuuneko.rpclient.feature.chat

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toGenerationFailureMessage
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.presentation.ChatDialogState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatConversationState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatPage
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatViewEvent
import me.kafuuneko.rpclient.feature.chat.presentation.resolveExportDialogState
import me.kafuuneko.rpclient.feature.chat.utils.ChatLorebookEntryData
import me.kafuuneko.rpclient.feature.chat.utils.replaceStreamingMessage
import me.kafuuneko.rpclient.feature.chat.utils.toChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.utils.toChatLorebookGroupItems
import me.kafuuneko.rpclient.feature.chat.utils.toChatMessageItems
import me.kafuuneko.rpclient.feature.chat.utils.toChatSessionItem
import me.kafuuneko.rpclient.feature.characteredit.CharacterEditActivity
import me.kafuuneko.rpclient.feature.worldbooklist.WorldBookListActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.chat.ChatArchiveRepository
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.prompt.ChatPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.PromptBuildContext
import me.kafuuneko.rpclient.libs.prompt.PromptGenerationMode
import me.kafuuneko.rpclient.libs.prompt.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.SummaryPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.summarySafeContent
import me.kafuuneko.rpclient.libs.regex.RegexExecutionMode
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScriptRepository
import me.kafuuneko.rpclient.libs.regex.RegexScriptRuntime
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.utils.formatTimestamp
import me.kafuuneko.rpclient.libs.utils.toggle
import me.kafuuneko.rpclient.libs.utils.toggleAll
import me.kafuuneko.rpclient.libs.utils.toDefaultChatTitle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 单角色聊天页的状态持有者。
 *
 * 负责会话加载、消息持久化、Prompt 构建、流式生成、Regex 处理和自动总结。
 * 流式生成由生成协程独占收尾；停止和返回只取消并等待该协程完成原子提交。
 */
class ChatViewModel : CoreViewModelWithEvent<ChatUiIntent, ChatUiState>(
    ChatUiState.None
), KoinComponent {
    private val mChatRepository by inject<ChatRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mChatPromptBuilder by inject<ChatPromptBuilder>()
    private val mSummaryPromptBuilder by inject<SummaryPromptBuilder>()
    private val mRegexRepository by inject<RegexScriptRepository>()
    private val mRegexRuntime by inject<RegexScriptRuntime>()
    private val mChatArchiveRepository by inject<ChatArchiveRepository>()
    private val mContext by inject<Context>()

    /** 当前页面绑定的会话 ID，初始化成功后在页面生命周期内保持不变。 */
    private var mSessionId: Long? = null
    /** 当前模型生成任务，用于阻止并发生成和响应停止操作。 */
    private var mGenerationJob: Job? = null
    /** 后台自动总结任务，与正文生成分开取消和收尾。 */
    private var mSummaryJob: Job? = null
    /** 用户明确触发的对话文件导出任务；运行期间阻止页面结束以免留下半写入文件。 */
    private var mChatExportJob: Job? = null
    /** 仅暴露当前不可变流式快照供 UI 刷新读取；生成协程是唯一写入者和收尾所有者。 */
    private var mActiveStreamingGeneration: ActiveStreamingGeneration? = null
    /** 最近一次实际发送请求的检查报告，供调试对话框读取。 */
    private var mLastPromptInspection: PromptInspection? = null

    /**
     * 初始化真实会话数据。
     *
     * 新建会话进入 Chat 页时，如果数据库中还没有消息且携带开场白，则在这里将开场白落库为角色消息。
     */
    @UiIntentObserver(ChatUiIntent.Init::class)
    private suspend fun onInit(intent: ChatUiIntent.Init) {
        if (!isStateOf<ChatUiState.None>()) return
        val sessionId = intent.sessionId?.toLongOrNull()
        if (sessionId == null) {
            finishWithToast(R.string.invalid_session_id)
            return
        }
        mSessionId = sessionId
        val loaded = withContext(Dispatchers.IO) { loadNormalState(sessionId) }
        if (loaded == null) {
            finishWithToast(R.string.session_not_found)
            return
        }
        loaded.setup()
    }

    @UiIntentObserver(ChatUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val refreshed = withContext(Dispatchers.IO) {
            loadNormalState(
                sessionId = sessionId,
                inputDraft = uiState.conversationState.inputDraft,
                page = uiState.page,
                isExpanded = uiState.lorebookState.isExpanded,
                loadState = uiState.loadState,
                generationState = uiState.conversationState.generationState,
                expandedThinkBlockIds = uiState.conversationState.expandedThinkBlockIds,
                editingMessageId = uiState.conversationState.editingMessageId,
                editingMessageDraft = uiState.conversationState.editingMessageDraft,
                dialogState = uiState.dialogState
            )
        }
        if (refreshed == null) {
            mGenerationJob?.cancel()
            ChatUiState.finished(uiStateFlow.value).setup()
            return
        }
        refreshed.copy(
            dialogState = refreshed.dialogState.resolveExportDialogState(
                isExportActive = mChatExportJob?.isActive == true
            )
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.Back::class)
    private suspend fun onBack() {
        if (mChatExportJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.chat_export_in_progress).tryEmit()
            return
        }
        val uiState = getOrNull<ChatUiState.Normal>()
        if (uiState?.page == ChatPage.Settings) {
            uiState.copy(page = ChatPage.Conversation).setup()
            return
        }
        cancelActiveGeneration()
        ChatUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(ChatUiIntent.ChangeInputDraft::class)
    private suspend fun onChangeInputDraft(intent: ChatUiIntent.ChangeInputDraft) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(
            conversationState = uiState.conversationState.copy(inputDraft = intent.value)
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: ChatUiIntent.ChangeLorebookQuery) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(
            lorebookState = uiState.lorebookState.copy(
                query = intent.value,
                visibleGroups = uiState.lorebookState.groups.filterForQuery(intent.value)
            )
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.SendMessage::class)
    private suspend fun onSendMessage() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val rawInput = uiState.conversationState.inputDraft.trim()
            .ifBlank { AppModel.replaceEmptyMessagePrompt.trim() }
        if (rawInput.isBlank()) {
            continueLastAssistantMessage(sessionId)
            return
        }
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }

        // 发送流程不使用 CoreViewModel 的状态回滚式任务队列，因为流式停止时需要保留 partial 内容。
        mGenerationJob = viewModelScope.launch {
            runCatching {
                val input = withContext(Dispatchers.IO) {
                    applyUserRegex(sessionId, rawInput)
                }
                withContext(Dispatchers.IO) {
                    mChatRepository.createMessage(sessionId, ChatMessage.Source.User, input)
                }
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = "",
                    isExpanded = uiState.lorebookState.isExpanded,
                    generationState = ChatGenerationState.Requesting
                )
                val built = withContext(Dispatchers.IO) { buildGenerationRequest(sessionId) }
                recordPromptInspection(built.inspection)
                if (AppModel.streamEnabled) {
                    generateStreaming(
                        sessionId,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.Char),
                        built.worldInfoStateJson
                    )
                } else {
                    generateOnce(
                        sessionId,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.Char),
                        built.worldInfoStateJson
                    )
                }
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                val message = throwable.toGenerationFailureMessage(
                    mContext,
                    R.string.generation_failed
                ) ?: return@onFailure
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = "",
                    isExpanded = uiState.lorebookState.isExpanded,
                    generationState = ChatGenerationState.Failed(message)
                )
                AppViewEvent.PopupToastMessage(message).tryEmit()
            }
        }
    }

    @UiIntentObserver(ChatUiIntent.StopGeneration::class)
    private suspend fun onStopGeneration() {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        if (!cancelActiveGeneration()) return
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    /** 取消当前生成并等待生成协程在 NonCancellable 收尾中完成唯一一次提交。 */
    private suspend fun cancelActiveGeneration(): Boolean {
        val job = mGenerationJob ?: return false
        if (!job.isActive) return false
        job.cancelAndJoin()
        return true
    }

    @UiIntentObserver(ChatUiIntent.RegenerateLast::class)
    private suspend fun onRegenerateLast() {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        regenerateLastAssistantMessage(sessionId)
    }

    @UiIntentObserver(ChatUiIntent.ContinueLast::class)
    private suspend fun onContinueLast() {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        continueLastAssistantMessage(sessionId)
    }

    @UiIntentObserver(ChatUiIntent.ImpersonateUser::class)
    private suspend fun onImpersonateUser() {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        generateUserImpersonation(sessionId)
    }

    @UiIntentObserver(ChatUiIntent.RegenerateFromMessage::class)
    private suspend fun onRegenerateFromMessage(intent: ChatUiIntent.RegenerateFromMessage) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        val messageId = intent.messageId.toLongOrNull() ?: return
        val latestAssistantMessage = withContext(Dispatchers.IO) {
            mChatRepository.getMessagesBySessionId(sessionId).lastOrNull { it.source == ChatMessage.Source.Char }
        }
        if (latestAssistantMessage?.id != messageId) {
            AppViewEvent.PopupToastMessageByResId(R.string.only_latest_assistant_reply_regenerate).tryEmit()
            return
        }
        regenerateLastAssistantMessage(sessionId)
    }

    /**
     * 从指定普通消息创建独立会话分支。
     *
     * Repository 在事务中复制消息并选择该边界仍有效的摘要；原会话和当前页面状态
     * 保持不变，分支成功后通过一次性事件打开新会话。
     */
    @UiIntentObserver(ChatUiIntent.BranchFromMessage::class)
    private suspend fun onBranchFromMessage(intent: ChatUiIntent.BranchFromMessage) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val messageId = intent.messageId.toLongOrNull() ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        uiState.copy(loadState = ChatLoadState.Saving).setup()
        val branchCreateTime = System.currentTimeMillis()
        val branchId = withContext(Dispatchers.IO) {
            mChatRepository.createBranchSession(
                sourceSessionId = sessionId,
                throughMessageId = messageId,
                title = branchCreateTime.toDefaultChatTitle(),
                createTime = branchCreateTime
            )
        }
        if (branchId == 0L) {
            AppViewEvent.PopupToastMessageByResId(R.string.branch_create_failed).tryEmit()
            refreshUiState(sessionId = sessionId)
            return
        }
        ChatViewEvent.OpenSession(branchId.toString()).emit()
    }


    @UiIntentObserver(ChatUiIntent.OpenSessionLore::class)
    private fun onOpenSessionLore() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(
            lorebookState = uiState.lorebookState.copy(
                isExpanded = !uiState.lorebookState.isExpanded
            )
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.ToggleSessionLoreEntry::class)
    private suspend fun onToggleSessionLoreEntry(intent: ChatUiIntent.ToggleSessionLoreEntry) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        if (uiState.lorebookState.groups.none { group ->
                group.entries.any { it.id == intent.entryId }
            }
        ) return
        val enabledIds = uiState.session.enabledLorebookEntryIds.toggle(intent.entryId)
        saveSessionLorebookEntryIds(sessionId, enabledIds)
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.conversationState.inputDraft,
            isExpanded = uiState.lorebookState.isExpanded,
            generationState = uiState.conversationState.generationState
        )
    }

    @UiIntentObserver(ChatUiIntent.ToggleSessionLorebook::class)
    private suspend fun onToggleSessionLorebook(intent: ChatUiIntent.ToggleSessionLorebook) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val group = uiState.lorebookState.groups
            .firstOrNull { it.lorebookId == intent.lorebookId } ?: return
        val entryIds = group.entries.map { it.id }.toSet()
        if (entryIds.isEmpty()) return
        val enabledIds = uiState.session.enabledLorebookEntryIds.toggleAll(entryIds)
        saveSessionLorebookEntryIds(sessionId, enabledIds)
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.conversationState.inputDraft,
            isExpanded = uiState.lorebookState.isExpanded,
            generationState = uiState.conversationState.generationState
        )
    }

    @UiIntentObserver(ChatUiIntent.OpenChatSettings::class)
    private fun onOpenChatSettings() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Settings).setup()
    }

    @UiIntentObserver(ChatUiIntent.OpenPromptInspector::class)
    private fun onOpenPromptInspector() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val inspection = mLastPromptInspection
        if (inspection == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.prompt_inspector_unavailable).tryEmit()
            return
        }
        uiState.copy(dialogState = ChatDialogState.PromptInspector(inspection)).setup()
    }

    @UiIntentObserver(ChatUiIntent.CloseChatSettings::class)
    private fun onCloseChatSettings() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Conversation).setup()
    }

    @UiIntentObserver(ChatUiIntent.ExportChatClick::class)
    private fun onExportChatClick() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        if (uiState.loadState != ChatLoadState.None || mChatExportJob?.isActive == true) return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.stop_generation_before_exporting
            ).tryEmit()
            return
        }
        if (mSummaryJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.wait_for_summary_before_exporting
            ).tryEmit()
            return
        }
        val timestamp = System.currentTimeMillis().formatTimestamp("yyyyMMdd_HHmmss")
        ChatViewEvent.OpenChatExporter(fileName = "chat_$timestamp.jsonl").tryEmit()
    }

    @UiIntentObserver(ChatUiIntent.ExportChatResult::class)
    private fun onExportChatResult(intent: ChatUiIntent.ExportChatResult) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        if (uiState.loadState != ChatLoadState.None || mChatExportJob?.isActive == true) return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.stop_generation_before_exporting
            ).tryEmit()
            return
        }
        if (mSummaryJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.wait_for_summary_before_exporting
            ).tryEmit()
            return
        }
        uiState.copy(dialogState = ChatDialogState.Exporting).setup()
        mChatExportJob = viewModelScope.launch {
            try {
                mChatArchiveRepository.exportToUri(sessionId, intent.uri)
                AppViewEvent.PopupToastMessageByResId(R.string.export_chat_success).tryEmit()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.export_chat_failed).tryEmit()
            } finally {
                mChatExportJob = null
                getOrNull<ChatUiState.Normal>()?.let { current ->
                    current.copy(
                        dialogState = current.dialogState.resolveExportDialogState(
                            isExportActive = false
                        )
                    ).setup()
                }
            }
        }
    }

    @UiIntentObserver(ChatUiIntent.SummarizeNow::class)
    private suspend fun onSummarizeNow() {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.stop_generation_before_summarizing).tryEmit()
            return
        }
        launchSummaryJob(sessionId, showToast = true)
    }

    @UiIntentObserver(ChatUiIntent.RestorePreviousSummary::class)
    private suspend fun onRestorePreviousSummary() {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        if (mGenerationJob?.isActive == true || mSummaryJob?.isActive == true) return
        val restored = withContext(Dispatchers.IO) {
            mChatRepository.restorePreviousSummary(sessionId)
        }
        AppViewEvent.PopupToastMessageByResId(
            if (restored) R.string.summary_restored else R.string.no_previous_summary
        ).tryEmit()
        if (restored) refreshUiState(sessionId = sessionId)
    }

    @UiIntentObserver(ChatUiIntent.ToggleAutoSummaryPaused::class)
    private suspend fun onToggleAutoSummaryPaused(
        intent: ChatUiIntent.ToggleAutoSummaryPaused
    ) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateAutoSummaryPaused(sessionId, intent.paused)
        }
        refreshUiState(sessionId = sessionId, page = ChatPage.Settings)
    }

    @UiIntentObserver(ChatUiIntent.CancelSummary::class)
    private fun onCancelSummary() {
        if (!isStateOf<ChatUiState.Normal>()) return
        mSummaryJob?.cancel()
    }

    @UiIntentObserver(ChatUiIntent.DeleteSessionClick::class)
    private fun onDeleteSessionClick() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.stop_generation_before_deleting).tryEmit()
            return
        }
        uiState.copy(
            dialogState = ChatDialogState.DeleteSessionConfirm(uiState.session.title)
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.ConfirmDeleteSession::class)
    private suspend fun onConfirmDeleteSession() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.stop_generation_before_deleting).tryEmit()
            uiState.copy(dialogState = ChatDialogState.None).setup()
            return
        }
        uiState.copy(
            loadState = ChatLoadState.Deleting,
            dialogState = ChatDialogState.None
        ).setup()
        withContext(Dispatchers.IO) {
            mChatRepository.deleteSession(sessionId)
        }
        AppViewEvent.PopupToastMessageByResId(R.string.chat_deleted).tryEmit()
        ChatUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(ChatUiIntent.DeleteMessageClick::class)
    private fun onDeleteMessageClick(intent: ChatUiIntent.DeleteMessageClick) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.stop_generation_before_deleting_message).tryEmit()
            return
        }
        uiState.copy(
            dialogState = ChatDialogState.DeleteMessageConfirm(intent.messageId)
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.ConfirmDeleteMessage::class)
    private suspend fun onConfirmDeleteMessage(intent: ChatUiIntent.ConfirmDeleteMessage) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.stop_generation_before_deleting_message).tryEmit()
            uiState.copy(dialogState = ChatDialogState.None).setup()
            return
        }
        uiState.copy(dialogState = ChatDialogState.None).setup()
        val messageId = intent.messageId.toLongOrNull() ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.deleteMessage(messageId)
        }
        AppViewEvent.PopupToastMessageByResId(R.string.message_deleted).tryEmit()
        refreshUiState(sessionId = sessionId)
    }

    @UiIntentObserver(ChatUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(dialogState = ChatDialogState.None).setup()
    }

    @UiIntentObserver(ChatUiIntent.SaveTitle::class)
    private suspend fun onSaveTitle(intent: ChatUiIntent.SaveTitle) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionTitle(sessionId, intent.value.trim().ifBlank { mContext.getString(R.string.untitled_chat) })
        }
        refreshUiState(sessionId = sessionId)
    }

    @UiIntentObserver(ChatUiIntent.SaveSummary::class)
    private suspend fun onSaveSummary(intent: ChatUiIntent.SaveSummary) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateCurrentSummary(sessionId, intent.value)
        }
        refreshUiState(sessionId = sessionId)
    }

    @UiIntentObserver(ChatUiIntent.SaveUserNote::class)
    private suspend fun onSaveUserNote(intent: ChatUiIntent.SaveUserNote) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionUserNote(sessionId, intent.value)
        }
        refreshUiState(sessionId = sessionId)
    }

    @UiIntentObserver(ChatUiIntent.OpenWorldBookManager::class)
    private fun onOpenWorldBookManager() {
        if (!isStateOf<ChatUiState.Normal>()) return
        AppViewEvent.StartActivity(WorldBookListActivity::class.java).tryEmit()
    }

    @UiIntentObserver(ChatUiIntent.OpenCharacterEditor::class)
    private fun onOpenCharacterEditor() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        AppViewEvent.StartActivity(
            activity = CharacterEditActivity::class.java,
            extras = Bundle().apply {
                putLong(CharacterEditActivity.EXTRA_CHARACTER_ID, uiState.character.id)
            }
        ).tryEmit()
    }

    /**
     * 保存当前会话的用户名称。
     *
     * 空白名称统一保存为默认值 `You`，避免生成 prompt 和消息署名出现空名称。
     */
    @UiIntentObserver(ChatUiIntent.SaveUserName::class)
    private suspend fun onSaveUserName(intent: ChatUiIntent.SaveUserName) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionUserName(sessionId, intent.value.trim().ifBlank { "You" })
        }
        refreshUiState(sessionId = sessionId)
    }

    /**
     * 保存当前会话的用户描述。
     *
     * 用户描述仅影响当前会话，并在保存前移除首尾空白。
     */
    @UiIntentObserver(ChatUiIntent.SaveUserDescription::class)
    private suspend fun onSaveUserDescription(intent: ChatUiIntent.SaveUserDescription) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionUserDescription(sessionId, intent.value.trim())
        }
        refreshUiState(sessionId = sessionId)
    }

    @UiIntentObserver(ChatUiIntent.SaveCreatorNotes::class)
    private suspend fun onSaveCreatorNotes(intent: ChatUiIntent.SaveCreatorNotes) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionCreatorNotes(sessionId, intent.value)
        }
        refreshUiState(sessionId = sessionId)
    }

    @UiIntentObserver(ChatUiIntent.CopyMessage::class)
    private suspend fun onCopyMessage(intent: ChatUiIntent.CopyMessage) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val message = uiState.conversationState.messages
            .firstOrNull { it.id == intent.messageId } ?: return
        if (message.content.isBlank()) return
        ChatViewEvent.CopyText(message.content).emit()
    }

    @UiIntentObserver(ChatUiIntent.StartEditMessage::class)
    private suspend fun onStartEditMessage(intent: ChatUiIntent.StartEditMessage) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val message = uiState.conversationState.messages
            .firstOrNull { it.id == intent.messageId } ?: return
        if (message.isStreaming) return
        val rawContent = withContext(Dispatchers.IO) {
            val sessionId = mSessionId ?: return@withContext null
            mChatRepository.getMessagesBySessionId(sessionId)
                .firstOrNull { it.id.toString() == intent.messageId }
                ?.content
        } ?: return
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                editingMessageId = message.id,
                editingMessageDraft = rawContent
            )
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.ChangeEditingMessageDraft::class)
    private fun onChangeEditingMessageDraft(intent: ChatUiIntent.ChangeEditingMessageDraft) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        if (uiState.conversationState.editingMessageId == null) return
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                editingMessageDraft = intent.value
            )
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.SaveEditingMessage::class)
    private suspend fun onSaveEditingMessage() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val messageId = uiState.conversationState.editingMessageId?.toLongOrNull() ?: return
        withContext(Dispatchers.IO) {
            val message = mChatRepository.getMessagesBySessionId(sessionId)
                .firstOrNull { it.id == messageId } ?: return@withContext
            val content = when (message.source) {
                ChatMessage.Source.User -> applyUserRegex(
                    sessionId,
                    uiState.conversationState.editingMessageDraft,
                    isEdit = true
                )
                ChatMessage.Source.Char -> applyAiRegex(
                    sessionId,
                    uiState.conversationState.editingMessageDraft,
                    isEdit = true
                )
                ChatMessage.Source.System,
                ChatMessage.Source.Summary -> uiState.conversationState.editingMessageDraft
            }
            mChatRepository.updateMessageContent(messageId, content)
        }
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.conversationState.inputDraft,
            isExpanded = uiState.lorebookState.isExpanded,
            generationState = uiState.conversationState.generationState,
            expandedThinkBlockIds = uiState.conversationState.expandedThinkBlockIds,
            editingMessageId = null,
            editingMessageDraft = ""
        )
    }

    @UiIntentObserver(ChatUiIntent.CancelEditingMessage::class)
    private fun onCancelEditingMessage() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                editingMessageId = null,
                editingMessageDraft = ""
            )
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.ToggleThinkBlock::class)
    private fun onToggleThinkBlock(intent: ChatUiIntent.ToggleThinkBlock) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val ids = uiState.conversationState.expandedThinkBlockIds.toMutableSet()
        if (!ids.add(intent.blockId)) {
            ids.remove(intent.blockId)
        }
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                expandedThinkBlockIds = ids.toSet()
            )
        ).setup()
    }

    private suspend fun regenerateLastAssistantMessage(sessionId: Long) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Conversation).setup()
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        // 当前数据结构尚未支持 swipe/branch，只允许重生成最后一条角色回复，避免破坏中间历史。
        val messages = withContext(Dispatchers.IO) {
            mChatRepository.getMessagesBySessionId(sessionId)
        }
        val latestAssistantMessage = messages.lastOrNull().takeIf { it?.source == ChatMessage.Source.Char }
        if (latestAssistantMessage == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.no_latest_assistant_reply_to_regenerate).tryEmit()
            return
        }
        if (messages.size == 1) {
            AppViewEvent.PopupToastMessageByResId(R.string.cannot_regenerate_only_first_message).tryEmit()
            return
        }
        mGenerationJob = viewModelScope.launch {
            runCatching {
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = uiState.conversationState.inputDraft,
                    page = ChatPage.Conversation,
                    isExpanded = uiState.lorebookState.isExpanded,
                    generationState = ChatGenerationState.Requesting,
                    expandedThinkBlockIds = uiState.conversationState.expandedThinkBlockIds
                )
                val built = withContext(Dispatchers.IO) {
                    buildGenerationRequest(
                        sessionId = sessionId,
                        generationMode = PromptGenerationMode.Regenerate,
                        excludedMessageId = latestAssistantMessage.id
                    )
                }
                recordPromptInspection(built.inspection)
                if (AppModel.streamEnabled) {
                    generateStreaming(
                        sessionId,
                        built.request,
                        GenerationOutput.Update(latestAssistantMessage.id),
                        built.worldInfoStateJson
                    )
                } else {
                    generateOnce(
                        sessionId,
                        built.request,
                        GenerationOutput.Update(latestAssistantMessage.id),
                        built.worldInfoStateJson
                    )
                }
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                val message = throwable.toGenerationFailureMessage(
                    mContext,
                    R.string.regenerate_failed
                ) ?: return@onFailure
                AppViewEvent.PopupToastMessage(message).tryEmit()
                refreshUiState(
                    sessionId = sessionId,
                    generationState = ChatGenerationState.Failed(message)
                )
            }
        }
    }

    /**
     * 继续最后一轮对话。
     *
     * 最后一条为用户消息时退化为普通角色回复；最后一条为角色消息时使用 Continue
     * 任务提示，并新建消息保存续写结果，避免覆盖已存在的历史正文。
     */
    private suspend fun continueLastAssistantMessage(sessionId: Long) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Conversation).setup()
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        val latestMessage = withContext(Dispatchers.IO) {
            mChatRepository.getMessagesBySessionId(sessionId).lastOrNull()
        }
        if (latestMessage == null || (latestMessage.source != ChatMessage.Source.User && latestMessage.source != ChatMessage.Source.Char)) {
            AppViewEvent.PopupToastMessageByResId(R.string.no_latest_assistant_reply_to_continue).tryEmit()
            return
        }
        val isLastUser = latestMessage.source == ChatMessage.Source.User
        mGenerationJob = viewModelScope.launch {
            runCatching {
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = uiState.conversationState.inputDraft,
                    isExpanded = uiState.lorebookState.isExpanded,
                    generationState = ChatGenerationState.Requesting,
                    expandedThinkBlockIds = uiState.conversationState.expandedThinkBlockIds
                )
                val generationMode = if (isLastUser) PromptGenerationMode.Normal else PromptGenerationMode.Continue
                val built = withContext(Dispatchers.IO) {
                    buildGenerationRequest(sessionId, generationMode)
                }
                recordPromptInspection(built.inspection)
                if (AppModel.streamEnabled) {
                    generateStreaming(
                        sessionId,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.Char),
                        built.worldInfoStateJson
                    )
                } else {
                    generateOnce(
                        sessionId,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.Char),
                        built.worldInfoStateJson
                    )
                }
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                val errorResId = if (isLastUser) R.string.generation_failed else R.string.continue_generation_failed
                val message = throwable.toGenerationFailureMessage(
                    mContext,
                    errorResId
                ) ?: return@onFailure
                AppViewEvent.PopupToastMessage(message).tryEmit()
                refreshUiState(
                    sessionId = sessionId,
                    generationState = ChatGenerationState.Failed(message)
                )
            }
        }
    }

    /** 让模型生成用户口吻的下一条消息，并以 User 来源提交，不能混入角色回复历史。 */
    private suspend fun generateUserImpersonation(sessionId: Long) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Conversation).setup()
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        mGenerationJob = viewModelScope.launch {
            runCatching {
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = uiState.conversationState.inputDraft,
                    page = ChatPage.Conversation,
                    isExpanded = uiState.lorebookState.isExpanded,
                    generationState = ChatGenerationState.Requesting,
                    expandedThinkBlockIds = uiState.conversationState.expandedThinkBlockIds
                )
                val built = withContext(Dispatchers.IO) {
                    buildGenerationRequest(sessionId, PromptGenerationMode.Impersonate)
                }
                recordPromptInspection(built.inspection)
                if (AppModel.streamEnabled) {
                    generateStreaming(
                        sessionId,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.User),
                        built.worldInfoStateJson
                    )
                } else {
                    generateOnce(
                        sessionId,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.User),
                        built.worldInfoStateJson
                    )
                }
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                val message = throwable.toGenerationFailureMessage(
                    mContext,
                    R.string.impersonation_failed
                ) ?: return@onFailure
                AppViewEvent.PopupToastMessage(message).tryEmit()
                refreshUiState(
                    sessionId = sessionId,
                    generationState = ChatGenerationState.Failed(message)
                )
            }
        }
    }

    /** 非流式结果完成 Source Regex 后，与世界书时序状态一起原子提交。 */
    private suspend fun generateOnce(
        sessionId: Long,
        request: LLMGenerationRequest,
        output: GenerationOutput,
        worldInfoStateJson: String
    ) {
        val response = withContext(Dispatchers.IO) {
            mLLMRepository.generateWithSelectedProvider(request)
        }
        val processedContent = withContext(Dispatchers.IO) {
            applyGeneratedRegex(sessionId, response.content, output)
        }
        if (processedContent.isBlank()) {
            refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
            return
        }
        withContext(Dispatchers.IO) {
            mChatRepository.commitGenerationResult(
                sessionId = sessionId,
                messageId = (output as? GenerationOutput.Update)?.messageId,
                source = output.source(),
                content = processedContent,
                deleteEmptyPlaceholder = false,
                worldInfoStateJson = worldInfoStateJson
            )
        }
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    /**
     * 收集流式增量并保证取消、异常或正常结束时只提交一次最终快照。
     *
     * UI 使用 Markdown Regex 的临时结果，但持久化时只对原始完整响应执行一次 Source Regex；
     * 收尾运行在 NonCancellable 中，因此用户停止生成后仍会保留已收到的 partial 内容，并清理
     * 尚未收到内容的占位消息。
     */
    private suspend fun generateStreaming(
        sessionId: Long,
        request: LLMGenerationRequest,
        output: GenerationOutput,
        worldInfoStateJson: String
    ) {
        val regexContext = withContext(Dispatchers.IO) {
            val session = mChatRepository.getSessionById(sessionId)
            val character = session?.let {
                mCharacterRepository.getCharacterById(it.characterId)
            }
            if (session != null && character != null) {
                StreamingRegexContext(
                    scripts = mRegexRepository.activeScripts(listOf(character)),
                    macros = RegexScriptRuntime.macros(
                        session.userName,
                        character.name,
                        session.userDescription,
                        character.scenario
                    )
                )
            } else {
                StreamingRegexContext()
            }
        }
        val token = Any()
        var active = ActiveStreamingGeneration(
            token = token,
            sessionId = sessionId,
            output = output,
            messageId = (output as? GenerationOutput.Update)?.messageId,
            createdPlaceholder = false,
            content = "",
            regexScripts = regexContext.scripts,
            regexMacros = regexContext.macros,
            worldInfoStateJson = worldInfoStateJson
        )
        mActiveStreamingGeneration = active
        try {
            if (output is GenerationOutput.Create) {
                // 占位消息不推进 latestTime；首个 delta 前取消时可以无痕删除。
                val placeholderId = withContext(NonCancellable + Dispatchers.IO) {
                    mChatRepository.createGenerationPlaceholder(sessionId, output.source)
                }
                active = active.copy(
                    messageId = placeholderId,
                    createdPlaceholder = true
                )
                mActiveStreamingGeneration = active
            }
            refreshUiState(
                sessionId = sessionId,
                generationState = ChatGenerationState.Streaming(active.messageId, active.content)
            )
            mLLMRepository.streamGenerateWithSelectedProvider(request).collect { event ->
                currentCoroutineContext().ensureActive()
                when (event) {
                    is LLMStreamEvent.Delta -> {
                        active = active.copy(content = active.content + event.content)
                        mActiveStreamingGeneration = active
                        val displayContent = applyStreamingDisplayRegex(active)
                        val uiState = getOrNull<ChatUiState.Normal>() ?: return@collect
                        uiState.copy(
                            conversationState = uiState.conversationState.copy(
                                generationState = ChatGenerationState.Streaming(
                                    active.messageId,
                                    active.content
                                ),
                                messages = uiState.conversationState.messages.replaceStreamingMessage(
                                    active.messageId,
                                    displayContent
                                )
                            )
                        ).setup()
                    }
                    is LLMStreamEvent.Finished -> Unit
                }
            }
        } finally {
            val snapshot = active
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    val finalContent = snapshot.content.takeIf { it.isNotBlank() }
                        ?.let { applyStreamingGeneratedRegex(snapshot) }
                        .orEmpty()
                    mChatRepository.commitGenerationResult(
                        sessionId = snapshot.sessionId,
                        messageId = snapshot.messageId,
                        source = snapshot.output.source(),
                        content = finalContent,
                        deleteEmptyPlaceholder = snapshot.createdPlaceholder,
                        worldInfoStateJson = snapshot.worldInfoStateJson
                    )
                }
            } finally {
                if (mActiveStreamingGeneration?.token === token) {
                    mActiveStreamingGeneration = null
                }
            }
        }
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    /** 串行替换当前摘要任务，并确保取消后关闭仍停留在“总结中”的对话框状态。 */
    private fun launchSummaryJob(sessionId: Long, showToast: Boolean): Job {
        mSummaryJob?.cancel()
        val job = viewModelScope.launch {
            try {
                summarizeSession(sessionId, showToast)
            } finally {
                withContext(NonCancellable) {
                    val currentState = getOrNull<ChatUiState.Normal>()
                    if (currentState != null && currentState.dialogState is ChatDialogState.Summarizing) {
                        refreshUiState(
                            sessionId = sessionId,
                            inputDraft = currentState.conversationState.inputDraft,
                            isExpanded = currentState.lorebookState.isExpanded,
                            expandedThinkBlockIds = currentState.conversationState.expandedThinkBlockIds,
                            dialogState = ChatDialogState.None
                        )
                    }
                }
            }
        }
        mSummaryJob = job
        return job
    }

    /** 仅在会话未暂停且未总结消息达到阈值时触发自动摘要，并等待其写入完成。 */
    private suspend fun maybeAutoSummarize(sessionId: Long) {
        if (!AppModel.autoSummaryEnabled) return
        val shouldSummarize = withContext(Dispatchers.IO) {
            val session = mChatRepository.getSessionById(sessionId)
            if (session?.autoSummaryPaused != false) return@withContext false
            val messages = mChatRepository.getMessagesAfterLatestSummary(sessionId)
            messages.isNotEmpty() && messages.size >= AppModel.summaryTriggerMessageCount
        }
        if (shouldSummarize) {
            val job = launchSummaryJob(sessionId, showToast = false)
            job.join()
        }
    }

    /**
     * 生成增量摘要，并以构建器实际选中的最后一条消息作为覆盖边界。
     *
     * 手动刷新允许重写最新摘要，自动任务只处理新增长度达到阈值的历史；提交前再次检查
     * 协程状态，避免取消后的旧响应覆盖用户随后生成的新摘要。
     */
    private suspend fun summarizeSession(sessionId: Long, showToast: Boolean) {
        runCatching {
            val data = withContext(Dispatchers.IO) {
                val session = mChatRepository.getSessionById(sessionId) ?: return@withContext null
                val character = mCharacterRepository.getCharacterById(session.characterId) ?: return@withContext null
                val summaryContext = mChatRepository.getSummaryGenerationContext(
                    sessionId = sessionId,
                    allowRefreshLatest = showToast
                )
                val provider = mLLMRepository.getSelectedProvider()
                AutoSummaryData(
                    session = session,
                    character = character,
                    summary = summaryContext.existingSummary,
                    messages = summaryContext.messages,
                    summaryIdToUpdate = summaryContext.summaryToUpdate?.id,
                    provider = provider
                )
            } ?: return
            if (data.messages.isEmpty()) {
                if (showToast) AppViewEvent.PopupToastMessageByResId(R.string.no_unsummarized_messages).tryEmit()
                return
            }
            if (!showToast && data.messages.size < AppModel.summaryTriggerMessageCount) return

            val uiState = getOrNull<ChatUiState.Normal>() ?: return
            uiState.copy(dialogState = ChatDialogState.Summarizing).setup()

            val built = mSummaryPromptBuilder.buildWithSelection(
                userName = data.session.userName,
                userDescription = data.session.userDescription,
                character = data.character,
                session = data.session,
                existingSummary = data.summary,
                messages = data.messages,
                provider = data.provider
            )
            if (built.selectedMessages.isEmpty()) return

            currentCoroutineContext().ensureActive()

            val response = withContext(Dispatchers.IO) {
                mLLMRepository.generateWithSelectedProvider(built.request)
            }
            val summaryContent = response.content.summarySafeContent()
            if (summaryContent.isBlank()) {
                error(mContext.getString(R.string.summary_failed))
            }

            currentCoroutineContext().ensureActive()

            withContext(Dispatchers.IO) {
                mChatRepository.saveSummary(
                    sessionId = sessionId,
                    content = summaryContent,
                    coveredMessageId = built.selectedMessages.last().id,
                    summaryIdToUpdate = data.summaryIdToUpdate
                )
            }
            if (showToast) AppViewEvent.PopupToastMessageByResId(R.string.summary_updated).tryEmit()
        }.onFailure { throwable ->
            val message = throwable.toGenerationFailureMessage(
                mContext,
                R.string.summary_failed
            ) ?: throw throwable
            AppViewEvent.PopupToastMessage(message).tryEmit()
        }
    }

    /**
     * 收集单聊 Prompt 所需领域快照并交给 ChatPromptBuilder 最终化。
     *
     * 重新生成的目标消息若恰好也是最新摘要覆盖边界，需要回退一版摘要重新取历史，
     * 否则请求会继续包含由待替换消息生成的摘要内容。
     */
    private suspend fun buildGenerationRequest(
        sessionId: Long,
        generationMode: PromptGenerationMode = PromptGenerationMode.Normal,
        excludedMessageId: Long? = null
    ): BuiltGenerationRequest {
        val session = mChatRepository.getSessionById(sessionId) ?: error(mContext.getString(R.string.session_not_found))
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: error(mContext.getString(R.string.character_not_found))
        val summaryContext = mChatRepository.getSummaryContext(sessionId)
        val generationHistory = if (
            excludedMessageId != null &&
            summaryContext.summary?.coveredMessageId == excludedMessageId &&
            summaryContext.messagesAfterSummary.isEmpty()
        ) {
            val regenerationContext = mChatRepository.getSummaryGenerationContext(
                sessionId = sessionId,
                allowRefreshLatest = true
            )
            GenerationHistory(
                summary = regenerationContext.existingSummary,
                messages = regenerationContext.messages.filterNot { it.id == excludedMessageId },
                totalMessageCount = (summaryContext.totalMessageCount - 1).coerceAtLeast(0)
            )
        } else {
            GenerationHistory(
                summary = summaryContext.summary?.content.orEmpty(),
                messages = summaryContext.messagesAfterSummary.filterNot { it.id == excludedMessageId },
                totalMessageCount = (
                    summaryContext.totalMessageCount - if (excludedMessageId == null) 0 else 1
                ).coerceAtLeast(0)
            )
        }
        val enabledIds = mChatRepository.getSessionLorebookEntryIds(session).toSet()
        val lorebookData = getAllLorebookEntries()
        val allLorebookEntries = lorebookData.entries
        val lorebookEntries = allLorebookEntries.filter { it.id in enabledIds }
        val activeLorebookIds = lorebookEntries.map { it.lorebookId }.toSet()
        val activeLorebooks = lorebookData.lorebooks
            .filterKeys { it in activeLorebookIds }
        val recursiveLorebookIds = activeLorebooks.values
            .filter { it.recursiveScanning }
            .map { it.id }
            .toSet()
        val provider = mLLMRepository.getSelectedProvider() ?: error(mContext.getString(R.string.no_enabled_llm_provider_configured))
        val buildResult = mChatPromptBuilder.buildWithMetadata(
            PromptBuildContext(
                userName = session.userName,
                userDescription = session.userDescription,
                character = character,
                session = session.copy(creatorNotes = mChatRepository.getSessionCreatorNotes(session)),
                summary = generationHistory.summary,
                messages = generationHistory.messages,
                currentUserMessage = null,
                totalMessageCount = generationHistory.totalMessageCount,
                candidateLorebookEntries = lorebookEntries,
                candidateLorebooks = activeLorebooks,
                recursiveScanningLorebookIds = recursiveLorebookIds,
                provider = provider,
                maxContextTokens = provider.contextTokens,
                maxResponseTokens = provider.maxTokens,
                generationMode = generationMode,
                regexScripts = mRegexRepository.activeScripts(listOf(character))
            )
        )
        return BuiltGenerationRequest(
            request = buildResult.request,
            inspection = buildResult.inspection,
            worldInfoStateJson = buildResult.worldInfoStateJson
        )
    }

    private fun recordPromptInspection(inspection: PromptInspection) {
        mLastPromptInspection = inspection
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(hasPromptInspection = true).setup()
        val hasWorldInfoOverflow = inspection.omittedItems.any {
            it.reason == PromptOmissionReason.WorldInfoBudget
        }
        val hasContextTrimming = inspection.omittedItems.any {
            it.reason == PromptOmissionReason.ContextBudget
        }
        when {
            AppModel.worldInfoOverflowAlert && hasWorldInfoOverflow -> {
                AppViewEvent.PopupToastMessageByResId(
                    R.string.world_info_budget_overflow_warning
                ).tryEmit()
            }
            AppModel.contextTrimmingAlert && hasContextTrimming -> {
                AppViewEvent.PopupToastMessageByResId(R.string.prompt_trimmed_warning).tryEmit()
            }
        }
    }

    /**
     * 从持久化领域数据重建完整页面状态。
     *
     * Markdown Regex 只作用于本次展示副本，Room 中的 Source 正文保持不变；敏感且体积较大的
     * Prompt 检查详情继续保存在 ViewModel 私有快照中，UiState 只暴露是否可查看。
     */
    private suspend fun loadNormalState(
        sessionId: Long,
        inputDraft: String = "",
        page: ChatPage = ChatPage.Conversation,
        isExpanded: Boolean = false,
        lorebookQuery: String = "",
        loadState: ChatLoadState = ChatLoadState.None,
        generationState: ChatGenerationState = ChatGenerationState.Idle,
        expandedThinkBlockIds: Set<String> = emptySet(),
        editingMessageId: String? = null,
        editingMessageDraft: String = "",
        dialogState: ChatDialogState = ChatDialogState.None
    ): ChatUiState.Normal? {
        val session = mChatRepository.getSessionById(sessionId) ?: return null
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: return null
        val messages = mChatRepository.getMessagesBySessionId(sessionId)
        val regexScripts = mRegexRepository.activeScripts(listOf(character))
        val regexMacros = RegexScriptRuntime.macros(
            userName = session.userName,
            characterName = character.name,
            userDescription = session.userDescription,
            scenario = character.scenario
        )
        val displayMessages = messages.mapIndexed { index, message ->
            val depth = messages.lastIndex - index
            val result = when (message.source) {
                ChatMessage.Source.User -> mRegexRuntime.executeDisplayMessage(
                    message.content,
                    regexScripts,
                    regexMacros,
                    depth,
                    RegexPlacement.UserInput
                )
                ChatMessage.Source.Char -> mRegexRuntime.executeDisplayMessage(
                    message.content,
                    regexScripts,
                    regexMacros,
                    depth
                )
                ChatMessage.Source.System,
                ChatMessage.Source.Summary -> null
            }
            if (result == null) message else message.copy(content = result.text)
        }
        val summary = mChatRepository.getLatestSummary(sessionId)?.content.orEmpty()
        val lorebookData = getAllLorebookEntries()
        val enabledIds = mChatRepository.getSessionLorebookEntryIds(session).toSet()
        val effectiveCreatorNotes = mChatRepository.getSessionCreatorNotes(session)
        val avatarImage = character.avatar.takeIf { it.isNotBlank() }?.let {
            mFileRepository.loadBitmap(it)?.asImageBitmap()
        }
        return ChatUiState.Normal(
            page = page,
            loadState = loadState,
            session = session.toChatSessionItem(
                summary = summary,
                creatorNotes = effectiveCreatorNotes,
                messageCount = messages.size,
                enabledIds = enabledIds
            ),
            character = character.toChatCharacterItem(avatarImage),
            conversationState = ChatConversationState(
                messages = displayMessages.toChatMessageItems(
                    characterName = character.name,
                    userName = session.userName,
                    systemSpeaker = mContext.getString(R.string.system_speaker),
                    streamingMessageId = mActiveStreamingGeneration?.messageId
                ),
                inputDraft = inputDraft,
                generationState = generationState,
                expandedThinkBlockIds = expandedThinkBlockIds,
                editingMessageId = editingMessageId,
                editingMessageDraft = editingMessageDraft
            ),
            lorebookState = lorebookData.toChatLorebookGroupItems(
                    enabledIds = enabledIds,
                    unknownLorebookName = mContext.getString(R.string.unknown_lorebook)
                ).let { groups ->
                    ChatLorebookState(
                        groups = groups,
                        visibleGroups = groups.filterForQuery(lorebookQuery),
                        query = lorebookQuery,
                        isExpanded = isExpanded
                    )
                },
            streamEnabled = AppModel.streamEnabled,
            hasPromptInspection = mLastPromptInspection != null,
            dialogState = dialogState
        )
    }

    private suspend fun refreshUiState(
        sessionId: Long,
        inputDraft: String = getOrNull<ChatUiState.Normal>()
            ?.conversationState?.inputDraft.orEmpty(),
        page: ChatPage = getOrNull<ChatUiState.Normal>()?.page ?: ChatPage.Conversation,
        isExpanded: Boolean = getOrNull<ChatUiState.Normal>()?.lorebookState?.isExpanded ?: false,
        lorebookQuery: String = getOrNull<ChatUiState.Normal>()?.lorebookState?.query.orEmpty(),
        loadState: ChatLoadState = ChatLoadState.None,
        generationState: ChatGenerationState = getOrNull<ChatUiState.Normal>()
            ?.conversationState?.generationState ?: ChatGenerationState.Idle,
        expandedThinkBlockIds: Set<String> = getOrNull<ChatUiState.Normal>()
            ?.conversationState?.expandedThinkBlockIds ?: emptySet(),
        editingMessageId: String? = getOrNull<ChatUiState.Normal>()
            ?.conversationState?.editingMessageId,
        editingMessageDraft: String = getOrNull<ChatUiState.Normal>()
            ?.conversationState?.editingMessageDraft.orEmpty(),
        dialogState: ChatDialogState = getOrNull<ChatUiState.Normal>()?.dialogState ?: ChatDialogState.None
    ) {
        val nextState = withContext(Dispatchers.IO) {
            loadNormalState(
                sessionId = sessionId,
                inputDraft = inputDraft,
                page = page,
                isExpanded = isExpanded,
                lorebookQuery = lorebookQuery,
                loadState = loadState,
                generationState = generationState,
                expandedThinkBlockIds = expandedThinkBlockIds,
                editingMessageId = editingMessageId,
                editingMessageDraft = editingMessageDraft,
                dialogState = dialogState
            )
        } ?: return
        nextState.setup()
    }

    private suspend fun saveSessionLorebookEntryIds(
        sessionId: Long,
        enabledIds: Set<Long>
    ) {
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionLorebookEntryIds(sessionId, enabledIds.toList())
        }
    }

    private suspend fun getAllLorebookEntries(): ChatLorebookEntryData {
        val lorebooks = mLorebookRepository.getAllLorebooks()
        val entries = lorebooks.flatMap { mLorebookRepository.getEntriesByLorebookId(it.id) }
        return ChatLorebookEntryData(lorebooks.associateBy { it.id }, entries)
    }

    private fun finishWithToast(messageResId: Int) {
        AppViewEvent.PopupToastMessageByResId(messageResId).tryEmit()
        ChatUiState.finished(uiStateFlow.value).setup()
    }

    private data class BuiltGenerationRequest(
        val request: LLMGenerationRequest,
        val inspection: PromptInspection,
        val worldInfoStateJson: String
    )

    /**
     * 在用户正文持久化前执行 Source Regex。
     *
     * 以 `/` 开头的输入先经过 SlashCommand placement，再进入 UserInput placement；
     * 编辑已有消息时透传 [isEdit]，让脚本的 runOnEdit 约束生效。
     */
    private suspend fun applyUserRegex(
        sessionId: Long,
        input: String,
        isEdit: Boolean = false
    ): String {
        val session = mChatRepository.getSessionById(sessionId) ?: return input
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: return input
        val scripts = mRegexRepository.activeScripts(listOf(character))
        val macros = RegexScriptRuntime.macros(
            session.userName,
            character.name,
            session.userDescription,
            character.scenario
        )
        val slashProcessed = if (input.startsWith('/')) {
            mRegexRuntime.execute(
                input,
                scripts,
                RegexPlacement.SlashCommand,
                RegexExecutionMode.Source,
                macros,
                isEdit = isEdit
            ).text
        } else {
            input
        }
        return mRegexRuntime.execute(
            slashProcessed,
            scripts,
            RegexPlacement.UserInput,
            RegexExecutionMode.Source,
            macros,
            isEdit = isEdit
        ).text
    }

    private suspend fun applyAiRegex(
        sessionId: Long,
        input: String,
        isEdit: Boolean = false
    ): String {
        val session = mChatRepository.getSessionById(sessionId) ?: return input
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: return input
        return mRegexRuntime.executeAiMessage(
            input = input,
            scripts = mRegexRepository.activeScripts(listOf(character)),
            mode = RegexExecutionMode.Source,
            macros = RegexScriptRuntime.macros(
                session.userName,
                character.name,
                session.userDescription,
                character.scenario
            ),
            isEdit = isEdit
        ).text
    }

    private suspend fun applyGeneratedRegex(
        sessionId: Long,
        input: String,
        output: GenerationOutput?
    ): String {
        return if (output is GenerationOutput.Create && output.source == ChatMessage.Source.User) {
            applyUserRegex(sessionId, input)
        } else {
            applyAiRegex(sessionId, input)
        }
    }

    /**
     * 使用生成启动时冻结的脚本和宏，对完整 partial 快照执行最终 Source Regex。
     *
     * 收尾可能运行在取消后的 NonCancellable 区域，因此不再查询数据库，避免用户在生成途中
     * 修改脚本导致屏幕上看到的内容与最终持久化规则来自不同脚本版本。
     */
    private fun applyStreamingGeneratedRegex(snapshot: ActiveStreamingGeneration): String {
        if (snapshot.output.source() != ChatMessage.Source.User) {
            return mRegexRuntime.executeAiMessage(
                input = snapshot.content,
                scripts = snapshot.regexScripts,
                mode = RegexExecutionMode.Source,
                macros = snapshot.regexMacros
            ).text
        }
        val slashProcessed = if (snapshot.content.startsWith('/')) {
            mRegexRuntime.execute(
                input = snapshot.content,
                scripts = snapshot.regexScripts,
                placement = RegexPlacement.SlashCommand,
                mode = RegexExecutionMode.Source,
                macros = snapshot.regexMacros
            ).text
        } else {
            snapshot.content
        }
        return mRegexRuntime.execute(
            input = slashProcessed,
            scripts = snapshot.regexScripts,
            placement = RegexPlacement.UserInput,
            mode = RegexExecutionMode.Source,
            macros = snapshot.regexMacros
        ).text
    }

    private fun applyStreamingDisplayRegex(snapshot: ActiveStreamingGeneration): String {
        return if (snapshot.output.source() == ChatMessage.Source.User) {
            mRegexRuntime.executeDisplayMessage(
                snapshot.content,
                snapshot.regexScripts,
                snapshot.regexMacros,
                bodyPlacement = RegexPlacement.UserInput
            ).text
        } else {
            mRegexRuntime.executeDisplayMessage(
                snapshot.content,
                snapshot.regexScripts,
                snapshot.regexMacros
            ).text
        }
    }

    private fun List<ChatLorebookGroupItem>.filterForQuery(
        query: String
    ): List<ChatLorebookGroupItem> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return this
        return mapNotNull { group ->
            val groupMatches = group.lorebookName.contains(normalizedQuery, ignoreCase = true)
            val matchingEntries = group.entries.filter { entry ->
                entry.lorebookName.contains(normalizedQuery, ignoreCase = true) ||
                    entry.name.contains(normalizedQuery, ignoreCase = true) ||
                    entry.content.contains(normalizedQuery, ignoreCase = true) ||
                    entry.keywords.any { it.contains(normalizedQuery, ignoreCase = true) } ||
                    entry.secondaryKeywords.any {
                        it.contains(normalizedQuery, ignoreCase = true)
                    }
            }
            when {
                groupMatches -> group
                matchingEntries.isNotEmpty() -> group.copy(entries = matchingEntries)
                else -> null
            }
        }
    }

    private data class AutoSummaryData(
        val session: ChatSession,
        val character: Character,
        val summary: String,
        val messages: List<ChatMessage>,
        val summaryIdToUpdate: Long?,
        val provider: LLMProvider?
    )

    private data class GenerationHistory(
        val summary: String,
        val messages: List<ChatMessage>,
        val totalMessageCount: Int
    )

    private sealed class GenerationOutput {
        data class Create(val source: ChatMessage.Source) : GenerationOutput()
        data class Update(val messageId: Long) : GenerationOutput()
    }

    private fun GenerationOutput.source(): ChatMessage.Source {
        return when (this) {
            is GenerationOutput.Create -> source
            is GenerationOutput.Update -> ChatMessage.Source.Char
        }
    }

    private data class StreamingRegexContext(
        val scripts: List<ScopedRegexScript> = emptyList(),
        val macros: Map<String, String> = emptyMap()
    )

    private data class ActiveStreamingGeneration(
        val token: Any,
        val sessionId: Long,
        val output: GenerationOutput,
        val messageId: Long?,
        val createdPlaceholder: Boolean,
        val content: String,
        val regexScripts: List<ScopedRegexScript>,
        val regexMacros: Map<String, String>,
        val worldInfoStateJson: String
    )
}
