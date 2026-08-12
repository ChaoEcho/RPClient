package me.kafuuneko.rpclient.libs.llm.adapter

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LLMReasoningEffort
import org.json.JSONObject

/** 可独立测试的请求字段增删结果，最终由协议适配器写入 JSON。 */
internal data class LLMReasoningPayloadMutation(
    val fields: Map<String, Any> = emptyMap(),
    val removedFields: Set<String> = emptySet()
) {
    fun applyTo(payload: JSONObject) {
        removedFields.forEach(payload::remove)
        fields.forEach { (name, value) -> payload.put(name, value.toJsonCompatible()) }
    }
}

/**
 * 将统一推理强度写入 OpenAI Compatible 请求体。
 *
 * Compatible 只约束基础消息格式，DeepSeek 与 OpenRouter 仍使用不同扩展字段；
 * 因此这里必须按实际 Provider 分派，Custom 默认不猜测其私有方言。
 */
internal fun JSONObject.applyOpenAICompatibleReasoning(
    providerType: LLMProviderType,
    model: String,
    effort: LLMReasoningEffort,
    includeReasoningInContent: Boolean
) {
    resolveOpenAICompatibleReasoning(
        providerType = providerType,
        model = model,
        effort = effort,
        includeReasoningInContent = includeReasoningInContent
    ).applyTo(this)
}

internal fun resolveOpenAICompatibleReasoning(
    providerType: LLMProviderType,
    model: String,
    effort: LLMReasoningEffort,
    includeReasoningInContent: Boolean
): LLMReasoningPayloadMutation {
    val fields = when (providerType) {
        LLMProviderType.DeepSeek -> deepSeekReasoningFields(effort)
        LLMProviderType.OpenRouter -> openRouterReasoningFields(
            model = model,
            effort = effort,
            includeReasoningInContent = includeReasoningInContent
        )
        LLMProviderType.ChatGPT -> openAIReasoningEffort(model, effort)
            ?.let { mapOf("reasoning_effort" to it) }
            .orEmpty()
        LLMProviderType.Grok -> grokReasoningEffort(model, effort)
            ?.let { mapOf("reasoning_effort" to it) }
            .orEmpty()
        LLMProviderType.Gemini,
        LLMProviderType.Claude,
        LLMProviderType.Custom -> emptyMap()
    }
    return LLMReasoningPayloadMutation(fields = fields)
}

/** 将统一推理强度写入 Gemini generationConfig。 */
internal fun JSONObject.applyGeminiReasoning(
    model: String,
    effort: LLMReasoningEffort,
    maxOutputTokens: Int
) {
    resolveGeminiReasoning(model, effort, maxOutputTokens).applyTo(this)
}

internal fun resolveGeminiReasoning(
    model: String,
    effort: LLMReasoningEffort,
    maxOutputTokens: Int
): LLMReasoningPayloadMutation {
    if (effort == LLMReasoningEffort.Auto) return LLMReasoningPayloadMutation()
    val normalizedModel = model.lowercase()
    val thinkingConfig: Map<String, Any> = when {
        normalizedModel.startsWith("gemini-3") -> mapOf(
            "thinkingLevel" to gemini3ThinkingLevel(normalizedModel, effort)
        )
        normalizedModel.startsWith("gemini-2.5-pro") -> mapOf(
            "thinkingBudget" to gemini25Budget(
                effort,
                maxOutputTokens,
                minimum = 128,
                maximum = 32_768
            )
        )
        normalizedModel.startsWith("gemini-2.5-flash") -> mapOf(
            "thinkingBudget" to gemini25Budget(
                effort = effort,
                maxOutputTokens = maxOutputTokens,
                minimum = 0,
                minimumPositive = if ("lite" in normalizedModel) 512 else 0,
                maximum = 24_576
            )
        )
        else -> return LLMReasoningPayloadMutation()
    }
    return LLMReasoningPayloadMutation(fields = mapOf("thinkingConfig" to thinkingConfig))
}

/** 将统一推理强度写入 Anthropic Messages 请求体。 */
internal fun JSONObject.applyAnthropicReasoning(
    providerType: LLMProviderType,
    model: String,
    effort: LLMReasoningEffort,
    maxTokens: Int
) {
    resolveAnthropicReasoning(providerType, model, effort, maxTokens).applyTo(this)
}

internal fun resolveAnthropicReasoning(
    providerType: LLMProviderType,
    model: String,
    effort: LLMReasoningEffort,
    maxTokens: Int
): LLMReasoningPayloadMutation {
    if (providerType != LLMProviderType.Claude) return LLMReasoningPayloadMutation()
    val normalizedModel = model.lowercase()
    val strictSamplingFields = if (normalizedModel.rejectsNonDefaultClaudeSampling()) {
        setOf("temperature", "top_p")
    } else {
        emptySet()
    }
    if (effort == LLMReasoningEffort.Auto) {
        return LLMReasoningPayloadMutation(removedFields = strictSamplingFields)
    }
    if (effort == LLMReasoningEffort.Minimum && !normalizedModel.requiresClaudeThinking()) {
        return LLMReasoningPayloadMutation(
            fields = mapOf("thinking" to mapOf("type" to "disabled")),
            removedFields = strictSamplingFields
        )
    }

    val fields = if (normalizedModel.supportsAdaptiveClaudeThinking()) {
        mapOf(
            "thinking" to mapOf("type" to "adaptive"),
            "output_config" to mapOf("effort" to effort.toAdaptiveClaudeEffort())
        )
    } else {
        require(maxTokens > MIN_ANTHROPIC_THINKING_TOKENS) {
            "Anthropic thinking requires max_tokens greater than " +
                "$MIN_ANTHROPIC_THINKING_TOKENS."
        }
        mapOf(
            "thinking" to mapOf(
                "type" to "enabled",
                "budget_tokens" to effort.toAnthropicBudget(maxTokens)
            )
        )
    }
    return LLMReasoningPayloadMutation(
        fields = fields,
        removedFields = setOf("temperature", "top_p")
    )
}

private fun deepSeekReasoningFields(effort: LLMReasoningEffort): Map<String, Any> {
    if (effort == LLMReasoningEffort.Auto) return emptyMap()
    if (effort == LLMReasoningEffort.Minimum) {
        return mapOf("thinking" to mapOf("type" to "disabled"))
    }
    val providerEffort = when (effort) {
        LLMReasoningEffort.Low -> "low"
        LLMReasoningEffort.Medium,
        LLMReasoningEffort.High -> "high"
        LLMReasoningEffort.Maximum -> "max"
        LLMReasoningEffort.Auto,
        LLMReasoningEffort.Minimum -> error("Handled before mapping")
    }
    return mapOf(
        "thinking" to mapOf("type" to "enabled"),
        "reasoning_effort" to providerEffort
    )
}

private fun openRouterReasoningFields(
    model: String,
    effort: LLMReasoningEffort,
    includeReasoningInContent: Boolean
): Map<String, Any> {
    val reasoning = mutableMapOf<String, Any>("exclude" to !includeReasoningInContent)
    if (effort != LLMReasoningEffort.Auto) {
        reasoning["effort"] = openRouterReasoningEffort(model, effort)
    }
    return mapOf("reasoning" to reasoning)
}

private fun openAIReasoningEffort(
    model: String,
    effort: LLMReasoningEffort
): String? {
    if (effort == LLMReasoningEffort.Auto) return null
    val normalizedModel = model.lowercase()
    val isGpt5 = normalizedModel.startsWith("gpt-5")
    val isOSeries = Regex("^o[1-9](?:-|$)").containsMatchIn(normalizedModel)
    if (!isGpt5 && !isOSeries) return null
    if (effort == LLMReasoningEffort.Minimum) {
        if ("-pro" in normalizedModel) return "medium"
        if (isOSeries) return "low"
        return if (Regex("^gpt-5\\.[1-9]").containsMatchIn(normalizedModel)) {
            "none"
        } else {
            "minimal"
        }
    }
    return when (effort) {
        LLMReasoningEffort.Low -> "low"
        LLMReasoningEffort.Medium -> "medium"
        LLMReasoningEffort.High -> "high"
        LLMReasoningEffort.Maximum -> when {
            normalizedModel.startsWith("gpt-5.6") -> "max"
            Regex("^gpt-5\\.[1-9]").containsMatchIn(normalizedModel) -> "xhigh"
            else -> "high"
        }
        LLMReasoningEffort.Auto,
        LLMReasoningEffort.Minimum -> error("Handled before mapping")
    }
}

private fun grokReasoningEffort(model: String, effort: LLMReasoningEffort): String? {
    if (effort == LLMReasoningEffort.Auto) return null
    val normalizedModel = model.lowercase()
    return when (effort) {
        LLMReasoningEffort.Minimum -> {
            if (isKnownGrokReasoningModel(normalizedModel)) "low" else "none"
        }
        LLMReasoningEffort.Low -> "low"
        LLMReasoningEffort.Medium -> "medium"
        LLMReasoningEffort.High -> "high"
        LLMReasoningEffort.Maximum -> if ("multi-agent" in normalizedModel) "xhigh" else "high"
        LLMReasoningEffort.Auto -> error("Handled before mapping")
    }
}

private fun openRouterReasoningEffort(model: String, effort: LLMReasoningEffort): String {
    val normalizedModel = model.lowercase()
    return when (effort) {
        LLMReasoningEffort.Minimum -> when {
            "google/gemini-3" in normalizedModel && "pro" in normalizedModel -> "low"
            "google/gemini-3" in normalizedModel -> "minimal"
            "grok-4.5" in normalizedModel -> "low"
            else -> "none"
        }
        LLMReasoningEffort.Low -> "low"
        LLMReasoningEffort.Medium -> "medium"
        LLMReasoningEffort.High -> "high"
        LLMReasoningEffort.Maximum -> when {
            "google/gemini" in normalizedModel || "grok-4.5" in normalizedModel -> "high"
            else -> "max"
        }
        LLMReasoningEffort.Auto -> error("Auto does not send an effort value")
    }
}

private fun gemini3ThinkingLevel(model: String, effort: LLMReasoningEffort): String {
    val isPro = "pro" in model
    // 旧 Gemini 3 Pro 只接受 low/high；3.1 Pro 起已支持 medium。
    val isLegacyPro = model.startsWith("gemini-3-pro")
    return when (effort) {
        LLMReasoningEffort.Minimum -> if (isPro) "low" else "minimal"
        LLMReasoningEffort.Low -> "low"
        LLMReasoningEffort.Medium -> if (isLegacyPro) "low" else "medium"
        LLMReasoningEffort.High,
        LLMReasoningEffort.Maximum -> "high"
        LLMReasoningEffort.Auto -> error("Auto does not send thinkingConfig")
    }
}

private fun gemini25Budget(
    effort: LLMReasoningEffort,
    maxOutputTokens: Int,
    minimum: Int,
    minimumPositive: Int = minimum,
    maximum: Int
): Int {
    val rawBudget = when (effort) {
        LLMReasoningEffort.Minimum -> minimum
        LLMReasoningEffort.Low -> maxOutputTokens * 15 / 100
        LLMReasoningEffort.Medium -> maxOutputTokens * 25 / 100
        LLMReasoningEffort.High -> maxOutputTokens * 50 / 100
        LLMReasoningEffort.Maximum -> maxOutputTokens
        LLMReasoningEffort.Auto -> error("Auto does not send thinkingConfig")
    }
    if (effort == LLMReasoningEffort.Minimum) return minimum
    return rawBudget.coerceIn(minimumPositive, maximum)
}

private fun String.supportsAdaptiveClaudeThinking(): Boolean {
    if (requiresClaudeThinking()) return true
    if (Regex("claude-(?:opus|sonnet)-4-(?:[6-9]|[1-9][0-9])").containsMatchIn(this)) {
        return true
    }
    return Regex("claude-(?:opus|sonnet|haiku|fable|mythos)-[5-9]").containsMatchIn(this)
}

private fun String.requiresClaudeThinking(): Boolean {
    return "fable-5" in this || "mythos" in this
}

/** 已知会强制推理的 Grok 模型不能接收 `none`，也不接受 stop 序列。 */
internal fun isKnownGrokReasoningModel(model: String): Boolean {
    val normalizedModel = model.lowercase()
    return normalizedModel.startsWith("grok-4.5") ||
        normalizedModel.startsWith("grok-build") ||
        "-reasoning" in normalizedModel ||
        "multi-agent" in normalizedModel
}

/** Claude 4.7+ 与第五代指定模型会在收到非默认采样参数时直接返回 400。 */
private fun String.rejectsNonDefaultClaudeSampling(): Boolean {
    return Regex("claude-(?:opus|sonnet)-4-(?:[7-9]|[1-9][0-9])").containsMatchIn(this) ||
        Regex("claude-(?:opus|sonnet|fable|mythos)-[5-9]").containsMatchIn(this) ||
        "claude-mythos-preview" in this
}

private fun LLMReasoningEffort.toAdaptiveClaudeEffort(): String {
    return when (this) {
        LLMReasoningEffort.Minimum,
        LLMReasoningEffort.Low -> "low"
        LLMReasoningEffort.Medium -> "medium"
        LLMReasoningEffort.High -> "high"
        LLMReasoningEffort.Maximum -> "max"
        LLMReasoningEffort.Auto -> error("Auto does not enable adaptive thinking")
    }
}

private fun LLMReasoningEffort.toAnthropicBudget(maxTokens: Int): Int {
    val requested = when (this) {
        LLMReasoningEffort.Minimum -> MIN_ANTHROPIC_THINKING_TOKENS
        LLMReasoningEffort.Low -> maxTokens * 10 / 100
        LLMReasoningEffort.Medium -> maxTokens * 25 / 100
        LLMReasoningEffort.High -> maxTokens * 50 / 100
        LLMReasoningEffort.Maximum -> maxTokens * 95 / 100
        LLMReasoningEffort.Auto -> error("Auto does not enable Anthropic thinking")
    }
    return requested
        .coerceAtLeast(MIN_ANTHROPIC_THINKING_TOKENS)
        .coerceAtMost(maxTokens - 1)
}

private fun Any.toJsonCompatible(): Any {
    if (this !is Map<*, *>) return this
    return JSONObject().also { json ->
        forEach { (key, value) ->
            if (key is String && value != null) {
                json.put(key, value.toJsonCompatible())
            }
        }
    }
}

private const val MIN_ANTHROPIC_THINKING_TOKENS = 1024
