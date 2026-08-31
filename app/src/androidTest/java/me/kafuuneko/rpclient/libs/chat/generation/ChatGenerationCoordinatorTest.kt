package me.kafuuneko.rpclient.libs.chat.generation

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatGenerationCoordinatorTest {
    @Test
    fun onlyOneGenerationRunsAcrossSessions() = runBlocking {
        val coordinator = ChatGenerationCoordinator()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = coordinator.launch(sessionId = 11L) {
            coordinator.publish(11L, ChatGenerationState.Requesting)
            entered.complete(Unit)
            release.await()
            coordinator.publish(11L, ChatGenerationState.Idle)
        }
        assertTrue(first is ChatGenerationStartResult.Started)
        entered.await()

        val second = coordinator.launch(sessionId = 22L) {}
        assertEquals(ChatGenerationStartResult.Busy(11L), second)
        assertEquals(11L, coordinator.activeSessionId())

        release.complete(Unit)
        (first as ChatGenerationStartResult.Started).job.join()
        assertNull(coordinator.activeSessionId())
        assertEquals(ChatGenerationState.Idle, coordinator.snapshot.value?.state)
    }

    @Test
    fun stopWaitsForNonCancellableCleanupAndRejectsOtherSession() = runBlocking {
        val coordinator = ChatGenerationCoordinator()
        val entered = CompletableDeferred<Unit>()
        val cleanupEntered = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        var cleanupFinished = false

        coordinator.launch(sessionId = 33L) {
            try {
                coordinator.publish(33L, ChatGenerationState.Requesting)
                entered.complete(Unit)
                delay(Long.MAX_VALUE)
            } finally {
                withContext(NonCancellable) {
                    cleanupEntered.complete(Unit)
                    releaseCleanup.await()
                    cleanupFinished = true
                }
            }
        }
        entered.await()

        assertFalse(coordinator.stop(44L))
        val stopResult = CompletableDeferred<Boolean>()
        launch { stopResult.complete(coordinator.stop(33L)) }
        cleanupEntered.await()

        assertFalse(stopResult.isCompleted)
        assertEquals(33L, coordinator.activeSessionId())
        assertEquals(
            ChatGenerationStartResult.Busy(33L),
            coordinator.launch(sessionId = 44L) {}
        )

        releaseCleanup.complete(Unit)
        assertTrue(stopResult.await())
        assertTrue(cleanupFinished)
        assertNull(coordinator.activeSessionId())
        assertEquals(ChatGenerationState.Idle, coordinator.snapshot.value?.state)
    }
}
