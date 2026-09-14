package me.kafuuneko.rpclient.libs.prompt.model

import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference

/**
 * 尚未裁剪的失败附件，只存在于 Prompt 候选阶段。
 * 保留消息、UUID 与原顺序，不伪造缓存键或实际尺寸，也不进入 LLM 请求和数据库。
 */
data class UnavailablePromptImage(
    val messageId: Long,
    val uuid: String,
    val position: Int,
    val failure: ImageRequestFailure
)

/** 一次候选准备的局部结果，资源失败仅在最终保留时传播。 */
data class PromptImagePreparation(
    val references: Map<Long, List<LLMImageReference>>,
    val unavailable: Map<Long, List<UnavailablePromptImage>>
)

/** 最终选择完成后才校验失败附件；被淘汰的旧图无需阻断本次请求。 */
fun List<UnavailablePromptImage>.requireAvailable() {
    firstOrNull()?.let { throw ImageRequestException(it.failure) }
}
