package me.kafuuneko.rpclient.feature.storycreate.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StoryCreateFormTest {
    @Test
    fun setCharacterActivationMode_replacesExistingPrimaryWithAuto() {
        val form = StoryCreateForm(
            characterActivationModes = mapOf(
                1L to StoryCreateCharacterActivationMode.Primary,
                2L to StoryCreateCharacterActivationMode.Always
            )
        )

        val updated = form.setCharacterActivationMode(
            2L,
            StoryCreateCharacterActivationMode.Primary
        )

        assertEquals(StoryCreateCharacterActivationMode.Auto, updated.activationModeOf(1L))
        assertEquals(StoryCreateCharacterActivationMode.Primary, updated.activationModeOf(2L))
    }

    @Test
    fun setCharacterActivationMode_keepsAlwaysAndAutoSelections() {
        val form = StoryCreateForm(
            characterActivationModes = mapOf(
                1L to StoryCreateCharacterActivationMode.Auto,
                2L to StoryCreateCharacterActivationMode.Auto
            )
        )

        val updated = form
            .setCharacterActivationMode(1L, StoryCreateCharacterActivationMode.Always)
            .setCharacterActivationMode(2L, StoryCreateCharacterActivationMode.Auto)

        assertEquals(StoryCreateCharacterActivationMode.Always, updated.activationModeOf(1L))
        assertEquals(StoryCreateCharacterActivationMode.Auto, updated.activationModeOf(2L))
        assertEquals(setOf(1L, 2L), updated.selectedCharacterIds)
    }

    @Test
    fun setCharacterActivationMode_ignoresUnselectedCharacter() {
        val form = StoryCreateForm()

        val updated = form.setCharacterActivationMode(
            1L,
            StoryCreateCharacterActivationMode.Primary
        )

        assertEquals(form, updated)
    }
}
