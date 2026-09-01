package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
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
}
