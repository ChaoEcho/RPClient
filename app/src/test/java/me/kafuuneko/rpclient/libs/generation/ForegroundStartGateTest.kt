package me.kafuuneko.rpclient.libs.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 用可控的前台晋升顺序复现快速结束任务的系统启动窗口。 */
class ForegroundStartGateTest {
    @Test
    fun quickReleaseWaitsUntilTheForegroundStartIsAcknowledged() {
        val events = mutableListOf<String>()
        val gate = ForegroundStartGate(
            start = { count, serial -> events += "start:$count:$serial" },
            stop = { events += "stop" }
        )
        gate.update(1)
        gate.update(0)
        assertEquals(listOf("start:1:1"), events)
        gate.onServiceForeground(1)
        assertEquals(listOf("start:1:1", "stop"), events)
    }

    @Test
    fun olderStartCannotStopASuccessorThatHasNotYetReachedTheForeground() {
        val events = mutableListOf<String>()
        val gate = ForegroundStartGate(
            start = { count, serial -> events += "start:$count:$serial" },
            stop = { events += "stop" }
        )
        gate.update(1)
        gate.update(0)
        gate.update(1)
        gate.update(0)
        gate.onServiceForeground(1)
        assertEquals(listOf("start:1:1", "start:1:2"), events)
        gate.onServiceForeground(2)
        assertEquals(listOf("start:1:1", "start:1:2", "stop"), events)
        gate.onServiceForeground(2)
        assertEquals(1, events.count { it == "stop" })
    }

    @Test
    fun overlappingLeasesKeepTheForegroundServiceUntilTheLastLeaseCloses() {
        val events = mutableListOf<String>()
        val gate = ForegroundStartGate(
            start = { count, serial -> events += "start:$count:$serial" },
            stop = { events += "stop" }
        )
        gate.update(1)
        gate.update(2)
        gate.onServiceForeground(1)
        gate.update(1)
        gate.onServiceForeground(2)
        assertEquals(listOf("start:1:1", "start:2:2", "start:1:3"), events)
        gate.update(0)
        gate.onServiceForeground(3)
        assertEquals("stop", events.last())
        assertEquals(1, events.count { it == "stop" })
    }

    @Test
    fun rejectedStartDoesNotLeaveAnUnacknowledgedForegroundRequest() {
        val events = mutableListOf<String>()
        var reject = true
        val gate = ForegroundStartGate(
            start = { count, serial ->
                if (reject) throw IllegalStateException("start rejected")
                events += "start:$count:$serial"
            },
            stop = { events += "stop" }
        )
        assertThrows(IllegalStateException::class.java) { gate.update(1) }
        reject = false
        gate.update(1)
        gate.onServiceForeground(1)
        gate.update(0)
        assertEquals(listOf("start:1:1", "stop"), events)
    }
}
