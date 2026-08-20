package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainChatSessionGroup
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemSelection
import me.kafuuneko.rpclient.feature.main.model.MainGroupChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainStoryItem

/** 首页内容流的筛选分类。 */
enum class MainHomeContentTab {
    All,
    Single,
    Group,
    Story
}

/** 首页状态树，组合资源统计、会话、故事、内容筛选 Tab 和多选交互状态。 */
data class MainHomeState(
    val resourceState: MainHomeResourceState,
    val recentChatsState: MainRecentChatsState,
    val recentGroupChatsState: MainRecentGroupChatsState,
    val recentStoriesState: MainRecentStoriesState,
    val selectedContentTab: MainHomeContentTab = MainHomeContentTab.All,
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

/** 最近故事列表的空内容与可渲染内容状态。 */
sealed class MainRecentStoriesState {
    data object Empty : MainRecentStoriesState()

    data class Content(
        val stories: List<MainStoryItem>
    ) : MainRecentStoriesState()
}

/** 首页普通浏览与批量选择的互斥状态。 */
sealed class MainHomeSelectionState {
    data object None : MainHomeSelectionState()

    data class Selecting(
        val selectedItems: Set<MainHomeItemSelection>
    ) : MainHomeSelectionState()
}

/** 切换单个主页内容的选择状态；内容类型属于稳定键的一部分。 */
internal fun MainHomeSelectionState.Selecting.toggleItem(
    item: MainHomeItemSelection
): MainHomeSelectionState.Selecting {
    val updated = if (item in selectedItems) {
        selectedItems - item
    } else {
        selectedItems + item
    }
    return copy(selectedItems = updated)
}

/**
 * 数据刷新后仅保留仍存在的单聊分组折叠状态。
 *
 * 多选状态不跨刷新恢复，避免数据库内容变化后保留失效的内容选择。
 */
internal fun MainHomeState.preserveCollapsedGroupsFrom(
    previous: MainHomeState
): MainHomeState {
    val tabPreserved = copy(selectedContentTab = previous.selectedContentTab)
    val refreshed = tabPreserved.recentChatsState as? MainRecentChatsState.Content ?: return tabPreserved
    val previousContent = previous.recentChatsState as? MainRecentChatsState.Content ?: return tabPreserved
    val availableCharacterIds = refreshed.sessionGroups.mapTo(mutableSetOf()) { it.characterId }
    return tabPreserved.copy(
        recentChatsState = refreshed.copy(
            collapsedCharacterIds = previousContent.collapsedCharacterIds
                .intersect(availableCharacterIds)
        )
    )
}
