package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainChatSessionGroup
import me.kafuuneko.rpclient.feature.main.model.MainSessionSelection
import me.kafuuneko.rpclient.feature.main.model.MainSessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MainHomeStateTest {
    @Test
    fun refreshKeepsOnlyCollapsedGroupsThatStillExist() {
        val previous = home(
            recentChatsState = MainRecentChatsState.Content(
                sessionGroups = listOf(group("existing"), group("removed")),
                collapsedCharacterIds = setOf("existing", "removed")
            ),
            selectionState = MainHomeSelectionState.Selecting(
                selectedSessions = setOf(
                    MainSessionSelection(MainSessionType.Chat, "1")
                )
            )
        )
        val refreshed = home(
            recentChatsState = MainRecentChatsState.Content(
                sessionGroups = listOf(group("existing"), group("new"))
            )
        )

        val merged = refreshed.preserveCollapsedGroupsFrom(previous)
        val recentChatsState = merged.recentChatsState as MainRecentChatsState.Content

        assertEquals(setOf("existing"), recentChatsState.collapsedCharacterIds)
        assertSame(MainHomeSelectionState.None, merged.selectionState)
    }

    @Test
    fun emptyRefreshDoesNotRetainCollapsedGroups() {
        val previous = home(
            recentChatsState = MainRecentChatsState.Content(
                sessionGroups = listOf(group("existing")),
                collapsedCharacterIds = setOf("existing")
            )
        )
        val refreshed = home(recentChatsState = MainRecentChatsState.Empty)

        val merged = refreshed.preserveCollapsedGroupsFrom(previous)

        assertSame(MainRecentChatsState.Empty, merged.recentChatsState)
    }

    @Test
    fun refreshPreservesSelectedSessionTab() {
        val previous = home(
            recentChatsState = MainRecentChatsState.Empty,
            selectedSessionTab = MainHomeSessionTab.Group
        )
        val refreshed = home(
            recentChatsState = MainRecentChatsState.Empty,
            selectedSessionTab = MainHomeSessionTab.All
        )

        val merged = refreshed.preserveCollapsedGroupsFrom(previous)

        assertEquals(MainHomeSessionTab.Group, merged.selectedSessionTab)
    }

    private fun home(
        recentChatsState: MainRecentChatsState,
        selectedSessionTab: MainHomeSessionTab = MainHomeSessionTab.All,
        selectionState: MainHomeSelectionState = MainHomeSelectionState.None
    ) = MainHomeState(
        resourceState = MainHomeResourceState(
            totalCharacters = 0,
            totalWorldBooks = 0
        ),
        recentChatsState = recentChatsState,
        recentGroupChatsState = MainRecentGroupChatsState.Empty,
        selectedSessionTab = selectedSessionTab,
        selectionState = selectionState
    )

    private fun group(characterId: String) = MainChatSessionGroup(
        characterId = characterId,
        characterName = characterId,
        sessions = emptyList()
    )
}
