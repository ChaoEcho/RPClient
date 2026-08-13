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
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionGroup
import me.kafuuneko.rpclient.feature.main.model.MainGenerationParameter
import me.kafuuneko.rpclient.feature.main.model.MainGroupChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainImportCharacterItem
import me.kafuuneko.rpclient.feature.main.model.MainProviderItem
import me.kafuuneko.rpclient.feature.main.model.MainSessionType
import me.kafuuneko.rpclient.feature.main.presentation.MainChatDataManagementState
import me.kafuuneko.rpclient.feature.main.presentation.MainDebugSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainDialogState
import me.kafuuneko.rpclient.feature.main.presentation.MainGenerationParametersState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeResourceState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeSelectionState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeState
import me.kafuuneko.rpclient.feature.main.presentation.MainPage
import me.kafuuneko.rpclient.feature.main.presentation.MainPromptBehaviorState
import me.kafuuneko.rpclient.feature.main.presentation.MainProviderPostProcessingState
import me.kafuuneko.rpclient.feature.main.presentation.MainProviderSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentChatsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentGroupChatsState
import me.kafuuneko.rpclient.feature.main.presentation.MainSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummaryInjectionState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummarySettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummarySettingsTab
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.main.presentation.MainUserAvatarState
import me.kafuuneko.rpclient.feature.main.presentation.MainUserIdentityState
import me.kafuuneko.rpclient.feature.main.presentation.MainViewEvent
import me.kafuuneko.rpclient.feature.main.presentation.MainWorldInfoBudgetState
import me.kafuuneko.rpclient.feature.main.presentation.canOpenDialog
import me.kafuuneko.rpclient.feature.main.presentation.mergeResumeRefresh
import me.kafuuneko.rpclient.feature.main.presentation.preserveCollapsedGroupsFrom
import me.kafuuneko.rpclient.feature.main.presentation.toggleSession
import me.kafuuneko.rpclient.feature.main.presentation.toMainSummaryInjectionState
import me.kafuuneko.rpclient.feature.story.list.StoryListActivity
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
        val settingsState = buildSettingsState(providers, selectedProvider)
        val current = getOrNull<MainUiState.Normal>() ?: return
        current.mergeResumeRefresh(
            homeState = homeState,
            settingsState = settingsState
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.homeState.selectionState is MainHomeSelectionState.Selecting) {
            uiState.copy(
                homeState = uiState.homeState.copy(
                    selectionState = MainHomeSelectionState.None
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
        if (uiState.homeState.selectionState is MainHomeSelectionState.Selecting) return
        uiState.copy(
            homeState = uiState.homeState.copy(
                selectionState = MainHomeSelectionState.Selecting(
                    selectedSessions = setOf(intent.session)
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleSessionSelection::class)
    private fun onToggleSessionSelection(intent: MainUiIntent.ToggleSessionSelection) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val selectionState = uiState.homeState.selectionState
            as? MainHomeSelectionState.Selecting
            ?: return
        uiState.copy(
            homeState = uiState.homeState.copy(
                selectionState = selectionState.toggleSession(intent.session)
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleSessionGroup::class)
    private fun onToggleSessionGroup(intent: MainUiIntent.ToggleSessionGroup) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val recentChatsState = uiState.homeState.recentChatsState
            as? MainRecentChatsState.Content
            ?: return
        if (recentChatsState.sessionGroups.none { it.characterId == intent.characterId }) return
        val collapsedCharacterIds = recentChatsState.collapsedCharacterIds
        val updated = if (intent.characterId in collapsedCharacterIds) {
            collapsedCharacterIds - intent.characterId
        } else {
            collapsedCharacterIds + intent.characterId
        }
        uiState.copy(
            homeState = uiState.homeState.copy(
                recentChatsState = recentChatsState.copy(
                    collapsedCharacterIds = updated
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ExitMultiSelect::class)
    private fun onExitMultiSelect() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.homeState.selectionState !is MainHomeSelectionState.Selecting) return
        uiState.copy(
            homeState = uiState.homeState.copy(
                selectionState = MainHomeSelectionState.None
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ShowDeleteSelectedDialog::class)
    private fun onShowDeleteSelectedDialog() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (!uiState.canOpenDialog()) return
        val selectionState = uiState.homeState.selectionState
            as? MainHomeSelectionState.Selecting
            ?: return
        val count = selectionState.selectedSessions.size
        if (count == 0) return
        uiState.copy(
            dialogState = MainDialogState.DeleteSelectedSessions(count = count)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ConfirmDeleteSelected::class)
    private suspend fun onConfirmDeleteSelected() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.dialogState !is MainDialogState.DeleteSelectedSessions) return
        val selectionState = uiState.homeState.selectionState
            as? MainHomeSelectionState.Selecting
        val selections = selectionState?.selectedSessions.orEmpty()
        withContext(Dispatchers.IO) {
            selections.forEach { selection ->
                val sessionId = selection.sessionId.toLongOrNull() ?: return@forEach
                when (selection.type) {
                    MainSessionType.Chat -> mChatRepository.deleteSession(sessionId)
                    MainSessionType.GroupChat -> mGroupChatRepository.deleteSession(sessionId)
                }
            }
        }
        val homeState = buildHomeState()
        val current = getOrNull<MainUiState.Normal>() ?: return
        current.copy(
            dialogState = if (
                current.dialogState is MainDialogState.DeleteSelectedSessions
            ) {
                MainDialogState.None
            } else {
                current.dialogState
            },
            homeState = homeState.preserveCollapsedGroupsFrom(current.homeState)
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
        if (!uiState.canOpenDialog() || mChatImportJob?.isActive == true) return
        MainViewEvent.OpenChatImporter.tryEmit()
    }

    @UiIntentObserver(MainUiIntent.ImportChatResult::class)
    private fun onImportChatResult(intent: MainUiIntent.ImportChatResult) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (!uiState.canOpenDialog() || mChatImportJob?.isActive == true) return
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                chatDataManagementState = MainChatDataManagementState.Reading
            )
        ).setup()
        mChatImportJob = viewModelScope.launch {
            try {
                val archive = mChatArchiveRepository.readImportFromUri(intent.uri)
                val characters = mCharacterRepository.getAllCharacters()
                mPendingChatImport = archive
                val current = getOrNull<MainUiState.Normal>() ?: return@launch
                val items = characters.map { it.toImportCharacterItem() }
                current.copy(
                    settingsState = current.settingsState.copy(
                        chatDataManagementState = MainChatDataManagementState.Idle
                    ),
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
                        settingsState = current.settingsState.copy(
                            chatDataManagementState = MainChatDataManagementState.Idle
                        )
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
                val homeState = buildHomeState()
                val current = getOrNull<MainUiState.Normal>() ?: return@launch
                current.copy(
                    homeState = homeState.preserveCollapsedGroupsFrom(current.homeState),
                    dialogState = if (
                        current.dialogState is MainDialogState.ImportChatCharacterSelection
                    ) {
                        MainDialogState.None
                    } else {
                        current.dialogState
                    }
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

    @UiIntentObserver(MainUiIntent.OpenStoryLibrary::class)
    private fun onOpenStoryLibrary() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(StoryListActivity::class.java).tryEmit()
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
        val providerState = uiState.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        AppViewEvent.StartActivity(
            activity = LLMProviderEditActivity::class.java,
            extras = Bundle().apply {
                putLong(LLMProviderEditActivity.EXTRA_PROVIDER_ID, providerState.selectedProviderId)
            }
        ).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.ShowGenerationParameterDialog::class)
    private suspend fun onShowGenerationParameterDialog(
        intent: MainUiIntent.ShowGenerationParameterDialog
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (!uiState.canOpenDialog()) return
        val providerState = uiState.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        val provider = withContext(Dispatchers.IO) {
            mLLMRepository.getProviderById(providerState.selectedProviderId)
        } ?: return
        val current = getOrNull<MainUiState.Normal>() ?: return
        if (!current.canOpenDialog()) return
        val currentProviderState = current.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        if (currentProviderState.selectedProviderId != provider.id) return
        current.copy(
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
        val providerState = uiState.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        val provider = withContext(Dispatchers.IO) {
            mLLMRepository.getProviderById(providerState.selectedProviderId)
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
        val current = getOrNull<MainUiState.Normal>() ?: return
        val currentProviderState = current.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        if (currentProviderState.selectedProviderId != updatedProvider.id) return
        current.copy(
            dialogState = if (
                current.dialogState is MainDialogState.EditGenerationParameter
            ) {
                MainDialogState.None
            } else {
                current.dialogState
            },
            settingsState = current.settingsState.copy(
                providerState = currentProviderState.copy(
                    generationParametersState = updatedProvider.toGenerationParametersState()
                )
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
        if (!isStateOf<MainUiState.Normal>()) return
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
        val avatarImage = resolveUserAvatarImage()
        val current = getOrNull<MainUiState.Normal>() ?: return
        current.copy(
            settingsState = current.settingsState.copy(
                identityState = current.settingsState.identityState.copy(
                    avatarState = MainUserAvatarState.Configured(avatarImage)
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ClearUserAvatar::class)
    private suspend fun onClearUserAvatar() {
        if (!isStateOf<MainUiState.Normal>()) return
        val oldAvatar = AppModel.userAvatar
        AppModel.userAvatar = ""
        if (oldAvatar.isNotBlank()) {
            runCatching {
                withContext(Dispatchers.IO) {
                    mFileRepository.deleteFile(oldAvatar)
                }
            }
        }
        val current = getOrNull<MainUiState.Normal>() ?: return
        current.copy(
            settingsState = current.settingsState.copy(
                identityState = current.settingsState.identityState.copy(
                    avatarState = MainUserAvatarState.None
                )
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
            settingsState = uiState.settingsState.copy(
                identityState = uiState.settingsState.identityState.copy(
                    userName = intent.value
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeUserDescription::class)
    private fun onChangeUserDescription(intent: MainUiIntent.ChangeUserDescription) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.userDescription = intent.value.trim()
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                identityState = uiState.settingsState.identityState.copy(
                    userDescription = intent.value
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.SelectProvider::class)
    private suspend fun onSelectProvider(intent: MainUiIntent.SelectProvider) {
        if (!isStateOf<MainUiState.Normal>()) return
        mLLMRepository.updateCurrentProvider(intent.providerId)
        val providers = mLLMRepository.getEnabledProviders()
        val selectedProvider = providers.firstOrNull { it.id == intent.providerId } ?: return
        val current = getOrNull<MainUiState.Normal>() ?: return
        val promptBehaviorState = current.settingsState.promptBehaviorState
        current.copy(
            selectedPage = MainPage.Settings,
            settingsState = current.settingsState.copy(
                providerState = buildProviderSettingsState(providers, selectedProvider),
                promptBehaviorState = promptBehaviorState.copy(
                    providerPostProcessingState = MainProviderPostProcessingState.Available(
                        selectedProvider.postProcessingMode()
                    )
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleAutoSummaryEnabled::class)
    private fun onToggleAutoSummaryEnabled(intent: MainUiIntent.ToggleAutoSummaryEnabled) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.autoSummaryEnabled = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryState = uiState.settingsState.summaryState.copy(
                    autoSummaryEnabled = intent.enabled
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryTriggerMessageCount::class)
    private fun onChangeSummaryTriggerMessageCount(intent: MainUiIntent.ChangeSummaryTriggerMessageCount) {
        updateSettingsInt(intent.value, minimum = 1) {
            AppModel.summaryTriggerMessageCount = it
            copy(
                summaryState = summaryState.copy(triggerMessageCount = it)
            )
        }
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryWordsLimit::class)
    private fun onChangeSummaryWordsLimit(intent: MainUiIntent.ChangeSummaryWordsLimit) {
        updateSettingsInt(intent.value, minimum = 50) {
            AppModel.summaryWordsLimit = it
            copy(
                summaryState = summaryState.copy(wordsLimit = it)
            )
        }
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryMaxMessagesPerRequest::class)
    private fun onChangeSummaryMaxMessagesPerRequest(intent: MainUiIntent.ChangeSummaryMaxMessagesPerRequest) {
        updateSettingsInt(intent.value, minimum = 0) {
            AppModel.summaryMaxMessagesPerRequest = it
            copy(
                summaryState = summaryState.copy(maxMessagesPerRequest = it)
            )
        }
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryResponseTokens::class)
    private fun onChangeSummaryResponseTokens(intent: MainUiIntent.ChangeSummaryResponseTokens) {
        updateSettingsInt(intent.value, minimum = 128) {
            AppModel.summaryResponseTokens = it
            copy(
                summaryState = summaryState.copy(responseTokens = it)
            )
        }
    }

    @UiIntentObserver(MainUiIntent.SelectSummarySettingsTab::class)
    private fun onSelectSummarySettingsTab(intent: MainUiIntent.SelectSummarySettingsTab) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryState = uiState.settingsState.summaryState.copy(
                    selectedTab = intent.tab
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.SelectSummaryInjectionPosition::class)
    private fun onSelectSummaryInjectionPosition(
        intent: MainUiIntent.SelectSummaryInjectionPosition
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.summaryInjectionPosition = intent.position.persistedValue
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryState = uiState.settingsState.summaryState.copy(
                    injectionState = buildSummaryInjectionState(intent.position)
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryInjectionDepth::class)
    private fun onChangeSummaryInjectionDepth(intent: MainUiIntent.ChangeSummaryInjectionDepth) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val injectionState = uiState.settingsState.summaryState.injectionState
            as? MainSummaryInjectionState.InChat
            ?: return
        val value = intent.value.toIntOrNull()?.coerceAtLeast(0) ?: 0
        AppModel.summaryInjectionDepth = value
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryState = uiState.settingsState.summaryState.copy(
                    injectionState = injectionState.copy(depth = value)
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.SelectSummaryInjectionRole::class)
    private fun onSelectSummaryInjectionRole(intent: MainUiIntent.SelectSummaryInjectionRole) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val injectionState = uiState.settingsState.summaryState.injectionState
            as? MainSummaryInjectionState.InChat
            ?: return
        AppModel.summaryInjectionRole = intent.role.persistedValue
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryState = uiState.settingsState.summaryState.copy(
                    injectionState = injectionState.copy(role = intent.role)
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleStreamEnabled::class)
    private fun onToggleStreamEnabled(intent: MainUiIntent.ToggleStreamEnabled) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.streamEnabled = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                promptBehaviorState = uiState.settingsState.promptBehaviorState.copy(
                    streamEnabled = intent.enabled
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.SelectPostProcessingMode::class)
    private suspend fun onSelectPostProcessingMode(intent: MainUiIntent.SelectPostProcessingMode) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val providerState = uiState.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        val provider = withContext(Dispatchers.IO) {
            mLLMRepository.getProviderById(providerState.selectedProviderId)
        } ?: return
        val updatedProvider = provider.copy(promptPostProcessingMode = intent.mode.ordinal)
        withContext(Dispatchers.IO) {
            mLLMRepository.saveProvider(updatedProvider)
        }
        val current = getOrNull<MainUiState.Normal>() ?: return
        val currentProviderState = current.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        if (currentProviderState.selectedProviderId != updatedProvider.id) return
        current.copy(
            settingsState = current.settingsState.copy(
                promptBehaviorState = current.settingsState.promptBehaviorState.copy(
                    providerPostProcessingState = MainProviderPostProcessingState.Available(
                        intent.mode
                    )
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleIncludeThinkInContext::class)
    private fun onToggleIncludeThinkInContext(intent: MainUiIntent.ToggleIncludeThinkInContext) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.includeThinkInContext = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                promptBehaviorState = uiState.settingsState.promptBehaviorState.copy(
                    includeThinkInContext = intent.enabled
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeWorldInfoBudgetPercent::class)
    private fun onChangeWorldInfoBudgetPercent(intent: MainUiIntent.ChangeWorldInfoBudgetPercent) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val percent = intent.value.coerceIn(0, 100)
        AppModel.worldInfoBudgetPercent = percent
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                worldInfoBudgetState = uiState.settingsState.worldInfoBudgetState.copy(
                    budgetPercent = percent
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeWorldInfoBudgetCap::class)
    private fun onChangeWorldInfoBudgetCap(intent: MainUiIntent.ChangeWorldInfoBudgetCap) {
        updateSettingsInt(intent.value, minimum = 0) {
            AppModel.worldInfoBudgetCap = it
            copy(
                worldInfoBudgetState = worldInfoBudgetState.copy(budgetCap = it)
            )
        }
    }

    @UiIntentObserver(MainUiIntent.ToggleWorldInfoOverflowAlert::class)
    private fun onToggleWorldInfoOverflowAlert(intent: MainUiIntent.ToggleWorldInfoOverflowAlert) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.worldInfoOverflowAlert = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                worldInfoBudgetState = uiState.settingsState.worldInfoBudgetState.copy(
                    overflowAlert = intent.enabled
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleContextTrimmingAlert::class)
    private fun onToggleContextTrimmingAlert(intent: MainUiIntent.ToggleContextTrimmingAlert) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.contextTrimmingAlert = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                promptBehaviorState = uiState.settingsState.promptBehaviorState.copy(
                    contextTrimmingAlert = intent.enabled
                )
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
                promptBehaviorState = uiState.settingsState.promptBehaviorState.copy(
                    exampleDialogueBehavior = intent.behavior
                )
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleDebugModeEnabled::class)
    private fun onToggleDebugModeEnabled(intent: MainUiIntent.ToggleDebugModeEnabled) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.debugModeEnabled = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                debugState = uiState.settingsState.debugState.copy(enabled = intent.enabled)
            )
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
            val sessionGroups = sessionItems.groupBy { it.characterId }.map { (id, items) ->
                MainChatSessionGroup(
                    characterId = id,
                    characterName = items.firstOrNull()?.characterName.orEmpty(),
                    sessions = items
                )
            }
            val groupChatItems = groupSessions.map { session ->
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
            }
            MainHomeState(
                resourceState = MainHomeResourceState(
                    totalCharacters = characters.size,
                    totalWorldBooks = mLorebookRepository.getAllLorebooks().size
                ),
                recentChatsState = if (sessionGroups.isEmpty()) {
                    MainRecentChatsState.Empty
                } else {
                    MainRecentChatsState.Content(sessionGroups = sessionGroups)
                },
                recentGroupChatsState = if (groupChatItems.isEmpty()) {
                    MainRecentGroupChatsState.Empty
                } else {
                    MainRecentGroupChatsState.Content(sessions = groupChatItems)
                },
            )
        }
    }

    private suspend fun buildSettingsState(
        providers: List<LLMProvider>,
        selectedProvider: LLMProvider?
    ): MainSettingsState {
        return MainSettingsState(
            identityState = MainUserIdentityState(
                userName = AppModel.userName,
                userDescription = AppModel.userDescription,
                avatarState = if (AppModel.userAvatar.isBlank()) {
                    MainUserAvatarState.None
                } else {
                    MainUserAvatarState.Configured(resolveUserAvatarImage())
                }
            ),
            providerState = buildProviderSettingsState(providers, selectedProvider),
            promptBehaviorState = MainPromptBehaviorState(
                providerPostProcessingState = selectedProvider?.let {
                    MainProviderPostProcessingState.Available(it.postProcessingMode())
                } ?: MainProviderPostProcessingState.Unavailable,
                exampleDialogueBehavior = readExampleDialogueBehavior(),
                includeThinkInContext = AppModel.includeThinkInContext,
                contextTrimmingAlert = AppModel.contextTrimmingAlert,
                streamEnabled = AppModel.streamEnabled
            ),
            worldInfoBudgetState = MainWorldInfoBudgetState(
                budgetPercent = AppModel.worldInfoBudgetPercent.coerceIn(0, 100),
                budgetCap = AppModel.worldInfoBudgetCap.coerceAtLeast(0),
                overflowAlert = AppModel.worldInfoOverflowAlert
            ),
            summaryState = MainSummarySettingsState(
                autoSummaryEnabled = AppModel.autoSummaryEnabled,
                triggerMessageCount = AppModel.summaryTriggerMessageCount,
                wordsLimit = AppModel.summaryWordsLimit,
                maxMessagesPerRequest = AppModel.summaryMaxMessagesPerRequest,
                responseTokens = AppModel.summaryResponseTokens,
                injectionState = buildSummaryInjectionState(readSummaryInjectionPosition())
            ),
            debugState = MainDebugSettingsState(
                enabled = AppModel.debugModeEnabled
            )
        )
    }

    private fun buildProviderSettingsState(
        providers: List<LLMProvider>,
        selectedProvider: LLMProvider?
    ): MainProviderSettingsState {
        if (selectedProvider == null) return MainProviderSettingsState.Empty
        return MainProviderSettingsState.Available(
            selectedProviderId = selectedProvider.id,
            providers = providers.map { it.toMainProviderItem() },
            generationParametersState = selectedProvider.toGenerationParametersState()
        )
    }

    private fun buildSummaryInjectionState(
        position: SummaryInjectionPosition
    ): MainSummaryInjectionState {
        return position.toMainSummaryInjectionState(
            depth = AppModel.summaryInjectionDepth,
            role = readSummaryInjectionRole()
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

    private fun LLMProvider.toGenerationParametersState(): MainGenerationParametersState {
        return MainGenerationParametersState(
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            contextTokens = contextTokens
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
