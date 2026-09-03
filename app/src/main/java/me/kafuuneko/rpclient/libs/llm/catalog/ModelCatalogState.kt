package me.kafuuneko.rpclient.libs.llm.catalog

import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel

/**
 * 在线模型目录查询的生命周期与可渲染结果。
 *
 * 对话模型与图片服务两个编辑页共用同一套拉取交互，状态定义因此放在目录层而非某个页面下。
 */
sealed class ModelCatalogState {
    data object Idle : ModelCatalogState()
    data object Loading : ModelCatalogState()
    data class Loaded(val models: List<LLMAvailableModel>) : ModelCatalogState()
    data class Failed(val failure: LLMModelCatalogFailure) : ModelCatalogState()
}
