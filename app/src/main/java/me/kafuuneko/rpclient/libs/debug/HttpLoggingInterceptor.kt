package me.kafuuneko.rpclient.libs.debug

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 满足隐私安全的轻量 HTTP 元数据调试日志拦截器。
 * 仅记录 HTTP 方法、路径、状态码与耗时，严禁记录 Authorization、Cookie、请求体与响应体。
 */
class HttpLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.method
        val url = request.url
        val path = "${url.host}${url.encodedPath}"
        val startNs = System.nanoTime()

        try {
            val response = chain.proceed(request)
            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            val code = response.code
            val message = "$method $path -> $code (${durationMs}ms)"
            if (response.isSuccessful) {
                AppLogger.d("HTTP", message)
            } else {
                AppLogger.w("HTTP", message)
            }
            return response
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            AppLogger.e("HTTP", "$method $path -> FAILED (${durationMs}ms): ${e.message ?: e.javaClass.simpleName}", e)
            throw e
        }
    }
}
