package me.kafuuneko.rpclient.libs.generation

import android.content.Context
import me.kafuuneko.rpclient.service.AiGenerationForegroundService
import java.util.concurrent.atomic.AtomicBoolean

/** Keeps the foreground service alive while submitted single-chat AI tasks are running. */
class AiTaskForegroundController(context: Context) {
    private val appContext = context.applicationContext
    private var activeCount = 0

    fun acquire(): AutoCloseable {
        val count = synchronized(this) {
            activeCount += 1
            activeCount
        }
        AiGenerationForegroundService.update(appContext, count)
        return Handle()
    }

    private inner class Handle : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            val count = synchronized(this@AiTaskForegroundController) {
                activeCount = (activeCount - 1).coerceAtLeast(0)
                activeCount
            }
            AiGenerationForegroundService.update(appContext, count)
        }
    }
}
