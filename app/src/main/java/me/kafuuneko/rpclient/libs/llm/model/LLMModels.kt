package me.kafuuneko.rpclient.libs.llm.model

/** 新供应商为模型回复预留的默认 Token 数。 */
const val DEFAULT_LLM_MAX_TOKENS = 8192

/** 新供应商用于输入与输出的默认总上下文预算。 */
const val DEFAULT_LLM_CONTEXT_TOKENS = 32768

/** 高级请求 JSON 中用于引用稳定匿名会话路由 ID 的系统变量名。 */
const val LLM_REQUEST_VARIABLE_ROUTING_SESSION_ID = "\$rpclient.routing_session_id"

/** 新建及迁移 OpenRouter 配置时写入高级 JSON 的默认会话粘性模板。 */
const val DEFAULT_OPENROUTER_REQUEST_BODY_PATCH_JSON =
    "{\"session_id\":\"\$rpclient.routing_session_id\"}"

/**
 * 在线模型供应商类型，用于 UI 展示和统计归类。
 */
enum class LLMProviderType {
    ChatGPT,
    Gemini,
    Claude,
    DeepSeek,
    Grok,
    OpenRouter,
    Custom
}

/**
 * 供应商实际使用的 HTTP 协议。
 *
 * ChatGPT、DeepSeek、OpenRouter 以及大多数第三方网关都归入 OpenAICompatible。
 */
enum class LLMProviderProtocol {
    OpenAICompatible,
    Gemini,
    AnthropicMessages
}

/**
 * LLM 模块运行时使用的 Provider 配置
 */
data class LLMProviderConfig(
    val name: String,
    val providerType: LLMProviderType,
    val protocol: LLMProviderProtocol,
    val baseUrl: String,
    val apiKey: String = "",
    val model: String,
    val customHeadersJson: String = "",
    /** 合并到协议请求体的 JSON Merge Patch；结构字段由各协议适配器保护。 */
    val requestBodyPatchJson: String = "{}",
    val temperature: Float = 0.8f,
    val topP: Float = 1.0f,
    val maxTokens: Int = DEFAULT_LLM_MAX_TOKENS,
    val contextTokens: Int = DEFAULT_LLM_CONTEXT_TOKENS,
    val sendTemperature: Boolean = true,
    val sendTopP: Boolean = true
)

/**
 * 通用聊天消息角色，适配器会转换成各协议自己的角色名称。
 */
enum class LLMMessageRole {
    System,
    User,
    Assistant
}

/**
 * 通用聊天消息。
 */
data class LLMMessage(
    val role: LLMMessageRole,
    val content: String
)

/**
 * 通用生成参数。为空时使用当前 Provider 的默认配置。
 */
data class LLMGenerationOptions(
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val topP: Float? = null,
    val stop: List<String> = emptyList()
)

/** 已按 Provider 能力开关收敛的实际请求参数。 */
data class ResolvedLLMGenerationOptions(
    val temperature: Float?,
    val maxTokens: Int,
    val topP: Float?,
    val stop: List<String>
)

/** 将业务请求参数与 Provider 默认值合并，并过滤未启用的可选参数。 */
fun LLMGenerationOptions.resolveFor(
    provider: LLMProviderConfig
): ResolvedLLMGenerationOptions {
    return ResolvedLLMGenerationOptions(
        temperature = if (provider.sendTemperature) {
            temperature ?: provider.temperature
        } else {
            null
        },
        maxTokens = maxTokens ?: provider.maxTokens,
        topP = if (provider.sendTopP) topP ?: provider.topP else null,
        stop = stop
    )
}

/**
 * 通用生成请求，非流式与流式接口共用同一个请求模型。
 */
data class LLMGenerationRequest(
    val messages: List<LLMMessage>,
    val model: String? = null,
    val options: LLMGenerationOptions = LLMGenerationOptions(),
    val includeReasoningInContent: Boolean = false,
    /** 请求模板可用于会话粘性路由的不透明 ID；字段位置由 Provider 配置决定。 */
    val routingSessionId: String? = null,
    /** 已完成宏展开、后处理和最终上下文预算，不应在 Repository 中再次改写。 */
    val isPromptFinalized: Boolean = false
)

/**
 * Token 用量信息。不同供应商字段不完全一致，因此允许为空。
 */
data class LLMUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)

/**
 * 一次性生成完成后的完整响应。
 */
data class LLMGenerationResponse(
    val content: String,
    val model: String,
    val provider: LLMProviderType,
    val usage: LLMUsage? = null,
    /** 供应商给出的停止原因，用于区分正常完成、长度限制和空响应。 */
    val finishReason: String? = null,
    val rawResponse: String
)

/**
 * 流式生成事件。
 */
sealed class LLMStreamEvent {
    /**
     * 模型增量输出的文本片段。
     */
    data class Delta(
        val content: String,
        val rawChunk: String
    ) : LLMStreamEvent()

    /**
     * 供应商明确返回的完成事件。
     */
    data class Finished(
        val rawChunk: String? = null,
        /** 流式协议在结束块中返回的停止原因。 */
        val finishReason: String? = null,
        /** 网关实际路由到的模型名；没有提供时由调用方使用请求模型。 */
        val model: String? = null
    ) : LLMStreamEvent()
}
