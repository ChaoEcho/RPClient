package me.kafuuneko.rpclient.libs.generation

import java.util.ArrayDeque
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Per-key coroutine limiter for requests whose lifetime may include Flow collection.
 *
 * The latest limit supplied for a key is used for subsequent admissions. Existing permits
 * are never revoked when that limit decreases.
 */
class RequestConcurrencyLimiter {
    private val lock = Any()
    private val gates = mutableMapOf<String, GateState>()

    /** Runs [block] after acquiring a permit for [key]. */
    suspend fun <T> withPermit(
        key: String,
        limit: Int,
        block: suspend () -> T
    ): T {
        require(limit > 0) { "Concurrency limit must be positive" }

        val waiter = acquire(key, limit)
        try {
            currentCoroutineContext().ensureActive()
            return block()
        } finally {
            release(key, waiter)
        }
    }

    private suspend fun acquire(key: String, requestedLimit: Int): Waiter {
        lateinit var waiter: Waiter
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                waiter = Waiter(continuation)
                continuation.invokeOnCancellation {
                    cancelWaiter(key, waiter)
                }

                val toResume = synchronized(lock) {
                    if (waiter.cancelled || !continuation.isActive) {
                        emptyList()
                    } else {
                        val gate = gates.getOrPut(key) { GateState(requestedLimit) }
                        gate.limit = requestedLimit
                        gate.waiters.addLast(waiter)
                        drain(gate)
                    }
                }
                resumeWaiters(toResume)
            }
            return waiter
        } catch (cause: Throwable) {
            // Cancellation can race with a grant before the continuation resumes. In that
            // case the cancellation handler deliberately leaves release to this path.
            releaseIfGranted(key, waiter)
            throw cause
        }
    }

    private fun cancelWaiter(key: String, waiter: Waiter) {
        val toResume = synchronized(lock) {
            if (waiter.cancelled || waiter.granted) {
                emptyList()
            } else {
                waiter.cancelled = true
                val gate = gates[key]
                if (gate == null) {
                    emptyList()
                } else {
                    gate.waiters.remove(waiter)
                    val resumed = drain(gate)
                    removeIfIdle(key, gate)
                    resumed
                }
            }
        }
        resumeWaiters(toResume)
    }

    private fun releaseIfGranted(key: String, waiter: Waiter) {
        val toResume = synchronized(lock) {
            releaseLocked(key, waiter)
        }
        resumeWaiters(toResume)
    }

    private fun release(key: String, waiter: Waiter) {
        val toResume = synchronized(lock) {
            releaseLocked(key, waiter)
        }
        resumeWaiters(toResume)
    }

    private fun releaseLocked(key: String, waiter: Waiter): List<Waiter> {
        if (!waiter.granted || waiter.released) return emptyList()
        waiter.released = true

        val gate = gates[key] ?: return emptyList()
        check(gate.active > 0) { "Concurrency limiter active count underflow" }
        gate.active -= 1
        val toResume = drain(gate)
        removeIfIdle(key, gate)
        return toResume
    }

    /** Grants the stable FIFO prefix that fits the latest limit. Must be called under [lock]. */
    private fun drain(gate: GateState): List<Waiter> {
        val toResume = ArrayList<Waiter>()
        while (gate.active < gate.limit && gate.waiters.isNotEmpty()) {
            val waiter = gate.waiters.removeFirst()
            if (waiter.cancelled || !waiter.continuation.isActive) continue
            waiter.granted = true
            gate.active += 1
            toResume += waiter
        }
        return toResume
    }

    private fun removeIfIdle(key: String, gate: GateState) {
        if (gate.active == 0 && gate.waiters.isEmpty()) {
            gates.remove(key)
        }
    }

    private fun resumeWaiters(waiters: List<Waiter>) {
        waiters.forEach { waiter ->
            if (waiter.continuation.isActive) {
                waiter.continuation.resume(Unit)
            }
        }
    }

    private class GateState(
        var limit: Int,
        var active: Int = 0,
        val waiters: ArrayDeque<Waiter> = ArrayDeque()
    )

    private class Waiter(
        val continuation: CancellableContinuation<Unit>,
        var cancelled: Boolean = false,
        var granted: Boolean = false,
        var released: Boolean = false
    )
}
