package me.kafuuneko.rpclient.feature.groupchatcreate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateGreetingState
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatGreetingCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatGreetingMode
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateLoadState
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiIntent
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGreetingCandidate
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGreetingPlanner
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGreetingSelection
import me.kafuuneko.rpclient.libs.groupchat.GroupChatNavigation
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookEntryItem
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.groupchat.model.toEntity
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.utils.toggle
import me.kafuuneko.rpclient.libs.utils.toggleAll
import me.kafuuneko.rpclient.libs.utils.toDefaultChatTitle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 新建群聊页状态持有者，负责成员排序、世界书授权与群聊聚合数据落库。 */
class GroupChatCreateViewModel :
    CoreViewModelWithEvent<GroupChatCreateUiIntent, GroupChatCreateUiState>(
        GroupChatCreateUiState.None
    ), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mGroupChatRepository by inject<GroupChatRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mGreetingPlanner by inject<GroupChatGreetingPlanner>()

    @UiIntentObserver(GroupChatCreateUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<GroupChatCreateUiState.None>()) return
        GroupChatCreateUiState.Normal(
            loadState = GroupChatCreateLoadState.Loading
        ).setup()
        val data = withContext(Dispatchers.IO) {
            val characters = mCharacterRepository.getAllCharacters().map {
                GroupChatCreateCharacterItem(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    selected = false,
                    characterLorebookId = it.characterLorebookId,
                    greetings = it.getChatFirstMessageList()
                )
            }
            val lorebookGroups = mLorebookRepository.getAllLorebooks().map { lorebook ->
                GroupChatLorebookGroupItem(
                    lorebookId = lorebook.id,
                    lorebookName = lorebook.name,
                    entries = mLorebookRepository.getEntriesByLorebookId(lorebook.id)
                        .sortedBy { it.order }
                        .map { entry ->
                            GroupChatLorebookEntryItem(
                                id = entry.id,
                                lorebookId = lorebook.id,
                                lorebookName = lorebook.name,
                                name = entry.name,
                                content = entry.content,
                                keywords = entry.getKeywordList(),
                                secondaryKeywords = entry.getSecondaryKeywordList(),
                                constant = entry.constant,
                                order = entry.order,
                                depth = entry.depth,
                                enabled = false
                            )
                        }
                )
            }.filter { it.entries.isNotEmpty() }
            characters to lorebookGroups
        }
        GroupChatCreateUiState.Normal(
            characters = data.first,
            visibleCharacters = data.first,
            lorebookGroups = data.second,
            visibleLorebookGroups = data.second
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<GroupChatCreateUiState.Finished>()) return
        GroupChatCreateUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ChangeTitle::class)
    private fun onChangeTitle(intent: GroupChatCreateUiIntent.ChangeTitle) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(title = intent.value).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ChangeSearchQuery::class)
    private fun onChangeSearchQuery(intent: GroupChatCreateUiIntent.ChangeSearchQuery) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val query = intent.value.trim()
        uiState.copy(
            searchQuery = intent.value,
            visibleCharacters = uiState.characters.filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: GroupChatCreateUiIntent.ChangeLorebookQuery) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(
            lorebookQuery = intent.value,
            visibleLorebookGroups = uiState.lorebookGroups.filterForQuery(intent.value)
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ToggleCharacter::class)
    private fun onToggleCharacter(intent: GroupChatCreateUiIntent.ToggleCharacter) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val characters = uiState.characters.map {
            if (it.id == intent.characterId) it.copy(selected = !it.selected) else it
        }
        val selectedCharacter = characters.firstOrNull { it.id == intent.characterId }
        val defaultEntryIds = selectedCharacter
            ?.takeIf { it.selected }
            ?.characterLorebookId
            ?.takeIf { it > 0L }
            ?.let { lorebookId ->
                uiState.lorebookGroups
                    .firstOrNull { it.lorebookId == lorebookId }
                    ?.entries
                    ?.mapTo(mutableSetOf()) { it.id }
            }
            .orEmpty()
        val selectedEntryIds = uiState.selectedLorebookEntryIds + defaultEntryIds
        val lorebookGroups = uiState.lorebookGroups.withEnabledIds(selectedEntryIds)
        uiState.copy(
            characters = characters,
            visibleCharacters = characters.visibleFor(uiState.searchQuery),
            selectedLorebookEntryIds = selectedEntryIds,
            lorebookGroups = lorebookGroups,
            visibleLorebookGroups = lorebookGroups.filterForQuery(uiState.lorebookQuery),
            greetingState = uiState.greetingState.reconcile(characters)
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.SelectStrategy::class)
    private fun onSelectStrategy(intent: GroupChatCreateUiIntent.SelectStrategy) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(activationStrategy = intent.strategy).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ToggleAllowSelfResponses::class)
    private fun onToggleAllowSelfResponses(
        intent: GroupChatCreateUiIntent.ToggleAllowSelfResponses
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(allowSelfResponses = intent.enabled).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.SelectGreetingMode::class)
    private fun onSelectGreetingMode(
        intent: GroupChatCreateUiIntent.SelectGreetingMode
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(
            greetingState = uiState.greetingState
                .copy(mode = intent.mode)
                .reconcile(uiState.characters)
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.SelectGreetingCharacter::class)
    private fun onSelectGreetingCharacter(
        intent: GroupChatCreateUiIntent.SelectGreetingCharacter
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        if (uiState.greetingState.characters.none { it.id == intent.characterId }) return
        uiState.copy(
            greetingState = uiState.greetingState.copy(
                selectedCharacterId = intent.characterId,
                selectedGreetingIndex = 0
            )
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.SelectGreeting::class)
    private fun onSelectGreeting(intent: GroupChatCreateUiIntent.SelectGreeting) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val greetings = uiState.greetingState.selectedCharacter?.greetings.orEmpty()
        if (intent.greetingIndex !in greetings.indices) return
        uiState.copy(
            greetingState = uiState.greetingState.copy(
                selectedGreetingIndex = intent.greetingIndex
            )
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ChangeCustomGreeting::class)
    private fun onChangeCustomGreeting(
        intent: GroupChatCreateUiIntent.ChangeCustomGreeting
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(
            greetingState = uiState.greetingState.copy(customGreeting = intent.value)
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ToggleLorebook::class)
    /** 切换创建表单中一本世界书的全部条目。 */
    private fun onToggleLorebook(intent: GroupChatCreateUiIntent.ToggleLorebook) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val entryIds = uiState.lorebookGroups
            .firstOrNull { it.lorebookId == intent.lorebookId }
            ?.entries
            ?.mapTo(mutableSetOf()) { it.id }
            .orEmpty()
        if (entryIds.isEmpty()) return
        val selectedIds = uiState.selectedLorebookEntryIds.toggleAll(entryIds)
        val groups = uiState.lorebookGroups.withEnabledIds(selectedIds)
        uiState.copy(
            selectedLorebookEntryIds = selectedIds,
            lorebookGroups = groups,
            visibleLorebookGroups = groups.filterForQuery(uiState.lorebookQuery)
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ToggleLorebookEntry::class)
    /** 切换创建表单中的单个世界书条目。 */
    private fun onToggleLorebookEntry(
        intent: GroupChatCreateUiIntent.ToggleLorebookEntry
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        if (uiState.lorebookGroups.none { group ->
                group.entries.any { it.id == intent.entryId }
            }
        ) return
        val selectedIds = uiState.selectedLorebookEntryIds.toggle(intent.entryId)
        val groups = uiState.lorebookGroups.withEnabledIds(selectedIds)
        uiState.copy(
            selectedLorebookEntryIds = selectedIds,
            lorebookGroups = groups,
            visibleLorebookGroups = groups.filterForQuery(uiState.lorebookQuery)
        ).setup()
    }

    /**
     * 规划群聊开场白并事务创建会话、成员和开场消息。
     *
     * GreetingPlanner 先把随机/手动选择固定为有序快照，Repository 再一次性提交，
     * 防止创建过程中成员顺序或随机候选发生变化。
     */
    @UiIntentObserver(GroupChatCreateUiIntent.Create::class)
    private suspend fun onCreate() {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        if (uiState.loadState != GroupChatCreateLoadState.None) return
        val characterIds = uiState.characters.filter { it.selected }.map { it.id }
        if (characterIds.size < 2) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.group_chat_select_two_characters
            ).tryEmit()
            return
        }
        if (!uiState.greetingState.canCreate) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.group_chat_greeting_incomplete
            ).tryEmit()
            return
        }
        uiState.copy(loadState = GroupChatCreateLoadState.Creating).setup()
        val createTime = System.currentTimeMillis()
        val userName = AppModel.userName.trim().ifBlank { "You" }
        val greetingCandidates = uiState.greetingState.characters.map {
            GroupChatGreetingCandidate(
                characterId = it.id,
                characterName = it.name,
                greetings = it.greetings
            )
        }
        val openingMessages = mGreetingPlanner.plan(
            candidates = greetingCandidates,
            selection = uiState.greetingState.toSelection(),
            userName = userName
        )
        val sessionId = withContext(Dispatchers.IO) {
            mGroupChatRepository.createSession(
                title = uiState.title.trim().ifBlank { createTime.toDefaultChatTitle() },
                userName = userName,
                userDescription = AppModel.userDescription.trim(),
                characterIds = characterIds,
                lorebookEntryIds = uiState.selectedLorebookEntryIds.sorted(),
                activationStrategy = uiState.activationStrategy.toEntity(),
                allowSelfResponses = uiState.allowSelfResponses,
                openingMessages = openingMessages,
                createTime = createTime
            )
        }
        AppViewEvent.StartActivityByIntent(
            GroupChatNavigation.createIntent(sessionId)
        ).emitAndAwait()
        GroupChatCreateUiState.finished(uiStateFlow.value).setup()
    }

    private fun List<GroupChatCreateCharacterItem>.visibleFor(
        query: String
    ): List<GroupChatCreateCharacterItem> {
        val normalized = query.trim()
        return filter {
            normalized.isBlank() ||
                it.name.contains(normalized, ignoreCase = true) ||
                it.description.contains(normalized, ignoreCase = true)
        }
    }

    private fun List<GroupChatLorebookGroupItem>.withEnabledIds(
        selectedIds: Set<Long>
    ): List<GroupChatLorebookGroupItem> = map { group ->
        group.copy(entries = group.entries.map { it.copy(enabled = it.id in selectedIds) })
    }

    private fun List<GroupChatLorebookGroupItem>.filterForQuery(
        query: String
    ): List<GroupChatLorebookGroupItem> {
        val normalized = query.trim()
        if (normalized.isBlank()) return this
        return mapNotNull { group ->
            val groupMatches = group.lorebookName.contains(normalized, ignoreCase = true)
            val matchingEntries = group.entries.filter { entry ->
                entry.lorebookName.contains(normalized, ignoreCase = true) ||
                    entry.name.contains(normalized, ignoreCase = true) ||
                    entry.content.contains(normalized, ignoreCase = true) ||
                    entry.keywords.any { it.contains(normalized, ignoreCase = true) } ||
                    entry.secondaryKeywords.any { it.contains(normalized, ignoreCase = true) }
            }
            when {
                groupMatches -> group
                matchingEntries.isNotEmpty() -> group.copy(entries = matchingEntries)
                else -> null
            }
        }
    }

    /**
     * 成员变化后收敛开场白候选和选择，避免删除成员后留下悬空角色或候选索引。
     */
    private fun GroupChatCreateGreetingState.reconcile(
        characters: List<GroupChatCreateCharacterItem>
    ): GroupChatCreateGreetingState {
        val candidates = characters.filter { it.selected }.map {
            GroupChatGreetingCharacterItem(
                id = it.id,
                name = it.name,
                greetings = it.greetings
            )
        }
        val current = candidates.firstOrNull { it.id == selectedCharacterId }
        val preferred = when (mode) {
            GroupChatGreetingMode.Manual ->
                current?.takeIf { it.greetings.isNotEmpty() }
                    ?: candidates.firstOrNull { it.greetings.isNotEmpty() }
            else -> current ?: candidates.firstOrNull()
        }
        val greetingIndex = selectedGreetingIndex
            ?.takeIf { it in preferred?.greetings.orEmpty().indices }
            ?: preferred?.greetings?.indices?.firstOrNull()
        return copy(
            characters = candidates,
            selectedCharacterId = preferred?.id,
            selectedGreetingIndex = greetingIndex
        )
    }

    private fun GroupChatCreateGreetingState.toSelection(): GroupChatGreetingSelection {
        return when (mode) {
            GroupChatGreetingMode.RandomPerCharacter ->
                GroupChatGreetingSelection.RandomPerCharacter
            GroupChatGreetingMode.Manual -> GroupChatGreetingSelection.Manual(
                characterId = requireNotNull(selectedCharacterId),
                greetingIndex = requireNotNull(selectedGreetingIndex)
            )
            GroupChatGreetingMode.Custom -> GroupChatGreetingSelection.Custom(
                characterId = requireNotNull(selectedCharacterId),
                content = customGreeting
            )
            GroupChatGreetingMode.None -> GroupChatGreetingSelection.None
        }
    }
}
