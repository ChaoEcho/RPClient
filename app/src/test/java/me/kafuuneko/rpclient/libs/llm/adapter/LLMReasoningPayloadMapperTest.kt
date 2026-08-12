package me.kafuuneko.rpclient.libs.llm.adapter

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LLMReasoningEffort
import org.junit.Assert.assertEquals
import org.junit.Test

class LLMReasoningPayloadMapperTest {
    @Test
    fun gemini31ProPreservesMediumThinkingLevel() {
        assertEquals(
            "medium",
            geminiThinkingLevel("gemini-3.1-pro-preview", LLMReasoningEffort.Medium)
        )
    }

    @Test
    fun legacyGemini3ProDowngradesUnsupportedMediumLevel() {
        assertEquals(
            "low",
            geminiThinkingLevel("gemini-3-pro-preview", LLMReasoningEffort.Medium)
        )
    }

    @Test
    fun gemini31ProMapsUnsupportedMinimumToLow() {
        assertEquals(
            "low",
            geminiThinkingLevel("gemini-3.1-pro-preview", LLMReasoningEffort.Minimum)
        )
    }

    @Test
    fun grokReasoningAliasesMapMinimumToLow() {
        listOf(
            "grok-4.5-latest",
            "grok-build-latest",
            "grok-4.20-0309-reasoning",
            "grok-4.20-multi-agent"
        ).forEach { model ->
            val mutation = resolveOpenAICompatibleReasoning(
                providerType = LLMProviderType.Grok,
                model = model,
                effort = LLMReasoningEffort.Minimum,
                includeReasoningInContent = false
            )

            assertEquals("low", mutation.fields["reasoning_effort"])
        }
    }

    @Test
    fun claude47RemovesSamplingFieldsEvenWithAutomaticReasoning() {
        val mutation = resolveAnthropicReasoning(
            providerType = LLMProviderType.Claude,
            model = "claude-opus-4-7",
            effort = LLMReasoningEffort.Auto,
            maxTokens = 8_192
        )

        assertEquals(setOf("temperature", "top_p"), mutation.removedFields)
    }

    @Test
    fun claude48MinimumDisablesThinkingAndRemovesSamplingFields() {
        val mutation = resolveAnthropicReasoning(
            providerType = LLMProviderType.Claude,
            model = "claude-opus-4-8",
            effort = LLMReasoningEffort.Minimum,
            maxTokens = 8_192
        )

        assertEquals(mapOf("type" to "disabled"), mutation.fields["thinking"])
        assertEquals(setOf("temperature", "top_p"), mutation.removedFields)
    }

    private fun geminiThinkingLevel(
        model: String,
        effort: LLMReasoningEffort
    ): String {
        val mutation = resolveGeminiReasoning(
            model = model,
            effort = effort,
            maxOutputTokens = 8_192
        )
        val thinkingConfig = mutation.fields["thinkingConfig"] as Map<*, *>
        return thinkingConfig["thinkingLevel"] as String
    }
}
