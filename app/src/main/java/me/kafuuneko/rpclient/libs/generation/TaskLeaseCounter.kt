package me.kafuuneko.rpclient.libs.generation

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 串行发布任务计数并返回可幂等释放的租约。
 *
 * 获取失败不增加计数；释放失败仍释放所有权，避免通知状态影响业务任务生命周期。
 * 外部更新与计数变更必须使用同一锁，防止并发调用把旧计数最后发布出去。
 */
internal class TaskLeaseCounter(private val update: (Int) -> Unit) {
    private val lock = Any()
    private var count = 0

    internal val activeCount: Int
        get() = synchronized(lock) { count }

    fun acquire(): AutoCloseable = synchronized(lock) {
        update(count + 1)
        count += 1
        Lease()
    }

    private inner class Lease : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            synchronized(lock) {
                check(count > 0) { "Task lease count underflow" }
                count -= 1
                update(count)
            }
        }
    }
}
