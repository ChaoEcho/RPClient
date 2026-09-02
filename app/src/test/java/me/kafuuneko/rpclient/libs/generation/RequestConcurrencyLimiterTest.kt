package me.kafuuneko.rpclient.libs.generation

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestConcurrencyLimiterTest {
    @Test
    fun sameKeyLimitOneHasAtMostOneActiveBlock() = runBlocking {
        val limiter = RequestConcurrencyLimiter()
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)

        coroutineScope {
            repeat(8) {
                launch(Dispatchers.Default) {
                    limiter.withPermit("provider", 1) {
                        val count = active.incrementAndGet()
                        maximum.updateAndGet { maxOf(it, count) }
                        delay(10)
                        active.decrementAndGet()
                    }
                }
            }
        }

        assertEquals(1, maximum.get())
    }

    @Test
    fun sameKeyLimitThreeNeverExceedsThreeActiveBlocks() = runBlocking {
        val limiter = RequestConcurrencyLimiter()
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)

        coroutineScope {
            repeat(12) {
                launch(Dispatchers.Default) {
                    limiter.withPermit("provider", 3) {
                        val count = active.incrementAndGet()
                        maximum.updateAndGet { maxOf(it, count) }
                        delay(10)
                        active.decrementAndGet()
                    }
                }
            }
        }

        assertTrue(maximum.get() <= 3)
    }

    @Test
    fun differentKeysDoNotBlockEachOther() = runBlocking {
        val limiter = RequestConcurrencyLimiter()
        val firstRelease = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()

        coroutineScope {
            val first = launch {
                limiter.withPermit("first", 1) {
                    firstRelease.await()
                }
            }
            val second = launch {
                limiter.withPermit("second", 1) {
                    secondStarted.complete(Unit)
                }
            }

            withTimeout(1_000) { secondStarted.await() }
            firstRelease.complete(Unit)
            joinAll(first, second)
        }
    }

    @Test
    fun fullGateQueuesLaterBlocks() = runBlocking {
        val limiter = RequestConcurrencyLimiter()
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()

        coroutineScope {
            val first = launch {
                limiter.withPermit("provider", 1) {
                    release.await()
                }
            }
            val second = launch {
                limiter.withPermit("provider", 1) {
                    started.complete(Unit)
                }
            }

            assertFalse(withTimeoutOrNull(100) { started.await() } != null)
            release.complete(Unit)
            withTimeout(1_000) { started.await() }
            joinAll(first, second)
        }
    }

    @Test
    fun cancellingWaiterRemovesItWithoutLeakingPermit() = runBlocking {
        val limiter = RequestConcurrencyLimiter()
        val release = CompletableDeferred<Unit>()
        val nextStarted = CompletableDeferred<Unit>()

        coroutineScope {
            val first = launch {
                limiter.withPermit("provider", 1) {
                    release.await()
                }
            }
            val cancelled = launch {
                limiter.withPermit("provider", 1) {
                    error("cancelled waiter was granted")
                }
            }

            delay(20)
            cancelled.cancelAndJoin()
            release.complete(Unit)
            first.join()

            val next = launch {
                limiter.withPermit("provider", 1) {
                    nextStarted.complete(Unit)
                }
            }
            withTimeout(1_000) { nextStarted.await() }
            next.join()
        }
    }

    @Test
    fun exceptionReleasesPermit() = runBlocking {
        val limiter = RequestConcurrencyLimiter()
        var failed = false

        try {
            limiter.withPermit("provider", 1) {
                error("expected failure")
            }
        } catch (error: IllegalStateException) {
            failed = error.message == "expected failure"
        }
        assertTrue(failed)

        var ranAfterFailure = false
        limiter.withPermit("provider", 1) {
            ranAfterFailure = true
        }
        assertTrue(ranAfterFailure)
    }

    @Test
    fun increasingLimitWakesExistingWaiters() = runBlocking {
        val limiter = RequestConcurrencyLimiter()
        val firstRelease = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val thirdStarted = CompletableDeferred<Unit>()
        val augmenterStarted = CompletableDeferred<Unit>()
        val augmenterRelease = CompletableDeferred<Unit>()

        coroutineScope {
            val first = launch {
                limiter.withPermit("provider", 1) {
                    firstRelease.await()
                }
            }
            val second = launch {
                limiter.withPermit("provider", 1) {
                    secondStarted.complete(Unit)
                    firstRelease.await()
                }
            }
            val third = launch {
                limiter.withPermit("provider", 1) {
                    thirdStarted.complete(Unit)
                    firstRelease.await()
                }
            }

            delay(20)
            val augmenter = launch {
                limiter.withPermit("provider", 3) {
                    augmenterStarted.complete(Unit)
                    augmenterRelease.await()
                }
            }

            withTimeout(1_000) {
                secondStarted.await()
                thirdStarted.await()
            }
            assertFalse(augmenterStarted.isCompleted)
            firstRelease.complete(Unit)
            withTimeout(1_000) { augmenterStarted.await() }
            augmenterRelease.complete(Unit)
            joinAll(first, second, third, augmenter)
        }
    }

    @Test
    fun decreasingLimitDoesNotCancelActiveBlocksOrAdmitNewOnesEarly() = runBlocking {
        val limiter = RequestConcurrencyLimiter()
        val releases = List(3) { CompletableDeferred<Unit>() }
        val activeFinished = AtomicInteger(0)
        val waitingStarted = CompletableDeferred<Unit>()

        coroutineScope {
            val activeJobs = releases.mapIndexed { index, release ->
                launch {
                    limiter.withPermit("provider", 3) {
                        release.await()
                    }
                    activeFinished.incrementAndGet()
                }
            }
            delay(20)
            val waiting = launch {
                limiter.withPermit("provider", 1) {
                    waitingStarted.complete(Unit)
                }
            }

            delay(20)
            releases[0].complete(Unit)
            activeJobs[0].join()
            assertEquals(1, activeFinished.get())
            assertFalse(withTimeoutOrNull(100) { waitingStarted.await() } != null)

            releases[1].complete(Unit)
            activeJobs[1].join()
            assertEquals(2, activeFinished.get())
            assertFalse(withTimeoutOrNull(100) { waitingStarted.await() } != null)

            releases[2].complete(Unit)
            activeJobs[2].join()
            withTimeout(1_000) { waitingStarted.await() }
            waiting.join()
        }
    }
}
