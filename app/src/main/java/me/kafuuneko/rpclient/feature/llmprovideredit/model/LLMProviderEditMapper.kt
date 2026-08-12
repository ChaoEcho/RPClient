package me.kafuuneko.rpclient.feature.llmprovideredit.model

import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** 将持久化 Provider 映射成不含密钥原文的编辑表单。 */
internal fun LLMProvider.toEditForm() = LLMProviderEditForm(
    id = id,
    createTime = createTime,
    name = name,
    providerType = providerType,
    protocol = protocol,
    baseUrl = baseUrl,
    hasExistingApiKey = apiKey.isNotBlank(),
    model = model,
    hasExistingCustomHeaders = customHeadersJson.isNotBlank(),
    requestBodyPatchJson = requestBodyPatchJson,
    temperature = temperature.toString(),
    topP = topP.toString(),
    maxTokens = maxTokens.toString(),
    contextTokens = contextTokens.toString(),
    tokenEstimateReservePercent = tokenEstimateReservePercent,
    sendTemperature = sendTemperature,
    sendTopP = sendTopP,
    promptPostProcessingMode = PromptPostProcessingMode.fromOrdinal(promptPostProcessingMode),
    isEnabled = isEnabled
)
