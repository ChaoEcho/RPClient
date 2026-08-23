package me.kafuuneko.rpclient.feature.requestlog.model

/** 请求日志列表展示模型，只保存固定长度预览；完整 JSON 在用户操作时按 ID 加载。 */
data class RequestLogItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val requestPreview: String,
    val responsePreview: String
)
