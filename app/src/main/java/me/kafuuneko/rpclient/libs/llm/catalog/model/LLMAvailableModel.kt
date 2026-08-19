package me.kafuuneko.rpclient.libs.llm.catalog.model

/** 模型服务目录中可供用户选择的非敏感模型信息。 */
data class LLMAvailableModel(
    val id: String,
    val displayName: String = id,
    val description: String? = null,
    val contextTokens: Int? = null,
    val maxOutputTokens: Int? = null,
    val supportedParameters: Set<String> = emptySet()
)
