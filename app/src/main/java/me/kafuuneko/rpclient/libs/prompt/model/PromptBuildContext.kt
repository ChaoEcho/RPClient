package me.kafuuneko.rpclient.libs.prompt.model

import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.prompt.model.UnavailablePromptImage
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/**
 * 单角色聊天 Prompt 构建所需的完整只读快照。
 *
 * 构建器不直接读取数据库；调用方应在进入构建流程前一次性准备会话、历史、
 * 世界书和 Regex 脚本，以保证同一次请求使用一致的数据版本。
 *
 * @property currentUserMessage 尚未写入历史的当前用户输入，重新生成时可为空。
 * @property totalMessageCount 会话普通消息总数，用于世界书 sticky/cooldown 计时。
 * @property recursiveScanningLorebookIds 明确允许递归扫描的世界书 ID。
 * @property regenerationInstruction 本次带指令重生成的一次性临时要求；不写入会话历史或数据库，仅在 [PromptGenerationMode.Regenerate] 且非空时注入尾部控制消息。
 */
data class PromptBuildContext(
    /** 当前会话或 Prompt 使用的用户名称。 */
    val userName: String,
    /** 当前会话或 Prompt 使用的用户设定。 */
    val userDescription: String,
    /** 当前状态或操作关联的角色数据。 */
    val character: Character,
    /** 当前页面展示或编辑的会话数据。 */
    val session: ChatSession,
    /** 当前会话或故事使用的摘要内容。 */
    val summary: String,
    /** 当前状态或请求包含的消息列表。 */
    val messages: List<ChatMessage>,
    val currentUserMessage: String?,
    /** 会话中的普通消息总数，不受总结后历史裁剪影响。 */
    val totalMessageCount: Int = messages.size + if (currentUserMessage.isNullOrBlank()) 0 else 1,
    /** 通过作用域筛选后待扫描的世界书条目列表。 */
    val candidateLorebookEntries: List<LorebookEntry>,
    /** 本次扫描可能参与激活的世界书列表。 */
    val candidateLorebooks: Map<Long, Lorebook> = emptyMap(),
    val recursiveScanningLorebookIds: Set<Long> = emptySet(),
    /** 当前请求关联的模型供应商类型。 */
    val provider: LLMProvider?,
    /** 模型请求允许使用的最大上下文 Token 数。 */
    val maxContextTokens: Int,
    /** 为模型回复预留的最大 Token 数。 */
    val maxResponseTokens: Int,
    /** 本次请求对应的新生成、续写或重生成模式。 */
    val generationMode: PromptGenerationMode = PromptGenerationMode.Normal,
    val regenerationInstruction: String = "",
    val regexScripts: List<ScopedRegexScript> = emptyList(),
    val messageImages: Map<Long, List<LLMImageReference>> = emptyMap(),
    val unavailableImages: Map<Long, List<UnavailablePromptImage>> = emptyMap()
)

/** 本次构建对应的用户操作，会影响尾部指令和世界书生成类型过滤。 */
enum class PromptGenerationMode {
    Normal,
    Continue,
    Impersonate,
    Regenerate
}

/**
 * 普通回复和重新生成共享“编写角色下一条回复”的任务提示。
 *
 * Continue 与 Impersonate 使用各自的任务提示；主提示词和 PHI 是否保留由偏好另行决定。
 */
internal fun PromptGenerationMode.usesCharacterReplyTask(): Boolean {
    return this == PromptGenerationMode.Normal || this == PromptGenerationMode.Regenerate
}

/**
 * 是否注入主提示词与 PHI。
 *
 * 默认在特殊模式保留用户配置的全局行为和世界观约束，由末尾的模式 Nudge 指定任务目标。
 * 开关通过构建器的只读偏好传入，不在模式判断中访问 Android 存储或用异常伪装默认值。
 */
internal fun PromptGenerationMode.injectsSystemFraming(keepInSpecialModes: Boolean): Boolean {
    return usesCharacterReplyTask() || keepInSpecialModes
}
