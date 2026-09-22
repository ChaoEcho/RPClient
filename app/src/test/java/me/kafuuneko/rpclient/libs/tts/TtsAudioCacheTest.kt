package me.kafuuneko.rpclient.libs.tts

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TtsAudioCacheTest {
    @get:Rule val temp = TemporaryFolder()

    private fun request(text: String = "Hello") = AzureTtsRequest(text, "fixture-region", "fixture-key", "fixture-voice", 1f)

    @Test
    fun concurrentIdenticalRequestsSynthesizeOnlyOnce() = runBlocking {
        val directory = temp.newFolder()
        val cache = TtsAudioCache(directory, 100)
        val count = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        withTimeout(5_000) {
            val jobs = (1..8).map {
                async {
                    cache.withAudio(TtsProviderType.Azure, request(), { output ->
                        count.incrementAndGet()
                        started.complete(Unit)
                        release.await()
                        output.writeBytes(byteArrayOf(1, 2, 3))
                    }) { assertEquals(3L, it.length()) }
                }
            }
            started.await()
            release.complete(Unit)
            jobs.awaitAll()
        }
        assertEquals(1, count.get())
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test
    fun largeAudioIsProtectedUntilPlaybackEnds() = runBlocking {
        val directory = temp.newFolder()
        val cache = TtsAudioCache(directory, 2)
        cache.withAudio(TtsProviderType.Azure, request(), { it.writeBytes(ByteArray(8)) }) { first ->
            assertTrue(first.isFile)
            cache.withAudio(TtsProviderType.Azure, request("second"), { it.writeBytes(ByteArray(4)) }) {
                assertTrue(first.isFile)
                assertTrue(it.isFile)
            }
            assertTrue(first.isFile)
        }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun cancellationCleansTemporaryAudioAndAllowsRetry() = runBlocking {
        val directory = temp.newFolder()
        val cache = TtsAudioCache(directory, 100)
        val started = CompletableDeferred<Unit>()
        val job = async {
            cache.withAudio(TtsProviderType.Azure, request(), {
                it.writeBytes(byteArrayOf(1))
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }) { error("Cancelled audio must not play") }
        }
        withTimeout(5_000) { started.await() }
        job.cancelAndJoin()
        assertTrue(directory.listFiles()!!.isEmpty())
        cache.withAudio(TtsProviderType.Azure, request(), { it.writeBytes(byteArrayOf(2)) }) {
            assertEquals(listOf<Byte>(2), it.readBytes().toList())
        }
        assertFalse(directory.listFiles()!!.any { it.name.endsWith(".partial") })
    }
}
