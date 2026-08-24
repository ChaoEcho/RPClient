package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryChapter
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.entity.StoryLorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.StoryVolume
import me.kafuuneko.rpclient.libs.room.model.StoryChapterOverview
import me.kafuuneko.rpclient.libs.room.model.StoryOverview
import me.kafuuneko.rpclient.libs.story.storyTextHash

/** Story 角色关联的领域聚合数据。 */
data class StoryCharacterCandidate(
    val relation: StoryCharacter,
    val character: Character
)

/** Story 世界书条目关联与原始条目的领域聚合数据。 */
data class StoryLorebookEntryCandidate(
    val relation: StoryLorebookEntry,
    val entry: LorebookEntry
)

/** 用户保存的一条 Story 角色关联配置。 */
data class StoryCharacterSelection(
    val characterId: Long,
    val activationMode: Int
)

/** 用户保存的一条 Story 世界书关联配置。 */
data class StoryLorebookEntrySelection(val lorebookEntryId: Long)

/** 可用于生成提交、撤销和恢复的 Story 条目级世界书状态快照。 */
data class StoryLorebookRuntimeState(
    val lorebookEntryId: Long,
    val activatedAtStep: Int? = null,
    val stickyUntilStep: Int? = null,
    val cooldownUntilStep: Int? = null,
    val stateSignature: String? = null
)

/** 编辑器初始化所需的 Story、轻量结构和当前完整章节。 */
data class StoryEditorData(
    val story: Story,
    val volumes: List<StoryVolume>,
    val chapters: List<StoryChapterOverview>,
    val currentChapter: StoryChapter
)

/** 一次章节正文保存后的两级新 revision。 */
data class StoryChapterWriteResult(
    val storyRevision: Long,
    val chapterRevision: Long
)

/** 删除章节后必须立即切换到的相邻章节。 */
data class StoryChapterDeleteResult(
    val deletedChapterId: Long,
    val fallbackChapterId: Long
)

/** 等待通过 Story、章节、原文和世界书快照校验后原子应用的 AI 修改。 */
data class StoryGeneratedEdit(
    val storyId: Long,
    val chapterId: Long,
    val baseStoryRevision: Long,
    val baseChapterRevision: Long,
    val start: Int,
    val end: Int,
    val originalTextHash: String,
    val result: String,
    val nextWorldInfoStates: List<StoryLorebookRuntimeState>,
    val nextWorldInfoGenerationStep: Int? = null
)

/** 已原子应用的章节正文、两级 revision 与 Story 世界书状态。 */
data class StoryAppliedEdit(
    val content: String,
    val storyRevision: Long,
    val chapterRevision: Long,
    val worldInfoGenerationStep: Int,
    val worldInfoStates: List<StoryLorebookRuntimeState>
)

/**
 * Story 聚合仓库。
 *
 * 正文以章节为加载和保存边界；分卷只负责结构分组。所有会改变 Story 聚合的写入都在事务中
 * 递增 [Story.revision]，AI 提交因此能拒绝基于旧设置、旧结构或旧世界书时序构建的结果。
 */
class StoryRepository(private val mAppDatabase: AppDatabase) {
    private val mStoryDao = mAppDatabase.getStoryDao()
    private val mStoryVolumeDao = mAppDatabase.getStoryVolumeDao()
    private val mStoryChapterDao = mAppDatabase.getStoryChapterDao()
    private val mStoryCharacterDao = mAppDatabase.getStoryCharacterDao()
    private val mStoryLorebookEntryDao = mAppDatabase.getStoryLorebookEntryDao()
    private val mCharacterDao = mAppDatabase.getCharacterDao()
    private val mLorebookEntryDao = mAppDatabase.getLorebookEntryDao()

    suspend fun getStoryOverviews(): List<StoryOverview> = mStoryDao.getStoryOverviews()

    suspend fun getStory(id: Long): Story? = mStoryDao.getStory(id)

    /** 加载轻量大纲，并只读取一个用户将要编辑的完整章节。 */
    suspend fun getStoryEditorData(
        storyId: Long,
        preferredChapterId: Long? = null
    ): StoryEditorData? = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction null
        val volumes = mStoryVolumeDao.getByStoryId(storyId)
        val chapters = mStoryChapterDao.getOverviewsByStoryId(storyId)
        val preferred = preferredChapterId
            ?.let { mStoryChapterDao.getById(it) }
            ?.takeIf { it.storyId == storyId }
        val current = preferred ?: mStoryChapterDao.getLatestByStoryId(storyId)
            ?: return@withTransaction null
        StoryEditorData(story, volumes, chapters, current)
    }

    suspend fun getChapter(storyId: Long, chapterId: Long): StoryChapter? {
        return mStoryChapterDao.getById(chapterId)?.takeIf { it.storyId == storyId }
    }

    suspend fun getStoryCharacterCandidates(
        storyId: Long
    ): List<StoryCharacterCandidate> = mAppDatabase.withTransaction {
        mStoryCharacterDao.getByStoryId(storyId).mapNotNull { relation ->
            mCharacterDao.getCharacterById(relation.characterId)?.let { character ->
                StoryCharacterCandidate(relation, character)
            }
        }
    }

    suspend fun getStoryLorebookEntryCandidates(
        storyId: Long
    ): List<StoryLorebookEntryCandidate> = mAppDatabase.withTransaction {
        mStoryLorebookEntryDao.getByStoryId(storyId).mapNotNull { relation ->
            mLorebookEntryDao.getEntryById(relation.lorebookEntryId)?.let { entry ->
                StoryLorebookEntryCandidate(relation, entry)
            }
        }
    }

    suspend fun getStoryLorebookRuntimeStates(
        storyId: Long
    ): List<StoryLorebookRuntimeState> {
        return mStoryLorebookEntryDao.getByStoryId(storyId).map { it.toRuntimeState() }
    }

    suspend fun createStory(
        title: String,
        createTime: Long = System.currentTimeMillis()
    ): Long = createStoryWithConfiguration(
        title = title,
        lorebookSelections = emptyList(),
        characterSelections = emptyList(),
        createTime = createTime
    )

    /** 创建 Story 时在同一事务中建立唯一默认章节和引用配置。 */
    suspend fun createStoryWithConfiguration(
        title: String,
        lorebookSelections: List<StoryLorebookEntrySelection>,
        characterSelections: List<StoryCharacterSelection>,
        includeUserPersona: Boolean = false,
        initialChapterTitle: String = DEFAULT_CHAPTER_TITLE,
        createTime: Long = System.currentTimeMillis()
    ): Long = mAppDatabase.withTransaction {
        val normalizedTitle = requireTitle(title, "Story title cannot be blank")
        val normalizedChapterTitle = requireTitle(
            initialChapterTitle,
            "Story chapter title cannot be blank"
        )
        val configuration = normalizeAndValidateConfiguration(
            lorebookSelections,
            characterSelections
        )
        val storyId = mStoryDao.insertOrReplace(
            Story(
                title = normalizedTitle,
                includeUserPersona = includeUserPersona,
                createTime = createTime,
                latestTime = createTime
            )
        )
        mStoryChapterDao.insert(
            StoryChapter(
                storyId = storyId,
                title = normalizedChapterTitle,
                sortOrder = 0,
                createTime = createTime,
                latestTime = createTime
            )
        )
        insertStoryCharacters(storyId, configuration.characterSelections)
        insertStoryLorebookEntries(storyId, configuration.lorebookSelections)
        storyId
    }

    suspend fun renameStory(
        id: Long,
        title: String,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(id) ?: return@withTransaction false
        val normalizedTitle = requireTitle(title, "Story title cannot be blank")
        mStoryDao.renameStory(id, story.revision, normalizedTitle, latestTime) == 1
    }

    suspend fun createVolume(
        storyId: Long,
        title: String,
        latestTime: Long = System.currentTimeMillis()
    ): Long = mAppDatabase.withTransaction {
        val story = requireStory(storyId)
        val id = mStoryVolumeDao.insert(
            StoryVolume(
                storyId = storyId,
                title = requireTitle(title, "Story volume title cannot be blank"),
                sortOrder = mStoryVolumeDao.getByStoryId(storyId).size
            )
        )
        advanceStory(story, latestTime)
        id
    }

    suspend fun renameVolume(
        storyId: Long,
        volumeId: Long,
        title: String,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val volume = mStoryVolumeDao.getById(volumeId)
            ?.takeIf { it.storyId == storyId }
            ?: return@withTransaction false
        check(
            mStoryVolumeDao.rename(
                volume.id,
                storyId,
                requireTitle(title, "Story volume title cannot be blank")
            ) == 1
        )
        advanceStory(story, latestTime)
        true
    }

    suspend fun moveVolume(
        storyId: Long,
        volumeId: Long,
        offset: Int,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val volumes = mStoryVolumeDao.getByStoryId(storyId)
        val from = volumes.indexOfFirst { it.id == volumeId }
        val to = from + offset
        if (from < 0 || to !in volumes.indices) return@withTransaction false
        check(mStoryVolumeDao.updateSortOrder(volumes[from].id, storyId, to) == 1)
        check(mStoryVolumeDao.updateSortOrder(volumes[to].id, storyId, from) == 1)
        advanceStory(story, latestTime)
        true
    }

    /** 删除分卷只解除结构分组，卷内章节按原顺序追加到未分卷区域。 */
    suspend fun deleteVolume(
        storyId: Long,
        volumeId: Long,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val volume = mStoryVolumeDao.getById(volumeId)
            ?.takeIf { it.storyId == storyId }
            ?: return@withTransaction false
        val ungroupedCount = mStoryChapterDao.getByContainer(storyId, null).size
        mStoryChapterDao.getByContainer(storyId, volumeId).forEachIndexed { index, chapter ->
            check(
                mStoryChapterDao.updateLocation(
                    chapter.id,
                    storyId,
                    null,
                    ungroupedCount + index
                ) == 1
            )
        }
        check(mStoryVolumeDao.deleteById(volume.id, storyId) == 1)
        normalizeVolumeOrder(storyId)
        advanceStory(story, latestTime)
        true
    }

    suspend fun createChapter(
        storyId: Long,
        volumeId: Long?,
        title: String,
        latestTime: Long = System.currentTimeMillis()
    ): Long = mAppDatabase.withTransaction {
        val story = requireStory(storyId)
        validateVolume(storyId, volumeId)
        val id = mStoryChapterDao.insert(
            StoryChapter(
                storyId = storyId,
                volumeId = volumeId,
                title = requireTitle(title, "Story chapter title cannot be blank"),
                sortOrder = mStoryChapterDao.getByContainer(storyId, volumeId).size,
                createTime = latestTime,
                latestTime = latestTime
            )
        )
        advanceStory(story, latestTime)
        id
    }

    suspend fun renameChapter(
        storyId: Long,
        chapterId: Long,
        title: String,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val chapter = mStoryChapterDao.getById(chapterId)
            ?.takeIf { it.storyId == storyId }
            ?: return@withTransaction false
        check(
            mStoryChapterDao.rename(
                chapter.id,
                storyId,
                requireTitle(title, "Story chapter title cannot be blank"),
                latestTime
            ) == 1
        )
        advanceStory(story, latestTime)
        true
    }

    suspend fun moveChapter(
        storyId: Long,
        chapterId: Long,
        offset: Int,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val chapter = mStoryChapterDao.getById(chapterId)
            ?.takeIf { it.storyId == storyId }
            ?: return@withTransaction false
        val siblings = mStoryChapterDao.getByContainer(storyId, chapter.volumeId)
        val from = siblings.indexOfFirst { it.id == chapterId }
        val to = from + offset
        if (from < 0 || to !in siblings.indices) return@withTransaction false
        check(mStoryChapterDao.updateSortOrder(siblings[from].id, storyId, to) == 1)
        check(mStoryChapterDao.updateSortOrder(siblings[to].id, storyId, from) == 1)
        advanceStory(story, latestTime)
        true
    }

    suspend fun moveChapterToVolume(
        storyId: Long,
        chapterId: Long,
        volumeId: Long?,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val chapter = mStoryChapterDao.getById(chapterId)
            ?.takeIf { it.storyId == storyId }
            ?: return@withTransaction false
        validateVolume(storyId, volumeId)
        if (chapter.volumeId == volumeId) return@withTransaction true
        val targetOrder = mStoryChapterDao.getByContainer(storyId, volumeId).size
        check(mStoryChapterDao.updateLocation(chapterId, storyId, volumeId, targetOrder) == 1)
        normalizeChapterOrder(storyId, chapter.volumeId)
        advanceStory(story, latestTime)
        true
    }

    suspend fun deleteChapter(
        storyId: Long,
        chapterId: Long,
        latestTime: Long = System.currentTimeMillis()
    ): StoryChapterDeleteResult? = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction null
        val ordered = mStoryChapterDao.getOverviewsByStoryId(storyId)
        require(ordered.size > 1) { "Story must retain at least one chapter" }
        val index = ordered.indexOfFirst { it.id == chapterId }
        if (index < 0) return@withTransaction null
        val chapter = mStoryChapterDao.getById(chapterId) ?: return@withTransaction null
        val fallback = ordered.getOrNull(index - 1) ?: ordered[index + 1]
        check(mStoryChapterDao.deleteById(chapterId, storyId) == 1)
        normalizeChapterOrder(storyId, chapter.volumeId)
        advanceStory(story, latestTime)
        StoryChapterDeleteResult(chapterId, fallback.id)
    }

    /** 保存一个章节，并与 Story 聚合 revision 在同一事务中推进。 */
    suspend fun updateChapterContent(
        storyId: Long,
        chapterId: Long,
        expectedChapterRevision: Long,
        content: String,
        latestTime: Long = System.currentTimeMillis()
    ): StoryChapterWriteResult? = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction null
        val chapter = mStoryChapterDao.getById(chapterId)
            ?.takeIf { it.storyId == storyId && it.contentRevision == expectedChapterRevision }
            ?: return@withTransaction null
        if (
            mStoryChapterDao.updateContent(
                chapter.id,
                storyId,
                expectedChapterRevision,
                content,
                latestTime
            ) != 1
        ) return@withTransaction null
        advanceStory(story, latestTime)
        StoryChapterWriteResult(story.revision + 1L, chapter.contentRevision + 1L)
    }

    /** 校验 Story、章节和世界书快照后，原子提交 AI 结果。 */
    suspend fun applyGeneratedEdit(edit: StoryGeneratedEdit): StoryAppliedEdit? {
        return mAppDatabase.withTransaction {
            val snapshots = validateGeneratedEdit(edit) ?: return@withTransaction null
            val story = snapshots.first
            val chapter = snapshots.second
            val content = chapter.content.replaceRange(edit.start, edit.end, edit.result)
            val nextStep = edit.nextWorldInfoGenerationStep
                ?: (story.worldInfoGenerationStep + 1)
            check(
                mStoryChapterDao.updateContent(
                    chapter.id,
                    story.id,
                    chapter.contentRevision,
                    content,
                    System.currentTimeMillis()
                ) == 1
            )
            check(
                mStoryDao.updateGenerationState(
                    story.id,
                    story.revision,
                    nextStep,
                    System.currentTimeMillis()
                ) == 1
            )
            val currentRelations = mStoryLorebookEntryDao.getByStoryId(story.id)
            val nextRelations = currentRelations.withRuntimeStates(edit.nextWorldInfoStates)
            updateLorebookRuntimeStates(nextRelations)
            StoryAppliedEdit(
                content = content,
                storyRevision = story.revision + 1L,
                chapterRevision = chapter.contentRevision + 1L,
                worldInfoGenerationStep = nextStep,
                worldInfoStates = nextRelations.map { it.toRuntimeState() }
            )
        }
    }

    /** 会话内撤销当前章节修改，并恢复应用前的 Story 世界书状态。 */
    suspend fun revertGeneratedEdit(
        storyId: Long,
        chapterId: Long,
        expectedStoryRevision: Long,
        expectedChapterRevision: Long,
        start: Int,
        insertedText: String,
        replacedText: String,
        previousWorldInfoStates: List<StoryLorebookRuntimeState>,
        previousWorldInfoGenerationStep: Int
    ): StoryAppliedEdit? = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId)
            ?.takeIf { it.revision == expectedStoryRevision }
            ?: return@withTransaction null
        val chapter = mStoryChapterDao.getById(chapterId)
            ?.takeIf { it.storyId == storyId && it.contentRevision == expectedChapterRevision }
            ?: return@withTransaction null
        val end = start + insertedText.length
        if (start !in 0..end || end > chapter.content.length) return@withTransaction null
        if (storyTextHash(chapter.content.substring(start, end)) != storyTextHash(insertedText)) {
            return@withTransaction null
        }
        val relations = mStoryLorebookEntryDao.getByStoryId(storyId)
        if (!relations.matches(previousWorldInfoStates)) return@withTransaction null
        val content = chapter.content.replaceRange(start, end, replacedText)
        check(
            mStoryChapterDao.updateContent(
                chapter.id,
                storyId,
                chapter.contentRevision,
                content,
                System.currentTimeMillis()
            ) == 1
        )
        check(
            mStoryDao.updateGenerationState(
                storyId,
                story.revision,
                previousWorldInfoGenerationStep,
                System.currentTimeMillis()
            ) == 1
        )
        val previousRelations = relations.withRuntimeStates(previousWorldInfoStates)
        updateLorebookRuntimeStates(previousRelations)
        StoryAppliedEdit(
            content = content,
            storyRevision = story.revision + 1L,
            chapterRevision = chapter.contentRevision + 1L,
            worldInfoGenerationStep = previousWorldInfoGenerationStep,
            worldInfoStates = previousRelations.map { it.toRuntimeState() }
        )
    }

    /** 保存 Story 级上下文和引用配置，并保留仍启用条目的时序状态。 */
    suspend fun updateStoryConfiguration(
        storyId: Long,
        memory: String,
        summary: String,
        authorNote: String,
        includeUserPersona: Boolean = false,
        lorebookSelections: List<StoryLorebookEntrySelection>,
        characterSelections: List<StoryCharacterSelection>,
        latestTime: Long = System.currentTimeMillis()
    ): Long = mAppDatabase.withTransaction {
        val story = requireStory(storyId)
        val configuration = normalizeAndValidateConfiguration(
            lorebookSelections,
            characterSelections
        )
        // 更新故事基础设定
        check(
            mStoryDao.updateStorySettings(
                id = storyId,
                expectedRevision = story.revision,
                memory = memory,
                summary = summary,
                authorNote = authorNote,
                includeUserPersona = includeUserPersona,
                latestTime = latestTime
            ) == 1
        ) { "Story settings update failed" }
        // 重新写入故事关联角色列表
        val previousEntries = mStoryLorebookEntryDao.getByStoryId(storyId)
            .associateBy { it.lorebookEntryId }
        mStoryCharacterDao.deleteByStoryId(storyId)
        insertStoryCharacters(storyId, configuration.characterSelections)
        // 重新写入故事关联世界书条目并保留原有条目的时序状态
        mStoryLorebookEntryDao.deleteByStoryId(storyId)
        val nextEntries = configuration.lorebookSelections.map { selection ->
            previousEntries[selection.lorebookEntryId] ?: StoryLorebookEntry(
                storyId = storyId,
                lorebookEntryId = selection.lorebookEntryId
            )
        }
        if (nextEntries.isNotEmpty()) mStoryLorebookEntryDao.insertAll(nextEntries)
        story.revision + 1L
    }

    /** 仅在故事与当前章节仍匹配生成快照时保存 Story 级滚动摘要。 */
    suspend fun saveGeneratedSummary(
        storyId: Long,
        chapterId: Long,
        expectedStoryRevision: Long,
        expectedChapterRevision: Long,
        content: String,
        latestTime: Long = System.currentTimeMillis()
    ): Long? = mAppDatabase.withTransaction {
        if (content.isBlank()) return@withTransaction null
        val story = mStoryDao.getStory(storyId)
            ?.takeIf { it.revision == expectedStoryRevision }
            ?: return@withTransaction null
        val chapter = mStoryChapterDao.getById(chapterId)
            ?.takeIf { it.storyId == storyId && it.contentRevision == expectedChapterRevision }
            ?: return@withTransaction null
        if (
            mStoryDao.updateSummary(
                storyId,
                story.revision,
                content,
                latestTime
            ) != 1
        ) return@withTransaction null
        story.revision + 1L
    }

    suspend fun deleteStory(id: Long) {
        mStoryDao.deleteStory(id)
    }

    private suspend fun validateGeneratedEdit(
        edit: StoryGeneratedEdit
    ): Pair<Story, StoryChapter>? {
        val story = mStoryDao.getStory(edit.storyId)
            ?.takeIf { it.revision == edit.baseStoryRevision }
            ?: return null
        val chapter = mStoryChapterDao.getById(edit.chapterId)
            ?.takeIf {
                it.storyId == edit.storyId && it.contentRevision == edit.baseChapterRevision
            }
            ?: return null
        if (edit.start !in 0..edit.end || edit.end > chapter.content.length) return null
        if (
            storyTextHash(chapter.content.substring(edit.start, edit.end)) != edit.originalTextHash
        ) return null
        val relations = mStoryLorebookEntryDao.getByStoryId(edit.storyId)
        if (!relations.matches(edit.nextWorldInfoStates)) return null
        return story to chapter
    }

    private suspend fun normalizeAndValidateConfiguration(
        lorebookSelections: List<StoryLorebookEntrySelection>,
        characterSelections: List<StoryCharacterSelection>
    ): NormalizedStoryConfiguration {
        require(characterSelections.distinctBy { it.characterId }.size == characterSelections.size) {
            "Story character selections must be unique"
        }
        require(
            characterSelections.count {
                it.activationMode == StoryCharacter.ACTIVATION_PRIMARY
            } <= 1
        ) { "Story can only have one primary character" }
        characterSelections.forEach { selection ->
            require(StoryCharacter.isValidActivationMode(selection.activationMode)) {
                "Unsupported character activation mode"
            }
            requireNotNull(mCharacterDao.getCharacterById(selection.characterId)) {
                "Character does not exist"
            }
        }
        require(lorebookSelections.distinctBy { it.lorebookEntryId }.size == lorebookSelections.size) {
            "Story lorebook entry selections must be unique"
        }
        lorebookSelections.forEach { selection ->
            requireNotNull(mLorebookEntryDao.getEntryById(selection.lorebookEntryId)) {
                "Lorebook entry does not exist"
            }
        }
        return NormalizedStoryConfiguration(lorebookSelections, characterSelections)
    }

    private suspend fun insertStoryCharacters(
        storyId: Long,
        selections: List<StoryCharacterSelection>
    ) {
        if (selections.isEmpty()) return
        mStoryCharacterDao.insertAll(
            selections.mapIndexed { index, selection ->
                StoryCharacter(
                    storyId = storyId,
                    characterId = selection.characterId,
                    sortOrder = index,
                    activationMode = selection.activationMode
                )
            }
        )
    }

    private suspend fun insertStoryLorebookEntries(
        storyId: Long,
        selections: List<StoryLorebookEntrySelection>
    ) {
        if (selections.isEmpty()) return
        mStoryLorebookEntryDao.insertAll(
            selections.map { selection ->
                StoryLorebookEntry(storyId, selection.lorebookEntryId)
            }
        )
    }

    private suspend fun updateLorebookRuntimeStates(entries: List<StoryLorebookEntry>) {
        if (entries.isEmpty()) return
        check(mStoryLorebookEntryDao.updateAll(entries) == entries.size) {
            "Story lorebook runtime state update failed"
        }
    }

    private suspend fun requireStory(storyId: Long): Story {
        return requireNotNull(mStoryDao.getStory(storyId)) { "Story does not exist" }
    }

    private suspend fun validateVolume(storyId: Long, volumeId: Long?) {
        if (volumeId == null) return
        require(mStoryVolumeDao.getById(volumeId)?.storyId == storyId) {
            "Story volume does not belong to the story"
        }
    }

    private suspend fun advanceStory(story: Story, latestTime: Long) {
        check(mStoryDao.advanceRevision(story.id, story.revision, latestTime) == 1) {
            "Story revision update failed"
        }
    }

    private suspend fun normalizeVolumeOrder(storyId: Long) {
        mStoryVolumeDao.getByStoryId(storyId).forEachIndexed { index, volume ->
            if (volume.sortOrder != index) {
                check(mStoryVolumeDao.updateSortOrder(volume.id, storyId, index) == 1)
            }
        }
    }

    private suspend fun normalizeChapterOrder(storyId: Long, volumeId: Long?) {
        mStoryChapterDao.getByContainer(storyId, volumeId).forEachIndexed { index, chapter ->
            if (chapter.sortOrder != index) {
                check(mStoryChapterDao.updateSortOrder(chapter.id, storyId, index) == 1)
            }
        }
    }

    private fun requireTitle(value: String, message: String): String {
        return value.trim().also { require(it.isNotEmpty()) { message } }
    }

    private data class NormalizedStoryConfiguration(
        val lorebookSelections: List<StoryLorebookEntrySelection>,
        val characterSelections: List<StoryCharacterSelection>
    )

    private companion object {
        const val DEFAULT_CHAPTER_TITLE = "正文"
    }
}

private fun StoryLorebookEntry.toRuntimeState(): StoryLorebookRuntimeState {
    return StoryLorebookRuntimeState(
        lorebookEntryId = lorebookEntryId,
        activatedAtStep = activatedAtStep,
        stickyUntilStep = stickyUntilStep,
        cooldownUntilStep = cooldownUntilStep,
        stateSignature = stateSignature
    )
}

private fun List<StoryLorebookEntry>.matches(
    states: List<StoryLorebookRuntimeState>
): Boolean {
    if (size != states.size) return false
    val stateById = states.associateBy { it.lorebookEntryId }
    return stateById.size == states.size && all { stateById.containsKey(it.lorebookEntryId) }
}

private fun List<StoryLorebookEntry>.withRuntimeStates(
    states: List<StoryLorebookRuntimeState>
): List<StoryLorebookEntry> {
    val stateById = states.associateBy { it.lorebookEntryId }
    return map { relation ->
        val state = requireNotNull(stateById[relation.lorebookEntryId])
        relation.copy(
            activatedAtStep = state.activatedAtStep,
            stickyUntilStep = state.stickyUntilStep,
            cooldownUntilStep = state.cooldownUntilStep,
            stateSignature = state.stateSignature
        )
    }
}
