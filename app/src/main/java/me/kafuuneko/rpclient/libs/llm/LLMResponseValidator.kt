package me.kafuuneko.rpclient.libs.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent

/**
 * 模型明确结束请求但没有返回任何可显示内容。
 *
 * 空内容配合 stop 并不代表一次有效生成，常见原因包括提示目标冲突、
 * 模型服务不接受当前消息顺序，或模型在网关内部立即停止。
 */
class LLMEmptyResponseException : IllegalStateException("The model returned an empty response")

/** 校验非流式结果，避免上层把空 stop 当作成功并静默结束。 */
internal fun LLMGenerationResponse.requireNonEmptyContent(): LLMGenerationResponse {
    if (content.isBlank()) {
        throw LLMEmptyResponseException()
    }
    return this
}

/**
 * 校验流式结果是否至少产生过一个非空文本增量。
 *
 * 完成事件仍会原样转发给 UI；只有流正常结束且始终没有有效文本时才抛出异常，
 * 网络错误和模型服务显式错误继续保留原异常。
 */
internal fun Flow<LLMStreamEvent>.requireNonEmptyContent(): Flow<LLMStreamEvent> = flow {
    var hasContent = false

    collect { event ->
        when (event) {
            is LLMStreamEvent.Delta -> {
                if (event.content.isNotBlank()) hasContent = true
            }
            is LLMStreamEvent.Finished -> Unit
        }
        emit(event)
    }

    if (!hasContent) {
        throw LLMEmptyResponseException()
    }
}
