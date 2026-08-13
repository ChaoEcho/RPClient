package me.kafuuneko.rpclient.libs.llm.adapter

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAICompatibleLLMClientTest {
    @Test
    fun cleanContentStringPreservesLeadingWhitespace() {
        assertEquals(" world", cleanContentString(" world"))
    }

    @Test
    fun cleanContentStringTreatsNullLiteralAsEmpty() {
        assertEquals("", cleanContentString("null"))
    }

    @Test
    fun chatGPTUsesMaxCompletionTokens() {
        assertEquals(
            "max_completion_tokens",
            openAICompatibleTokenLimitField(LLMProviderType.ChatGPT)
        )
    }

    @Test
    fun thirdPartyCompatibleProviderKeepsMaxTokens() {
        assertEquals(
            "max_tokens",
            openAICompatibleTokenLimitField(LLMProviderType.OpenRouter)
        )
        assertEquals(
            "max_tokens",
            openAICompatibleTokenLimitField(LLMProviderType.Custom)
        )
    }

}
