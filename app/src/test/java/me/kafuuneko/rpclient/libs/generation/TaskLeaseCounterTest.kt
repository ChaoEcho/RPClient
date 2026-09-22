package me.kafuuneko.rpclient.libs.generation

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TaskLeaseCounterTest {
    @Test
    fun rejectedStartDoesNotLeaveAPhantomTask() {
        var rejected = true
        val published = mutableListOf<Int>()
        val counter = TaskLeaseCounter {
            if (rejected) throw IllegalStateException("start rejected")
            published += it
        }
        assertThrows(IllegalStateException::class.java) { counter.acquire() }
        assertEquals(0, counter.activeCount)
        rejected = false
        counter.acquire().close()
        assertEquals(listOf(1, 0), published)
    }

    @Test
    fun rejectedStopStillReleasesOwnershipExactlyOnce() {
        val counter = TaskLeaseCounter { if (it == 0) error("stop rejected") }
        val lease = counter.acquire()
        assertThrows(IllegalStateException::class.java) { lease.close() }
        lease.close()
        assertEquals(0, counter.activeCount)
        val next = counter.acquire()
        assertEquals(1, counter.activeCount)
        assertThrows(IllegalStateException::class.java) { next.close() }
        assertEquals(0, counter.activeCount)
    }

    @Test
    fun concurrentTransitionsNeverPublishStaleCountsAfterIdle() {
        val published = mutableListOf<Int>()
        val counter = TaskLeaseCounter { published += it }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..100).map {
                pool.submit { counter.acquire().use { Thread.yield() } }
            }
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(0, counter.activeCount)
            assertEquals(0, published.last())
            assertEquals(200, published.size)
        } finally {
            pool.shutdownNow()
        }
    }
}
