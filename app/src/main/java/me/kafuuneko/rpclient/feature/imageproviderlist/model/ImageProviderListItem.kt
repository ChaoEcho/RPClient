package me.kafuuneko.rpclient.feature.imageproviderlist.model

/** 图片服务列表项展示模型。 */
data class ImageProviderListItem(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val model: String,
    val maxConcurrentRequests: Int,
    val isCurrent: Boolean
) {
    /** 地址或模型缺失时无法发起请求，列表用状态点提示。 */
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()
}
