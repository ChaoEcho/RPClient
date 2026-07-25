package me.kafuuneko.rpclient.feature.chatcreate

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateForm
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateLorebookGroupItem
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateLorebookEntryItem
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateLoadState
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiIntent
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.prompt.PromptBuildContext
import me.kafuuneko.rpclient.libs.prompt.PromptMacroResolver
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.utils.toggle
import me.kafuuneko.rpclient.libs.utils.toggleAll
import me.kafuuneko.rpclient.libs.utils.toDefaultChatTitle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 新建单角色会话页状态持有者，负责角色选择、世界书授权和初始会话落库。 */
class ChatCreateViewModel : CoreViewModelWithEvent<ChatCreateUiIntent, ChatCreateUiState>(
    ChatCreateUiState.None
), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mChatRepository by inject<ChatRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mMacroResolver by inject<PromptMacroResolver>()
    private var mCharactersById: Map<Long, Character> = emptyMap()

    @UiIntentObserver(ChatCreateUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<ChatCreateUiState.None>()) return
        ChatCreateUiState.Normal(loadState = ChatCreateLoadState.Loading).setup()
        val data = withContext(Dispatchers.IO) {
            val characters = mCharacterRepository.getAllCharacters()
            val lorebooks = mLorebookRepository.getAllLorebooks()
            val groups = lorebooks.map { lorebook ->
                val entries = mLorebookRepository.getEntriesByLorebookId(lorebook.id)
                    .sortedBy { it.order }
                    .map { entry ->
                        ChatCreateLorebookEntryItem(
                            id = entry.id,
                            lorebookName = lorebook.name,
                            name = entry.name,
                            content = entry.content,
                            keywords = entry.getKeywordList(),
                            secondaryKeywords = entry.getSecondaryKeywordList(),
                            category = entry.getCategoryList(),
                            constant = entry.constant,
                            order = entry.order,
                            depth = entry.depth
                        )
                    }
                ChatCreateLorebookGroupItem(
                    lorebookId = lorebook.id,
                    lorebookName = lorebook.name,
                    entryCount = entries.size,
                    entries = entries
                )
            }.filter { it.entries.isNotEmpty() }
            characters to groups
        }
        mCharactersById = data.first.associateBy { it.id }
        val selectedCharacter = data.first.firstOrNull()
        val selectedCharacterFirstMessages = selectedCharacter?.getChatFirstMessageList().orEmpty()
        val selectedLorebookEntryIds = selectedCharacter
            ?.defaultLorebookEntryIds(data.second)
            .orEmpty()
        ChatCreateUiState.Normal(
            loadState = ChatCreateLoadState.None,
            form = (getOrNull<ChatCreateUiState.Normal>()?.form ?: ChatCreateForm())
                .selectCharacter(
                    characterId = selectedCharacter?.id,
                    hasFirstMessage = selectedCharacterFirstMessages.isNotEmpty(),
                    linkedLorebookEntryIds = selectedLorebookEntryIds
                ),
            characters = data.first.map { character ->
                ChatCreateCharacterItem(
                    id = character.id,
                    name = character.name,
                    description = character.description,
                    tags = character.getCharacterTagList()
                )
            },
            selectedCharacterFirstMessages = selectedCharacterFirstMessages,
            lorebookGroups = data.second,
            visibleLorebookGroups = data.second
        ).setup()
    }

    @UiIntentObserver(ChatCreateUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<ChatCreateUiState.Finished>()) return
        ChatCreateUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(ChatCreateUiIntent.SelectCharacter::class)
    private fun onSelectCharacter(intent: ChatCreateUiIntent.SelectCharacter) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val character = mCharactersById[intent.characterId] ?: return
        if (uiState.form.selectedCharacterId == character.id) return
        val previousLinkedLorebookEntryIds = uiState.selectedCharacter()
            ?.defaultLorebookEntryIds(uiState.lorebookGroups)
            .orEmpty()
        val selectedCharacterFirstMessages = character.getChatFirstMessageList()
        uiState.copy(
            form = uiState.form.selectCharacter(
                characterId = character.id,
                hasFirstMessage = selectedCharacterFirstMessages.isNotEmpty(),
                previousLinkedLorebookEntryIds = previousLinkedLorebookEntryIds,
                linkedLorebookEntryIds = character.defaultLorebookEntryIds(uiState.lorebookGroups)
            ),
            selectedCharacterFirstMessages = selectedCharacterFirstMessages
        ).setup()
    }

    @UiIntentObserver(ChatCreateUiIntent.SelectFirstMessage::class)
    private fun onSelectFirstMessage(intent: ChatCreateUiIntent.SelectFirstMessage) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        if (intent.index !in uiState.selectedCharacterFirstMessages.indices) return
        updateForm { copy(selectedFirstMessageIndex = intent.index) }
    }

    @UiIntentObserver(ChatCreateUiIntent.ChangeTitle::class)
    private fun onChangeTitle(intent: ChatCreateUiIntent.ChangeTitle) {
        if (!isStateOf<ChatCreateUiState.Normal>()) return
        updateForm { copy(title = intent.value) }
    }

    @UiIntentObserver(ChatCreateUiIntent.ChangeUserNote::class)
    private fun onChangeUserNote(intent: ChatCreateUiIntent.ChangeUserNote) {
        if (!isStateOf<ChatCreateUiState.Normal>()) return
        updateForm { copy(userNote = intent.value) }
    }

    @UiIntentObserver(ChatCreateUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: ChatCreateUiIntent.ChangeLorebookQuery) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        uiState.copy(
            lorebookQuery = intent.value,
            visibleLorebookGroups = uiState.lorebookGroups.filterForQuery(intent.value)
        ).setup()
    }

    @UiIntentObserver(ChatCreateUiIntent.ToggleLorebookEntry::class)
    private fun onToggleLorebookEntry(intent: ChatCreateUiIntent.ToggleLorebookEntry) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        if (uiState.lorebookGroups.none { group -> group.entries.any { it.id == intent.entryId } }) return
        val selectedIds = uiState.form.selectedLorebookEntryIds
        updateForm {
            copy(
                selectedLorebookEntryIds = selectedIds.toggle(intent.entryId)
            )
        }
    }

    @UiIntentObserver(ChatCreateUiIntent.ToggleLorebook::class)
    private fun onToggleLorebook(intent: ChatCreateUiIntent.ToggleLorebook) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val group = uiState.lorebookGroups.firstOrNull { it.lorebookId == intent.lorebookId } ?: return
        val entryIds = group.entries.map { it.id }.toSet()
        if (entryIds.isEmpty()) return
        val selectedIds = uiState.form.selectedLorebookEntryIds
        updateForm {
            copy(
                selectedLorebookEntryIds = selectedIds.toggleAll(entryIds)
            )
        }
    }

    /**
     * 将创建表单转换为会话及可选开场消息，然后在事务成功后进入聊天页。
     *
     * 开场白宏使用与新会话完全相同的用户和角色快照解析；会话与首条消息由 Repository
     * 原子创建，页面不会观察到只有会话而缺少开场白的中间状态。
     */
    @UiIntentObserver(ChatCreateUiIntent.CreateChat::class)
    private suspend fun onCreateChat() {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        if (uiState.loadState != ChatCreateLoadState.None) return
        val character = uiState.selectedCharacter() ?: run {
            AppViewEvent.PopupToastMessageByResId(R.string.no_character_selected).tryEmit()
            return
        }
        val firstMessageSelection = uiState.resolveFirstMessageSelection() ?: return
        uiState.copy(loadState = ChatCreateLoadState.Creating).setup()
        val userName = AppModel.userName.trim().ifBlank { "You" }
        val userDescription = AppModel.userDescription.trim()
        val createTime = System.currentTimeMillis()
        val sessionTitle = uiState.form.normalizedTitle(createTime)

        val firstMessageContent = firstMessageSelection.value?.let { rawFirstMessage ->
            val session = ChatSession(
                id = 0L,
                characterId = character.id,
                createTime = createTime,
                latestTime = createTime,
                lorebookEntrySet = "",
                title = sessionTitle,
                userNote = uiState.form.userNote.trim(),
                userName = userName,
                userDescription = userDescription,
                creatorNotes = null
            )
            val context = PromptBuildContext(
                userName = userName,
                userDescription = userDescription,
                character = character,
                session = session,
                summary = "",
                messages = emptyList(),
                currentUserMessage = null,
                candidateLorebookEntries = emptyList(),
                provider = mLLMRepository.getSelectedProvider(),
                maxContextTokens = 0,
                maxResponseTokens = 0
            )
            mMacroResolver.resolve(rawFirstMessage, context)
        }

        val sessionId = withContext(Dispatchers.IO) {
            mChatRepository.createSessionWithFirstMessage(
                characterId = character.id,
                title = sessionTitle,
                userNote = uiState.form.userNote.trim(),
                userName = userName,
                userDescription = userDescription,
                lorebookEntryIds = uiState.form.selectedLorebookEntryIds.sorted(),
                firstMessageContent = firstMessageContent,
                createTime = createTime
            )
        }
        AppViewEvent.StartActivity(
            activity = ChatActivity::class.java,
            extras = Bundle().apply {
                putString(ChatActivity.EXTRA_SESSION_ID, sessionId.toString())
            }
        ).emitAndAwait()
        ChatCreateUiState.finished(uiStateFlow.value).setup()
    }

    private fun updateForm(block: ChatCreateForm.() -> ChatCreateForm) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

    private fun ChatCreateUiState.Normal.selectedCharacter(): Character? {
        val characterId = form.selectedCharacterId ?: return null
        return mCharactersById[characterId]
    }

    private fun ChatCreateUiState.Normal.resolveFirstMessageSelection(): FirstMessageSelection? {
        if (selectedCharacterFirstMessages.isEmpty()) return FirstMessageSelection(null)
        val selectedIndex = form.selectedFirstMessageIndex
        if (selectedIndex == null || selectedIndex !in selectedCharacterFirstMessages.indices) {
            AppViewEvent.PopupToastMessageByResId(R.string.first_message_required).tryEmit()
            return null
        }
        return FirstMessageSelection(selectedCharacterFirstMessages[selectedIndex])
    }

    private fun ChatCreateForm.normalizedTitle(createTime: Long): String {
        return title.trim().ifBlank { createTime.toDefaultChatTitle() }
    }

    private fun Character.defaultLorebookEntryIds(
        lorebookGroups: List<ChatCreateLorebookGroupItem>
    ): Set<Long> {
        if (characterLorebookId == 0L) return emptySet()
        return lorebookGroups
            .firstOrNull { it.lorebookId == characterLorebookId }
            ?.entries
            ?.mapTo(mutableSetOf()) { it.id }
            .orEmpty()
    }

    private fun List<ChatCreateLorebookGroupItem>.filterForQuery(
        query: String
    ): List<ChatCreateLorebookGroupItem> {
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
                    } ||
                    entry.category.any { it.contains(normalizedQuery, ignoreCase = true) }
            }
            when {
                groupMatches -> group
                matchingEntries.isNotEmpty() -> group.copy(entries = matchingEntries)
                else -> null
            }
        }
    }

    private data class FirstMessageSelection(val value: String?)
}
