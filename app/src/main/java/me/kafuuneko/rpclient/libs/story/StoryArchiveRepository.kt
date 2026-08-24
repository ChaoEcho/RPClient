package me.kafuuneko.rpclient.libs.story

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import java.io.BufferedWriter
import java.io.FilterInputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.chat.ChatCharacterMatcher
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryChapter
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.entity.StoryLorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.StoryVolume
import me.kafuuneko.rpclient.libs.room.model.StoryChapterOverview

/** 故事文本与 `.rpstory.json` 的 URI 读写和事务导入入口。 */
class StoryArchiveRepository(
    private val mContext: Context,
    private val mAppDatabase: AppDatabase,
    private val mCodec: StoryArchiveCodec
) {
    private val mStoryDao = mAppDatabase.getStoryDao()
    private val mStoryVolumeDao = mAppDatabase.getStoryVolumeDao()
    private val mStoryChapterDao = mAppDatabase.getStoryChapterDao()
    private val mStoryCharacterDao = mAppDatabase.getStoryCharacterDao()
    private val mStoryLorebookEntryDao = mAppDatabase.getStoryLorebookEntryDao()
    private val mCharacterDao = mAppDatabase.getCharacterDao()
    private val mLorebookDao = mAppDatabase.getLorebookDao()
    private val mLorebookEntryDao = mAppDatabase.getLorebookEntryDao()

    /**
     * 按章节顺序写出可读文本，避免先把整部长篇小说拼成第二份巨大字符串。
     */
    suspend fun exportTextToUri(
        storyId: Long,
        uri: Uri,
        markdown: Boolean
    ) = withContext(Dispatchers.IO) {
        val plan = mAppDatabase.withTransaction { buildTextExportPlan(storyId) }
        mContext.contentResolver.openOutputStream(uri)
            ?.bufferedWriter(Charsets.UTF_8)
            ?.use { writer -> writeTextExport(writer, plan, markdown) }
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
            ungroupedChapters = listOf(
                ArchivedChapter(title = DEFAULT_CHAPTER_TITLE, content = content)
            ),
            type = StoryImportType.Text
        )
    }

    suspend fun readArchiveImportFromUri(uri: Uri): StoryImportDraft = withContext(Dispatchers.IO) {
        val archive = openLimitedInput(uri).reader(Charsets.UTF_8).use(mCodec::decode)
        StoryImportDraft(
            title = archive.story.title,
            memory = archive.story.memory,
            summary = archive.story.summary,
            authorNote = archive.story.authorNote,
            includeUserPersona = archive.story.includeUserPersona,
            ungroupedChapters = archive.story.ungroupedChapters,
            volumes = archive.story.volumes,
            characterHints = archive.characterHints,
            lorebookHints = archive.lorebookHints,
            type = StoryImportType.Archive
        )
    }

    /** 仅在用户确认后创建整个故事聚合；任何关联校验或写入失败都会回滚。 */
    suspend fun saveImport(draft: StoryImportDraft, title: String): Long = withContext(Dispatchers.IO) {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Story title cannot be blank" }
        require(draft.chapterCount > 0) { "Story must contain at least one chapter" }
        mAppDatabase.withTransaction {
            val characters = mCharacterDao.getAllCharacters()
            val lorebooks = mLorebookDao.getAllLorebooks()
            val entries = lorebooks.flatMap { mLorebookEntryDao.getEntriesByLorebookId(it.id) }
            val matchedCharacters = matchCharacters(draft.characterHints, characters)
            val matchedEntries = matchLorebookEntries(
                hints = draft.lorebookHints,
                entries = entries,
                lorebooks = lorebooks
            )
            val now = System.currentTimeMillis()
            val storyId = mStoryDao.insertOrReplace(
                Story(
                    title = normalizedTitle,
                    memory = draft.memory,
                    summary = draft.summary,
                    authorNote = draft.authorNote,
                    includeUserPersona = draft.includeUserPersona,
                    createTime = now,
                    latestTime = now
                )
            )
            draft.ungroupedChapters.forEachIndexed { index, chapter ->
                insertChapter(storyId, null, chapter, index, now)
            }
            draft.volumes.forEachIndexed { volumeIndex, archivedVolume ->
                val volumeId = mStoryVolumeDao.insert(
                    StoryVolume(
                        storyId = storyId,
                        title = requireTitle(archivedVolume.title),
                        sortOrder = volumeIndex
                    )
                )
                archivedVolume.chapters.forEachIndexed { chapterIndex, chapter ->
                    insertChapter(storyId, volumeId, chapter, chapterIndex, now)
                }
            }
            val storyCharacters = matchedCharacters.mapIndexed { index, (characterId, hint) ->
                StoryCharacter(
                    storyId = storyId,
                    characterId = characterId,
                    sortOrder = index,
                    activationMode = when (hint.activationMode) {
                        StoryArchiveCodec.MODE_PRIMARY -> StoryCharacter.ACTIVATION_PRIMARY
                        StoryArchiveCodec.MODE_ALWAYS -> StoryCharacter.ACTIVATION_ALWAYS
                        else -> StoryCharacter.ACTIVATION_AUTO
                    }
                )
            }
            if (storyCharacters.isNotEmpty()) mStoryCharacterDao.insertAll(storyCharacters)
            val storyLorebookEntries = matchedEntries.map { entryId ->
                StoryLorebookEntry(storyId = storyId, lorebookEntryId = entryId)
            }
            if (storyLorebookEntries.isNotEmpty()) {
                mStoryLorebookEntryDao.insertAll(storyLorebookEntries)
            }
            storyId
        }
    }

    private suspend fun buildTextExportPlan(storyId: Long): TextExportPlan {
        val story = requireNotNull(mStoryDao.getStory(storyId)) { "Story not found" }
        return TextExportPlan(
            storyTitle = story.title,
            volumes = mStoryVolumeDao.getByStoryId(storyId),
            chapters = mStoryChapterDao.getOverviewsByStoryId(storyId)
        )
    }

    private suspend fun writeTextExport(
        writer: BufferedWriter,
        plan: TextExportPlan,
        markdown: Boolean
    ) {
        if (markdown) writer.write("# ${plan.storyTitle}\n") else writer.write(plan.storyTitle)
        writer.write("\n\n")
        val ungrouped = plan.chapters.filter { it.volumeId == null }
        ungrouped.forEach { chapter ->
            writeChapter(writer, chapter, headingLevel = 2, markdown = markdown)
        }
        plan.volumes.forEach { volume ->
            if (markdown) {
                writer.write("## ${volume.title}\n\n")
            } else {
                writer.write("== ${volume.title} ==\n\n")
            }
            plan.chapters.filter { it.volumeId == volume.id }.forEach { chapter ->
                writeChapter(writer, chapter, headingLevel = 3, markdown = markdown)
            }
        }
    }

    private suspend fun writeChapter(
        writer: BufferedWriter,
        overview: StoryChapterOverview,
        headingLevel: Int,
        markdown: Boolean
    ) {
        val chapter = requireNotNull(mStoryChapterDao.getById(overview.id)) {
            "Story chapter disappeared during export"
        }
        if (markdown) {
            writer.write("${"#".repeat(headingLevel)} ${chapter.title}\n\n")
        } else {
            writer.write("-- ${chapter.title} --\n\n")
        }
        writer.write(chapter.content)
        writer.write("\n\n")
    }

    private suspend fun buildArchive(storyId: Long): StoryArchive {
        val story = requireNotNull(mStoryDao.getStory(storyId)) { "Story not found" }
        val volumes = mStoryVolumeDao.getByStoryId(storyId)
        val chapters = mStoryChapterDao.getByStoryId(storyId)
        val relations = mStoryCharacterDao.getByStoryId(storyId)
        val characters = relations.mapNotNull { relation ->
            mCharacterDao.getCharacterById(relation.characterId)?.let { relation to it }
        }
        val lorebooks = mLorebookDao.getAllLorebooks().associateBy { it.id }
        val selectedEntries = mStoryLorebookEntryDao.getByStoryId(storyId).mapNotNull { relation ->
            mLorebookEntryDao.getEntryById(relation.lorebookEntryId)?.let { entry ->
                relation to entry
            }
        }
        return StoryArchive(
            story = ArchivedStory(
                title = story.title,
                memory = story.memory,
                summary = story.summary,
                authorNote = story.authorNote,
                includeUserPersona = story.includeUserPersona,
                ungroupedChapters = chapters.filter { it.volumeId == null }.map {
                    ArchivedChapter(it.title, it.content)
                },
                volumes = volumes.map { volume ->
                    ArchivedVolume(
                        title = volume.title,
                        chapters = chapters.filter { it.volumeId == volume.id }.map {
                            ArchivedChapter(it.title, it.content)
                        }
                    )
                }
            ),
            characterHints = characters.map { (relation, character) ->
                StoryCharacterHint(
                    name = character.name,
                    fingerprint = ChatCharacterMatcher.fingerprintOf(character),
                    activationMode = when (relation.activationMode) {
                        StoryCharacter.ACTIVATION_PRIMARY -> StoryArchiveCodec.MODE_PRIMARY
                        StoryCharacter.ACTIVATION_ALWAYS -> StoryArchiveCodec.MODE_ALWAYS
                        else -> StoryArchiveCodec.MODE_AUTO
                    }
                )
            },
            lorebookHints = selectedEntries.map { (_, entry) ->
                StoryLorebookHint(
                    lorebookName = lorebooks[entry.lorebookId]?.name.orEmpty(),
                    entryName = entry.name,
                    fingerprint = fingerprintOf(entry)
                )
            }
        )
    }

    private suspend fun insertChapter(
        storyId: Long,
        volumeId: Long?,
        chapter: ArchivedChapter,
        sortOrder: Int,
        now: Long
    ) {
        mStoryChapterDao.insert(
            StoryChapter(
                storyId = storyId,
                volumeId = volumeId,
                title = requireTitle(chapter.title),
                content = chapter.content,
                sortOrder = sortOrder,
                createTime = now,
                latestTime = now
            )
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
        val selectedEntryIds = mutableSetOf<Long>()
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
            match?.takeIf { selectedEntryIds.add(it.id) }?.id
        }
    }

    private fun fingerprintOf(entry: LorebookEntry): String = storyTextHash(entry.content)

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

    private fun requireTitle(value: String): String {
        return value.trim().also { require(it.isNotEmpty()) { "Story structure title cannot be blank" } }
    }

    private data class TextExportPlan(
        val storyTitle: String,
        val volumes: List<StoryVolume>,
        val chapters: List<StoryChapterOverview>
    )

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
        const val DEFAULT_CHAPTER_TITLE = "正文"
    }
}
