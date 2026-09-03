package me.kafuuneko.rpclient.libs.prompt.model

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript

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
    val userName: String,
    val userDescription: String,
    val character: Character,
    val session: ChatSession,
    val summary: String,
    val messages: List<ChatMessage>,
    val currentUserMessage: String?,
    /** 会话中的普通消息总数，不受总结后历史裁剪影响。 */
    val totalMessageCount: Int = messages.size + if (currentUserMessage.isNullOrBlank()) 0 else 1,
    val candidateLorebookEntries: List<LorebookEntry>,
    val candidateLorebooks: Map<Long, Lorebook> = emptyMap(),
    val recursiveScanningLorebookIds: Set<Long> = emptySet(),
    val provider: LLMProvider?,
    val maxContextTokens: Int,
    val maxResponseTokens: Int,
    val generationMode: PromptGenerationMode = PromptGenerationMode.Normal,
    val regenerationInstruction: String = "",
    val regexScripts: List<ScopedRegexScript> = emptyList()
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
 * Continue 与 Impersonate 会在聊天末尾提供各自唯一的生成目标，不能再叠加主提示词或 PHI。
 */
internal fun PromptGenerationMode.usesCharacterReplyTask(): Boolean {
    return this == PromptGenerationMode.Normal || this == PromptGenerationMode.Regenerate
}

/**
 * 是否注入主提示词与 PHI。
 *
 * 这两段同时承担两件事：一是"让角色写下一条回复"的任务，二是全局沙盒定义与虚构免责声明。
 * 只按任务维度剥掉它们，世界书就会成为请求的第一条消息，上游内容安全模型看到的开头
 * 是未加框的剧情描写，严格风控的服务会直接返回 403。因此默认在续写与扮演下也保留，
 * 由排在最后的模式 Nudge 覆盖任务目标；需要旧行为的用户可以关掉这个开关。
 */
internal fun PromptGenerationMode.injectsSystemFraming(): Boolean {
    return usesCharacterReplyTask() ||
            runCatching { AppModel.keepSystemPromptInSpecialModes }.getOrDefault(true)
}
