package me.kafuuneko.rpclient.libs.llm.image

import me.kafuuneko.rpclient.libs.llm.ImageInputCapabilityResolver
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.ImageInputSetting
import me.kafuuneko.rpclient.libs.llm.model.ImageTokenEstimatorType
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.messageWithBlocks
import me.kafuuneko.rpclient.libs.prompt.PromptTokenizer
import me.kafuuneko.rpclient.libs.prompt.PromptTokenizerRegistry
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import me.kafuuneko.rpclient.libs.room.Converters
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.toConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证模型 sizing、能力优先级和完整／有界统计的共同边界。 */
class ImageTokenEstimatorTest {
    private val mCapabilities = ImageInputCapabilityResolver()
    private val mImages = ImageTokenEstimatorRegistry(mCapabilities)
    private val mTokenizers = PromptTokenizerRegistry(mImages)
    private val mProvider = LLMProvider(name = "test", providerType = LLMProviderType.Custom,
        protocol = LLMProviderProtocol.OpenAICompatible, baseUrl = "https://example.invalid", model = "gpt-4o")

    private fun image(width: Int, height: Int) = LLMImageReference("same", "same", "image/png", width, height, 10)
    private fun count(type: ImageTokenEstimatorType, width: Int, height: Int): Long =
        mImages.resolve(mProvider.copy(imageTokenEstimatorType = type).toConfig()).count(listOf(image(width, height)))

    @Test
    fun tileFamiliesUseDifferentCoefficientsWithoutUpscalingSmallImages() {
        assertEquals(765L, count(ImageTokenEstimatorType.OpenAiTile4o, 1024, 1024))
        assertEquals(25501L, count(ImageTokenEstimatorType.OpenAiTile4oMini, 1024, 1024))
        assertEquals(255L, count(ImageTokenEstimatorType.OpenAiTile4o, 128, 128))
        assertEquals(1105L, count(ImageTokenEstimatorType.OpenAiTile4o, 2048, 4096))
    }

    @Test
    fun patchFamiliesRespectTheirOwnSizingTableAndMultiplier() {
        assertEquals(1659L, count(ImageTokenEstimatorType.OpenAiPatch41Mini, 1024, 1024))
        assertEquals(6636L, count(ImageTokenEstimatorType.OpenAiPatch41Mini, 2048, 2048))
        assertEquals(1229L, count(ImageTokenEstimatorType.OpenAiPatch54, 1024, 1024))
        assertEquals(3000L, count(ImageTokenEstimatorType.OpenAiPatch54, 2048, 2048))
        assertEquals(2L, count(ImageTokenEstimatorType.OpenAiPatch54, 32, 32))
        assertEquals(3L, count(ImageTokenEstimatorType.OpenAiPatch54, 33, 32))
        assertEquals(count(ImageTokenEstimatorType.OpenAiPatch54, 4096, 512),
            count(ImageTokenEstimatorType.OpenAiPatch54, 512, 4096))
    }

    @Test
    fun claudeMatchesOfficialResizingExamplesAndPatchBoundaries() {
        // 使用官方给出的整数缩放样例，尤其保护阶跃边界。
        val standard = ImageTokenEstimatorType.ClaudeStandard
        val high = ImageTokenEstimatorType.ClaudeHighResolution
        assertEquals(64L, count(standard, 200, 200))
        assertEquals(1296L, count(standard, 1000, 1000))
        assertEquals(1560L, count(standard, 1920, 1080))
        assertEquals(1564L, count(standard, 2000, 1500))
        assertEquals(4784L, count(high, 3840, 2160))
        assertEquals(2691L, count(high, 1920, 1080))
        assertEquals(1L, count(standard, 28, 28))
        assertEquals(2L, count(standard, 29, 28))
        assertEquals(count(standard, 1075, 1520), count(standard, 1520, 1075))
    }

    @Test
    fun automaticAliasesAreExplicitAndManualChoiceCanCrossProtocols() {
        assertEquals(ImageTokenEstimatorType.OpenAiTile4oMini, mImages.automaticType("openai/gpt-4o-mini"))
        assertEquals(ImageTokenEstimatorType.OpenAiPatch41Mini, mImages.automaticType("gpt-4.1-mini-2025-04-14"))
        for (unknown in listOf("gateway/gpt-4o", "gpt-4o-custom", "gpt-future", "gemini-2.5-pro")) {
            assertEquals(ImageTokenEstimatorType.Generic, mImages.automaticType(unknown))
        }
        val config = mProvider.copy(protocol = LLMProviderProtocol.AnthropicMessages,
            imageTokenEstimatorType = ImageTokenEstimatorType.OpenAiTile4oMini).toConfig()
        assertEquals(25501L, mImages.resolve(config).count(listOf(image(1024, 1024))))
        assertEquals(ImageTokenEstimatorType.Automatic, Converters().toImageTokenEstimatorType("future-value"))
    }

    @Test
    fun geminiReadsExistingResolutionWithoutChangingRequestParameters() {
        val provider = mProvider.copy(imageTokenEstimatorType = ImageTokenEstimatorType.Gemini3)
        val expected = mapOf("MEDIA_RESOLUTION_LOW" to 280L, "MEDIA_RESOLUTION_MEDIUM" to 560L,
            "MEDIA_RESOLUTION_HIGH" to 1120L, "future" to 4096L)
        for ((resolution, tokens) in expected) {
            val config = provider.copy(requestBodyPatchJson = """{"generationConfig":{"mediaResolution":"$resolution"}}""").toConfig()
            assertEquals(tokens, mImages.resolve(config).count(listOf(image(1024, 1024))))
        }
        assertEquals(1120L, mImages.resolve(provider.toConfig()).count(listOf(image(1024, 1024))))
    }

    @Test
    fun unsupportedDoesNotCallEstimatorEvenForUnavailableMetadata() {
        // 禁用状态在任何元数据读取或公式执行之前直接返回零。
        val counter = ImageTokenCounter(null)
        assertEquals(0L, counter.count(listOf(image(0, 0))))
        assertEquals(0L, counter.countUnavailable(3))
        for (content in listOf("text", "")) {
            val provider = mProvider.copy(imageInputSetting = ImageInputSetting.Unsupported,
                imageTokenEstimatorType = ImageTokenEstimatorType.OpenAiTile4oMini)
            val tokenizer = mTokenizers.resolve(provider)
            val withImage = messageWithBlocks(LLMMessageRole.User,
                listOf(LLMContentBlock.Image(image(0, 0)), LLMContentBlock.Text(content)))
            val expected = tokenizer.countMessages(listOf(LLMMessage(LLMMessageRole.User, content)))
            assertTrue(expected > 0)
            assertEquals(expected, tokenizer.countMessages(listOf(withImage)))
            assertEquals(expected, tokenizer.countMessagesUpTo(listOf(withImage), expected))
            assertEquals(expected, mTokenizers.resolveForUsage(provider.toConfig()).countMessages(listOf(withImage)))
        }
    }

    @Test
    fun catalogCapabilityAndEstimatorAreFrozenPerTokenizer() {
        val knownBefore = mTokenizers.resolve(mProvider)
        val config = mProvider.toConfig()
        mCapabilities.record(config, listOf(LLMAvailableModel(config.model, inputModalities = setOf("text"))))
        val disabled = mTokenizers.resolve(mProvider)
        assertEquals(0L, disabled.countImages(listOf(image(1024, 1024))))
        assertEquals(765L, knownBefore.countImages(listOf(image(1024, 1024))))
        val enabled = mTokenizers.resolve(mProvider.copy(imageInputSetting = ImageInputSetting.Supported))
        assertEquals(765L, enabled.countImages(listOf(image(1024, 1024))))
        assertEquals(4096L, mTokenizers.resolve(mProvider.copy(model = "unknown")).countUnavailableImages(1))
    }

    @Test
    fun eachOccurrenceAndEachModelUsesItsOwnEstimate() {
        val images = listOf(image(1024, 1024), image(1024, 1024))
        assertEquals(1530L, mTokenizers.resolve(mProvider).countImages(images))
        assertEquals(51002L, mTokenizers.resolve(mProvider.copy(model = "gpt-4o-mini")).countImages(images))
        assertEquals(0L, mTokenizers.resolve(mProvider.copy(imageInputSetting = ImageInputSetting.Unsupported)).countImages(images))
        assertEquals(1530L, mTokenizers.resolve(mProvider).countImages(images))
        assertEquals(8192L, mTokenizers.resolve(null).countImages(images))
    }

    @Test
    fun fullAndBoundedCountingSaturateInsteadOfWrapping() {
        val tokenizer = object : PromptTokenizer {
            override val name = "extreme"
            override val strategy = PromptTokenizerStrategy.Estimated
            override fun countText(text: String) = Int.MAX_VALUE
        }
        val messages = listOf(LLMMessage(LLMMessageRole.User, "a"), LLMMessage(LLMMessageRole.Assistant, "b"))
        assertEquals(Int.MAX_VALUE, tokenizer.countMessages(messages))
        assertEquals(Int.MAX_VALUE, tokenizer.countMessagesUpTo(messages, Int.MAX_VALUE))
        for (type in ImageTokenEstimatorType.entries) {
            assertTrue(count(type, Int.MAX_VALUE, 1) > 0)
            assertTrue(count(type, Int.MAX_VALUE, Int.MAX_VALUE) > 0)
        }
    }
}
