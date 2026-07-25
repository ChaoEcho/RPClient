package me.kafuuneko.rpclient.libs.groupchat.model

import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession

/** 将持久化策略穷举映射为共享模型，新增枚举成员时由编译器强制补齐。 */
internal fun GroupChatSession.ActivationStrategy.toGroupChatActivationStrategy() = when (this) {
    GroupChatSession.ActivationStrategy.Manual -> GroupChatActivationStrategy.Manual
    GroupChatSession.ActivationStrategy.Natural -> GroupChatActivationStrategy.Natural
    GroupChatSession.ActivationStrategy.List -> GroupChatActivationStrategy.List
    GroupChatSession.ActivationStrategy.Pooled -> GroupChatActivationStrategy.Pooled
}

/** 将共享发言策略转换回 Room 枚举，保持持久化层不依赖 Prompt 模型。 */
internal fun GroupChatActivationStrategy.toEntity() = when (this) {
    GroupChatActivationStrategy.Manual -> GroupChatSession.ActivationStrategy.Manual
    GroupChatActivationStrategy.Natural -> GroupChatSession.ActivationStrategy.Natural
    GroupChatActivationStrategy.List -> GroupChatSession.ActivationStrategy.List
    GroupChatActivationStrategy.Pooled -> GroupChatSession.ActivationStrategy.Pooled
}

/** 将 Room 中的角色卡组合模式映射为群聊 Prompt 使用的共享模型。 */
internal fun GroupChatSession.CharacterCardMode.toGroupChatCharacterCardMode() = when (this) {
    GroupChatSession.CharacterCardMode.Swap -> GroupChatCharacterCardMode.Swap
    GroupChatSession.CharacterCardMode.Join -> GroupChatCharacterCardMode.Join
}

/** 将共享角色卡组合模式转换回可持久化枚举。 */
internal fun GroupChatCharacterCardMode.toEntity() = when (this) {
    GroupChatCharacterCardMode.Swap -> GroupChatSession.CharacterCardMode.Swap
    GroupChatCharacterCardMode.Join -> GroupChatSession.CharacterCardMode.Join
}

/** 将持久化消息来源映射为群聊构建器使用的来源类型。 */
internal fun GroupChatMessage.Source.toGroupChatMessageSource() = when (this) {
    GroupChatMessage.Source.Character -> GroupChatMessageSource.Character
    GroupChatMessage.Source.User -> GroupChatMessageSource.User
    GroupChatMessage.Source.System -> GroupChatMessageSource.System
}

/** 将共享消息来源转换回 Room 枚举；所有分支显式穷举以防新增来源被静默降级。 */
internal fun GroupChatMessageSource.toEntity() = when (this) {
    GroupChatMessageSource.Character -> GroupChatMessage.Source.Character
    GroupChatMessageSource.User -> GroupChatMessage.Source.User
    GroupChatMessageSource.System -> GroupChatMessage.Source.System
}
