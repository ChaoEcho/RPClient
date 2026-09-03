package me.kafuuneko.rpclient.libs.chat.generation

import me.kafuuneko.rpclient.libs.room.entity.Character
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatImageGenerationCoordinatorTest {
    private val testCharacter = Character(
        id = 1L,
        name = "Aster",
        avatar = "",
        characterTags = "[]",
        description = "silver-haired mage",
        creatorNotes = "",
        personality = "",
        scenario = "in a library",
        firstMessages = "",
        examplesOfDialogue = "",
        postHistoryInstructions = ""
    )

    @Test
    fun buildSceneRefinementInputStripsClosedThinkBlocks() {
        val input = buildSceneRefinementInput(
            character = testCharacter,
            recentUserMessage = "<think>user planning</think>The user reaches for a spellbook.",
            assistantReply = "<think>\nAster analyzes the spellbook.\n</think>\nAster catches the book and looks surprised."
        )

        assertTrue(input.contains("Character name:\nAster"))
        assertTrue(input.contains("Character description:\nsilver-haired mage"))
        assertTrue(input.contains("Scenario:\nin a library"))
        assertTrue(input.contains("Recent user message:\nThe user reaches for a spellbook."))
        assertTrue(input.contains("Latest character reply:\nAster catches the book and looks surprised."))
        assertFalse(input.contains("user planning"))
        assertFalse(input.contains("Aster analyzes the spellbook"))
        assertFalse(input.contains("<think>", ignoreCase = true))
        assertFalse(input.contains("</think>", ignoreCase = true))
    }

    @Test
    fun buildSceneRefinementInputStripsUnclosedThinkBlocks() {
        val input = buildSceneRefinementInput(
            character = testCharacter,
            recentUserMessage = "Look out!<think>unfinished user reasoning",
            assistantReply = "Visible reply.\n<think>unfinished character reasoning"
        )

        assertTrue(input.contains("Recent user message:\nLook out!"))
        assertTrue(input.contains("Latest character reply:\nVisible reply."))
        assertFalse(input.contains("unfinished user reasoning"))
        assertFalse(input.contains("unfinished character reasoning"))
        assertFalse(input.contains("<think>", ignoreCase = true))
    }

    @Test
    fun buildSceneRefinementInputStripsCaseInsensitiveThinkBlocks() {
        val input = buildSceneRefinementInput(
            character = testCharacter,
            recentUserMessage = "<THINK>secret</THINK>Hello",
            assistantReply = "<Think>secret</Think>World"
        )

        assertTrue(input.contains("Recent user message:\nHello"))
        assertTrue(input.contains("Latest character reply:\nWorld"))
        assertFalse(input.contains("secret"))
        assertFalse(input.contains("<think>", ignoreCase = true))
    }

    @Test
    fun buildSceneRefinementInputHandlesOnlyThinkAsNone() {
        val input = buildSceneRefinementInput(
            character = testCharacter,
            recentUserMessage = "<think>only user reasoning</think>",
            assistantReply = "<think>only character reasoning</think>"
        )

        assertTrue(input.contains("Recent user message:\n(none)"))
        assertTrue(input.contains("Latest character reply:\n(none)"))
        assertFalse(input.contains("only user reasoning"))
        assertFalse(input.contains("only character reasoning"))
        assertFalse(input.contains("<think>", ignoreCase = true))
    }
}
