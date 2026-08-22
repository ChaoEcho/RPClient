package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.entity.StoryLorebookEntry
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
data class StoryLorebookEntrySelection(
    val lorebookEntryId: Long
)

/** 可用于生成提交、撤销和恢复的 Story 条目级世界书状态快照。 */
data class StoryLorebookRuntimeState(
    val lorebookEntryId: Long,
    val activatedAtStep: Int? = null,
    val stickyUntilStep: Int? = null,
    val cooldownUntilStep: Int? = null,
    val stateSignature: String? = null
)

/** 等待通过 revision、原文哈希和世界书配置校验后原子应用的 AI 正文修改。 */
data class StoryGeneratedEdit(
    val storyId: Long,
    val baseRevision: Long,
    val start: Int,
    val end: Int,
    val originalTextHash: String,
    val result: String,
    val nextWorldInfoStates: List<StoryLorebookRuntimeState>,
    val nextWorldInfoGenerationStep: Int? = null
)

/** 已原子应用的正文、新 revision、世界书生成步数和条目状态。 */
data class StoryAppliedEdit(
    val content: String,
    val revision: Long,
    val worldInfoGenerationStep: Int,
    val worldInfoStates: List<StoryLorebookRuntimeState>
)

/**
 * 连续文档 Story 的业务仓库。
 *
 * 负责正文 revision、角色和世界书关联、条目级时序状态以及设置事务；不构建页面状态或
 * AI Prompt。世界书配置与生成结果必须在同一快照上提交，避免旧 Prompt 覆盖新配置。
 */
class StoryRepository(
    private val mAppDatabase: AppDatabase
) {
    private val mStoryDao = mAppDatabase.getStoryDao()
    private val mStoryCharacterDao = mAppDatabase.getStoryCharacterDao()
    private val mStoryLorebookEntryDao = mAppDatabase.getStoryLorebookEntryDao()
    private val mCharacterDao = mAppDatabase.getCharacterDao()
    private val mLorebookEntryDao = mAppDatabase.getLorebookEntryDao()

    suspend fun getStoryOverviews(): List<StoryOverview> = mStoryDao.getStoryOverviews()

    suspend fun getStory(id: Long): Story? = mStoryDao.getStory(id)

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
                StoryLorebookEntryCandidate(
                    relation = relation,
                    entry = entry
                )
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

    /** 创建 Story，并在一个事务中写入角色和世界书关联。 */
    suspend fun createStoryWithConfiguration(
        title: String,
        lorebookSelections: List<StoryLorebookEntrySelection>,
        characterSelections: List<StoryCharacterSelection>,
        includeUserPersona: Boolean = false,
        createTime: Long = System.currentTimeMillis()
    ): Long = mAppDatabase.withTransaction {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Story title cannot be blank" }
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
        insertStoryCharacters(storyId, configuration.characterSelections)
        insertStoryLorebookEntries(storyId, configuration.lorebookSelections)
        storyId
    }

    suspend fun renameStory(
        id: Long,
        title: String,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Story title cannot be blank" }
        return mStoryDao.renameStory(id, normalizedTitle, latestTime) == 1
    }

    suspend fun updateContent(
        storyId: Long,
        expectedRevision: Long,
        content: String,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mStoryDao.updateContent(
        storyId = storyId,
        expectedRevision = expectedRevision,
        content = content,
        latestTime = latestTime
    ) == 1

    /** 校验正文和世界书配置快照后，原子提交 AI 结果与条目级时序状态。 */
    suspend fun applyGeneratedEdit(edit: StoryGeneratedEdit): StoryAppliedEdit? {
        return mAppDatabase.withTransaction {
            val story = mStoryDao.getStory(edit.storyId) ?: return@withTransaction null
            if (story.contentRevision != edit.baseRevision) return@withTransaction null
            if (edit.start !in 0..edit.end || edit.end > story.content.length) {
                return@withTransaction null
            }
            if (storyTextHash(story.content.substring(edit.start, edit.end)) != edit.originalTextHash) {
                return@withTransaction null
            }
            val currentRelations = mStoryLorebookEntryDao.getByStoryId(edit.storyId)
            if (!currentRelations.matches(edit.nextWorldInfoStates)) return@withTransaction null
            val content = story.content.replaceRange(edit.start, edit.end, edit.result)
            val nextStep = edit.nextWorldInfoGenerationStep
                ?: (story.worldInfoGenerationStep + 1)
            if (
                mStoryDao.updateGeneratedContent(
                    storyId = story.id,
                    expectedRevision = story.contentRevision,
                    content = content,
                    worldInfoGenerationStep = nextStep,
                    latestTime = System.currentTimeMillis()
                ) != 1
            ) return@withTransaction null
            val nextRelations = currentRelations.withRuntimeStates(edit.nextWorldInfoStates)
            updateLorebookRuntimeStates(nextRelations)
            StoryAppliedEdit(
                content = content,
                revision = story.contentRevision + 1L,
                worldInfoGenerationStep = nextStep,
                worldInfoStates = nextRelations.map { it.toRuntimeState() }
            )
        }
    }

    /** 会话内撤销 AI 修改，并恢复应用前的条目级世界书状态。 */
    suspend fun revertGeneratedEdit(
        storyId: Long,
        expectedRevision: Long,
        start: Int,
        insertedText: String,
        replacedText: String,
        previousWorldInfoStates: List<StoryLorebookRuntimeState>,
        previousWorldInfoGenerationStep: Int
    ): StoryAppliedEdit? = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction null
        if (story.contentRevision != expectedRevision) return@withTransaction null
        val end = start + insertedText.length
        if (start !in 0..end || end > story.content.length) return@withTransaction null
        if (storyTextHash(story.content.substring(start, end)) != storyTextHash(insertedText)) {
            return@withTransaction null
        }
        val currentRelations = mStoryLorebookEntryDao.getByStoryId(storyId)
        if (!currentRelations.matches(previousWorldInfoStates)) return@withTransaction null
        val content = story.content.replaceRange(start, end, replacedText)
        if (
            mStoryDao.updateGeneratedContent(
                storyId = story.id,
                expectedRevision = story.contentRevision,
                content = content,
                worldInfoGenerationStep = previousWorldInfoGenerationStep,
                latestTime = System.currentTimeMillis()
            ) != 1
        ) return@withTransaction null
        val previousRelations = currentRelations.withRuntimeStates(previousWorldInfoStates)
        updateLorebookRuntimeStates(previousRelations)
        StoryAppliedEdit(
            content = content,
            revision = story.contentRevision + 1L,
            worldInfoGenerationStep = previousWorldInfoGenerationStep,
            worldInfoStates = previousRelations.map { it.toRuntimeState() }
        )
    }

    /** 保存长期上下文，并保留仍启用条目的时序状态。 */
    suspend fun updateStoryConfiguration(
        storyId: Long,
        memory: String,
        summary: String,
        authorNote: String,
        includeUserPersona: Boolean = false,
        lorebookSelections: List<StoryLorebookEntrySelection>,
        characterSelections: List<StoryCharacterSelection>,
        latestTime: Long = System.currentTimeMillis()
    ) = mAppDatabase.withTransaction {
        requireNotNull(mStoryDao.getStory(storyId)) { "Story does not exist" }
        val configuration = normalizeAndValidateConfiguration(
            lorebookSelections,
            characterSelections
        )
        check(
            mStoryDao.updateStorySettings(
                id = storyId,
                memory = memory,
                summary = summary,
                authorNote = authorNote,
                includeUserPersona = includeUserPersona,
                latestTime = latestTime
            ) == 1
        ) { "Story settings update failed" }
        val previousLorebookEntries = mStoryLorebookEntryDao.getByStoryId(storyId)
            .associateBy { it.lorebookEntryId }
        mStoryCharacterDao.deleteByStoryId(storyId)
        insertStoryCharacters(storyId, configuration.characterSelections)
        mStoryLorebookEntryDao.deleteByStoryId(storyId)
        val nextLorebookEntries = configuration.lorebookSelections.map { selection ->
            val previous = previousLorebookEntries[selection.lorebookEntryId]
            previous ?: run {
                StoryLorebookEntry(
                    storyId = storyId,
                    lorebookEntryId = selection.lorebookEntryId
                )
            }
        }
        if (nextLorebookEntries.isNotEmpty()) {
            mStoryLorebookEntryDao.insertAll(nextLorebookEntries)
        }
    }

    suspend fun saveGeneratedSummary(
        storyId: Long,
        expectedContentRevision: Long,
        content: String,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean {
        if (content.isBlank()) return false
        return mStoryDao.updateSummary(
            storyId = storyId,
            expectedContentRevision = expectedContentRevision,
            summary = content,
            latestTime = latestTime
        ) == 1
    }

    suspend fun deleteStory(id: Long) {
        mStoryDao.deleteStory(id)
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
        ) {
            "Story can only have one primary character"
        }
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
                StoryLorebookEntry(
                    storyId = storyId,
                    lorebookEntryId = selection.lorebookEntryId
                )
            }
        )
    }

    private suspend fun updateLorebookRuntimeStates(entries: List<StoryLorebookEntry>) {
        if (entries.isEmpty()) return
        check(mStoryLorebookEntryDao.updateAll(entries) == entries.size) {
            "Story lorebook runtime state update failed"
        }
    }

    private data class NormalizedStoryConfiguration(
        val lorebookSelections: List<StoryLorebookEntrySelection>,
        val characterSelections: List<StoryCharacterSelection>
    )
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
    if (stateById.size != states.size) return false
    return all { relation ->
        stateById.containsKey(relation.lorebookEntryId)
    }
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
