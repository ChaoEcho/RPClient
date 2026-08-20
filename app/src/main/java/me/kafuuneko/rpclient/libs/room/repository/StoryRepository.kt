package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.model.StoryOverview
import me.kafuuneko.rpclient.libs.story.storyTextHash
import me.kafuuneko.rpclient.utils.toJsonString
import me.kafuuneko.rpclient.utils.toStringList

/** Story 角色关联的领域聚合数据。 */
data class StoryCharacterCandidate(
    val relation: StoryCharacter,
    val character: Character,
    val activationKeys: List<String>
)

/** 用户保存的一条 Story 角色关联配置。 */
data class StoryCharacterSelection(
    val characterId: Long,
    val activationMode: Int,
    val activationKeys: List<String>
)

/** 等待通过 revision 和原文哈希校验后原子应用的 AI 正文修改。 */
data class StoryGeneratedEdit(
    val storyId: Long,
    val baseRevision: Long,
    val start: Int,
    val end: Int,
    val originalTextHash: String,
    val result: String,
    val nextWorldInfoStateJson: String,
    val nextWorldInfoGenerationStep: Int? = null
)

/** 已原子应用的正文及其新 revision、世界书生成步数。 */
data class StoryAppliedEdit(
    val content: String,
    val revision: Long,
    val worldInfoGenerationStep: Int
)

/**
 * 连续文档 Story 的业务仓库。
 *
 * 负责正文 revision 写入、角色关联替换和设置事务；不构建页面状态或 AI Prompt。
 */
class StoryRepository(
    private val mAppDatabase: AppDatabase,
    private val mGson: Gson
) {
    private val mStoryDao = mAppDatabase.getStoryDao()
    private val mStoryCharacterDao = mAppDatabase.getStoryCharacterDao()
    private val mCharacterDao = mAppDatabase.getCharacterDao()
    private val mLorebookEntryDao = mAppDatabase.getLorebookEntryDao()

    suspend fun getStoryOverviews(): List<StoryOverview> {
        return mStoryDao.getStoryOverviews()
    }

    suspend fun getStory(id: Long): Story? {
        return mStoryDao.getStory(id)
    }

    /** 读取候选角色并容忍单条旧别名 JSON 损坏，避免整篇 Story 无法打开。 */
    suspend fun getStoryCharacterCandidates(
        storyId: Long
    ): List<StoryCharacterCandidate> = mAppDatabase.withTransaction {
        mStoryCharacterDao.getByStoryId(storyId).mapNotNull { relation ->
            mCharacterDao.getCharacterById(relation.characterId)?.let { character ->
                StoryCharacterCandidate(
                    relation = relation,
                    character = character,
                    activationKeys = mGson.toStringList(relation.activationKeysJson)
                )
            }
        }
    }

    suspend fun createStory(
        title: String,
        createTime: Long = System.currentTimeMillis()
    ): Long {
        return createStoryWithConfiguration(
            title = title,
            lorebookEntryIds = emptyList(),
            characterSelections = emptyList(),
            createTime = createTime
        )
    }

    /**
     * 在一个事务中创建 Story，并写入初始世界书选择与候选角色配置。
     *
     * 所有外键引用会在写入前校验；任一配置无效时不会留下只有 Story 主记录的半成品。
     */
    suspend fun createStoryWithConfiguration(
        title: String,
        lorebookEntryIds: List<Long>,
        characterSelections: List<StoryCharacterSelection>,
        createTime: Long = System.currentTimeMillis()
    ): Long = mAppDatabase.withTransaction {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Story title cannot be blank" }
        val configuration = normalizeAndValidateConfiguration(
            lorebookEntryIds = lorebookEntryIds,
            characterSelections = characterSelections
        )
        val storyId = mStoryDao.insertOrReplace(
            Story(
                title = normalizedTitle,
                lorebookEntrySet = mGson.toJson(configuration.lorebookEntryIds),
                createTime = createTime,
                latestTime = createTime
            )
        )
        insertStoryCharacters(storyId, configuration.characterSelections)
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

    /**
     * 用预期 revision 保存正文。
     *
     * 返回 false 表示正文已由其他状态路径修改；调用方必须保留草稿且不能覆盖数据库内容。
     */
    suspend fun updateContent(
        storyId: Long,
        expectedRevision: Long,
        content: String,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean {
        return mStoryDao.updateContent(
            storyId = storyId,
            expectedRevision = expectedRevision,
            content = content,
            latestTime = latestTime
        ) == 1
    }

    /** 校验 revision 与目标正文后，原子提交 AI 结果和世界书时序状态。 */
    suspend fun applyGeneratedEdit(edit: StoryGeneratedEdit): StoryAppliedEdit? {
        return mAppDatabase.withTransaction {
            val story = mStoryDao.getStory(edit.storyId) ?: return@withTransaction null
            if (story.contentRevision != edit.baseRevision) return@withTransaction null
            if (edit.start !in 0..edit.end || edit.end > story.content.length) {
                return@withTransaction null
            }
            val originalText = story.content.substring(edit.start, edit.end)
            if (storyTextHash(originalText) != edit.originalTextHash) return@withTransaction null
            val content = story.content.replaceRange(edit.start, edit.end, edit.result)
            val nextStep = edit.nextWorldInfoGenerationStep
                ?: (story.worldInfoGenerationStep + 1)
            val updated = mStoryDao.updateGeneratedContent(
                storyId = story.id,
                expectedRevision = story.contentRevision,
                content = content,
                worldInfoStateJson = edit.nextWorldInfoStateJson,
                worldInfoGenerationStep = nextStep,
                latestTime = System.currentTimeMillis()
            )
            if (updated != 1) return@withTransaction null
            StoryAppliedEdit(content, story.contentRevision + 1L, nextStep)
        }
    }

    /** 会话内撤销 AI 修改，并恢复应用前的世界书状态。 */
    suspend fun revertGeneratedEdit(
        storyId: Long,
        expectedRevision: Long,
        start: Int,
        insertedText: String,
        replacedText: String,
        previousWorldInfoStateJson: String,
        previousWorldInfoGenerationStep: Int
    ): StoryAppliedEdit? = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction null
        if (story.contentRevision != expectedRevision) return@withTransaction null
        val end = start + insertedText.length
        if (start !in 0..end || end > story.content.length) return@withTransaction null
        if (storyTextHash(story.content.substring(start, end)) != storyTextHash(insertedText)) {
            return@withTransaction null
        }
        val content = story.content.replaceRange(start, end, replacedText)
        val updated = mStoryDao.updateGeneratedContent(
            storyId = story.id,
            expectedRevision = story.contentRevision,
            content = content,
            worldInfoStateJson = previousWorldInfoStateJson,
            worldInfoGenerationStep = previousWorldInfoGenerationStep,
            latestTime = System.currentTimeMillis()
        )
        if (updated != 1) return@withTransaction null
        StoryAppliedEdit(content, story.contentRevision + 1L, previousWorldInfoGenerationStep)
    }

    /** 在一个事务中保存长期上下文、世界书选择和完整候选角色配置。 */
    suspend fun updateStoryConfiguration(
        storyId: Long,
        memory: String,
        summary: String,
        authorNote: String,
        lorebookEntryIds: List<Long>,
        characterSelections: List<StoryCharacterSelection>,
        latestTime: Long = System.currentTimeMillis()
    ) = mAppDatabase.withTransaction {
        requireNotNull(mStoryDao.getStory(storyId)) { "Story does not exist" }
        val configuration = normalizeAndValidateConfiguration(
            lorebookEntryIds = lorebookEntryIds,
            characterSelections = characterSelections
        )
        check(
            mStoryDao.updateStorySettings(
                id = storyId,
                memory = memory,
                summary = summary,
                authorNote = authorNote,
                lorebookEntrySet = mGson.toJson(configuration.lorebookEntryIds),
                latestTime = latestTime
            ) == 1
        ) { "Story settings update failed" }
        mStoryCharacterDao.deleteByStoryId(storyId)
        insertStoryCharacters(storyId, configuration.characterSelections)
    }

    /** 仅在正文仍是生成时快照时保存摘要，避免旧响应覆盖新正文。 */
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

    suspend fun getLorebookEntryIds(story: Story): List<Long> {
        return runCatching {
            mGson.fromJson(story.lorebookEntrySet, Array<Long>::class.java)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun deleteStory(id: Long) {
        mStoryDao.deleteStory(id)
    }

    private fun normalizeSelections(
        selections: List<StoryCharacterSelection>
    ): List<StoryCharacterSelection> {
        require(selections.distinctBy { it.characterId }.size == selections.size) {
            "Story character selections must be unique"
        }
        return selections.map { selection ->
            require(StoryCharacter.isValidActivationMode(selection.activationMode)) {
                "Unsupported character activation mode"
            }
            selection.copy(
                activationKeys = selection.activationKeys
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
            )
        }
    }

    private suspend fun normalizeAndValidateConfiguration(
        lorebookEntryIds: List<Long>,
        characterSelections: List<StoryCharacterSelection>
    ): NormalizedStoryConfiguration {
        val distinctLorebookEntryIds = lorebookEntryIds.distinct()
        distinctLorebookEntryIds.forEach { entryId ->
            requireNotNull(mLorebookEntryDao.getEntryById(entryId)) {
                "Lorebook entry does not exist"
            }
        }
        val normalizedSelections = normalizeSelections(characterSelections)
        normalizedSelections.forEach { selection ->
            requireNotNull(mCharacterDao.getCharacterById(selection.characterId)) {
                "Character does not exist"
            }
        }
        return NormalizedStoryConfiguration(
            lorebookEntryIds = distinctLorebookEntryIds,
            characterSelections = normalizedSelections
        )
    }

    private suspend fun insertStoryCharacters(
        storyId: Long,
        selections: List<StoryCharacterSelection>
    ) {
        mStoryCharacterDao.insertOrReplaceAll(
            selections.mapIndexed { index, selection ->
                StoryCharacter(
                    storyId = storyId,
                    characterId = selection.characterId,
                    sortOrder = index,
                    activationMode = selection.activationMode,
                    activationKeysJson = mGson.toJsonString(selection.activationKeys)
                )
            }
        )
    }

    private data class NormalizedStoryConfiguration(
        val lorebookEntryIds: List<Long>,
        val characterSelections: List<StoryCharacterSelection>
    )
}
