package me.kafuuneko.rpclient.libs.imagegeneration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePromptBuilderTest {
    @Test
    fun includesIdentitySceneCompositionStyleAndNoTextConstraints() {
        val prompt = buildImagePrompt(
            characterName = "Aster",
            characterDescription = "silver-haired mage",
            scenario = "in a library",
            scenePrompt = "Aster catches the book and looks surprised while the user reaches toward it.",
            stylePrompt = "cinematic illustration"
        )

        assertTrue(prompt.contains("Name: Aster"))
        assertTrue(prompt.contains("Character description: silver-haired mage"))
        assertTrue(prompt.contains("Base scenario: in a library"))
        assertTrue(prompt.contains("Visible scene description: Aster catches the book"))
        assertTrue(prompt.contains("cinematic illustration"))
        assertTrue(prompt.contains("Composition:"))
        assertTrue(prompt.contains("the user may be represented", ignoreCase = true))
        assertTrue(prompt.contains("hands, arms, shoulder"))
        assertTrue(prompt.contains("Keep the roleplay character visually dominant"))
        assertFalse(prompt.contains("never show the user", ignoreCase = true))
        assertTrue(prompt.contains("Do not render dialogue, subtitles, speech bubbles, UI elements, or watermarks"))
    }

    @Test
    fun fallbackUsesRecentUserMessageAndAssistantReplyDeterministically() {
        val prompt = buildFallbackScenePrompt(
            recentUserMessage = "The user reaches for the same book.",
            assistantReply = "Aster catches the book and looks surprised."
        )

        assertTrue(prompt.contains("The user reaches for the same book."))
        assertTrue(prompt.contains("Aster catches the book and looks surprised."))
    }

    @Test
    fun fallbackStripsClosedThinkBlocksFromInputs() {
        val prompt = buildFallbackScenePrompt(
            recentUserMessage = "<think>user internal thought</think>The user reaches for the same book.",
            assistantReply = "<think>\nAster decides to catch the book\n</think>\nAster catches the book and looks surprised."
        )

        assertTrue(prompt.contains("The user reaches for the same book."))
        assertTrue(prompt.contains("Aster catches the book and looks surprised."))
        assertFalse(prompt.contains("user internal thought"))
        assertFalse(prompt.contains("Aster decides to catch the book"))
        assertFalse(prompt.contains("<think>", ignoreCase = true))
        assertFalse(prompt.contains("</think>", ignoreCase = true))
    }

    @Test
    fun fallbackStripsUnclosedThinkBlocksFromInputs() {
        val prompt = buildFallbackScenePrompt(
            recentUserMessage = "The user reaches for the book.<think>unclosed thought",
            assistantReply = "Aster catches the book.<think>unclosed reasoning"
        )

        assertTrue(prompt.contains("The user reaches for the book."))
        assertTrue(prompt.contains("Aster catches the book."))
        assertFalse(prompt.contains("unclosed thought"))
        assertFalse(prompt.contains("unclosed reasoning"))
        assertFalse(prompt.contains("<think>", ignoreCase = true))
    }

    @Test
    fun fallbackStripsCaseInsensitiveThinkBlocks() {
        val prompt = buildFallbackScenePrompt(
            recentUserMessage = "<THINK>planning</THINK>User waits.",
            assistantReply = "<Think>pondering</Think>Aster nods."
        )

        assertTrue(prompt.contains("User waits."))
        assertTrue(prompt.contains("Aster nods."))
        assertFalse(prompt.contains("planning"))
        assertFalse(prompt.contains("pondering"))
        assertFalse(prompt.contains("<think>", ignoreCase = true))
    }

    @Test
    fun fallbackWithOnlyThinkBlocksYieldsDefaultOrUserScene() {
        val promptOnlyThinkReply = buildFallbackScenePrompt(
            recentUserMessage = "User waits.",
            assistantReply = "<think>only reasoning here</think>"
        )
        assertEquals("The user visibly says or does: User waits.", promptOnlyThinkReply)
        assertFalse(promptOnlyThinkReply.contains("only reasoning here"))

        val promptBothOnlyThink = buildFallbackScenePrompt(
            recentUserMessage = "<think>user thought</think>",
            assistantReply = "<think>only reasoning here</think>"
        )
        assertEquals("The character remains in the current scene.", promptBothOnlyThink)
    }
}
