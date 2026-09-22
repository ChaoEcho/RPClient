package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.generation.DataMaintenance
import me.kafuuneko.rpclient.libs.generation.launchDataTask
import java.io.FileInputStream
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupRepositoryRoundTripTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: BackupRepository
    private lateinit var fileRepository: FileRepository
    private lateinit var isolatedRoot: File
    private lateinit var originalUserName: String
    private var originalDefaultProvidersInitialized = false

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        isolatedRoot = File(targetContext.cacheDir, "backup_test_${UUID.randomUUID()}")
        val isolatedContext = object : ContextWrapper(targetContext) {
            override fun getCacheDir(): File = File(isolatedRoot, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir(): File = File(isolatedRoot, "no_backup").apply { mkdirs() }

            override fun getDir(name: String, mode: Int): File =
                File(isolatedRoot, name).apply { mkdirs() }
        }
        database = Room.inMemoryDatabaseBuilder(isolatedContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fileRepository = FileRepository(isolatedContext, database)
        val crypto = BackupCrypto()
        repository = BackupRepository(
            mContext = isolatedContext,
            mDatabase = database,
            mFileRepository = fileRepository,
            mCodec = BackupCodec(isolatedContext, Gson(), crypto),
            mCrypto = crypto
        )
        originalUserName = AppModel.userName
        originalDefaultProvidersInitialized = AppModel.llmDefaultProvidersInitialized
    }

    @After
    fun tearDown() {
        DataMaintenance.barrier.recovered()
        AppModel.userName = originalUserName
        AppModel.llmDefaultProvidersInitialized = originalDefaultProvidersInitialized
        database.close()
        isolatedRoot.deleteRecursively()
    }

    @Test
    fun encryptedBackup_roundTripsIdsFilesAndPreferences() = runBlocking {
        val payload = "round-trip asset".toByteArray()
        val source = File.createTempFile("backup-source-", ".bin", isolatedRoot).apply {
            writeBytes(payload)
        }
        val fileUuid = fileRepository.saveFile(source, "application/octet-stream")
        val fileEntity = requireNotNull(fileRepository.getFileEntity(fileUuid))
        val characterId = database.getCharacterDao().insertOrReplace(
            Character(
                name = "Backup Character",
                avatar = fileUuid,
                characterTags = "[]",
                description = "description",
                personality = "personality",
                scenario = "scenario",
                firstMessages = "hello",
                examplesOfDialogue = "",
                postHistoryInstructions = ""
            )
        )
        AppModel.userName = "Backup Alice"

        val password = "correct horse battery staple".toCharArray()
        val encrypted = try {
            repository.createEncryptedBackupFile(password)
        } finally {
            password.fill('\u0000')
        }

        try {
            database.clearAllTables()
            assertEquals(null, database.getCharacterDao().getCharacterById(characterId))
            assertEquals(null, fileRepository.getFileEntity(fileUuid))
            assertNotNull(fileRepository.getPhysicalFileByHash(fileEntity.hash))
            fileRepository.getPhysicalFileByHash(fileEntity.hash)?.delete()
            AppModel.userName = "Changed After Export"
            AppModel.llmDefaultProvidersInitialized = false

            val restorePassword = "correct horse battery staple".toCharArray()
            val validated = try {
                repository.validateEncryptedBackup(encrypted, restorePassword)
            } finally {
                restorePassword.fill('\u0000')
            }
            assertEquals(1L, validated.manifest.tableCounts["tables/characters.jsonl"])
            assertEquals(1L, validated.manifest.tableCounts["tables/files.jsonl"])

            repository.restore(validated)

            val restoredCharacter = database.getCharacterDao().getCharacterById(characterId)
            assertEquals("Backup Character", restoredCharacter?.name)
            assertEquals(fileUuid, restoredCharacter?.avatar)
            assertEquals(fileEntity, fileRepository.getFileEntity(fileUuid))
            assertArrayEquals(payload, fileRepository.getFile(fileUuid)?.readBytes())
            assertEquals("Backup Alice", AppModel.userName)
            assertEquals(true, AppModel.llmDefaultProvidersInitialized)
        } finally {
            encrypted.delete()
            source.delete()
        }
    }
    @Test
    fun failedRestoreRollsBackRowsSettingsAndTheLastBackgroundPartial() = runBlocking<Unit> {
        val id = insertRecoveryCharacter("Incoming")
        AppModel.userName = "Incoming name"
        val password = "fixture restore password".toCharArray()
        val encrypted = repository.createEncryptedBackupFile(password)
        val incoming = repository.validateEncryptedBackup(encrypted, password)
        val current = requireNotNull(database.getCharacterDao().getCharacterById(id)).copy(name = "Current")
        database.getCharacterDao().update(current)
        AppModel.userName = "Current name"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        val task = scope.launchDataTask {
            try { entered.complete(Unit); awaitCancellation() }
            finally {
                withContext(NonCancellable) {
                    database.getCharacterDao().update(current.copy(name = "Saved partial"))
                }
            }
        }
        try {
            entered.await()
            val outcome = runCatching {
                repository.restore(incoming) { phase ->
                    if (phase == BackupOperationPhase.RestoringSettings) error("fixture restore failure")
                }
            }
            assertTrue(outcome.isFailure)
            task.join()
            assertEquals("Saved partial", database.getCharacterDao().getCharacterById(id)?.name)
            assertEquals("Current name", AppModel.userName)
            assertFalse(RestoreJournal(File(isolatedRoot, "no_backup/restore")).isPending)
            assertFalse(DataMaintenance.state.value.recoveryRequired)
        } finally {
            scope.cancel()
            encrypted.delete()
            password.fill('\u0000')
        }
    }

    @Test
    fun startupRecoveryRestoresAnInterruptedReplacementIncludingAssets() = runBlocking<Unit> {
        val source = File(isolatedRoot, "fixture-asset").apply { writeText("original bytes") }
        val uuid = fileRepository.saveFile(source, "application/octet-stream")
        val id = insertRecoveryCharacter("Original", uuid)
        AppModel.userName = "Original name"
        val password = "fixture rollback password".toCharArray()
        val encrypted = repository.createEncryptedBackupFile(password)
        val journal = RestoreJournal(File(isolatedRoot, "no_backup/restore"))
        try {
            journal.prepare(AppModel.llmDefaultProvidersInitialized) { target ->
                FileInputStream(encrypted).use { input ->
                    FileOutputStream(target).use { output -> BackupCrypto().decrypt(input, output, password) }
                }
            }
            // 模拟进程在替换数据后、提交完整恢复标记前终止。
            val original = requireNotNull(database.getCharacterDao().getCharacterById(id))
            database.getCharacterDao().update(original.copy(name = "Interrupted", avatar = ""))
            fileRepository.deleteFile(uuid)
            AppModel.userName = "Interrupted name"
            repository.recoverInterruptedRestore()
            assertEquals("Original", database.getCharacterDao().getCharacterById(id)?.name)
            assertEquals(uuid, database.getCharacterDao().getCharacterById(id)?.avatar)
            assertEquals("original bytes", fileRepository.getFile(uuid)?.readText())
            assertEquals("Original name", AppModel.userName)
            assertFalse(journal.isPending)
            assertFalse(journal.snapshot.exists())
        } finally {
            encrypted.delete()
            password.fill('\u0000')
        }
    }

    @Test
    fun invalidRecoveryJournalDoesNotOpenBusinessDataOrEraseTheSnapshot() = runBlocking<Unit> {
        val journal = RestoreJournal(File(isolatedRoot, "no_backup/restore"))
        journal.prepare(false) { it.writeText("invalid fixture archive") }
        assertTrue(runCatching { repository.recoverInterruptedRestore() }.exceptionOrNull() is RestoreRecoveryRequiredException)
        assertTrue(DataMaintenance.state.value.recoveryRequired)
        assertTrue(journal.snapshot.isFile)
        assertTrue(journal.isPending)
    }

    private suspend fun insertRecoveryCharacter(name: String, avatar: String = ""): Long =
        database.getCharacterDao().insertOrReplace(Character(
            name = name, avatar = avatar, characterTags = "[]", description = "",
            personality = "", scenario = "", firstMessages = "", examplesOfDialogue = "",
            postHistoryInstructions = ""
        ))

}
