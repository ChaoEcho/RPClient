package me.kafuuneko.rpclient.libs.room.model

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol

/** 请求日志列表专用投影，只携带元数据和固定长度预览，不加载完整原始载荷。 */
data class LLMRequestLogOverview(
    val id: Long,
    val createTime: Long,
    val providerName: String,
    val protocol: LLMProviderProtocol,
    val model: String,
    val isStreaming: Boolean,
    val requestPreview: String,
    val responsePreview: String
)
