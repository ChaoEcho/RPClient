package me.kafuuneko.rpclient.libs.chat

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChatArchiveRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ChatArchiveRepository
    private var characterId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatArchiveRepository(
            mContext = context,
            mAppDatabase = database,
            mCodec = ChatArchiveCodec(Gson())
        )
        characterId = database.getCharacterDao().insertOrReplace(
            Character(
                name = "Target",
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
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun confirmedCharacterIsUsedAndSummaryBoundaryUsesNewMessageId() = runBlocking {
        val sessionId = repository.saveImport(archive(), characterId)
        val session = database.getChatSessionDao().getSessionById(sessionId)
        val messages = database.getChatMessageDao().getMessagesBySessionId(sessionId)
        val summary = database.getChatMessageDao().getLatestSummaryBySessionId(sessionId)

        assertEquals(characterId, session?.characterId)
        assertEquals("[]", session?.lorebookEntrySet)
        assertEquals("{}", session?.worldInfoStateJson)
        assertEquals("mimo_voice_1", session?.mimoTtsVoiceOverride)
        assertEquals(
            listOf(ChatMessage.Source.User, ChatMessage.Source.Char, ChatMessage.Source.System),
            messages.map { it.source }
        )
        assertEquals(messages[1].id, summary?.coveredMessageId)
        assertEquals("Summary", summary?.content)
    }

    @Test
    fun missingCharacterLeavesNoPartialSession() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.saveImport(archive(), Long.MAX_VALUE)
            }
        }
        runBlocking {
            assertEquals(emptyList<Any>(), database.getChatSessionDao().getAllSessions())
        }
    }

    @Test
    fun exportStreamsMultiplePagesInStableOrderAndPreservesSummaryBoundary() = runBlocking {
        val archive = largeArchive()
        val sessionId = repository.saveImport(archive, characterId)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exportFile = File.createTempFile("chat-export-", ".jsonl", context.cacheDir)

        try {
            repository.exportToUri(sessionId, Uri.fromFile(exportFile))
            val decoded = exportFile.reader(Charsets.UTF_8).use { reader ->
                ChatArchiveCodec(Gson()).decode(reader, fallbackTitle = "Fallback")
            }

            assertEquals(archive.messages.map { it.content }, decoded.messages.map { it.content })
            assertEquals(archive.messages.map { it.role }, decoded.messages.map { it.role })
            assertEquals(archive.summary?.coveredMessageIndex, decoded.summary?.coveredMessageIndex)
            assertEquals(archive.mimoTtsVoiceOverride, decoded.mimoTtsVoiceOverride)
        } finally {
            exportFile.delete()
        }
    }

    private fun archive(): ChatArchive {
        return ChatArchive(
            title = "Imported",
            createTime = 1_000L,
            latestTime = 3_000L,
            userName = "Alice",
            userDescription = "",
            userNote = "",
            creatorNotes = null,
            lorebookEntrySet = "[999]",
            worldInfoStateJson = """{"unsafe":true}""",
            autoSummaryPaused = true,
            characterNameHint = "Source",
            characterFingerprint = null,
            messages = listOf(
                ChatArchiveMessage(1_000L, ChatArchiveMessageRole.User, "Hello"),
                ChatArchiveMessage(2_000L, ChatArchiveMessageRole.Character, "Welcome"),
                ChatArchiveMessage(3_000L, ChatArchiveMessageRole.Narrator, "Rain")
            ),
            summary = ChatArchiveSummary(
                content = "Summary",
                createTime = 4_000L,
                coveredMessageIndex = 1
            ),
            mimoTtsVoiceOverride = "mimo_voice_1"
        )
    }

    private fun largeArchive(): ChatArchive {
        val messages = List(300) { index ->
            ChatArchiveMessage(
                createTime = 1_000L + index / 3,
                role = when (index % 3) {
                    0 -> ChatArchiveMessageRole.User
                    1 -> ChatArchiveMessageRole.Character
                    else -> ChatArchiveMessageRole.Narrator
                },
                content = "Message $index"
            )
        }
        return archive().copy(
            latestTime = messages.last().createTime,
            messages = messages,
            summary = ChatArchiveSummary(
                content = "Large summary",
                createTime = messages.last().createTime + 1L,
                coveredMessageIndex = 257
            )
        )
    }
}
