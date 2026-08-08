package me.kafuuneko.rpclient.libs.room

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.kafuuneko.rpclient.libs.room.dao.CharacterDao
import me.kafuuneko.rpclient.libs.room.dao.ChatMessageDao
import me.kafuuneko.rpclient.libs.room.dao.ChatSessionDao
import me.kafuuneko.rpclient.libs.room.dao.FileDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatMemberDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatMessageDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatSessionDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatSummaryDao
import me.kafuuneko.rpclient.libs.room.dao.LLMProviderDao
import me.kafuuneko.rpclient.libs.room.dao.LorebookDao
import me.kafuuneko.rpclient.libs.room.dao.LorebookEntryDao
import me.kafuuneko.rpclient.libs.room.dao.RegexScriptDao
import me.kafuuneko.rpclient.libs.room.dao.StoryCharacterDao
import me.kafuuneko.rpclient.libs.room.dao.StoryDao
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.FileEntity
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSummary
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.RegexCharacterAuthorization
import me.kafuuneko.rpclient.libs.room.entity.RegexScriptEntity
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.migration.AppDatabaseAutoMigration1To2Spec

/** RPClient 的 Room 数据库入口，集中声明实体、类型转换器和 DAO。 */
@Database(
    entities = [
        Character::class,
        Lorebook::class,
        LorebookEntry::class,
        ChatSession::class,
        ChatMessage::class,
        LLMProvider::class,
        FileEntity::class,
        GroupChatSession::class,
        GroupChatMember::class,
        GroupChatMessage::class,
        GroupChatSummary::class,
        RegexScriptEntity::class,
        RegexCharacterAuthorization::class,
        Story::class,
        StoryCharacter::class
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AppDatabaseAutoMigration1To2Spec::class),
        AutoMigration(from = 2, to = 3)
    ],
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getCharacterDao(): CharacterDao
    abstract fun getLorebookDao(): LorebookDao
    abstract fun getLorebookEntryDao(): LorebookEntryDao
    abstract fun getChatSessionDao(): ChatSessionDao
    abstract fun getChatMessageDao(): ChatMessageDao
    abstract fun getLLMProviderDao(): LLMProviderDao
    abstract fun getFileDao(): FileDao
    abstract fun getGroupChatSessionDao(): GroupChatSessionDao
    abstract fun getGroupChatMemberDao(): GroupChatMemberDao
    abstract fun getGroupChatMessageDao(): GroupChatMessageDao
    abstract fun getGroupChatSummaryDao(): GroupChatSummaryDao
    abstract fun getRegexScriptDao(): RegexScriptDao
    abstract fun getStoryDao(): StoryDao
    abstract fun getStoryCharacterDao(): StoryCharacterDao

}
