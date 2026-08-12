package me.kafuuneko.rpclient.feature.llmprovideredit.model

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_LLM_CONTEXT_TOKENS
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_LLM_MAX_TOKENS
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode

class LLMProviderEditFormTest {
    @Test
    fun newFormDefaultsTokenEstimateReserveToFifteenPercent() {
        assertEquals(15, LLMProviderEditForm().tokenEstimateReservePercent)
    }

    @Test
    fun newFormReservesEnoughOutputAndPromptBudget() {
        val form = LLMProviderEditForm()

        assertEquals(DEFAULT_LLM_MAX_TOKENS.toString(), form.maxTokens)
        assertEquals(DEFAULT_LLM_CONTEXT_TOKENS.toString(), form.contextTokens)
        assertNotNull(
            form.copy(
                name = "Test",
                baseUrl = "https://example.com",
                model = "model"
            ).toProviderOrNull()
        )
    }

    @Test
    fun acceptsPositiveResponseBudgetSmallerThanContext() {
        assertNotNull(validForm().toProviderOrNull())
    }

    @Test
    fun rejectsNonPositiveOrExhaustedPromptBudget() {
        assertNull(validForm().copy(maxTokens = "0").toProviderOrNull())
        assertNull(validForm().copy(contextTokens = "0").toProviderOrNull())
        assertNull(
            validForm()
                .copy(maxTokens = "4096", contextTokens = "4096")
                .toProviderOrNull()
        )
        assertNull(
            validForm()
                .copy(maxTokens = "4097", contextTokens = "4096")
                .toProviderOrNull()
        )
    }

    @Test
    fun validatesEnabledSamplingParametersAgainstProtocolRange() {
        assertNull(
            validForm()
                .copy(
                    protocol = LLMProviderProtocol.AnthropicMessages,
                    temperature = "1.5",
                    sendTemperature = true
                )
                .toProviderOrNull()
        )
        assertNotNull(
            validForm()
                .copy(
                    protocol = LLMProviderProtocol.AnthropicMessages,
                    temperature = "1.5",
                    sendTemperature = false
                )
                .toProviderOrNull()
        )
        assertNull(validForm().copy(topP = "1.1", sendTopP = true).toProviderOrNull())
    }

    @Test
    fun validatesTokenEstimateReserveRange() {
        listOf(0, 15, 35, 50).forEach { value ->
            assertNotNull(
                validForm().copy(tokenEstimateReservePercent = value).toProviderOrNull()
            )
        }
        listOf(-1, 51).forEach { value ->
            assertNull(
                validForm().copy(tokenEstimateReservePercent = value).toProviderOrNull()
            )
        }
    }

    @Test
    fun roundTripsCapabilityFlagsAndProviderPostProcessing() {
        val provider = validForm().copy(
            sendTemperature = false,
            sendTopP = true,
            tokenEstimateReservePercent = 35,
            promptPostProcessingMode = PromptPostProcessingMode.SemiStrict
        ).toProviderOrNull() ?: error("Provider should be valid")

        val restored = provider.toEditForm()

        assertEquals(false, restored.sendTemperature)
        assertEquals(true, restored.sendTopP)
        assertEquals(35, restored.tokenEstimateReservePercent)
        assertEquals(PromptPostProcessingMode.SemiStrict, restored.promptPostProcessingMode)
    }

    @Test
    fun roundTripsRequestBodyPatchAndRejectsProtectedFields() {
        val patch = """{"provider":{"order":["deepinfra"]}}"""
        val provider = validForm().copy(
            providerType = LLMProviderType.OpenRouter,
            requestBodyPatchJson = patch
        ).toProviderOrNull() ?: error("Provider should be valid")

        assertEquals(patch, provider.requestBodyPatchJson)
        assertEquals(patch, provider.toEditForm().requestBodyPatchJson)
        assertNull(validForm().copy(requestBodyPatchJson = """{"messages":[]}""").toProviderOrNull())
        assertNull(
            validForm().copy(
                providerType = LLMProviderType.OpenRouter,
                requestBodyPatchJson = """{"provider":{"order":[""]}}"""
            ).toProviderOrNull()
        )
    }

    @Test
    fun acceptsSupportedRequestVariableAndRejectsUnknownVariable() {
        val supportedPatch =
            """{"metadata":{"conversation":"${'$'}rpclient.routing_session_id"}}"""

        assertEquals(
            supportedPatch,
            validForm().copy(requestBodyPatchJson = supportedPatch)
                .toProviderOrNull()
                ?.requestBodyPatchJson
        )
        assertNull(
            validForm().copy(
                requestBodyPatchJson =
                    """{"metadata":{"conversation":"${'$'}rpclient.unknown"}}"""
            ).toProviderOrNull()
        )
    }

    private fun validForm(): LLMProviderEditForm {
        return LLMProviderEditForm(
            name = "Test",
            baseUrl = "https://example.com",
            model = "model",
            maxTokens = "512",
            contextTokens = "4096"
        )
    }
}
