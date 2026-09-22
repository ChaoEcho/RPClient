package me.kafuuneko.rpclient.libs.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * IO 准备结果在切回调用方调度器时可能因取消而无法交付，此时仍必须清理临时文件。
 * 准备函数负责准备过程中的失败；本函数负责已经完成但未能移交的资源。
 */
internal suspend fun <T : Any> prepareIoResource(
    release: suspend (T) -> Unit,
    prepare: suspend () -> T
): T {
    var completed: T? = null
    try {
        return withContext(Dispatchers.IO) { prepare().also { completed = it } }
    } catch (error: Throwable) {
        val resource = completed
        if (resource != null) {
            try {
                withContext(NonCancellable + Dispatchers.IO) { release(resource) }
            } catch (cleanupFailure: Throwable) {
                error.addSuppressed(cleanupFailure)
            }
        }
        throw error
    }
}
