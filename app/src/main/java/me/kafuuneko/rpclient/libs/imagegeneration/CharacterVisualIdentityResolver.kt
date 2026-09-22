package me.kafuuneko.rpclient.libs.imagegeneration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.generation.RequestConcurrencyLimiter
import me.kafuuneko.rpclient.libs.debug.AppLogger
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LLM_PERMIT_SCOPE_IMAGE_PROMPT

/**
 * 把角色描述提炼成纯外貌段落，并缓存在角色行上。
 *
 * 角色卡里性格、背景、关系通常占大头，原样喂给绘图模型会稀释外貌特征，
 * 表现为"同一个角色每次出图长得都不一样"，且卡越长越明显。头像与聊天配图
 * 此前各写了一份提炼逻辑，现在合并到这里，同一角色只提炼一次。
 *
 * 缓存的失效方式是保存角色时整行清空（`Character.visualIdentity` 默认空串），
 * 不做字段级比对——多一次提炼远比用错一份过期外貌便宜。
 */
class CharacterVisualIdentityResolver(
    private val characterRepository: CharacterRepository,
    private val llmRepository: LLMRepository,
    private val providerSelectionResolver: LLMProviderSelectionResolver
) {

    private val refinementLimiter = RequestConcurrencyLimiter()

    /** 同角色请求串行重查缓存；排队取消不占额度，角色已编辑时不复用另一份输入的结果。 */
    suspend fun resolveForCharacter(character: Character): String =
        refinementLimiter.withPermit("visual:${character.id}", 1) {
            val current = withContext(Dispatchers.IO) {
                characterRepository.getCharacterById(character.id)
            }
            if (current != null && current.copy(visualIdentity = character.visualIdentity) == character) {
                current.visualIdentity.takeIf { it.isNotBlank() }?.let { return@withPermit it }
            }
            val description = character.description.trim()
            if (description.length < REFINEMENT_MIN_LENGTH) return@withPermit description

            // 本次生成继续使用提交时的角色快照，缓存写回则必须验证快照没有过期。
            val refined = refine(character.name, description, null, character)
            if (refined != description && character.id != 0L) {
                withContext(Dispatchers.IO) {
                    characterRepository.updateVisualIdentityIfUnchanged(character, refined)
                }
            }
            refined
        }

    /**
     * 尚未落库的角色编辑草稿：只提炼不缓存。
     *
     * 草稿里的描述可能与角色行不一致，写回会污染缓存。
     */
    suspend fun refineDraft(
        characterName: String,
        characterDescription: String,
        promptProviderId: Long
    ): String {
        val description = characterDescription.trim()
        if (description.length < REFINEMENT_MIN_LENGTH) return description
        return refine(
            characterName = characterName,
            description = description,
            promptProviderId = promptProviderId,
            character = null
        )
    }

    /** 任何失败都回退到原始描述，绝不因此挡住出图。 */
    private suspend fun refine(
        characterName: String,
        description: String,
        promptProviderId: Long?,
        character: Character?
    ): String {
        return try {
            val provider = withContext(Dispatchers.IO) {
                if (character != null) {
                    providerSelectionResolver.requireImagePromptProvider(character)
                } else {
                    providerSelectionResolver.requireImagePromptProvider(promptProviderId ?: 0L)
                }
            }
            val response = withContext(Dispatchers.IO) {
                llmRepository.generateWithProvider(
                    provider = provider,
                    request = LLMGenerationRequest(
                        messages = listOf(
                            LLMMessage(
                                LLMMessageRole.System,
                                AVATAR_APPEARANCE_REFINEMENT_SYSTEM_PROMPT
                            ),
                            LLMMessage(
                                LLMMessageRole.User,
                                "Character name:\n" +
                                    "${characterName.trim().ifBlank { "(none)" }}\n\n" +
                                    "Character description:\n$description"
                            )
                        ),
                        options = LLMGenerationOptions(
                            temperature = REFINEMENT_TEMPERATURE,
                            maxTokens = REFINEMENT_MAX_TOKENS
                        ),
                        includeReasoningInContent = false,
                        captureReasoning = false,
                        isPromptFinalized = true
                    ),
                    // 标记辅助任务类别，但仍服从 Provider 的总并发限制。
                    permitScope = LLM_PERMIT_SCOPE_IMAGE_PROMPT
                )
            }
            response.content.trim().takeIf { it.isNotEmpty() } ?: description
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AppLogger.w("Image", "Visual identity refinement failed: ${error.javaClass.simpleName}")
            description
        }
    }

    private companion object {
        /** 短描述本身就是外貌，多一次模型往返只会增加延迟。 */
        const val REFINEMENT_MIN_LENGTH = 80
        const val REFINEMENT_TEMPERATURE = 0.2f
        const val REFINEMENT_MAX_TOKENS = 220
    }
}
