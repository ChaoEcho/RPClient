package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatRepositoryImageLifecycleTest {
    private lateinit var database: AppDatabase
    private lateinit var fileRepository: FileRepository
    private lateinit var repository: ChatRepository
    private var sessionId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fileRepository = FileRepository(context, database)
        repository = ChatRepository(database, Gson(), fileRepository)
        val characterId = database.getCharacterDao().insertOrReplace(
            Character(
                name = "Character",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = ""
            )
        )
        sessionId = database.getChatSessionDao().insertOrReplace(
            ChatSession(
                characterId = characterId,
                createTime = 1L,
                latestTime = 1L,
                lorebookEntrySet = "[]",
                title = "Test",
                userNote = ""
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replaceGuardsContentAndCleansOldFileAfterCommit() = runBlocking {
        val messageId = repository.createMessage(sessionId, ChatMessage.Source.Char, "old")
        val oldUuid = fileRepository.saveBytes(byteArrayOf(1, 2, 3), "image/png")
        assertTrue(repository.replaceMessageImage(messageId, "old", oldUuid))

        val newUuid = fileRepository.saveBytes(byteArrayOf(4, 5, 6), "image/png")
        assertTrue(repository.replaceMessageImage(messageId, "old", newUuid))
        assertEquals(newUuid, repository.getMessageById(messageId)?.imageFileUuid)
        assertNull(fileRepository.getFileEntity(oldUuid))
        assertTrue(fileRepository.getFileEntity(newUuid) != null)

        val rejectedUuid = fileRepository.saveBytes(byteArrayOf(7, 8, 9), "image/png")
        repository.updateMessageContent(messageId, "edited")
        assertFalse(repository.replaceMessageImage(messageId, "old", rejectedUuid))
        assertTrue(fileRepository.getFileEntity(rejectedUuid) != null)
        assertNull(repository.getMessageById(messageId)?.imageFileUuid)
        assertNull(fileRepository.getFileEntity(newUuid))
    }

    @Test
    fun deleteMessageAndSessionCleanAttachedImages() = runBlocking {
        val firstMessageId = repository.createMessage(sessionId, ChatMessage.Source.Char, "first")
        val firstUuid = fileRepository.saveBytes(byteArrayOf(20, 21, 22), "image/png")
        assertTrue(repository.replaceMessageImage(firstMessageId, "first", firstUuid))

        repository.deleteMessage(firstMessageId)
        assertNull(repository.getMessageById(firstMessageId))
        assertNull(fileRepository.getFileEntity(firstUuid))

        val secondMessageId = repository.createMessage(sessionId, ChatMessage.Source.Char, "second")
        val secondUuid = fileRepository.saveBytes(byteArrayOf(23, 24, 25), "image/png")
        assertTrue(repository.replaceMessageImage(secondMessageId, "second", secondUuid))

        repository.deleteSession(sessionId)
        assertNull(repository.getSessionById(sessionId))
        assertNull(fileRepository.getFileEntity(secondUuid))
    }

    @Test
    fun editRegenerateDeleteAndBranchDoNotRetainImageLinks() = runBlocking {
        val messageId = repository.createMessage(sessionId, ChatMessage.Source.Char, "reply", createTime = 2L)
        val imageUuid = fileRepository.saveBytes(byteArrayOf(10, 11, 12), "image/png")
        assertTrue(repository.replaceMessageImage(messageId, "reply", imageUuid))

        val branchId = repository.createBranchSession(sessionId, messageId, "Branch", createTime = 10L)
        assertNull(repository.getMessageById(repository.getMessagesBySessionId(branchId).single().id)?.imageFileUuid)
        assertTrue(fileRepository.getFileEntity(imageUuid) != null)

        repository.commitGenerationResult(
            sessionId = sessionId,
            messageId = messageId,
            source = ChatMessage.Source.Char,
            content = "regenerated",
            deleteEmptyPlaceholder = false,
            worldInfoStateJson = "{}"
        )
        assertNull(repository.getMessageById(messageId)?.imageFileUuid)
        assertNull(fileRepository.getFileEntity(imageUuid))

        val secondUuid = fileRepository.saveBytes(byteArrayOf(13, 14, 15), "image/png")
        assertTrue(repository.replaceMessageImage(messageId, "regenerated", secondUuid))
        repository.deleteMessagesBySessionId(sessionId)
        assertNull(fileRepository.getFileEntity(secondUuid))
    }
}
