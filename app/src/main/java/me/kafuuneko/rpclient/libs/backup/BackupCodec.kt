package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParseException
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
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * 负责加密容器解包、ZIP staging 与完整格式校验。
 *
 * Room 导出和恢复仍由 [BackupRepository] 显式调度，避免 Codec 获得数据库职责。
 */
class BackupCodec internal constructor(
    private val mCacheDirectory: File,
    private val mGson: Gson,
    private val mCrypto: BackupCrypto
) {
    constructor(context: Context, gson: Gson, crypto: BackupCrypto) : this(
        context.cacheDir,
        gson,
        crypto
    )
    /** 解密并完整验证备份，失败时始终清理包含明文的 staging。 */
    fun validate(encryptedFile: File, password: CharArray): ValidatedBackup {
        val stagingDirectory = File(
            mCacheDirectory,
            "backup_restore_${UUID.randomUUID()}"
        )
        if (!stagingDirectory.mkdirs()) throw BackupException.StorageInsufficient()
        var success = false
        try {
            // 先完成 GCM 认证并落地临时 ZIP，错误密码不会进入恢复阶段
            val payloadFile = File(stagingDirectory, PAYLOAD_FILE_NAME)
            FileInputStream(encryptedFile).use { input ->
                FileOutputStream(payloadFile).use { output ->
                    mCrypto.decrypt(input, output, password)
                }
            }
            // 将 ZIP 安全展开到 staging 后再解析任何业务数据
            val contentDirectory = File(stagingDirectory, CONTENT_DIRECTORY_NAME)
            if (!contentDirectory.mkdirs()) throw BackupException.StorageInsufficient()
            extractZip(payloadFile, contentDirectory)
            payloadFile.delete()
            val validatedBackup = validateExtracted(stagingDirectory, contentDirectory)
            success = true
            return validatedBackup
        } catch (error: BackupException) {
            throw error
        } catch (error: Exception) {
            throw BackupException.RestoreValidationFailed(error)
        } finally {
            if (!success) stagingDirectory.deleteRecursively()
        }
    }

    /** 读取已验证快照中的偏好对象。 */
    fun readPreferences(backup: ValidatedBackup): BackupPreferencesSnapshot {
        return try {
            mGson.fromJson(
                backup.preferencesFile.readText(Charsets.UTF_8),
                BackupPreferencesSnapshot::class.java
            ).also { it.validate() }
        } catch (error: BackupException) {
            throw error
        } catch (error: Exception) {
            throw BackupException.RestoreValidationFailed(error)
        }
    }

    /** 清理包含解密内容的私有 staging。 */
    fun cleanup(backup: ValidatedBackup) {
        backup.stagingDirectory.deleteRecursively()
    }

    /** 将单个对象编码为适合 JSONL 的紧凑单行 JSON。 */
    fun encodeLine(value: Any): String = mGson.toJson(value)

    /** 将对象编码为 UTF-8 JSON。 */
    fun encodeJson(value: Any): ByteArray = mGson.toJson(value).toByteArray(Charsets.UTF_8)

    /** 将已通过完整校验的 JSONL 单行解码为指定实体。 */
    fun <T> decodeLine(line: String, type: Class<T>): T = mGson.fromJson(line, type)

    private fun extractZip(payloadFile: File, contentDirectory: File) {
        val seenEntries = mutableSetOf<String>()
        ZipInputStream(FileInputStream(payloadFile).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val entryName = entry.name
                // 拒绝重复、绝对路径、反斜杠和目录穿越
                if (!seenEntries.add(entryName) ||
                    entryName.startsWith('/') ||
                    '\\' in entryName ||
                    entryName.split('/').any { it == ".." }
                ) {
                    throw BackupException.RestoreValidationFailed()
                }
                val target = File(contentDirectory, entryName)
                val rootPath = contentDirectory.canonicalPath + File.separator
                if (!target.canonicalPath.startsWith(rootPath)) {
                    throw BackupException.RestoreValidationFailed()
                }
                if (entry.isDirectory) {
                    if (!target.mkdirs() && !target.isDirectory) {
                        throw BackupException.StorageInsufficient()
                    }
                } else {
                    val parent = target.parentFile
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory) {
                        throw BackupException.StorageInsufficient()
                    }
                    FileOutputStream(target).use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun validateExtracted(stagingDirectory: File, contentDirectory: File): ValidatedBackup {
        // 清单和偏好必须存在且能按当前 V1 契约解析
        val manifestFile = File(contentDirectory, MANIFEST_ENTRY)
        val preferencesFile = File(contentDirectory, PREFERENCES_ENTRY)
        if (!manifestFile.isFile || !preferencesFile.isFile) {
            throw BackupException.RestoreValidationFailed()
        }
        val manifest = parseManifest(manifestFile)
        parsePreferences(preferencesFile)
        // 每张显式业务表都必须存在、逐行可解析且计数一致
        val tableFiles = linkedMapOf<String, File>()
        BackupContract.requiredTableEntries.forEach { entryName ->
            val tableFile = File(contentDirectory, entryName)
            if (!tableFile.isFile) throw BackupException.RestoreValidationFailed()
            val type = requireNotNull(TABLE_TYPES[entryName])
            val actualCount = validateJsonLines(tableFile, type)
            if (manifest.tableCounts[entryName] != actualCount) {
                throw BackupException.RestoreValidationFailed()
            }
            tableFiles[entryName] = tableFile
        }
        if (manifest.tableCounts.keys != BackupContract.requiredTableEntries.toSet()) {
            throw BackupException.RestoreValidationFailed()
        }
        // FileEntity 引用的每个 hash 都必须有内容正确的物理资产
        val referencedHashes = readFileHashes(tableFiles.getValue(FILES_TABLE_ENTRY))
        val assetFiles = validateAssets(File(contentDirectory, FILES_DIRECTORY), referencedHashes)
        if (manifest.fileCount != assetFiles.size.toLong()) {
            throw BackupException.RestoreValidationFailed()
        }
        return ValidatedBackup(
            manifest = manifest,
            stagingDirectory = stagingDirectory,
            preferencesFile = preferencesFile,
            tableFiles = tableFiles,
            assetFiles = assetFiles
        )
    }

    private fun parseManifest(file: File): BackupManifest {
        val manifest = try {
            mGson.fromJson(file.readText(Charsets.UTF_8), BackupManifest::class.java)
        } catch (error: Exception) {
            throw BackupException.RestoreValidationFailed(error)
        }
        if (manifest.format != BackupContract.FORMAT) throw BackupException.UnsupportedFormat()
        if (manifest.backupVersion > BackupContract.BACKUP_VERSION) {
            throw BackupException.UnsupportedVersion()
        }
        if (manifest.backupVersion != BackupContract.BACKUP_VERSION ||
            manifest.containerVersion != BackupContract.CONTAINER_VERSION ||
            manifest.tableCounts == null
        ) {
            throw BackupException.RestoreValidationFailed()
        }
        return manifest
    }

    private fun parsePreferences(file: File) {
        try {
            mGson.fromJson(
                file.readText(Charsets.UTF_8),
                BackupPreferencesSnapshot::class.java
            ).validate()
        } catch (error: BackupException) {
            throw error
        } catch (error: Exception) {
            throw BackupException.RestoreValidationFailed(error)
        }
    }

    private fun validateJsonLines(file: File, type: Class<*>): Long {
        var count = 0L
        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) throw BackupException.RestoreValidationFailed()
                try {
                    mGson.fromJson(line, type) ?: throw BackupException.RestoreValidationFailed()
                } catch (error: BackupException) {
                    throw error
                } catch (error: JsonParseException) {
                    throw BackupException.RestoreValidationFailed(error)
                }
                count++
            }
        }
        return count
    }

    private fun readFileHashes(file: File): Set<String> {
        val hashes = linkedSetOf<String>()
        file.forEachLine(Charsets.UTF_8) { line ->
            val entity = try {
                mGson.fromJson(line, FileEntity::class.java)
            } catch (error: Exception) {
                throw BackupException.RestoreValidationFailed(error)
            }
            if (!HASH_PATTERN.matches(entity.hash)) throw BackupException.RestoreValidationFailed()
            hashes += entity.hash
        }
        return hashes
    }

    private fun validateAssets(directory: File, requiredHashes: Set<String>): Map<String, File> {
        val result = linkedMapOf<String, File>()
        val files = directory.listFiles().orEmpty()
        files.forEach { file ->
            if (!file.isFile || !HASH_PATTERN.matches(file.name)) {
                throw BackupException.RestoreValidationFailed()
            }
            if (sha256(file) != file.name) throw BackupException.RestoreValidationFailed()
            result[file.name] = file
        }
        if (result.keys != requiredHashes) throw BackupException.MissingAsset()
        return result
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val PREFERENCES_ENTRY = "preferences.json"
        const val FILES_DIRECTORY = "files"
        const val FILES_TABLE_ENTRY = "tables/files.jsonl"
        const val PAYLOAD_FILE_NAME = "payload.zip"
        const val CONTENT_DIRECTORY_NAME = "content"
        val HASH_PATTERN = Regex("[0-9a-f]{64}")
        val TABLE_TYPES = mapOf(
            "tables/characters.jsonl" to Character::class.java,
            "tables/character_llm_provider_associations.jsonl" to
                CharacterLLMProviderAssociation::class.java,
            "tables/lorebooks.jsonl" to Lorebook::class.java,
            "tables/lorebook_entries.jsonl" to LorebookEntry::class.java,
            "tables/chat_sessions.jsonl" to ChatSession::class.java,
            "tables/chat_messages.jsonl" to ChatMessage::class.java,
            "tables/llm_providers.jsonl" to LLMProvider::class.java,
            FILES_TABLE_ENTRY to FileEntity::class.java,
            "tables/group_chat_sessions.jsonl" to GroupChatSession::class.java,
            "tables/group_chat_members.jsonl" to GroupChatMember::class.java,
            "tables/group_chat_messages.jsonl" to GroupChatMessage::class.java,
            "tables/group_chat_summaries.jsonl" to GroupChatSummary::class.java,
            "tables/regex_scripts.jsonl" to RegexScriptEntity::class.java,
            "tables/regex_character_authorizations.jsonl" to
                RegexCharacterAuthorization::class.java,
            "tables/stories.jsonl" to Story::class.java,
            "tables/story_volumes.jsonl" to StoryVolume::class.java,
            "tables/story_chapters.jsonl" to StoryChapter::class.java,
            "tables/story_characters.jsonl" to StoryCharacter::class.java,
            "tables/story_lorebook_entries.jsonl" to StoryLorebookEntry::class.java
        )
    }
}
