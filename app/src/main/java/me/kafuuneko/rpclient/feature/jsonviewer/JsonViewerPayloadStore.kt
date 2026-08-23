package me.kafuuneko.rpclient.feature.jsonviewer

import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerPayload
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * JSON 查看器的进程内临时载荷仓库。
 *
 * 避免把可能很大的请求或响应 JSON 放入 Intent；查看器开始解析时即消费并删除载荷。
 */
object JsonViewerPayloadStore {
    /** 支持页面启动期间并发存取的载荷表。 */
    private val mPayloads = ConcurrentHashMap<String, JsonViewerPayload>()

    /** 使用随机 key 保存载荷并返回导航参数。 */
    fun put(title: String, json: String): String {
        val key = UUID.randomUUID().toString()
        mPayloads[key] = JsonViewerPayload(title = title, json = json)
        return key
    }

    /** 取出载荷并立即从暂存区移除，解析完成后不继续持有大字符串。 */
    fun take(key: String?): JsonViewerPayload? {
        if (key.isNullOrBlank()) return null
        return mPayloads.remove(key)
    }

    /** 页面真正结束时释放载荷。 */
    fun remove(key: String?) {
        if (key.isNullOrBlank()) return
        mPayloads.remove(key)
    }
}
