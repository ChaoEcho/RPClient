package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.CharacterLLMProviderAssociation
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.FileEntity
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSummary
import me.kafuuneko.rpclient.libs.room.entity.ImageProvider
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.RegexCharacterAuthorization
import me.kafuuneko.rpclient.libs.room.entity.RegexScriptEntity
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.entity.StoryChapter
import me.kafuuneko.rpclient.libs.room.entity.StoryLorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.StoryVolume

/** 完整备份使用的显式分页、批量写入和清表接口。 */
@Dao
interface BackupDao {
    /** 统计角色数量。 */
    @Query("SELECT COUNT(*) FROM character")
    suspend fun countCharacters(): Long

    /** 按主键正序分页读取角色。 */
    @Query("SELECT * FROM character ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readCharacters(limit: Int, offset: Int): List<Character>

    /** 批量写入角色并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCharacters(items: List<Character>)

    /** 清空角色表。 */
    @Query("DELETE FROM character")
    suspend fun deleteAllCharacters()

    /** 统计角色与模型配置关联数量。 */
    @Query("SELECT COUNT(*) FROM character_llm_provider_associations")
    suspend fun countCharacterLLMProviderAssociations(): Long

    /** 按复合主键正序分页读取角色与模型配置关联。 */
    @Query(
        "SELECT * FROM character_llm_provider_associations " +
            "ORDER BY characterId ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun readCharacterLLMProviderAssociations(
        limit: Int,
        offset: Int
    ): List<CharacterLLMProviderAssociation>

    /** 批量写入角色与模型配置关联并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCharacterLLMProviderAssociations(
        items: List<CharacterLLMProviderAssociation>
    )

    /** 清空角色与模型配置关联表。 */
    @Query("DELETE FROM character_llm_provider_associations")
    suspend fun deleteAllCharacterLLMProviderAssociations()

    /** 统计世界书数量。 */
    @Query("SELECT COUNT(*) FROM lorebooks")
    suspend fun countLorebooks(): Long

    /** 按主键正序分页读取世界书。 */
    @Query("SELECT * FROM lorebooks ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readLorebooks(limit: Int, offset: Int): List<Lorebook>

    /** 批量写入世界书并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLorebooks(items: List<Lorebook>)

    /** 清空世界书表。 */
    @Query("DELETE FROM lorebooks")
    suspend fun deleteAllLorebooks()

    /** 统计世界书条目数量。 */
    @Query("SELECT COUNT(*) FROM lorebook_entries")
    suspend fun countLorebookEntries(): Long

    /** 按主键正序分页读取世界书条目。 */
    @Query("SELECT * FROM lorebook_entries ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readLorebookEntries(limit: Int, offset: Int): List<LorebookEntry>

    /** 批量写入世界书条目并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLorebookEntries(items: List<LorebookEntry>)

    /** 清空世界书条目表。 */
    @Query("DELETE FROM lorebook_entries")
    suspend fun deleteAllLorebookEntries()

    /** 统计单聊会话数量。 */
    @Query("SELECT COUNT(*) FROM chat_sessions")
    suspend fun countChatSessions(): Long

    /** 按主键正序分页读取单聊会话。 */
    @Query("SELECT * FROM chat_sessions ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readChatSessions(limit: Int, offset: Int): List<ChatSession>

    /** 批量写入单聊会话并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChatSessions(items: List<ChatSession>)

    /** 清空单聊会话表。 */
    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllChatSessions()

    /** 统计单聊消息数量。 */
    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun countChatMessages(): Long

    /** 按主键正序分页读取单聊消息。 */
    @Query("SELECT * FROM chat_messages ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readChatMessages(limit: Int, offset: Int): List<ChatMessage>

    /** 批量写入单聊消息并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChatMessages(items: List<ChatMessage>)

    /** 清空单聊消息表。 */
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllChatMessages()

    /** 统计模型配置数量。 */
    @Query("SELECT COUNT(*) FROM llm_providers")
    suspend fun countLLMProviders(): Long

    /** 按主键正序分页读取模型配置。 */
    @Query("SELECT * FROM llm_providers ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readLLMProviders(limit: Int, offset: Int): List<LLMProvider>

    /** 批量写入模型配置并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLLMProviders(items: List<LLMProvider>)

    /** 清空模型配置表。 */
    @Query("DELETE FROM llm_providers")
    suspend fun deleteAllLLMProviders()

    /** 统计图片服务数量。 */
    @Query("SELECT COUNT(*) FROM image_providers")
    suspend fun countImageProviders(): Long

    /** 按主键正序分页读取图片服务。 */
    @Query("SELECT * FROM image_providers ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readImageProviders(limit: Int, offset: Int): List<ImageProvider>

    /** 批量写入图片服务并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImageProviders(items: List<ImageProvider>)

    /** 清空图片服务表。 */
    @Query("DELETE FROM image_providers")
    suspend fun deleteAllImageProviders()

    /** 统计文件记录数量。 */
    @Query("SELECT COUNT(*) FROM files")
    suspend fun countFiles(): Long

    /** 按 UUID 正序分页读取文件记录。 */
    @Query("SELECT * FROM files ORDER BY uuid ASC LIMIT :limit OFFSET :offset")
    suspend fun readFiles(limit: Int, offset: Int): List<FileEntity>

    /** 批量写入文件记录并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFiles(items: List<FileEntity>)

    /** 清空文件记录表。 */
    @Query("DELETE FROM files")
    suspend fun deleteAllFiles()

    /** 统计群聊会话数量。 */
    @Query("SELECT COUNT(*) FROM group_chat_sessions")
    suspend fun countGroupChatSessions(): Long

    /** 按主键正序分页读取群聊会话。 */
    @Query("SELECT * FROM group_chat_sessions ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readGroupChatSessions(limit: Int, offset: Int): List<GroupChatSession>

    /** 批量写入群聊会话并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroupChatSessions(items: List<GroupChatSession>)

    /** 清空群聊会话表。 */
    @Query("DELETE FROM group_chat_sessions")
    suspend fun deleteAllGroupChatSessions()

    /** 统计群聊成员数量。 */
    @Query("SELECT COUNT(*) FROM group_chat_members")
    suspend fun countGroupChatMembers(): Long

    /** 按复合主键正序分页读取群聊成员。 */
    @Query(
        "SELECT * FROM group_chat_members " +
            "ORDER BY sessionId ASC, characterId ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun readGroupChatMembers(limit: Int, offset: Int): List<GroupChatMember>

    /** 批量写入群聊成员并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroupChatMembers(items: List<GroupChatMember>)

    /** 清空群聊成员表。 */
    @Query("DELETE FROM group_chat_members")
    suspend fun deleteAllGroupChatMembers()

    /** 统计群聊消息数量。 */
    @Query("SELECT COUNT(*) FROM group_chat_messages")
    suspend fun countGroupChatMessages(): Long

    /** 按主键正序分页读取群聊消息。 */
    @Query("SELECT * FROM group_chat_messages ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readGroupChatMessages(limit: Int, offset: Int): List<GroupChatMessage>

    /** 批量写入群聊消息并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroupChatMessages(items: List<GroupChatMessage>)

    /** 清空群聊消息表。 */
    @Query("DELETE FROM group_chat_messages")
    suspend fun deleteAllGroupChatMessages()

    /** 统计群聊摘要数量。 */
    @Query("SELECT COUNT(*) FROM group_chat_summaries")
    suspend fun countGroupChatSummaries(): Long

    /** 按主键正序分页读取群聊摘要。 */
    @Query("SELECT * FROM group_chat_summaries ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readGroupChatSummaries(limit: Int, offset: Int): List<GroupChatSummary>

    /** 批量写入群聊摘要并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroupChatSummaries(items: List<GroupChatSummary>)

    /** 清空群聊摘要表。 */
    @Query("DELETE FROM group_chat_summaries")
    suspend fun deleteAllGroupChatSummaries()

    /** 统计正则脚本数量。 */
    @Query("SELECT COUNT(*) FROM regex_scripts")
    suspend fun countRegexScripts(): Long

    /** 按主键正序分页读取正则脚本。 */
    @Query("SELECT * FROM regex_scripts ORDER BY rowId ASC LIMIT :limit OFFSET :offset")
    suspend fun readRegexScripts(limit: Int, offset: Int): List<RegexScriptEntity>

    /** 批量写入正则脚本并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRegexScripts(items: List<RegexScriptEntity>)

    /** 清空正则脚本表。 */
    @Query("DELETE FROM regex_scripts")
    suspend fun deleteAllRegexScripts()

    /** 统计正则角色授权数量。 */
    @Query("SELECT COUNT(*) FROM regex_character_authorizations")
    suspend fun countRegexCharacterAuthorizations(): Long

    /** 按主键正序分页读取正则角色授权。 */
    @Query(
        "SELECT * FROM regex_character_authorizations " +
            "ORDER BY characterId ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun readRegexCharacterAuthorizations(
        limit: Int,
        offset: Int
    ): List<RegexCharacterAuthorization>

    /** 批量写入正则角色授权并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRegexCharacterAuthorizations(
        items: List<RegexCharacterAuthorization>
    )

    /** 清空正则角色授权表。 */
    @Query("DELETE FROM regex_character_authorizations")
    suspend fun deleteAllRegexCharacterAuthorizations()

    /** 统计故事数量。 */
    @Query("SELECT COUNT(*) FROM stories")
    suspend fun countStories(): Long

    /** 按主键正序分页读取故事。 */
    @Query("SELECT * FROM stories ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readStories(limit: Int, offset: Int): List<Story>

    /** 批量写入故事并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStories(items: List<Story>)

    /** 清空故事表。 */
    @Query("DELETE FROM stories")
    suspend fun deleteAllStories()

    /** 统计故事分卷数量。 */
    @Query("SELECT COUNT(*) FROM story_volumes")
    suspend fun countStoryVolumes(): Long

    /** 按主键正序分页读取故事分卷。 */
    @Query("SELECT * FROM story_volumes ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readStoryVolumes(limit: Int, offset: Int): List<StoryVolume>

    /** 批量写入故事分卷并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStoryVolumes(items: List<StoryVolume>)

    /** 清空故事分卷表。 */
    @Query("DELETE FROM story_volumes")
    suspend fun deleteAllStoryVolumes()

    /** 统计故事章节数量。 */
    @Query("SELECT COUNT(*) FROM story_chapters")
    suspend fun countStoryChapters(): Long

    /** 按主键正序分页读取故事章节。 */
    @Query("SELECT * FROM story_chapters ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun readStoryChapters(limit: Int, offset: Int): List<StoryChapter>

    /** 批量写入故事章节并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStoryChapters(items: List<StoryChapter>)

    /** 清空故事章节表。 */
    @Query("DELETE FROM story_chapters")
    suspend fun deleteAllStoryChapters()

    /** 统计故事角色关联数量。 */
    @Query("SELECT COUNT(*) FROM story_characters")
    suspend fun countStoryCharacters(): Long

    /** 按复合主键正序分页读取故事角色关联。 */
    @Query(
        "SELECT * FROM story_characters " +
            "ORDER BY storyId ASC, characterId ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun readStoryCharacters(limit: Int, offset: Int): List<StoryCharacter>

    /** 批量写入故事角色关联并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStoryCharacters(items: List<StoryCharacter>)

    /** 清空故事角色关联表。 */
    @Query("DELETE FROM story_characters")
    suspend fun deleteAllStoryCharacters()

    /** 统计故事世界书条目关联数量。 */
    @Query("SELECT COUNT(*) FROM story_lorebook_entries")
    suspend fun countStoryLorebookEntries(): Long

    /** 按复合主键正序分页读取故事世界书条目关联。 */
    @Query(
        "SELECT * FROM story_lorebook_entries " +
            "ORDER BY storyId ASC, lorebookEntryId ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun readStoryLorebookEntries(
        limit: Int,
        offset: Int
    ): List<StoryLorebookEntry>

    /** 批量写入故事世界书条目关联并拒绝主键冲突。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStoryLorebookEntries(items: List<StoryLorebookEntry>)

    /** 清空故事世界书条目关联表。 */
    @Query("DELETE FROM story_lorebook_entries")
    suspend fun deleteAllStoryLorebookEntries()
}
