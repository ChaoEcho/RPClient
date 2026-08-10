package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainImportCharacterItem
import me.kafuuneko.rpclient.libs.prompt.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.llm.model.LLMReasoningEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainResumeStateTest {
    @Test
    fun completedImportStateSurvivesStaleResumeRefresh() {
        val staleSettings = settings(isImportReading = true)
        val dialog = MainDialogState.ImportChatCharacterSelection(
            title = "Imported",
            sourceCharacterName = "Seraphina",
            messageCount = 2,
            query = "",
            characters = listOf(MainImportCharacterItem(1L, "Seraphina", "")),
            visibleCharacters = listOf(MainImportCharacterItem(1L, "Seraphina", "")),
            selectedCharacterId = 1L
        )
        val current = MainUiState.Normal(
            homeState = home(totalCharacters = 1),
            settingsState = settings(isImportReading = false),
            dialogState = dialog
        )

        val merged = current.mergeResumeRefresh(
            homeState = home(totalCharacters = 2),
            settingsState = staleSettings.copy(
                identityState = staleSettings.identityState.copy(userName = "Refreshed")
            )
        )

        assertEquals(dialog, merged.dialogState)
        assertEquals(
            MainChatDataManagementState.Idle,
            merged.settingsState.chatDataManagementState
        )
        assertEquals("Refreshed", merged.settingsState.identityState.userName)
        assertEquals(2, merged.homeState.resourceState.totalCharacters)
    }

    @Test
    fun dialogIsBlockedWhileImportIsReading() {
        val state = MainUiState.Normal(
            homeState = home(totalCharacters = 1),
            settingsState = settings(isImportReading = true)
        )

        assertFalse(state.canOpenDialog())
    }

    @Test
    fun dialogIsBlockedWhenAnotherDialogIsVisible() {
        val state = MainUiState.Normal(
            homeState = home(totalCharacters = 1),
            settingsState = settings(isImportReading = false),
            dialogState = MainDialogState.DeleteSelectedSessions(count = 1)
        )

        assertFalse(state.canOpenDialog())
    }

    @Test
    fun dialogCanOpenWhenImportIsIdleAndNoDialogIsVisible() {
        val state = MainUiState.Normal(
            homeState = home(totalCharacters = 1),
            settingsState = settings(isImportReading = false)
        )

        assertTrue(state.canOpenDialog())
    }

    private fun home(totalCharacters: Int) = MainHomeState(
        resourceState = MainHomeResourceState(
            totalCharacters = totalCharacters,
            totalWorldBooks = 0
        ),
        recentChatsState = MainRecentChatsState.Empty,
        recentGroupChatsState = MainRecentGroupChatsState.Empty
    )

    private fun settings(isImportReading: Boolean) = MainSettingsState(
        identityState = MainUserIdentityState(
            userName = "You",
            userDescription = "",
            avatarState = MainUserAvatarState.None
        ),
        providerState = MainProviderSettingsState.Empty,
        reasoningState = MainReasoningSettingsState(
            conversationEffort = LLMReasoningEffort.Auto,
            storyEffort = LLMReasoningEffort.Minimum
        ),
        promptBehaviorState = MainPromptBehaviorState(
            providerPostProcessingState = MainProviderPostProcessingState.Unavailable,
            exampleDialogueBehavior = ExampleDialogueBehavior.Normal,
            includeThinkInContext = false,
            contextTrimmingAlert = true,
            streamEnabled = true
        ),
        worldInfoBudgetState = MainWorldInfoBudgetState(
            budgetPercent = 10,
            budgetCap = 0,
            overflowAlert = true
        ),
        summaryState = MainSummarySettingsState(
            autoSummaryEnabled = false,
            triggerMessageCount = 20,
            wordsLimit = 200,
            maxMessagesPerRequest = 20,
            responseTokens = 400,
            injectionState = MainSummaryInjectionState.AfterMain
        ),
        chatDataManagementState = if (isImportReading) {
            MainChatDataManagementState.Reading
        } else {
            MainChatDataManagementState.Idle
        },
        debugState = MainDebugSettingsState(enabled = false)
    )
}
