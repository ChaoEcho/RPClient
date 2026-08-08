package me.kafuuneko.rpclient.libs.story

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.chat.ChatCharacterMatcher
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.utils.toJsonString
import java.io.FilterInputStream
import java.io.InputStream

/** 故事文本与 `.rpstory.json` 的 URI 读写和事务导入入口。 */
class StoryArchiveRepository(
    private val mContext: Context,
    private val mAppDatabase: AppDatabase,
    private val mGson: Gson,
    private val mCodec: StoryArchiveCodec
) {
    private val mStoryDao = mAppDatabase.getStoryDao()
    private val mStoryCharacterDao = mAppDatabase.getStoryCharacterDao()
    private val mCharacterDao = mAppDatabase.getCharacterDao()
    private val mLorebookDao = mAppDatabase.getLorebookDao()
    private val mLorebookEntryDao = mAppDatabase.getLorebookEntryDao()

    suspend fun exportTextToUri(storyId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        val story = requireNotNull(mStoryDao.getStory(storyId)) { "Story not found" }
        mContext.contentResolver.openOutputStream(uri)
            ?.bufferedWriter(Charsets.UTF_8)
            ?.use { writer -> writer.write(story.content) }
            ?: error("Cannot open story export destination")
    }

    suspend fun exportArchiveToUri(storyId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        val archive = mAppDatabase.withTransaction { buildArchive(storyId) }
        mContext.contentResolver.openOutputStream(uri)
            ?.bufferedWriter(Charsets.UTF_8)
            ?.use { writer -> mCodec.encode(archive, writer) }
            ?: error("Cannot open story export destination")
    }

    suspend fun readTextImportFromUri(uri: Uri): StoryImportDraft = withContext(Dispatchers.IO) {
        val content = openLimitedInput(uri).reader(Charsets.UTF_8).use { it.readText() }
        StoryImportDraft(
            title = resolveDisplayTitle(uri),
            content = content,
            type = StoryImportType.Text
        )
    }

    suspend fun readArchiveImportFromUri(uri: Uri): StoryImportDraft = withContext(Dispatchers.IO) {
        val archive = openLimitedInput(uri).reader(Charsets.UTF_8).use(mCodec::decode)
        StoryImportDraft(
            title = archive.story.title,
            content = archive.story.content,
            memory = archive.story.memory,
            summary = archive.story.summary,
            authorNote = archive.story.authorNote,
            characterHints = archive.characterHints,
            lorebookHints = archive.lorebookHints,
            type = StoryImportType.Archive
        )
    }

    /** 仅在用户确认后创建 Story；任何关联校验或写入失败都会回滚。 */
    suspend fun saveImport(draft: StoryImportDraft, title: String): Long = withContext(Dispatchers.IO) {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Story title cannot be blank" }
        mAppDatabase.withTransaction {
            val characters = mCharacterDao.getAllCharacters()
            val lorebooks = mLorebookDao.getAllLorebooks()
            val entries = lorebooks.flatMap { mLorebookEntryDao.getEntriesByLorebookId(it.id) }
            val matchedCharacters = matchCharacters(draft.characterHints, characters)
            val matchedEntryIds = matchLorebookEntries(draft.lorebookHints, entries, lorebooks)
            val now = System.currentTimeMillis()
            val storyId = mStoryDao.insertOrReplace(
                Story(
                    title = normalizedTitle,
                    content = draft.content,
                    memory = draft.memory,
                    summary = draft.summary,
                    authorNote = draft.authorNote,
                    lorebookEntrySet = mGson.toJson(matchedEntryIds),
                    createTime = now,
                    latestTime = now
                )
            )
            mStoryCharacterDao.insertOrReplaceAll(
                matchedCharacters.mapIndexed { index, (characterId, hint) ->
                    StoryCharacter(
                        storyId = storyId,
                        characterId = characterId,
                        sortOrder = index,
                        activationMode = if (hint.activationMode == StoryArchiveCodec.MODE_ALWAYS) {
                            StoryCharacter.ACTIVATION_ALWAYS
                        } else {
                            StoryCharacter.ACTIVATION_AUTO
                        },
                        activationKeysJson = mGson.toJsonString(
                            hint.activationKeys.map(String::trim).filter(String::isNotEmpty).distinct()
                        )
                    )
                }
            )
            storyId
        }
    }

    private suspend fun buildArchive(storyId: Long): StoryArchive {
        val story = requireNotNull(mStoryDao.getStory(storyId)) { "Story not found" }
        val relations = mStoryCharacterDao.getByStoryId(storyId)
        val characters = relations.mapNotNull { relation ->
            mCharacterDao.getCharacterById(relation.characterId)?.let { relation to it }
        }
        val lorebooks = mLorebookDao.getAllLorebooks().associateBy { it.id }
        val selectedEntries = runCatching {
            mGson.fromJson(story.lorebookEntrySet, Array<Long>::class.java).orEmpty().toList()
        }.getOrDefault(emptyList()).mapNotNull { mLorebookEntryDao.getEntryById(it) }
        return StoryArchive(
            story = ArchivedStory(
                title = story.title,
                content = story.content,
                memory = story.memory,
                summary = story.summary,
                authorNote = story.authorNote
            ),
            characterHints = characters.map { (relation, character) ->
                StoryCharacterHint(
                    name = character.name,
                    fingerprint = ChatCharacterMatcher.fingerprintOf(character),
                    activationMode = if (
                        relation.activationMode == StoryCharacter.ACTIVATION_ALWAYS
                    ) {
                        StoryArchiveCodec.MODE_ALWAYS
                    } else {
                        StoryArchiveCodec.MODE_AUTO
                    },
                    activationKeys = runCatching {
                        mGson.fromJson(relation.activationKeysJson, Array<String>::class.java)
                            .orEmpty()
                            .toList()
                    }.getOrDefault(emptyList())
                )
            },
            lorebookHints = selectedEntries.map { entry ->
                StoryLorebookHint(
                    lorebookName = lorebooks[entry.lorebookId]?.name.orEmpty(),
                    entryName = entry.name,
                    fingerprint = fingerprintOf(entry)
                )
            }
        )
    }

    private fun matchCharacters(
        hints: List<StoryCharacterHint>,
        characters: List<Character>
    ): List<Pair<Long, StoryCharacterHint>> {
        val selected = mutableSetOf<Long>()
        return hints.mapNotNull { hint ->
            val fingerprintMatches = characters.filter {
                hint.fingerprint.equals(ChatCharacterMatcher.fingerprintOf(it), ignoreCase = true)
            }
            val match = fingerprintMatches.singleOrNull()
                ?: characters.filter { it.name.trim().equals(hint.name.trim(), ignoreCase = true) }
                    .singleOrNull()
            match?.takeIf { selected.add(it.id) }?.id?.let { it to hint }
        }
    }

    private fun matchLorebookEntries(
        hints: List<StoryLorebookHint>,
        entries: List<LorebookEntry>,
        lorebooks: List<Lorebook>
    ): List<Long> {
        val lorebookNames = lorebooks.associate { it.id to it.name }
        return hints.mapNotNull { hint ->
            val fingerprintMatches = entries.filter {
                hint.fingerprint.equals(fingerprintOf(it), ignoreCase = true)
            }
            val match = fingerprintMatches.singleOrNull()
                ?: entries.filter { entry ->
                    entry.name.trim().equals(hint.entryName.trim(), ignoreCase = true) &&
                        lorebookNames[entry.lorebookId]
                            ?.trim()
                            ?.equals(hint.lorebookName.trim(), ignoreCase = true) == true
                }.singleOrNull()
            match?.id
        }.distinct()
    }

    private fun fingerprintOf(entry: LorebookEntry): String {
        return storyTextHash(entry.content)
    }

    private fun openLimitedInput(uri: Uri): InputStream {
        val input = mContext.contentResolver.openInputStream(uri)
            ?: error("Cannot read story import")
        return SizeLimitedInputStream(input, MAX_IMPORT_BYTES.toLong())
    }

    private fun resolveDisplayTitle(uri: Uri): String {
        val displayName = runCatching {
            mContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }.getOrNull()
        return displayName
            ?.removeSuffix(".rpstory.json")
            ?.removeSuffix(".markdown")
            ?.removeSuffix(".md")
            ?.removeSuffix(".txt")
            ?.takeIf(String::isNotBlank)
            ?: DEFAULT_TITLE
    }

    private class SizeLimitedInputStream(
        input: InputStream,
        private val mMaxBytes: Long
    ) : FilterInputStream(input) {
        private var mTotalBytes = 0L

        override fun read(): Int = super.read().also { if (it >= 0) record(1) }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return super.read(buffer, offset, length).also { if (it > 0) record(it) }
        }

        private fun record(count: Int) {
            mTotalBytes += count
            require(mTotalBytes <= mMaxBytes) { "Story import is too large" }
        }
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 16 * 1024 * 1024
        const val DEFAULT_TITLE = "Imported story"
    }
}
