package me.kafuuneko.rpclient.feature.worldbookedit.presentation

import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEditForm
import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEntryListItem
import me.kafuuneko.rpclient.feature.worldbookedit.model.hasUnsavedChangesFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorldBookEditUiStateTest {

    private val entries = listOf(
        entry(id = 1L, name = "Castle", keywords = listOf("stone"), constant = true),
        entry(id = 2L, name = "Forest", keywords = listOf("ancient"), disabled = true),
        entry(id = 3L, name = "Village", keywords = listOf("stone", "market"))
    )

    @Test
    fun `entry list rebuild applies query and filter and preserves counts`() {
        val state = WorldBookEntryListState(
            query = "stone",
            filter = WorldBookEntryFilter.Enabled
        ).rebuild(entries)

        assertEquals(listOf(1L, 3L), state.visibleEntries.map { it.id })
        assertEquals(3, state.totalCount)
        assertEquals(2, state.activeCount)
    }

    @Test
    fun `persisted disabled change updates form baseline and derived list`() {
        val form = WorldBookEditForm(entries = entries)
        val state = WorldBookEditUiState.Normal(
            mode = WorldBookEditMode.Edit,
            form = form,
            entryListState = WorldBookEntryListState(
                filter = WorldBookEntryFilter.Disabled
            ).rebuild(entries)
        )

        val updated = state.withPersistedEntryDisabled(entryId = 3L, disabled = true)

        assertFalse(updated.form.hasUnsavedChangesFrom(updated.initialForm))
        assertEquals(listOf(2L, 3L), updated.entryListState.visibleEntries.map { it.id })
        assertEquals(1, updated.entryListState.activeCount)
    }

    private fun entry(
        id: Long,
        name: String,
        keywords: List<String>,
        constant: Boolean = false,
        disabled: Boolean = false
    ) = WorldBookEntryListItem(
        id = id,
        name = name,
        keywords = keywords,
        constant = constant,
        disabled = disabled,
        order = 0,
        depth = 2
    )
}
