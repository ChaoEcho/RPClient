package me.kafuuneko.rpclient.libs.chat.generation

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.debug.AppLogger
import me.kafuuneko.rpclient.libs.generation.AiTaskForegroundController
import me.kafuuneko.rpclient.libs.generation.RequestConcurrencyLimiter
import me.kafuuneko.rpclient.libs.imagegeneration.CharacterVisualIdentityResolver
import me.kafuuneko.rpclient.libs.imagegeneration.GeneratedImage
import me.kafuuneko.rpclient.libs.imagegeneration.ImageGenerationConfig
import me.kafuuneko.rpclient.libs.imagegeneration.OpenAICompatibleImageClient
import me.kafuuneko.rpclient.libs.imagegeneration.buildFallbackScenePrompt
import me.kafuuneko.rpclient.libs.imagegeneration.buildImagePrompt
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ImageProvider
import me.kafuuneko.rpclient.libs.room.entity.imageProviderPermitKey
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.ImageProviderRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LLM_PERMIT_SCOPE_IMAGE_PROMPT
import me.kafuuneko.rpclient.utils.stripThinkBlocks

/** Application-scoped owner for independent per-message image generation tasks. */
class ChatImageGenerationCoordinator(
    private val chatRepository: ChatRepository,
    private val characterRepository: CharacterRepository,
    private val fileRepository: FileRepository,
    private val imageProviderRepository: ImageProviderRepository,
    private val visualIdentityResolver: CharacterVisualIdentityResolver,
    private val imageClient: OpenAICompatibleImageClient,
    private val llmRepository: LLMRepository,
    private val providerSelectionResolver: LLMProviderSelectionResolver,
    private val context: Context,
    private val requestConcurrencyLimiter: RequestConcurrencyLimiter,
    private val foregroundController: AiTaskForegroundController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeByMessage = mutableMapOf<Long, ActiveTask>()
    private val mutableStates = MutableStateFlow<Map<Long, ChatImageGenerationTaskState>>(emptyMap())
    val states: StateFlow<Map<Long, ChatImageGenerationTaskState>> = mutableStates.asStateFlow()

    /** Starts at most one task for a message; different messages may queue or run independently. */
    @Synchronized
    fun generate(sessionId: Long, messageId: Long): Boolean {
        if (activeByMessage[messageId]?.job?.isCompleted == false) return false

        val token = Any()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val foregroundHandle = foregroundController.acquire()
            try {
                runGeneration(sessionId, messageId)
            } finally {
                foregroundHandle.close()
                synchronized(this@ChatImageGenerationCoordinator) {
                    if (activeByMessage[messageId]?.token === token) {
                        activeByMessage.remove(messageId)
                    }
                }
            }
        }
        activeByMessage[messageId] = ActiveTask(job, token)
        publish(messageId, ChatImageGenerationTaskState.Generating)
        job.start()
        return true
    }

    @Synchronized
    fun isActive(messageId: Long): Boolean =
        activeByMessage[messageId]?.job?.isCompleted == false

    private suspend fun runGeneration(sessionId: Long, messageId: Long) {
        var newUuid: String? = null
        try {
            val preparation = withContext(Dispatchers.IO) {
                prepare(sessionId, messageId)
            } ?: run {
                clearState(messageId)
                return
            }
            val provider = preparation.provider
            if (provider == null || !preparation.config.isConfigured) {
                publish(
                    messageId,
                    ChatImageGenerationTaskState.Failed(
                        context.getString(R.string.image_generation_not_configured)
                    )
                )
                return
            }

            val scenePrompt = refineImageScene(preparation, sessionId)
            // 传提炼后的外貌而不是整张角色卡，否则性格与背景会稀释外貌特征。
            val characterAppearance =
                visualIdentityResolver.resolveForCharacter(preparation.character)
            val prompt = buildImagePrompt(
                characterName = preparation.character.name,
                characterDescription = characterAppearance,
                scenario = preparation.character.scenario,
                scenePrompt = scenePrompt,
                stylePrompt = preparation.stylePrompt
            )
            val generated: GeneratedImage = requestConcurrencyLimiter.withPermit(
                key = imageProviderPermitKey(provider.id),
                limit = provider.maxConcurrentRequests
            ) {
                imageClient.generate(preparation.config, prompt)
            }
            withContext(NonCancellable + Dispatchers.IO) {
                AppLogger.i(
                    "Image",
                    "Attaching image to message $messageId (${generated.bytes.size} bytes)"
                )
                newUuid = fileRepository.saveBytes(generated.bytes, generated.mimeType)
                val savedUuid = requireNotNull(newUuid)
                val replaced = chatRepository.replaceMessageImage(
                    messageId = preparation.target.id,
                    expectedContent = preparation.target.content,
                    newFileUuid = savedUuid
                )
                if (!replaced) fileRepository.deleteFile(savedUuid)
                newUuid = null
            }
            clearState(messageId)
        } catch (cancelled: CancellationException) {
            newUuid?.let { uuid ->
                withContext(NonCancellable + Dispatchers.IO) { fileRepository.deleteFile(uuid) }
            }
            clearState(messageId)
            throw cancelled
        } catch (error: Throwable) {
            AppLogger.e(
                "Image",
                "Image generation failed for message $messageId: ${error.message}",
                error
            )
            newUuid?.let { uuid ->
                withContext(NonCancellable + Dispatchers.IO) { fileRepository.deleteFile(uuid) }
            }
            publish(
                messageId,
                ChatImageGenerationTaskState.Failed(
                    error.message?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.image_generation_failed)
                )
            )
        }
    }

    private suspend fun prepare(sessionId: Long, messageId: Long): ImageGenerationPreparation? {
        val session = chatRepository.getSessionById(sessionId) ?: return null
        val messages = chatRepository.getMessagesBySessionId(sessionId)
        val targetIndex = messages.indexOfFirst { it.id == messageId }
        val target = messages.getOrNull(targetIndex)
        if (target == null || target.sessionId != sessionId || target.source != ChatMessage.Source.Char) {
            return null
        }
        val character = characterRepository.getCharacterById(session.characterId) ?: return null
        val provider = imageProviderRepository.getSelectedProvider()
        return ImageGenerationPreparation(
            target = target,
            character = character,
            recentUserMessage = messages.take(targetIndex)
                .lastOrNull { it.source == ChatMessage.Source.User }
                ?.content.orEmpty(),
            provider = provider,
            config = provider?.let { ImageGenerationConfig.fromProvider(it) } ?: EMPTY_IMAGE_CONFIG,
            stylePrompt = AppModel.imageGenerationStylePrompt
        )
    }

    private suspend fun refineImageScene(
        preparation: ImageGenerationPreparation,
        sessionId: Long
    ): String {
        val fallback = buildFallbackScenePrompt(
            recentUserMessage = preparation.recentUserMessage,
            assistantReply = preparation.target.content
        )
        return try {
            val provider = withContext(Dispatchers.IO) {
                providerSelectionResolver.requireImagePromptProvider(preparation.character)
            }
            val response = withContext(Dispatchers.IO) {
                llmRepository.generateWithProvider(
                    provider = provider,
                    request = LLMGenerationRequest(
                        messages = listOf(
                            LLMMessage(LLMMessageRole.System, IMAGE_SCENE_REFINEMENT_SYSTEM_PROMPT),
                            LLMMessage(
                                LLMMessageRole.User,
                                buildSceneRefinementInput(preparation)
                            )
                        ),
                        options = LLMGenerationOptions(temperature = 0.2f, maxTokens = 220),
                        includeReasoningInContent = false,
                        captureReasoning = false,
                        isPromptFinalized = true
                    ),
                    routingSessionKey = "image-prompt:$sessionId",
                    permitScope = LLM_PERMIT_SCOPE_IMAGE_PROMPT
                )
            }
            response.content.trim().takeIf { it.isNotEmpty() } ?: fallback
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // 静默回退会让"图突然不像了"变成无从排查的玄学，这里必须留痕。
            AppLogger.w(
                "Image",
                "Scene refinement failed, falling back to raw turn text: ${error.message}"
            )
            fallback
        }
    }

    private fun buildSceneRefinementInput(preparation: ImageGenerationPreparation): String =
        buildSceneRefinementInput(
            character = preparation.character,
            recentUserMessage = preparation.recentUserMessage,
            assistantReply = preparation.target.content
        )

    @Synchronized
    private fun publish(messageId: Long, state: ChatImageGenerationTaskState) {
        mutableStates.value = mutableStates.value + (messageId to state)
    }

    @Synchronized
    private fun clearState(messageId: Long) {
        mutableStates.value = mutableStates.value - messageId
    }

    private data class ActiveTask(val job: Job, val token: Any)

    private data class ImageGenerationPreparation(
        val target: ChatMessage,
        val character: Character,
        val recentUserMessage: String,
        val provider: ImageProvider?,
        val config: ImageGenerationConfig,
        val stylePrompt: String
    )

    private companion object {
        /** 没有可用图片服务时的占位配置，走与"未配置"完全相同的提示路径。 */
        val EMPTY_IMAGE_CONFIG = ImageGenerationConfig("", "", "", "")

        const val IMAGE_SCENE_REFINEMENT_SYSTEM_PROMPT = """
Refine the latest roleplay turn into a concise English description of the visible scene for an image prompt.
Include only visible subjects, location, current actions, pose, facial expression, spatial interaction, and relevant objects.
Do not include art, render, or photo style; permanent character appearance; ethnicity; global color grading; watermark, UI, or text-rendering rules.
Output only the scene description, with no labels, analysis, dialogue, or instructions.
"""
    }
}

sealed interface ChatImageGenerationTaskState {
    data object Generating : ChatImageGenerationTaskState
    data class Failed(val message: String) : ChatImageGenerationTaskState
}

internal fun buildSceneRefinementInput(
    character: Character,
    recentUserMessage: String,
    assistantReply: String
): String = buildString {
    appendLine("Character name:")
    appendLine(character.name.trim().ifBlank { "(none)" })
    appendLine()
    appendLine("Character description:")
    appendLine(character.description.trim().ifBlank { "(none)" })
    appendLine()
    appendLine("Scenario:")
    appendLine(character.scenario.trim().ifBlank { "(none)" })
    appendLine()
    appendLine("Recent user message:")
    appendLine(recentUserMessage.stripThinkBlocks().ifBlank { "(none)" })
    appendLine()
    appendLine("Latest character reply:")
    appendLine(assistantReply.stripThinkBlocks().ifBlank { "(none)" })
}
