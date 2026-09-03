package me.kafuuneko.rpclient.libs.imagegeneration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** 已落库角色：命中缓存直接返回，未命中则提炼并写回。 */
    suspend fun resolveForCharacter(character: Character): String {
        character.visualIdentity.takeIf { it.isNotBlank() }?.let { return it }
        val description = character.description.trim()
        if (description.length < REFINEMENT_MIN_LENGTH) return description

        val refined = refine(
            characterName = character.name,
            description = description,
            promptProviderId = null,
            character = character
        )
        if (refined != description && character.id != 0L) {
            withContext(Dispatchers.IO) {
                characterRepository.updateVisualIdentity(character.id, refined)
            }
        }
        return refined
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
                    // 后台辅助任务必须用独立配额档位，否则会排在正文生成后面。
                    permitScope = LLM_PERMIT_SCOPE_IMAGE_PROMPT
                )
            }
            response.content.trim().takeIf { it.isNotEmpty() } ?: description
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AppLogger.w("Image", "Visual identity refinement failed: ${error.message}")
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
