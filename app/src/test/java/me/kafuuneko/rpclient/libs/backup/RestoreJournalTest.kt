package me.kafuuneko.rpclient.libs.backup

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestoreJournalTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun interruptedPrepareNeverPublishesPendingState() = runBlocking<Unit> {
        val directory = temp.newFolder()
        val journal = RestoreJournal(directory)
        runCatching {
            journal.prepare(true) { it.writeText("partial"); error("fixture interruption") }
        }
        assertFalse(RestoreJournal(directory).isPending)
        assertFalse(File(directory, "rollback.tmp").exists())
    }

    @Test
    fun pendingAndCommittedSurviveReconstructionWithDifferentRecoveryPolicies() = runBlocking<Unit> {
        val directory = temp.newFolder()
        RestoreJournal(directory).prepare(false) { it.writeText("fixture snapshot") }
        val interrupted = RestoreJournal(directory)
        assertTrue(interrupted.isPending)
        assertFalse(interrupted.previousProvidersInitialized())
        assertEquals("fixture snapshot", interrupted.snapshot.readText())
        interrupted.markCommitted()
        val completed = RestoreJournal(directory)
        assertFalse(completed.isPending)
        assertTrue(completed.isCommitted)
        completed.clear()
        assertTrue(directory.listFiles()!!.isEmpty())
    }
}
