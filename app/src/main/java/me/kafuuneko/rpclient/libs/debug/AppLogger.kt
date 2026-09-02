package me.kafuuneko.rpclient.libs.debug

import android.util.Log

/**
 * 统一应用内部运行日志工具。
 * 自动向 Android Logcat 输出并记录至 AppLogStore。
 */
object AppLogger {
    fun d(module: String, message: String, throwable: Throwable? = null) {
        runCatching { Log.d("RPClient-$module", message, throwable) }
        AppLogStore.addLog(AppLogLevel.DEBUG, module, message, throwable)
    }

    fun i(module: String, message: String, throwable: Throwable? = null) {
        runCatching { Log.i("RPClient-$module", message, throwable) }
        AppLogStore.addLog(AppLogLevel.INFO, module, message, throwable)
    }

    fun w(module: String, message: String, throwable: Throwable? = null) {
        runCatching { Log.w("RPClient-$module", message, throwable) }
        AppLogStore.addLog(AppLogLevel.WARN, module, message, throwable)
    }

    fun e(module: String, message: String, throwable: Throwable? = null) {
        runCatching { Log.e("RPClient-$module", message, throwable) }
        AppLogStore.addLog(AppLogLevel.ERROR, module, message, throwable)
    }
}
