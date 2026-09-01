package me.kafuuneko.rpclient.libs.backup

import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val root = Files.createTempDirectory("backup_codec_test_").toFile()
    private val gson = Gson()
    private val crypto = BackupCrypto()
    private val codec = BackupCodec(root, gson, crypto)
    private val password = "test password".toCharArray()

    @Test
    fun oldSnapshotMissingAvatarStyleRestoresSuccessfully() {
        val json = gson.toJson(validPreferences())
        val snapshot = gson.fromJson(json, BackupPreferencesSnapshot::class.java)
        snapshot.validate()
        assertEquals(null, snapshot.imageGenerationAvatarStylePrompt)
        assertEquals("", snapshot.imageGenerationAvatarStylePrompt.orEmpty())
    }

    @Test
    fun newSnapshotRoundtripsPreservesAvatarStyle() {
        val prefs = validPreferences().toMutableMap().apply {
            put("imageGenerationAvatarStylePrompt", "anime avatar portrait")
        }
        val json = gson.toJson(prefs)
        val snapshot = gson.fromJson(json, BackupPreferencesSnapshot::class.java)
        snapshot.validate()
        assertEquals("anime avatar portrait", snapshot.imageGenerationAvatarStylePrompt)
    }

    @After
    fun tearDown() {
        password.fill('\u0000')
        root.deleteRecursively()
    }

    @Test
    fun validatesManifestVersionOneAndAllRequiredEmptyTables() {
        val encrypted = createBackup()

        val backup = codec.validate(encrypted, password)

        assertEquals(BackupContract.BACKUP_VERSION, backup.manifest.backupVersion)
        assertEquals(BackupContract.requiredTableEntries.toSet(), backup.tableFiles.keys)
        codec.cleanup(backup)
    }

    @Test
    fun rejectsNewerBackupVersion() {
        val encrypted = createBackup(backupVersion = BackupContract.BACKUP_VERSION + 1)

        assertThrows(BackupException.UnsupportedVersion::class.java) {
            codec.validate(encrypted, password)
        }
    }

    @Test
    fun rejectsMissingRequiredTable() {
        val missing = BackupContract.requiredTableEntries.first()
        val encrypted = createBackup(omittedEntries = setOf(missing))

        assertThrows(BackupException.RestoreValidationFailed::class.java) {
            codec.validate(encrypted, password)
        }
    }

    @Test
    fun rejectsMalformedJsonLine() {
        val entry = "tables/characters.jsonl"
        val encrypted = createBackup(tableContents = mapOf(entry to "not-json\n"))

        assertThrows(BackupException.RestoreValidationFailed::class.java) {
            codec.validate(encrypted, password)
        }
    }

    @Test
    fun rejectsManifestCountMismatch() {
        val entry = "tables/characters.jsonl"
        val encrypted = createBackup(tableCountsOverride = mapOf(entry to 1L))

        assertThrows(BackupException.RestoreValidationFailed::class.java) {
            codec.validate(encrypted, password)
        }
    }

    @Test
    fun rejectsMissingReferencedAsset() {
        val hash = sha256("missing asset".toByteArray())
        val filesLine = gson.toJson(
            mapOf("uuid" to "file-uuid", "hash" to hash, "mimeType" to "text/plain")
        ) + "\n"
        val encrypted = createBackup(
            tableContents = mapOf("tables/files.jsonl" to filesLine),
            tableCountsOverride = mapOf("tables/files.jsonl" to 1L),
            fileCount = 1L
        )

        assertThrows(BackupException.MissingAsset::class.java) {
            codec.validate(encrypted, password)
        }
    }

    /** 生成最小但结构完整的 V1 ZIP，再使用正式容器加密。 */
    private fun createBackup(
        backupVersion: Int = BackupContract.BACKUP_VERSION,
        omittedEntries: Set<String> = emptySet(),
        tableContents: Map<String, String> = emptyMap(),
        tableCountsOverride: Map<String, Long> = emptyMap(),
        fileCount: Long = 0L
    ): File {
        val zipFile = File.createTempFile("payload_", ".zip", root)
        val encryptedFile = File.createTempFile("backup_", BackupContract.FILE_EXTENSION, root)
        val tableCounts = BackupContract.requiredTableEntries.associateWith { entry ->
            tableCountsOverride[entry] ?: tableContents[entry]?.lineSequence()
                ?.count { it.isNotEmpty() }
                ?.toLong()
                ?: 0L
        }
        val manifest = BackupManifest(
            backupVersion = backupVersion,
            appVersionCode = 1,
            appVersionName = "test",
            createdAt = 1L,
            tableCounts = tableCounts,
            fileCount = fileCount
        )

        // 写入清单、偏好和所有未省略的显式表。
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            writeEntry(zip, "manifest.json", gson.toJson(manifest))
            writeEntry(zip, "preferences.json", gson.toJson(validPreferences()))
            BackupContract.requiredTableEntries.forEach { entry ->
                if (entry !in omittedEntries) writeEntry(zip, entry, tableContents[entry].orEmpty())
            }
        }

        // 使用真实加密实现生成测试输入，覆盖 Codec 的解密入口。
        FileInputStream(zipFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                crypto.encrypt(input, output, password)
            }
        }
        zipFile.delete()
        return encryptedFile
    }

    private fun validPreferences(): Map<String, Any> {
        val requiredStrings = listOf(
            "ttsProvider", "ttsSystemLanguageTag", "ttsSystemVoiceName", "ttsMimoBaseUrl",
            "ttsMimoApiKey", "ttsMimoModel", "ttsMimoVoice", "ttsMimoInstructions",
            "ttsAzureApiKey", "ttsAzureRegion", "ttsAzureVoice", "imageGenerationBaseUrl",
            "imageGenerationApiKey", "imageGenerationModel", "imageGenerationSize",
            "imageGenerationStylePrompt", "mainPrompt", "summarizePrompt",
            "postHistoryInstructions", "auxiliaryPrompt", "impersonationPrompt",
            "newChatPrompt", "newExampleChatPrompt", "continueNudgePrompt",
            "replaceEmptyMessagePrompt", "worldInfoFormat", "scenarioFormat",
            "personalityFormat", "userPersonaFormat", "groupNudgePrompt",
            "newGroupChatPrompt", "groupSummarizePrompt", "storyMainPrompt",
            "storyMemoryTemplate", "storySummaryTemplate", "storySummarizePrompt",
            "storyContinuationGuidancePrompt", "storyContinuePrompt", "userName",
            "userAvatar", "userDescription", "summaryInjectionTemplate"
        )
        return buildMap {
            put("version", BackupPreferencesSnapshot.CURRENT_VERSION)
            requiredStrings.forEach { put(it, "") }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
