package me.kafuuneko.rpclient.libs.character

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.regex.RegexScript
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.regex.RegexScriptRepository
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope
import me.kafuuneko.rpclient.libs.regex.RegexScriptTarget
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterCardRepositoryUpdateTest {
    private lateinit var database: AppDatabase
    private lateinit var characterRepository: CharacterRepository
    private lateinit var lorebookRepository: LorebookRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var regexRepository: RegexScriptRepository
    private lateinit var repository: CharacterCardRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val gson = Gson()
        val regexCodec = RegexScriptCodec(gson)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        characterRepository = CharacterRepository(database, gson, regexCodec)
        lorebookRepository = LorebookRepository(database, gson, context)
        chatRepository = ChatRepository(database, gson, FileRepository(context, database))
        regexRepository = RegexScriptRepository(context, gson, database, regexCodec)
        repository = CharacterCardRepository(
            context,
            gson,
            characterRepository,
            lorebookRepository,
            FileRepository(context, database),
            regexCodec,
            regexRepository
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun updatePreservesIdentityAvatarChatsAndSummaryWhileRebindingLorebook() = runBlocking {
        val oldBookId = lorebookRepository.saveImport(
            CharacterBookImport(
                lorebook = Lorebook(name = "Old book"),
                entries = listOf(entry(name = "Old entry", content = "old"))
            )
        )
        val oldEntryId = lorebookRepository.getEntriesByLorebookId(oldBookId).single().id
        val providerId = database.getLLMProviderDao().insertOrReplace(
            LLMProvider(
                name = "Stable provider",
                providerType = LLMProviderType.Custom,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "https://example.invalid",
                model = "model"
            )
        )
        val characterId = characterRepository.saveCharacterWithLLMProvider(
            character = character(name = "Old name").copy(
                avatar = "stable-avatar-uuid",
                characterLorebookId = oldBookId
            ),
            llmProviderId = providerId
        )
        val sessionId = chatRepository.createSession(
            characterId = characterId,
            title = "Existing chat",
            userNote = "note",
            userName = "User",
            userDescription = "persona",
            lorebookEntryIds = listOf(oldEntryId),
            createTime = 1L
        )
        val messageId = chatRepository.createMessage(
            sessionId = sessionId,
            source = ChatMessage.Source.User,
            content = "existing message",
            createTime = 2L
        )
        chatRepository.saveSummary(
            sessionId = sessionId,
            content = "existing memory",
            coveredMessageId = messageId,
            createTime = 3L
        )
        val originalSession = checkNotNull(chatRepository.getSessionById(sessionId))

        repository.updateFromDraft(
            characterId,
            CharacterCardImportDraft(
                card = CharacterCardImport(
                    character = character(name = "Imported name").copy(
                        avatar = "must-not-replace-avatar",
                        description = "updated description"
                    ),
                    embeddedLorebook = CharacterBookImport(
                        lorebook = Lorebook(name = "Imported book"),
                        entries = listOf(entry(name = "New entry", content = "new"))
                    ),
                    regexScripts = listOf(
                        RegexScript(
                            id = "updated-script",
                            scriptName = "Updated script",
                            findRegex = "old",
                            replaceString = "new",
                            placement = listOf(2)
                        )
                    )
                ),
                avatarSourceUri = null,
                avatarMimeType = "application/json"
            )
        )

        val updated = checkNotNull(characterRepository.getCharacterById(characterId))
        assertEquals(characterId, updated.id)
        assertEquals("stable-avatar-uuid", updated.avatar)
        assertEquals("Imported name", updated.name)
        assertEquals("updated description", updated.description)
        assertEquals(providerId, characterRepository.getLLMProviderId(characterId))
        assertNotEquals(oldBookId, updated.characterLorebookId)
        assertNotNull(lorebookRepository.getLorebookById(oldBookId))
        assertEquals("Imported book", lorebookRepository.getLorebookById(updated.characterLorebookId)?.name)
        assertEquals(
            originalSession.lorebookEntrySet,
            chatRepository.getSessionById(sessionId)?.lorebookEntrySet
        )
        assertEquals("existing message", chatRepository.getMessagesBySessionId(sessionId).single().content)
        assertEquals("existing memory", chatRepository.getLatestSummary(sessionId)?.content)
        assertEquals(
            listOf("updated-script"),
            regexRepository.getScripts(
                RegexScriptTarget(RegexScriptScope.Character, characterId)
            ).map { it.id }
        )

        val importedBookId = updated.characterLorebookId
        repository.updateFromDraft(
            characterId,
            CharacterCardImportDraft(
                card = CharacterCardImport(
                    character = character(name = "No-book update"),
                    embeddedLorebook = null
                ),
                avatarSourceUri = null,
                avatarMimeType = "application/json"
            )
        )
        assertEquals(0L, characterRepository.getCharacterById(characterId)?.characterLorebookId)
        assertEquals(providerId, characterRepository.getLLMProviderId(characterId))
        assertEquals(
            emptyList<RegexScript>(),
            regexRepository.getScripts(
                RegexScriptTarget(RegexScriptScope.Character, characterId)
            )
        )
        assertNotNull(lorebookRepository.getLorebookById(oldBookId))
        assertNotNull(lorebookRepository.getLorebookById(importedBookId))
        assertEquals(
            originalSession.lorebookEntrySet,
            chatRepository.getSessionById(sessionId)?.lorebookEntrySet
        )
    }

    private fun character(name: String) = Character(
        name = name,
        avatar = "",
        characterTags = "[]",
        description = "description",
        personality = "personality",
        scenario = "scenario",
        firstMessages = "hello",
        examplesOfDialogue = "example",
        postHistoryInstructions = "post"
    )

    private fun entry(name: String, content: String) = LorebookEntry(
        lorebookId = 0L,
        name = name,
        keywords = "[]",
        secondaryKeywords = "[]",
        order = 100,
        depth = 4,
        category = "[]",
        content = content
    )
}
