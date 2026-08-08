package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.repository.StoryCharacterCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryCharacterActivatorTest {
    private val mActivator = StoryCharacterActivator()

    @Test
    fun alwaysAndAllMatchedAutoCharactersActivateInStableOrder() {
        val candidates = listOf(
            candidate(2L, "Alice", 2, StoryCharacter.ACTIVATION_AUTO),
            candidate(1L, "Bob", 0, StoryCharacter.ACTIVATION_ALWAYS),
            candidate(3L, "林月", 1, StoryCharacter.ACTIVATION_AUTO, listOf("月姐"))
        )

        val result = mActivator.activate(candidates, "ALICE met 月姐 beside the gate.")

        assertEquals(listOf(1L, 3L, 2L), result.map { it.candidate.character.id })
        assertEquals(StoryCharacterActivationReason.Always, result[0].reason)
        assertEquals(StoryCharacterActivationReason.Alias, result[1].reason)
        assertEquals(StoryCharacterActivationReason.Name, result[2].reason)
    }

    @Test
    fun latinNameUsesWholeWordWhileCjkNameUsesSubstring() {
        val candidates = listOf(
            candidate(1L, "Ann", 0, StoryCharacter.ACTIVATION_AUTO),
            candidate(2L, "林月", 1, StoryCharacter.ACTIVATION_AUTO)
        )

        val result = mActivator.activate(candidates, "Annette 看见小林月亮起身。")

        assertEquals(listOf(2L), result.map { it.candidate.character.id })
    }

    private fun candidate(
        id: Long,
        name: String,
        order: Int,
        mode: Int,
        aliases: List<String> = emptyList()
    ): StoryCharacterCandidate {
        return StoryCharacterCandidate(
            relation = StoryCharacter(1L, id, order, mode),
            character = Character(
                id = id,
                name = name,
                avatar = "",
                characterTags = "[]",
                description = "Description",
                personality = "Personality",
                scenario = "Scenario",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = ""
            ),
            activationKeys = aliases
        )
    }
}
