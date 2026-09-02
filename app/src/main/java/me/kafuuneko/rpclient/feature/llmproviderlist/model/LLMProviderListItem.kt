package me.kafuuneko.rpclient.feature.llmproviderlist.model

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType

/** 模型配置列表渲染所需的最小摘要，不包含密钥和自定义请求头。 */
data class LLMProviderListItem(
    val id: Long,
    val name: String,
    val providerType: LLMProviderType,
    val protocol: LLMProviderProtocol,
    val baseUrl: String,
    val model: String,
    val isEnabled: Boolean,
    val isCurrent: Boolean = false
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()
}
