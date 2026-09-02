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
    fun sameSessionIsBusyWhileDifferentSessionsRunTogether() = runBlocking {
        val coordinator = ChatGenerationCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = coordinator.launch(11L) {
            coordinator.publish(11L, ChatGenerationState.Requesting)
            firstEntered.complete(Unit)
            release.await()
        }
        firstEntered.await()

        assertEquals(ChatGenerationStartResult.Busy(11L), coordinator.launch(11L) {})
        val second = coordinator.launch(22L) {
            coordinator.publish(22L, ChatGenerationState.Requesting)
            secondEntered.complete(Unit)
            release.await()
        }
        secondEntered.await()

        assertTrue(first is ChatGenerationStartResult.Started)
        assertTrue(second is ChatGenerationStartResult.Started)
        assertEquals(setOf(11L, 22L), coordinator.activeSessionIds())
        assertEquals(
            setOf(11L, 22L),
            coordinator.snapshotBySession.value.keys
        )

        release.complete(Unit)
        (first as ChatGenerationStartResult.Started).job.join()
        (second as ChatGenerationStartResult.Started).job.join()
        assertTrue(coordinator.activeSessionIds().isEmpty())
        assertTrue(coordinator.snapshotBySession.value.isEmpty())
    }

    @Test
    fun stopOnlyCancelsRequestedSessionAndWaitsForItsCleanup() = runBlocking {
        val coordinator = ChatGenerationCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val cleanupEntered = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val secondRelease = CompletableDeferred<Unit>()
        var cleanupFinished = false

        coordinator.launch(33L) {
            try {
                coordinator.publish(33L, ChatGenerationState.Requesting)
                firstEntered.complete(Unit)
                delay(Long.MAX_VALUE)
            } finally {
                withContext(NonCancellable) {
                    cleanupEntered.complete(Unit)
                    releaseCleanup.await()
                    cleanupFinished = true
                }
            }
        }
        coordinator.launch(44L) {
            coordinator.publish(44L, ChatGenerationState.Requesting)
            secondRelease.await()
        }
        firstEntered.await()

        val stopResult = CompletableDeferred<Boolean>()
        launch { stopResult.complete(coordinator.stop(33L)) }
        cleanupEntered.await()
        assertFalse(stopResult.isCompleted)
        assertTrue(coordinator.isActive(44L))

        releaseCleanup.complete(Unit)
        assertTrue(stopResult.await())
        assertTrue(cleanupFinished)
        assertFalse(coordinator.isActive(33L))
        assertTrue(coordinator.isActive(44L))
        assertNull(coordinator.stateFor(33L))
        assertEquals(ChatGenerationState.Requesting, coordinator.stateFor(44L))

        secondRelease.complete(Unit)
    }

    @Test
    fun summaryRunsAlongsideGenerationAndRefusesASecondOneForTheSameKey() = runBlocking {
        val coordinator = ChatGenerationCoordinator()
        val generationEntered = CompletableDeferred<Unit>()
        val summaryEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        coordinator.launch(55L) {
            generationEntered.complete(Unit)
            release.await()
        }
        generationEntered.await()

        // 正文生成不应挡住摘要：两者是独立的任务表。
        assertTrue(
            coordinator.launchSummary(chatSummaryKey(55L)) {
                summaryEntered.complete(Unit)
                release.await()
            }
        )
        summaryEntered.await()
        assertTrue(coordinator.isSummaryActive(chatSummaryKey(55L)))

        // 同一个键的第二个摘要请求被拒绝，而不是取消正在跑的那个。
        assertFalse(coordinator.launchSummary(chatSummaryKey(55L)) { error("must not run") })
        // 群聊使用独立的键空间，即使会话 ID 数值相同也互不影响。
        assertTrue(coordinator.launchSummary(groupChatSummaryKey(55L)) { release.await() })

        release.complete(Unit)
    }

    @Test
    fun stopSummaryCancelsTheTaskWithoutSuspendingTheCaller() = runBlocking {
        val coordinator = ChatGenerationCoordinator()
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()

        coordinator.launchSummary(chatSummaryKey(66L)) {
            try {
                entered.complete(Unit)
                delay(Long.MAX_VALUE)
            } finally {
                withContext(NonCancellable) { cancelled.complete(Unit) }
            }
        }
        entered.await()

        // stopSummary 由唯一的意图收集器调用，必须立即返回，不能等待 join。
        assertTrue(coordinator.stopSummary(chatSummaryKey(66L)))
        cancelled.await()
        assertFalse(coordinator.isSummaryActive(chatSummaryKey(66L)))
        // 取消完成后同一个键可以重新启动。
        assertTrue(coordinator.launchSummary(chatSummaryKey(66L)) {})
    }

    @Test
    fun stopSummaryReportsFalseWhenNothingIsRunning() = runBlocking {
        val coordinator = ChatGenerationCoordinator()
        assertFalse(coordinator.stopSummary(chatSummaryKey(77L)))
        assertFalse(coordinator.isSummaryActive(chatSummaryKey(77L)))
    }
}
