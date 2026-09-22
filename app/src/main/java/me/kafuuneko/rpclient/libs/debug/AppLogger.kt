package me.kafuuneko.rpclient.libs.debug

import android.util.Log

/** 先经过开关与脱敏，再把同一份安全日志分别交给文件、内存及 Logcat。 */
object AppLogger {
    fun d(module: String, message: String, throwable: Throwable? = null) =
        write(AppLogLevel.DEBUG, Log.DEBUG, module, message, throwable)

    fun i(module: String, message: String, throwable: Throwable? = null) =
        write(AppLogLevel.INFO, Log.INFO, module, message, throwable)

    fun w(module: String, message: String, throwable: Throwable? = null) =
        write(AppLogLevel.WARN, Log.WARN, module, message, throwable)

    fun e(module: String, message: String, throwable: Throwable? = null) =
        write(AppLogLevel.ERROR, Log.ERROR, module, message, throwable)

    private fun write(
        level: AppLogLevel,
        priority: Int,
        module: String,
        message: String,
        throwable: Throwable?
    ) {
        val entry = AppLogStore.addLog(level, module, message, throwable) ?: return
        val text = entry.throwableSummary?.let { "${entry.message}\n$it" } ?: entry.message
        // 不把原始 Throwable 交给 Android 的重载，避免其消息绕过脱敏出口。
        runCatching { Log.println(priority, "RPClient-${entry.module}", text) }
    }
}
