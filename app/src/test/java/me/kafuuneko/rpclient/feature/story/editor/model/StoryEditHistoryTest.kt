package me.kafuuneko.rpclient.feature.story.editor.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryEditHistoryTest {
    @Test
    fun clearRemovesUndoAndRedoEntries() {
        val history = StoryEditHistory()
        history.recordManualEdit(
            previousContent = "正文",
            currentContent = "正文修改",
            worldInfoStateJson = "{}",
            worldInfoGenerationStep = 0,
            eventTimeMillis = 1L
        )
        val entry = requireNotNull(history.nextUndo())
        history.confirmUndo(entry)
        assertTrue(history.canRedo)

        history.clear()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)

        history.record(entry)
        assertTrue(history.canUndo)
        history.clear()

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
}
