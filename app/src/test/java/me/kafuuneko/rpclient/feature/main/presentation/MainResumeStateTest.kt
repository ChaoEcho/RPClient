package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainImportCharacterItem
import me.kafuuneko.rpclient.libs.prompt.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            settingsState = staleSettings.copy(userName = "Refreshed")
        )

        assertEquals(dialog, merged.dialogState)
        assertFalse(merged.settingsState.isChatImportReading)
        assertEquals("Refreshed", merged.settingsState.userName)
        assertEquals(2, merged.homeState.totalCharacters)
    }

    private fun home(totalCharacters: Int) = MainHomeState(
        sessionGroups = emptyList(),
        totalCharacters = totalCharacters,
        totalWorldBooks = 0
    )

    private fun settings(isImportReading: Boolean) = MainSettingsState(
        userName = "You",
        hasUserAvatar = false,
        userAvatarImage = null,
        userDescription = "",
        selectedProviderId = "",
        providers = emptyList(),
        temperature = 0.8f,
        topP = 1f,
        maxTokens = 1_200,
        contextTokens = 8_192,
        streamEnabled = true,
        promptPostProcessingMode = PromptPostProcessingMode.None,
        exampleDialogueBehavior = ExampleDialogueBehavior.Normal,
        includeThinkInContext = false,
        worldInfoBudgetPercent = 10,
        worldInfoBudgetCap = 0,
        worldInfoOverflowAlert = true,
        contextTrimmingAlert = true,
        debugModeEnabled = false,
        autoSummaryEnabled = false,
        summaryTriggerMessageCount = 20,
        summaryWordsLimit = 200,
        summaryMaxMessagesPerRequest = 20,
        summaryResponseTokens = 400,
        summaryInjectionPosition = SummaryInjectionPosition.AfterMain,
        summaryInjectionDepth = 0,
        summaryInjectionRole = SummaryInjectionRole.System,
        isChatImportReading = isImportReading
    )
}
