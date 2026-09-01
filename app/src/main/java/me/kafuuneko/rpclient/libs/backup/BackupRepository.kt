package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.BuildConfig
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.CharacterLLMProviderAssociation
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
import me.kafuuneko.rpclient.libs.room.entity.StoryChapter
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.entity.StoryLorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.StoryVolume
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 应用级完整备份与 Replace Restore 的唯一业务入口。
 *
 * 本地文件和 WebDAV 都复用同一加密文件、校验 staging 与恢复事务。
 */
class BackupRepository(
    private val mContext: Context,
    private val mDatabase: AppDatabase,
    private val mFileRepository: FileRepository,
    private val mCodec: BackupCodec,
    private val mCrypto: BackupCrypto
) {
    private val mBackupDao = mDatabase.getBackupDao()

    /** 创建完整加密备份并在成功后复制到用户选择的文档。 */
    suspend fun createLocalBackup(
        target: Uri,
        password: CharArray,
        onPhase: (BackupOperationPhase) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val encryptedFile = createEncryptedBackupFile(password, onPhase)
        try {
            val output = mContext.contentResolver.openOutputStream(target, "w")
                ?: throw BackupException.GenericFailure()
            output.use { stream -> FileInputStream(encryptedFile).use { it.copyTo(stream) } }
            BackupSettingsModel.lastSuccessfulBackupAt = System.currentTimeMillis()
        } finally {
            encryptedFile.delete()
        }
    }

    /** 创建可供 WebDAV PUT 使用的定长加密临时文件，调用方负责删除。 */
    suspend fun createEncryptedBackupFile(
        password: CharArray,
        onPhase: (BackupOperationPhase) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        onPhase(BackupOperationPhase.Preparing)
        val plainZip = File.createTempFile("backup_export_", ".zip", mContext.cacheDir)
        val encryptedFile = File.createTempFile("backup_upload_", BackupContract.FILE_EXTENSION, mContext.cacheDir)
        var success = false
        try {
            writePlainArchive(plainZip, onPhase)
            onPhase(BackupOperationPhase.Encrypting)
            FileInputStream(plainZip).use { input ->
                FileOutputStream(encryptedFile).use { output ->
                    mCrypto.encrypt(input, output, password)
                }
            }
            success = true
            encryptedFile
        } catch (error: BackupException) {
            throw error
        } catch (error: Exception) {
            throw classifyIoFailure(error)
        } finally {
            plainZip.delete()
            if (!success) encryptedFile.delete()
        }
    }

    /** 从本地文档复制密文后执行完整校验，当前数据尚不会被修改。 */
    suspend fun validateLocalBackup(
        source: Uri,
        password: CharArray,
        onPhase: (BackupOperationPhase) -> Unit = {}
    ): ValidatedBackup = withContext(Dispatchers.IO) {
        val encryptedFile = File.createTempFile("backup_import_", BackupContract.FILE_EXTENSION, mContext.cacheDir)
        try {
            val input = mContext.contentResolver.openInputStream(source)
                ?: throw BackupException.UnsupportedFormat()
            input.use { stream -> FileOutputStream(encryptedFile).use { stream.copyTo(it) } }
            validateEncryptedBackup(encryptedFile, password, onPhase)
        } catch (error: BackupException) {
            throw error
        } catch (error: Exception) {
            throw classifyIoFailure(error)
        } finally {
            encryptedFile.delete()
        }
    }

    /** 校验已下载或已生成的同格式加密备份文件。 */
    suspend fun validateEncryptedBackup(
        encryptedFile: File,
        password: CharArray,
        onPhase: (BackupOperationPhase) -> Unit = {}
    ): ValidatedBackup = withContext(Dispatchers.IO) {
        onPhase(BackupOperationPhase.Validating)
        mCodec.validate(encryptedFile, password)
    }

    /**
     * 用已验证 staging 完整替换业务数据。
     *
     * 新物理文件先安全落盘；Room 与偏好应用成功后才清理不再引用的旧文件。
     */
    suspend fun restore(
        backup: ValidatedBackup,
        onPhase: (BackupOperationPhase) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            // 偏好在 destructive transaction 前完成解析，避免清库后才发现格式错误
            val preferences = mCodec.readPreferences(backup)
            val oldPreferences = BackupPreferencesSnapshot.capture()
            val oldDefaultProvidersInitialized = me.kafuuneko.rpclient.libs.AppModel
                .llmDefaultProvidersInitialized
            // 新 hash 文件先通过临时文件校验和原子发布，失败不影响当前数据库
            backup.assetFiles.forEach { (hash, source) ->
                mFileRepository.prepareRestoredFile(hash, source)
            }
            var preferenceApplyStarted = false
            try {
                mDatabase.withTransaction {
                    onPhase(BackupOperationPhase.RestoringDatabase)
                    deleteBusinessTables()
                    insertBusinessTables(backup)
                    onPhase(BackupOperationPhase.RestoringSettings)
                    preferenceApplyStarted = true
                    preferences.apply()
                }
            } catch (error: Exception) {
                if (preferenceApplyStarted) {
                    runCatching {
                        oldPreferences.apply()
                        me.kafuuneko.rpclient.libs.AppModel.llmDefaultProvidersInitialized =
                            oldDefaultProvidersInitialized
                    }
                }
                throw error
            }
            // 正确数据已经提交，孤儿文件清理失败不反向破坏恢复结果
            onPhase(BackupOperationPhase.Finishing)
            runCatching {
                mFileRepository.deleteUnreferencedPhysicalFiles(backup.assetFiles.keys)
            }
        } catch (error: BackupException) {
            throw error
        } catch (error: Exception) {
            throw BackupException.GenericFailure(error)
        } finally {
            mCodec.cleanup(backup)
        }
    }

    /** 用户取消确认时立即清理包含明文的 staging。 */
    suspend fun discard(backup: ValidatedBackup) = withContext(Dispatchers.IO) {
        mCodec.cleanup(backup)
    }

    private suspend fun writePlainArchive(
        target: File,
        onPhase: (BackupOperationPhase) -> Unit
    ) {
        FileOutputStream(target).use { fileOutput ->
            ZipOutputStream(fileOutput.buffered()).use { zip ->
                mDatabase.withTransaction {
                    // 在同一一致性读取中计算计数和去重文件 hash
                    val tableCounts = readTableCounts()
                    val fileHashes = readDistinctFileHashes()
                    val manifest = BackupManifest(
                        appVersionCode = BuildConfig.VERSION_CODE,
                        appVersionName = BuildConfig.VERSION_NAME,
                        createdAt = System.currentTimeMillis(),
                        tableCounts = tableCounts,
                        fileCount = fileHashes.size.toLong()
                    )
                    writeBytesEntry(zip, MANIFEST_ENTRY, mCodec.encodeJson(manifest))
                    writeBytesEntry(
                        zip,
                        PREFERENCES_ENTRY,
                        mCodec.encodeJson(BackupPreferencesSnapshot.capture())
                    )
                    onPhase(BackupOperationPhase.ExportingDatabase)
                    writeDatabaseTables(zip)
                    onPhase(BackupOperationPhase.ExportingFiles)
                    fileHashes.forEach { hash ->
                        val file = mFileRepository.getPhysicalFileByHash(hash)
                            ?: throw BackupException.MissingAsset()
                        zip.putNextEntry(ZipEntry("files/$hash"))
                        FileInputStream(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    private suspend fun readTableCounts(): Map<String, Long> = linkedMapOf(
        "tables/characters.jsonl" to mBackupDao.countCharacters(),
        "tables/character_llm_provider_associations.jsonl" to
            mBackupDao.countCharacterLLMProviderAssociations(),
        "tables/lorebooks.jsonl" to mBackupDao.countLorebooks(),
        "tables/lorebook_entries.jsonl" to mBackupDao.countLorebookEntries(),
        "tables/chat_sessions.jsonl" to mBackupDao.countChatSessions(),
        "tables/chat_messages.jsonl" to mBackupDao.countChatMessages(),
        "tables/llm_providers.jsonl" to mBackupDao.countLLMProviders(),
        "tables/files.jsonl" to mBackupDao.countFiles(),
        "tables/group_chat_sessions.jsonl" to mBackupDao.countGroupChatSessions(),
        "tables/group_chat_members.jsonl" to mBackupDao.countGroupChatMembers(),
        "tables/group_chat_messages.jsonl" to mBackupDao.countGroupChatMessages(),
        "tables/group_chat_summaries.jsonl" to mBackupDao.countGroupChatSummaries(),
        "tables/regex_scripts.jsonl" to mBackupDao.countRegexScripts(),
        "tables/regex_character_authorizations.jsonl" to
            mBackupDao.countRegexCharacterAuthorizations(),
        "tables/stories.jsonl" to mBackupDao.countStories(),
        "tables/story_volumes.jsonl" to mBackupDao.countStoryVolumes(),
        "tables/story_chapters.jsonl" to mBackupDao.countStoryChapters(),
        "tables/story_characters.jsonl" to mBackupDao.countStoryCharacters(),
        "tables/story_lorebook_entries.jsonl" to mBackupDao.countStoryLorebookEntries()
    )

    private suspend fun readDistinctFileHashes(): Set<String> {
        val hashes = linkedSetOf<String>()
        var offset = 0
        while (true) {
            val page = mBackupDao.readFiles(BackupContract.PAGE_SIZE, offset)
            page.forEach { hashes += it.hash }
            if (page.size < BackupContract.PAGE_SIZE) break
            offset += page.size
        }
        return hashes
    }

    private suspend fun writeDatabaseTables(zip: ZipOutputStream) {
        writeTable(zip, "tables/characters.jsonl", mBackupDao::readCharacters)
        writeTable(
            zip,
            "tables/character_llm_provider_associations.jsonl",
            mBackupDao::readCharacterLLMProviderAssociations
        )
        writeTable(zip, "tables/lorebooks.jsonl", mBackupDao::readLorebooks)
        writeTable(zip, "tables/lorebook_entries.jsonl", mBackupDao::readLorebookEntries)
        writeTable(zip, "tables/chat_sessions.jsonl", mBackupDao::readChatSessions)
        writeTable(zip, "tables/chat_messages.jsonl", mBackupDao::readChatMessages)
        writeTable(zip, "tables/llm_providers.jsonl", mBackupDao::readLLMProviders)
        writeTable(zip, "tables/files.jsonl", mBackupDao::readFiles)
        writeTable(zip, "tables/group_chat_sessions.jsonl", mBackupDao::readGroupChatSessions)
        writeTable(zip, "tables/group_chat_members.jsonl", mBackupDao::readGroupChatMembers)
        writeTable(zip, "tables/group_chat_messages.jsonl", mBackupDao::readGroupChatMessages)
        writeTable(zip, "tables/group_chat_summaries.jsonl", mBackupDao::readGroupChatSummaries)
        writeTable(zip, "tables/regex_scripts.jsonl", mBackupDao::readRegexScripts)
        writeTable(
            zip,
            "tables/regex_character_authorizations.jsonl",
            mBackupDao::readRegexCharacterAuthorizations
        )
        writeTable(zip, "tables/stories.jsonl", mBackupDao::readStories)
        writeTable(zip, "tables/story_volumes.jsonl", mBackupDao::readStoryVolumes)
        writeTable(zip, "tables/story_chapters.jsonl", mBackupDao::readStoryChapters)
        writeTable(zip, "tables/story_characters.jsonl", mBackupDao::readStoryCharacters)
        writeTable(
            zip,
            "tables/story_lorebook_entries.jsonl",
            mBackupDao::readStoryLorebookEntries
        )
    }

    private suspend fun <T : Any> writeTable(
        zip: ZipOutputStream,
        entryName: String,
        readPage: suspend (Int, Int) -> List<T>
    ) {
        zip.putNextEntry(ZipEntry(entryName))
        val writer = OutputStreamWriter(zip, Charsets.UTF_8)
        var offset = 0
        while (true) {
            val page = readPage(BackupContract.PAGE_SIZE, offset)
            page.forEach { item ->
                writer.write(mCodec.encodeLine(item))
                writer.write('\n'.code)
            }
            writer.flush()
            if (page.size < BackupContract.PAGE_SIZE) break
            offset += page.size
        }
        zip.closeEntry()
    }

    private suspend fun deleteBusinessTables() {
        mBackupDao.deleteAllStoryLorebookEntries()
        mBackupDao.deleteAllStoryCharacters()
        mBackupDao.deleteAllStoryChapters()
        mBackupDao.deleteAllStoryVolumes()
        mBackupDao.deleteAllRegexCharacterAuthorizations()
        mBackupDao.deleteAllRegexScripts()
        mBackupDao.deleteAllGroupChatSummaries()
        mBackupDao.deleteAllGroupChatMessages()
        mBackupDao.deleteAllGroupChatMembers()
        mBackupDao.deleteAllChatMessages()
        mBackupDao.deleteAllChatSessions()
        mBackupDao.deleteAllLorebookEntries()
        mBackupDao.deleteAllCharacterLLMProviderAssociations()
        mBackupDao.deleteAllFiles()
        mBackupDao.deleteAllStories()
        mBackupDao.deleteAllGroupChatSessions()
        mBackupDao.deleteAllLorebooks()
        mBackupDao.deleteAllLLMProviders()
        mBackupDao.deleteAllCharacters()
    }

    private suspend fun insertBusinessTables(backup: ValidatedBackup) {
        restoreTable(backup, "tables/characters.jsonl", Character::class.java, mBackupDao::insertCharacters)
        restoreTable(backup, "tables/llm_providers.jsonl", LLMProvider::class.java, mBackupDao::insertLLMProviders)
        restoreTable(backup, "tables/lorebooks.jsonl", Lorebook::class.java, mBackupDao::insertLorebooks)
        restoreTable(backup, "tables/group_chat_sessions.jsonl", GroupChatSession::class.java, mBackupDao::insertGroupChatSessions)
        restoreTable(backup, "tables/stories.jsonl", Story::class.java, mBackupDao::insertStories)
        restoreTable(backup, "tables/files.jsonl", FileEntity::class.java, mBackupDao::insertFiles)
        restoreTable(
            backup,
            "tables/character_llm_provider_associations.jsonl",
            CharacterLLMProviderAssociation::class.java,
            mBackupDao::insertCharacterLLMProviderAssociations
        )
        restoreTable(backup, "tables/lorebook_entries.jsonl", LorebookEntry::class.java, mBackupDao::insertLorebookEntries)
        restoreTable(backup, "tables/chat_sessions.jsonl", ChatSession::class.java, mBackupDao::insertChatSessions)
        restoreTable(backup, "tables/chat_messages.jsonl", ChatMessage::class.java, mBackupDao::insertChatMessages)
        restoreTable(backup, "tables/group_chat_members.jsonl", GroupChatMember::class.java, mBackupDao::insertGroupChatMembers)
        restoreTable(backup, "tables/group_chat_messages.jsonl", GroupChatMessage::class.java, mBackupDao::insertGroupChatMessages)
        restoreTable(backup, "tables/group_chat_summaries.jsonl", GroupChatSummary::class.java, mBackupDao::insertGroupChatSummaries)
        restoreTable(backup, "tables/regex_scripts.jsonl", RegexScriptEntity::class.java, mBackupDao::insertRegexScripts)
        restoreTable(
            backup,
            "tables/regex_character_authorizations.jsonl",
            RegexCharacterAuthorization::class.java,
            mBackupDao::insertRegexCharacterAuthorizations
        )
        restoreTable(backup, "tables/story_volumes.jsonl", StoryVolume::class.java, mBackupDao::insertStoryVolumes)
        restoreTable(backup, "tables/story_chapters.jsonl", StoryChapter::class.java, mBackupDao::insertStoryChapters)
        restoreTable(backup, "tables/story_characters.jsonl", StoryCharacter::class.java, mBackupDao::insertStoryCharacters)
        restoreTable(
            backup,
            "tables/story_lorebook_entries.jsonl",
            StoryLorebookEntry::class.java,
            mBackupDao::insertStoryLorebookEntries
        )
    }

    private suspend fun <T : Any> restoreTable(
        backup: ValidatedBackup,
        entryName: String,
        type: Class<T>,
        insertBatch: suspend (List<T>) -> Unit
    ) {
        val batch = ArrayList<T>(BackupContract.PAGE_SIZE)
        val file = backup.tableFiles.getValue(entryName)
        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                batch += mCodec.decodeLine(line, type)
                if (batch.size == BackupContract.PAGE_SIZE) {
                    insertBatch(batch.toList())
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) insertBatch(batch)
    }

    private fun writeBytesEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun classifyIoFailure(error: Exception): BackupException {
        val message = error.message.orEmpty().lowercase()
        return if ("no space" in message || "enospc" in message) {
            BackupException.StorageInsufficient(error)
        } else {
            BackupException.GenericFailure(error)
        }
    }

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val PREFERENCES_ENTRY = "preferences.json"
    }
}
