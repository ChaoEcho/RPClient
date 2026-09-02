package me.kafuuneko.rpclient.feature.main.model

/** 设置页渲染所需的模型配置摘要，不包含任何鉴权信息。 */
data class MainProviderItem(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val model: String,
    val isEnabled: Boolean,
    /** 上下文预算是最影响体感的设置，但它只存在于模型编辑页；带到首页让它至少可见。 */
    val contextTokens: Int
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()
}
