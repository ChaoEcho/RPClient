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
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.character.CharacterCardRepository
import me.kafuuneko.rpclient.libs.character.CharacterCardImportDraft
import me.kafuuneko.rpclient.libs.character.LorebookImportPolicy
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.prompt.resolveCharacterUserMacros
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.ui.theme.CharacterAccentColors
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 角色列表页状态持有者。
 *
 * 核心职责：
 * - 维护全量角色列表与搜索语料库快照（包含名称、描述、作者附言、性格、场景设定、历史后指令与标签）；
 * - 结合列表视口动态调度头像下采样与异步缩略图加载，维护独立的 LRU 头像位图内存缓存；
 * - 调度角色卡导入流程，拦截并确认内嵌世界书的低 Token 预算策略；
 * - 调度角色卡 JSON 格式的本地导出；
 * - 驱动新建与编辑角色的导航跳转。
 */
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

    /** 初始化角色列表，进入加载中状态并拉取数据库数据。 */
    @UiIntentObserver(CharacterListUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<CharacterListUiState.None>()) return
        CharacterListUiState.Normal(loadState = CharacterListLoadState.Loading).setup()
        refreshCharacters(selectedCharacterId = null)
    }

    /** 页面恢复可见时刷新角色列表数据（导入或导出传输任务进行中时不打断）。 */
    @UiIntentObserver(CharacterListUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        // Activity Result 会早于紧随其后的 onResume；传输任务负责唯一的最终刷新
        if (mTransferJob?.isActive == true) return
        refreshCharacters(selectedCharacterId = uiState.selectedCharacterId)
    }

    /** 处理返回操作，取消未完成的传输作业与头像加载任务并迁移至 Finished 状态。 */
    @UiIntentObserver(CharacterListUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<CharacterListUiState.Finished>()) return
        mRefreshGeneration++
        mTransferJob?.cancel()
        mPendingImport = null
        cancelAvatarLoads()
        CharacterListUiState.finished(uiStateFlow.value).setup()
    }

    /** 搜索关键词变化，重新过滤并渲染列表展示。 */
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
     *
     * @param intent 包含当前可见角色 ID 集合与目标缩略图尺寸像素的意图
     */
    @UiIntentObserver(CharacterListUiIntent.VisibleCharactersChanged::class)
    private fun onVisibleCharactersChanged(
        intent: CharacterListUiIntent.VisibleCharactersChanged
    ) {
        if (!isStateOf<CharacterListUiState.Normal>()) return
        if (intent.targetSizePx !in 1..MAX_THUMBNAIL_DIMENSION) return
        // 计算当前视口内有效的角色 ID 集合
        val visibleIds = intent.characterIds.intersect(mAvatarUuids.keys)
        val sizeChanged = mThumbnailTargetSizePx != intent.targetSizePx
        mVisibleCharacterIds = visibleIds
        mThumbnailTargetSizePx = intent.targetSizePx
        // 尺寸变更时清空视口缓存
        if (sizeChanged) mVisibleAvatars.clear()
        mVisibleAvatars.keys.removeAll { it !in visibleIds }
        // 取消已滑出可视区或尺寸过期的加载协程
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
        // 调度当前可见项的异步缩略图加载并刷新列表
        scheduleVisibleAvatarLoads()
        publishRenderedCharacters()
    }

    /**
     * 选择并打开指定角色的编辑页面。
     *
     * @param intent 包含目标角色 ID 的意图
     */
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

    /** 打开新建角色编辑页面。 */
    @UiIntentObserver(CharacterListUiIntent.CreateCharacter::class)
    private fun onCreateCharacter() {
        if (!isStateOf<CharacterListUiState.Normal>()) return
        AppViewEvent.StartActivity(CharacterEditActivity::class.java).tryEmit()
    }

    /** 触发系统文件选择器以导入角色卡文件。 */
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
     *
     * @param intent 包含角色卡文件 URI 的导入意图
     */
    @UiIntentObserver(CharacterListUiIntent.ImportCharacterCard::class)
    private fun onImportCharacterCard(intent: CharacterListUiIntent.ImportCharacterCard) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        if (uiState.loadState != CharacterListLoadState.None || mTransferJob?.isActive == true) return
        // 生成本次传输的唯一标识 Token
        val token = Any()
        mTransferToken = token
        // 进入加载中状态
        uiState.copy(loadState = CharacterListLoadState.Loading).setup()
        mTransferJob = viewModelScope.launch {
            try {
                // 在 IO 线程解析 URI 对应的角色卡文件（JSON 或 PNG Tavern Card）
                val draft = withContext(Dispatchers.IO) {
                    mCharacterCardRepository.readImportFromUri(intent.uri)
                }
                // 检查内嵌世界书是否包含低于阈值的固定 Token 预算，若有则弹出确认对话框
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
                    // 直接执行角色与关联世界书入库
                    saveImport(draft)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // 导入失败弹出 Toast 提示并刷新列表
                AppViewEvent.PopupToastMessageByResId(R.string.import_character_failed).tryEmit()
                refreshCharacters(selectedCharacterId = uiState.selectedCharacterId)
            } finally {
                // 结束传输状态
                finishTransfer(token)
            }
        }
    }

    /** 用户确认将内嵌世界书改为跟随全局预算并继续导入。 */
    @UiIntentObserver(CharacterListUiIntent.ImportCharacterWithGlobalLorebookBudget::class)
    private fun onImportCharacterWithGlobalLorebookBudget() {
        continuePendingImport(followGlobal = true)
    }

    /** 用户确认保留内嵌世界书原固定预算并继续导入。 */
    @UiIntentObserver(CharacterListUiIntent.ImportCharacterWithOriginalLorebookBudget::class)
    private fun onImportCharacterWithOriginalLorebookBudget() {
        continuePendingImport(followGlobal = false)
    }

    /**
     * 消费一次待确认草稿，应用用户选择的预算策略后再开始事务导入。
     *
     * @param followGlobal 是否将内嵌世界书改为跟随全局预算
     */
    private fun continuePendingImport(followGlobal: Boolean) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        if (uiState.dialogState !is CharacterListDialogState.LowEmbeddedLorebookBudgetConfirm) return
        val draft = mPendingImport ?: return
        mPendingImport = null
        val token = Any()
        mTransferToken = token
        // 关闭弹窗并重置为加载中状态
        uiState.copy(
            loadState = CharacterListLoadState.Loading,
            dialogState = CharacterListDialogState.None
        ).setup()
        mTransferJob = viewModelScope.launch {
            try {
                // 根据用户选择应用预算策略并保存入库
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
                // 结束传输状态
                finishTransfer(token)
            }
        }
    }

    /** 将解析后的角色、头像与关联世界书写入数据库，并弹出成功 Toast。 */
    private suspend fun saveImport(draft: CharacterCardImportDraft) {
        val importedId = withContext(Dispatchers.IO) {
            mCharacterCardRepository.saveImport(draft)
        }
        AppViewEvent.PopupToastMessageByResId(R.string.import_character_success).tryEmit()
        refreshCharacters(selectedCharacterId = importedId)
    }

    /** 准备导出指定角色的 JSON 格式文件，并触发文件创建器。 */
    @UiIntentObserver(CharacterListUiIntent.ExportCharacterJsonClick::class)
    private fun onExportCharacterJsonClick(intent: CharacterListUiIntent.ExportCharacterJsonClick) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        val character = uiState.characters.firstOrNull { it.id == intent.characterId } ?: return
        CharacterListViewEvent.OpenCharacterCardJsonExporter(
            characterId = intent.characterId,
            fileName = "${character.name.ifBlank { "character" }}.json"
        ).tryEmit()
    }

    /**
     * 将指定角色导出为 JSON 格式至目标 URI。
     *
     * @param intent 包含角色 ID 与写入 URI 的意图
     */
    @UiIntentObserver(CharacterListUiIntent.ExportCharacterJson::class)
    private fun onExportCharacterJson(intent: CharacterListUiIntent.ExportCharacterJson) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        if (uiState.loadState != CharacterListLoadState.None || mTransferJob?.isActive == true) return
        val token = Any()
        mTransferToken = token
        // 进入加载中状态
        uiState.copy(loadState = CharacterListLoadState.Loading).setup()
        mTransferJob = viewModelScope.launch {
            try {
                // 在 IO 线程序列化角色卡为 JSON 并写入目标 URI
                withContext(Dispatchers.IO) {
                    mCharacterCardRepository.exportJsonToUri(intent.characterId, intent.uri)
                }
                AppViewEvent.PopupToastMessageByResId(R.string.export_character_success).tryEmit()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.export_character_failed).tryEmit()
            } finally {
                // 结束传输状态
                finishTransfer(token)
            }
        }
    }

    /**
     * 重建角色列表的搜索快照，并使已变更或删除头像对应的异步加载失效。
     *
     * [mRefreshGeneration] 防止较早的数据库查询晚返回后覆盖新列表；头像位图只按当前
     * 可见项延迟加载，不进入完整角色列表状态。
     *
     * @param selectedCharacterId 刷新后需高亮选中的角色 ID（可选）
     */
    private suspend fun refreshCharacters(selectedCharacterId: Long?) {
        if (!isStateOf<CharacterListUiState.Normal>()) return
        val generation = ++mRefreshGeneration
        // 从数据库查询全量角色
        val characters = withContext(Dispatchers.IO) {
            mCharacterRepository.getAllCharacters()
        }
        val userName = AppModel.userName.trim().ifBlank { "You" }
        // 构建 UI 渲染列表项
        val allCharacterItems = characters.map { character ->
            CharacterListItem(
                id = character.id,
                name = character.name,
                description = resolveCharacterUserMacros(
                    template = character.description,
                    characterName = character.name,
                    userName = userName
                ),
                tags = character.getCharacterTagList(),
                avatarText = character.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                avatarColor = CharacterAccentColors[
                    (character.id % CharacterAccentColors.size).toInt()
                ]
            )
        }
        val avatarUuids = characters.associate { it.id to it.avatar }
        // 构建多字段全文检索语料库
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
        // 校验代数防止旧请求覆盖
        if (generation != mRefreshGeneration) return
        val current = getOrNull<CharacterListUiState.Normal>() ?: return
        val previousAvatarUuids = mAvatarUuids
        // 清理已变更或被移除的头像缓存与加载任务
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
        // 更新内存数据集
        mAvatarUuids = avatarUuids
        mVisibleCharacterIds = mVisibleCharacterIds.intersect(avatarUuids.keys)
        mAllCharacterItems = allCharacterItems
        mSearchCorpus = searchCorpus
        // 驱动 UI 状态展示
        current.copy(
            loadState = CharacterListLoadState.None,
            selectedCharacterId = characters.firstOrNull { it.id == selectedCharacterId }?.id,
            characters = renderCharacters(current.searchText)
        ).setup()
        scheduleVisibleAvatarLoads()
    }

    /**
     * 仅允许当前传输任务清理 Loading；Finished 页面不再发布 Normal。
     *
     * @param token 传输任务创建时绑定的唯一标识 Token
     */
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
        // 遍历视口内全部可见角色
        mVisibleCharacterIds.forEach { characterId ->
            val avatarUuid = mAvatarUuids[characterId].orEmpty()
            if (avatarUuid.isBlank()) {
                mVisibleAvatars.remove(characterId)
                cancelAvatarLoad(characterId)
                return@forEach
            }
            val key = AvatarCacheKey(avatarUuid, targetSizePx, targetSizePx)
            if (mVisibleAvatars[characterId]?.key == key) return@forEach
            // 命中内存 LRU 缓存则直接复用
            mAvatarCache.get(key)?.let { cached ->
                mVisibleAvatars[characterId] = LoadedAvatar(key, cached.asImageBitmap())
                return@forEach
            }
            // 若该尺寸已在加载中则跳过
            if (mAvatarLoadKeys[characterId] == key && mAvatarLoadJobs[characterId]?.isActive == true) {
                return@forEach
            }
            // 取消旧任务并启动新的下采样加载协程
            cancelAvatarLoad(characterId)
            val token = Any()
            mAvatarLoadTokens[characterId] = token
            mAvatarLoadKeys[characterId] = key
            mAvatarLoadJobs[characterId] = viewModelScope.launch {
                try {
                    // 在 IO 线程按目标尺寸下采样解码头像位图
                    val bitmap = withContext(Dispatchers.IO) {
                        mFileRepository.loadSampledBitmap(
                            avatarUuid,
                            targetSizePx,
                            targetSizePx
                        )
                    } ?: return@launch
                    // 校验响应时效性与视口状态
                    val isCurrentRequest = mAvatarLoadTokens[characterId] === token &&
                        characterId in mVisibleCharacterIds &&
                        mAvatarUuids[characterId] == avatarUuid &&
                        mThumbnailTargetSizePx == targetSizePx &&
                        isStateOf<CharacterListUiState.Normal>()
                    if (!isCurrentRequest) return@launch
                    // 写入缓存并更新视口位图
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

    /** 发布当前渲染的角色列表至 UI 状态。 */
    private fun publishRenderedCharacters() {
        val current = getOrNull<CharacterListUiState.Normal>() ?: return
        current.copy(characters = renderCharacters(current.searchText)).setup()
    }

    /** 取消指定角色的头像加载任务并移除任务记录。 */
    private fun cancelAvatarLoad(characterId: Long) {
        mAvatarLoadTokens.remove(characterId)
        mAvatarLoadKeys.remove(characterId)
        mAvatarLoadJobs.remove(characterId)?.cancel()
    }

    /** 取消所有正在执行的头像加载任务。 */
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

    /** 根据关键词在搜索语料库中过滤角色。 */
    private fun filterCharacters(query: String): List<CharacterListItem> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return mAllCharacterItems
        return mAllCharacterItems.filter { item ->
            mSearchCorpus[item.id].orEmpty().contains(keyword, ignoreCase = true)
        }
    }

    /** 组合过滤结果与当前已加载的头像 ImageBitmap 生成最终列表项。 */
    private fun renderCharacters(query: String): List<CharacterListItem> {
        return filterCharacters(query).map { item ->
            item.copy(avatarImage = mVisibleAvatars[item.id]?.image)
        }
    }

    /** 头像内存缓存键，由文件 UUID 与宽高像素共同决定。 */
    private data class AvatarCacheKey(
        val avatarUuid: String,
        val widthPx: Int,
        val heightPx: Int
    )

    /** 视口内已完成解码的头像图像对象。 */
    private data class LoadedAvatar(
        val key: AvatarCacheKey,
        val image: ImageBitmap
    )

    /** 针对角色列表头像缩略图定制的 LRU 内存位图缓存容器。 */
    private class AvatarBitmapCache(private val mMaxBytes: Long) {
        private val mEntries = LinkedHashMap<AvatarCacheKey, Bitmap>(16, 0.75f, true)
        private var mSizeBytes: Long = 0L

        /** 从缓存中获取指定尺寸的位图。 */
        fun get(key: AvatarCacheKey): Bitmap? = mEntries[key]

        /** 将解码后的位图存入缓存，并执行内存预算裁剪。 */
        fun put(key: AvatarCacheKey, bitmap: Bitmap) {
            mEntries.put(key, bitmap)?.let { mSizeBytes -= it.byteCount.toLong() }
            mSizeBytes += bitmap.byteCount.toLong()
            trimToBudget()
        }

        /** 移除指定 UUID 对应的所有尺寸位图缓存。 */
        fun removeAvatar(avatarUuid: String) {
            val matchingKeys = mEntries.keys.filter { it.avatarUuid == avatarUuid }
            matchingKeys.forEach { key ->
                mEntries.remove(key)?.let { mSizeBytes -= it.byteCount.toLong() }
            }
        }

        /** 清空全部缓存位图。 */
        fun clear() {
            mEntries.clear()
            mSizeBytes = 0L
        }

        /** 裁剪超出最大字节预算的最久未访问位图项。 */
        private fun trimToBudget() {
            val iterator = mEntries.entries.iterator()
            while (mSizeBytes > mMaxBytes && iterator.hasNext()) {
                val bitmap = iterator.next().value
                mSizeBytes -= bitmap.byteCount.toLong()
                iterator.remove()
            }
        }
    }

    private companion object {
        /** 缩略图最大支持的目标像素尺寸（4096px）。 */
        const val MAX_THUMBNAIL_DIMENSION = 4_096
        /** 头像内存缓存最大字节容量（16MB）。 */
        const val MAX_AVATAR_CACHE_BYTES = 16L * 1024L * 1024L
    }
}
