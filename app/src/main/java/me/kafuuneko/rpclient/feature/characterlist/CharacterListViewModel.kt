package me.kafuuneko.rpclient.feature.characterlist

import android.os.Bundle
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.characterlist.model.CharacterListItem
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListDialogState
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListLoadState
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListUiIntent
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListUiState
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListViewEvent
import me.kafuuneko.rpclient.feature.characteredit.CharacterEditActivity
import me.kafuuneko.rpclient.libs.character.CharacterCardRepository
import me.kafuuneko.rpclient.libs.character.CharacterCardImportDraft
import me.kafuuneko.rpclient.libs.character.LorebookImportPolicy
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.ui.theme.CharacterAccentColors
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 角色列表页状态持有者，协调检索、编辑导航及角色卡导入导出。 */
class CharacterListViewModel : CoreViewModelWithEvent<CharacterListUiIntent, CharacterListUiState>(
    CharacterListUiState.None
), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mCharacterCardRepository by inject<CharacterCardRepository>()
    private var mAllCharacterItems: List<CharacterListItem> = emptyList()
    private var mSearchCorpus: Map<Long, String> = emptyMap()
    private var mTransferJob: Job? = null
    private var mTransferToken: Any? = null
    private var mRefreshGeneration: Long = 0L
    private var mAvatarUuids: Map<Long, String> = emptyMap()
    private var mVisibleCharacterIds: Set<Long> = emptySet()
    private var mThumbnailTargetSizePx: Int = 0
    private val mAvatarLoadJobs = mutableMapOf<Long, Job>()
    private val mAvatarLoadTokens = mutableMapOf<Long, Any>()
    private val mAvatarLoadKeys = mutableMapOf<Long, AvatarCacheKey>()
    private val mVisibleAvatars = mutableMapOf<Long, LoadedAvatar>()
    private val mAvatarCache = AvatarBitmapCache(MAX_AVATAR_CACHE_BYTES)
    private var mPendingImport: CharacterCardImportDraft? = null

    @UiIntentObserver(CharacterListUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<CharacterListUiState.None>()) return
        CharacterListUiState.Normal(loadState = CharacterListLoadState.Loading).setup()
        refreshCharacters(selectedCharacterId = null)
    }

    @UiIntentObserver(CharacterListUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        // Activity Result 会早于紧随其后的 onResume；传输任务负责唯一的最终刷新。
        if (mTransferJob?.isActive == true) return
        refreshCharacters(selectedCharacterId = uiState.selectedCharacterId)
    }

    @UiIntentObserver(CharacterListUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<CharacterListUiState.Finished>()) return
        mRefreshGeneration++
        mTransferJob?.cancel()
        mPendingImport = null
        cancelAvatarLoads()
        CharacterListUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(CharacterListUiIntent.ChangeSearchText::class)
    private fun onChangeSearchText(intent: CharacterListUiIntent.ChangeSearchText) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        uiState.copy(
            searchText = intent.value,
            characters = renderCharacters(intent.value)
        ).setup()
    }

    /**
     * 根据当前可见 ID 和实际渲染尺寸维护头像加载集合。
     *
     * 尺寸变化会使旧位图立即失效；离开可视区或 key 已变化的任务主动取消，
     * 防止快速滚动后过期缩略图回写到被复用的列表项。
     */
    @UiIntentObserver(CharacterListUiIntent.VisibleCharactersChanged::class)
    private fun onVisibleCharactersChanged(
        intent: CharacterListUiIntent.VisibleCharactersChanged
    ) {
        if (!isStateOf<CharacterListUiState.Normal>()) return
        if (intent.targetSizePx !in 1..MAX_THUMBNAIL_DIMENSION) return
        val visibleIds = intent.characterIds.intersect(mAvatarUuids.keys)
        val sizeChanged = mThumbnailTargetSizePx != intent.targetSizePx
        mVisibleCharacterIds = visibleIds
        mThumbnailTargetSizePx = intent.targetSizePx
        if (sizeChanged) mVisibleAvatars.clear()
        mVisibleAvatars.keys.removeAll { it !in visibleIds }
        mAvatarLoadJobs.keys.toList().forEach { characterId ->
            val uuid = mAvatarUuids[characterId]
            val expectedKey = uuid?.let {
                AvatarCacheKey(it, intent.targetSizePx, intent.targetSizePx)
            }
            val shouldCancel = characterId !in visibleIds ||
                mVisibleAvatars[characterId]?.key?.let { it != expectedKey } == true ||
                sizeChanged
            if (shouldCancel) cancelAvatarLoad(characterId)
        }
        scheduleVisibleAvatarLoads()
        publishRenderedCharacters()
    }

    @UiIntentObserver(CharacterListUiIntent.SelectCharacter::class)
    private fun onSelectCharacter(intent: CharacterListUiIntent.SelectCharacter) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        if (uiState.characters.none { it.id == intent.characterId }) return
        uiState.copy(selectedCharacterId = intent.characterId).setup()
        AppViewEvent.StartActivity(
            activity = CharacterEditActivity::class.java,
            extras = Bundle().apply {
                putLong(CharacterEditActivity.EXTRA_CHARACTER_ID, intent.characterId)
            }
        ).tryEmit()
    }

    @UiIntentObserver(CharacterListUiIntent.CreateCharacter::class)
    private fun onCreateCharacter() {
        if (!isStateOf<CharacterListUiState.Normal>()) return
        AppViewEvent.StartActivity(CharacterEditActivity::class.java).tryEmit()
    }

    @UiIntentObserver(CharacterListUiIntent.ImportCharacterClick::class)
    private fun onImportCharacterClick() {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        if (uiState.dialogState != CharacterListDialogState.None) return
        CharacterListViewEvent.OpenCharacterCardImporter.tryEmit()
    }

    /**
     * 解析角色卡并在需要时暂停于内嵌世界书预算确认。
     *
     * 确认前只保存进程内草稿，不写入角色、头像或世界书；传输 token 保证被替换或页面结束
     * 的旧任务不能清除新任务的 Loading 状态。
     */
    @UiIntentObserver(CharacterListUiIntent.ImportCharacterCard::class)
    private fun onImportCharacterCard(intent: CharacterListUiIntent.ImportCharacterCard) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        if (uiState.loadState != CharacterListLoadState.None || mTransferJob?.isActive == true) return
        val token = Any()
        mTransferToken = token
        uiState.copy(loadState = CharacterListLoadState.Loading).setup()
        mTransferJob = viewModelScope.launch {
            try {
                val draft = withContext(Dispatchers.IO) {
                    mCharacterCardRepository.readImportFromUri(intent.uri)
                }
                if (LorebookImportPolicy.requiresLowBudgetConfirmation(draft.card)) {
                    mPendingImport = draft
                    getOrNull<CharacterListUiState.Normal>()?.copy(
                        loadState = CharacterListLoadState.None,
                        dialogState = CharacterListDialogState.LowEmbeddedLorebookBudgetConfirm(
                            importedTokenBudget = requireNotNull(
                                draft.card.embeddedLorebook
                            ).lorebook.tokenBudget
                        )
                    )?.setup()
                } else {
                    saveImport(draft)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.import_character_failed).tryEmit()
                refreshCharacters(selectedCharacterId = uiState.selectedCharacterId)
            } finally {
                finishTransfer(token)
            }
        }
    }

    @UiIntentObserver(CharacterListUiIntent.ImportCharacterWithGlobalLorebookBudget::class)
    private fun onImportCharacterWithGlobalLorebookBudget() {
        continuePendingImport(followGlobal = true)
    }

    @UiIntentObserver(CharacterListUiIntent.ImportCharacterWithOriginalLorebookBudget::class)
    private fun onImportCharacterWithOriginalLorebookBudget() {
        continuePendingImport(followGlobal = false)
    }

    /** 消费一次待确认草稿，应用用户选择的预算策略后再开始事务导入。 */
    private fun continuePendingImport(followGlobal: Boolean) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        if (uiState.dialogState !is CharacterListDialogState.LowEmbeddedLorebookBudgetConfirm) return
        val draft = mPendingImport ?: return
        mPendingImport = null
        val token = Any()
        mTransferToken = token
        uiState.copy(
            loadState = CharacterListLoadState.Loading,
            dialogState = CharacterListDialogState.None
        ).setup()
        mTransferJob = viewModelScope.launch {
            try {
                saveImport(
                    draft.copy(
                        card = LorebookImportPolicy.resolveBudget(draft.card, followGlobal)
                    )
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.import_character_failed).tryEmit()
                refreshCharacters(selectedCharacterId = uiState.selectedCharacterId)
            } finally {
                finishTransfer(token)
            }
        }
    }

    private suspend fun saveImport(draft: CharacterCardImportDraft) {
        val importedId = withContext(Dispatchers.IO) {
            mCharacterCardRepository.saveImport(draft)
        }
        AppViewEvent.PopupToastMessageByResId(R.string.import_character_success).tryEmit()
        refreshCharacters(selectedCharacterId = importedId)
    }

    @UiIntentObserver(CharacterListUiIntent.ExportCharacterJsonClick::class)
    private fun onExportCharacterJsonClick(intent: CharacterListUiIntent.ExportCharacterJsonClick) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        val character = uiState.characters.firstOrNull { it.id == intent.characterId } ?: return
        CharacterListViewEvent.OpenCharacterCardJsonExporter(
            characterId = intent.characterId,
            fileName = "${character.name.ifBlank { "character" }}.json"
        ).tryEmit()
    }

    @UiIntentObserver(CharacterListUiIntent.ExportCharacterJson::class)
    private fun onExportCharacterJson(intent: CharacterListUiIntent.ExportCharacterJson) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        if (uiState.loadState != CharacterListLoadState.None || mTransferJob?.isActive == true) return
        val token = Any()
        mTransferToken = token
        uiState.copy(loadState = CharacterListLoadState.Loading).setup()
        mTransferJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    mCharacterCardRepository.exportJsonToUri(intent.characterId, intent.uri)
                }
                AppViewEvent.PopupToastMessageByResId(R.string.export_character_success).tryEmit()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.export_character_failed).tryEmit()
            } finally {
                finishTransfer(token)
            }
        }
    }

    /**
     * 重建角色列表的搜索快照，并使已变更或删除头像对应的异步加载失效。
     *
     * [mRefreshGeneration] 防止较早的数据库查询晚返回后覆盖新列表；头像位图只按当前
     * 可见项延迟加载，不进入完整角色列表状态。
     */
    private suspend fun refreshCharacters(selectedCharacterId: Long?) {
        if (!isStateOf<CharacterListUiState.Normal>()) return
        val generation = ++mRefreshGeneration
        val characters = withContext(Dispatchers.IO) {
            mCharacterRepository.getAllCharacters()
        }
        val allCharacterItems = characters.map { character ->
            CharacterListItem(
                id = character.id,
                name = character.name,
                description = character.description,
                tags = character.getCharacterTagList(),
                avatarText = character.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                avatarColor = CharacterAccentColors[
                    (character.id % CharacterAccentColors.size).toInt()
                ]
            )
        }
        val avatarUuids = characters.associate { it.id to it.avatar }
        val searchCorpus = characters.associate { character ->
            character.id to listOf(
                character.name,
                character.description,
                character.creatorNotes,
                character.personality,
                character.scenario,
                character.postHistoryInstructions,
                character.getCharacterTagList().joinToString(" ")
            ).joinToString("\n")
        }
        if (generation != mRefreshGeneration) return
        val current = getOrNull<CharacterListUiState.Normal>() ?: return
        val previousAvatarUuids = mAvatarUuids
        avatarUuids.forEach { (characterId, uuid) ->
            val previousUuid = previousAvatarUuids[characterId]
            if (previousUuid != null && previousUuid != uuid) {
                cancelAvatarLoad(characterId)
                mVisibleAvatars.remove(characterId)
                mAvatarCache.removeAvatar(previousUuid)
            }
        }
        (previousAvatarUuids.keys - avatarUuids.keys).forEach { characterId ->
            cancelAvatarLoad(characterId)
            mVisibleAvatars.remove(characterId)
            previousAvatarUuids[characterId]?.let(mAvatarCache::removeAvatar)
        }
        mAvatarUuids = avatarUuids
        mVisibleCharacterIds = mVisibleCharacterIds.intersect(avatarUuids.keys)
        mAllCharacterItems = allCharacterItems
        mSearchCorpus = searchCorpus
        current.copy(
            loadState = CharacterListLoadState.None,
            selectedCharacterId = characters.firstOrNull { it.id == selectedCharacterId }?.id,
            characters = renderCharacters(current.searchText)
        ).setup()
        scheduleVisibleAvatarLoads()
    }

    /** 仅允许当前传输任务清理 Loading；Finished 页面不再发布 Normal。 */
    private fun finishTransfer(token: Any) {
        if (mTransferToken !== token) return
        mTransferToken = null
        mTransferJob = null
        val current = getOrNull<CharacterListUiState.Normal>() ?: return
        if (current.loadState == CharacterListLoadState.Loading) {
            current.copy(loadState = CharacterListLoadState.None).setup()
        }
    }

    /** 只为当前可见且尚未命中缓存的角色启动缩略图任务。 */
    private fun scheduleVisibleAvatarLoads() {
        val targetSizePx = mThumbnailTargetSizePx
        if (targetSizePx !in 1..MAX_THUMBNAIL_DIMENSION) return
        mVisibleCharacterIds.forEach { characterId ->
            val avatarUuid = mAvatarUuids[characterId].orEmpty()
            if (avatarUuid.isBlank()) {
                mVisibleAvatars.remove(characterId)
                cancelAvatarLoad(characterId)
                return@forEach
            }
            val key = AvatarCacheKey(avatarUuid, targetSizePx, targetSizePx)
            if (mVisibleAvatars[characterId]?.key == key) return@forEach
            mAvatarCache.get(key)?.let { cached ->
                mVisibleAvatars[characterId] = LoadedAvatar(key, cached.asImageBitmap())
                return@forEach
            }
            if (mAvatarLoadKeys[characterId] == key && mAvatarLoadJobs[characterId]?.isActive == true) {
                return@forEach
            }
            cancelAvatarLoad(characterId)
            val token = Any()
            mAvatarLoadTokens[characterId] = token
            mAvatarLoadKeys[characterId] = key
            mAvatarLoadJobs[characterId] = viewModelScope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        mFileRepository.loadSampledBitmap(
                            avatarUuid,
                            targetSizePx,
                            targetSizePx
                        )
                    } ?: return@launch
                    val isCurrentRequest = mAvatarLoadTokens[characterId] === token &&
                        characterId in mVisibleCharacterIds &&
                        mAvatarUuids[characterId] == avatarUuid &&
                        mThumbnailTargetSizePx == targetSizePx &&
                        isStateOf<CharacterListUiState.Normal>()
                    if (!isCurrentRequest) return@launch
                    mAvatarCache.put(key, bitmap)
                    mVisibleAvatars[characterId] = LoadedAvatar(key, bitmap.asImageBitmap())
                    publishRenderedCharacters()
                } finally {
                    if (mAvatarLoadTokens[characterId] === token) {
                        mAvatarLoadTokens.remove(characterId)
                        mAvatarLoadKeys.remove(characterId)
                        mAvatarLoadJobs.remove(characterId)
                    }
                }
            }
        }
    }

    private fun publishRenderedCharacters() {
        val current = getOrNull<CharacterListUiState.Normal>() ?: return
        current.copy(characters = renderCharacters(current.searchText)).setup()
    }

    private fun cancelAvatarLoad(characterId: Long) {
        mAvatarLoadTokens.remove(characterId)
        mAvatarLoadKeys.remove(characterId)
        mAvatarLoadJobs.remove(characterId)?.cancel()
    }

    private fun cancelAvatarLoads() {
        mAvatarLoadJobs.values.forEach(Job::cancel)
        mAvatarLoadJobs.clear()
        mAvatarLoadTokens.clear()
        mAvatarLoadKeys.clear()
    }

    override fun onCleared() {
        cancelAvatarLoads()
        mVisibleAvatars.clear()
        mAvatarCache.clear()
        super.onCleared()
    }

    private fun filterCharacters(query: String): List<CharacterListItem> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return mAllCharacterItems
        return mAllCharacterItems.filter { item ->
            mSearchCorpus[item.id].orEmpty().contains(keyword, ignoreCase = true)
        }
    }

    private fun renderCharacters(query: String): List<CharacterListItem> {
        return filterCharacters(query).map { item ->
            item.copy(avatarImage = mVisibleAvatars[item.id]?.image)
        }
    }

    private data class AvatarCacheKey(
        val avatarUuid: String,
        val widthPx: Int,
        val heightPx: Int
    )

    private data class LoadedAvatar(
        val key: AvatarCacheKey,
        val image: ImageBitmap
    )

    private class AvatarBitmapCache(private val maxBytes: Long) {
        private val entries = LinkedHashMap<AvatarCacheKey, Bitmap>(16, 0.75f, true)
        private var sizeBytes: Long = 0L

        fun get(key: AvatarCacheKey): Bitmap? = entries[key]

        fun put(key: AvatarCacheKey, bitmap: Bitmap) {
            entries.put(key, bitmap)?.let { sizeBytes -= it.byteCount.toLong() }
            sizeBytes += bitmap.byteCount.toLong()
            trimToBudget()
        }

        fun removeAvatar(avatarUuid: String) {
            val matchingKeys = entries.keys.filter { it.avatarUuid == avatarUuid }
            matchingKeys.forEach { key ->
                entries.remove(key)?.let { sizeBytes -= it.byteCount.toLong() }
            }
        }

        fun clear() {
            entries.clear()
            sizeBytes = 0L
        }

        private fun trimToBudget() {
            val iterator = entries.entries.iterator()
            while (sizeBytes > maxBytes && iterator.hasNext()) {
                val bitmap = iterator.next().value
                sizeBytes -= bitmap.byteCount.toLong()
                iterator.remove()
            }
        }
    }

    private companion object {
        const val MAX_THUMBNAIL_DIMENSION = 4_096
        const val MAX_AVATAR_CACHE_BYTES = 16L * 1024L * 1024L
    }
}
