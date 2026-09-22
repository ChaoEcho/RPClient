package me.kafuuneko.rpclient.libs.generation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataOperationBarrierTest {
    @Test
    fun exclusiveWaitsForPartialPersistenceAndRejectsNewWork() = runBlocking<Unit> {
        val barrier = DataOperationBarrier()
        val started = CompletableDeferred<Unit>()
        val cleaning = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        val task = barrier.launch(this) {
            try { started.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable) { cleaning.complete(Unit); finishCleanup.await() } }
        }
        started.await()
        var changed = false
        val restore = async { barrier.exclusive { changed = true } }
        cleaning.await()
        assertFalse(changed)
        val rejected = barrier.launch(this) { error("No task may enter during restore") }
        assertTrue(rejected.isCancelled)
        finishCleanup.complete(Unit)
        restore.await()
        task.join()
        assertTrue(changed)
        assertFalse(barrier.state.value.running)
    }

    @Test
    fun callerAndItsRegisteredAncestorsAreNotCancelled() = runBlocking<Unit> {
        val barrier = DataOperationBarrier()
        val task = barrier.launch(this) {
            withContext(kotlinx.coroutines.Dispatchers.Default) {
                barrier.exclusive { assertTrue(barrier.state.value.running) }
            }
        }
        withTimeout(5_000) { task.join() }
        assertFalse(task.isCancelled)
        assertEquals(1L, barrier.epoch)
    }

    @Test
    fun operationKeepsStructuredChildrenRegisteredUntilTheyFinish() = runBlocking<Unit> {
        val barrier = DataOperationBarrier()
        val entered = CompletableDeferred<Unit>()
        val childFinished = CompletableDeferred<Unit>()
        val operation = launch {
            barrier.operation {
                launch {
                    try { entered.complete(Unit); awaitCancellation() }
                    finally { childFinished.complete(Unit) }
                }
            }
        }
        entered.await()
        barrier.exclusive { assertTrue(childFinished.isCompleted) }
        operation.join()
    }

    @Test
    fun exceptionReopensAdmissionButUnrecoveredJournalDoesNot() = runBlocking<Unit> {
        val barrier = DataOperationBarrier()
        runCatching { barrier.exclusive { error("fixture failure") } }
        assertFalse(barrier.state.value.running)
        barrier.requireRecovery()
        assertTrue(barrier.launch(this) { error("Recovery must happen first") }.isCancelled)
        barrier.exclusive { barrier.recovered() }
        var ran = false
        barrier.launch(this) { ran = true }.join()
        assertTrue(ran)
    }
    @Test
    fun stalePageCannotStartLateWorkAfterTheBarrierReopens() = runBlocking<Unit> {
        val barrier = DataOperationBarrier()
        val pageEpoch = barrier.epoch
        barrier.exclusive { }
        val lateTask = barrier.launch(this, expectedEpoch = pageEpoch) { error("Stale page wrote new data") }
        assertTrue(lateTask.isCancelled)
        assertTrue(runCatching { barrier.operation(expectedEpoch = pageEpoch) { error("Stale intent") } }.isFailure)
    }

}
