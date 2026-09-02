package me.kafuuneko.rpclient.libs.groupchat

import com.google.gson.JsonParser
import kotlin.random.Random
import me.kafuuneko.rpclient.libs.prompt.matchesPlainTextKey
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData

/**
 * 根据群聊激活策略选择本轮一个或多个发言成员。
 *
 * 支持四种激活策略：
 * - Manual：手动指定单成员发言；
 * - List：全员依次按列表顺序发言；
 * - Pooled：轮流池模式（优先选择自上一轮用户发言后尚未发言的成员）；
 * - Natural：自然交互模式（结合点名、连续发言限制与角色活跃度掷骰决定激活列表）。
 */
class GroupChatSpeakerSelector {
    /**
     * 根据会话策略、成员状态和用户输入选择本轮发言者。
     *
     * @param explicitCharacterIds 由回复关系等其他明确用户操作提供的目标角色 ID。
     */
    fun select(
        session: GroupChatSession,
        members: List<GroupChatMemberData>,
        messages: List<GroupChatMessage>,
        activationText: String,
        isUserInput: Boolean,
        manualCharacterId: Long?,
        explicitCharacterIds: Set<Long> = emptySet(),
        random: Random = Random.Default
    ): List<GroupChatMemberData> {
        // 过滤已被静音的成员
        val available = members.filterNot { it.relation.muted }
        if (available.isEmpty()) return emptyList()
        val requestedCharacterIds = explicitCharacterIds + available
            .filter { activationText.containsExplicitMention(it.character.name) }
            .map { it.character.id }

        // 根据会话配置的激活策略分别派发
        return when (session.activationStrategy) {
            GroupChatSession.ActivationStrategy.Manual -> {
                val selected = available.firstOrNull {
                    it.character.id == manualCharacterId
                }
                listOfNotNull(selected ?: available.randomOrNull(random).takeIf {
                    !isUserInput
                })
            }
            GroupChatSession.ActivationStrategy.List -> available
            GroupChatSession.ActivationStrategy.Pooled -> {
                listOf(
                    selectPooled(
                        members = available,
                        messages = messages,
                        isUserInput = isUserInput,
                        explicitCharacterIds = requestedCharacterIds,
                        random = random
                    )
                )
            }
            GroupChatSession.ActivationStrategy.Natural -> {
                selectNatural(
                    session = session,
                    members = available,
                    messages = messages,
                    activationText = activationText,
                    isUserInput = isUserInput,
                    explicitCharacterIds = requestedCharacterIds,
                    random = random
                )
            }
        }
    }

    /**
     * 轮流池模式：优先从本轮尚未发言的成员池中随机选择一名成员。
     *
     * 规则：
     * - 若为用户新输入，重置发言记录；
     * - 查找自上一轮用户消息以来尚未发言的成员；
     * - 若全员均已发言，则排除上一条消息的发言者后从剩余成员中随机选取；
     * - 有明确 @ 或回复目标时，优先选择成员列表中的第一个明确目标。
     */
    private fun selectPooled(
        members: List<GroupChatMemberData>,
        messages: List<GroupChatMessage>,
        isUserInput: Boolean,
        explicitCharacterIds: Set<Long>,
        random: Random
    ): GroupChatMemberData {
        members.firstOrNull { it.character.id in explicitCharacterIds }?.let { return it }
        // 统计自上一条用户消息以来已发言的角色 ID 集合
        val spokenSinceUser = if (isUserInput) {
            emptySet()
        } else {
            messages.asReversed()
                .takeWhile { it.source != GroupChatMessage.Source.User }
                .mapNotNull { it.speakerCharacterId }
                .toSet()
        }
        // 筛选未发言候选者；若都已发言则排除上一条消息发言者（避免单人连续发言）
        val candidates = members.filterNot { it.character.id in spokenSinceUser }
            .ifEmpty {
                val lastSpeakerId = messages.lastOrNull {
                    it.source == GroupChatMessage.Source.Character
                }?.speakerCharacterId
                members.filterNot {
                    members.size > 1 && it.character.id == lastSpeakerId
                }.ifEmpty { members }
            }
        return candidates.random(random)
    }

    /**
     * 自然交互模式：综合点名、连续发言限制和角色活跃度进行自然选择。
     *
     * 规则：
     * - 检查是否允许自回复（连续发言）；
     * - 显式目标（@完整角色名 或 回复目标）属于强意图，超过 naturalMaxSpeakers 时仍全部保留；
     * - 普通角色名检测（弱意图）和活跃度（Talkativeness）候选只补足剩余名额；
     * - 若无任何成员被激活，则保底随机挑选一名成员。
     */
    private fun selectNatural(
        session: GroupChatSession,
        members: List<GroupChatMemberData>,
        messages: List<GroupChatMessage>,
        activationText: String,
        isUserInput: Boolean,
        explicitCharacterIds: Set<Long>,
        random: Random
    ): List<GroupChatMemberData> {
        // 判定自回复限制。明确目标不受该自动调度限制影响。
        val lastSpeakerId = messages.lastOrNull {
            it.source == GroupChatMessage.Source.Character
        }?.speakerCharacterId
        val automaticCandidates = if (session.allowSelfResponses || isUserInput) {
            members
        } else {
            members.filterNot { it.character.id == lastSpeakerId }.ifEmpty { members }
        }

        // 显式目标（强意图：@ 或 回复）
        val explicit = members.filter {
            it.character.id in explicitCharacterIds
        }

        // 非显式候选者参与自动调度
        val nonExplicitCandidates = automaticCandidates.filterNot { candidate ->
            explicit.any { it.character.id == candidate.character.id }
        }

        // 兼容原有的普通角色名检测
        val mentioned = nonExplicitCandidates.filter { member ->
            member.character.name
                .split(Regex("""[\s_-]+"""))
                .filter { it.isNotBlank() }
                .any { activationText.containsWholeToken(it) }
        }

        // 对剩余角色按活跃度概率抽取激活
        val rolled = nonExplicitCandidates.filterNot { candidate ->
            mentioned.any { it.character.id == candidate.character.id }
        }.shuffled(random).filter { member ->
            random.nextDouble() <= member.talkativeness()
        }

        val maxSpeakers = session.naturalMaxSpeakers.normalizedNaturalMaxSpeakers()
        val remainingCapacity = if (maxSpeakers == -1) {
            Int.MAX_VALUE
        } else {
            (maxSpeakers - explicit.size).coerceAtLeast(0)
        }
        val automaticActivated = (mentioned + rolled)
            .distinctBy { it.character.id }
            .take(remainingCapacity)

        // 显式成员必须全部保留；未超出总上限时，再用自动成员补足剩余名额。
        val activated = (explicit + automaticActivated).distinctBy { it.character.id }
        if (activated.isNotEmpty()) return activated

        // 若均未激活，从活跃度大于 0 的池中保底抽取一名
        val randomPool = automaticCandidates
            .filter { it.talkativeness() > 0.0 }
            .ifEmpty { automaticCandidates }
        return listOf(randomPool.random(random))
    }

    /** 从角色扩展字段读取活跃度，并限制在 [0.0, 1.0] 有效概率区间。 */
    private fun GroupChatMemberData.talkativeness(): Double {
        return runCatching {
            val root = JsonParser.parseString(character.extensionsJson).asJsonObject
            when {
                root.has("talkativeness") -> root.get("talkativeness").asDouble
                root.has("group_chat_talkativeness") -> root.get("group_chat_talkativeness").asDouble
                else -> DEFAULT_TALKATIVENESS
            }
        }.getOrDefault(DEFAULT_TALKATIVENESS).coerceIn(0.0, 1.0)
    }

    /** 按完整词边界判断用户是否点名，避免命中名称子串。 */
    private fun String.containsWholeToken(token: String): Boolean {
        return matchesPlainTextKey(token, ignoreCase = true, matchWholeWords = true)
    }

    /** 按 @ 完整角色名判断显式点名，保留普通文本名称匹配作为兼容路径。 */
    private fun String.containsExplicitMention(name: String): Boolean {
        if (name.isBlank()) return false
        var start = 0
        while (true) {
            val at = indexOf('@', start)
            if (at < 0) return false
            val nameStart = at + 1
            if (regionMatches(nameStart, name, 0, name.length, ignoreCase = true)) {
                val end = nameStart + name.length
                val next = getOrNull(end)
                if (next == null || !next.isLetterOrDigit() && next != '_') return true
            }
            start = nameStart + 1
        }
    }

    private companion object {
        /** 角色卡未提供活跃度时使用的默认发言概率 (50%)。 */
        const val DEFAULT_TALKATIVENESS = 0.5

        /** 将 Natural 人数上限归一化为可执行值。 */
        fun Int.normalizedNaturalMaxSpeakers(): Int {
            return when {
                this == -1 -> -1
                this in 1..3 -> this
                else -> 2
            }
        }
    }
}
