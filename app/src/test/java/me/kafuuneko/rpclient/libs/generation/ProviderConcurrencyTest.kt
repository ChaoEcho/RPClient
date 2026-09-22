package me.kafuuneko.rpclient.libs.generation

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderConcurrencyTest {
    @Test
    fun replySummaryAndImagePromptShareOneProviderLimit() = runBlocking {
        val limiter = RequestConcurrencyLimiter()
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        withTimeout(5_000) {
            listOf("reply", "summary", "image-prompt").map {
                async {
                    limiter.withProviderPermit(7, 1) {
                        val current = active.incrementAndGet()
                        maximum.updateAndGet { maxOf(it, current) }
                        try { delay(10) } finally { active.decrementAndGet() }
                    }
                }
            }.awaitAll()
        }
        assertEquals(1, maximum.get())
        assertEquals(0, active.get())
    }

    @Test
    fun independentProvidersDoNotBlockEachOther() = runBlocking {
        val limiter = RequestConcurrencyLimiter()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val first = async {
            limiter.withProviderPermit(1, 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        try {
            withTimeout(5_000) {
                firstStarted.await()
                limiter.withProviderPermit(2, 1) { releaseFirst.complete(Unit) }
                first.await()
            }
        } finally {
            first.cancel()
        }
    }
}
