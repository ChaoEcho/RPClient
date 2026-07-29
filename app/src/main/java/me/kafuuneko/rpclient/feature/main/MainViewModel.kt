package me.kafuuneko.rpclient.feature.main

import android.content.Context
import android.os.Bundle
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.about.AboutActivity
import me.kafuuneko.rpclient.feature.characterlist.CharacterListActivity
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.chatcreate.ChatCreateActivity
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.groupchatcreate.GroupChatCreateActivity
import me.kafuuneko.rpclient.feature.llmproviderlist.LLMProviderListActivity
import me.kafuuneko.rpclient.feature.llmprovideredit.LLMProviderEditActivity
import me.kafuuneko.rpclient.feature.main.presentation.MainDialogState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeState
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionGroup
import me.kafuuneko.rpclient.feature.main.model.MainGroupChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainGenerationParameter
import me.kafuuneko.rpclient.feature.main.model.MainImportCharacterItem
import me.kafuuneko.rpclient.feature.main.model.MainProviderItem
import me.kafuuneko.rpclient.feature.main.model.MainSessionType
import me.kafuuneko.rpclient.feature.main.presentation.MainPage
import me.kafuuneko.rpclient.feature.main.presentation.MainSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.main.presentation.MainViewEvent
import me.kafuuneko.rpclient.feature.main.presentation.mergeResumeRefresh
import me.kafuuneko.rpclient.feature.promptpreset.PromptPresetActivity
import me.kafuuneko.rpclient.feature.requestlog.RequestLogActivity
import me.kafuuneko.rpclient.feature.regexscript.RegexScriptActivity
import me.kafuuneko.rpclient.feature.worldbooklist.WorldBookListActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.chat.ChatArchive
import me.kafuuneko.rpclient.libs.chat.ChatArchiveRepository
import me.kafuuneko.rpclient.libs.chat.ChatCharacterMatcher
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.prompt.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionRole
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.utils.formatTimestamp
import me.kafuuneko.rpclient.libs.utils.stripThinkBlocks
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 主页面状态持有者。
 *
 * 聚合最近会话、角色与群聊入口，并将全局设置和当前模型配置映射为可编辑状态。
 */
class MainViewModel : CoreViewModelWithEvent<MainUiIntent, MainUiState>(
    MainUiState.None
), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mChatRepository by inject<ChatRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mGroupChatRepository by inject<GroupChatRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mChatArchiveRepository by inject<ChatArchiveRepository>()
    private val mContext by inject<Context>()

    /** 文件解析结果只在用户确认角色前暂存，不进入可持久状态或数据库。 */
    private var mPendingChatImport: ChatArchive? = null
    /** 导入文件读取与最终事务共用单任务守卫，阻止重复选择或重复提交。 */
    private var mChatImportJob: Job? = null

    @UiIntentObserver(MainUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<MainUiState.None>()) return
        val providers = mLLMRepository.getEnabledProviders()
        val currentId = AppModel.currentLLMProvider
        val selectedProvider = providers.firstOrNull { it.id == currentId } ?: providers.firstOrNull()
        MainUiState.Normal(
            homeState = buildHomeState(),
            settingsState = buildSettingsState(providers, selectedProvider)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val providers = mLLMRepository.getEnabledProviders()
        val currentId = AppModel.currentLLMProvider
        val selectedProvider = providers.firstOrNull { it.id == currentId } ?: providers.firstOrNull()
        val homeState = buildHomeState()
        val settingsState = uiState.settingsState.copy(
            selectedProviderId = selectedProvider?.id?.toString().orEmpty(),
            providers = providers.map { it.toMainProviderItem() },
            userName = AppModel.userName,
            hasUserAvatar = AppModel.userAvatar.isNotBlank(),
            userAvatarImage = resolveUserAvatarImage(),
            userDescription = AppModel.userDescription,
            temperature = selectedProvider?.temperature ?: 0f,
            topP = selectedProvider?.topP ?: 0f,
            maxTokens = selectedProvider?.maxTokens ?: 0,
            contextTokens = selectedProvider?.contextTokens ?: 0,
            streamEnabled = AppModel.streamEnabled,
            promptPostProcessingMode = selectedProvider?.postProcessingMode()
                ?: PromptPostProcessingMode.None,
            exampleDialogueBehavior = readExampleDialogueBehavior(),
            includeThinkInContext = AppModel.includeThinkInContext,
            worldInfoBudgetPercent = AppModel.worldInfoBudgetPercent.coerceIn(0, 100),
            worldInfoBudgetCap = AppModel.worldInfoBudgetCap.coerceAtLeast(0),
            worldInfoOverflowAlert = AppModel.worldInfoOverflowAlert,
            contextTrimmingAlert = AppModel.contextTrimmingAlert,
            debugModeEnabled = AppModel.debugModeEnabled,
            autoSummaryEnabled = AppModel.autoSummaryEnabled,
            summaryTriggerMessageCount = AppModel.summaryTriggerMessageCount,
            summaryWordsLimit = AppModel.summaryWordsLimit,
            summaryMaxMessagesPerRequest = AppModel.summaryMaxMessagesPerRequest,
            summaryResponseTokens = AppModel.summaryResponseTokens,
            summaryInjectionPosition = readSummaryInjectionPosition(),
            summaryInjectionDepth = AppModel.summaryInjectionDepth,
            summaryInjectionRole = readSummaryInjectionRole()
        )
        val current = getOrNull<MainUiState.Normal>() ?: return
        current.mergeResumeRefresh(
            homeState = homeState,
            settingsState = settingsState
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.homeState.multiSelectMode) {
            uiState.copy(
                homeState = uiState.homeState.copy(
                    multiSelectMode = false,
                    selectedSessions = emptySet()
                )
            ).setup()
            return
        }
        if (uiState.selectedPage != MainPage.Home) {
            uiState.copy(selectedPage = MainPage.Home).setup()
            return
        }
        MainUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(MainUiIntent.EnterMultiSelect::class)
    private fun onEnterMultiSelect(intent: MainUiIntent.EnterMultiSelect) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.homeState.multiSelectMode) return
        uiState.copy(
            homeState = uiState.homeState.copy(
                multiSelectMode = true,
                selectedSessions = setOf(intent.session)
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleSessionSelection::class)
    private fun onToggleSessionSelection(intent: MainUiIntent.ToggleSessionSelection) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (!uiState.homeState.multiSelectMode) return
        val current = uiState.homeState.selectedSessions
        val updated = if (intent.session in current) {
            current - intent.session
        } else {
            current + intent.session
        }
        uiState.copy(
            homeState = uiState.homeState.copy(selectedSessions = updated)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ExitMultiSelect::class)
    private fun onExitMultiSelect() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        uiState.copy(
            homeState = uiState.homeState.copy(
                multiSelectMode = false,
                selectedSessions = emptySet()
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ShowDeleteSelectedDialog::class)
    private fun onShowDeleteSelectedDialog() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val count = uiState.homeState.selectedSessions.size
        if (count == 0) return
        uiState.copy(
            dialogState = MainDialogState.DeleteSelectedSessions(count = count)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ConfirmDeleteSelected::class)
    private suspend fun onConfirmDeleteSelected() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.DeleteSelectedSessions ?: return
        val selections = uiState.homeState.selectedSessions
        withContext(Dispatchers.IO) {
            selections.forEach { selection ->
                val sessionId = selection.sessionId.toLongOrNull() ?: return@forEach
                when (selection.type) {
                    MainSessionType.Chat -> mChatRepository.deleteSession(sessionId)
                    MainSessionType.GroupChat -> mGroupChatRepository.deleteSession(sessionId)
                }
            }
        }
        uiState.copy(
            dialogState = MainDialogState.None,
            homeState = buildHomeState()
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val importDialog = uiState.dialogState as? MainDialogState.ImportChatCharacterSelection
        if (importDialog?.isImporting == true) return
        if (importDialog != null) {
            mPendingChatImport = null
        }
        uiState.copy(dialogState = MainDialogState.None).setup()
    }

    @UiIntentObserver(MainUiIntent.ImportChatClick::class)
    private fun onImportChatClick() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.settingsState.isChatImportReading || mChatImportJob?.isActive == true) return
        MainViewEvent.OpenChatImporter.tryEmit()
    }

    @UiIntentObserver(MainUiIntent.ImportChatResult::class)
    private fun onImportChatResult(intent: MainUiIntent.ImportChatResult) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.settingsState.isChatImportReading || mChatImportJob?.isActive == true) return
        uiState.copy(
            settingsState = uiState.settingsState.copy(isChatImportReading = true)
        ).setup()
        mChatImportJob = viewModelScope.launch {
            try {
                val archive = mChatArchiveRepository.readImportFromUri(intent.uri)
                val characters = mCharacterRepository.getAllCharacters()
                mPendingChatImport = archive
                val current = getOrNull<MainUiState.Normal>() ?: return@launch
                val items = characters.map { it.toImportCharacterItem() }
                current.copy(
                    settingsState = current.settingsState.copy(isChatImportReading = false),
                    dialogState = MainDialogState.ImportChatCharacterSelection(
                        title = archive.title,
                        sourceCharacterName = archive.characterNameHint,
                        messageCount = archive.messages.size,
                        query = "",
                        characters = items,
                        visibleCharacters = items,
                        selectedCharacterId = ChatCharacterMatcher.suggestCharacterId(
                            archive,
                            characters
                        )
                    )
                ).setup()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mPendingChatImport = null
                AppViewEvent.PopupToastMessageByResId(R.string.import_chat_failed).tryEmit()
                getOrNull<MainUiState.Normal>()?.let { current ->
                    current.copy(
                        settingsState = current.settingsState.copy(isChatImportReading = false)
                    ).setup()
                }
            } finally {
                mChatImportJob = null
            }
        }
    }

    @UiIntentObserver(MainUiIntent.ChangeImportCharacterQuery::class)
    private fun onChangeImportCharacterQuery(
        intent: MainUiIntent.ChangeImportCharacterQuery
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.ImportChatCharacterSelection ?: return
        if (dialog.isImporting) return
        val query = intent.value.trim()
        val visible = if (query.isBlank()) {
            dialog.characters
        } else {
            dialog.characters.filter { item ->
                item.name.contains(query, ignoreCase = true) ||
                    item.details.contains(query, ignoreCase = true)
            }
        }
        uiState.copy(
            dialogState = dialog.copy(
                query = intent.value,
                visibleCharacters = visible
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.SelectImportCharacter::class)
    private fun onSelectImportCharacter(intent: MainUiIntent.SelectImportCharacter) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.ImportChatCharacterSelection ?: return
        if (dialog.isImporting || dialog.characters.none { it.id == intent.characterId }) return
        uiState.copy(
            dialogState = dialog.copy(selectedCharacterId = intent.characterId)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ConfirmImportChat::class)
    private fun onConfirmImportChat() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.ImportChatCharacterSelection ?: return
        val characterId = dialog.selectedCharacterId ?: return
        val archive = mPendingChatImport ?: return
        if (dialog.isImporting || mChatImportJob?.isActive == true) return
        uiState.copy(dialogState = dialog.copy(isImporting = true)).setup()
        mChatImportJob = viewModelScope.launch {
            try {
                val sessionId = mChatArchiveRepository.saveImport(archive, characterId)
                mPendingChatImport = null
                val current = getOrNull<MainUiState.Normal>() ?: return@launch
                current.copy(
                    homeState = buildHomeState(),
                    dialogState = MainDialogState.None
                ).setup()
                AppViewEvent.PopupToastMessageByResId(R.string.import_chat_success).tryEmit()
                AppViewEvent.StartActivity(
                    activity = ChatActivity::class.java,
                    extras = Bundle().apply {
                        putString(ChatActivity.EXTRA_SESSION_ID, sessionId.toString())
                    }
                ).tryEmit()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.import_chat_failed).tryEmit()
                val current = getOrNull<MainUiState.Normal>() ?: return@launch
                val currentDialog = current.dialogState
                    as? MainDialogState.ImportChatCharacterSelection
                    ?: return@launch
                current.copy(
                    dialogState = currentDialog.copy(isImporting = false)
                ).setup()
            } finally {
                mChatImportJob = null
            }
        }
    }

    @UiIntentObserver(MainUiIntent.SelectPage::class)
    private fun onSelectPage(intent: MainUiIntent.SelectPage) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        uiState.copy(selectedPage = intent.page).setup()
    }

    @UiIntentObserver(MainUiIntent.OpenChat::class)
    private fun onOpenChat(intent: MainUiIntent.OpenChat) {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(
            activity = ChatActivity::class.java,
            extras = Bundle().apply { putString(ChatActivity.EXTRA_SESSION_ID, intent.sessionId) }
        ).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenCreateChat::class)
    private fun onOpenCreateChat() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(ChatCreateActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenGroupChat::class)
    private fun onOpenGroupChat(intent: MainUiIntent.OpenGroupChat) {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(
            activity = GroupChatActivity::class.java,
            extras = Bundle().apply {
                putString(GroupChatActivity.EXTRA_SESSION_ID, intent.sessionId)
            }
        ).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenCreateGroupChat::class)
    private fun onOpenCreateGroupChat() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(GroupChatCreateActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenCharacterManager::class)
    private fun onOpenCharacterManager() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(CharacterListActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenWorldBookManager::class)
    private fun onOpenWorldBookManager() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(WorldBookListActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenProviderManager::class)
    private fun onOpenProviderManager() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(LLMProviderListActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenSelectedProviderEdit::class)
    private fun onOpenSelectedProviderEdit() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val providerId = uiState.settingsState.selectedProviderId.toLongOrNull() ?: return
        AppViewEvent.StartActivity(
            activity = LLMProviderEditActivity::class.java,
            extras = Bundle().apply {
                putLong(LLMProviderEditActivity.EXTRA_PROVIDER_ID, providerId)
            }
        ).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.ShowGenerationParameterDialog::class)
    private suspend fun onShowGenerationParameterDialog(
        intent: MainUiIntent.ShowGenerationParameterDialog
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val providerId = uiState.settingsState.selectedProviderId.toLongOrNull() ?: return
        val provider = withContext(Dispatchers.IO) {
            mLLMRepository.getProviderById(providerId)
        } ?: return
        uiState.copy(
            dialogState = MainDialogState.EditGenerationParameter(
                parameter = intent.parameter,
                draftValue = intent.parameter.valueOf(provider)
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeGenerationParameterDraft::class)
    private fun onChangeGenerationParameterDraft(
        intent: MainUiIntent.ChangeGenerationParameterDraft
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.EditGenerationParameter ?: return
        uiState.copy(
            dialogState = dialog.copy(draftValue = intent.value)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ConfirmGenerationParameter::class)
    private suspend fun onConfirmGenerationParameter() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.EditGenerationParameter ?: return
        val providerId = uiState.settingsState.selectedProviderId.toLongOrNull() ?: return
        val provider = withContext(Dispatchers.IO) {
            mLLMRepository.getProviderById(providerId)
        } ?: return
        val updatedProvider = dialog.parameter.updateProviderOrNull(provider, dialog.draftValue)
        if (updatedProvider == null) {
            val messageRes = if (dialog.hasInvalidTokenRelationship(provider)) {
                R.string.max_tokens_must_be_less_than_context
            } else {
                R.string.generation_params_invalid
            }
            AppViewEvent.PopupToastMessageByResId(messageRes).tryEmit()
            return
        }
        withContext(Dispatchers.IO) {
            mLLMRepository.saveProvider(updatedProvider)
        }
        uiState.copy(
            dialogState = MainDialogState.None,
            settingsState = uiState.settingsState.copy(
                temperature = updatedProvider.temperature,
                topP = updatedProvider.topP,
                maxTokens = updatedProvider.maxTokens,
                contextTokens = updatedProvider.contextTokens
            )
        ).setup()
    }

    private fun MainDialogState.EditGenerationParameter.hasInvalidTokenRelationship(
        provider: LLMProvider
    ): Boolean {
        val value = draftValue.toIntOrNull() ?: return false
        return when (parameter) {
            MainGenerationParameter.MaxTokens -> value >= provider.contextTokens
            MainGenerationParameter.ContextTokens -> value <= provider.maxTokens
            MainGenerationParameter.Temperature, MainGenerationParameter.TopP -> false
        }
    }

    @UiIntentObserver(MainUiIntent.PickUserAvatarClick::class)
    private fun onPickUserAvatarClick() {
        if (!isStateOf<MainUiState.Normal>()) return
        MainViewEvent.OpenUserAvatarPicker.tryEmit()
    }

    @UiIntentObserver(MainUiIntent.UserAvatarSelected::class)
    private suspend fun onUserAvatarSelected(intent: MainUiIntent.UserAvatarSelected) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val oldAvatar = AppModel.userAvatar
        val avatarUuid = runCatching {
            withContext(Dispatchers.IO) {
                mFileRepository.saveFile(intent.uri)
            }
        }.getOrElse {
            AppViewEvent.PopupToastMessageByResId(R.string.user_avatar_save_failed).tryEmit()
            return
        }
        AppModel.userAvatar = avatarUuid
        if (oldAvatar.isNotBlank() && oldAvatar != avatarUuid) {
            runCatching {
                withContext(Dispatchers.IO) {
                    mFileRepository.deleteFile(oldAvatar)
                }
            }
        }
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                hasUserAvatar = true,
                userAvatarImage = resolveUserAvatarImage()
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ClearUserAvatar::class)
    private suspend fun onClearUserAvatar() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val oldAvatar = AppModel.userAvatar
        AppModel.userAvatar = ""
        if (oldAvatar.isNotBlank()) {
            runCatching {
                withContext(Dispatchers.IO) {
                    mFileRepository.deleteFile(oldAvatar)
                }
            }
        }
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                hasUserAvatar = false,
                userAvatarImage = null
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.OpenPromptPreset::class)
    private fun onOpenPromptPreset() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(PromptPresetActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenRegexScripts::class)
    private fun onOpenRegexScripts() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(RegexScriptActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenRequestLogs::class)
    private fun onOpenRequestLogs() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(RequestLogActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenAbout::class)
    private fun onOpenAbout() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(AboutActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.ChangeUserName::class)
    private fun onChangeUserName(intent: MainUiIntent.ChangeUserName) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val value = intent.value.trim()
        AppModel.userName = value.ifBlank { "You" }
        uiState.copy(
            settingsState = uiState.settingsState.copy(userName = intent.value)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeUserDescription::class)
    private fun onChangeUserDescription(intent: MainUiIntent.ChangeUserDescription) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.userDescription = intent.value.trim()
        uiState.copy(
            settingsState = uiState.settingsState.copy(userDescription = intent.value)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.SelectProvider::class)
    private suspend fun onSelectProvider(intent: MainUiIntent.SelectProvider) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val providerId = intent.providerId.toLongOrNull() ?: return
        mLLMRepository.updateCurrentProvider(providerId)
        val providers = mLLMRepository.getEnabledProviders()
        val selectedProvider = providers.firstOrNull { it.id == providerId }
        uiState.copy(
            selectedPage = MainPage.Settings,
            settingsState = uiState.settingsState.copy(
                selectedProviderId = intent.providerId,
                providers = providers.map { it.toMainProviderItem() },
                temperature = selectedProvider?.temperature ?: uiState.settingsState.temperature,
                topP = selectedProvider?.topP ?: uiState.settingsState.topP,
                maxTokens = selectedProvider?.maxTokens ?: uiState.settingsState.maxTokens,
                contextTokens = selectedProvider?.contextTokens ?: uiState.settingsState.contextTokens,
                promptPostProcessingMode = selectedProvider?.postProcessingMode()
                    ?: PromptPostProcessingMode.None
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleAutoSummaryEnabled::class)
    private fun onToggleAutoSummaryEnabled(intent: MainUiIntent.ToggleAutoSummaryEnabled) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.autoSummaryEnabled = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(autoSummaryEnabled = intent.enabled)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryTriggerMessageCount::class)
    private fun onChangeSummaryTriggerMessageCount(intent: MainUiIntent.ChangeSummaryTriggerMessageCount) {
        updateSettingsInt(intent.value, minimum = 1) {
            AppModel.summaryTriggerMessageCount = it
            copy(summaryTriggerMessageCount = it)
        }
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryWordsLimit::class)
    private fun onChangeSummaryWordsLimit(intent: MainUiIntent.ChangeSummaryWordsLimit) {
        updateSettingsInt(intent.value, minimum = 50) {
            AppModel.summaryWordsLimit = it
            copy(summaryWordsLimit = it)
        }
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryMaxMessagesPerRequest::class)
    private fun onChangeSummaryMaxMessagesPerRequest(intent: MainUiIntent.ChangeSummaryMaxMessagesPerRequest) {
        updateSettingsInt(intent.value, minimum = 0) {
            AppModel.summaryMaxMessagesPerRequest = it
            copy(summaryMaxMessagesPerRequest = it)
        }
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryResponseTokens::class)
    private fun onChangeSummaryResponseTokens(intent: MainUiIntent.ChangeSummaryResponseTokens) {
        updateSettingsInt(intent.value, minimum = 128) {
            AppModel.summaryResponseTokens = it
            copy(summaryResponseTokens = it)
        }
    }

    @UiIntentObserver(MainUiIntent.SelectSummaryInjectionPosition::class)
    private fun onSelectSummaryInjectionPosition(
        intent: MainUiIntent.SelectSummaryInjectionPosition
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.summaryInjectionPosition = intent.position.persistedValue
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryInjectionPosition = intent.position
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryInjectionDepth::class)
    private fun onChangeSummaryInjectionDepth(intent: MainUiIntent.ChangeSummaryInjectionDepth) {
        updateSettingsInt(intent.value, minimum = 0) {
            AppModel.summaryInjectionDepth = it
            copy(summaryInjectionDepth = it)
        }
    }

    @UiIntentObserver(MainUiIntent.SelectSummaryInjectionRole::class)
    private fun onSelectSummaryInjectionRole(intent: MainUiIntent.SelectSummaryInjectionRole) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.summaryInjectionRole = intent.role.persistedValue
        uiState.copy(
            settingsState = uiState.settingsState.copy(summaryInjectionRole = intent.role)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleStreamEnabled::class)
    private fun onToggleStreamEnabled(intent: MainUiIntent.ToggleStreamEnabled) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.streamEnabled = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(streamEnabled = intent.enabled)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.SelectPostProcessingMode::class)
    private suspend fun onSelectPostProcessingMode(intent: MainUiIntent.SelectPostProcessingMode) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val providerId = uiState.settingsState.selectedProviderId.toLongOrNull() ?: return
        val provider = withContext(Dispatchers.IO) {
            mLLMRepository.getProviderById(providerId)
        } ?: return
        val updatedProvider = provider.copy(promptPostProcessingMode = intent.mode.ordinal)
        withContext(Dispatchers.IO) {
            mLLMRepository.saveProvider(updatedProvider)
        }
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                promptPostProcessingMode = intent.mode
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleIncludeThinkInContext::class)
    private fun onToggleIncludeThinkInContext(intent: MainUiIntent.ToggleIncludeThinkInContext) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.includeThinkInContext = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(includeThinkInContext = intent.enabled)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeWorldInfoBudgetPercent::class)
    private fun onChangeWorldInfoBudgetPercent(intent: MainUiIntent.ChangeWorldInfoBudgetPercent) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val percent = intent.value.coerceIn(0, 100)
        AppModel.worldInfoBudgetPercent = percent
        uiState.copy(
            settingsState = uiState.settingsState.copy(worldInfoBudgetPercent = percent)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeWorldInfoBudgetCap::class)
    private fun onChangeWorldInfoBudgetCap(intent: MainUiIntent.ChangeWorldInfoBudgetCap) {
        updateSettingsInt(intent.value, minimum = 0) {
            AppModel.worldInfoBudgetCap = it
            copy(worldInfoBudgetCap = it)
        }
    }

    @UiIntentObserver(MainUiIntent.ToggleWorldInfoOverflowAlert::class)
    private fun onToggleWorldInfoOverflowAlert(intent: MainUiIntent.ToggleWorldInfoOverflowAlert) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.worldInfoOverflowAlert = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                worldInfoOverflowAlert = intent.enabled
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleContextTrimmingAlert::class)
    private fun onToggleContextTrimmingAlert(intent: MainUiIntent.ToggleContextTrimmingAlert) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.contextTrimmingAlert = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                contextTrimmingAlert = intent.enabled
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.SelectExampleDialogueBehavior::class)
    private fun onSelectExampleDialogueBehavior(
        intent: MainUiIntent.SelectExampleDialogueBehavior
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.exampleDialogueBehavior = intent.behavior.persistedValue
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                exampleDialogueBehavior = intent.behavior
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleDebugModeEnabled::class)
    private fun onToggleDebugModeEnabled(intent: MainUiIntent.ToggleDebugModeEnabled) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.debugModeEnabled = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(debugModeEnabled = intent.enabled)
        ).setup()
    }

    private suspend fun buildHomeState(): MainHomeState {
        return withContext(Dispatchers.IO) {
            val characters = mCharacterRepository.getAllCharacters()
            val characterMap = characters.associateBy { it.id }
            val sessions = mChatRepository.getAllSessions()
            val groupSessions = mGroupChatRepository.getAllSessions()
            val sessionItems = sessions.map { session ->
                session.toUiModel(characterMap[session.characterId])
            }
            MainHomeState(
                sessionGroups = sessionItems.groupBy { it.characterId }.map { (id, items) ->
                    MainChatSessionGroup(
                        characterId = id,
                        characterName = items.firstOrNull()?.characterName.orEmpty(),
                        sessions = items
                    )
                },
                groupChatSessions = groupSessions.map { session ->
                    val data = mGroupChatRepository.getGroupChatData(session.id)
                    MainGroupChatSessionItem(
                        id = session.id.toString(),
                        title = session.title,
                        memberNames = data?.members
                            ?.joinToString(", ") { it.character.name }
                            .orEmpty(),
                        preview = data?.messages?.lastOrNull()?.content
                            ?.stripThinkBlocks()
                            ?.takeIf { it.isNotBlank() }
                            ?: mContext.getString(R.string.no_messages_yet),
                        messageCount = data?.messages?.size ?: 0,
                        updatedAt = session.latestTime.formatTimestamp("MM-dd HH:mm")
                    )
                },
                totalCharacters = characters.size,
                totalWorldBooks = mLorebookRepository.getAllLorebooks().size
            )
        }
    }

    private suspend fun buildSettingsState(
        providers: List<LLMProvider>,
        selectedProvider: LLMProvider?
    ): MainSettingsState {
        return MainSettingsState(
            userName = AppModel.userName,
            hasUserAvatar = AppModel.userAvatar.isNotBlank(),
            userAvatarImage = resolveUserAvatarImage(),
            userDescription = AppModel.userDescription,
            selectedProviderId = selectedProvider?.id?.toString().orEmpty(),
            providers = providers.map { it.toMainProviderItem() },
            temperature = selectedProvider?.temperature ?: 0.8f,
            topP = selectedProvider?.topP ?: 1.0f,
            maxTokens = selectedProvider?.maxTokens ?: 1200,
            contextTokens = selectedProvider?.contextTokens ?: 8192,
            streamEnabled = AppModel.streamEnabled,
            promptPostProcessingMode = selectedProvider?.postProcessingMode()
                ?: PromptPostProcessingMode.None,
            exampleDialogueBehavior = readExampleDialogueBehavior(),
            includeThinkInContext = AppModel.includeThinkInContext,
            worldInfoBudgetPercent = AppModel.worldInfoBudgetPercent.coerceIn(0, 100),
            worldInfoBudgetCap = AppModel.worldInfoBudgetCap.coerceAtLeast(0),
            worldInfoOverflowAlert = AppModel.worldInfoOverflowAlert,
            contextTrimmingAlert = AppModel.contextTrimmingAlert,
            debugModeEnabled = AppModel.debugModeEnabled,
            autoSummaryEnabled = AppModel.autoSummaryEnabled,
            summaryTriggerMessageCount = AppModel.summaryTriggerMessageCount,
            summaryWordsLimit = AppModel.summaryWordsLimit,
            summaryMaxMessagesPerRequest = AppModel.summaryMaxMessagesPerRequest,
            summaryResponseTokens = AppModel.summaryResponseTokens,
            summaryInjectionPosition = readSummaryInjectionPosition(),
            summaryInjectionDepth = AppModel.summaryInjectionDepth,
            summaryInjectionRole = readSummaryInjectionRole()
        )
    }

    private fun LLMProvider.postProcessingMode(): PromptPostProcessingMode {
        return PromptPostProcessingMode.fromOrdinal(promptPostProcessingMode)
    }

    private fun LLMProvider.toMainProviderItem(): MainProviderItem {
        return MainProviderItem(
            id = id,
            name = name,
            baseUrl = baseUrl,
            model = model,
            isEnabled = isEnabled
        )
    }

    private fun Character.toImportCharacterItem(): MainImportCharacterItem {
        return MainImportCharacterItem(
            id = id,
            name = name,
            details = creator.takeIf { it.isNotBlank() }
                ?: description.lineSequence().firstOrNull().orEmpty().take(80)
        )
    }

    private fun readSummaryInjectionPosition(): SummaryInjectionPosition {
        return SummaryInjectionPosition.fromPersistedValue(AppModel.summaryInjectionPosition)
    }

    private fun readExampleDialogueBehavior(): ExampleDialogueBehavior {
        return ExampleDialogueBehavior.fromPersistedValue(AppModel.exampleDialogueBehavior)
    }

    private fun readSummaryInjectionRole(): SummaryInjectionRole {
        return SummaryInjectionRole.fromPersistedValue(AppModel.summaryInjectionRole)
    }

    private suspend fun resolveUserAvatarImage() =
        AppModel.userAvatar
            .takeIf { it.isNotBlank() }
            ?.let { withContext(Dispatchers.IO) { mFileRepository.loadBitmap(it)?.asImageBitmap() } }

    private fun updateSettingsInt(
        value: String,
        minimum: Int,
        update: MainSettingsState.(Int) -> MainSettingsState
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val intValue = value.toIntOrNull()?.coerceAtLeast(minimum) ?: minimum
        uiState.copy(
            settingsState = uiState.settingsState.update(intValue)
        ).setup()
    }

    private suspend fun ChatSession.toUiModel(character: Character?): MainChatSessionItem {
        val latestMessage = mChatRepository.getLatestMessageBySessionId(id)
        return MainChatSessionItem(
            id = id.toString(),
            characterId = characterId.toString(),
            characterName = character?.name.orEmpty().ifBlank { mContext.getString(R.string.unknown_character) },
            title = title,
            preview = latestMessage?.content?.stripThinkBlocks()?.takeIf { it.isNotBlank() } ?: mContext.getString(R.string.no_messages_yet),
            messageCount = mChatRepository.getMessageCountBySessionId(id),
            updatedAt = latestTime.formatTimestamp("MM-dd HH:mm")
        )
    }
}
