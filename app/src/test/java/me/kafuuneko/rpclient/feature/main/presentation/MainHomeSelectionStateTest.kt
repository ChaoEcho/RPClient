package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainSessionSelection
import me.kafuuneko.rpclient.feature.main.model.MainSessionType
import org.junit.Assert.assertEquals
import org.junit.Test

class MainHomeSelectionStateTest {
    @Test
    fun togglesSameIdAcrossSessionTypesIndependently() {
        val chat = MainSessionSelection(MainSessionType.Chat, "1")
        val groupChat = MainSessionSelection(MainSessionType.GroupChat, "1")
        val initial = MainHomeSelectionState.Selecting(
            selectedSessions = setOf(chat)
        )

        val bothSelected = initial.toggleSession(groupChat)
        val groupOnly = bothSelected.toggleSession(chat)

        assertEquals(setOf(chat, groupChat), bothSelected.selectedSessions)
        assertEquals(setOf(groupChat), groupOnly.selectedSessions)
    }
}
