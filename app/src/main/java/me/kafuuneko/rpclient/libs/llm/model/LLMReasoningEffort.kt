package me.kafuuneko.rpclient.libs.llm.model

/**
 * 跨供应商统一的推理强度。
 *
 * [Minimum] 表示供应商允许的最低推理量：能够关闭思考的模型映射为关闭，
 * 强制思考的模型则映射为其最低档。具体 JSON 字段由协议适配器负责生成。
 */
enum class LLMReasoningEffort(val persistedValue: Int) {
    /** 不发送推理控制参数，沿用供应商和模型默认行为。 */
    Auto(0),

    /** 使用模型支持的最低推理量，可能等价于关闭思考。 */
    Minimum(1),

    Low(2),
    Medium(3),
    High(4),
    Maximum(5);

    companion object {
        val conversationDefault: LLMReasoningEffort = Auto
        val storyDefault: LLMReasoningEffort = Minimum

        fun fromPersistedValue(
            value: Int,
            fallback: LLMReasoningEffort = conversationDefault
        ): LLMReasoningEffort {
            return entries.firstOrNull { it.persistedValue == value } ?: fallback
        }
    }
}

/** 一次生成请求所属的全局推理配置类别。 */
enum class LLMReasoningScope {
    Conversation,
    Story
}

/** 在请求构建时读取当前类别的推理强度，避免 Builder 绑定具体偏好实现。 */
fun interface LLMReasoningEffortProvider {
    fun current(scope: LLMReasoningScope): LLMReasoningEffort

    companion object {
        val defaults = LLMReasoningEffortProvider { scope ->
            when (scope) {
                LLMReasoningScope.Conversation -> LLMReasoningEffort.conversationDefault
                LLMReasoningScope.Story -> LLMReasoningEffort.storyDefault
            }
        }
    }
}
