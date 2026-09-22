package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualIdentityCacheTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: CharacterRepository
    private lateinit var original: Character

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val gson = Gson()
        repository = CharacterRepository(database, gson, RegexScriptCodec(gson), MessageImageRepository(database, FileRepository(context, database)))
        val id = repository.saveCharacter(Character(
            name = "Fixture", avatar = "", characterTags = "[]", description = "Old appearance",
            personality = "", scenario = "", firstMessages = "", examplesOfDialogue = "",
            postHistoryInstructions = ""
        ))
        original = requireNotNull(repository.getCharacterById(id))
    }

    @After fun tearDown() { database.close() }

    @Test
    fun editedCharacterRejectsDelayedRefinement() = runBlocking {
        repository.saveCharacter(original.copy(description = "New appearance"))
        assertFalse(repository.updateVisualIdentityIfUnchanged(original, "Stale result"))
        val saved = requireNotNull(repository.getCharacterById(original.id))
        assertEquals("New appearance", saved.description)
        assertEquals("", saved.visualIdentity)
    }

    @Test
    fun competingAndDeletedSnapshotsCannotOverwriteCache() = runBlocking {
        assertTrue(repository.updateVisualIdentityIfUnchanged(original, "Accepted result"))
        assertFalse(repository.updateVisualIdentityIfUnchanged(original, "Late result"))
        assertEquals("Accepted result", repository.getCharacterById(original.id)?.visualIdentity)
        repository.deleteCharacter(original.id)
        assertFalse(repository.updateVisualIdentityIfUnchanged(original, "Deleted result"))
    }
}
