package me.kafuuneko.rpclient.libs.imagegeneration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePromptBuilderTest {
    @Test
    fun includesSceneInputsAndAllowsInteractionWithoutLosingCharacterFocus() {
        val prompt = buildImagePrompt(
            characterName = "Aster",
            characterDescription = "silver-haired mage",
            scenario = "in a library",
            recentUserMessage = "The user reaches for the same book.",
            assistantReply = "Aster catches the book and looks surprised.",
            stylePrompt = "cinematic illustration"
        )

        assertTrue(prompt.contains("Name: Aster"))
        assertTrue(prompt.contains("Character description: silver-haired mage"))
        assertTrue(prompt.contains("Base scenario: in a library"))
        assertTrue(prompt.contains("Recent user context: The user reaches for the same book."))
        assertTrue(prompt.contains("Aster catches the book and looks surprised."))
        assertTrue(prompt.contains("cinematic illustration"))
        assertTrue(prompt.contains("the user may appear when useful"))
        assertTrue(prompt.contains("hands, arms, shoulder"))
        assertTrue(prompt.contains("Keep the roleplay character visually dominant"))
        assertFalse(prompt.contains("never show the user", ignoreCase = true))
        assertTrue(prompt.contains("Do not render dialogue, subtitles, speech bubbles, UI elements, or watermarks"))
    }
}
