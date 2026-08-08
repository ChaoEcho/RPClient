package me.kafuuneko.rpclient.libs.room.repository

import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_LLM_CONTEXT_TOKENS
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_LLM_MAX_TOKENS
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.DEFAULT_TOKEN_ESTIMATE_RESERVE_PERCENT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LLMProviderDefaultsTest {
    @Test
    fun currentProviderTemplatesUseSupportedModelIds() {
        val providers = createDefaultLLMProviders(now = 123L).associateBy { it.name }

        assertEquals(DEFAULT_GEMINI_MODEL, providers.getValue("Gemini").model)
        assertEquals(DEFAULT_CLAUDE_MODEL, providers.getValue("Claude").model)
        assertEquals(DEFAULT_DEEPSEEK_MODEL, providers.getValue("DeepSeek").model)
        assertEquals(DEFAULT_GROK_MODEL, providers.getValue("Grok").model)
        assertEquals(LLMProviderType.Grok, providers.getValue("Grok").providerType)
        assertEquals(
            LLMProviderProtocol.OpenAICompatible,
            providers.getValue("Grok").protocol
        )
        assertEquals("https://api.x.ai/v1", providers.getValue("Grok").baseUrl)
        assertEquals(DEFAULT_OPENROUTER_MODEL, providers.getValue("OpenRouter").model)
        assertEquals(
            setOf(DEFAULT_LLM_MAX_TOKENS),
            providers.values.map { it.maxTokens }.toSet()
        )
        assertEquals(
            setOf(DEFAULT_LLM_CONTEXT_TOKENS),
            providers.values.map { it.contextTokens }.toSet()
        )
        assertFalse(providers.getValue("Claude").sendTopP)
        assertFalse(providers.values.any { it.isEnabled })
        assertEquals(
            setOf(DEFAULT_TOKEN_ESTIMATE_RESERVE_PERCENT),
            providers.values.map { it.tokenEstimateReservePercent }.toSet()
        )
        assertEquals(setOf(123L), providers.values.map { it.createTime }.toSet())
        assertEquals(setOf(123L), providers.values.map { it.updateTime }.toSet())
    }
}
