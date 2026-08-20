package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.story.storyTextHash
import me.kafuuneko.rpclient.libs.story.StoryArchiveCodec
import me.kafuuneko.rpclient.libs.story.StoryArchiveRepository
import me.kafuuneko.rpclient.libs.story.StoryCharacterHint
import me.kafuuneko.rpclient.libs.story.StoryImportDraft
import me.kafuuneko.rpclient.libs.story.StoryImportType
import me.kafuuneko.rpclient.libs.chat.ChatCharacterMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        mRepository = StoryRepository(mDatabase, Gson())
    }

    @After
    fun tearDown() {
        mDatabase.close()
    }

    @Test
    fun revisionAndConfiguration_updatesAreAtomicAndRelationsCascadeOnly() = runBlocking {
        val characterId = mDatabase.getCharacterDao().insertOrReplace(testCharacter())
        val lorebookId = mDatabase.getLorebookDao().insertOrReplace(Lorebook(name = "World"))
        val entryId = mDatabase.getLorebookEntryDao().insertOrReplace(
            testLorebookEntry(lorebookId)
        )
        val storyId = mRepository.createStory(" Story ", createTime = 10L)

        assertTrue(mRepository.updateContent(storyId, 0L, "First draft", latestTime = 11L))
        assertFalse(mRepository.updateContent(storyId, 0L, "Stale draft", latestTime = 12L))
        assertEquals("First draft", mRepository.getStory(storyId)?.content)
        assertEquals(1L, mRepository.getStory(storyId)?.contentRevision)

        mRepository.updateStoryConfiguration(
            storyId = storyId,
            memory = "Long-term fact",
            summary = "Current summary",
            authorNote = "Close third person",
            lorebookEntryIds = listOf(entryId, entryId),
            characterSelections = listOf(
                StoryCharacterSelection(
                    characterId = characterId,
                    activationMode = StoryCharacter.ACTIVATION_AUTO,
                    activationKeys = listOf("  Ally  ", "Ally", "")
                )
            ),
            latestTime = 13L
        )

        val candidate = mRepository.getStoryCharacterCandidates(storyId).single()
        assertEquals(listOf("Ally"), candidate.activationKeys)
        assertEquals(listOf(entryId), mRepository.getLorebookEntryIds(requireNotNull(mRepository.getStory(storyId))))

        mRepository.deleteStory(storyId)
        assertNull(mRepository.getStory(storyId))
        assertEquals("Alice", mDatabase.getCharacterDao().getCharacterById(characterId)?.name)
        assertEquals("World", mDatabase.getLorebookDao().getLorebookById(lorebookId)?.name)
    }

    @Test
    fun createStoryWithConfiguration_persistsInitialReferences() = runBlocking {
        val characterId = mDatabase.getCharacterDao().insertOrReplace(testCharacter())
        val lorebookId = mDatabase.getLorebookDao().insertOrReplace(Lorebook(name = "World"))
        val entryId = mDatabase.getLorebookEntryDao().insertOrReplace(
            testLorebookEntry(lorebookId)
        )

        val storyId = mRepository.createStoryWithConfiguration(
            title = " Configured story ",
            lorebookEntryIds = listOf(entryId, entryId),
            characterSelections = listOf(
                StoryCharacterSelection(
                    characterId = characterId,
                    activationMode = StoryCharacter.ACTIVATION_AUTO,
                    activationKeys = listOf("  Ally  ", "Ally", "")
                )
            ),
            createTime = 10L
        )

        val story = requireNotNull(mRepository.getStory(storyId))
        assertEquals("Configured story", story.title)
        assertEquals(10L, story.createTime)
        assertEquals(10L, story.latestTime)
        assertEquals(listOf(entryId), mRepository.getLorebookEntryIds(story))
        val candidate = mRepository.getStoryCharacterCandidates(storyId).single()
        assertEquals(characterId, candidate.character.id)
        assertEquals(0, candidate.relation.sortOrder)
        assertEquals(StoryCharacter.ACTIVATION_AUTO, candidate.relation.activationMode)
        assertEquals(listOf("Ally"), candidate.activationKeys)
    }

    @Test
    fun createStoryWithConfiguration_invalidReferenceRollsBackStory() = runBlocking {
        val characterId = mDatabase.getCharacterDao().insertOrReplace(testCharacter())

        val result = runCatching {
            mRepository.createStoryWithConfiguration(
                title = "Invalid configuration",
                lorebookEntryIds = listOf(Long.MAX_VALUE),
                characterSelections = listOf(
                    StoryCharacterSelection(
                        characterId = characterId,
                        activationMode = StoryCharacter.ACTIVATION_ALWAYS,
                        activationKeys = emptyList()
                    )
                ),
                createTime = 10L
            )
        }

        assertTrue(result.isFailure)
        assertTrue(mRepository.getStoryOverviews().isEmpty())
    }

    @Test
    fun summaryChecksRevisionAndOverwritesCurrentValue() = runBlocking {
        val storyId = mRepository.createStory("Story", createTime = 10L)
        assertTrue(mRepository.updateContent(storyId, 0L, "Manuscript", latestTime = 11L))

        assertFalse(mRepository.saveGeneratedSummary(storyId, 0L, "Stale"))
        assertTrue(mRepository.saveGeneratedSummary(storyId, 1L, "First", latestTime = 12L))
        mRepository.updateStoryConfiguration(
            storyId = storyId,
            memory = "",
            summary = "Corrected",
            authorNote = "",
            lorebookEntryIds = emptyList(),
            characterSelections = emptyList(),
            latestTime = 13L
        )

        assertEquals("Corrected", mRepository.getStory(storyId)?.summary)
    }

    @Test
    fun generatedEdit_checksRevisionAndTargetAndUpdatesWorldStateAtomically() = runBlocking {
        val storyId = mRepository.createStory("Story", createTime = 10L)
        assertTrue(mRepository.updateContent(storyId, 0L, "Before target after", latestTime = 11L))
        val start = "Before ".length
        val end = start + "target".length
        val edit = StoryGeneratedEdit(
            storyId = storyId,
            baseRevision = 1L,
            start = start,
            end = end,
            originalTextHash = storyTextHash("target"),
            result = "replacement",
            nextWorldInfoStateJson = "{\"next\":true}"
        )

        val applied = requireNotNull(mRepository.applyGeneratedEdit(edit))
        assertEquals("Before replacement after", applied.content)
        assertEquals(2L, applied.revision)
        assertEquals(1, applied.worldInfoGenerationStep)
        val persisted = requireNotNull(mRepository.getStory(storyId))
        assertEquals("{\"next\":true}", persisted.worldInfoStateJson)

        assertNull(mRepository.applyGeneratedEdit(edit))
        assertNull(
            mRepository.applyGeneratedEdit(
                edit.copy(baseRevision = 2L, originalTextHash = storyTextHash("wrong"))
            )
        )
        assertEquals("Before replacement after", mRepository.getStory(storyId)?.content)

        val reverted = requireNotNull(
            mRepository.revertGeneratedEdit(
                storyId = storyId,
                expectedRevision = 2L,
                start = start,
                insertedText = "replacement",
                replacedText = "target",
                previousWorldInfoStateJson = "{}",
                previousWorldInfoGenerationStep = 0
            )
        )
        assertEquals("Before target after", reverted.content)
        assertEquals(3L, reverted.revision)
        assertEquals(0, reverted.worldInfoGenerationStep)

        val redone = requireNotNull(
            mRepository.applyGeneratedEdit(edit.copy(baseRevision = reverted.revision))
        )
        assertEquals("Before replacement after", redone.content)
        assertEquals(4L, redone.revision)
        assertEquals(1, redone.worldInfoGenerationStep)
        assertEquals("{\"next\":true}", mRepository.getStory(storyId)?.worldInfoStateJson)
    }

    @Test
    fun generatedEdits_supportMultipleUndoAndRedoWithCurrentRevision() = runBlocking {
        val storyId = mRepository.createStory("Story", createTime = 10L)
        val firstEdit = StoryGeneratedEdit(
            storyId = storyId,
            baseRevision = 0L,
            start = 0,
            end = 0,
            originalTextHash = storyTextHash(""),
            result = "First",
            nextWorldInfoStateJson = "{\"step\":1}"
        )
        val firstApplied = requireNotNull(mRepository.applyGeneratedEdit(firstEdit))
        val secondEdit = StoryGeneratedEdit(
            storyId = storyId,
            baseRevision = firstApplied.revision,
            start = firstApplied.content.length,
            end = firstApplied.content.length,
            originalTextHash = storyTextHash(""),
            result = " second",
            nextWorldInfoStateJson = "{\"step\":2}"
        )
        val secondApplied = requireNotNull(mRepository.applyGeneratedEdit(secondEdit))

        val secondReverted = requireNotNull(
            mRepository.revertGeneratedEdit(
                storyId = storyId,
                expectedRevision = secondApplied.revision,
                start = secondEdit.start,
                insertedText = secondEdit.result,
                replacedText = "",
                previousWorldInfoStateJson = firstEdit.nextWorldInfoStateJson,
                previousWorldInfoGenerationStep = firstApplied.worldInfoGenerationStep
            )
        )
        val firstReverted = requireNotNull(
            mRepository.revertGeneratedEdit(
                storyId = storyId,
                expectedRevision = secondReverted.revision,
                start = firstEdit.start,
                insertedText = firstEdit.result,
                replacedText = "",
                previousWorldInfoStateJson = "{}",
                previousWorldInfoGenerationStep = 0
            )
        )

        assertEquals("", firstReverted.content)
        assertEquals(0, firstReverted.worldInfoGenerationStep)
        assertEquals("{}", mRepository.getStory(storyId)?.worldInfoStateJson)

        val firstRedone = requireNotNull(
            mRepository.applyGeneratedEdit(firstEdit.copy(baseRevision = firstReverted.revision))
        )
        val secondRedone = requireNotNull(
            mRepository.applyGeneratedEdit(secondEdit.copy(baseRevision = firstRedone.revision))
        )

        assertEquals("First second", secondRedone.content)
        assertEquals(2, secondRedone.worldInfoGenerationStep)
        assertEquals("{\"step\":2}", mRepository.getStory(storyId)?.worldInfoStateJson)
    }

    @Test
    fun manualEditUndoAndRedoPreserveWorldInfoGenerationStep() = runBlocking {
        val storyId = mRepository.createStory("Story", createTime = 10L)
        val generated = requireNotNull(
            mRepository.applyGeneratedEdit(
                StoryGeneratedEdit(
                    storyId = storyId,
                    baseRevision = 0L,
                    start = 0,
                    end = 0,
                    originalTextHash = storyTextHash(""),
                    result = "AI",
                    nextWorldInfoStateJson = "{\"step\":1}"
                )
            )
        )
        assertTrue(
            mRepository.updateContent(
                storyId = storyId,
                expectedRevision = generated.revision,
                content = "AI!"
            )
        )

        val undone = requireNotNull(
            mRepository.revertGeneratedEdit(
                storyId = storyId,
                expectedRevision = generated.revision + 1L,
                start = 2,
                insertedText = "!",
                replacedText = "",
                previousWorldInfoStateJson = "{\"step\":1}",
                previousWorldInfoGenerationStep = generated.worldInfoGenerationStep
            )
        )
        assertEquals("AI", undone.content)
        assertEquals(1, undone.worldInfoGenerationStep)

        val redone = requireNotNull(
            mRepository.applyGeneratedEdit(
                StoryGeneratedEdit(
                    storyId = storyId,
                    baseRevision = undone.revision,
                    start = 2,
                    end = 2,
                    originalTextHash = storyTextHash(""),
                    result = "!",
                    nextWorldInfoStateJson = "{\"step\":1}",
                    nextWorldInfoGenerationStep = generated.worldInfoGenerationStep
                )
            )
        )
        assertEquals("AI!", redone.content)
        assertEquals(1, redone.worldInfoGenerationStep)
    }

    @Test
    fun confirmedArchiveImport_persistsStoryAndUniqueCharacterHint() = runBlocking {
        val character = testCharacter()
        val characterId = mDatabase.getCharacterDao().insertOrReplace(character)
        val gson = Gson()
        val archiveRepository = StoryArchiveRepository(
            InstrumentationRegistry.getInstrumentation().targetContext,
            mDatabase,
            gson,
            StoryArchiveCodec(gson)
        )

        val storyId = archiveRepository.saveImport(
            draft = StoryImportDraft(
                title = "Imported",
                content = "正文",
                memory = "Memory",
                authorNote = "Note",
                summary = "Summary",
                characterHints = listOf(
                    StoryCharacterHint(
                        name = character.name,
                        fingerprint = ChatCharacterMatcher.fingerprintOf(character),
                        activationMode = StoryArchiveCodec.MODE_ALWAYS,
                        activationKeys = listOf("Ally")
                    )
                ),
                type = StoryImportType.Archive
            ),
            title = "Confirmed title"
        )

        val story = requireNotNull(mRepository.getStory(storyId))
        assertEquals("Confirmed title", story.title)
        assertEquals("正文", story.content)
        assertEquals("Memory", story.memory)
        assertEquals("Summary", mRepository.getStory(storyId)?.summary)
        val relation = mRepository.getStoryCharacterCandidates(storyId).single()
        assertEquals(characterId, relation.character.id)
        assertEquals(StoryCharacter.ACTIVATION_ALWAYS, relation.relation.activationMode)
        assertEquals(listOf("Ally"), relation.activationKeys)
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

    private fun testLorebookEntry(lorebookId: Long): LorebookEntry {
        return LorebookEntry(
            lorebookId = lorebookId,
            name = "Station",
            keywords = "[]",
            secondaryKeywords = "[]",
            order = 0,
            depth = 0,
            category = "[]",
            content = "The station was abandoned years ago."
        )
    }
}
