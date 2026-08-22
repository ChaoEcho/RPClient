package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.chat.ChatCharacterMatcher
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.story.StoryArchiveCodec
import me.kafuuneko.rpclient.libs.story.StoryArchiveRepository
import me.kafuuneko.rpclient.libs.story.StoryCharacterHint
import me.kafuuneko.rpclient.libs.story.StoryImportDraft
import me.kafuuneko.rpclient.libs.story.StoryImportType
import me.kafuuneko.rpclient.libs.story.StoryLorebookHint
import me.kafuuneko.rpclient.libs.story.storyTextHash
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoryRepositoryTest {
    private lateinit var mDatabase: AppDatabase
    private lateinit var mRepository: StoryRepository

    @Before
    fun setup() {
        mDatabase = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        mRepository = StoryRepository(mDatabase)
    }

    @After
    fun tearDown() {
        mDatabase.close()
    }

    @Test
    fun configuration_persistsPrimaryPersonaAndLorebookEntryAndCascadesRelations() = runBlocking {
        val characterId = mDatabase.getCharacterDao().insertOrReplace(testCharacter())
        val entryId = insertLorebookEntry()
        val storyId = mRepository.createStoryWithConfiguration(
            title = " Configured story ",
            includeUserPersona = true,
            lorebookSelections = listOf(StoryLorebookEntrySelection(entryId)),
            characterSelections = listOf(
                StoryCharacterSelection(
                    characterId = characterId,
                    activationMode = StoryCharacter.ACTIVATION_PRIMARY
                )
            ),
            createTime = 10L
        )

        val story = requireNotNull(mRepository.getStory(storyId))
        assertEquals("Configured story", story.title)
        assertTrue(story.includeUserPersona)
        val character = mRepository.getStoryCharacterCandidates(storyId).single()
        assertEquals(StoryCharacter.ACTIVATION_PRIMARY, character.relation.activationMode)
        val lorebook = mRepository.getStoryLorebookEntryCandidates(storyId).single()
        assertEquals(entryId, lorebook.entry.id)

        mRepository.deleteStory(storyId)
        assertNull(mRepository.getStory(storyId))
        assertEquals("Alice", mDatabase.getCharacterDao().getCharacterById(characterId)?.name)
        assertEquals(entryId, mDatabase.getLorebookEntryDao().getEntryById(entryId)?.id)
    }

    @Test
    fun configuration_rejectsMultiplePrimaryCharacters() = runBlocking {
        val firstId = mDatabase.getCharacterDao().insertOrReplace(testCharacter())
        val secondId = mDatabase.getCharacterDao().insertOrReplace(
            testCharacter().copy(name = "Bob")
        )
        val multiplePrimary = runCatching {
            mRepository.createStoryWithConfiguration(
                title = "Invalid",
                lorebookSelections = emptyList(),
                characterSelections = listOf(
                    StoryCharacterSelection(firstId, StoryCharacter.ACTIVATION_PRIMARY),
                    StoryCharacterSelection(secondId, StoryCharacter.ACTIVATION_PRIMARY)
                )
            )
        }
        assertTrue(multiplePrimary.isFailure)
        assertTrue(mRepository.getStoryOverviews().isEmpty())
    }

    @Test
    fun configuration_canAddANewUnboundLorebookEntry() = runBlocking {
        val entryId = insertLorebookEntry()
        val storyId = mRepository.createStory("Story")

        mRepository.updateStoryConfiguration(
            storyId = storyId,
            memory = "",
            summary = "",
            authorNote = "",
            lorebookSelections = listOf(StoryLorebookEntrySelection(entryId)),
            characterSelections = emptyList()
        )

        assertEquals(
            entryId,
            mRepository.getStoryLorebookRuntimeStates(storyId).single().lorebookEntryId
        )
    }

    @Test
    fun generatedEdit_updatesAndRestoresEntryRuntimeStateAtomically() = runBlocking {
        val entryId = insertLorebookEntry()
        val storyId = mRepository.createStoryWithConfiguration(
            title = "Story",
            lorebookSelections = listOf(StoryLorebookEntrySelection(entryId)),
            characterSelections = emptyList(),
            createTime = 10L
        )
        assertTrue(mRepository.updateContent(storyId, 0L, "Before", latestTime = 11L))
        val previousStates = mRepository.getStoryLorebookRuntimeStates(storyId)
        val nextStates = previousStates.map {
            it.copy(activatedAtStep = 1, stickyUntilStep = 3, stateSignature = "active")
        }
        val applied = requireNotNull(
            mRepository.applyGeneratedEdit(
                StoryGeneratedEdit(
                    storyId = storyId,
                    baseRevision = 1L,
                    start = 6,
                    end = 6,
                    originalTextHash = storyTextHash(""),
                    result = " after",
                    nextWorldInfoStates = nextStates
                )
            )
        )

        assertEquals("Before after", applied.content)
        assertEquals(nextStates, mRepository.getStoryLorebookRuntimeStates(storyId))
        assertNull(
            mRepository.applyGeneratedEdit(
                StoryGeneratedEdit(
                    storyId = storyId,
                    baseRevision = applied.revision,
                    start = applied.content.length,
                    end = applied.content.length,
                    originalTextHash = storyTextHash(""),
                    result = " stale",
                    nextWorldInfoStates = nextStates.map {
                        it.copy(lorebookEntryId = Long.MAX_VALUE)
                    }
                )
            )
        )

        val reverted = requireNotNull(
            mRepository.revertGeneratedEdit(
                storyId = storyId,
                expectedRevision = applied.revision,
                start = 6,
                insertedText = " after",
                replacedText = "",
                previousWorldInfoStates = previousStates,
                previousWorldInfoGenerationStep = 0
            )
        )
        assertEquals("Before", reverted.content)
        assertEquals(previousStates, mRepository.getStoryLorebookRuntimeStates(storyId))
    }

    @Test
    fun updatingConfiguration_retainsRuntimeStateOnlyForStillSelectedEntries() = runBlocking {
        val firstId = mDatabase.getCharacterDao().insertOrReplace(testCharacter())
        val secondId = mDatabase.getCharacterDao().insertOrReplace(testCharacter().copy(name = "Bob"))
        val retainedEntryId = insertLorebookEntry("Retained")
        val removedEntryId = insertLorebookEntry("Removed")
        val storyId = mRepository.createStoryWithConfiguration(
            title = "Story",
            lorebookSelections = listOf(
                StoryLorebookEntrySelection(retainedEntryId),
                StoryLorebookEntrySelection(removedEntryId)
            ),
            characterSelections = listOf(
                StoryCharacterSelection(firstId, StoryCharacter.ACTIVATION_PRIMARY),
                StoryCharacterSelection(secondId, StoryCharacter.ACTIVATION_AUTO)
            )
        )
        val activeStates = mRepository.getStoryLorebookRuntimeStates(storyId).map {
            it.copy(activatedAtStep = 1, stateSignature = "active")
        }
        requireNotNull(
            mRepository.applyGeneratedEdit(
                StoryGeneratedEdit(
                    storyId = storyId,
                    baseRevision = 0L,
                    start = 0,
                    end = 0,
                    originalTextHash = storyTextHash(""),
                    result = "AI",
                    nextWorldInfoStates = activeStates
                )
            )
        )

        mRepository.updateStoryConfiguration(
            storyId = storyId,
            memory = "",
            summary = "",
            authorNote = "",
            lorebookSelections = listOf(StoryLorebookEntrySelection(retainedEntryId)),
            characterSelections = listOf(
                StoryCharacterSelection(firstId, StoryCharacter.ACTIVATION_AUTO),
                StoryCharacterSelection(secondId, StoryCharacter.ACTIVATION_PRIMARY)
            )
        )

        val state = mRepository.getStoryLorebookRuntimeStates(storyId).single()
        assertEquals(retainedEntryId, state.lorebookEntryId)
        assertEquals(1, state.activatedAtStep)
    }

    @Test
    fun archiveImport_restoresPrimaryAndLorebookHints() = runBlocking {
        val character = testCharacter()
        mDatabase.getCharacterDao().insertOrReplace(character)
        val entryId = insertLorebookEntry()
        val entry = requireNotNull(mDatabase.getLorebookEntryDao().getEntryById(entryId))
        val gson = Gson()
        val archiveRepository = StoryArchiveRepository(
            InstrumentationRegistry.getInstrumentation().targetContext,
            mDatabase,
            StoryArchiveCodec(gson)
        )

        val storyId = archiveRepository.saveImport(
            draft = StoryImportDraft(
                title = "Imported",
                content = "正文",
                includeUserPersona = true,
                characterHints = listOf(
                    StoryCharacterHint(
                        name = character.name,
                        fingerprint = ChatCharacterMatcher.fingerprintOf(character),
                        activationMode = StoryArchiveCodec.MODE_PRIMARY
                    )
                ),
                lorebookHints = listOf(
                    StoryLorebookHint(
                        lorebookName = "World",
                        entryName = entry.name,
                        fingerprint = storyTextHash(entry.content)
                    )
                ),
                type = StoryImportType.Archive
            ),
            title = "Confirmed title"
        )

        assertTrue(requireNotNull(mRepository.getStory(storyId)).includeUserPersona)
        assertEquals(
            StoryCharacter.ACTIVATION_PRIMARY,
            mRepository.getStoryCharacterCandidates(storyId).single().relation.activationMode
        )
        assertEquals(
            entryId,
            mRepository.getStoryLorebookEntryCandidates(storyId).single().entry.id
        )
    }

    private suspend fun insertLorebookEntry(name: String = "Station"): Long {
        val lorebook = mDatabase.getLorebookDao().getAllLorebooks().firstOrNull()
            ?: Lorebook(name = "World").let {
                it.copy(id = mDatabase.getLorebookDao().insertOrReplace(it))
            }
        return mDatabase.getLorebookEntryDao().insertOrReplace(
            LorebookEntry(
                lorebookId = lorebook.id,
                name = name,
                keywords = "[]",
                secondaryKeywords = "[]",
                order = 0,
                depth = 0,
                category = "[]",
                content = "The station was abandoned years ago."
            )
        )
    }

    private fun testCharacter(): Character {
        return Character(
            name = "Alice",
            avatar = "",
            characterTags = "[]",
            description = "A careful investigator",
            personality = "Patient",
            scenario = "An old city",
            firstMessages = "",
            examplesOfDialogue = "",
            postHistoryInstructions = ""
        )
    }
}
