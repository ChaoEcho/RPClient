package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainChatSessionGroup
import me.kafuuneko.rpclient.feature.main.model.MainGroupChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainSessionSelection

/** 首页状态树，组合资源统计、两类最近会话和多选交互状态。 */
data class MainHomeState(
    val resourceState: MainHomeResourceState,
    val recentChatsState: MainRecentChatsState,
    val recentGroupChatsState: MainRecentGroupChatsState,
    val selectionState: MainHomeSelectionState = MainHomeSelectionState.None
)

/** 首页角色卡与世界书入口所需的资源统计。 */
data class MainHomeResourceState(
    val totalCharacters: Int,
    val totalWorldBooks: Int
)

/** 最近单聊列表状态；分组折叠属于该列表节点的可追踪 UI 状态。 */
sealed class MainRecentChatsState {
    data object Empty : MainRecentChatsState()

    data class Content(
        val sessionGroups: List<MainChatSessionGroup>,
        val collapsedCharacterIds: Set<String> = emptySet()
    ) : MainRecentChatsState()
}

/** 最近群聊列表的空内容与可渲染内容状态。 */
sealed class MainRecentGroupChatsState {
    data object Empty : MainRecentGroupChatsState()

    data class Content(
        val sessions: List<MainGroupChatSessionItem>
    ) : MainRecentGroupChatsState()
}

/** 首页普通浏览与批量选择的互斥状态。 */
sealed class MainHomeSelectionState {
    data object None : MainHomeSelectionState()

    data class Selecting(
        val selectedSessions: Set<MainSessionSelection>
    ) : MainHomeSelectionState()
}

/**
 * 数据刷新后仅保留仍存在的单聊分组折叠状态。
 *
 * 多选状态不跨刷新恢复，避免数据库内容变化后保留失效的会话选择。
 */
internal fun MainHomeState.preserveCollapsedGroupsFrom(
    previous: MainHomeState
): MainHomeState {
    val refreshed = recentChatsState as? MainRecentChatsState.Content ?: return this
    val previousContent = previous.recentChatsState as? MainRecentChatsState.Content ?: return this
    val availableCharacterIds = refreshed.sessionGroups.mapTo(mutableSetOf()) { it.characterId }
    return copy(
        recentChatsState = refreshed.copy(
            collapsedCharacterIds = previousContent.collapsedCharacterIds
                .intersect(availableCharacterIds)
        )
    )
}
