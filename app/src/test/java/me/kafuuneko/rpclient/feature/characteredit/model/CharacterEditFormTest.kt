package me.kafuuneko.rpclient.feature.characteredit.model

import me.kafuuneko.rpclient.libs.room.entity.Character
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterEditFormTest {
    @Test
    fun providerAssociationIsKeptInFormInsteadOfCharacterEntity() {
        val character = character()

        val form = CharacterEditForm.from(character, llmProviderId = 42L)
        val restored = form.toCharacter()

        assertEquals(42L, form.llmProviderId)
        assertEquals(character, restored)
    }

    private fun character() = Character(
        id = 1L,
        name = "Character",
        avatar = "",
        characterTags = "[]",
        description = "",
        personality = "",
        scenario = "",
        firstMessages = "Hello",
        examplesOfDialogue = "",
        postHistoryInstructions = ""
    )
}
