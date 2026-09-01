package me.kafuuneko.rpclient.libs.imagegeneration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarPromptBuilderTest {

    @Test
    fun containsCharacterNameAndDescription() {
        val prompt = AvatarPromptBuilder.buildAvatarPrompt(
            characterName = "Alice",
            characterDescription = "Silver hair and blue eyes",
            avatarStylePrompt = "watercolor portrait"
        )
        assertTrue(prompt.contains("Name: Alice"))
        assertTrue(prompt.contains("Appearance and description: Silver hair and blue eyes"))
    }

    @Test
    fun containsAvatarStyleWhenNonEmpty() {
        val prompt = AvatarPromptBuilder.buildAvatarPrompt(
            characterName = "Bob",
            characterDescription = "Short brown hair",
            avatarStylePrompt = "anime digital illustration"
        )
        assertTrue(prompt.contains("Style:"))
        assertTrue(prompt.contains("anime digital illustration"))
    }

    @Test
    fun omitsStyleSectionWhenEmpty() {
        val prompt = AvatarPromptBuilder.buildAvatarPrompt(
            characterName = "Bob",
            characterDescription = "Short brown hair",
            avatarStylePrompt = "   "
        )
        assertFalse(prompt.contains("Style:"))
    }

    @Test
    fun enforcesSingleCharacterAndOnePanel() {
        val prompt = AvatarPromptBuilder.buildAvatarPrompt(
            characterName = "Alice",
            characterDescription = "Silver hair",
            avatarStylePrompt = ""
        )
        assertTrue(prompt.contains("exactly one character"))
        assertTrue(prompt.contains("one panel"))
        assertTrue(prompt.contains("one frame"))
        assertTrue(prompt.contains("one image"))
    }

    @Test
    fun containsNoCollageAndNoSplitScreen() {
        val prompt = AvatarPromptBuilder.buildAvatarPrompt(
            characterName = "Alice",
            characterDescription = "Silver hair",
            avatarStylePrompt = ""
        )
        assertTrue(prompt.contains("no collage"))
        assertTrue(prompt.contains("no split screen"))
        assertTrue(prompt.contains("no comic page layout"))
        assertTrue(prompt.contains("no multi-panel composition"))
    }

    @Test
    fun allowsMangaStyleWhileEnforcingSinglePanelConstraints() {
        val prompt = AvatarPromptBuilder.buildAvatarPrompt(
            characterName = "Charlie",
            characterDescription = "Spiky hair",
            avatarStylePrompt = "manga comic illustration"
        )
        assertTrue(prompt.contains("manga comic illustration"))
        assertTrue(prompt.contains("one panel"))
        assertTrue(prompt.contains("no comic page layout"))
        assertTrue(prompt.contains("no multi-panel composition"))
    }

    @Test
    fun constantSizeIsSquare1024() {
        assertEquals("1024x1024", AVATAR_IMAGE_SIZE)
    }
}
