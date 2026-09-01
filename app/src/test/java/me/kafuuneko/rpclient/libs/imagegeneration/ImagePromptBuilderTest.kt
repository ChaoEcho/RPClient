package me.kafuuneko.rpclient.libs.imagegeneration

import org.junit.Assert.assertFalse
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
}
