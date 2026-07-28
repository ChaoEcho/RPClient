package me.kafuuneko.rpclient.feature.chat.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDialogStateTest {
    @Test
    fun finishedExportClearsStaleDialogState() {
        assertEquals(
            ChatDialogState.None,
            ChatDialogState.Exporting.resolveExportDialogState(isExportActive = false)
        )
    }

    @Test
    fun activeExportKeepsDialogState() {
        assertEquals(
            ChatDialogState.Exporting,
            ChatDialogState.Exporting.resolveExportDialogState(isExportActive = true)
        )
    }

    @Test
    fun unrelatedDialogStateIsUnchanged() {
        assertEquals(
            ChatDialogState.Summarizing,
            ChatDialogState.Summarizing.resolveExportDialogState(isExportActive = false)
        )
    }
}
